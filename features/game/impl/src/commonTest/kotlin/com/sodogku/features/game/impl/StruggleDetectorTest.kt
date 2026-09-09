package com.sodogku.features.game.impl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the board decides a player is stuck, and how loudly it is allowed to say
 * so.
 *
 * Every test drives the detector with explicit millis rather than a clock, which
 * is the whole reason this logic lives apart from the ViewModel: "ten seconds of
 * not touching the screen" is a rule about time, and a rule about time that can
 * only be exercised by waiting is a rule nobody exercises.
 *
 * The frequency budget gets as much attention here as the detection does. Every
 * signal below is easy to make fire; the hard part, and the part a regression
 * would land in, is that it stops.
 *
 * What is deliberately not here: whether the button is *drawn* beating.
 * `GameScreen` gates that on the board being uncovered and the attempt being
 * live, and `GameViewModelTest` covers the wiring from a tap to
 * `GameState.nudgeBoosters`.
 */
class StruggleDetectorTest {

    @Test
    fun aBoardNobodyHasTouchedIsNeverStuck() {
        val detector = StruggleDetector()

        // Two minutes of nothing. A player who has not made a single mark has
        // not tried the board yet, and the likeliest reading of this is a phone
        // face-up on a table.
        assertFalse(detector.nudging(TwoMinutes))
    }

    @Test
    fun tenSecondsWithoutTouchingTheScreenIsStuck() {
        val detector = StruggleDetector()
        detector.onMarked(Second)

        assertFalse(detector.nudging(Second + Idle - Second), "nudged before the idle window closed")
        assertTrue(detector.nudging(Second + Idle), "ten seconds of nothing did not read as stuck")
    }

    @Test
    fun anyInputRestartsTheIdleClock() {
        val detector = StruggleDetector()
        detector.onMarked(0)
        detector.onMarked(Idle - Second)

        assertFalse(
            detector.nudging(Idle + Second),
            "the idle window ran from the first input rather than the last",
        )
    }

    @Test
    fun aRefusedTapCountsAsBeingAwake() {
        val detector = StruggleDetector()
        detector.onMarked(0)
        // Tapping a dog that is already placed changes nothing on the board, but
        // it is a person looking at it.
        detector.onTouched(Idle - Second)

        assertFalse(detector.nudging(Idle + Second))
    }

    @Test
    fun aRefusedTapIsNotProgressTowardsTheMarkingStall() {
        val stalling = StruggleDetector()
        val fidgeting = StruggleDetector()
        // Both stay busy right up to the moment of the question, so neither can
        // be idle and the stall rule is the only thing that can differ.
        val now = StallFloor + Beat
        markThrough(stalling, from = 0, to = now)
        var at = 0L
        while (at <= now) {
            fidgeting.onTouched(at)
            at += Beat
        }

        assertTrue(stalling.nudging(now), "crossing off and never guessing did not read as stalled")
        assertFalse(fidgeting.nudging(now), "tapping a refused square counted as crossing off")
    }

    @Test
    fun crossingOffWithoutEverGuessingIsStuck() {
        val detector = StruggleDetector()
        // Marking steadily, so the idle rule can never be what fires: this is
        // the player who is busy and getting nowhere, which is the case the old
        // strike-counting gate could not see at all.
        markThrough(detector, from = Beat, to = StallFloor)

        assertTrue(detector.nudging(StallFloor + Beat))
    }

    @Test
    fun aHandfulOfCrossesIsNotAStall() {
        val detector = StruggleDetector()
        // One short of the threshold, spread wide enough to be past the stall
        // floor and close enough together never to be idle. Everything about
        // this player says stuck except how much they have actually done.
        repeat(MarksBeforeStall - 1) { detector.onMarked((it + 1).toLong() * FiveSeconds) }
        val lastMark = (MarksBeforeStall - 1) * FiveSeconds

        assertFalse(
            detector.nudging(lastMark + Second),
            "a few crosses is somebody thinking, not somebody stuck",
        )
    }

    @Test
    fun aWrongGuessIsStuckImmediately() {
        val detector = StruggleDetector()

        detector.onStruck(FiveSeconds)

        assertTrue(detector.nudging(FiveSeconds), "a wrong guess waited for a timer")
    }

    @Test
    fun aPlacementEndsTheBurstAtOnce() {
        val detector = StruggleDetector()
        detector.onStruck(FiveSeconds)
        assertTrue(detector.nudging(FiveSeconds))

        detector.onPlaced(FiveSeconds + Second)

        assertFalse(
            detector.nudging(FiveSeconds + Second),
            "the button kept beating after the move that unstuck the player",
        )
    }

    @Test
    fun aPlacementClearsTheLatchedWrongGuess() {
        val detector = StruggleDetector()
        detector.onStruck(0)
        detector.nudging(0)
        detector.onPlaced(Second)

        // Well past the burst and the quiet period, and long enough that a latch
        // left set would have re-armed several times over. `onTouched` rather
        // than `onMarked` keeps the player awake without feeding the stall rule,
        // so the only thing that could fire here is the strike.
        var at = Second
        val well = Second + Burst + Quiet
        while (at <= well) {
            detector.onTouched(at)
            at += Beat
        }

        assertFalse(detector.nudging(well), "the wrong guess never stopped counting")
    }

    @Test
    fun theBurstEndsOnItsOwnEvenWhileStillStuck() {
        val detector = StruggleDetector()
        detector.onStruck(0)

        assertTrue(detector.nudging(0))
        assertTrue(detector.nudging(Burst - Second), "the burst ended early")
        assertFalse(detector.nudging(Burst), "the burst ran past its window")
    }

    @Test
    fun aSecondBurstWaitsOutTheQuietPeriod() {
        val detector = StruggleDetector()
        detector.onStruck(0)
        detector.nudging(0)
        // Ends the burst and opens the quiet period.
        detector.nudging(Burst)

        assertFalse(
            detector.nudging(Burst + Quiet - Second),
            "a second burst started inside the quiet period",
        )
        assertTrue(
            detector.nudging(Burst + Quiet),
            "the player is still stuck and the button never came back",
        )
    }

    @Test
    fun aDeliberatePlayersOwnPaceRaisesTheBar() {
        // Three placements forty seconds apart, so 2.5x their median is a
        // hundred seconds. Crossing off throughout, so idle can never be what
        // fires and this is about the stall threshold alone.
        val slow = detectorPacedAt(FortySeconds)
        val lastDog = FortySeconds * PlacementsForAPace
        val pacedThreshold = (FortySeconds * StallPaceMultiple).toLong()

        markThrough(slow, from = lastDog, to = lastDog + pacedThreshold)

        assertFalse(
            slow.nudging(lastDog + StallFloor + Beat),
            "a player who takes forty seconds a dog was nagged at the twenty-second floor",
        )
        assertTrue(
            slow.nudging(lastDog + pacedThreshold),
            "the pace raised the bar out of reach entirely",
        )
    }

    @Test
    fun aFastPlayerStillGetsTheFloorRatherThanTheirOwnPace() {
        // Two-second placements would put 2.5x their pace at five seconds, which
        // is not a stall, it is a breath. The floor is what applies.
        val fast = detectorPacedAt(TwoSeconds)
        val lastDog = TwoSeconds * PlacementsForAPace

        markThrough(fast, from = lastDog, to = lastDog + StallFloor)

        assertFalse(
            fast.nudging(lastDog + StallFloor - Beat),
            "the floor moved down to meet a fast player's pace",
        )
        assertTrue(
            fast.nudging(lastDog + StallFloor),
            "twenty seconds without a dog never counted as a stall",
        )
    }

    @Test
    fun aFreshAttemptForgetsTheLastOne() {
        val detector = StruggleDetector()
        detector.onStruck(0)
        assertTrue(detector.nudging(0))

        detector.reset()

        assertFalse(detector.nudging(0), "the new board opened with the old board's wrong guess")
        assertFalse(detector.nudging(TwoMinutes), "the new board inherited the old board's idle clock")
    }

    /**
     * A detector that has seen three placements [gap] apart, so it has a median
     * pace to reason from, and nothing else against it.
     */
    private fun detectorPacedAt(gap: Long) = StruggleDetector().apply {
        repeat(PlacementsForAPace) { onPlaced((it + 1).toLong() * gap) }
    }

    /**
     * Crosses squares off every [Beat] from [from] to [to], which keeps the idle
     * rule quiet so a test can be about the stall rule alone. Well over
     * [MarksBeforeStall] crosses over any window worth testing.
     */
    private fun markThrough(detector: StruggleDetector, from: Long, to: Long) {
        var at = from
        while (at <= to) {
            detector.onMarked(at)
            at += Beat
        }
    }

    private companion object {
        const val Second = 1_000L
        const val TwoSeconds = 2_000L
        const val FiveSeconds = 5_000L
        const val FortySeconds = 40_000L
        const val TwoMinutes = 120_000L

        /**
         * How often a busy player touches the board in these tests. Comfortably
         * inside [Idle], so a test that means to be about something else is not
         * quietly answered by the idle rule.
         */
        const val Beat = 2_000L

        /** Mirrors the detector's own defaults; these tests are about them. */
        const val Idle = 10_000L
        const val StallFloor = 20_000L
        const val StallPaceMultiple = 2.5f
        const val Burst = 7_000L
        const val Quiet = 23_000L
        const val MarksBeforeStall = 6

        /** Enough placements to be past the detector's pace sample floor. */
        const val PlacementsForAPace = 3
    }
}
