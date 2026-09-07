package com.sodogku.libraries.progress

import kotlinx.coroutines.flow.Flow

/**
 * The player's campaign history, and the only thing allowed to decide what is
 * unlocked.
 *
 * Callers report what happened — an attempt started, a level cleared, a level
 * skipped — and never write a state or a "best" themselves. That keeps the two
 * rules that are easy to get wrong in one place: a metric only ever improves,
 * and a level only ever moves forward through [LevelState].
 *
 * Device-local by design. There are no accounts (see `docs/decisions.md`), so
 * this does not survive a reinstall and Settings says so.
 */
interface ProgressRepository {

    /**
     * Emits on every change to [levelId], starting with its current record.
     * Levels with no history emit [LevelRecord.unplayed].
     */
    fun observe(levelId: Int): Flow<LevelRecord>

    suspend fun record(levelId: Int): LevelRecord

    /** Only levels the player has touched. The map fills the gaps itself. */
    suspend fun all(): List<LevelRecord>

    /** Highest level the player has unlocked. */
    suspend fun unlockedThrough(): Int

    /**
     * Called when a level opens, not when it is won — an abandoned attempt
     * still counts, which is what makes the number worth showing.
     */
    suspend fun onAttemptStarted(levelId: Int)

    /** Records a clear; keeps the best of each metric, never a worse one. */
    suspend fun onCompleted(levelId: Int, score: Int, paws: Int, timeMs: Long)

    /** Advances past a level without clearing it, so the next one still opens. */
    suspend fun onSkipped(levelId: Int)

    suspend fun reset()
}
