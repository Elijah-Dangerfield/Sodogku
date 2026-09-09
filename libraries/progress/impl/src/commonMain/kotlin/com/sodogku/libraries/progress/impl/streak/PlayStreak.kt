package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The streak, folded from the days the player finished a board.
 *
 * A separate fold from the daily's, and the split is the point rather than
 * duplication. The daily's walk has to reason about four outcomes, because a
 * daily can be completed, failed, frozen or restored, and each means something
 * different to a run. A day here has exactly two states: they played or they did
 * not. Trying to keep one function serving both is what tied the streak to the
 * daily in the first place.
 *
 * Everything is a pure function of a set of dates. No clock, no zone, no
 * database, so every rule below is one assertion rather than a scenario.
 */

/**
 * How many days in a row end at [today].
 *
 * **Today not being played does not break the run.** The walk starts at
 * yesterday in that case, because the day is not over: a player opening the app
 * at breakfast on day nine has a streak of eight, not zero, and telling them
 * otherwise would be the app breaking a streak the player still has hours to
 * keep.
 */
internal fun playStreakOn(today: LocalDate, played: Set<LocalDate>): Int {
    var day = if (today in played) today else today.previousDay()
    var streak = 0
    while (day in played) {
        streak++
        day = day.previousDay()
    }
    return streak
}

/**
 * The longest run the player has ever finished, current one included while it is
 * still the best.
 *
 * Walks the played days in order rather than probing outward from each one, so
 * this is linear in the number of days played rather than quadratic.
 */
internal fun longestPlayStreak(played: Set<LocalDate>): Int {
    if (played.isEmpty()) return 0
    val sorted = played.sorted()
    var longest = 1
    var run = 1
    for (index in 1 until sorted.size) {
        run = if (sorted[index - 1].nextDay() == sorted[index]) run + 1 else 1
        if (run > longest) longest = run
    }
    return longest
}

/**
 * Whole weeks ending in the week [today] falls in, oldest first, Monday first.
 *
 * A rolling window rather than a month, for the reason `StreakSummary` gives:
 * a month view needs navigation, an empty-month state, and an answer for what
 * January looks like to somebody who installed in March.
 */
internal fun playCalendarOn(today: LocalDate, played: Set<LocalDate>, weeks: Int): List<StreakDay> {
    val endOfWeek = today.plus(DatePeriod(days = DaysInWeek - today.dayOfWeek.isoIndex()))
    val start = endOfWeek.minus(DatePeriod(days = weeks * DaysInWeek - 1))

    return (0 until weeks * DaysInWeek).map { offset ->
        val date = start.plus(DatePeriod(days = offset))
        StreakDay(
            date = date,
            // Future is tested *before* played, and the order is the rule
            // rather than style. Clocks go backwards, from a timezone change or
            // a player moving the date, and a row dated ahead of today would
            // otherwise draw as a completed day the player has not lived yet.
            // `playStreakOn` already refuses to count those; the grid has to
            // agree with it or the headline number and the calendar disagree.
            state = when {
                date > today -> StreakDayState.Future
                date in played -> StreakDayState.Completed
                else -> StreakDayState.Missed
            },
            isToday = date == today,
        )
    }
}

/**
 * Monday is 1, Sunday is 7.
 *
 * Spelled out rather than taken from `ordinal`, which is zero-based and would
 * shift the whole grid by a day. That is the sort of mistake that looks right
 * every day except the one it is wrong on.
 */
private fun DayOfWeek.isoIndex(): Int = ordinal + 1

private fun LocalDate.previousDay(): LocalDate = minus(DatePeriod(days = 1))

private fun LocalDate.nextDay(): LocalDate = plus(DatePeriod(days = 1))

private const val DaysInWeek = 7
