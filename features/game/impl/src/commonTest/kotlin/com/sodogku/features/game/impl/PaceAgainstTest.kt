package com.sodogku.features.game.impl

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one comparison behind the time to beat, one rule per assertion.
 *
 * It is pulled out of both readers on purpose. The clock under the board asks it
 * once a second and the win sheet asks it once at the end, and the two have to
 * mean the same thing by "beat it" — otherwise a run can be told it is inside
 * the target for its whole length and then get no credit for finishing there.
 */
class PaceAgainstTest {

    @Test
    fun aLevelWithNoBestTimeHasNothingToMeasureAgainst() {
        // The first clear. Zero on the record is "never cleared", so treating it
        // as a target would put every run in the game past a time of nought
        // before the player touched a square.
        assertEquals(Pace.None, paceAgainst(elapsedMs = 30_000, bestMs = 0))
    }

    @Test
    fun aNegativeBestIsNoTargetEither() {
        // Nothing writes one. If something ever did, the honest answer is to
        // show nothing rather than to mark every run as behind immediately.
        assertEquals(Pace.None, paceAgainst(elapsedMs = 0, bestMs = -1))
    }

    @Test
    fun aRunUnderTheBestIsStillInside() {
        assertEquals(Pace.Inside, paceAgainst(elapsedMs = 89_999, bestMs = 90_000))
    }

    @Test
    fun matchingTheBestExactlyIsNotBeatingIt() {
        // The tie. `ProgressRepository` keeps the *lower* of the two times, so
        // an equal run writes nothing to the record, and a sheet that said "New
        // best" over a row that did not move would be reporting a fact that is
        // not one.
        assertEquals(Pace.Past, paceAgainst(elapsedMs = 90_000, bestMs = 90_000))
    }

    @Test
    fun aRunPastTheBestStaysPast() {
        // Monotonic, and it has to be: the mark holds for the rest of the run
        // rather than flashing for the one second the clock is level with the
        // target and then unmarking itself.
        assertEquals(Pace.Past, paceAgainst(elapsedMs = 90_001, bestMs = 90_000))
        assertEquals(Pace.Past, paceAgainst(elapsedMs = 900_000, bestMs = 90_000))
    }

    @Test
    fun theStartOfAChasedRunIsInsideRatherThanUnmeasured() {
        // A board that has only just opened is already being measured. The
        // difference matters at the first frame of a resume: nought against a
        // real target is a run in progress, not a run with nothing to chase.
        assertEquals(Pace.Inside, paceAgainst(elapsedMs = 0, bestMs = 90_000))
    }
}
