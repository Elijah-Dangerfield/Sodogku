package com.sodogku.tools.levelgen

import com.sodogku.libraries.levels.CurveBand
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.BoardFactory
import com.sodogku.libraries.puzzle.canonicalKey
import com.sodogku.libraries.puzzle.Difficulty
import com.sodogku.libraries.puzzle.PuzzleSolver
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.puzzle.structuralProblems
import kotlin.random.Random

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
     * Builds the distinct unique-solution boards [band] asks for, tier by tier,
     * or as many as [attemptBudget] allows. Returns fewer than asked rather than
     * spinning: the caller reports the shortfall so a tightened budget fails
     * loudly instead of silently shipping a short pack.
     *
     * Generation cannot aim at a tier — a board's difficulty is whatever the
     * deduction engine finds once it exists — so this draws and discards. The
     * budget has to cover that: at 10x10 roughly a sixth of draws come out at
     * tier 4, so a band wanting eighty of them needs some five hundred attempts.
     */
    fun build(band: CurveBand, attemptBudget: Int = band.count * ATTEMPTS_PER_LEVEL): List<Candidate> {
        val wanted = band.runs.associate { it.tier to it.count }.toMutableMap()
        val results = mutableListOf<Candidate>()
        var attempts = 0

        while (results.size < band.count && attempts < attemptBudget) {
            attempts++
            val seed = BoardFactory.randomSolution(band.size, random) ?: continue
            val grown = BoardFactory.growRegions(seed, random)
            val refined = BoardFactory.refineToUnique(grown, seed, random) ?: continue

            // Belt and braces: refinement is supposed to guarantee this, and a
            // silent regression there would otherwise ship an ambiguous board.
            val solution = PuzzleSolver.uniqueSolutionOrNull(refined) ?: continue
            if (refined.structuralProblems().isNotEmpty()) continue

            // Tier before dedup, and not the other way round: a board rejected
            // for its tier would otherwise burn its canonical key on the way
            // out, so the one board of that shape the pack could have used
            // later is gone. Cheaper too — the key costs eight symmetries.
            val difficulty = Difficulty.score(refined)
            val remaining = wanted[difficulty] ?: continue
            if (remaining == 0) continue
            if (!seen.add(refined.canonicalKey())) continue

            wanted[difficulty] = remaining - 1
            results += Candidate(refined, solution, difficulty)
        }
        return results
    }

    private companion object {
        /**
         * Refinement converts roughly half of 10x10 draws and the tier filter
         * throws away most of what survives, so the budget has to be well over
         * the target or the largest band comes up short. Measured yields per
         * accepted board: tier 4 is 2% of 4x4 draws and 32% of 10x10 ones.
         */
        const val ATTEMPTS_PER_LEVEL = 24
    }
}

/**
 * Orders a band so it opens gently and ramps.
 *
 * Sorting by difficulty inside each band is what produces the sawtooth across
 * the campaign: a band ends on its hardest board, then the next one starts a
 * tier lower on a bigger grid. A flat global sort would make every size change
 * feel like a wall.
 */
fun orderBand(candidates: List<Candidate>): List<Candidate> =
    candidates.sortedBy { it.difficulty }

fun Candidate.toLevel(id: Int): LevelDefinition =
    LevelDefinition(id = id, board = board, solution = solution, difficulty = difficulty)
