package com.sodogku.libraries.progress.streak

import kotlinx.coroutines.flow.Flow

/**
 * What the streak *looks like*, and when it is allowed to interrupt.
 *
 * Separate from `DailyRepository` rather than four more methods on it, for two
 * reasons. The daily owns the rules (which day it is, what a freeze covers,
 * how the run is folded) and none of that changes here; this reads the same
 * rows and presents them. And the daily card is on the hot path of the level
 * pane, while this is opened deliberately, so the two have no reason to share
 * a lifecycle.
 *
 * Device-local, like everything else in `:libraries:progress`. There is no
 * server to arbitrate a streak and no account to carry one between phones.
 */
interface StreakRepository {

    /**
     * Re-emits when a daily result is written and when the local date rolls
     * over, so a page left open at midnight redraws its calendar.
     */
    fun observe(): Flow<StreakSummary>

    suspend fun summary(): StreakSummary

    /**
     * The ceremony owed to the player right now, if any.
     *
     * Safe to call as often as you like. It is a read and a pure decision, and
     * it keeps answering the same thing until [onPromptShown] records that the
     * moment happened. Callers should ask at a natural pause (a level finishing,
     * a sheet closing) rather than mid-puzzle.
     */
    suspend fun pendingPrompt(): StreakPrompt

    /**
     * Records that [prompt] was put in front of the player.
     *
     * Called when the screen appears, not when it is dismissed. A force-quit
     * half way through a celebration is not a reason to show it again, and the
     * intention moment is non-skippable, so earning a second one by killing the
     * app would make it a trap rather than a moment.
     */
    suspend fun onPromptShown(prompt: StreakPrompt)

    suspend fun reset()
}
