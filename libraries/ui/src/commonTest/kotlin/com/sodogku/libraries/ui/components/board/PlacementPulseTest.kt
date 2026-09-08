package com.sodogku.libraries.ui.components.board

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which squares react to a placement. Wrong here and the board celebrates the
 * wrong line, which is worse than not celebrating at all — the glow is meant to
 * *say* what the placement resolved.
 */
class PlacementPulseTest {

    private val size = 5

    private fun pulseFor(cell: Int) =
        PlacementPulse.between(before = emptySet(), after = setOf(cell), size = size, nonce = 1)

    @Test
    fun theLandingSquareIsTheOrigin() {
        assertEquals(PlacementRole.Origin, pulseFor(12).roleOf(12))
    }

    @Test
    fun everyOtherSquareInTheRowAndColumnIsOnTheLine() {
        val pulse = pulseFor(12)
        val row = (10..14).filter { it != 12 }
        val column = listOf(2, 7, 17, 22)

        (row + column).forEach {
            assertEquals(PlacementRole.Line, pulse.roleOf(it), "cell $it should be on the line")
        }
    }

    /**
     * The count matters as much as the membership. A `roleOf` that answered
     * `Line` for everything would satisfy the test above and light the whole
     * board.
     */
    @Test
    fun exactlyTwoLinesLightUp() {
        val pulse = pulseFor(12)
        val lit = (0 until size * size).count { pulse.roleOf(it) != PlacementRole.None }
        assertEquals(size + size - 1, lit)
    }

    @Test
    fun aCornerPlacementLightsItsOwnTwoEdges() {
        val pulse = pulseFor(0)
        assertEquals(PlacementRole.Origin, pulse.roleOf(0))
        assertEquals(PlacementRole.Line, pulse.roleOf(4))
        assertEquals(PlacementRole.Line, pulse.roleOf(20))
        assertEquals(PlacementRole.None, pulse.roleOf(6))
    }

    @Test
    fun nothingAddedIsNoPulse() {
        val unchanged = PlacementPulse.between(setOf(3), setOf(3), size, nonce = 7)
        assertEquals(0, unchanged.nonce)
        assertEquals(PlacementRole.None, unchanged.roleOf(3))
    }

    /**
     * A restore, an undo or a board being loaded moves several squares at once.
     * There is no single line to celebrate, and picking one arbitrarily would
     * point the player at a deduction nobody made.
     */
    @Test
    fun severalSquaresAppearingAtOnceIsNotAPlacement() {
        val restored = PlacementPulse.between(emptySet(), setOf(1, 7, 13), size, nonce = 2)
        assertEquals(PlacementRole.None, restored.roleOf(7))
    }

    @Test
    fun aSquareLeavingIsNotAPlacement() {
        val cleared = PlacementPulse.between(setOf(1, 7), setOf(1), size, nonce = 2)
        assertEquals(PlacementRole.None, cleared.roleOf(7))
    }

    /**
     * The nonce is what lets the same square animate twice. A pulse that
     * reported zero — or repeated its previous value — would leave the second
     * placement in a row silent.
     */
    @Test
    fun thePulseCarriesTheNonceItWasGiven() {
        assertEquals(9, PlacementPulse.between(emptySet(), setOf(4), size, nonce = 9).nonce)
    }

    @Test
    fun aZeroNonceMeansNobodyHasPlacedAnythingYet() {
        val pulse = PlacementPulse.between(emptySet(), setOf(12), size, nonce = 0)
        assertEquals(PlacementRole.None, pulse.roleOf(12))
    }
}
