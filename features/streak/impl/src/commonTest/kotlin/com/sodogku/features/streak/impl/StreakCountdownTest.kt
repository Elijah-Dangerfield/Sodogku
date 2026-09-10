package com.sodogku.features.streak.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The "you have N left to keep it" label.
 *
 * Small enough to look obviously right and wrong in two places: the rounding
 * direction, and where it stops talking in hours.
 */
class StreakCountdownTest {

    @Test
    fun wholeHoursReadAsHours() {
        assertEquals("5 hr", labelFor(5.hours))
    }

    @Test
    fun partHoursRoundUp() {
        // Up, not down. "1 hr" with fifty-nine minutes on the clock is a kinder
        // rounding than "0 hr", and this is a nudge rather than a timer.
        assertEquals("6 hr", labelFor(5.hours + 1.minutes))
        assertEquals("2 hr", labelFor(1.hours + 59.minutes))
    }

    @Test
    fun theLastHourSwitchesToMinutes() {
        // The point where the number stops being reassuring and starts being
        // the message.
        assertEquals("59 min", labelFor(59.minutes))
        assertEquals("5 min", labelFor(5.minutes))
    }

    @Test
    fun exactlyOneHourIsStillHours() {
        // The boundary, and the one that a `<=` instead of a `<` would flip.
        assertEquals("1 hr", labelFor(60.minutes))
    }

    @Test
    fun nothingLeftDoesNotReadAsAnHour() {
        assertEquals("0 min", labelFor(kotlin.time.Duration.ZERO))
    }

    private fun labelFor(duration: kotlin.time.Duration) =
        StreakState(untilTomorrow = duration).hoursLeftLabel
}
