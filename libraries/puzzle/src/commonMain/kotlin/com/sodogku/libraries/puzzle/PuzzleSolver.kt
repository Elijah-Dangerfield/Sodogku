package com.sodogku.libraries.puzzle

/**
 * Exact solver. Places one dog per row in row order, tracking used columns and
 * used regions as bitmasks (boards cap at [Board.MAX_SIZE], so an `Int` is
 * plenty) and pruning on the three rules.
 *
 * The adjacency rule collapses to a single comparison here, which is what makes
 * the search cheap: with exactly one dog per row, two dogs can only touch if
 * they are in consecutive rows, so the whole "cannot touch" constraint is
 * `abs(col - previousCol) >= 2`.
 *
 * This is the same solver the generator runs at build time and the hint engine
 * runs on device. There is deliberately no second implementation to drift.
 */
object PuzzleSolver {

    /**
     * Number of complete solutions consistent with [given], counted up to
     * [limit] and then abandoned.
     *
     * Uniqueness is the only question the generator ever asks, so the default
     * limit is 2: "is it 0, 1, or more than 1". Raising it costs real time on a
     * loosely constrained board.
     */
    fun countSolutions(
        board: Board,
        given: Solution = Solution.empty(board.size),
        limit: Int = 2,
    ): Int {
        require(limit >= 1) { "limit must be at least 1" }
        val search = Search(board, given)
        return if (search.contradicted) 0 else search.count(limit)
    }

    /** The first solution found consistent with [given], or null if there is none. */
    fun solve(board: Board, given: Solution = Solution.empty(board.size)): Solution? {
        val search = Search(board, given)
        return if (search.contradicted) null else search.first()
    }

    /**
     * The solution when there is exactly one, else null. A board that resolves
     * to null here must never ship: the tap mechanic reports "right" or "wrong"
     * on every tap, and neither answer means anything without a unique target.
     */
    fun uniqueSolutionOrNull(
        board: Board,
        given: Solution = Solution.empty(board.size),
    ): Solution? {
        val search = Search(board, given)
        if (search.contradicted) return null
        val solutions = search.take(2)
        return solutions.singleOrNull()
    }

    /**
     * Up to [limit] complete solutions consistent with [given]. The generator
     * uses this to get hold of a rival placement it can then design against;
     * everything else should ask a narrower question.
     */
    fun solutions(
        board: Board,
        given: Solution = Solution.empty(board.size),
        limit: Int,
    ): List<Solution> {
        require(limit >= 1) { "limit must be at least 1" }
        val search = Search(board, given)
        return if (search.contradicted) emptyList() else search.take(limit)
    }

    /** True when [given] can still be extended to at least one complete solution. */
    fun isSatisfiable(board: Board, given: Solution): Boolean =
        countSolutions(board, given, limit = 1) > 0

    private class Search(private val board: Board, given: Solution) {

        private val size = board.size
        private val fixed = given.columnByRow.copyOf()
        private val working = IntArray(size) { Solution.UNPLACED }

        private var usedColumns = 0
        private var usedRegions = 0

        /** Set when [given] already breaks a rule, so no extension can be legal. */
        val contradicted: Boolean = board.ruleViolations(given).isNotEmpty()

        fun first(): Solution? = take(1).firstOrNull()

        fun count(limit: Int): Int {
            var found = 0
            search(0) {
                found++
                found < limit
            }
            return found
        }

        fun take(limit: Int): List<Solution> {
            val results = mutableListOf<Solution>()
            search(0) {
                results += Solution(working)
                results.size < limit
            }
            return results
        }

        /**
         * Depth-first over rows. [onSolution] returns true to keep searching for
         * more, false to unwind and stop; [search] propagates that as its own
         * return value so every frame above unwinds too.
         */
        private fun search(row: Int, onSolution: () -> Boolean): Boolean {
            if (row == size) return onSolution()

            val previousColumn = if (row == 0) Solution.UNPLACED else working[row - 1]
            val candidates = if (fixed[row] != Solution.UNPLACED) {
                intArrayOf(fixed[row])
            } else {
                COLUMN_RANGE
            }

            for (col in candidates) {
                if (col >= size) break
                if (usedColumns and (1 shl col) != 0) continue
                if (previousColumn != Solution.UNPLACED && abs(col - previousColumn) < 2) continue
                val region = board.regionAt(row, col)
                if (usedRegions and (1 shl region) != 0) continue

                working[row] = col
                usedColumns = usedColumns or (1 shl col)
                usedRegions = usedRegions or (1 shl region)

                val keepGoing = search(row + 1, onSolution)

                working[row] = Solution.UNPLACED
                usedColumns = usedColumns and (1 shl col).inv()
                usedRegions = usedRegions and (1 shl region).inv()

                if (!keepGoing) return false
            }
            return true
        }

        private fun abs(value: Int): Int = if (value < 0) -value else value
    }

    private val COLUMN_RANGE = IntArray(Board.MAX_SIZE) { it }
}
