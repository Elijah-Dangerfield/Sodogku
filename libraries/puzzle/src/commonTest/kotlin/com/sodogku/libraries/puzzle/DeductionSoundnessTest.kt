package com.sodogku.libraries.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The test that matters most in this module.
 *
 * Every technique in [DeductionEngine] is an assertion about what a player can
 * *validly* conclude. An unsound one does not fail loudly: it quietly eliminates
 * a cell that was part of the answer, and the damage shows up as a mis-scored
 * difficulty, a hint pointing at the wrong square, or a level the generator
 * accepted that no reasoning can actually solve.
 *
 * So: drive the engine over many random unique-solution boards and check the one
 * invariant that has to hold at every step — the engine never eliminates a cell
 * the unique solution occupies, and never places a dog anywhere else.
 */
class DeductionSoundnessTest {

    @Test
    fun theEngineNeverContradictsTheUniqueSolution() {
        forEachUniqueBoard(seed = 90720261, boards = 40) { board, solution ->
            val truth = solution.cells().toSet()
            val grid = CandidateGrid(board)

            var steps = 0
            while (!grid.isSolved && steps < MAX_STEPS) {
                val step = DeductionEngine.nextStep(grid) ?: break
                steps++
                when (step) {
                    is Deduction.Place -> {
                        assertTrue(
                            step.cell in truth,
                            "${step.technique} placed ${describe(board, step.cell)}, " +
                                "which is not in the only solution\n$board",
                        )
                        grid.place(step.cell)
                    }
                    is Deduction.Eliminate -> {
                        step.cells.forEach { cell ->
                            assertTrue(
                                cell !in truth,
                                "${step.technique} eliminated ${describe(board, cell)}, " +
                                    "which the only solution occupies\n$board",
                            )
                            grid.eliminate(cell)
                        }
                    }
                }
            }
            assertTrue(steps < MAX_STEPS, "engine failed to converge on\n$board")
        }
    }

    @Test
    fun autoMarkNeverHidesACellTheSolutionNeeds() {
        forEachUniqueBoard(seed = 12071999, boards = 30) { board, solution ->
            val truth = solution.cells().toSet()

            (0 until board.size).forEach { row ->
                val partial = Solution.empty(board.size).withPlacement(row, solution[row])
                val marked = board.autoMarkedCells(partial)

                assertTrue(
                    marked.none { it in truth },
                    "auto-mark from row $row hid part of the solution\n$board",
                )
            }
        }
    }

    @Test
    fun aSolvedBoardHasEveryCellPlacedOrEliminated() {
        forEachUniqueBoard(seed = 5150, boards = 20) { board, solution ->
            val grid = CandidateGrid.of(board, solution)

            val undecided = (0 until board.cellCount).filter { grid.isCandidate(it) }

            assertTrue(undecided.isEmpty(), "cells left undecided: $undecided\n$board")
        }
    }

    @Test
    fun hintsAlwaysPointAtTheRealAnswer() {
        forEachUniqueBoard(seed = 31415926, boards = 30) { board, solution ->
            val truth = solution.cells().toSet()
            var placed = Solution.empty(board.size)

            while (!placed.isComplete) {
                val hint = assertNotNull(
                    HintFinder.nextCell(board, placed),
                    "hint dried up with ${placed.placedCount}/${board.size} placed\n$board",
                )
                assertTrue(hint in truth, "hint pointed at ${describe(board, hint)}\n$board")
                placed = placed.withPlacement(board.rowOf(hint), board.colOf(hint))
            }
        }
    }

    @Test
    fun hintsStopOnceTheBoardIsFinished() {
        forEachUniqueBoard(seed = 271828, boards = 10) { board, solution ->
            assertTrue(HintFinder.nextCell(board, solution) == null)
        }
    }

    private fun forEachUniqueBoard(
        seed: Int,
        boards: Int,
        block: (Board, Solution) -> Unit,
    ) {
        val random = Random(seed)
        var built = 0
        var attempts = 0
        while (built < boards && attempts < boards * ATTEMPT_SLACK) {
            attempts++
            val size = Board.MIN_SIZE + random.nextInt(PROPERTY_MAX_SIZE - Board.MIN_SIZE + 1)
            val (board, solution) = Fixtures.uniqueBoard(size, random) ?: continue
            built++
            block(board, solution)
        }
        assertTrue(built >= boards, "only built $built unique boards in $attempts attempts")
    }

    private fun describe(board: Board, cell: Int): String =
        "r${board.rowOf(cell)}c${board.colOf(cell)}"

    @Test
    fun aHintNeverRulesOutASquareTheAnswerOccupies() = forEachUniqueBoard(seed = 8675309, boards = 30) { board, solution ->
        // The failure this guards is quiet and expensive: a hint that crosses off
        // a square the dog actually belongs on makes the puzzle unsolvable and
        // tells the player their own reasoning was wrong.
        val truth = solution.cells().toSet()
        var placed = Solution.empty(board.size)

        repeat(board.size) {
            val ruledOut = HintFinder.ruledOutCells(board, placed)
            assertTrue(
                ruledOut.none { it in truth },
                "hint ruled out ${ruledOut.filter { c -> c in truth }} from the answer\n$board",
            )
            val next = HintFinder.nextCell(board, placed) ?: return@repeat
            placed = placed.withPlacement(board.rowOf(next), board.colOf(next))
        }
    }

    @Test
    fun aHintRespectsItsLimit() {
        val (board, _) = assertNotNull(Fixtures.uniqueBoard(7, Random(4242)))

        val ruledOut = HintFinder.ruledOutCells(board, Solution.empty(board.size), limit = 3)

        assertTrue(ruledOut.size <= 3, "asked for 3, got ${ruledOut.size}")
    }

    @Test
    fun aFinishedBoardHasNothingLeftToRuleOut() {
        val (board, solution) = assertNotNull(Fixtures.uniqueBoard(6, Random(99)))

        assertTrue(HintFinder.ruledOutCells(board, solution).isEmpty())
    }

    private companion object {
        /**
         * Capped below [Board.MAX_SIZE]: the property tests run the tier-4
         * technique, which is quadratic in candidates, on every board. The
         * soundness of a technique does not depend on grid size, so paying for
         * 10x10 here buys nothing the pack verification in C2 will not cover.
         */
        const val PROPERTY_MAX_SIZE = 8

        const val MAX_STEPS = 2_000
        const val ATTEMPT_SLACK = 4
    }
}