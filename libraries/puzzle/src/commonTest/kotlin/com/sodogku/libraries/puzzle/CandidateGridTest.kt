package com.sodogku.libraries.puzzle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The mutable working state every search and every hint runs on.
 *
 * A placement eliminates its row, its column, its region and all eight
 * neighbours, and those four sweeps are asserted separately because a grid
 * missing one of them still solves most boards and quietly accepts an illegal
 * answer on the rest. Around that: placing twice is idempotent, eliminating
 * twice reports no change, a copy shares nothing with the original, and a group
 * left with neither a dog nor a candidate is a contradiction rather than a
 * silently unsolvable grid.
 *
 * Placing on an already eliminated cell throws, because no caller inside the
 * library can reach that state legitimately. What a *player* does on a crossed
 * off square is a different question entirely and lives in the game feature.
 *
 * `autoMarkedCells` is here too, since it is the same elimination read from the
 * outside. It is what the board draws when assistance is on, so the test covers
 * the cell the player placed staying unmarked and an unrelated cell staying
 * open, which are the two ends a sweep that is too eager gets wrong.
 *
 * ### Not here
 *
 * The search that drives this grid, and how many answers it finds, is
 * `PuzzleSolverTest`. Which deductions a human would reach for is
 * `DeductionEngineTest`.
 */
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
