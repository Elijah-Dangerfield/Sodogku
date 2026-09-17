package com.sodogku.features.game.impl

import com.sodogku.libraries.scoring.NearMiss
import com.sodogku.libraries.scoring.Standing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a clear is allowed to say about itself, and in what order.
 *
 * [winBeats] is the win celebration's script: the list is both the content and
 * the sequence, because a beat's position in it is the delay it waits before it
 * lands. So the interesting cases are the four optional lines, each of which is
 * a refusal that is invisible from the code drawing it — the same reason
 * [lossFacts] is a function and not a run of `if`s inside a composable.
 *
 * Deliberately not covered here: that the beats actually *arrive* one after the
 * other, and that they hold still when the player has asked for fewer
 * animations. Both are claims about a composition. The paw rating's half of that
 * lives in `PawRatingHoldsStillTest` in `:libraries:ui`, which is the module set
 * up for that tier; the panel's own entrance is the same rule written the same
 * way beside it, and is read rather than asserted.
 */
class WinBeatsTest {

    @Test
    fun everyClearSaysTheSameFiveThingsInTheSameOrder() {
        // The floor, and it comes first: every refusal below is "this beat is
        // absent", which is also what a script that had stopped being built at
        // all would report.
        assertEquals(
            listOf(
                WinBeat.Dog,
                WinBeat.Verdict,
                WinBeat.Paws,
                WinBeat.Score,
                WinBeat.Stats,
            ),
            winBeats(wonState()),
            "a plain clear changed what it says, or the order it says it in",
        )
    }

    @Test
    fun aRunThatAlmostEarnedAnotherPawSaysSo() {
        val beats = winBeats(wonState(nearMiss = NearMiss(nextPaw = 3, pointsShort = 40)))

        assertTrue(WinBeat.NearMiss in beats)
        // After the facts, not among them. The gap is an aside about the run,
        // and reading it before the score would make the score read as the
        // consolation.
        assertTrue(beats.indexOf(WinBeat.NearMiss) > beats.indexOf(WinBeat.Stats))
    }

    @Test
    fun aRunWithNoGapWorthNamingSaysNothingAboutOne() {
        assertFalse(WinBeat.NearMiss in winBeats(wonState(nearMiss = null)))
    }

    @Test
    fun aRunThatCameInUnderTheOldBestClaimsIt() {
        assertTrue(
            WinBeat.NewBest in winBeats(wonState(elapsedMs = 78_000, targetTimeMs = 90_000)),
        )
    }

    @Test
    fun aFirstClearClaimsNoRecordBecauseThereWasNoneToBeat() {
        // A zero target is "never cleared", not "cleared instantly". The beat
        // has to drop out rather than arrive and draw nothing, or the sequence
        // holds a gap where a line was scheduled.
        assertFalse(WinBeat.NewBest in winBeats(wonState(elapsedMs = 78_000, targetTimeMs = 0)))
    }

    @Test
    fun aRunThatMissedTheOldBestSaysNothingAboutTheClock() {
        // The clock under the board already said "Over 1:30" while this was
        // happening. Repeating it on a clear turns it into a telling-off.
        assertFalse(
            WinBeat.NewBest in winBeats(wonState(elapsedMs = 96_000, targetTimeMs = 90_000)),
        )
    }

    @Test
    fun aTreatIsAnnouncedWhereItWasPromised() {
        assertTrue(WinBeat.Treat in winBeats(wonState(treatAwarded = true)))
        assertFalse(WinBeat.Treat in winBeats(wonState(treatAwarded = false)))
    }

    @Test
    fun onlyTheDailyReportsAStreak() {
        // The number is on the state for every board, because the drawer's flame
        // reads it too. A campaign clear mentioning it would be answering a
        // question nobody asked on that screen.
        assertTrue(WinBeat.Streak in winBeats(wonState(isDaily = true, dailyStreak = 12)))
        assertFalse(WinBeat.Streak in winBeats(wonState(isDaily = false, dailyStreak = 12)))
    }

    @Test
    fun aFirstDailyWithNoRunBehindItSaysNothingAboutAStreak() {
        assertFalse(WinBeat.Streak in winBeats(wonState(isDaily = true, dailyStreak = 0)))
    }

    @Test
    fun theOptionalLinesKeepTheirOrderWhenAllFourLand() {
        val beats = winBeats(
            wonState(
                nearMiss = NearMiss(nextPaw = 3, pointsShort = 40),
                elapsedMs = 78_000,
                targetTimeMs = 90_000,
                treatAwarded = true,
                isDaily = true,
                dailyStreak = 12,
            ),
        )

        assertEquals(
            listOf(WinBeat.NearMiss, WinBeat.NewBest, WinBeat.Treat, WinBeat.Streak),
            beats.drop(FixedBeats),
            "the order the extras arrive in moved, which also moves when each one lands",
        )
    }

    @Test
    fun eachBeatWaitsLongerThanTheOneBeforeIt() {
        // The script's index is its delay, so a stagger of zero would build the
        // right list and play it as a single flash.
        assertTrue(beatDelayMillis(order = 1) > beatDelayMillis(order = 0))
        assertTrue(beatDelayMillis(order = 0) > 0)
    }

    private fun wonState(
        nearMiss: NearMiss? = null,
        elapsedMs: Long = 0,
        targetTimeMs: Long = 0,
        treatAwarded: Boolean = false,
        isDaily: Boolean = false,
        dailyStreak: Int = 0,
    ) = GameState(
        // `beatBestTime` is gated on the phase as well as on the clock, so a
        // state that is not Won claims no record however the times read.
        phase = GamePhase.Won,
        paws = 3,
        standing = Standing.Flawless,
        nearMiss = nearMiss,
        elapsedMs = elapsedMs,
        targetTimeMs = targetTimeMs,
        treatAwarded = treatAwarded,
        isDaily = isDaily,
        dailyStreak = dailyStreak,
    )

    private companion object {
        /** Dog, verdict, paws, score, stats: the five every clear carries. */
        const val FixedBeats = 5
    }
}
