package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.Solution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The screen-reader route to placing a dog.
 *
 * A screen reader eats the double tap the sighted gesture is made of, so this
 * is the *only* way a player using one can commit a guess. Two things have to
 * hold: the action has to reach `commit` at all, and it has to be absent
 * everywhere `commit` would refuse — an announced action that does nothing is
 * worse than no action, because a player cannot tell it from a wrong guess.
 */
class PlaceAtTest {

    @Test
    fun anEmptySquareOffersThePlacement() {
        assertNotNull(placeAt(playing(), cell = 5) {})
    }

    @Test
    fun aSquareTheBoardHasAlreadyRuledOutDoesNot() {
        assertNull(placeAt(playing(autoMarks = setOf(5)), cell = 5) {})
    }

    @Test
    fun aSquareThatAlreadyHasADogDoesNot() {
        val placed = Solution.empty(4).withPlacement(row = 1, col = 1)
        assertNull(placeAt(playing(placed = placed), cell = 5) {})
    }

    @Test
    fun aSquareThatCostABoneDoesNot() {
        assertNull(placeAt(playing(wrongGuesses = setOf(5)), cell = 5) {})
    }

    /**
     * The board is still on screen while the win sheet is over it, and the
     * sheet is a drawing — nothing about it stops a semantics action firing.
     */
    @Test
    fun aFinishedBoardOffersNothing() {
        GamePhase.entries.filter { it != GamePhase.Playing }.forEach { phase ->
            assertNull(placeAt(playing().copy(phase = phase), cell = 5) {}, "$phase still offered a placement")
        }
    }

    /**
     * Two taps, because that is what the ViewModel measures a placement in. One
     * would only cross the square off — which is the bug this whole action
     * exists to avoid, and it would look like a working feature to anyone
     * reading the semantics tree.
     */
    @Test
    fun thePlacementSendsTheSameTwoTapsAThumbWouldEmit() {
        val sent = mutableListOf<GameAction>()
        placeAt(playing(), cell = 7) { sent += it }!!.invoke()

        assertEquals(listOf<GameAction>(GameAction.CellTapped(7), GameAction.CellTapped(7)), sent)
    }

    /**
     * A manual cross is the player's own note and is not the board refusing the
     * square. The sighted gesture converts it — tap to clear, tap again to
     * commit — so the action has to be on offer there too.
     */
    @Test
    fun aSquareThePlayerCrossedOffThemselvesStillOffersThePlacement() {
        assertNotNull(placeAt(playing(manualMarks = setOf(5)), cell = 5) {})
    }

    /**
     * With auto-mark off nothing is drawn, so nothing is refused.
     *
     * This is the accessibility half of R8 and it fails silently in the worst
     * direction: a screen-reader player who turns the crosses off would find
     * most of the board — a whole row, column, region and ring per dog — quietly
     * offering no way to place anything, with no announcement saying why.
     */
    @Test
    fun withAutoMarkOffARuledOutSquareOffersThePlacementAgain() {
        val state = playing(autoMarks = setOf(5))
        assertNull(placeAt(state, cell = 5) {}, "the fixture rules nothing out")

        assertNotNull(placeAt(state.copy(autoMarkVisible = false), cell = 5) {})
    }

    /**
     * An auto-mark the player tapped away reads as empty, so it has to offer
     * what an empty square offers. It did not before: `placeAt` tested the raw
     * deduction, and `clearedMarks` only ever came off in the drawing.
     */
    @Test
    fun anAutoMarkThePlayerClearedOffersThePlacement() {
        assertNotNull(placeAt(playing(autoMarks = setOf(5), clearedMarks = setOf(5)), cell = 5) {})
    }

    private fun playing(
        autoMarks: Set<Int> = emptySet(),
        manualMarks: Set<Int> = emptySet(),
        wrongGuesses: Set<Int> = emptySet(),
        clearedMarks: Set<Int> = emptySet(),
        placed: Solution = Solution.empty(4),
    ) = GameState(
        clearedMarks = clearedMarks,
        level = LevelDefinition(
            id = 1,
            board = Board.parse("AABBAABBCCDDCCDD"),
            solution = Solution.empty(4),
            difficulty = 1,
        ),
        phase = GamePhase.Playing,
        placed = placed,
        autoMarks = autoMarks,
        manualMarks = manualMarks,
        wrongGuesses = wrongGuesses,
    )
}
