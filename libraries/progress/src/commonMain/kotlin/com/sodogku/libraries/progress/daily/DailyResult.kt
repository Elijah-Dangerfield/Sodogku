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
     * It stays because rows carrying it are on players' disks and the fold has
     * to keep reading them — deleting the name would make those rows unparseable
     * and `toResult` would drop them, which silently shortens somebody's
     * history. Every rule below that names it is about that history.
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
