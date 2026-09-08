package com.sodogku.features.settings.impl

import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.config.values.FeatureAchievements
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
    private val achievementsEnabled: FeatureAchievements,
    private val entitlements: Entitlements,
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
            SettingsAction.ToggleAchievements -> action.toggleAchievements()
            SettingsAction.RerunTutorial -> action.rerunTutorial()
            SettingsAction.OpenAchievements -> sendEvent(SettingsEvent.OpenAchievements)
            SettingsAction.OpenTerms -> sendEvent(SettingsEvent.OpenLink(termsUrl()))
            SettingsAction.OpenPrivacy -> sendEvent(SettingsEvent.OpenLink(privacyUrl()))
            SettingsAction.OpenFeedback -> sendEvent(SettingsEvent.OpenFeedback)
            SettingsAction.OpenPaywall -> sendEvent(SettingsEvent.OpenPaywall)
            SettingsAction.RestorePurchases -> action.restorePurchases()
            SettingsAction.DismissRestoreMessage -> action.updateState {
                it.copy(restoreMessage = null)
            }
        }
    }

    private suspend fun SettingsAction.load() {
        // Its own update, ahead of the disk read, because that read can fail and
        // return — and a screen that could not load a setting still has to show
        // (or hide) the badge rows correctly. Read here rather than captured at
        // construction: `features.achievements` is a live-ops switch.
        updateState {
            it.copy(
                achievementsAvailable = achievementsEnabled(),
                isPro = entitlements.isPro.value,
            )
        }

        val saved = Catching { appCache.get() }
            .logOnFailure { "Failed to read settings" }
            .getOrNull()
            ?: return

        updateState {
            it.copy(
                hapticsEnabled = saved.hapticsEnabled,
                reduceAnimations = saved.reduceAnimations,
                colorblindMode = saved.colorblindMode,
                achievementsVisible = saved.achievementsVisible,
            )
        }
    }

    /**
     * Asks the store what this device already owns.
     *
     * The outcome travels as a value into one `updateState` rather than being
     * read back off `state`, which lags by a dispatch.
     *
     * Every branch says something. A restore that silently does nothing is the
     * single most common reason this control gets reported as broken: the player
     * cannot tell "you never bought it" from "we could not ask".
     */
    private suspend fun SettingsAction.restorePurchases() {
        updateState { it.copy(restoreMessage = RestoreMessage.Working) }
        val outcome = Catching { entitlements.restore() }
            .logOnFailure { "Restore failed" }
            .getOrNull()
        val pro = entitlements.isPro.value
        updateState {
            it.copy(
                isPro = pro,
                restoreMessage = when (outcome) {
                    RestoreOutcome.Restored -> RestoreMessage.Restored
                    RestoreOutcome.NothingToRestore -> RestoreMessage.NothingToRestore
                    else -> RestoreMessage.Failed
                },
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

    /**
     * Display only.
     *
     * This writes one boolean to [AppCache] and stops there. It deliberately
     * does **not** reach `AchievementsRepository`: the fact log keeps recording
     * while badges are switched off, so a player who turns them back on months
     * later sees what they actually earned instead of starting from zero. If
     * this ever grows a second line that touches the repository, that is the
     * bug.
     */
    private suspend fun SettingsAction.toggleAchievements() {
        val next = !state.achievementsVisible
        updateState { it.copy(achievementsVisible = next) }
        persist { it.copy(achievementsVisible = next) }
    }

    /**
     * Puts the guided first three levels back on.
     *
     * The flag is cleared *before* the navigation event goes out, because the
     * board's ViewModel reads it once as it loads and a write that landed after
     * that read would open level 1 with nothing to teach.
     *
     * This is the reason `hasCompletedTutorial` is a separate flag from
     * `hasUserOnboarded`: replaying the lessons must not put the welcome screen
     * back in front of someone with 200 levels behind them.
     */
    private suspend fun SettingsAction.rerunTutorial() {
        persist { it.copy(hasCompletedTutorial = false) }
        sendEvent(SettingsEvent.RerunTutorial)
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

    /**
     * Whether badges are shown. Display only — the achievement log keeps
     * recording either way, which is why the row that opens the grid is hidden
     * rather than the grid being emptied.
     */
    val achievementsVisible: Boolean = true,

    /** Whether this device already has Pro, which decides what the store row offers. */
    val isPro: Boolean = false,

    /** Set while a restore is in flight, and to its result afterwards. */
    val restoreMessage: RestoreMessage? = null,

    /**
     * `features.achievements`. False takes the badge rows off this screen
     * entirely, toggle included — a switch that hid the grid but left a
     * "Show badges" control would be a setting with nothing behind it.
     *
     * True by default so an unreachable config leaves the feature present,
     * which is the fail-open direction SPEC 4.2 asks for.
     */
    val achievementsAvailable: Boolean = true,
    val appVersion: String = "",
)

/** What a restore attempt has to say for itself. */
enum class RestoreMessage { Working, Restored, NothingToRestore, Failed }

sealed interface SettingsEvent {
    data object NavigateBack : SettingsEvent
    data object OpenFeedback : SettingsEvent
    data object OpenAchievements : SettingsEvent

    /** The Pro sheet, opened by the player rather than offered. */
    data object OpenPaywall : SettingsEvent

    /** Back to level 1 with the coach marks armed, replacing the back stack. */
    data object RerunTutorial : SettingsEvent

    /** Terms and privacy are hosted pages, so they open in a browser. */
    data class OpenLink(val url: String) : SettingsEvent
}

sealed interface SettingsAction {
    data object Load : SettingsAction
    data object Back : SettingsAction
    data object ToggleHaptics : SettingsAction
    data object ToggleReduceAnimations : SettingsAction
    data object ToggleColorblind : SettingsAction
    data object ToggleAchievements : SettingsAction
    data object RerunTutorial : SettingsAction
    data object OpenAchievements : SettingsAction
    data object OpenPaywall : SettingsAction

    /**
     * Apple requires a visible control that restores a non-consumable purchase,
     * and rejects for its absence. It is also simply the right thing on a device
     * with no account: a reinstall is the only way a paying player gets their
     * purchase back.
     */
    data object RestorePurchases : SettingsAction
    data object DismissRestoreMessage : SettingsAction
    data object OpenTerms : SettingsAction
    data object OpenPrivacy : SettingsAction
    data object OpenFeedback : SettingsAction
}
