package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.impl.daily.nextDay
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/**
 * The two things the streak page knows that the daily card does not: how long
 * the best run ever was, and what the last few weeks looked like.
 *
 * Both are folds over the same rows `DailyStreak.streakOn` walks, and both obey
 * its rules rather than restating them. Where the two could disagree there is a
 * test that says they don't.
 */

/**
 * The longest run of completed days the player has ever finished.
 *
 * Same three rules as the current streak, applied to the whole history instead
 * of to the tail:
 *
 * - a bridged day (frozen or restored) **joins** the days either side of it
 *   without counting itself, so an ad can never inflate a record;
 * - a failed day ends a run, because losing is not missing;
 * - a day with no row at all ends a run;
 * - rows dated after [today] are invisible, which is what a clock set forward
 *   and back leaves behind. `streakOn` ignores them and a record that did not
 *   would be the one number a moved clock could permanently inflate.
 *
 * Never smaller than the current streak, because the current run is one of the
 * runs it considers.
 */
internal fun longestStreakOn(today: LocalDate, outcomes: Map<LocalDate, DailyOutcome>): Int {
    val dates = outcomes.keys.filter { it <= today }.sorted()
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    for (date in dates) {
        val continues = previous != null && date == previous.nextDay()
        if (!continues) {
            longest = maxOf(longest, run)
            run = 0
        }
        when (outcomes[date]) {
            DailyOutcome.Completed -> run++
            DailyOutcome.Frozen, DailyOutcome.Restored -> Unit
            // The row exists, so the next day is still adjacent to *something*;
            // what it is not is adjacent to a run. Banking here and resetting is
            // what makes a loss end a run rather than bridge one.
            else -> {
                longest = maxOf(longest, run)
                run = 0
            }
        }
        previous = date
    }
    return maxOf(longest, run)
}

/**
 * A whole number of weeks ending in [today]'s week, oldest first.
 *
 * Aligned to Monday rather than to "[weeks] × 7 days back from today", so the
 * columns mean something and can carry weekday headings. An unaligned window
 * puts a different weekday in each column every day, which is a grid of numbers
 * rather than a calendar.
 *
 * Monday, not the device's locale first day. kotlinx-datetime has no
 * locale-aware first-day-of-week, and guessing it from the region would put the
 * app's grid out of step with the platform calendar for exactly the players who
 * would notice. ISO is at least consistently wrong for the Sunday-first regions
 * rather than sometimes wrong for everyone.
 */
internal fun calendarOn(
    today: LocalDate,
    outcomes: Map<LocalDate, DailyOutcome>,
    weeks: Int,
): List<StreakDay> {
    val start = today.startOfWeek().minus(weeks - 1, DateTimeUnit.WEEK)
    val days = mutableListOf<StreakDay>()
    var date = start
    repeat(weeks * DaysPerWeek) {
        days += StreakDay(
            date = date,
            state = date.stateOn(today, outcomes),
            isToday = date == today,
        )
        date = date.nextDay()
    }
    return days
}

private fun LocalDate.stateOn(
    today: LocalDate,
    outcomes: Map<LocalDate, DailyOutcome>,
): StreakDayState = when {
    this > today -> StreakDayState.Future
    else -> when (outcomes[this]) {
        DailyOutcome.Completed -> StreakDayState.Completed
        DailyOutcome.Frozen, DailyOutcome.Restored -> StreakDayState.Bridged
        DailyOutcome.Failed -> StreakDayState.Failed
        null -> StreakDayState.Missed
    }
}

private fun LocalDate.startOfWeek(): LocalDate =
    minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

internal const val DaysPerWeek = 7
