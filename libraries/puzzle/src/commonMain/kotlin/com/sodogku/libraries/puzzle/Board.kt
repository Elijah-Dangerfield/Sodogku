package com.sodogku.libraries.puzzle

/**
 * An `N x N` grid partitioned into exactly `N` contiguous regions.
 *
 * Cells are addressed by a flat row-major index (`row * size + col`) everywhere
 * inside this module — the solver and the candidate grid both live or die on
 * tight inner loops, and a flat `IntArray` is what makes those cheap. Callers
 * that want coordinates use [rowOf] / [colOf].
 *
 * A board is *shape* only. It carries no placements and no solution; those are
 * a [Solution] resolved against it.
 */
class Board(
    val size: Int,
    regions: IntArray,
) {
    /** Region id per cell, row-major. Values are `0 until size`. */
    val regions: IntArray = regions.copyOf()

    val cellCount: Int get() = size * size

    init {
        require(size >= MIN_SIZE) { "Board size must be at least $MIN_SIZE, was $size" }
        require(regions.size == size * size) {
            "Expected ${size * size} region entries for a ${size}x$size board, got ${regions.size}"
        }
    }

    fun rowOf(cell: Int): Int = cell / size

    fun colOf(cell: Int): Int = cell % size

    fun cellAt(row: Int, col: Int): Int = row * size + col

    fun regionAt(cell: Int): Int = regions[cell]

    fun regionAt(row: Int, col: Int): Int = regions[cellAt(row, col)]

    /** The cells belonging to [region], ascending. */
    fun cellsInRegion(region: Int): IntArray =
        regions.indices.filter { regions[it] == region }.toIntArray()

    /**
     * The up-to-eight cells touching [cell], including diagonals. This is the
     * adjacency the "dogs cannot touch" rule is written against.
     */
    fun neighborsOf(cell: Int): IntArray {
        val row = rowOf(cell)
        val col = colOf(cell)
        val result = ArrayList<Int>(NEIGHBOR_CAPACITY)
        for (dRow in -1..1) {
            for (dCol in -1..1) {
                if (dRow == 0 && dCol == 0) continue
                val r = row + dRow
                val c = col + dCol
                if (r in 0 until size && c in 0 until size) result += cellAt(r, c)
            }
        }
        return result.toIntArray()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Board && size == other.size && regions.contentEquals(other.regions))

    override fun hashCode(): Int = 31 * size + regions.contentHashCode()

    override fun toString(): String = (0 until size).joinToString("\n") { row ->
        (0 until size).joinToString("") { col -> REGION_LETTERS[regionAt(row, col)].toString() }
    }

    companion object {
        /**
         * The smallest board with any valid placement at all. A 2x2 and a 3x3
         * have none: one dog per row and column forces a column permutation,
         * and no permutation of three columns keeps every consecutive pair two
         * apart.
         */
        const val MIN_SIZE: Int = 4

        /** The largest board Sodogku ships, and the width of the bitmasks the solver uses. */
        const val MAX_SIZE: Int = 10

        internal const val REGION_LETTERS: String = "ABCDEFGHIJ"

        private const val NEIGHBOR_CAPACITY = 8

        /**
         * Parses the pack's on-disk form: one letter per cell, row-major, `A`
         * for region 0. Throws on an unrecognized letter or a length that isn't
         * a perfect square.
         */
        fun parse(regions: String): Board {
            val size = when (val root = sqrtExact(regions.length)) {
                null -> error("Region string length ${regions.length} is not a perfect square")
                else -> root
            }
            val ids = IntArray(regions.length) { index ->
                val letter = regions[index]
                val id = REGION_LETTERS.indexOf(letter)
                require(id >= 0) { "Unknown region letter '$letter' at index $index" }
                id
            }
            return Board(size, ids)
        }

        private fun sqrtExact(value: Int): Int? =
            (MIN_SIZE..MAX_SIZE).firstOrNull { it * it == value }
    }
}

/**
 * Everything structurally wrong with a board, empty when it is well formed.
 * Separate from the `init` requirements above: those catch a caller mistake, an
 * incoherent partition is a *content* problem the generator and the pack
 * verification test need to report on rather than crash over.
 */
fun Board.structuralProblems(): List<String> {
    val problems = mutableListOf<String>()

    val outOfRange = regions.filter { it !in 0 until size }.distinct()
    if (outOfRange.isNotEmpty()) {
        problems += "region ids out of range 0..${size - 1}: ${outOfRange.sorted()}"
        return problems
    }

    val counts = IntArray(size)
    regions.forEach { counts[it]++ }
    val empty = counts.indices.filter { counts[it] == 0 }
    if (empty.isNotEmpty()) problems += "empty regions: $empty"

    val disconnected = (0 until size).filter { counts[it] > 0 && !isRegionContiguous(it) }
    if (disconnected.isNotEmpty()) problems += "non-contiguous regions: $disconnected"

    return problems
}

/** Four-connectivity flood fill: diagonal-only touching does not make a region one shape. */
private fun Board.isRegionContiguous(region: Int): Boolean {
    val cells = cellsInRegion(region)
    if (cells.isEmpty()) return false

    val seen = HashSet<Int>(cells.size)
    val stack = ArrayDeque<Int>()
    stack.addLast(cells.first())
    seen += cells.first()

    while (stack.isNotEmpty()) {
        val cell = stack.removeLast()
        val row = rowOf(cell)
        val col = colOf(cell)
        for ((dRow, dCol) in ORTHOGONAL) {
            val r = row + dRow
            val c = col + dCol
            if (r !in 0 until size || c !in 0 until size) continue
            val next = cellAt(r, c)
            if (regionAt(next) == region && seen.add(next)) stack.addLast(next)
        }
    }
    return seen.size == cells.size
}

private val ORTHOGONAL = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
