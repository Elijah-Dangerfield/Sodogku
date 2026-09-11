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
     * A square deduction shut, and the [Technique] whose step shut it.
     *
     * First step wins. A square is usually re-proved by everything that follows,
     * so crediting the latest step would credit the deepest reasoning for a
     * square the shallowest had already closed, and the sentence the player
     * reads would be harder than the one they could have used.
     */
    data class RuledOut(val cell: Int, val technique: Technique)

    /**
     * One technique, and only the squares that technique proved.
     *
     * [cells] is a subset of what was found rather than all of it, and that is
     * the point of the type: a batch usually spans two or three techniques, and
     * naming one of them over all of the crosses would be false for some of the
     * squares the player is looking at.
     */
    data class HintReason(val technique: Technique, val cells: Set<Int>)

    /**
     * The cell to reveal given what the player has already placed, or null when
     * the board is finished or the placements have gone wrong.
     *
     * When no chain of deductions reaches a placement (a board needing depth
     * beyond the engine), it falls back to the unique solution and picks the
     * most constrained unresolved row, which is the closest thing to "the cell
     * you were nearest to working out".
     *
     * **That fallback cannot be reached on a board the game ships, and is not
     * dead.** Every level in both packs is verified unique and scored below
     * [Difficulty.BEYOND_DEDUCTION], and a correct placement only removes
     * candidates, so a board the engine can finish from empty it can finish
     * from any partial a player reaches. Measured: zero fallbacks across 4,680
     * legal partials on 78 generated boards from 4x4 to 10x10. On a
     * [Difficulty.BEYOND_DEDUCTION] board it fires constantly, and not by the
     * obvious route — the engine eliminates its way into a contradiction,
     * because several techniques are sound only where there is one answer, so
     * it never reaches a placement at all.
     * `DeductionSoundnessTest.aHintOnABoardDeductionCannotFinishComesFromThe
     * TightestRowLeft` pins it. Keep it: a sniff is a paid consumable, handing
     * back nothing is the regression this file has already had once, and tier 5
     * is one generator change away from shippable.
     */
    fun nextCell(board: Board, placed: Solution): Int? {
        if (placed.isComplete) return null
        if (board.ruleViolations(placed).isNotEmpty()) return null
        // Solving up front rather than after `deducePlacement` is the whole
        // guard: see [isDeadEnd]. It costs well under a millisecond even at
        // 10x10, so there is nothing to save by deferring it.
        val solution = PuzzleSolver.solve(board, placed) ?: return null

        val grid = CandidateGrid.of(board, placed)
        deducePlacement(grid)?.let { return it }

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
     *
     * [limit] has no default. Left unbounded this returns every non-dog square on
     * the board — 90 of 100 at 10x10 — which hands over the answer by exclusion.
     * That is never what a caller wants, so it has to be an explicit choice.
     *
     * Each square carries the technique that shut it, because a cross the player
     * cannot account for teaches them to stop asking. Which of those techniques
     * the hint actually says out loud is [strongestReason]'s decision.
     */
    fun ruledOutCells(board: Board, placed: Solution, limit: Int): List<RuledOut> {
        if (placed.isComplete || limit <= 0) return emptyList()
        if (isDeadEnd(board, placed)) return emptyList()

        val grid = CandidateGrid.of(board, placed)
        val alreadyKnown = (0 until board.cellCount).filterNot { grid.isCandidate(it) }.toSet()
        val found = LinkedHashMap<Int, Technique>()

        while (found.size < limit && !grid.isSolved && !grid.isContradicted) {
            val step = DeductionEngine.nextStep(grid) ?: break
            when (step) {
                is Deduction.Place -> grid.place(step.cell)
                is Deduction.Eliminate -> step.cells.forEach { grid.eliminate(it) }
            }
            (0 until board.cellCount).forEach { cell ->
                if (cell !in alreadyKnown && !grid.isCandidate(cell) && !grid.isPlaced(cell)) {
                    // A placement shuts a square as surely as an elimination
                    // does, and on an easy board it is the only thing that ever
                    // shuts one. The step that did it owns the square either way.
                    found.getOrPut(cell) { step.technique }
                }
            }
        }
        return found.entries.take(limit).map { RuledOut(it.key, it.value) }
    }

    /**
     * The one technique worth saying, and only the squares it proved.
     *
     * A batch of squares is rarely all one technique's work, so there are three
     * ways to report it and two of them are bad. Per-square reasons are four
     * sentences in a speech bubble that already holds a title, a count and two
     * buttons. One reason over all the squares is worse than verbose: it is
     * wrong about the squares the other techniques closed, and a player who
     * checks the sentence against a cross it does not explain learns that the
     * sentence is decoration.
     *
     * So the reason is chosen first and the reveal is cut to fit it. That sounds
     * like it costs the player squares and measurably does not: over 199 hints
     * on generated boards, 39% of batches spanned more than one technique and
     * cutting them cost 5 crosses in total, none of them more than one from a
     * single hint. The biggest group is nearly always big enough to fill the
     * sheet on its own.
     *
     * Ordered by how many squares a technique accounts for, counted as they will
     * be *shown* rather than as they were found: two techniques that both fill
     * the sheet are equally generous, and between equals the shallower tier wins
     * because it is the one the player has a chance of following.
     */
    fun strongestReason(ruledOut: List<RuledOut>, limit: Int): HintReason? {
        if (limit <= 0) return null
        return ruledOut.groupBy { it.technique }
            .map { (technique, shut) ->
                HintReason(technique, shut.take(limit).map { it.cell }.toSet())
            }
            .sortedWith(
                compareByDescending<HintReason> { it.cells.size }.thenBy { it.technique.tier },
            )
            .firstOrNull()
    }

    /**
     * True when [placed] can no longer be completed.
     *
     * Breaking no rule is not the same as still being winnable: a partial can be
     * perfectly legal and have zero completions. Every technique below is sound,
     * which is exactly the problem — reason soundly from a false premise and the
     * engine will confidently rule out the square the answer is sitting on. It
     * did, on 12% of legal-but-dead partials.
     *
     * Today's flow marks a wrong guess rather than placing it, so [placed] should
     * only ever hold correct dogs and this should never fire. It is one undo or
     * restore-state feature away from mattering, and a hint crossing out the
     * answer is the worst failure this library has available.
     */
    private fun isDeadEnd(board: Board, placed: Solution): Boolean =
        board.ruleViolations(placed).isNotEmpty() || PuzzleSolver.solve(board, placed) == null

    private fun mostConstrainedUnresolvedCell(
        board: Board,
        grid: CandidateGrid,
        solution: Solution,
    ): Int? = (0 until board.size)
        .filter { !grid.hasPlacement(Group(GroupKind.Row, it)) }
        .minByOrNull { grid.candidatesIn(Group(GroupKind.Row, it)).size }
        ?.let { board.cellAt(it, solution[it]) }
}
