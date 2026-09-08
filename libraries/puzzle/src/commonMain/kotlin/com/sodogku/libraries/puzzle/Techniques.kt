package com.sodogku.libraries.puzzle

/**
 * The deduction steps a player can actually perform, ordered by how hard they
 * are to see. [tier] is what [Difficulty] scores a board on, so changing a
 * technique's tier re-rates every level in the pack.
 */
enum class Technique(val tier: Int) {
    /** A row, column, or region with one candidate left. The dog goes there. */
    LastCandidateInGroup(1),

    /**
     * A cell in the row above or below has no non-touching partner left, so it
     * cannot hold a dog. The "cannot touch" rule read forwards instead of
     * backwards from a placement.
     */
    AdjacencyConfinement(2),

    /**
     * A region's remaining candidates all sit in one row or column (so no other
     * region may use that line), or a line's candidates all sit in one region
     * (so that region may not stray off the line).
     */
    GroupConfinement(2),

    /**
     * `k` regions whose candidates span exactly `k` rows must consume those
     * rows between them, so nothing else can. Plus the row/column duals.
     */
    NakedSet(3),

    /** Assume a candidate, propagate tiers 1 to 3, and eliminate it if that breaks the board. */
    Contradiction(4),
}

/** One applicable deduction: either a dog goes somewhere, or cells are ruled out. */
sealed interface Deduction {
    val technique: Technique

    data class Place(val cell: Int, override val technique: Technique) : Deduction

    data class Eliminate(val cells: List<Int>, override val technique: Technique) : Deduction
}

/**
 * Solves by deduction rather than by search, reporting how hard it had to work.
 *
 * The exact solver ([PuzzleSolver]) answers "what is the answer". This answers
 * "could a person reason their way there, and how much reasoning did it take",
 * which is what difficulty scoring and hinting both need.
 */
object DeductionEngine {

    /**
     * The shallowest deduction available on [grid], or null when none is.
     * Tiers are tried in order and the expensive ones are never evaluated when
     * [maxTier] rules them out — [contradiction] recurses into [run], so the
     * short circuit is load-bearing, not tidiness.
     */
    fun nextStep(grid: CandidateGrid, maxTier: Int = Technique.Contradiction.tier): Deduction? {
        if (maxTier < 1) return null
        lastCandidate(grid)?.let { return it }
        if (maxTier < 2) return null
        adjacencyConfinement(grid)?.let { return it }
        groupConfinement(grid)?.let { return it }
        if (maxTier < 3) return null
        nakedSet(grid)?.let { return it }
        if (maxTier < 4) return null
        return contradiction(grid)
    }

    /**
     * Applies deductions until the board is solved, contradicted, or nothing
     * shallower than [maxTier] applies.
     */
    fun run(grid: CandidateGrid, maxTier: Int = Technique.Contradiction.tier): DeductionRun {
        var hardest = 0
        var steps = 0
        while (!grid.isSolved && !grid.isContradicted) {
            val step = nextStep(grid, maxTier) ?: break
            hardest = maxOf(hardest, step.technique.tier)
            steps++
            when (step) {
                is Deduction.Place -> grid.place(step.cell)
                is Deduction.Eliminate -> step.cells.forEach { grid.eliminate(it) }
            }
        }
        val outcome = when {
            grid.isContradicted -> DeductionOutcome.Contradicted
            grid.isSolved -> DeductionOutcome.Solved
            else -> DeductionOutcome.Stuck
        }
        return DeductionRun(outcome, hardestTier = hardest, steps = steps)
    }

    private fun lastCandidate(grid: CandidateGrid): Deduction? {
        grid.unresolvedGroups().forEach { group ->
            val candidates = grid.candidatesIn(group)
            if (candidates.size == 1) {
                return Deduction.Place(candidates.single(), Technique.LastCandidateInGroup)
            }
        }
        return null
    }

    /**
     * A dog in row `r` at column `c` needs the neighbouring rows' dogs at least
     * two columns away. So a candidate in row `r ± 1` is dead unless some
     * candidate in row `r` can coexist with it. Same argument by column.
     */
    private fun adjacencyConfinement(grid: CandidateGrid): Deduction? =
        // The two axes are swept separately on purpose. Sharing one loop meant a
        // resolved row skipped the *column* of the same index as well, which
        // deleted a slab of tier-2 reasoning exactly when the board is most
        // constrained and mis-rated 4% of boards as harder than they are.
        adjacencyConfinement(grid, GroupKind.Row)
            ?: adjacencyConfinement(grid, GroupKind.Column)

    private fun adjacencyConfinement(grid: CandidateGrid, kind: GroupKind): Deduction? {
        val board = grid.board
        val size = grid.size
        // Along a row the constraint is on columns, and vice versa.
        val across: (Int) -> Int = if (kind == GroupKind.Row) board::colOf else board::rowOf

        for (line in 0 until size) {
            val candidates = grid.candidatesIn(Group(kind, line)).map(across)
            if (candidates.isEmpty()) continue
            for (neighbour in listOf(line - 1, line + 1)) {
                if (neighbour !in 0 until size) continue
                val doomed = grid.candidatesIn(Group(kind, neighbour))
                    .filter { cell -> candidates.none { abs(it - across(cell)) >= 2 } }
                if (doomed.isNotEmpty()) {
                    return Deduction.Eliminate(doomed, Technique.AdjacencyConfinement)
                }
            }
        }
        return null
    }

    /**
     * Two directions of the same idea. A region confined to one line owns that
     * line; a line confined to one region pins that region to the line.
     */
    private fun groupConfinement(grid: CandidateGrid): Deduction? {
        val board = grid.board

        for (group in grid.unresolvedGroups()) {
            val candidates = grid.candidatesIn(group)
            if (candidates.size < 2) continue

            val containers: List<Group> = when (group.kind) {
                GroupKind.Region -> listOf(
                    linesOf(candidates.map { board.rowOf(it) }, GroupKind.Row),
                    linesOf(candidates.map { board.colOf(it) }, GroupKind.Column),
                ).flatten()
                GroupKind.Row, GroupKind.Column ->
                    linesOf(candidates.map { board.regionAt(it) }, GroupKind.Region)
            }

            containers.forEach { container ->
                val doomed = grid.candidatesIn(container).filterNot { it in candidates }
                if (doomed.isNotEmpty()) {
                    return Deduction.Eliminate(doomed, Technique.GroupConfinement)
                }
            }
        }
        return null
    }

    /** The single containing group when every value is the same, else nothing. */
    private fun linesOf(values: List<Int>, kind: GroupKind): List<Group> =
        values.distinct().singleOrNull()?.let { listOf(Group(kind, it)) }.orEmpty()

    /**
     * `k` groups of one kind whose candidates span exactly `k` groups of
     * another kind consume those entirely. Subsets are enumerated over the
     * unresolved groups only, so the `2^size` worst case shrinks fast as the
     * board fills.
     */
    private fun nakedSet(grid: CandidateGrid): Deduction? {
        val board = grid.board
        val pairings = listOf(
            GroupKind.Region to GroupKind.Row,
            GroupKind.Region to GroupKind.Column,
            GroupKind.Row to GroupKind.Region,
            GroupKind.Column to GroupKind.Region,
        )

        pairings.forEach { (sourceKind, targetKind) ->
            val sources = grid.unresolvedGroups().filter { it.kind == sourceKind }
            val spans = sources.associateWith { source ->
                grid.candidatesIn(source).map { containerIndex(board, it, targetKind) }.toSet()
            }

            subsetsOf(sources).forEach { subset ->
                val span = subset.flatMap { spans.getValue(it) }.toSet()
                if (span.size != subset.size) return@forEach

                val protectedCells = subset.flatMap { grid.candidatesIn(it) }.toSet()
                val doomed = span
                    .flatMap { grid.candidatesIn(Group(targetKind, it)) }
                    .filterNot { it in protectedCells }
                if (doomed.isNotEmpty()) {
                    return Deduction.Eliminate(doomed.distinct(), Technique.NakedSet)
                }
            }
        }
        return null
    }

    private fun containerIndex(board: Board, cell: Int, kind: GroupKind): Int = when (kind) {
        GroupKind.Row -> board.rowOf(cell)
        GroupKind.Column -> board.colOf(cell)
        GroupKind.Region -> board.regionAt(cell)
    }

    /** Subsets of size 2 up to `n - 1`; singletons are [groupConfinement]'s job. */
    private fun subsetsOf(groups: List<Group>): Sequence<List<Group>> = sequence {
        val n = groups.size
        if (n < 2) return@sequence
        for (mask in 1 until (1 shl n)) {
            val bits = mask.countOneBits()
            if (bits < 2 || bits >= n) continue
            yield(groups.filterIndexed { index, _ -> mask and (1 shl index) != 0 })
        }
    }

    /**
     * Assume a candidate, propagate everything shallower, and eliminate it if
     * the board falls apart. Deliberately last: it is the only technique that
     * asks a player to hold a hypothetical in their head.
     */
    private fun contradiction(grid: CandidateGrid): Deduction? {
        val doomed = (0 until grid.board.cellCount)
            .firstOrNull { cell ->
                grid.isCandidate(cell) && run(
                    grid.copy().apply { place(cell) },
                    maxTier = Technique.NakedSet.tier,
                ).outcome == DeductionOutcome.Contradicted
            }
        return doomed?.let { Deduction.Eliminate(listOf(it), Technique.Contradiction) }
    }

    private fun abs(value: Int): Int = if (value < 0) -value else value
}

enum class DeductionOutcome { Solved, Stuck, Contradicted }

/**
 * What [DeductionEngine.run] achieved. [hardestTier] is 0 when the board needed
 * no reasoning at all (already solved).
 */
data class DeductionRun(
    val outcome: DeductionOutcome,
    val hardestTier: Int,
    val steps: Int,
)
