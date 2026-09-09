package com.sodogku.libraries.ui.components.dog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dog's idle behaviour, which is the one part of it that is logic rather
 * than art.
 *
 * What matters is not that the sequence is random. It is that a viewer cannot
 * learn it: no clip repeats back to back, the pauses do not fall into step with
 * the clip changes, and every clip actually gets used.
 */
class DogLoopScheduleTest {

    @Test
    fun theSameClipNeverPlaysTwiceRunning() {
        // The one thing a viewer reliably spots. Swept rather than sampled,
        // because a fold that is off by one shows up at particular turns.
        for (clips in 2..6) {
            val schedule = DogLoopSchedule(seed = clips * 7, clipCount = clips)
            for (turn in 1 until Turns) {
                val previous = schedule.clipAt(turn - 1)
                val current = schedule.clipAt(turn)
                assertTrue(
                    previous != current,
                    "clip $current repeated at turn $turn with $clips clips",
                )
            }
        }
    }

    @Test
    fun everyClipGetsPlayed() {
        // A fold that quietly excluded the last clip would still pass the
        // no-repeat test above while the dog only ever did two things.
        for (clips in 2..6) {
            val schedule = DogLoopSchedule(seed = 1, clipCount = clips)
            val seen = (0 until Turns).map { schedule.clipAt(it) }.toSet()
            assertEquals(
                (0 until clips).toSet(),
                seen,
                "with $clips clips these were never played: ${(0 until clips).toSet() - seen}",
            )
        }
    }

    @Test
    fun aSingleClipIsAllThereIsToPlay() {
        // The board's degenerate case. No-repeat is impossible here and asking
        // for it would mean returning something out of range.
        val schedule = DogLoopSchedule(seed = 3, clipCount = 1)

        assertTrue((0 until Turns).all { schedule.clipAt(it) == 0 })
    }

    @Test
    fun theDogSometimesPausesAndSometimesDoesNot() {
        // Always pausing is a metronome and never pausing is the loop this
        // exists to break up. Both halves have to occur.
        val schedule = DogLoopSchedule(seed = 11, clipCount = 3)
        val holds = (0 until Turns).map { schedule.holdTurnsAt(it) }

        assertTrue(holds.any { it == 0 }, "the dog never once went straight into a clip")
        assertTrue(holds.any { it > 0 }, "the dog never once held still")
        assertTrue(holds.all { it in 0..MaxHold }, "a hold fell outside the configured range: $holds")
    }

    @Test
    fun thePausesAreSpreadAcrossTheClips() {
        // A dog that only ever pauses before the same gesture has a tell.
        //
        // This does *not* guard the hold salt, though it was written thinking it
        // did: removing that salt leaves this green, because the two questions
        // take different moduli of the hash and decorrelate without it. Renamed
        // to what it actually checks rather than left claiming more.
        val schedule = DogLoopSchedule(seed = 5, clipCount = 3)
        val perClip = IntArray(3)
        var total = 0
        for (turn in 0 until Turns) {
            if (schedule.holdTurnsAt(turn) > 0) {
                perClip[schedule.clipAt(turn)]++
                total++
            }
        }

        assertTrue(total > 0, "no holds at all, so this proves nothing")
        assertTrue(
            perClip.all { it < total * SkewCeiling },
            "holds cluster on one clip: ${perClip.toList()} of $total",
        )
    }

    @Test
    fun aSeedRepeatsExactlyAndDifferentSeedsDoNot() {
        // The whole reason this is not seeded from the clock: a dog that cannot
        // be reproduced cannot be debugged, and two dogs on one screen doing the
        // identical thing reads as a rendering fault.
        val first = DogLoopSchedule(seed = 42, clipCount = 3)
        val same = DogLoopSchedule(seed = 42, clipCount = 3)
        val other = DogLoopSchedule(seed = 43, clipCount = 3)

        val firstRun = (0 until Turns).map { first.clipAt(it) to first.holdTurnsAt(it) }
        val sameRun = (0 until Turns).map { same.clipAt(it) to same.holdTurnsAt(it) }
        val otherRun = (0 until Turns).map { other.clipAt(it) to other.holdTurnsAt(it) }

        assertEquals(firstRun, sameRun, "the same seed produced two different dogs")
        assertTrue(firstRun != otherRun, "two seeds produced the identical dog")
    }

    @Test
    fun aScheduleWithNoClipsIsRefused() {
        // Silently playing nothing would look like a broken image.
        assertTrue(runCatching { DogLoopSchedule(seed = 0, clipCount = 0) }.isFailure)
    }

    private companion object {
        const val Turns = 200
        const val MaxHold = 3

        /** No clip may own more than this share of the pauses. */
        const val SkewCeiling = 0.6
    }
}
