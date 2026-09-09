package com.sodogku.libraries.achievements

import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.scoring.ScoreCard
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whether the *shipped game* can produce the numbers the catalog asks for.
 *
 * `AchievementEngineTest.everyAchievementInTheCatalogCanBeEarned` proves each
 * target is reachable by *some* history, which is a statement about the fold and
 * nothing else — it would happily bless a 40,000-point badge on a formula whose
 * ceiling is 31,760, or an eleven-long combo on a board that only holds ten
 * dogs. This file asks the packs and the scoring formula instead.
 *
 * Only some stats have a ceiling. A count of clears is bounded by how long
 * somebody keeps playing, which is not a number this test knows; the `when`
 * below says so branch by branch rather than with an `else`, so a new stat has
 * to answer the question.
 */
class AchievementReachabilityTest {

    @Test
    fun noTargetIsBeyondWhatTheShippedGameCanProduce() {
        Achievements.catalog.forEach { achievement ->
            val ceiling = ceilingFor(achievement.stat) ?: return@forEach
            assertTrue(
                achievement.target <= ceiling,
                "${achievement.id} wants ${achievement.stat} at ${achievement.target}, " +
                    "and the game tops out at $ceiling",
            )
        }
    }

    @Test
    fun theBoardSizesTheCatalogTalksAboutExist() {
        // Every "on a 7x7 or bigger" and "on a full-size board" counter is dead
        // weight if the packs never ship one, and the failure is silent.
        assertEquals(
            Board.MAX_SIZE,
            AchievementCounters.MAX_BOARD_SIZE,
            "the catalog's idea of a full-size board has drifted from the puzzle module's",
        )
        assertEquals(
            Board.MIN_SIZE,
            ScoreLadder.MIN_BOARD_SIZE,
            "the score ladder's idea of the first board has drifted from the puzzle module's",
        )
        assertTrue(
            allLevels.any { it.size >= AchievementCounters.BIG_BOARD_SIZE },
            "no shipped level is a big board",
        )
        assertTrue(
            allLevels.any { it.size >= AchievementCounters.MAX_BOARD_SIZE },
            "no shipped level is full size",
        )
        assertEquals(
            ScoreLadder.MIN_BOARD_SIZE,
            allLevels.minOf { it.size },
            "the score ladder prices its first rung on a board no pack ships",
        )
    }

    @Test
    fun everyScoreTargetIsEarnableByARunSomebodyCouldActuallyPlay() {
        // The hole in `noTargetIsBeyondWhatTheShippedGameCanProduce`, which is
        // the test that would have caught the 10x rescale and is still the first
        // line of defence. Its ceiling is par, and par is the score for a board
        // solved in *zero* elapsed milliseconds — `Scoring.parScore`'s own KDoc
        // says it is a number no human equals. So a target at 99% of par passes
        // it and is unearnable, which is the third-paw bug wearing the ceiling
        // test as a disguise.
        //
        // This one plays instead, through the same `placement`/`complete` calls
        // the game makes, at a pace described in wall clock rather than as a
        // fraction of a coefficient: nine seconds a move on the biggest board,
        // clean. That is a good run, not a superhuman one, and every score badge
        // has to fall out of it.
        val best = allLevels.maxByOrNull { it.size }!!
        val banked = playCleanly(best.size, best.difficulty)

        Achievements.catalog.filter { it.stat == Stat.BestScore }.forEach { achievement ->
            assertTrue(
                achievement.target <= banked,
                "${achievement.id} wants ${achievement.target} points and a clean " +
                    "${FAST_MS_PER_ROW}ms-a-row run on a ${best.size}x${best.size} banks $banked",
            )
        }
    }

    @Test
    fun theScoreLadderClimbs() {
        // Three rungs derived from three separate `parScore` calls could come
        // out in any order if a fraction or a board size were swapped, and a
        // ladder that reads 2,300 then 450 then 1,600 unlocks backwards without
        // failing anything else here. `AchievementEngineTest.aLadderNeverRepeats
        // ARung` has the other half, that no two rungs land on the same number.
        val targets = Achievements.catalog.filter { it.stat == Stat.BestScore }.map { it.target }

        assertEquals(targets.sorted(), targets, "the score badges are out of order: $targets")
    }

    /** A whole board placed at [FAST_MS_PER_ROW] per row per move, no bones lost. */
    private fun playCleanly(size: Int, difficulty: Int): Int {
        var card = ScoreCard.Empty
        repeat(size) {
            card = Scoring.placement(card, size, millisSinceLastPlacement = FAST_MS_PER_ROW * size).card
        }
        return Scoring.complete(card, size, difficulty, ScoringConfig.MAX_LIVES).total
    }

    @Test
    fun theTopScoreBadgeNeedsTheBiggestBoards() {
        // The point of a score ladder is that grinding level 1 cannot climb it.
        val bestSmallBoard = allLevels
            .filter { it.size < AchievementCounters.BIG_BOARD_SIZE }
            .maxOf { Scoring.parScore(it.size, it.difficulty) }
        val topTarget = Achievements.catalog
            .filter { it.stat == Stat.BestScore }
            .maxOf { it.target }

        assertTrue(
            topTarget > bestSmallBoard,
            "a perfect small board scores $bestSmallBoard, which already earns the $topTarget badge",
        )
    }

    private fun ceilingFor(stat: Stat): Long? = when (stat) {
        // Par is every placement at full combo and full speed with all three
        // bones intact, so it is also the most the formula can ever pay.
        Stat.BestScore -> allLevels.maxOf { Scoring.parScore(it.size, it.difficulty) }.toLong()

        // A combo is consecutive correct placements and a board holds one dog
        // per row, so the longest possible run is the board's own size.
        Stat.BestCombo -> largestBoard
        Stat.LargestGridCleared -> largestBoard

        // First-clear gated, so the campaign's length is the end of it.
        Stat.LevelsCleared -> LevelPacks.campaign.size.toLong()

        // A streak repeats the pool once it runs out, and a badge that needed
        // more days than there are boards would be asking for a replay of a
        // board the player has already seen.
        Stat.BestDailyStreak -> LevelPacks.daily.size.toLong()

        // Everything else is a tally or a run that only time bounds.
        Stat.DailiesCleared,
        Stat.FlawlessClears,
        Stat.FlawlessStreak,
        Stat.BestFlawlessStreak,
        Stat.FlawlessBigBoardClears,
        Stat.ThreePawClears,
        Stat.ThreePawStreak,
        Stat.BestThreePawStreak,
        Stat.MaxBoardThreePawClears,
        Stat.ComebackClears,
        Stat.BigBoardComebackClears,
        Stat.RedemptionClears,
        Stat.BoosterFreeClears,
        Stat.BoosterFreeStreak,
        Stat.BestBoosterFreeStreak,
        Stat.AssistedClears,
        Stat.SprintClears,
        Stat.FlashClears,
        Stat.BigBoardSprintClears,
        Stat.MaxBoardSprintClears,
        Stat.BigBoardClears,
        Stat.MaxBoardClears,
        Stat.NightClears,
        Stat.DawnClears,
        Stat.DailyFlawlessClears,
        Stat.DailyThreePawClears,
        Stat.MinutesPlayed,
        -> null
    }

    private val allLevels: List<LevelDefinition>
        get() = LevelPacks.campaign.levels + LevelPacks.daily.levels

    private val largestBoard: Long get() = allLevels.maxOf { it.size }.toLong()

    private companion object {
        /**
         * The pace [everyScoreTargetIsEarnableByARunSomebodyCouldActuallyPlay]
         * plays at, in milliseconds per row of board per placement — nine
         * seconds a move on a 10x10, a minute and a half for the board. Per row
         * because a move on a 10x10 is not the same amount of work as one on a
         * 4x4, and the same number in `ScoringTest` is the pace that earns three
         * paws on every shape the campaign ships.
         */
        const val FAST_MS_PER_ROW = 900L
    }
}
