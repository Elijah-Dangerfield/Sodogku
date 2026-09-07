package com.sodogku.libraries.puzzle

/**
 * One dog per row, stored as the column it sits in. The "one dog per row"
 * constraint is structural here rather than checked: a solution physically
 * cannot express two dogs in a row.
 *
 * [UNPLACED] marks a row with no dog yet, which is how a partially solved board
 * (a game in progress, or the solver mid-search) is represented.
 */
class Solution(columnByRow: IntArray) {

    val columnByRow: IntArray = columnByRow.copyOf()

    val size: Int get() = columnByRow.size

    val placedCount: Int get() = columnByRow.count { it != UNPLACED }

    val isComplete: Boolean get() = columnByRow.none { it == UNPLACED }

    operator fun get(row: Int): Int = columnByRow[row]

    fun withPlacement(row: Int, col: Int): Solution =
        Solution(columnByRow.copyOf().also { it[row] = col })

    fun withoutPlacement(row: Int): Solution =
        Solution(columnByRow.copyOf().also { it[row] = UNPLACED })

    /** The placed cells as flat row-major indices, ascending by row. */
    fun cells(): IntArray = (0 until size)
        .filter { columnByRow[it] != UNPLACED }
        .map { it * size + columnByRow[it] }
        .toIntArray()

    override fun equals(other: Any?): Boolean =
        this === other || (other is Solution && columnByRow.contentEquals(other.columnByRow))

    override fun hashCode(): Int = columnByRow.contentHashCode()

    override fun toString(): String = columnByRow.joinToString(",") {
        if (it == UNPLACED) "_" else it.toString()
    }

    companion object {
        const val UNPLACED: Int = -1

        fun empty(size: Int): Solution = Solution(IntArray(size) { UNPLACED })
    }
}

/**
 * Why a placement set is not a legal solution, empty when it is. Reports every
 * broken rule rather than the first, so the generator's failure output is
 * actionable in one pass.
 *
 * A partial solution is judged on what it has: rules are checked against the
 * placed rows only, so an in-progress board is "valid" until it actually
 * breaks something.
 */
fun Board.ruleViolations(solution: Solution): List<String> {
    require(solution.size == size) {
        "Solution is for a ${solution.size}-row board, this board is $size"
    }
    val problems = mutableListOf<String>()

    val placedRows = (0 until size).filter { solution[it] != Solution.UNPLACED }

    val outOfRange = placedRows.filter { solution[it] !in 0 until size }
    if (outOfRange.isNotEmpty()) {
        problems += "columns out of range in rows $outOfRange"
        return problems
    }

    val byColumn = placedRows.groupBy { solution[it] }.filterValues { it.size > 1 }
    if (byColumn.isNotEmpty()) {
        problems += "columns used more than once: ${byColumn.keys.sorted()}"
    }

    val byRegion = placedRows
        .groupBy { regionAt(it, solution[it]) }
        .filterValues { it.size > 1 }
    if (byRegion.isNotEmpty()) {
        problems += "regions used more than once: ${byRegion.keys.sorted()}"
    }

    val touching = placedRows.filter { row ->
        val previous = row - 1
        previous in placedRows && kotlin.math.abs(solution[row] - solution[previous]) < 2
    }
    if (touching.isNotEmpty()) {
        problems += "dogs touch between rows ${touching.map { it - 1 to it }}"
    }

    return problems
}

/** True when [solution] is complete and breaks no rule on this board. */
fun Board.isSolvedBy(solution: Solution): Boolean =
    solution.isComplete && ruleViolations(solution).isEmpty()
