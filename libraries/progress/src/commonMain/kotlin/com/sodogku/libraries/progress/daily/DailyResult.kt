package com.sodogku.libraries.progress.daily

import kotlinx.datetime.LocalDate

/**
 * How one day of the daily challenge ended.
 *
 * A day with no result at all is *missed*, and missing is the only thing a
 * [Frozen] day is allowed to stand in for — a day the player attempted and lost
 * is not missed, they had their turn.
 */
enum class DailyOutcome {
    Completed,

    /** Played and ran out of bones. The day is spent either way. */
    Failed,

    /** Never played; a rewarded ad bridged the gap so the streak survives it. */
    Frozen,
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
) {
    val isCompleted: Boolean get() = outcome == DailyOutcome.Completed
}
