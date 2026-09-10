package com.sodogku.libraries.puzzle

/**
 * Identity of a board up to the eight grid symmetries and any renaming of
 * regions, so a rotated or recoloured duplicate has the same key as the
 * original.
 *
 * Two boards a player would call the same puzzle are the same puzzle. A 10x10
 * turned a quarter turn is not a new board to anybody solving it, and neither
 * is the same partition with the pink and the blue swapped: regions carry no
 * meaning beyond "one dog each", so their ids are a labelling and not a fact.
 * Comparing `Board.regions` directly misses both, which means a pack can be
 * free of literal duplicates and still repeat itself eight times over.
 *
 * This lives here rather than in `:tools:level-generator`, where it started,
 * because the claim it supports is about the *shipped pack* and not about the
 * run that produced it. The generator dedups with it as boards are made;
 * `LevelPackVerificationTest` re-derives it over every level in the binary, on
 * the same principle as re-solving each board rather than trusting the
 * uniqueness check that accepted it.
 *
 * Region ids are renumbered by first appearance in each transform before
 * comparing, and the smallest of the eight strings wins, so the key is a
 * property of the shape alone.
 */
fun Board.canonicalKey(): String = symmetriesOf(this).minOf { relabel(it, size) }

private fun symmetriesOf(board: Board): List<IntArray> {
    val size = board.size
    var current = board.regions
    val forms = mutableListOf<IntArray>()
    repeat(QUARTER_TURNS) {
        forms += current
        forms += mirror(current, size)
        current = rotate(current, size)
    }
    return forms
}

private fun rotate(regions: IntArray, size: Int): IntArray =
    IntArray(size * size) { index ->
        val row = index / size
        val col = index % size
        regions[(size - 1 - col) * size + row]
    }

private fun mirror(regions: IntArray, size: Int): IntArray =
    IntArray(size * size) { index ->
        val row = index / size
        val col = index % size
        regions[row * size + (size - 1 - col)]
    }

private fun relabel(regions: IntArray, size: Int): String {
    val mapping = HashMap<Int, Int>(size)
    val builder = StringBuilder(regions.size)
    regions.forEach { region ->
        val id = mapping.getOrPut(region) { mapping.size }
        builder.append(Board.REGION_LETTERS[id])
    }
    return builder.toString()
}

private const val QUARTER_TURNS = 4
