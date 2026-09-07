package com.sodogku.tools.levelgen

import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.BoardFactory
import com.sodogku.libraries.puzzle.Difficulty
import com.sodogku.libraries.puzzle.PuzzleSolver
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.puzzle.structuralProblems
import kotlin.random.Random

/**
 * One stretch of the campaign at a fixed grid size.
 *
 * [maxDifficulty] exists for the opening bands. A tier-4 board needs a
 * hold-a-hypothesis-in-your-head contradiction step, and meeting one at level 10
 * is how a puzzle game loses a player on their first session — so the tutorial
 * bands refuse them outright rather than relying on the sort to bury them.
 */
data class Band(
    val size: Int,
    val count: Int,
    val maxDifficulty: Int = Difficulty.BEYOND_DEDUCTION,
)

/** A board that survived generation, before it is given a level number. */
data class Candidate(val board: Board, val solution: Solution, val difficulty: Int)

/**
 * Produces verified, deduplicated boards.
 *
 * Every candidate is checked with [PuzzleSolver.uniqueSolutionOrNull] before it
 * is accepted, so "exactly one solution" is established here rather than
 * asserted later. The verification test over the shipped pack is the backstop,
 * not the primary check.
 */
class Generator(private val random: Random) {

    private val seen = mutableSetOf<String>()

    /**
     * Builds [count] distinct unique-solution boards at [size], or as many as
     * [attemptBudget] allows. Returns fewer than asked rather than spinning: the
     * caller reports the shortfall so a tightened budget fails loudly instead of
     * silently shipping a short pack.
     */
    fun build(
        size: Int,
        count: Int,
        maxDifficulty: Int = Difficulty.BEYOND_DEDUCTION,
        attemptBudget: Int = count * ATTEMPTS_PER_LEVEL,
    ): List<Candidate> {
        val results = mutableListOf<Candidate>()
        var attempts = 0

        while (results.size < count && attempts < attemptBudget) {
            attempts++
            val seed = BoardFactory.randomSolution(size, random) ?: continue
            val grown = BoardFactory.growRegions(seed, random)
            val refined = BoardFactory.refineToUnique(grown, seed, random) ?: continue

            // Belt and braces: refinement is supposed to guarantee this, and a
            // silent regression there would otherwise ship an ambiguous board.
            val solution = PuzzleSolver.uniqueSolutionOrNull(refined) ?: continue
            if (refined.structuralProblems().isNotEmpty()) continue
            if (!seen.add(canonicalKey(refined))) continue

            val difficulty = Difficulty.score(refined)
            if (difficulty > maxDifficulty) continue

            results += Candidate(refined, solution, difficulty)
        }
        return results
    }

    /**
     * Identity of a board up to the eight grid symmetries and any renaming of
     * regions, so a rotated or recoloured duplicate is caught.
     *
     * Region ids are renumbered by first appearance in each transform before
     * comparing, which is what makes "same shape, different colours" collapse to
     * one key.
     */
    private fun canonicalKey(board: Board): String =
        symmetriesOf(board).minOf { relabel(it, board.size) }

    private fun symmetriesOf(board: Board): List<IntArray> {
        val size = board.size
        var current = board.regions
        val forms = mutableListOf<IntArray>()
        repeat(4) {
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

    private companion object {
        /**
         * Refinement converts roughly a quarter of 10x10 draws, so the budget
         * has to be several times the target or the largest band comes up short.
         */
        const val ATTEMPTS_PER_LEVEL = 12
    }
}

/**
 * Orders a band so it opens gently and ramps.
 *
 * Sorting by difficulty inside each band is what produces the sawtooth across
 * the campaign: a band ends on its hardest board, then the next one starts over
 * at its easiest on a bigger grid. A flat global sort would make every size
 * change feel like a wall.
 */
fun orderBand(candidates: List<Candidate>): List<Candidate> =
    candidates.sortedBy { it.difficulty }

fun Candidate.toLevel(id: Int): LevelDefinition =
    LevelDefinition(id = id, board = board, solution = solution, difficulty = difficulty)
