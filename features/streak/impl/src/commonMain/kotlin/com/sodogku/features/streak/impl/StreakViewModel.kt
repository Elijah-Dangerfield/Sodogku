package com.sodogku.features.streak.impl

import androidx.lifecycle.viewModelScope
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.flowroutines.collectIn
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.progress.streak.StreakSummary
import com.sodogku.libraries.sodogku.AppCache
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * The streak page.
 *
 * [celebrating] is the whole difference between the two ways in, and it is
 * decided once, here, from the route. A page opened by tap must not animate, and
 * "must not animate" is easy to get wrong by leaving an entrance in the
 * composable and hoping nothing triggers it, so the screen has no entrance at
 * all and the one animated cell is opt-in.
 */
@Inject
class StreakViewModel(
    private val streak: StreakRepository,
    private val appCache: AppCache,
    @Assisted private val celebrating: Int,
) : SEAViewModel<StreakState, StreakEvent, StreakAction>(
    initialStateArg = StreakState(celebrating = celebrating),
) {

    init {
        takeAction(StreakAction.Load)
        // Observed as well as loaded: the page is reachable from the level pane,
        // which is reachable mid-session, so a daily finished in another tab of
        // the same session should land here without a reopen. It also redraws at
        // the rollover.
        streak.observe().collectIn(viewModelScope) { takeAction(StreakAction.SummaryChanged(it)) }
        appCache.updates
            .map { PlaybackSettings(it.hapticsEnabled, it.reduceAnimations) }
            .distinctUntilChanged()
            .collectIn(viewModelScope) { takeAction(StreakAction.PlaybackChanged(it)) }
        if (celebrating > 0) {
            // Recorded on arrival rather than on dismissal. A player who kills
            // the app mid-celebration has seen it, and showing it again on the
            // next launch would make a reward feel like a nag.
            takeAction(StreakAction.MarkCelebrated)
        }
    }

    override suspend fun handleAction(action: StreakAction) {
        when (action) {
            StreakAction.Load -> action.load()
            is StreakAction.SummaryChanged -> action.onSummary(action.summary)
            is StreakAction.PlaybackChanged -> action.updateState {
                it.copy(
                    haptics = action.settings.haptics,
                    // A page already open when the setting is flipped is not
                    // re-animated; only whether the *next* fill runs changes.
                    reduceAnimations = action.settings.reduceAnimations,
                )
            }
            StreakAction.MarkCelebrated -> {
                Catching { streak.onPromptShown(StreakPrompt.Celebrate(celebrating)) }
                    .logOnFailure { "Failed to record the streak celebration" }
            }
            StreakAction.Back -> sendEvent(StreakEvent.NavigateBack)
        }
    }

    /**
     * The first paint.
     *
     * Read as well as observed, for the reason `AchievementsViewModel` gives:
     * the observed flow is driven by a change feed, and on a launch where
     * nothing writes a result the screen would sit loading forever.
     */
    private suspend fun StreakAction.load() {
        val playback = Catching {
            appCache.get().let { PlaybackSettings(it.hapticsEnabled, it.reduceAnimations) }
        }
            .logOnFailure { "Failed to read the playback settings" }
            .getOrNull()
            ?: PlaybackSettings(haptics = true, reduceAnimations = false)
        updateState { it.copy(haptics = playback.haptics, reduceAnimations = playback.reduceAnimations) }

        val summary = Catching { streak.summary() }
            .logOnFailure { "Failed to read the streak" }
            .getOrNull()
            ?: return
        onSummary(summary)
    }

    private suspend fun StreakAction.onSummary(summary: StreakSummary) {
        updateState {
            it.copy(
                loading = false,
                current = summary.current,
                longest = summary.longest,
                today = summary.today,
                days = summary.days,
                enabled = summary.enabled,
                // Resolved here rather than in the screen, so the composable
                // never searches a list during composition, and so a
                // celebration that arrives with nothing completed (a wiped
                // table, a config change mid-flight) animates nothing instead
                // of reaching for a day that is not there.
                //
                // Null too when the player has asked for less motion. The page
                // still says the number; what it does not do is move.
                fillingIndex = summary.days
                    .indexOfFirst { day -> day == summary.latestCompleted }
                    .takeIf { index -> index >= 0 && celebrating > 0 && !it.reduceAnimations },
            )
        }
    }
}

data class StreakState(
    val loading: Boolean = true,
    val current: Int = 0,
    val longest: Int = 0,
    val today: LocalDate? = null,
    val days: List<StreakDay> = emptyList(),
    val enabled: Boolean = true,

    /** The player's setting. Provided to the design system, not read here. */
    val haptics: Boolean = true,
    val reduceAnimations: Boolean = false,

    /** The run this page was opened to celebrate, or `0` for a plain visit. */
    val celebrating: Int = 0,

    /** Index into [days] of the single cell that animates, or null. */
    val fillingIndex: Int? = null,
)

/** The two `AppData` toggles the celebration has to obey. */
data class PlaybackSettings(val haptics: Boolean, val reduceAnimations: Boolean)

sealed interface StreakEvent {
    data object NavigateBack : StreakEvent
}

sealed interface StreakAction {
    data object Load : StreakAction
    data class SummaryChanged(val summary: StreakSummary) : StreakAction
    data class PlaybackChanged(val settings: PlaybackSettings) : StreakAction
    data object MarkCelebrated : StreakAction
    data object Back : StreakAction
}
