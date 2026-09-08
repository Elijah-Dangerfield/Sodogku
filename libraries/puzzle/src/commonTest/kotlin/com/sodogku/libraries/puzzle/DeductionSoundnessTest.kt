package com.sodogku.libraries.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        minSize: Int = Board.MIN_SIZE,
        maxSize: Int = PROPERTY_MAX_SIZE,
        block: (Board, Solution) -> Unit,
    ) {
        val random = Random(seed)
        var built = 0
        var attempts = 0
        while (built < boards && attempts < boards * ATTEMPT_SLACK) {
            attempts++
            val size = minSize + random.nextInt(maxSize - minSize + 1)
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
            val ruledOut = HintFinder.ruledOutCells(board, placed, limit = HINT_LIMIT)
            assertTrue(
                ruledOut.none { it in truth },
                "hint ruled out ${ruledOut.filter { c -> c in truth }} from the answer\n$board",
            )
            val next = HintFinder.nextCell(board, placed) ?: return@forEachUniqueBoard
            placed = placed.withPlacement(board.rowOf(next), board.colOf(next))
        }
    }

    @Test
    fun aHintAlwaysHasSomethingToSay() = forEachUniqueBoard(seed = 5150, boards = 25) { board, _ ->
        // The regression this exists for cost a paid consumable: on an easy board
        // the engine solves by placement alone and announces no eliminations, so
        // the first version returned nothing and a sniff was spent for no visible
        // effect. Every assertion around it passed on an empty list, which is why
        // it reached a device before it was caught.
        //
        // Only the opening half is asserted. Late in a board the auto-marks have
        // already crossed off everything derivable — measured: 5 of 7 placed with
        // one candidate left per open row — so there is genuinely nothing a hint
        // could add, and the ViewModel refuses to spend a sniff rather than
        // pretending otherwise. Demanding a reveal there would be demanding a lie.
        var placed = Solution.empty(board.size)

        repeat(board.size / 2) {
            assertTrue(
                HintFinder.ruledOutCells(board, placed, limit = HINT_LIMIT).isNotEmpty(),
                "nothing to rule out with ${placed.placedCount} of ${board.size} placed\n$board",
            )
            val next = HintFinder.nextCell(board, placed) ?: return@forEachUniqueBoard
            placed = placed.withPlacement(board.rowOf(next), board.colOf(next))
        }
    }

    @Test
    fun aHintRespectsItsLimit() {
        val (board, _) = assertNotNull(Fixtures.uniqueBoard(7, Random(4242)))

        val ruledOut = HintFinder.ruledOutCells(board, Solution.empty(board.size), limit = 3)

        // Both halves. Asserting only `<= 3` passes on an empty list, which is
        // the failure that actually happened.
        assertEquals(3, ruledOut.size, "asked for 3, got ${ruledOut.size}")
    }

    @Test
    fun aFinishedBoardHasNothingLeftToRuleOut() {
        val (board, solution) = assertNotNull(Fixtures.uniqueBoard(6, Random(99)))

        assertTrue(HintFinder.ruledOutCells(board, solution, limit = HINT_LIMIT).isEmpty())
    }

    @Test
    fun aHintSaysNothingRatherThanReasonFromADeadEnd() {
        // Legal so far, but unwinnable: no completion of this board keeps the dog
        // at r3c3. Every technique below is sound, so reasoning on from here
        // produces confident, correct-looking conclusions about a false premise,
        // and one of them crosses out a square the answer occupies.
        val board = Board.parse("BAAC" + "BBBC" + "DDDC" + "DDDC")
        val placed = Solution.empty(4).withPlacement(3, 3)

        assertEquals(emptyList(), board.ruleViolations(placed), "the fixture should break no rule")
        assertEquals(0, PuzzleSolver.countSolutions(board, placed, limit = 1), "and have no completion")

        assertEquals(emptyList(), HintFinder.ruledOutCells(board, placed, limit = HINT_LIMIT))
        assertNull(HintFinder.nextCell(board, placed))
    }

    @Test
    fun everyTechniqueIsSoundOnTheLargestBoardsWeShip() {
        // The tier-2 adjacency bug fixed on 2026-09-07 got worse the more rows
        // were resolved, and big boards resolve more rows. Soundness is not
        // obviously size-independent, so the sizes that actually ship are checked
        // here rather than assumed. Few boards, because 10x10 generation is slow.
        val random = Random(31337)
        listOf(9, Board.MAX_SIZE).forEach { size ->
            val (board, solution) = assertNotNull(
                Fixtures.uniqueLargeBoard(size, random),
                "could not generate a unique ${size}x$size board",
            )
            val truth = solution.cells().toSet()
            val grid = CandidateGrid(board)
            var steps = 0

            while (!grid.isSolved && !grid.isContradicted && steps++ < MAX_STEPS) {
                when (val step = DeductionEngine.nextStep(grid) ?: break) {
                    is Deduction.Place -> {
                        assertTrue(step.cell in truth, "placed ${describe(board, step.cell)} off-answer\n$board")
                        grid.place(step.cell)
                    }
                    is Deduction.Eliminate -> step.cells.forEach { cell ->
                        assertTrue(cell !in truth, "eliminated ${describe(board, cell)} from the answer\n$board")
                        grid.eliminate(cell)
                    }
                }
            }
        }
    }

    private companion object {
        /**
         * Default cap for the property tests, which run the tier-4 technique
         * (quadratic in candidates) on every board. The shipped sizes are not
         * skipped on the assumption that soundness is size-independent —
         * `everyTechniqueIsSoundOnTheLargestBoardsWeShip` covers 9x9 and 10x10
         * explicitly, at a board count that keeps the suite fast.
         */
        const val PROPERTY_MAX_SIZE = 8

        /** What the game actually asks a sniff for. Nothing here uses the whole board. */
        const val HINT_LIMIT = 3

        const val MAX_STEPS = 2_000
        const val ATTEMPT_SLACK = 4
    }
}