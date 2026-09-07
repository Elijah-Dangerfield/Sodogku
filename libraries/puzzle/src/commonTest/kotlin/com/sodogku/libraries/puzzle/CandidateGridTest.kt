package com.sodogku.libraries.puzzle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CandidateGridTest {

    private val board = Fixtures.rowsAsRegions(6)

    @Test
    fun placing_eliminatesTheWholeRowColumnAndRegion() {
        val grid = CandidateGrid(board)
        val cell = board.cellAt(2, 3)

        grid.place(cell)

        (0 until board.size).filter { it != 3 }.forEach { col ->
            assertTrue(grid.isEliminated(board.cellAt(2, col)), "row cell c$col survived")
        }
        (0 until board.size).filter { it != 2 }.forEach { row ->
            assertTrue(grid.isEliminated(board.cellAt(row, 3)), "column cell r$row survived")
        }
    }

    @Test
    fun placing_eliminatesAllEightNeighbours() {
        val grid = CandidateGrid(Fixtures.columnsAsRegions(6))
        val cell = board.cellAt(3, 3)

        grid.place(cell)

        board.neighborsOf(cell).forEach {
            assertTrue(grid.isEliminated(it), "neighbour ${it} survived")
        }
    }

    @Test
    fun placing_isIdempotent() {
        val grid = CandidateGrid(board)
        val cell = board.cellAt(1, 4)

        grid.place(cell)
        grid.place(cell)

        assertEquals(1, grid.placedCount)
    }

    @Test
    fun placingOnAnEliminatedCellIsProgrammerError() {
        val grid = CandidateGrid(board)
        grid.place(board.cellAt(0, 0))

        assertFailsWith<IllegalArgumentException> { grid.place(board.cellAt(0, 1)) }
    }

    @Test
    fun eliminate_reportsWhetherItChangedAnything() {
        val grid = CandidateGrid(board)
        val cell = board.cellAt(4, 4)

        assertTrue(grid.eliminate(cell))
        assertFalse(grid.eliminate(cell), "second elimination is a no-op")
    }

    @Test
    fun contradiction_isDetectedWhenAGroupRunsOutOfRoom() {
        val grid = CandidateGrid(board)
        assertFalse(grid.isContradicted)

        (0 until board.size).forEach { grid.eliminate(board.cellAt(0, it)) }

        assertTrue(grid.isContradicted, "row 0 has no placement and no candidates")
    }

    @Test
    fun copy_isIndependentOfTheOriginal() {
        val grid = CandidateGrid(board)
        val copy = grid.copy()

        copy.place(board.cellAt(0, 0))

        assertEquals(0, grid.placedCount)
        assertEquals(1, copy.placedCount)
    }

    @Test
    fun toSolution_roundTripsThroughOf() {
        val solution = Fixtures.FOUR_BY_FOUR_B
        val grid = CandidateGrid.of(Fixtures.uniqueFourByFour(), solution)

        assertEquals(solution, grid.toSolution())
        assertTrue(grid.isSolved)
    }

    @Test
    fun unresolvedGroups_shrinkAsDogsArePlaced() {
        val grid = CandidateGrid(board)
        val before = grid.unresolvedGroups().size

        grid.place(board.cellAt(0, 0))

        // One row, one column and one region all get resolved by a placement.
        assertEquals(before - 3, grid.unresolvedGroups().size)
    }

    @Test
    fun autoMarkedCells_matchWhatTheGridEliminated() {
        val partial = Solution.empty(6).withPlacement(2, 2)

        val marked = board.autoMarkedCells(partial)

        assertTrue(board.cellAt(2, 5) in marked, "same row")
        assertTrue(board.cellAt(5, 2) in marked, "same column")
        assertTrue(board.cellAt(1, 1) in marked, "diagonal neighbour")
        assertFalse(board.cellAt(2, 2) in marked, "the placed cell itself is not a mark")
        assertFalse(board.cellAt(4, 4) in marked, "an unrelated cell stays open")
    }
}
