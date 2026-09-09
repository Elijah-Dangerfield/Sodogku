package com.sodogku.libraries.progress.streak

import kotlin.time.Duration
import kotlinx.datetime.LocalDate

/**
 * How one day looks on the streak page.
 *
 * There is no `Failed` any more. It existed when the streak was the daily's, and
 * a daily can be attempted and lost; a day you turned up and did not finish
 * anything is, for a streak about turning up, simply a day you did not finish
 * anything. Losing a board no longer costs a day that another board could still
 * save.
 */
enum class StreakDayState {
    /** The player finished a board. Any board. */
    Completed,

    /** A freeze covered it. Nothing spends one yet; see SD-28. */
    Bridged,

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

    /**
     * Whether today already counts.
     *
     * Kept as its own field rather than left for the caller to dig out of
     * [days], because it is the one thing the status page is really asking and
     * every caller would compute it the same way.
     */
    val playedToday: Boolean,

    /**
     * How long until the local date rolls over, so the status page can say how
     * long is left to keep the run.
     *
     * Resolved from the same clock snapshot as everything else here: a countdown
     * built from a second reading of the clock can disagree with the calendar
     * beside it across midnight.
     */
    val untilTomorrow: Duration,
) {
    /** The newest day the player actually played. What a celebration animates. */
    val latestCompleted: StreakDay?
        get() = days.lastOrNull { it.state == StreakDayState.Completed }
}
