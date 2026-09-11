package com.sodogku.features.game.impl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the screen acts on `StruggleDetector`'s conclusion.
 *
 * The detector decides whether a *player* is stuck; this decides whether the
 * board is in any position to say so. Keeping them apart is what lets both be
 * one assertion each — `StruggleDetectorTest` drives a clock, and everything
 * here is a state object and a boolean.
 *
 * These conditions used to be an expression inside `GameScreen`'s booster row,
 * which is why they are worth this file: every reason to *start* a nudge had a
 * test and not one reason to stop it did, because stopping one could only be
 * observed by rendering a screen.
 */
class BoosterAttentionTest {

    private val stuck = GameState(
        phase = GamePhase.Playing,
        nudgeBoosters = true,
        livesRemaining = 3,
    )

    @Test
    fun aStuckPlayerOnALiveBoardGetsTheButton() {
        assertTrue(stuck.boostersAskingForAttention)
    }

    @Test
    fun aBoardNobodyIsStuckOnIsQuiet() {
        assertFalse(stuck.copy(nudgeBoosters = false).boostersAskingForAttention)
    }

    /**
     * A tick on a finished board returns before it asks the detector anything,
     * so `nudgeBoosters` keeps whatever it held when the last dog landed. Win
     * mid-burst and the flag is still true underneath the sheet.
     */
    @Test
    fun aSolvedBoardStopsAskingEvenWithTheFlagStillSet() {
        assertFalse(stuck.copy(phase = GamePhase.Won).boostersAskingForAttention)
        assertFalse(stuck.copy(phase = GamePhase.Lost).boostersAskingForAttention)
        assertFalse(stuck.copy(phase = GamePhase.Recap).boostersAskingForAttention)
    }

    /**
     * [GamePhase.Loading] is the one phase the rule has to exclude on its own.
     * Every other non-playing phase is in [GameState.isCovered] too, so dropping
     * the phase clause entirely is invisible unless this case is written down —
     * which is how the mutation that deleted it survived a first pass.
     */
    @Test
    fun aBoardThatHasNotOpenedYetDoesNotAsk() {
        assertFalse(stuck.copy(phase = GamePhase.Loading).boostersAskingForAttention)
    }

    /**
     * Each of these draws over the grid and takes the taps the beating button is
     * inviting. Listed one at a time rather than as a scenario, because the
     * failure mode is a single missing entry.
     */
    @Test
    fun aButtonUnderneathSomethingIsAskingForATapNobodyCanGive() {
        assertFalse(stuck.copy(drawerOpen = true).boostersAskingForAttention)
        assertFalse(stuck.copy(hintCells = setOf(0, 1)).boostersAskingForAttention)
    }

    /**
     * The rehearsal covers the board with its own coaching, and the detector is
     * not even asked during it. Both halves have to agree or the tutorial argues
     * with itself.
     */
    @Test
    fun theRehearsalNeverAsks() {
        assertFalse(stuck.copy(tutorial = TutorialStep.entries.first()).boostersAskingForAttention)
    }
}
