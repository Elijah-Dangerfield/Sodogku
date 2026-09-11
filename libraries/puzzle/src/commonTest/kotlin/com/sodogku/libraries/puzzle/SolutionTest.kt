package com.sodogku.libraries.puzzle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules themselves, stated once so everything else can be checked against
 * them.
 *
 * `ruleViolations` is the definition of a legal placement in this game, and the
 * solver, the generator and the board all defer to it. Each of the four ways to
 * break it gets its own test, matched on the message rather than the count, so
 * a checker that reports the wrong reason fails rather than passing on a
 * coincidence.
 *
 * Two properties matter more than the four rules. A partial placement is judged
 * only on what is placed, which is what lets a half-finished board be valid and
 * unsolved at the same time. And rows two apart can never touch whatever
 * columns they use, which is the fact that lets the solver reduce adjacency to
 * one comparison per consecutive pair instead of a sweep.
 *
 * The rest is the value type: placements are copies, an index is row-major, and
 * clearing a row leaves it unplaced rather than at column zero.
 *
 * ### Not here
 *
 * Region shape and contiguity belong to the board, in `BoardTest`. Counting how
 * many placements satisfy these rules is `PuzzleSolverTest`.
 */
class SolutionTest {

    private val board = Fixtures.uniqueFourByFour()

    @Test
    fun ruleViolations_areEmptyForTheRealSolution() {
        assertTrue(board.ruleViolations(Fixtures.FOUR_BY_FOUR_B).isEmpty())
        assertTrue(board.isSolvedBy(Fixtures.FOUR_BY_FOUR_B))
    }

    @Test
    fun ruleViolations_reportARepeatedColumn() {
        val violations = Fixtures.rowsAsRegions(4)
            .ruleViolations(Solution(intArrayOf(0, 2, 0, 3)))

        assertTrue(violations.any { it.contains("columns used more than once") }, "$violations")
    }

    @Test
    fun ruleViolations_reportARepeatedRegion() {
        val violations = board.ruleViolations(Fixtures.FOUR_BY_FOUR_A)

        assertTrue(violations.any { it.contains("regions used more than once") }, "$violations")
    }

    @Test
    fun ruleViolations_reportTouchingDogs() {
        val violations = Fixtures.rowsAsRegions(4)
            .ruleViolations(Solution(intArrayOf(0, 1, 2, 3)))

        assertTrue(violations.any { it.contains("dogs touch") }, "$violations")
    }

    @Test
    fun ruleViolations_reportColumnsOutOfRange() {
        val violations = Fixtures.rowsAsRegions(4)
            .ruleViolations(Solution(intArrayOf(0, 2, 9, 3)))

        assertTrue(violations.any { it.contains("out of range") }, "$violations")
    }

    @Test
    fun aPartialSolutionIsJudgedOnlyOnWhatIsPlaced() {
        val partial = Solution.empty(4).withPlacement(0, 1)

        assertTrue(board.ruleViolations(partial).isEmpty())
        assertFalse(board.isSolvedBy(partial), "incomplete is not solved")
    }

    @Test
    fun nonAdjacentRowsCannotTouchAcrossAGap() {
        // Rows 0 and 2 are two apart vertically, so no column pairing makes
        // them touch. Only consecutive rows can, which is what lets the solver
        // reduce adjacency to one comparison.
        val skipping = Solution.empty(4)
            .withPlacement(0, 0)
            .withPlacement(2, 1)

        assertTrue(Fixtures.rowsAsRegions(4).ruleViolations(skipping).isEmpty())
    }

    @Test
    fun cells_areRowMajorIndicesOfPlacedRowsOnly() {
        val partial = Solution.empty(4).withPlacement(1, 3).withPlacement(3, 0)

        assertContentEquals(intArrayOf(7, 12), partial.cells())
        assertEquals(2, partial.placedCount)
    }

    @Test
    fun withPlacement_doesNotMutateTheOriginal() {
        val original = Solution.empty(4)

        original.withPlacement(0, 2)

        assertEquals(0, original.placedCount)
    }

    @Test
    fun withoutPlacement_clearsARow() {
        val cleared = Fixtures.FOUR_BY_FOUR_B.withoutPlacement(2)

        assertEquals(Solution.UNPLACED, cleared[2])
        assertEquals(3, cleared.placedCount)
    }
}
