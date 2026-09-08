package com.sodogku.libraries.progress.streak

import kotlinx.datetime.LocalDate

/**
 * How one day looks on the streak page.
 *
 * [Bridged] deliberately covers both `DailyOutcome.Frozen` and
 * `DailyOutcome.Restored`. The two are separate on disk so their monthly
 * allowances can be counted apart, and that distinction is bookkeeping. A
 * player looking at a calendar wants to know whether the day is covered, not
 * which budget paid for it.
 *
 * [Failed] is its own state rather than a kind of miss, because the difference
 * matters to the player: a missed day can still be bought back and a lost one
 * never can.
 */
enum class StreakDayState {
    Completed,
    Bridged,
    Failed,
    Missed,

    /** Later than today. Drawn as a hole in the grid, not as a miss. */
    Future,
}

data class StreakDay(
    val date: LocalDate,
    val state: StreakDayState,
    val isToday: Boolean,
)

/**
 * Everything the streak page renders, resolved for one local date.
 *
 * Like `DailyStatus`, every field comes from the same snapshot of the clock, so
 * a page built from one summary cannot show today's run against yesterday's
 * calendar.
 *
 * Nothing in here is stored. [current] and [longest] are both folded out of the
 * `daily_result` rows on every read, for the reason `DailyRepository` gives at
 * length: a counter on disk is one dropped write away from a number nobody can
 * reconstruct and the player cannot dispute.
 */
data class StreakSummary(
    /** Consecutive days completed, as `DailyStatus.streak` reports it. */
    val current: Int,

    /**
     * The longest run the player has ever finished, including the current one
     * while it is still the best. Never smaller than [current].
     */
    val longest: Int,
    val today: LocalDate,

    /**
     * A whole number of weeks ending in the week [today] falls in, oldest first,
     * starting on a Monday. Days after today are [StreakDayState.Future].
     *
     * A rolling window rather than a month, and no way to page back through
     * history. The run that matters is the one ending today; a month view would
     * need navigation, an empty-month state, and a decision about what January
     * looks like to somebody who installed in March.
     */
    val days: List<StreakDay>,

    /** `daily.enabled` and `features.dailyChallenge`. The daily is the only input. */
    val enabled: Boolean,
) {
    /** The newest day the player actually played. What a celebration animates. */
    val latestCompleted: StreakDay?
        get() = days.lastOrNull { it.state == StreakDayState.Completed }
}
