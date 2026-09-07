package com.sodogku.libraries.puzzle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
