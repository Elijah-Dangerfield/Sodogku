package com.sodogku.libraries.puzzle

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardFactoryTest {

    @Test
    fun randomSolution_obeysTheAdjacencyRule() {
        val random = Random(2026)
        (Board.MIN_SIZE..Board.MAX_SIZE).forEach { size ->
            repeat(20) {
                val solution = assertNotNull(BoardFactory.randomSolution(size, random))

                assertEquals(size, solution.columnByRow.toSet().size, "columns must be a permutation")
                (1 until size).forEach { row ->
                    assertTrue(
                        abs(solution[row] - solution[row - 1]) >= 2,
                        "rows ${row - 1} and $row touch in $solution",
                    )
                }
            }
        }
    }

    @Test
    fun randomSolution_refusesSizesWithNoLegalPlacement() {
        listOf(2, 3).forEach { size ->
            val failure = runCatching { BoardFactory.randomSolution(size, Random(1)) }
            assertTrue(failure.isFailure, "size $size should be rejected outright")
        }
    }

    @Test
    fun growRegions_producesContiguousRegionsHoldingExactlyOneDogEach() {
        val random = Random(555)
        (Board.MIN_SIZE..Board.MAX_SIZE).forEach { size ->
            repeat(15) {
                val seed = assertNotNull(BoardFactory.randomSolution(size, random))
                val board = BoardFactory.growRegions(seed, random)

                assertTrue(board.structuralProblems().isEmpty(), board.structuralProblems().toString())
                assertEquals(
                    size,
                    seed.cells().map { board.regionAt(it) }.toSet().size,
                    "every dog must land in its own region\n$board",
                )
                assertTrue(board.isSolvedBy(seed), "the seed must solve the board grown around it")
            }
        }
    }

    @Test
    fun growRegions_coversEveryCell() {
        val random = Random(777)
        val seed = assertNotNull(BoardFactory.randomSolution(9, random))

        val board = BoardFactory.growRegions(seed, random)

        assertTrue(board.regions.all { it in 0 until board.size }, "found unassigned cells")
    }

    @Test
    fun mutateRegions_actuallyMovesCells() {
        // Pinned because a mutation primitive that silently no-ops looks
        // identical to a generator strategy that does not converge, and the
        // two want completely different fixes.
        val random = Random(31337)
        val seed = assertNotNull(BoardFactory.randomSolution(8, random))
        val board = BoardFactory.growRegions(seed, random)

        val mutated = BoardFactory.mutateRegions(board, seed, random, moves = 5)

        val moved = board.regions.indices.count { board.regions[it] != mutated.regions[it] }
        assertTrue(moved > 0, "mutation changed nothing")
    }

    @Test
    fun mutateRegions_neverBreaksTheInvariants() {
        val random = Random(24680)
        repeat(40) {
            val size = Board.MIN_SIZE + random.nextInt(5)
            val seed = assertNotNull(BoardFactory.randomSolution(size, random))
            var board = BoardFactory.growRegions(seed, random)

            repeat(10) {
                board = BoardFactory.mutateRegions(board, seed, random)

                assertTrue(
                    board.structuralProblems().isEmpty(),
                    "mutation broke the partition: ${board.structuralProblems()}\n$board",
                )
                assertTrue(
                    board.isSolvedBy(seed),
                    "mutation orphaned the seed solution\n$board",
                )
            }
        }
    }
}
