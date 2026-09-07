package com.sodogku.libraries.puzzle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoardTest {

    @Test
    fun parse_readsRowMajorLetters() {
        val board = Board.parse(
            "AABB" +
                "AABB" +
                "CCDD" +
                "CCDD",
        )

        assertEquals(4, board.size)
        assertEquals(0, board.regionAt(0, 0))
        assertEquals(1, board.regionAt(0, 3))
        assertEquals(2, board.regionAt(3, 0))
        assertEquals(3, board.regionAt(3, 3))
    }

    @Test
    fun parse_rejectsNonSquareLength() {
        assertFailsWith<IllegalStateException> { Board.parse("AAB") }
    }

    @Test
    fun parse_rejectsUnknownLetter() {
        assertFailsWith<IllegalArgumentException> { Board.parse("AABZAABBCCDDCCDD") }
    }

    @Test
    fun construction_rejectsBoardsSmallerThanTheSmallestSolvableOne() {
        assertFailsWith<IllegalArgumentException> { Board(3, IntArray(9)) }
    }

    @Test
    fun neighbours_includeDiagonalsAndClampAtEdges() {
        val board = squareQuadrants()

        assertEquals(3, board.neighborsOf(board.cellAt(0, 0)).size)
        assertEquals(5, board.neighborsOf(board.cellAt(0, 1)).size)
        assertEquals(8, board.neighborsOf(board.cellAt(1, 1)).size)
    }

    @Test
    fun neighbours_ofACentreCellAreTheEightSurrounding() {
        val board = squareQuadrants()
        val centre = board.cellAt(1, 1)

        val expected = listOf(
            board.cellAt(0, 0), board.cellAt(0, 1), board.cellAt(0, 2),
            board.cellAt(1, 0), board.cellAt(1, 2),
            board.cellAt(2, 0), board.cellAt(2, 1), board.cellAt(2, 2),
        ).sorted()

        assertContentEquals(expected, board.neighborsOf(centre).sorted())
    }

    @Test
    fun structuralProblems_areEmptyForAWellFormedBoard() {
        assertTrue(squareQuadrants().structuralProblems().isEmpty())
    }

    @Test
    fun structuralProblems_reportAnEmptyRegion() {
        val board = Board.parse(
            "AABB" +
                "AABB" +
                "CCBB" +
                "CCBB",
        )

        val problems = board.structuralProblems()

        assertTrue(problems.any { it.contains("empty regions") }, problems.toString())
    }

    @Test
    fun structuralProblems_reportARegionSplitInTwo() {
        val board = Board.parse(
            "ABAB" +
                "CDCD" +
                "ABAB" +
                "CDCD",
        )

        val problems = board.structuralProblems()

        assertTrue(problems.any { it.contains("non-contiguous") }, problems.toString())
    }

    @Test
    fun structuralProblems_doNotCountDiagonalTouchingAsContiguous() {
        val board = Board.parse(
            "AABB" +
                "AABB" +
                "CCDD" +
                "DDCC",
        )

        val problems = board.structuralProblems()

        assertTrue(problems.any { it.contains("non-contiguous") }, problems.toString())
    }

    @Test
    fun toString_roundTripsThroughParse() {
        val board = squareQuadrants()

        assertEquals(board, Board.parse(board.toString().replace("\n", "")))
    }

    private fun squareQuadrants() = Board.parse(
        "AABB" +
            "AABB" +
            "CCDD" +
            "CCDD",
    )
}
