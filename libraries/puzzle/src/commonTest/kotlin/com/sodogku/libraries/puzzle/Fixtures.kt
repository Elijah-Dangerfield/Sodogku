package com.sodogku.libraries.puzzle

import kotlin.random.Random

/**
 * Hand-built boards with properties worked out on paper, so a test asserting
 * "this one is unique" is anchored to something other than the code under test.
 *
 * The 4x4 case is small enough to reason about completely: of the 24 column
 * permutations, exactly two keep every consecutive pair two columns apart —
 * `1,3,0,2` and `2,0,3,1`. So a 4x4's solution count is decided purely by which
 * of those two the regions admit, and it is never more than 2.
 */
object Fixtures {

    /** `1,3,0,2` — one of the only two adjacency-legal 4x4 placements. */
    val FOUR_BY_FOUR_A: Solution = Solution(intArrayOf(1, 3, 0, 2))

    /** `2,0,3,1` — the other one. */
    val FOUR_BY_FOUR_B: Solution = Solution(intArrayOf(2, 0, 3, 1))

    /**
     * Admits [FOUR_BY_FOUR_B] only. Region `A` swallows both of
     * [FOUR_BY_FOUR_A]'s first two dogs, which kills it on the one-per-region
     * rule while leaving B untouched.
     */
    fun uniqueFourByFour(): Board = Board.parse(
        "BAAA" +
            "BBCA" +
            "DDCC" +
            "DDDD",
    )

    /**
     * Admits both. With each row its own region, one-per-region is implied by
     * one-per-row, so the regions constrain nothing at all.
     */
    fun ambiguousFourByFour(): Board = rowsAsRegions(4)

    /**
     * Admits neither, so it has no solution at all. Region `A` holds two of
     * [FOUR_BY_FOUR_A]'s dogs and region `B` holds two of [FOUR_BY_FOUR_B]'s,
     * and those are the only two placements the adjacency rule allows.
     */
    fun unsolvableFourByFour(): Board = Board.parse(
        "CAAA" +
            "BDDA" +
            "BDDD" +
            "BBDD",
    )

    /** Region `r` is row `r`. Contiguous, and a no-op constraint. */
    fun rowsAsRegions(size: Int): Board =
        Board(size, IntArray(size * size) { it / size })

    /** Region `c` is column `c`. Also contiguous, also a no-op constraint. */
    fun columnsAsRegions(size: Int): Board =
        Board(size, IntArray(size * size) { it % size })

    /**
     * A board with exactly one solution, plus that solution. Draws random
     * boards and mutates near-misses until one comes out unique, which is the
     * generator loop in miniature.
     */
    fun uniqueBoard(size: Int, random: Random, attempts: Int = 200): Pair<Board, Solution>? {
        repeat(attempts) {
            val seed = BoardFactory.randomSolution(size, random) ?: return@repeat
            var board = BoardFactory.growRegions(seed, random)
            repeat(MUTATION_ROUNDS) {
                PuzzleSolver.uniqueSolutionOrNull(board)?.let { return board to it }
                board = BoardFactory.mutateRegions(board, seed, random)
            }
        }
        return null
    }

    /**
     * A unique board at a size [uniqueBoard] cannot reach. Random mutation
     * converges at small sizes and not at all from 9x9 up, so this takes the
     * same targeted-refinement path the real generator uses.
     */
    fun uniqueLargeBoard(size: Int, random: Random, attempts: Int = 20): Pair<Board, Solution>? {
        repeat(attempts) {
            val seed = BoardFactory.randomSolution(size, random) ?: return@repeat
            val grown = BoardFactory.growRegions(seed, random)
            val refined = BoardFactory.refineToUnique(grown, seed, random) ?: return@repeat
            PuzzleSolver.uniqueSolutionOrNull(refined)?.let { return refined to it }
        }
        return null
    }

    private const val MUTATION_ROUNDS = 12
}
