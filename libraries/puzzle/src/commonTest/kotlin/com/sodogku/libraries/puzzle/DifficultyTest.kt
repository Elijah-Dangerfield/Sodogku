package com.sodogku.libraries.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The number that orders the packs, and the three ways it can lie.
 *
 * A board with no answer and a board with several both score
 * `BEYOND_DEDUCTION`, which is the value that keeps them out of a pack. That is
 * a refusal rather than a rating, and reading it as "very hard" is the mistake
 * the first two tests exist to make loud.
 *
 * The other two failures are statistical, so they are pinned statistically over
 * sixty generated boards. A scorer whose engine gives up on most boards is
 * measuring its own blind spots, and a scorer that returns one tier for
 * everything cannot drive a curve at all. Neither shows up as a wrong answer on
 * any single board, which is why neither would be caught anywhere else.
 *
 * ### Not here
 *
 * That the engine's reasoning is sound, and that it prefers shallow techniques,
 * is `DeductionEngineTest`. Which levels end up in which pack is
 * `:libraries:levels`.
 */
class DifficultyTest {

    @Test
    fun anAmbiguousBoardScoresBeyondDeduction() {
        // Not because it is hard. Because no reasoning determines an answer,
        // which is exactly why such a board must never reach the pack.
        assertEquals(Difficulty.BEYOND_DEDUCTION, Difficulty.score(Fixtures.ambiguousFourByFour()))
    }

    @Test
    fun anUnsolvableBoardScoresBeyondDeduction() {
        assertEquals(Difficulty.BEYOND_DEDUCTION, Difficulty.score(Fixtures.unsolvableFourByFour()))
    }

    @Test
    fun aUniqueBoardScoresWithinTheDeducibleRange() {
        val score = Difficulty.score(Fixtures.uniqueFourByFour())

        assertTrue(
            score in Difficulty.EASIEST until Difficulty.BEYOND_DEDUCTION,
            "expected a deducible score, got $score",
        )
    }

    /**
     * The soundness test proves the engine never concludes something false. It
     * would pass just as happily if the engine concluded nothing at all, so
     * this pins the other half: the engine has to actually finish most boards,
     * or difficulty scoring is measuring the engine's blind spots rather than
     * the puzzle's difficulty.
     */
    @Test
    fun theEngineSolvesTheLargeMajorityOfUniqueBoards() {
        val scores = scoreRandomUniqueBoards(seed = 606, boards = 60)

        val deduced = scores.count { it < Difficulty.BEYOND_DEDUCTION }

        assertTrue(
            deduced >= scores.size * MIN_DEDUCED_PERCENT / 100,
            "only $deduced/${scores.size} boards were solvable by deduction",
        )
    }

    @Test
    fun scoresSpreadAcrossTiersRatherThanCollapsingToOne() {
        // A scorer that returns the same tier for everything cannot drive a
        // difficulty curve, and would not show up as a failure anywhere else.
        val distinct = scoreRandomUniqueBoards(seed = 909, boards = 60)
            .filter { it < Difficulty.BEYOND_DEDUCTION }
            .distinct()

        assertTrue(distinct.size >= 2, "every board scored the same tier: $distinct")
    }

    @Test
    fun everyScoreIsWithinBounds() {
        scoreRandomUniqueBoards(seed = 4242, boards = 40).forEach { score ->
            assertTrue(
                score in Difficulty.EASIEST..Difficulty.BEYOND_DEDUCTION,
                "score $score is outside 1..${Difficulty.BEYOND_DEDUCTION}",
            )
        }
    }

    private fun scoreRandomUniqueBoards(seed: Int, boards: Int): List<Int> {
        val random = Random(seed)
        val scores = mutableListOf<Int>()
        var attempts = 0
        while (scores.size < boards && attempts < boards * ATTEMPT_SLACK) {
            attempts++
            val size = Board.MIN_SIZE + random.nextInt(MAX_SIZE - Board.MIN_SIZE + 1)
            val (board, _) = Fixtures.uniqueBoard(size, random) ?: continue
            scores += Difficulty.score(board)
        }
        assertTrue(scores.size >= boards, "only built ${scores.size} boards in $attempts attempts")
        return scores
    }

    private companion object {
        const val MAX_SIZE = 8
        const val MIN_DEDUCED_PERCENT = 80
        const val ATTEMPT_SLACK = 4
    }
}
