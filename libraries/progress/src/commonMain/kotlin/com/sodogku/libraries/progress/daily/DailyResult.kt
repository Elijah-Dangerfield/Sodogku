package com.sodogku.libraries.progress.daily

import kotlinx.datetime.LocalDate

/**
 * How one day of the daily challenge ended.
 *
 * A day with no result at all is *missed*, and missing is the only thing
 * [Frozen] and [Restored] are allowed to stand in for — a day the player
 * attempted and lost is not missed, they had their turn.
 */
enum class DailyOutcome {
    Completed,

    /**
     * Played and given up on. **Nothing writes this any more** (SD-49): the
     * control that did was Give up on today, and a run that merely goes out of
     * bones leaves the day open so it can be revived or restarted.
     *
     * **It has no writer and is not deleted, on purpose.** Rows carrying it are
     * still on players' disks, and since SD-111 `toResult` recognises the name
     * and skips them so the day opens on its board again. Deleting the name
     * would skip them too — by failing to parse — which reaches the same place
     * by accident and costs the parse failure its own meaning: a name that
     * stops parsing takes any *future* outcome down with it, and the drop here
     * is meant to be about this one reading and no other.
     *
     * So the folds in `DailyStreak` still have to answer for it even though it
     * can no longer reach them, and their branches say so where they sit.
     */
    Failed,

    /** Never played; a rewarded ad bridged the gap so the streak survives it. */
    Frozen,

    /**
     * Never played; bridged by a streak restore rather than a freeze.
     *
     * The fold treats it exactly like [Frozen] — it bridges without counting.
     * It is a separate name only so the two allowances can be counted apart:
     * `daily.freezesPerMonth` counts [Frozen] rows and
     * `daily.restoreDaysPerMonth` counts these, and one budget must not quietly
     * spend the other.
     */
    Restored,
}

/**
 * What happened on one calendar day, keyed by the player's **local** date.
 *
 * [levelIndex] is the position in `daily.pack` that the date resolved to when the
 * result was written. It is recorded rather than recomputed because
 * `daily.poolOffset` can move, and a shared score that no longer names the board
 * it was scored on is worse than no record.
 */
data class DailyResult(
    val date: LocalDate,
    val levelIndex: Int,
    val outcome: DailyOutcome,
    val score: Int,
    val paws: Int,
    val timeMs: Long,
)
