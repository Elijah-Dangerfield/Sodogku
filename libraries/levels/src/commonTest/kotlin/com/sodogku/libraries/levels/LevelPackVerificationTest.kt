package com.sodogku.libraries.levels

import com.sodogku.libraries.puzzle.Board
import com.sodogku.libraries.puzzle.Difficulty
import com.sodogku.libraries.puzzle.PuzzleSolver
import com.sodogku.libraries.puzzle.isSolvedBy
import com.sodogku.libraries.puzzle.structuralProblems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one thing standing between an unsolvable level and the store.
 *
 * A bad level cannot be recalled: it ships inside the binary, and a player stuck
 * on level 312 has no route past it but an app update. So every shipped board is
 * re-solved from scratch here, on every build, with the same solver the app
 * hints with.
 *
 * This deliberately re-derives rather than trusts. The generator already
 * checked uniqueness; this exists precisely for the case where the generator's
 * check was wrong.
 */
class LevelPackVerificationTest {

    @Test
    fun everyCampaignLevelHasExactlyOneSolutionAndItIsTheOneWeShipped() {
        verify(LevelPacks.campaign)
    }

    @Test
    fun everyDailyLevelHasExactlyOneSolutionAndItIsTheOneWeShipped() {
        verify(LevelPacks.daily)
    }

    @Test
    fun campaignHasTheFullFiveHundredLevels() {
        assertEquals(EXPECTED_CAMPAIGN_SIZE, LevelPacks.campaign.size)
    }

    @Test
    fun dailyPoolCoversTwoYears() {
        assertTrue(
            LevelPacks.daily.size >= DAYS_IN_TWO_YEARS,
            "daily pool is ${LevelPacks.daily.size}, which wraps inside two years",
        )
    }

    @Test
    fun levelIdsAreDenseAndOneBased() {
        listOf(LevelPacks.campaign, LevelPacks.daily).forEach { pack ->
            assertEquals(
                (1..pack.size).toList(),
                pack.levels.map { it.id },
                "${pack.kind} ids must run 1..n with no gaps — progress is keyed on them",
            )
        }
    }

    @Test
    fun noBoardIsDuplicatedWithinAPack() {
        listOf(LevelPacks.campaign, LevelPacks.daily).forEach { pack ->
            val distinct = pack.levels.map { it.board }.distinct()

            assertEquals(pack.size, distinct.size, "${pack.kind} contains duplicate boards")
        }
    }

    @Test
    fun campaignGridSizesNeverShrink() {
        // The curve is the product. If a 10x10 turns up at level 40 the bands
        // have been scrambled, and no other test would notice.
        val sizes = LevelPacks.campaign.levels.map { it.size }

        assertEquals(sizes.sorted(), sizes, "campaign grid sizes must be non-decreasing")
        assertEquals(Board.MIN_SIZE, sizes.first())
        assertEquals(Board.MAX_SIZE, sizes.last())
    }

    @Test
    fun theCampaignIsTheCurveItDeclares() {
        // The curve is the product and the pack is the only copy of it, so
        // "regenerated with a different seed" and "quietly lost its ramp" used
        // to look identical in a diff of 500 generated lines. This asserts the
        // shipped order against `LevelCurve` level by level, which means a
        // re-curve is a deliberate edit to a list a person can read.
        val shipped = LevelPacks.campaign.levels.map { LevelShape(it.size, it.difficulty) }

        assertEquals(LevelCurve.campaignShape.size, shipped.size)
        val drifted = shipped.indices.filter { shipped[it] != LevelCurve.campaignShape[it] }
        assertTrue(
            drifted.isEmpty(),
            "${drifted.size} level(s) off the declared curve, first at id ${drifted.firstOrNull()?.plus(1)}: " +
                drifted.take(5).joinToString("\n") { index ->
                    "  level ${index + 1}: pack has ${shipped[index]}, " +
                        "curve says ${LevelCurve.campaignShape[index]}"
                },
        )
    }

    @Test
    fun theDailyPoolHoldsTheMixItDeclares() {
        // By multiset, not in order: the daily is shuffled on purpose, so
        // there is no level 37 of the daily to pin.
        val shipped = LevelPacks.daily.levels
            .groupingBy { LevelShape(it.size, it.difficulty) }
            .eachCount()
        val declared = LevelCurve.dailyShape.groupingBy { it }.eachCount()

        assertEquals(declared, shipped)
    }

    @Test
    fun theCampaignReachesRealDifficultyInsideTheFirstSession() {
        // The complaint this curve was rebuilt for: "it stays at easy
        // difficulty for too long." Stated as the two boundaries a player
        // feels rather than as a distribution, because a distribution can be
        // right on average and still put the whole ramp after level 100.
        //
        // The window is generous on purpose — this is a floor on the pace of
        // the ramp, not a restatement of `LevelCurve`, which the test above
        // already pins exactly.
        val firstTierThree = LevelPacks.campaign.levels.first { it.difficulty >= 3 }.id
        val firstTierFour = LevelPacks.campaign.levels.first { it.difficulty >= 4 }.id

        assertTrue(firstTierThree <= FIRST_REAL_DEDUCTION_BY, "first tier-3 level is $firstTierThree")
        assertTrue(firstTierFour <= FIRST_CONTRADICTION_BY, "first tier-4 level is $firstTierFour")
        assertEquals(
            Difficulty.EASIEST,
            LevelPacks.campaign.levels.first().difficulty,
            "level 1 runs behind the tutorial and has to fall out on its own",
        )
    }

    @Test
    fun eachCampaignBandOpensNoHarderThanItEnds() {
        LevelPacks.campaign.levels
            .groupBy { it.size }
            .forEach { (size, band) ->
                val difficulties = band.map { it.difficulty }

                assertEquals(
                    difficulties.sorted(),
                    difficulties,
                    "band ${size}x$size must ramp, so a size change never lands on a wall",
                )
            }
    }

    @Test
    fun difficultyIsAlwaysDeducible() {
        // A level scored BEYOND_DEDUCTION is one no reasoning solves, which
        // means the hint engine cannot help a stuck player on it either.
        (LevelPacks.campaign.levels + LevelPacks.daily.levels).forEach { level ->
            assertTrue(
                level.difficulty in Difficulty.EASIEST until Difficulty.BEYOND_DEDUCTION,
                "level ${level.id} scored ${level.difficulty}",
            )
        }
    }

    @Test
    fun everyBakedDifficultyMatchesWhatTheEngineNowSays() {
        // The baked number is a cache of `Difficulty.score`, and nothing else
        // checked the two still agree. When a tier-2 technique was fixed on
        // 2026-09-07 the whole pack silently re-rated and every other test here
        // stayed green, because they only ask whether the stored numbers are
        // in range and ordered — not whether they are still true.
        //
        // A failure here means the engine changed and the packs need
        // regenerating: `./gradlew :tools:level-generator:run`.
        val stale = (LevelPacks.campaign.levels + LevelPacks.daily.levels)
            .mapNotNull { level ->
                val scored = Difficulty.score(level.board)
                "level ${level.id}: stored ${level.difficulty}, engine says $scored"
                    .takeIf { scored != level.difficulty }
            }

        assertTrue(stale.isEmpty(), "${stale.size} level(s) re-rated:\n${stale.take(10).joinToString("\n")}")
    }

    @Test
    fun encodingRoundTrips() {
        LevelPacks.campaign.levels.forEach { level ->
            assertEquals(level, LevelCodec.decode(LevelCodec.encode(level)))
        }
    }

    @Test
    fun dailyForWrapsAcrossTheEpochAndNeverThrows() {
        val pool = LevelPacks.daily.size

        assertEquals(LevelPacks.daily[0], LevelPacks.dailyFor(0))
        assertEquals(LevelPacks.daily[1], LevelPacks.dailyFor(1))
        assertEquals(LevelPacks.daily[0], LevelPacks.dailyFor(pool.toLong()))
        // Negative epoch days are only reachable by a device clock set before
        // 1970, but a crash there would be a crash on the daily card.
        assertEquals(LevelPacks.daily[pool - 1], LevelPacks.dailyFor(-1))
    }

    private fun verify(pack: LevelPack) {
        pack.levels.forEach { level ->
            val problems = level.board.structuralProblems()
            assertTrue(problems.isEmpty(), "level ${level.id}: $problems\n${level.board}")

            assertEquals(
                level.size,
                level.board.regions.toSet().size,
                "level ${level.id} must have exactly ${level.size} regions",
            )

            assertTrue(
                level.board.isSolvedBy(level.solution),
                "level ${level.id}'s shipped solution does not solve it\n${level.board}",
            )

            assertEquals(
                level.solution,
                PuzzleSolver.uniqueSolutionOrNull(level.board),
                "level ${level.id} is not uniquely solvable by the shipped answer\n${level.board}",
            )
        }
    }

    private companion object {
        const val EXPECTED_CAMPAIGN_SIZE = 500
        const val DAYS_IN_TWO_YEARS = 730

        /**
         * A first session is about half an hour, which on the opening bands is
         * somewhere around forty levels. Both boundaries have to land inside
         * it, or a player decides whether the game is worth their time on
         * evidence that is nothing like the game.
         */
        const val FIRST_REAL_DEDUCTION_BY = 30
        const val FIRST_CONTRADICTION_BY = 45
    }
}
