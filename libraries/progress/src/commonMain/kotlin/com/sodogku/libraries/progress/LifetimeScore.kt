package com.sodogku.libraries.progress

import com.sodogku.libraries.progress.daily.DailyResult
import kotlinx.datetime.LocalDate

/**
 * One score for the whole game, folded out of what is already stored.
 *
 * Every board the player has finished already keeps what it was worth —
 * [LevelRecord.bestScore] for a campaign level, [DailyResult.score] for a day —
 * so the lifetime total is a sum of those rows and never a counter of its own.
 * Same argument as the daily streak (`docs/decisions.md`): a stored tally has no
 * witness. If a crash between two writes, or a bug in one call site, leaves it
 * wrong, nothing on the device can tell, and progress is device-local so there
 * is no server copy to rebuild it from. A fold makes every past bug
 * retroactively fixable — ship the fix and the number is right on the next read.
 *
 * The cost is two full table reads per board opened. A few hundred rows, no
 * joins, once per level.
 */
object LifetimeScore {

    /** Everything banked, campaign and daily. Both packs pay into one number. */
    fun banked(levels: List<LevelRecord>, dailies: List<DailyResult>): Int =
        levels.sumOf { it.bestScore } + dailies.sumOf { it.score }

    /** What this campaign level has already contributed to [banked]. */
    fun bankedForLevel(levels: List<LevelRecord>, levelId: Int): Int =
        levels.firstOrNull { it.levelId == levelId }?.bestScore ?: 0

    /** What this day has already contributed to [banked]. */
    fun bankedForDaily(dailies: List<DailyResult>, date: LocalDate): Int =
        dailies.firstOrNull { it.date == date }?.score ?: 0

    /**
     * The number to show while a board is open: everything *else* the player has
     * banked, plus the better of what this board already held and what the
     * attempt on screen has earned so far.
     *
     * This is the whole double-count rule. A record only ever improves
     * ([ProgressRepository.onCompleted] keeps the better of the two), so a
     * replay of a level worth 5,000 must not add a second 5,000 as it goes —
     * the total may only move once the attempt passes the old best, and then
     * only by the difference. Adding [attemptScore] to [banked] instead would
     * make replaying one easy level the fastest way to earn in the game.
     */
    fun withAttempt(banked: Int, bankedForThisBoard: Int, attemptScore: Int): Int =
        banked - bankedForThisBoard + maxOf(bankedForThisBoard, attemptScore)
}
