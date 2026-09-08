package com.sodogku.features.game.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The one clock the game shows a player, covering the two things it decides:
 * whether there is a time worth showing at all, and what shape it takes.
 *
 * The shape itself belongs to `ShareText.duration` and is tested there against
 * every branch (seconds, minutes, the run that crosses an hour). What is tested
 * here is that this reads the same formatter rather than a second copy of it,
 * and the null, which is this function's own answer and nobody else's.
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
        // The shape the share card prints, so a player who solved a board over
        // a lunch break reads the same time on the sheet and in the post.
        assertEquals("1:02:03", elapsedLabel(3_723_000L))
    }
}
