package com.sodogku.libraries.progress.impl.daily

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The date arithmetic the daily is built on: which day it is for this player, and
 * how long that stays true.
 *
 * Everything the rest of the daily does about time reduces to these two
 * functions, so this is where midnight, timezones, DST and a device clock set to
 * 1970 are pinned. The repository test asserts the behaviour they add up to; it
 * does not re-test the arithmetic.
 */
@OptIn(ExperimentalTime::class)
class DailyCalendarTest {

    @Test
    fun theDayIsTheLocalOne_notUtc() {
        val lateEveningInLondon = Instant.parse("2026-09-07T23:30:00Z")

        assertEquals(LocalDate(2026, 9, 7), dayOf(lateEveningInLondon, TimeZone.UTC))
        assertEquals(
            LocalDate(2026, 9, 8),
            dayOf(lateEveningInLondon, TimeZone.of("Europe/Berlin")),
            "an hour ahead is already tomorrow, and gets tomorrow's board",
        )
        assertEquals(
            LocalDate(2026, 9, 7),
            dayOf(lateEveningInLondon, TimeZone.of("America/New_York")),
            "five hours behind is still on the same board",
        )
    }

    @Test
    fun untilNextDay_countsToLocalMidnight() {
        val now = Instant.parse("2026-09-07T21:00:00Z")

        assertEquals(3.hours, untilNextDay(now, TimeZone.UTC))
        assertEquals(
            1.hours,
            untilNextDay(now, TimeZone.of("Europe/Berlin")),
            "the same instant is closer to midnight further east",
        )
    }

    @Test
    fun untilNextDay_respectsAShortDstDay() {
        val springForwardMorning = Instant.parse("2026-03-08T05:00:00Z")
        val newYork = TimeZone.of("America/New_York")

        assertEquals(LocalDate(2026, 3, 8), dayOf(springForwardMorning, newYork))
        assertEquals(
            23.hours,
            untilNextDay(springForwardMorning, newYork),
            "the clock jumps an hour on this date, so adding 24h would land an hour late",
        )
    }

    @Test
    fun untilNextDay_isAlwaysPositive() {
        val justBeforeMidnight = Instant.parse("2026-09-07T23:59:59Z")

        val remaining = untilNextDay(justBeforeMidnight, TimeZone.UTC)

        assertTrue(remaining > kotlin.time.Duration.ZERO, "a zero wait would spin the rollover flow")
        assertTrue(remaining <= 1.minutes)
    }

    @Test
    fun aClockSetToTheEpoch_stillResolvesToARealDay() {
        val brandNewDevice = Instant.parse("1970-01-01T00:30:00Z")

        assertEquals(LocalDate(1970, 1, 1), dayOf(brandNewDevice, TimeZone.UTC))
        assertEquals(
            LocalDate(1969, 12, 31),
            dayOf(brandNewDevice, TimeZone.of("America/New_York")),
            "west of Greenwich the epoch is still the day before, and epoch day goes negative",
        )
        assertTrue(
            LocalDate(1969, 12, 31).toEpochDays() < 0,
            "the pack has to wrap a negative index, which is why dailyIndexFor takes the modulo twice",
        )
    }

    @Test
    fun dayStepsAreSymmetricAcrossAMonthBoundary() {
        assertEquals(LocalDate(2026, 9, 1), LocalDate(2026, 8, 31).nextDay())
        assertEquals(LocalDate(2026, 8, 31), LocalDate(2026, 9, 1).previousDay())
        assertEquals(LocalDate(2026, 2, 28), LocalDate(2026, 3, 1).previousDay())
    }

    @Test
    fun sameMonth_isTheMonthAndTheYear() {
        assertTrue(LocalDate(2026, 9, 1).inSameMonthAs(LocalDate(2026, 9, 30)))
        assertTrue(!LocalDate(2026, 9, 30).inSameMonthAs(LocalDate(2026, 10, 1)))
        assertTrue(
            !LocalDate(2025, 9, 1).inSameMonthAs(LocalDate(2026, 9, 1)),
            "a monthly allowance that ignored the year would refill once a year",
        )
    }
}
