package com.sodogku.libraries.ui.components.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which square a finger is on.
 *
 * The gesture that reads this cannot be unit tested, and every answer that
 * matters here is an edge: the gutter, the far side of the last row, a finger
 * that has left the board mid-stroke. Wrong by one and a drag paints the row
 * above the one under the thumb, which is the kind of bug that reads as the
 * board being possessed.
 */
class BoardGeometryTest {

    /** Four squares of 10px with a 2px gutter: 46px across in total. */
    private val grid = BoardGeometry(size = 4, cellPx = 10f, gapPx = 2f)

    @Test
    fun aPointInTheMiddleOfASquareIsThatSquare() {
        assertEquals(0, grid.cellAt(x = 5f, y = 5f))
        assertEquals(5, grid.cellAt(x = 17f, y = 17f))
        assertEquals(15, grid.cellAt(x = 41f, y = 41f))
    }

    @Test
    fun theSquaresAreNumberedRowMajor() {
        assertEquals(3, grid.cellAt(x = 41f, y = 5f), "the top right square")
        assertEquals(12, grid.cellAt(x = 5f, y = 41f), "the bottom left square")
    }

    @Test
    fun bothEdgesOfASquareBelongToIt() {
        assertEquals(0, grid.cellAt(x = 0f, y = 0f))
        assertEquals(0, grid.cellAt(x = 10f, y = 10f))
    }

    /**
     * The white between two squares is neither of them. Rounding it to the
     * nearer one would let a stroke travelling down a grid line paint a whole
     * column it never touched.
     */
    @Test
    fun theGutterIsNoSquareAtAll() {
        assertNull(grid.cellAt(x = 11f, y = 5f))
        assertNull(grid.cellAt(x = 5f, y = 11f))
    }

    /**
     * A stroke that leaves the board keeps receiving events — the grid captured
     * the pointer. Clamping to the nearest square would paint the outer ring
     * while the finger was somewhere else entirely.
     */
    @Test
    fun aFingerPastTheLastSquareIsOffTheBoard() {
        assertNull(grid.cellAt(x = 48f, y = 5f))
        assertNull(grid.cellAt(x = 5f, y = 48f))
        assertEquals(3, grid.cellAt(x = 46f, y = 5f), "the last square's own far edge")
    }

    @Test
    fun aFingerAboveOrLeftOfTheBoardIsOffItToo() {
        assertNull(grid.cellAt(x = -1f, y = 5f))
        assertNull(grid.cellAt(x = 5f, y = -1f))
    }
}
