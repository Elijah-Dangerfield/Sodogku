package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.streak.StreakDayState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The streak, folded from the days a board was finished.
 *
 * Replaces `StreakFoldTest`, which tested the same questions against the daily's
 * four outcomes. The rules that survived the move are here; the ones that did
 * not were about a daily being failed or frozen, which a streak measured in
 * "did you turn up" has no opinion on.
 *
 * Three numbers come out of the same set of dates and they are easy to confuse,
 * so `brokenPlayStreakOn` is pinned against both of the others rather than on
 * its own: the run standing now, the best run ever, and the run that ended.
 */
class PlayStreakTest {

    @Test
    fun aRunEndingTodayIsCounted() {
        val played = daysBackFrom(Today, 5)

        assertEquals(5, playStreakOn(Today, played))
    }

    @Test
    fun todayNotYetPlayedDoesNotBreakTheRun() {
        // The day is not over. A player opening the app at breakfast on day nine
        // has a streak of eight, and telling them zero would be the app breaking
        // a run they still have hours to keep.
        val played = daysBackFrom(Today.minus(DatePeriod(days = 1)), 8)

        assertEquals(8, playStreakOn(Today, played))
    }

    @Test
    fun aGapEndsTheRun() {
        val played = daysBackFrom(Today, 3) + daysBackFrom(Today.minus(DatePeriod(days = 4)), 10)

        assertEquals(3, playStreakOn(Today, played), "the run stops at the missing day")
    }

    @Test
    fun aPlayerWhoHasNeverPlayedHasNothing() {
        assertEquals(0, playStreakOn(Today, emptySet()))
        assertEquals(0, longestPlayStreak(emptySet()))
    }

    @Test
    fun onlyYesterdayIsAStreakOfOne() {
        // The boundary of the "today does not count against you" rule. One more
        // day of not playing and this is zero.
        assertEquals(1, playStreakOn(Today, setOf(Today.minus(DatePeriod(days = 1)))))
        assertEquals(0, playStreakOn(Today, setOf(Today.minus(DatePeriod(days = 2)))))
    }

    @Test
    fun theLongestRunIsFoundAnywhereInHistory() {
        val old = daysBackFrom(Today.minus(DatePeriod(days = 40)), 9)
        val current = daysBackFrom(Today, 3)

        assertEquals(9, longestPlayStreak(old + current))
    }

    @Test
    fun theLongestRunIsNeverShorterThanTheCurrentOne() {
        // The property the old page depended on: a record smaller than the run
        // you are looking at is a page that opens by contradicting itself.
        for (length in 1..30) {
            val played = daysBackFrom(Today, length)
            assertTrue(
                longestPlayStreak(played) >= playStreakOn(Today, played),
                "a run of $length reported a shorter record",
            )
        }
    }

    @Test
    fun daysInTheFutureAreNotARun() {
        // Clocks go backwards, from a timezone change or a player fiddling with
        // the date. Rows ahead of today must not count toward a run ending today.
        val timeTravelled = daysBackFrom(Today.plus(DatePeriod(days = 5)), 3)

        assertEquals(0, playStreakOn(Today, timeTravelled))
    }

    @Test
    fun theCalendarIsWholeWeeksStartingOnMonday() {
        val days = playCalendarOn(Today, emptySet(), weeks = 5)

        assertEquals(35, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, days.last().date.dayOfWeek)
        assertEquals(1, days.count { it.isToday })
    }

    @Test
    fun theCalendarMarksPlayedMissedAndFuture() {
        val played = setOf(Today, Today.minus(DatePeriod(days = 1)))
        val days = playCalendarOn(Today, played, weeks = 1)

        val byDate = days.associateBy { it.date }
        assertEquals(StreakDayState.Completed, byDate.getValue(Today).state)
        assertEquals(StreakDayState.Completed, byDate.getValue(Today.minus(DatePeriod(days = 2 - 1))).state)
        assertEquals(
            StreakDayState.Missed,
            byDate.getValue(Today.minus(DatePeriod(days = 2))).state,
            "a day before today with no row is missed",
        )
        assertTrue(
            days.filter { it.date > Today }.all { it.state == StreakDayState.Future },
            "days after today are holes, not misses",
        )
    }

    @Test
    fun theCalendarAlwaysContainsToday() {
        // Swept across a whole week, because the Monday-first arithmetic is
        // exactly the kind that is right six days out of seven.
        for (offset in 0..6) {
            val day = Today.plus(DatePeriod(days = offset))
            val days = playCalendarOn(day, emptySet(), weeks = 5)
            assertTrue(days.any { it.date == day && it.isToday }, "$day (${day.dayOfWeek}) fell outside its own calendar")
        }
    }

    /**
     * What the streak screens' week strip depends on: the last seven cells are
     * the week today falls in, Monday to Sunday, whatever day today is. The
     * strip slices the tail of this calendar rather than asking for a second
     * window, so if this stopped being true the strip would show the wrong
     * week and nothing in the feature would notice.
     */
    @Test
    fun theLastSevenDaysAreTheWeekTodayFallsIn() {
        for (offset in 0..6) {
            val day = Today.plus(DatePeriod(days = offset))
            val week = playCalendarOn(day, emptySet(), weeks = 5).takeLast(7)

            assertEquals(DayOfWeek.MONDAY, week.first().date.dayOfWeek, "on $day the strip did not start on Monday")
            assertEquals(DayOfWeek.SUNDAY, week.last().date.dayOfWeek, "on $day the strip did not end on Sunday")
            assertTrue(week.any { it.isToday }, "on $day today was not in the last seven cells")
            assertEquals(
                day.minus(DatePeriod(days = day.dayOfWeek.ordinal)),
                week.first().date,
                "on $day the strip's Monday is a different Monday from today's",
            )
        }
    }

    @Test
    fun theRunBeforeTheGapIsWhatBroke() {
        // SD-127's whole question. Twelve days, a missed day, then today.
        val played = daysBackFrom(Today, 1) + daysBackFrom(Today.minus(DatePeriod(days = 2)), 12)

        assertEquals(1, playStreakOn(Today, played), "the run standing is the one day back")
        assertEquals(12, brokenPlayStreakOn(Today, played), "and the one that ended was twelve")
    }

    @Test
    fun theBrokenRunIsTheMostRecentOneRatherThanTheBest() {
        // The reason this is not `longestPlayStreak`. A record never falls, so
        // it can never say anything ended, and a player whose best was thirty
        // and who just lost four would be told the wrong number.
        val best = daysBackFrom(Today.minus(DatePeriod(days = 40)), 30)
        val recent = daysBackFrom(Today.minus(DatePeriod(days = 2)), 4)
        val played = best + recent + daysBackFrom(Today, 1)

        assertEquals(4, brokenPlayStreakOn(Today, played))
        assertEquals(30, longestPlayStreak(played), "the record is a different number, and still right")
    }

    @Test
    fun aFirstRunHasNothingBehindIt() {
        // Nobody's first day is a day they lost something.
        assertEquals(0, brokenPlayStreakOn(Today, daysBackFrom(Today, 1)))
        assertEquals(0, brokenPlayStreakOn(Today, daysBackFrom(Today, 9)))
        assertEquals(0, brokenPlayStreakOn(Today, emptySet()))
    }

    @Test
    fun anUnbrokenRunReportsNothingBrokenHoweverLongTheHistoryIs() {
        // The common case, and the one that would be most expensive to get
        // wrong: every player still on a run would be told it had ended.
        for (length in 1..30) {
            assertEquals(
                0,
                brokenPlayStreakOn(Today, daysBackFrom(Today, length)),
                "a run of $length days reported something behind it",
            )
        }
    }

    @Test
    fun aGapOfMonthsIsStillTheRunBeforeIt() {
        // No staleness rule, deliberately. A run that broke in the spring is
        // still the last run this player had, and the first board back is the
        // only moment anybody is listening.
        val old = daysBackFrom(Today.minus(DatePeriod(days = 240)), 7)
        val played = old + setOf(Today)

        assertEquals(7, brokenPlayStreakOn(Today, played))
    }

    @Test
    fun todayNotYetPlayedReadsTheSameRunAsTheStreakDoes() {
        // `playStreakOn` starts at yesterday when today is empty, because the
        // day is not over. This has to agree with it or the pair could report a
        // run of eight with the same eight days as the run that broke.
        val played = daysBackFrom(Today.minus(DatePeriod(days = 1)), 8)

        assertEquals(8, playStreakOn(Today, played))
        assertEquals(0, brokenPlayStreakOn(Today, played), "the run that is still running is not a run that broke")
    }

    private fun daysBackFrom(end: LocalDate, count: Int): Set<LocalDate> =
        (0 until count).mapTo(mutableSetOf()) { end.minus(DatePeriod(days = it)) }

    private companion object {
        /** A Wednesday, so the Monday-first grid is exercised off its own edges. */
        val Today = LocalDate(2026, 9, 9)
    }
}
