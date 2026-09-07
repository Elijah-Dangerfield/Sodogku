package com.sodogku.features.onboarding.impl

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.sodogku.AppCache
import kotlin.time.TimeSource
import me.tatarka.inject.annotations.Inject

/**
 * First-launch welcome. Sodogku has no accounts, so this flow only decides
 * whether the player goes straight to the game or through the guided tutorial
 * first, then flips `hasUserOnboarded` so it never opens again.
 *
 * The guided tutorial itself is levels 1 to 3 of the campaign, not a separate
 * mode — [OnboardingEvent.NavigateToHome] is the exit either way, and the
 * tutorial flag rides on [AppCache].
 */
@Inject
class OnboardingViewModel(
    private val appCache: AppCache,
) : SEAViewModel<OnboardingState, OnboardingEvent, OnboardingAction>(
    initialStateArg = OnboardingState(),
) {

    private val logger = KLog.withTag("OnboardingFlow")

    private val onboardingStartedAt = TimeSource.Monotonic.markNow()

    private var exitedToHome = false

    init {
        takeAction(OnboardingAction.ResolveEntry)
    }

    override suspend fun handleAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.ResolveEntry -> action.handleResolveEntry()
            OnboardingAction.Start -> action.finish(skippedTutorial = false)
            OnboardingAction.SkipTutorial -> action.finish(skippedTutorial = true)
        }
    }

    private suspend fun OnboardingAction.handleResolveEntry() {
        Catching {
            if (appCache.get().hasUserOnboarded) {
                exitedToHome = true
                sendEvent(OnboardingEvent.NavigateToHome)
            } else {
                logger.logEvent("onboarding.step_viewed", "step" to "welcome")
            }
        }.logOnFailure { "Onboarding entry resolution failed" }
    }

    private suspend fun OnboardingAction.finish(skippedTutorial: Boolean) {
        updateState { it.copy(isFinishing = true) }
        exitedToHome = true
        logger.logEvent(
            "onboarding.completed",
            "duration_sec" to onboardingStartedAt.elapsedNow().inWholeSeconds,
            "skipped_tutorial" to skippedTutorial,
        )
        appCache.update {
            it.copy(hasUserOnboarded = true, hasCompletedTutorial = skippedTutorial)
        }
        sendEvent(OnboardingEvent.NavigateToHome)
    }

    override fun onCleared() {
        if (!exitedToHome) logger.logEvent("onboarding.abandoned", "step" to "welcome")
        super.onCleared()
    }
}

data class OnboardingState(
    val isFinishing: Boolean = false,
)

sealed interface OnboardingEvent {
    data object NavigateToHome : OnboardingEvent
}

sealed interface OnboardingAction {
    /** Internal: fired once on init to bounce a returning player straight home. */
    data object ResolveEntry : OnboardingAction

    /** Play, running the guided tutorial across the first three levels. */
    data object Start : OnboardingAction

    /** Play, marking the tutorial already seen. */
    data object SkipTutorial : OnboardingAction
}
