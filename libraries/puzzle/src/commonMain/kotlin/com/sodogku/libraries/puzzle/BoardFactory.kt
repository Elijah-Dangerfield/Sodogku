package com.sodogku.libraries.puzzle

import kotlin.random.Random

/**
 * Builds boards that are legal by construction.
 *
 * Two steps, in this order for a reason: pick the answer first, then draw the
 * regions around it. Growing each region outward from the cell its dog occupies
 * makes "exactly one dog per region" and "every region is contiguous" true by
 * construction rather than by rejection, which is the difference between a
 * generator that finds a 10x10 in milliseconds and one that spins.
 *
 * What construction does *not* guarantee is uniqueness. That is the caller's
 * job, via [PuzzleSolver.uniqueSolutionOrNull] and [mutateRegions] when the
 * first draw is ambiguous.
 */
object BoardFactory {

    /**
     * A random legal placement: a permutation of columns where consecutive rows
     * sit at least two apart. Returns null only if [attempts] random restarts
     * all fail, which is vanishingly unlikely for [Board.MIN_SIZE] and above.
     */
    fun randomSolution(size: Int, random: Random, attempts: Int = DEFAULT_ATTEMPTS): Solution? {
        require(size >= Board.MIN_SIZE) { "No legal placement exists below size ${Board.MIN_SIZE}" }
        repeat(attempts) {
            buildSolution(size, random)?.let { return it }
        }
        return null
    }

    private fun buildSolution(size: Int, random: Random): Solution? {
        val columns = IntArray(size) { Solution.UNPLACED }
        val used = BooleanArray(size)

        fun place(row: Int): Boolean {
            if (row == size) return true
            val previous = if (row == 0) Solution.UNPLACED else columns[row - 1]
            val options = (0 until size)
                .filter { !used[it] }
                .filter { previous == Solution.UNPLACED || abs(it - previous) >= 2 }
                .shuffled(random)
            options.forEach { col ->
                columns[row] = col
                used[col] = true
                if (place(row + 1)) return true
                used[col] = false
                columns[row] = Solution.UNPLACED
            }
            return false
        }

        return if (place(0)) Solution(columns) else null
    }

    /**
     * Draws [size] contiguous regions, one seeded at each dog in [solution] and
     * grown by randomly claiming an unassigned cell orthogonally adjacent to
     * something already claimed.
     *
     * Growth picks a random *frontier cell* rather than a random region, which
     * keeps region sizes uneven and organic. Picking a region round-robin makes
     * every board look like a pinwheel.
     */
    fun growRegions(solution: Solution, random: Random): Board {
        val size = solution.size
        val regions = IntArray(size * size) { UNASSIGNED }

        val frontier = mutableListOf<Int>()
        (0 until size).forEach { row ->
            val cell = row * size + solution[row]
            regions[cell] = row
            frontier += cell
        }

        var remaining = size * size - size
        while (remaining > 0 && frontier.isNotEmpty()) {
            val pick = random.nextInt(frontier.size)
            val cell = frontier[pick]
            val open = orthogonalNeighbours(cell, size).filter { regions[it] == UNASSIGNED }
            if (open.isEmpty()) {
                frontier.removeAt(pick)
                continue
            }
            val claimed = open.random(random)
            regions[claimed] = regions[cell]
            frontier += claimed
            remaining--
        }

        return Board(size, regions)
    }

    /**
     * Nudges region boundaries without breaking the invariants: moves [moves]
     * boundary cells to a neighbouring region, skipping any move that would
     * orphan a dog or split a region in two.
     *
     * This is what turns a near-miss into a usable puzzle. Redrawing from
     * scratch throws away a board that was one cell from unique; mutating
     * explores its neighbourhood instead.
     */
    fun mutateRegions(board: Board, solution: Solution, random: Random, moves: Int = 1): Board {
        var current = board
        repeat(moves) {
            val candidates = (0 until current.cellCount)
                .filter { it !in solution.cells() }
                .flatMap { cell ->
                    orthogonalNeighbours(cell, current.size)
                        .map { current.regionAt(it) }
                        .filter { it != current.regionAt(cell) }
                        .distinct()
                        .map { cell to it }
                }
                .shuffled(random)

            val applied = candidates.firstNotNullOfOrNull { (cell, newRegion) ->
                val regions = current.regions.copyOf()
                regions[cell] = newRegion
                val next = Board(current.size, regions)
                next.takeIf { it.structuralProblems().isEmpty() && it.holdsOneDogPerRegion(solution) }
            }
            if (applied != null) current = applied
        }
        return current
    }

    private fun Board.holdsOneDogPerRegion(solution: Solution): Boolean =
        solution.cells().map { regionAt(it) }.distinct().size == size

    private fun orthogonalNeighbours(cell: Int, size: Int): List<Int> {
        val row = cell / size
        val col = cell % size
        return buildList {
            if (row > 0) add(cell - size)
            if (row < size - 1) add(cell + size)
            if (col > 0) add(cell - 1)
            if (col < size - 1) add(cell + 1)
        }
    }

    private fun abs(value: Int): Int = if (value < 0) -value else value

    private const val UNASSIGNED = -1
    private const val DEFAULT_ATTEMPTS = 64
}
