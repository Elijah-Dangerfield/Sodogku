package com.sodogku.libraries.puzzle

/**
 * How hard a board is to *reason* about, 1 to 5. Not how big it is: a 10x10
 * that falls to repeated last-candidate is easier than a 6x6 that needs a
 * contradiction, and the shipped level order interleaves the two.
 *
 * The score is the deepest technique the deduction solver had to reach, so it
 * is defined entirely by [Technique.tier]. Retiering a technique re-rates the
 * whole pack, which is why the pack is regenerated rather than hand-edited.
 */
object Difficulty {

    /** Boards the deduction solver cannot finish at all, even with contradictions. */
    const val BEYOND_DEDUCTION: Int = 5

    const val EASIEST: Int = 1

    /**
     * Scores [board] from an empty grid. Returns [BEYOND_DEDUCTION] when the
     * deduction solver gets stuck, which for a unique-solution board means it
     * needs reasoning deeper than one level of hypothesis.
     *
     * Callers should have already established uniqueness: an ambiguous board
     * scores [BEYOND_DEDUCTION] here too, for the entirely different reason
     * that no amount of reasoning determines an answer.
     */
    fun score(board: Board): Int {
        val run = DeductionEngine.run(CandidateGrid(board))
        return when (run.outcome) {
            DeductionOutcome.Solved -> run.hardestTier.coerceAtLeast(EASIEST)
            DeductionOutcome.Stuck, DeductionOutcome.Contradicted -> BEYOND_DEDUCTION
        }
    }
}

/**
 * Picks the cell a hint should reveal.
 *
 * Never a random unsolved cell. It runs the deduction solver forward from the
 * player's current position and returns the first cell the *shallowest*
 * available reasoning proves, so a hint teaches a technique instead of just
 * handing over a square.
 */
object HintFinder {

    /**
     * The cell to reveal given what the player has already placed, or null when
     * the board is finished or the placements have gone wrong.
     *
     * When no chain of deductions reaches a placement (a board needing depth
     * beyond the engine), it falls back to the unique solution and picks the
     * most constrained unresolved row, which is the closest thing to "the cell
     * you were nearest to working out".
     */
    fun nextCell(board: Board, placed: Solution): Int? {
        if (placed.isComplete) return null
        if (board.ruleViolations(placed).isNotEmpty()) return null

        val grid = CandidateGrid.of(board, placed)
        deducePlacement(grid)?.let { return it }

        val solution = PuzzleSolver.solve(board, placed) ?: return null
        return mostConstrainedUnresolvedCell(board, grid, solution)
    }

    /**
     * Applies eliminations until a placement is provable, then reports it
     * without mutating the caller's view of the board.
     */
    private fun deducePlacement(grid: CandidateGrid): Int? {
        val working = grid.copy()
        while (!working.isSolved && !working.isContradicted) {
            when (val step = DeductionEngine.nextStep(working)) {
                null -> return null
                is Deduction.Place -> return step.cell
                is Deduction.Eliminate -> step.cells.forEach { working.eliminate(it) }
            }
        }
        return null
    }

    /**
     * Squares that deduction proves cannot hold a dog and the player cannot yet
     * see, from their current position.
     *
     * This is what a *hint* should hand over. Revealing where a dog goes ends the
     * puzzle; revealing where one cannot go leaves the deduction intact and shows
     * the technique that ruled the squares out.
     *
     * It reports every square that *became* ruled out, not only the ones the
     * engine announced as eliminations. On an easy board the engine solves by
     * placement alone and announces no eliminations at all — the first version of
     * this returned an empty list there, so a hint could be spent for nothing.
     * What matters to the player is the cells that were open when they asked and
     * are provably shut now, however the engine got there.
     */
    fun ruledOutCells(board: Board, placed: Solution, limit: Int = Int.MAX_VALUE): List<Int> {
        if (placed.isComplete || limit <= 0) return emptyList()
        if (board.ruleViolations(placed).isNotEmpty()) return emptyList()

        val grid = CandidateGrid.of(board, placed)
        val alreadyKnown = (0 until board.cellCount).filterNot { grid.isCandidate(it) }.toSet()
        val found = LinkedHashSet<Int>()

        while (found.size < limit && !grid.isSolved && !grid.isContradicted) {
            when (val step = DeductionEngine.nextStep(grid) ?: break) {
                is Deduction.Place -> grid.place(step.cell)
                is Deduction.Eliminate -> step.cells.forEach { grid.eliminate(it) }
            }
            (0 until board.cellCount).forEach { cell ->
                if (cell !in alreadyKnown && !grid.isCandidate(cell) && !grid.isPlaced(cell)) {
                    found += cell
                }
            }
        }
        return found.take(limit)
    }

    private fun mostConstrainedUnresolvedCell(
        board: Board,
        grid: CandidateGrid,
        solution: Solution,
    ): Int? = (0 until board.size)
        .filter { !grid.hasPlacement(Group(GroupKind.Row, it)) }
        .minByOrNull { grid.candidatesIn(Group(GroupKind.Row, it)).size }
        ?.let { board.cellAt(it, solution[it]) }
}
