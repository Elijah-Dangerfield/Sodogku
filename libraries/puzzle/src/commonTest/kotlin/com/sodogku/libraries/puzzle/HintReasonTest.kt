package com.sodogku.libraries.puzzle

import com.sodogku.libraries.puzzle.HintFinder.RuledOut
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a hint is allowed to claim proved it.
 *
 * A crossed square the player cannot account for is worse than no hint, because
 * the lesson they take is that the explanation is decoration. So two things have
 * to hold: every square carries the reasoning that actually shut it, and the one
 * sentence the sheet shows is true of every square it lit.
 */
class HintReasonTest {

    /**
     * A 7x7 whose opening is worth the region string.
     *
     * From the player's two dogs the shallowest thing available is *not* the
     * shallowest technique: the first step is a tier-2 [Technique.GroupConfinement]
     * eliminating exactly four squares, and only after those does the engine
     * reach a forced placement. So the board separates "the technique that shut
     * this square" from "the technique that shut most of them", which is the
     * distinction the whole file is about, and it does it without needing a
     * mocked engine.
     */
    private fun openingBoard(): Pair<Board, Solution> {
        val board = Board.parse("BGGCACCBGGCCCCDDGCCCCDDGCCCCDDGEEECGGGGFFCGGGGFFC")
        val placed = Solution.empty(board.size)
            .withPlacement(0, 4)
            .withPlacement(1, 0)
        assertEquals(emptyList(), board.ruleViolations(placed), "the fixture broke a rule")
        return board to placed
    }

    @Test
    fun aSquareCarriesTheStepThatShutIt() {
        val (board, placed) = openingBoard()
        // Derived rather than written down: the fixture's first step is the
        // premise of the test, so it is checked here instead of trusted.
        val grid = CandidateGrid.of(board, placed)
        val open = (0 until board.cellCount).filter { grid.isCandidate(it) }.toSet()
        val first = assertNotNull(DeductionEngine.nextStep(grid))
        when (first) {
            is Deduction.Place -> grid.place(first.cell)
            is Deduction.Eliminate -> first.cells.forEach { grid.eliminate(it) }
        }
        val shutFirst = open.filter { !grid.isCandidate(it) && !grid.isPlaced(it) }.toSet()
        assertTrue(shutFirst.isNotEmpty(), "the first step shut nothing, so there is nothing to attribute")

        val ruledOut = HintFinder.ruledOutCells(board, placed, limit = WHOLE_BATCH)
        val filed = ruledOut.associate { it.cell to it.technique }

        shutFirst.forEach { cell ->
            assertEquals(
                first.technique,
                filed[cell],
                "r${board.rowOf(cell)}c${board.colOf(cell)} was shut by ${first.technique}",
            )
        }
        // The other half, and the half that does the work. Deduction re-proves a
        // shut square on every step that follows, so a batch that ends in a
        // different technique is what separates "the step that shut it" from
        // "the last step to touch it" — and the last step is nearly always the
        // deepest reasoning on the board, which is the sentence the player can
        // least follow. Without this the assertions above pass on either.
        assertTrue(
            ruledOut.last().technique != first.technique,
            "every square came from one technique, so attribution was never tested",
        )
    }

    @Test
    fun theReasonLightsOnlyTheSquaresItProved() {
        val (board, placed) = openingBoard()
        val ruledOut = HintFinder.ruledOutCells(board, placed, limit = WHOLE_BATCH)
        // The fixture's batch is 9 forced-placement squares against 8 from
        // confinement and 2 from adjacency, so all three are on offer and the
        // biggest is not the one the opening step produced.
        assertEquals(
            mapOf(
                Technique.GroupConfinement to 8,
                Technique.AdjacencyConfinement to 2,
                Technique.LastCandidateInGroup to 9,
            ),
            ruledOut.groupingBy { it.technique }.eachCount(),
        )

        val reason = assertNotNull(HintFinder.strongestReason(ruledOut, limit = SHEET))

        assertEquals(Technique.LastCandidateInGroup, reason.technique)
        assertEquals(SHEET, reason.cells.size)
        assertEquals(
            emptySet(),
            reason.cells - ruledOut.filter { it.technique == reason.technique }.map { it.cell }.toSet(),
            "the reveal lit squares ${reason.technique} did not prove",
        )
    }

    @Test
    fun theReasonIsWhicheverAccountsForMostSquares() {
        // Not the shallowest available. A tier-4 sentence about four squares
        // teaches more than a tier-1 sentence about one.
        val reason = assertNotNull(
            HintFinder.strongestReason(
                batch(Technique.LastCandidateInGroup, 2) + batch(Technique.Contradiction, 3),
                limit = SHEET,
            ),
        )

        assertEquals(Technique.Contradiction, reason.technique)
        assertEquals(3, reason.cells.size)
    }

    @Test
    fun squaresAreCountedAsTheyWillBeShown() {
        // Nine squares and four squares are the same offer through a sheet that
        // shows four. Counting what was *found* rather than what will be lit
        // would spend the difference on the deeper technique for nothing.
        val reason = assertNotNull(
            HintFinder.strongestReason(
                batch(Technique.Contradiction, 9) + batch(Technique.LastCandidateInGroup, 4),
                limit = SHEET,
            ),
        )

        assertEquals(Technique.LastCandidateInGroup, reason.technique)
    }

    @Test
    fun anEvenMatchGoesToTheShallowerReasoning() {
        val reason = assertNotNull(
            HintFinder.strongestReason(
                batch(Technique.NakedSet, SHEET) + batch(Technique.GroupConfinement, SHEET),
                limit = SHEET,
            ),
        )

        assertEquals(Technique.GroupConfinement, reason.technique)
    }

    @Test
    fun nothingRuledOutIsNoReasonRatherThanAnEmptyOne() {
        // The ViewModel spends the charge on a non-null answer, so an empty
        // reason here is a sniff taken for a blank sheet.
        assertNull(HintFinder.strongestReason(emptyList(), limit = SHEET))
    }

    @Test
    fun aSheetWithNoRoomShowsNoReason() {
        assertNull(HintFinder.strongestReason(batch(Technique.LastCandidateInGroup, 3), limit = 0))
    }

    @Test
    fun theReasonNeverLightsASquareTheAnswerOccupies() {
        // `aHintNeverRulesOutASquareTheAnswerOccupies` guards the raw batch. This
        // guards the set the player is actually shown, which is a different list
        // built by a different function.
        val random = Random(20260910)
        var built = 0
        var attempts = 0
        var explained = 0
        while (built < BOARDS && attempts < BOARDS * ATTEMPT_SLACK) {
            attempts++
            val (board, solution) = Fixtures.uniqueBoard(5 + random.nextInt(3), random) ?: continue
            built++
            val truth = solution.cells().toSet()
            var placed = Solution.empty(board.size)

            repeat(board.size) {
                val ruledOut = HintFinder.ruledOutCells(board, placed, limit = WHOLE_BATCH)
                HintFinder.strongestReason(ruledOut, limit = SHEET)?.let { reason ->
                    explained++
                    assertTrue(
                        reason.cells.none { it in truth },
                        "${reason.technique} lit ${reason.cells.filter { c -> c in truth }} from the answer\n$board",
                    )
                }
                val next = HintFinder.nextCell(board, placed) ?: return@repeat
                placed = placed.withPlacement(board.rowOf(next), board.colOf(next))
            }
        }
        assertTrue(explained > BOARDS, "only $explained hints had anything to explain")
    }

    /** [count] squares with distinct ids, all filed under [technique]. */
    private fun batch(technique: Technique, count: Int): List<RuledOut> =
        (0 until count).map { RuledOut(technique.ordinal * OFFSET + it, technique) }

    private companion object {
        /** What the sniff sheet has room for. */
        const val SHEET = 4

        /** Everything the engine can reach, so nothing is lost to truncation. */
        const val WHOLE_BATCH = 100

        const val BOARDS = 15
        const val ATTEMPT_SLACK = 4

        /** Keeps two synthetic batches from sharing a cell id. */
        const val OFFSET = 100
    }
}
