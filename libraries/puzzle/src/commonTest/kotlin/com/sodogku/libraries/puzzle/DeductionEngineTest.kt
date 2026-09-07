package com.sodogku.libraries.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeductionEngineTest {

    @Test
    fun aGroupWithOneCandidateLeftIsTheShallowestDeduction() {
        val board = Fixtures.rowsAsRegions(6)
        val grid = CandidateGrid(board)
        (0 until 5).forEach { grid.eliminate(board.cellAt(0, it)) }

        val step = assertNotNull(DeductionEngine.nextStep(grid))

        assertEquals(Technique.LastCandidateInGroup, step.technique)
        assertEquals(Deduction.Place(board.cellAt(0, 5), Technique.LastCandidateInGroup), step)
    }

    @Test
    fun shallowerTechniquesAreAlwaysPreferred() {
        // Difficulty is "the deepest technique needed", which is only meaningful
        // if the engine never reaches for a deep one while a shallow one applies.
        val random = Random(8080)
        var checked = 0
        repeat(200) {
            val (board, _) = Fixtures.uniqueBoard(6, random) ?: return@repeat
            val grid = CandidateGrid(board)
            while (!grid.isSolved) {
                val step = DeductionEngine.nextStep(grid) ?: break
                val shallower = DeductionEngine.nextStep(grid, maxTier = step.technique.tier - 1)
                assertNull(
                    shallower,
                    "chose ${step.technique} while ${shallower?.technique} was available\n$board",
                )
                checked++
                when (step) {
                    is Deduction.Place -> grid.place(step.cell)
                    is Deduction.Eliminate -> step.cells.forEach { grid.eliminate(it) }
                }
            }
            if (checked > 300) return
        }
        assertTrue(checked > 0, "no deductions were exercised")
    }

    @Test
    fun capping_maxTier_stopsTheEngineEarly() {
        val random = Random(4004)
        val (board, _) = assertNotNull(Fixtures.uniqueBoard(7, random))

        val shallow = DeductionEngine.run(CandidateGrid(board), maxTier = 1)
        val full = DeductionEngine.run(CandidateGrid(board))

        assertTrue(shallow.hardestTier <= 1)
        assertTrue(
            full.hardestTier >= shallow.hardestTier,
            "the uncapped run cannot be shallower than the capped one",
        )
    }

    @Test
    fun run_reportsContradictionForABoardWithNoSolution() {
        val outcome = DeductionEngine.run(CandidateGrid(Fixtures.unsolvableFourByFour())).outcome

        assertTrue(
            outcome == DeductionOutcome.Contradicted || outcome == DeductionOutcome.Stuck,
            "expected the engine to fail on an unsolvable board, got $outcome",
        )
    }

    @Test
    fun run_solvesTheUniqueFixtureAndCountsItsSteps() {
        val run = DeductionEngine.run(CandidateGrid(Fixtures.uniqueFourByFour()))

        assertEquals(DeductionOutcome.Solved, run.outcome)
        assertTrue(run.steps > 0)
    }

    @Test
    fun run_onAnAlreadySolvedBoardDoesNothing() {
        val board = Fixtures.uniqueFourByFour()
        val grid = CandidateGrid.of(board, Fixtures.FOUR_BY_FOUR_B)

        val run = DeductionEngine.run(grid)

        assertEquals(DeductionOutcome.Solved, run.outcome)
        assertEquals(0, run.steps)
        assertEquals(0, run.hardestTier)
    }
}
