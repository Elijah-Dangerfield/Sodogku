package com.sodogku.libraries.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PuzzleSolverTest {

    @Test
    fun solve_returnsAPlacementThatBreaksNoRule() {
        val board = Fixtures.uniqueFourByFour()

        val solution = assertNotNull(PuzzleSolver.solve(board))

        assertTrue(board.isSolvedBy(solution), board.ruleViolations(solution).toString())
    }

    @Test
    fun countSolutions_agreesWithBruteForceEnumeration() {
        // The solver prunes; this enumerates every column permutation and
        // filters with `ruleViolations`, which shares no code with the search.
        // If the pruning is wrong these disagree.
        listOf(
            Fixtures.uniqueFourByFour(),
            Fixtures.ambiguousFourByFour(),
            Fixtures.rowsAsRegions(5),
            Fixtures.columnsAsRegions(5),
        ).forEach { board ->
            val expected = bruteForceSolutions(board).size

            val actual = PuzzleSolver.countSolutions(board, limit = Int.MAX_VALUE)

            assertEquals(expected, actual, "disagreement on:\n$board")
        }
    }

    @Test
    fun countSolutions_stopsAtTheLimit() {
        val board = Fixtures.rowsAsRegions(6)
        assertTrue(bruteForceSolutions(board).size > 2, "fixture must be ambiguous to test the cap")

        assertEquals(2, PuzzleSolver.countSolutions(board, limit = 2))
        assertEquals(1, PuzzleSolver.countSolutions(board, limit = 1))
    }

    @Test
    fun uniqueSolutionOrNull_returnsTheAnswerWhenThereIsExactlyOne() {
        val board = Fixtures.uniqueFourByFour()

        val solution = assertNotNull(PuzzleSolver.uniqueSolutionOrNull(board))

        assertEquals(bruteForceSolutions(board).single(), solution)
    }

    @Test
    fun uniqueSolutionOrNull_isNullWhenTheBoardIsAmbiguous() {
        assertNull(PuzzleSolver.uniqueSolutionOrNull(Fixtures.ambiguousFourByFour()))
    }

    @Test
    fun uniqueSolutionOrNull_isNullWhenThereIsNoSolutionAtAll() {
        val board = Fixtures.unsolvableFourByFour()
        assertTrue(bruteForceSolutions(board).isEmpty())

        assertNull(PuzzleSolver.uniqueSolutionOrNull(board))
        assertNull(PuzzleSolver.solve(board))
        assertEquals(0, PuzzleSolver.countSolutions(board))
    }

    @Test
    fun columnsAsRegionsConstrainNothing_soTheyMatchRowsAsRegions() {
        // Worth pinning: one dog per column already implies one per region when
        // the regions *are* the columns, so this fixture is a no-op constraint
        // and any change that makes it look constrained is a bug in the solver.
        assertEquals(
            PuzzleSolver.countSolutions(Fixtures.rowsAsRegions(5), limit = Int.MAX_VALUE),
            PuzzleSolver.countSolutions(Fixtures.columnsAsRegions(5), limit = Int.MAX_VALUE),
        )
    }

    @Test
    fun givenPlacements_constrainTheSearch() {
        val board = Fixtures.rowsAsRegions(6)
        val all = bruteForceSolutions(board)
        val pinnedColumn = all.first()[0]
        val expected = all.count { it[0] == pinnedColumn }

        val given = Solution.empty(6).withPlacement(0, pinnedColumn)

        assertEquals(expected, PuzzleSolver.countSolutions(board, given, limit = Int.MAX_VALUE))
    }

    @Test
    fun givenPlacements_thatAlreadyBreakARuleYieldNothing() {
        val board = Fixtures.rowsAsRegions(6)
        val touching = Solution.empty(6)
            .withPlacement(0, 2)
            .withPlacement(1, 3)

        assertEquals(0, PuzzleSolver.countSolutions(board, touching))
        assertNull(PuzzleSolver.solve(board, touching))
        assertFalse(PuzzleSolver.isSatisfiable(board, touching))
    }

    @Test
    fun everyGeneratedBoardSolvesToItsSeedPlacement() {
        val random = Random(20260907)
        repeat(60) {
            val size = Board.MIN_SIZE + random.nextInt(Board.MAX_SIZE - Board.MIN_SIZE + 1)
            val seed = assertNotNull(BoardFactory.randomSolution(size, random))
            val board = BoardFactory.growRegions(seed, random)

            assertTrue(board.structuralProblems().isEmpty(), board.structuralProblems().toString())
            assertTrue(board.isSolvedBy(seed), "seed must satisfy the board it generated")
            assertTrue(
                PuzzleSolver.isSatisfiable(board, Solution.empty(size)),
                "a constructed board always has at least the seed solution",
            )
        }
    }

    /**
     * Independent oracle: every permutation of columns, filtered by the public
     * rule checker. Exponential, so only ever called on small fixtures.
     */
    private fun bruteForceSolutions(board: Board): List<Solution> =
        permutations((0 until board.size).toList())
            .map { Solution(it.toIntArray()) }
            .filter { board.ruleViolations(it).isEmpty() }
            .toList()

    private fun <T> permutations(items: List<T>): Sequence<List<T>> = sequence {
        if (items.size <= 1) {
            yield(items)
            return@sequence
        }
        items.indices.forEach { index ->
            val rest = items.toMutableList().also { it.removeAt(index) }
            permutations(rest).forEach { yield(listOf(items[index]) + it) }
        }
    }
}
