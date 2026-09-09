package com.sodogku.libraries.progress.streak

import kotlinx.coroutines.flow.Flow

/**
 * What the streak *looks like*, when it is allowed to interrupt, and the one
 * write that feeds it.
 *
 * Nothing to do with `DailyRepository` any more. The streak is fed by finishing
 * any board and owns its own table; the daily owns which puzzle everybody shares
 * today and how a freeze is budgeted. They used to be the same rows, which meant
 * clearing six campaign boards in a day did nothing for a streak.
 *
 * Device-local, like everything else in `:libraries:progress`. There is no
 * server to arbitrate a streak and no account to carry one between phones.
 */
interface StreakRepository {

    /**
     * Re-emits when a board is finished and when the local date rolls over, so a
     * page left open at midnight redraws its calendar.
     */
    fun observe(): Flow<StreakSummary>

    suspend fun summary(): StreakSummary

    /**
     * Records that the player finished a board today, whichever board it was.
     *
     * Idempotent, and called on every completion rather than only the first of
     * the day: the caller has no way of knowing whether today already counts
     * without asking, and asking is the same round trip as writing.
     */
    suspend fun onBoardCompleted()

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
