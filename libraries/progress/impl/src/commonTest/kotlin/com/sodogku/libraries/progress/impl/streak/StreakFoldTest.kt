package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.impl.daily.nextDay
import com.sodogku.libraries.progress.impl.daily.previousDay
import com.sodogku.libraries.progress.impl.daily.streakOn
import com.sodogku.libraries.progress.streak.StreakDayState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The record and the calendar, against the folds that produce them.
 *
 * Like `DailyStreakTest`, every assertion names an exact number or an exact
 * list. "The record is at least as long as the run" is satisfied by a fold that
 * always returns zero, and zero is what a broken walk produces.
 *
 * The calendar tests are written so that an empty history cannot pass them: each
 * one asserts the state of a named date, and the two that check the window's
 * shape assert its first and last date rather than its size.
 */
class StreakFoldTest {

    /** A Monday, so the grid's alignment is visible in the expected dates. */
    private val today = LocalDate(2026, 9, 7)

    @Test
    fun noHistory_hasNoRecord() {
        assertEquals(0, longestStreakOn(today, emptyMap()))
    }

    @Test
    fun theRecordIsTheBestRun_notTheLatestAndNotTheTotal() {
        val history = completed(0..1) + completed(5..10) + completed(20..22)

        assertEquals(
            6,
            longestStreakOn(today, history),
            "six days in the middle beats the two running now and the three before them",
        )
        assertEquals(2, streakOn(today, history), "and the current run is untouched by the record")
    }

    @Test
    fun theCurrentRunIsTheRecordWhileItIsTheBest() {
        val history = completed(0..8) + completed(15..17)

        assertEquals(9, longestStreakOn(today, history))
        assertEquals(9, streakOn(today, history), "the two agree while the best run is the live one")
    }

    @Test
    fun aBridgedDayJoinsTwoRunsWithoutCountingItself() {
        val history = completed(10..12) +
            mapOf(today.minusDays(13) to DailyOutcome.Frozen) +
            completed(14..17)

        assertEquals(
            7,
            longestStreakOn(today, history),
            "three plus four days played; the ad is not an eighth day of playing",
        )
    }

    @Test
    fun aRestoredDayBridgesExactlyLikeAFrozenOne() {
        val frozen = completed(10..12) +
            mapOf(today.minusDays(13) to DailyOutcome.Frozen) +
            completed(14..17)
        val restored = completed(10..12) +
            mapOf(today.minusDays(13) to DailyOutcome.Restored) +
            completed(14..17)

        assertEquals(longestStreakOn(today, frozen), longestStreakOn(today, restored))
        assertEquals(7, longestStreakOn(today, restored), "and it is seven, not zero")
    }

    @Test
    fun aLostDayEndsARunRatherThanBridgingIt() {
        val history = completed(10..12) +
            mapOf(today.minusDays(13) to DailyOutcome.Failed) +
            completed(14..17)

        assertEquals(
            4,
            longestStreakOn(today, history),
            "losing is not missing: the four behind it and the three in front are separate runs",
        )
    }

    @Test
    fun aRunResumesTheDayAfterALoss() {
        val history = mapOf(today.minusDays(10) to DailyOutcome.Failed) + completed(4..9)

        assertEquals(
            6,
            longestStreakOn(today, history),
            "the day after the loss starts a clean run rather than inheriting the broken one",
        )
    }

    @Test
    fun futureResultsAreInvisibleToTheRecordToo() {
        val timeTravelled = completed(0..2) +
            (1..9).associate { today.plusDays(it) to DailyOutcome.Completed }

        assertEquals(
            3,
            longestStreakOn(today, timeTravelled),
            "a clock wound forward and back must not leave a permanent record nobody earned",
        )
    }

    @Test
    fun theRecordIsNeverShorterThanTheRunInFlight() {
        val histories = listOf(
            completed(0..0),
            completed(0..4) + completed(10..12),
            completed(1..3) + mapOf(today to DailyOutcome.Failed),
            completed(0..1) + mapOf(today.minusDays(2) to DailyOutcome.Frozen) + completed(3..5),
        )

        histories.forEach { history ->
            val current = streakOn(today, history)
            assertTrue(current > 0, "a history with completions in it must have a run to compare")
            assertTrue(
                longestStreakOn(today, history) >= current,
                "the record considers every run, including the one still going",
            )
        }
    }

    @Test
    fun theCalendarStartsOnAMondayAndEndsOnTheSundayOfThisWeek() {
        val days = calendarOn(today, completed(0..0), weeks = 5)

        assertEquals(
            LocalDate(2026, 8, 10),
            days.first().date,
            "four whole weeks back from the Monday of today's week",
        )
        assertEquals(LocalDate(2026, 9, 13), days.last().date, "and out to the end of today's week")
        assertEquals(35, days.size)
    }

    @Test
    fun theWindowAlignsToTheWeekEvenWhenTodayIsNotAMonday() {
        val thursday = LocalDate(2026, 9, 10)

        val days = calendarOn(thursday, completed(0..0), weeks = 5)

        assertEquals(
            LocalDate(2026, 8, 10),
            days.first().date,
            "the Monday of the fifth week back, not thirty-five days before Thursday",
        )
        assertEquals(LocalDate(2026, 9, 13), days.last().date)
    }

    @Test
    fun everyOutcomeGetsItsOwnCell() {
        val history = mapOf(
            today to DailyOutcome.Completed,
            today.minusDays(1) to DailyOutcome.Failed,
            today.minusDays(2) to DailyOutcome.Frozen,
            today.minusDays(3) to DailyOutcome.Restored,
        )

        val states = calendarOn(today, history, weeks = 5).associate { it.date to it.state }

        assertEquals(StreakDayState.Completed, states[today])
        assertEquals(StreakDayState.Failed, states[today.minusDays(1)])
        assertEquals(StreakDayState.Bridged, states[today.minusDays(2)])
        assertEquals(
            StreakDayState.Bridged,
            states[today.minusDays(3)],
            "which budget paid for the day is bookkeeping, not something to draw twice",
        )
        assertEquals(
            StreakDayState.Missed,
            states[today.minusDays(4)],
            "and a day with no row at all is a miss, not an empty cell",
        )
    }

    @Test
    fun daysAfterTodayAreHolesRatherThanMisses() {
        val days = calendarOn(today, completed(0..0), weeks = 5).associate { it.date to it.state }

        assertEquals(StreakDayState.Future, days[today.plusDays(1)])
        assertEquals(StreakDayState.Future, days[LocalDate(2026, 9, 13)], "out to the end of the week")
        assertEquals(
            StreakDayState.Missed,
            days[today.minusDays(1)],
            "a day the player could have played and did not is still a miss",
        )
    }

    @Test
    fun exactlyOneCellIsMarkedToday() {
        val days = calendarOn(today, completed(0..2), weeks = 5)

        assertEquals(listOf(today), days.filter { it.isToday }.map { it.date })
    }

    private fun completed(daysBack: IntRange): Map<LocalDate, DailyOutcome> =
        daysBack.associate { today.minusDays(it) to DailyOutcome.Completed }

    private fun LocalDate.minusDays(days: Int): LocalDate {
        var date = this
        repeat(days) { date = date.previousDay() }
        return date
    }

    private fun LocalDate.plusDays(days: Int): LocalDate {
        var date = this
        repeat(days) { date = date.nextDay() }
        return date
    }
}
