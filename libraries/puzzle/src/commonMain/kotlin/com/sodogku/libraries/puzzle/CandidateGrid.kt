package com.sodogku.libraries.puzzle

/** The three groupings that must each hold exactly one dog. */
enum class GroupKind { Row, Column, Region }

/** One row, column, or region, identified by its index within that kind. */
data class Group(val kind: GroupKind, val index: Int)

/**
 * Mutable per-cell state during deduction: every cell is a live candidate, an
 * eliminated cell, or a placed dog.
 *
 * This is the model the technique solver reasons over and the model the board
 * screen renders. Placing a dog cascades the eliminations the player sees as
 * auto-mark (whole row, whole column, whole region, all eight neighbors), so
 * what the difficulty scorer assumes the player can see and what the UI
 * actually shows them are the same thing by construction.
 */
class CandidateGrid private constructor(
    val board: Board,
    private val eliminated: BooleanArray,
    private val placed: BooleanArray,
) {

    constructor(board: Board) : this(
        board,
        BooleanArray(board.cellCount),
        BooleanArray(board.cellCount),
    )

    val size: Int get() = board.size

    fun copy(): CandidateGrid = CandidateGrid(board, eliminated.copyOf(), placed.copyOf())

    fun isPlaced(cell: Int): Boolean = placed[cell]

    fun isEliminated(cell: Int): Boolean = eliminated[cell]

    fun isCandidate(cell: Int): Boolean = !placed[cell] && !eliminated[cell]

    val placedCells: List<Int> get() = placed.indices.filter { placed[it] }

    val placedCount: Int get() = placed.count { it }

    val isSolved: Boolean get() = placedCount == size

    /**
     * True when some group can no longer be satisfied. Checked after every
     * cascade, and the whole basis of the tier-4 technique.
     */
    val isContradicted: Boolean
        get() = GroupKind.entries.any { kind ->
            (0 until size).any { index ->
                val group = Group(kind, index)
                !hasPlacement(group) && candidatesIn(group).isEmpty()
            }
        }

    /**
     * Places a dog and cascades every elimination it implies. Idempotent on a
     * cell already placed; throws on a cell already eliminated, which would
     * mean the caller and the grid disagree about what is legal.
     */
    fun place(cell: Int) {
        if (placed[cell]) return
        require(!eliminated[cell]) { "Cannot place on eliminated cell $cell" }

        placed[cell] = true
        val row = board.rowOf(cell)
        val col = board.colOf(cell)
        val region = board.regionAt(cell)

        for (other in 0 until board.cellCount) {
            if (other == cell || placed[other]) continue
            val sameRow = board.rowOf(other) == row
            val sameCol = board.colOf(other) == col
            val sameRegion = board.regionAt(other) == region
            if (sameRow || sameCol || sameRegion) eliminated[other] = true
        }
        board.neighborsOf(cell).forEach { if (!placed[it]) eliminated[it] = true }
    }

    /** Marks [cell] impossible. Returns true when that actually changed something. */
    fun eliminate(cell: Int): Boolean {
        if (placed[cell] || eliminated[cell]) return false
        eliminated[cell] = true
        return true
    }

    fun cellsIn(group: Group): IntArray = when (group.kind) {
        GroupKind.Row -> IntArray(size) { col -> board.cellAt(group.index, col) }
        GroupKind.Column -> IntArray(size) { row -> board.cellAt(row, group.index) }
        GroupKind.Region -> board.cellsInRegion(group.index)
    }

    fun candidatesIn(group: Group): List<Int> = cellsIn(group).filter { isCandidate(it) }

    fun hasPlacement(group: Group): Boolean = cellsIn(group).any { placed[it] }

    /** Every group of every kind that still needs a dog. */
    fun unresolvedGroups(): List<Group> = GroupKind.entries.flatMap { kind ->
        (0 until size).map { Group(kind, it) }.filterNot { hasPlacement(it) }
    }

    /** The current placements as a [Solution], for handing back to the exact solver. */
    fun toSolution(): Solution {
        val columns = IntArray(size) { Solution.UNPLACED }
        placedCells.forEach { columns[board.rowOf(it)] = board.colOf(it) }
        return Solution(columns)
    }

    companion object {
        /** A grid with [solution]'s placements already applied and cascaded. */
        fun of(board: Board, solution: Solution): CandidateGrid =
            CandidateGrid(board).apply {
                (0 until board.size).forEach { row ->
                    val col = solution[row]
                    if (col != Solution.UNPLACED) place(board.cellAt(row, col))
                }
            }
    }
}

/**
 * The cells auto-mark should X given [solution]'s placements: everything the
 * three rules rule out. Exposed separately from [CandidateGrid] because the
 * board screen wants the set without owning a deduction model.
 */
fun Board.autoMarkedCells(solution: Solution): Set<Int> {
    val grid = CandidateGrid.of(this, solution)
    return (0 until cellCount).filter { grid.isEliminated(it) }.toSet()
}
