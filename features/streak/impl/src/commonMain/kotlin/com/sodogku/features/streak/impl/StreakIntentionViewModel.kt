package com.sodogku.features.streak.impl

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.progress.daily.DailyRepository
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.sodogku.AppCache
import me.tatarka.inject.annotations.Inject

/**
 * The one-off "start your streak" moment.
 *
 * It records itself as shown the instant it opens, before the player has done
 * anything. That is the point: the screen has no way past it except through it,
 * so if a force-quit could un-record it, killing the app would be the only exit
 * and the moment would become a thing to escape rather than a thing to do.
 *
 * The daily's id is resolved lazily rather than passed in the route, because the
 * screen can outlive a midnight rollover and a board id captured at navigate
 * time would send the player to yesterday's puzzle.
 */
@Inject
class StreakIntentionViewModel(
    private val streak: StreakRepository,
    private val daily: DailyRepository,
    private val appCache: AppCache,
) : SEAViewModel<StreakIntentionState, StreakIntentionEvent, StreakIntentionAction>(
    initialStateArg = StreakIntentionState(),
) {

    init {
        takeAction(StreakIntentionAction.Shown)
    }

    override suspend fun handleAction(action: StreakIntentionAction) {
        when (action) {
            StreakIntentionAction.Shown -> action.onShown()
            StreakIntentionAction.Filled -> action.updateState { it.copy(started = true) }
            StreakIntentionAction.Play -> action.play()
            StreakIntentionAction.Later -> sendEvent(StreakIntentionEvent.Close)
        }
    }

    private suspend fun StreakIntentionAction.onShown() {
        Catching { streak.onPromptShown(StreakPrompt.Intention) }
            .logOnFailure { "Failed to record the streak intention moment" }
        // Read once. The screen is over in a few seconds and a player does not
        // walk into Settings mid-moment, so there is nothing here to observe.
        val haptics = Catching { appCache.get().hapticsEnabled }
            .logOnFailure { "Failed to read the haptics setting" }
            .getOrNull()
            ?: true
        updateState { it.copy(haptics = haptics) }
    }

    private suspend fun StreakIntentionAction.play() {
        val status = Catching { daily.status() }
            .logOnFailure { "Failed to read today's daily" }
            .getOrNull()
        // No board, no navigation. Closing on a failed read leaves the player
        // where they were, which is worse than a puzzle and much better than a
        // screen with no way out.
        if (status == null || !status.enabled) {
            sendEvent(StreakIntentionEvent.Close)
            return
        }
        sendEvent(StreakIntentionEvent.OpenDaily(status.levelId))
    }
}

data class StreakIntentionState(
    /** True once the paw has finished filling. Nothing else is offered before. */
    val started: Boolean = false,

    /** The player's setting. Provided to the design system, not read here. */
    val haptics: Boolean = true,
)

sealed interface StreakIntentionEvent {
    data class OpenDaily(val levelId: Int) : StreakIntentionEvent
    data object Close : StreakIntentionEvent
}

sealed interface StreakIntentionAction {
    data object Shown : StreakIntentionAction
    data object Filled : StreakIntentionAction
    data object Play : StreakIntentionAction
    data object Later : StreakIntentionAction
}
