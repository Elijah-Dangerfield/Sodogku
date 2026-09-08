package com.sodogku.features.settings.impl

import com.sodogku.libraries.config.values.LegalPrivacyUrl
import com.sodogku.libraries.config.values.LegalTermsUrl
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.versionString
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import me.tatarka.inject.annotations.Inject

/**
 * The player's settings.
 *
 * Every toggle writes straight through to [AppCache] rather than batching on
 * exit: a setting the player flipped and then backgrounded the app on has to
 * still be flipped when they come back, and there is no "save" button to hang
 * a commit off.
 *
 * The legal URLs come from remote config so a moved policy page is a config
 * change rather than an app release.
 */
@Inject
class SettingsViewModel(
    private val appCache: AppCache,
    private val termsUrl: LegalTermsUrl,
    private val privacyUrl: LegalPrivacyUrl,
) : SEAViewModel<SettingsState, SettingsEvent, SettingsAction>(
    initialStateArg = SettingsState(appVersion = BuildInfo.versionString()),
) {

    init {
        takeAction(SettingsAction.Load)
    }

    override suspend fun handleAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Load -> action.load()
            SettingsAction.Back -> sendEvent(SettingsEvent.NavigateBack)
            SettingsAction.ToggleHaptics -> action.toggleHaptics()
            SettingsAction.ToggleReduceAnimations -> action.toggleReduceAnimations()
            SettingsAction.ToggleColorblind -> action.toggleColorblind()
            SettingsAction.OpenTerms -> sendEvent(SettingsEvent.OpenLink(termsUrl()))
            SettingsAction.OpenPrivacy -> sendEvent(SettingsEvent.OpenLink(privacyUrl()))
            SettingsAction.OpenFeedback -> sendEvent(SettingsEvent.OpenFeedback)
        }
    }

    private suspend fun SettingsAction.load() {
        val saved = Catching { appCache.get() }
            .logOnFailure { "Failed to read settings" }
            .getOrNull()
            ?: return

        updateState {
            it.copy(
                hapticsEnabled = saved.hapticsEnabled,
                reduceAnimations = saved.reduceAnimations,
                colorblindMode = saved.colorblindMode,
            )
        }
    }

    // Each toggle reads `state` exactly once, at the top, and passes the result
    // on. `state` is a derived flow that lags `updateState` by a dispatch, so
    // reading it back after a write returns the stale value.
    private suspend fun SettingsAction.toggleHaptics() {
        val next = !state.hapticsEnabled
        updateState { it.copy(hapticsEnabled = next) }
        persist { it.copy(hapticsEnabled = next) }
    }

    private suspend fun SettingsAction.toggleReduceAnimations() {
        val next = !state.reduceAnimations
        updateState { it.copy(reduceAnimations = next) }
        persist { it.copy(reduceAnimations = next) }
    }

    private suspend fun SettingsAction.toggleColorblind() {
        val next = !state.colorblindMode
        updateState { it.copy(colorblindMode = next) }
        persist { it.copy(colorblindMode = next) }
    }

    private suspend fun persist(transform: (AppData) -> AppData) {
        Catching { appCache.update(transform) }
            .logOnFailure { "Failed to persist a setting" }
    }
}

data class SettingsState(
    val hapticsEnabled: Boolean = true,
    val reduceAnimations: Boolean = false,
    val colorblindMode: Boolean = false,
    val appVersion: String = "",
)

sealed interface SettingsEvent {
    data object NavigateBack : SettingsEvent
    data object OpenFeedback : SettingsEvent

    /** Terms and privacy are hosted pages, so they open in a browser. */
    data class OpenLink(val url: String) : SettingsEvent
}

sealed interface SettingsAction {
    data object Load : SettingsAction
    data object Back : SettingsAction
    data object ToggleHaptics : SettingsAction
    data object ToggleReduceAnimations : SettingsAction
    data object ToggleColorblind : SettingsAction
    data object OpenTerms : SettingsAction
    data object OpenPrivacy : SettingsAction
    data object OpenFeedback : SettingsAction
}
