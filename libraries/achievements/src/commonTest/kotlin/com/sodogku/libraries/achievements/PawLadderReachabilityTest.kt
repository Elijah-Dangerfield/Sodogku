package com.sodogku.libraries.achievements

import com.sodogku.libraries.levels.LevelCurve
import com.sodogku.libraries.levels.LevelShape
import com.sodogku.libraries.scoring.ScoreCard
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whether every rung of the paw ladder can be earned on every board the packs
 * actually ship.
 *
 * `ScoringTest` sweeps 4x4 to 10x10 because those are the sizes the campaign
 * ships today, and it does so from two constants of its own. This file asks
 * [LevelCurve] instead, so a new band, a re-curve, or a pack that quietly runs a
 * tier the ladder was never measured at all fail here rather than shipping. It
 * lives in this module for the same reason `AchievementReachabilityTest` does:
 * `:libraries:scoring` depends on nothing on purpose, and this is the module
 * that already has both the formula and the packs on its test classpath.
 *
 * **This is the bug that has now happened three times and been found by a human
 * each time.** A flat speed window put the top rung out of reach above 6x6; a
 * range too narrow to hold four cuts put two rungs below anything a clean run
 * could score; and par priced on the whole board took a paw off every level that
 * opens with a starter dog. All three were invisible to every worked-example
 * test in the repo, because a worked example picks one board and the failure is
 * always on a different one.
 */
class PawLadderReachabilityTest {

    @Test
    fun everyRungCanBeEarnedOnEveryShapeTheCampaignShips() {
        val earned = mutableSetOf<Int>()
        val failures = mutableListOf<String>()

        shippedShapes().forEach { (shape, placements) ->
            val name = "${shape.size}x${shape.size} tier ${shape.difficulty}" +
                if (placements < shape.size) ", starter dog" else ""
            val ratings = PACES.flatMap { pace ->
                (0..ScoringConfig.MAX_LIVES - 1).map { strikes ->
                    Triple(pace, strikes, rating(shape, placements, pace, strikes))
                }
            }
            earned += ratings.map { it.third }

            val fast = ratings.single { it.first == FAST_MS_PER_ROW && it.second == 0 }.third
            val worst = ratings.single {
                it.first == SLOW_MS_PER_ROW && it.second == ScoringConfig.MAX_LIVES - 1
            }.third

            if (fast != Scoring.MAX_PAWS) {
                failures += "$name: a clean run at ${FAST_MS_PER_ROW}ms a row earned $fast, " +
                    "so the top rung cannot be earned there"
            }
            if (worst != Scoring.ONE_PAW) {
                failures += "$name: the worst run a player can finish earned $worst"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        assertEquals(
            (Scoring.ONE_PAW..Scoring.MAX_PAWS).toSet(),
            earned,
            "no run anywhere in the shipped campaign earns one of the rungs",
        )
    }

    @Test
    fun theSweepActuallyCoversTheCampaign() {
        // The sweep above is only worth anything if it is looking at the whole
        // pack. A `campaignShape` that came back empty, or a `distinct()` that
        // collapsed it to one entry, would make every assertion above vacuous
        // and green.
        val sizes = LevelCurve.campaign.map { it.size }

        assertTrue(sizes.size >= 2, "the campaign declares only $sizes")
        assertEquals(sizes.toSet().size, sizes.size, "a grid size appears in two bands: $sizes")
        assertTrue(
            shippedShapes().size >= sizes.size * 2,
            "every size should contribute a full board and a starter-dog board",
        )
    }

    /**
     * Every distinct (size, tier) the campaign declares, each paired with both
     * the whole board and one placement fewer.
     *
     * The short one is the board `LevelCurve.opensWithStarterDog` hands a free
     * dog to, which is the first `progression.starterDogLevelsPerBand` levels of
     * every band. The count is a config value this module cannot read, but the
     * *shape* it produces is fixed: one dog down, `size - 1` left to place.
     */
    private fun shippedShapes(): List<Pair<LevelShape, Int>> =
        LevelCurve.campaignShape.distinct().flatMap { shape ->
            listOf(shape to shape.size, shape to shape.size - 1)
        }

    private fun rating(shape: LevelShape, placements: Int, msPerRow: Long, strikes: Int): Int {
        var card = ScoreCard.Empty
        repeat(placements) { index ->
            if (index in 1..strikes) card = Scoring.strike(card)
            card = Scoring.placement(card, shape.size, msPerRow * shape.size).card
        }
        val finished = Scoring.complete(
            card,
            shape.size,
            shape.difficulty,
            livesRemaining = ScoringConfig.MAX_LIVES - strikes,
        )
        return Scoring.paws(
            finished.total,
            shape.size,
            shape.difficulty,
            completed = true,
            placements = placements,
        )
    }

    private companion object {
        /**
         * Paces in milliseconds per row of board per placement, which is the
         * unit `Scoring.speedWindowMsFor` is quoted in, so one number means the
         * same fraction of the window on every size. The fast one matches
         * `AchievementReachabilityTest` and `ScoringTest`; the slow one is past
         * the window on every board, so it is the floor of what a run can score.
         */
        const val FAST_MS_PER_ROW = 900L
        const val SLOW_MS_PER_ROW = 6_000L

        val PACES = listOf(FAST_MS_PER_ROW, 2_500L, SLOW_MS_PER_ROW)
    }
}
