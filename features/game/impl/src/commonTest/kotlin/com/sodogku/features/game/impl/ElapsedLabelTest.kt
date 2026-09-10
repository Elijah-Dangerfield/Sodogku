package com.sodogku.features.game.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The one clock the game shows a player, covering the two things it decides:
 * whether there is a time worth showing at all, and what shape it takes.
 *
 * Every branch of the shape is covered here (minutes and seconds, and the run
 * that crosses an hour) along with the null, which is the answer that keeps an
 * unplayed board from reading as a run that took no time.
 */
class ElapsedLabelTest {

    @Test
    fun aBoardWithNoTimeOnItShowsNoClock() {
        // An unplayed level, a day that was given up on, and a record written
        // before times were kept all hold zero. "0:00" would read as a run that
        // took no time rather than as one that never happened.
        assertNull(elapsedLabel(0L))
    }

    @Test
    fun aNegativeTimeShowsNoClockEither() {
        // Nothing writes one, which is the point: a clock that ran backwards is
        // a bug, and "-1:-1" on a level row is the worst way to find out.
        assertNull(elapsedLabel(-1L))
    }

    @Test
    fun aFinishedRunReadsAsMinutesAndSeconds() {
        assertEquals("1:42", elapsedLabel(102_000L))
    }

    @Test
    fun aRunPastAnHourKeepsTheHours() {
        // Rare, but a board left open over a lunch break produces one, and
        // "62:03" is a worse answer than an hours field.
        assertEquals("1:02:03", elapsedLabel(3_723_000L))
    }
}
