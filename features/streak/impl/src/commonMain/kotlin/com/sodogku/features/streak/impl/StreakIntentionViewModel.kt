package com.sodogku.features.streak.impl

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.sodogku.AppCache
import me.tatarka.inject.annotations.Inject

/**
 * The one-off "you have a streak now, keep it" moment.
 *
 * **It shows a run the player already has**, which is the change that makes it
 * worth showing at all. It used to arrive before anything had happened and ask
 * for a commitment to a daily puzzle they had never played: a contract for an
 * unknown thing, with an invented tap-the-paw ritual in front of it. Now it
 * lands after the second board, by which point finishing those boards *is* a
 * streak of one, and the ask is the honest one: you have this, do you want to
 * keep it.
 *
 * It records itself as shown the instant it opens, before the player has done
 * anything. That is the point: the screen has no way past it except through it,
 * so if a force-quit could un-record it, killing the app would be the only exit
 * and the moment would become a thing to escape rather than a thing to do.
 *
 * No `DailyRepository` any more. Sending the player into the daily was the old
 * screen's whole purpose, and it stopped making sense when the streak stopped
 * being the daily's: any board keeps it, including the one they just finished.
 * The only thing this screen does now is get out of the way.
 */
@Inject
class StreakIntentionViewModel(
    private val streak: StreakRepository,
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
            StreakIntentionAction.Commit -> sendEvent(StreakIntentionEvent.Close)
        }
    }

    private suspend fun StreakIntentionAction.onShown() {
        Catching { streak.onPromptShown(StreakPrompt.Intention) }
            .logOnFailure { "Failed to record the streak intention moment" }

        val summary = Catching { streak.summary() }
            .logOnFailure { "Failed to read the streak" }
            .getOrNull()

        // Read once. The screen is over in a few seconds and a player does not
        // walk into Settings mid-moment, so there is nothing here to observe.
        val haptics = Catching { appCache.get().hapticsEnabled }
            .logOnFailure { "Failed to read the haptics setting" }
            .getOrNull()
            ?: true

        updateState {
            it.copy(
                loaded = true,
                // Floored at one. This screen only opens off the back of a
                // finished board, so the run is at least a day; a zero here
                // would mean a failed read, and "0 day streak" under "keep it
                // up" is worse than an optimistic 1.
                streak = (summary?.current ?: 1).coerceAtLeast(1),
                week = summary?.days.orEmpty(),
                haptics = haptics,
            )
        }
    }
}

data class StreakIntentionState(
    val loaded: Boolean = false,

    /** The run the player already has. Never below one. */
    val streak: Int = 1,

    /** The repository's calendar; the screen takes the last week of it. */
    val week: List<StreakDay> = emptyList(),

    /** The player's setting. Provided to the design system, not read here. */
    val haptics: Boolean = true,
)

sealed interface StreakIntentionEvent {
    data object Close : StreakIntentionEvent
}

sealed interface StreakIntentionAction {
    data object Shown : StreakIntentionAction

    /** The one button. There is nothing to decline, so there is nothing else. */
    data object Commit : StreakIntentionAction
}
