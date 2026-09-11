package com.sodogku.libraries.achievements

import com.sodogku.libraries.levels.LevelCurve
import com.sodogku.libraries.levels.LevelShape
import com.sodogku.libraries.scoring.ScoreCard
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.ScoringConfig
import com.sodogku.libraries.scoring.Standing
import com.sodogku.libraries.scoring.standingFor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Whether every rung of the paw ladder can be earned on every board the packs
 * actually ship.
 *
 * `ScoringTest` sweeps 4x4 to 10x10 at every tier from two constants of its own.
 * This file asks [LevelCurve] instead, so a new band, a re-curve, or a pack that
 * quietly runs a tier the ladder was never measured at all fail here rather than
 * shipping. It lives in this module for the same reason `AchievementReachabilityTest`
 * does: `:libraries:scoring` depends on nothing on purpose, and this is the module
 * that already has both the formula and the packs on its test classpath.
 *
 * **This is the bug that has now happened four times and been found by a human
 * each time.** A flat speed window put the top rung out of reach above 6x6; a
 * range too narrow to hold four cuts put two rungs below anything a clean run
 * could score; par priced on the whole board took a paw off every level that
 * opens with a starter dog; and `Standing.Sharp` was a verdict no human pace
 * could earn. All four were invisible to every worked-example test in the repo,
 * because a worked example picks one board and the failure is always on a
 * different one.
 *
 * The version of this file that shipped was invisible to them too, which is the
 * part worth remembering. Its name said every rung on every shape; what it
 * asserted per shape was rungs 1 and 5, with 2 to 4 collected into a set and
 * checked once at the end. A rung reachable on a single board and out of reach
 * on the other twenty-seven passed. It also walked the campaign only, while
 * `LevelCurve.dailyShape` ships 7x7 and 8x8 at tier 2, a pairing no campaign
 * band declares.
 *
 * So the rungs are asserted one shape at a time now, in the two directions they
 * mean different things in:
 *
 * - **Reachable**, per shape. Some run on *this* board has to clear the rung, or
 *   a player on this board cannot earn it. This is the direction every one of
 *   the four bugs failed.
 * - **Distinguishing**, across the curve. No run in the band below a rung may
 *   reach it, or the rung says nothing. Checked on full boards only, and over
 *   the whole curve rather than per shape: a board that hands out a starter dog
 *   has one fewer placement against the same completion bonus, so its bands sit
 *   a point or two of par higher and overlap the full-board ones. That overlap
 *   is a property of the shape, not a tuning error.
 */
class PawLadderReachabilityTest {

    @Test
    fun everyRungCanBeEarnedOnEveryShapeTheGameShips() {
        val failures = mutableListOf<String>()

        shippedShapes().forEach { (shape, placements) ->
            val name = describe(shape, placements)

            CUTS.forEach { cut ->
                val best = fractionOf(shape, placements, cut.above)
                if (best < cut.threshold(CONFIG)) {
                    failures += "$name: ${cut.name} at ${cut.threshold(CONFIG)} is out of reach, " +
                        "because the best run in the '${cut.above}' band only scores " +
                        "${percent(best)}% of par there"
                }
            }

            val fast = rating(shape, placements, FAST_MS_PER_ROW, strikes = 0)
            if (fast != Scoring.MAX_PAWS) {
                failures += "$name: a clean run at ${FAST_MS_PER_ROW}ms a row earned $fast, " +
                    "so the top rung cannot be earned there"
            }
            val worst = rating(shape, placements, SLOW_MS_PER_ROW, ScoringConfig.MAX_LIVES - 1)
            if (worst != Scoring.ONE_PAW) {
                failures += "$name: the best run a two-strike player can finish earned $worst"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n") + table())
    }

    @Test
    fun everyVerdictCanBeEarnedOnEveryShapeTheGameShips() {
        // The same question the sweep above asks of the paws, asked of the
        // words, because they are cut from the same par and fail the same way.
        // `Standing.Sharp` was the fourth instance: it was five paws' worth of
        // score with a bone lost, and `ScoringConfig`'s own KDoc says a clean
        // run is necessary for five paws, so the two sentences defined it as
        // empty. "Sharp work" was a shipped string that never rendered.
        //
        // The paw sweep could not have caught it and still cannot. Paws and
        // verdicts read the same thresholds but not the same way: only the
        // verdict reads lives, so a rung can be perfectly reachable while the
        // verdict cut from it is not.
        //
        // Swept over the same grid rather than pinned pace by pace. Which pace
        // earns which word is a tuning and is allowed to move; a word no run on
        // this board can produce is not.
        val failures = mutableListOf<String>()

        shippedShapes().forEach { (shape, placements) ->
            val earned = BANDS.map { band -> verdict(shape, placements, band) }.toSet()
            val missing = Standing.entries - earned
            if (missing.isNotEmpty()) {
                failures += "${describe(shape, placements)}: no run earns $missing there, " +
                    "at any pace from ${FAST_MS_PER_ROW}ms to ${SLOW_MS_PER_ROW}ms a row"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n") + table())
    }

    @Test
    fun noRungCanBeEarnedByTheRunsInTheBandBelowIt() {
        // The other half of a cut, and the half that keeps the rating worth
        // reading. A threshold under the ceiling of the band below it is earned
        // by the runs it was meant to separate out, so every player clears it
        // and the paw carries no information. That is how the first pass at
        // these coefficients failed, with the best two-strike run landing
        // above the two-paw line.
        val failures = mutableListOf<String>()

        CUTS.forEach { cut ->
            val ceiling = shippedShapes()
                .filter { (shape, placements) -> placements == shape.size }
                .maxOf { (shape, placements) -> fractionOf(shape, placements, cut.below) }
            if (ceiling >= cut.threshold(CONFIG)) {
                failures += "${cut.name} at ${cut.threshold(CONFIG)} separates nothing: a full-board " +
                    "run in the '${cut.below}' band already scores ${percent(ceiling)}% of par"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n") + table())
    }

    @Test
    fun theSweepActuallyCoversBothCurves() {
        // The sweeps above are only worth anything if they are looking at the
        // whole of what ships. A `campaignShape` that came back empty, or a
        // `distinct()` that collapsed it to one entry, would make every
        // assertion above vacuous and green.
        //
        // Counted by distinct *size* rather than by band, because a size can be
        // declared by several bands and is: levels 501 to 1000 are five more
        // 10x10 bands, so six of them share that size. An earlier version of
        // this guard asserted one band per size and went red the moment the
        // campaign grew, which made it a tripwire for the pack rather than for
        // itself.
        val campaignSizes = LevelCurve.campaign.map { it.size }.toSet()
        val dailySizes = LevelCurve.daily.map { it.size }.toSet()

        assertTrue(campaignSizes.size >= 2, "the campaign declares only $campaignSizes")
        assertTrue(dailySizes.isNotEmpty(), "the daily curve declares no sizes")
        assertTrue(
            shippedShapes().size >= (campaignSizes + dailySizes).size * 2,
            "every size should contribute a full board and a starter-dog board",
        )
        // The daily is the half this file used to skip, and it is not a subset
        // of the campaign: it ships 7x7 and 8x8 at tier 2, which no campaign
        // band declares. If that ever stops being true the sweep is still
        // correct, but the reason for walking both curves has gone and somebody
        // should know.
        assertTrue(
            LevelCurve.dailyShape.any { it !in LevelCurve.campaignShape },
            "the daily curve no longer contributes a shape of its own",
        )
    }

    /**
     * Every distinct (size, tier) either curve declares, each paired with both
     * the whole board and one placement fewer.
     *
     * The short one is the board `LevelCurve.opensWithStarterDog` hands a free
     * dog to, which is the first `progression.starterDogLevelsPerBand` levels of
     * every band. The count is a config value this module cannot read, but the
     * *shape* it produces is fixed: one dog down, `size - 1` left to place.
     */
    private fun shippedShapes(): List<Pair<LevelShape, Int>> =
        (LevelCurve.campaignShape + LevelCurve.dailyShape).distinct().flatMap { shape ->
            listOf(shape to shape.size, shape to shape.size - 1)
        }

    private fun describe(shape: LevelShape, placements: Int): String =
        "${shape.size}x${shape.size} tier ${shape.difficulty}" +
            if (placements < shape.size) ", starter dog" else ""

    /** What fraction of par a whole attempt in [band] scores on this shape. */
    private fun fractionOf(shape: LevelShape, placements: Int, band: Band): Double =
        score(shape, placements, band.msPerRow, band.strikes).toDouble() /
            Scoring.parScore(shape.size, shape.difficulty, placements)

    private fun verdict(shape: LevelShape, placements: Int, band: Band): Standing =
        checkNotNull(
            Scoring.standingFor(
                score(shape, placements, band.msPerRow, band.strikes),
                shape.size,
                shape.difficulty,
                completed = true,
                livesRemaining = ScoringConfig.MAX_LIVES - band.strikes,
                placements = placements,
            ),
        ) { "a finished run has no verdict" }

    private fun rating(shape: LevelShape, placements: Int, msPerRow: Long, strikes: Int): Int =
        Scoring.paws(
            score(shape, placements, msPerRow, strikes),
            shape.size,
            shape.difficulty,
            completed = true,
            placements = placements,
        )

    private fun score(shape: LevelShape, placements: Int, msPerRow: Long, strikes: Int): Int {
        var card = ScoreCard.Empty
        repeat(placements) { index ->
            if (index in 1..strikes) card = Scoring.strike(card)
            card = Scoring.placement(card, shape.size, msPerRow * shape.size).card
        }
        return Scoring.complete(
            card,
            shape.size,
            shape.difficulty,
            livesRemaining = ScoringConfig.MAX_LIVES - strikes,
        ).total
    }

    /**
     * The whole measured table, appended to any failure above.
     *
     * A broken tuning is only diagnosable next to the numbers it was measured
     * against, and the numbers move with every coefficient, so printing them
     * beats keeping a copy in a comment that goes stale.
     */
    private fun table(): String {
        val bands = CUTS.flatMap { listOf(it.below, it.above) }.distinct()
        return "\n\nmeasured bands, over every shape both curves ship:\n" +
            bands.joinToString("\n") { band ->
                val all = shippedShapes().map { (shape, placements) -> fractionOf(shape, placements, band) }
                val full = shippedShapes()
                    .filter { (shape, placements) -> placements == shape.size }
                    .map { (shape, placements) -> fractionOf(shape, placements, band) }
                "  $band: ${percent(full.min())}% .. ${percent(full.max())}% of par on a full board, " +
                    "${percent(all.min())}% .. ${percent(all.max())}% counting starter-dog boards"
            }
    }

    private fun percent(fraction: Double): Int = (fraction * PERCENT).toInt()

    /**
     * A pace and a number of strikes, which together are one description of how
     * a run went. Every rung sits in the gap between two of these.
     */
    private data class Band(val strikes: Int, val msPerRow: Long) {
        override fun toString(): String = "$strikes strike(s) at ${msPerRow}ms a row"
    }

    /**
     * One rung, the band that must not reach it and the band that must.
     *
     * Read off `ScoringConfig`'s own table, so the two stay describing the same
     * ladder: a paw is awarded for finishing after mistakes, for finishing
     * clean, for finishing clean and briskly, and for finishing clean and fast.
     */
    private data class Cut(
        val name: String,
        val threshold: (ScoringConfig) -> Double,
        val below: Band,
        val above: Band,
    )

    private companion object {
        val CONFIG = ScoringConfig.Default

        /**
         * Paces in milliseconds per row of board per placement, which is the
         * unit `Scoring.speedWindowMsFor` is quoted in, so one number means the
         * same fraction of the window on every size. The fast one matches
         * `AchievementReachabilityTest` and `ScoringTest`; the slow one is past
         * the window on every board, so it is the floor of what a run can score.
         */
        const val FAST_MS_PER_ROW = 900L
        const val UNHURRIED_MS_PER_ROW = 2_500L
        const val SLOW_MS_PER_ROW = 6_000L

        const val PERCENT = 100

        /**
         * Every way a run can go that this file measures: three paces by three
         * strike counts. The two ends are load-bearing — a clean fast run is
         * the top of what the formula pays and a two-strike run past the window
         * is the bottom — and the middle is there so a rung or a word that only
         * a sprint can reach shows up as one that nothing ordinary reaches.
         */
        val BANDS = listOf(FAST_MS_PER_ROW, UNHURRIED_MS_PER_ROW, SLOW_MS_PER_ROW).flatMap { pace ->
            (0 until ScoringConfig.MAX_LIVES).map { strikes -> Band(strikes, pace) }
        }

        val CUTS = listOf(
            Cut(
                "twoPawFraction", { it.twoPawFraction },
                below = Band(ScoringConfig.MAX_LIVES - 1, SLOW_MS_PER_ROW),
                above = Band(1, SLOW_MS_PER_ROW),
            ),
            Cut(
                "threePawFraction", { it.threePawFraction },
                below = Band(1, SLOW_MS_PER_ROW),
                above = Band(0, SLOW_MS_PER_ROW),
            ),
            Cut(
                "fourPawFraction", { it.fourPawFraction },
                below = Band(0, SLOW_MS_PER_ROW),
                above = Band(0, UNHURRIED_MS_PER_ROW),
            ),
            Cut(
                "fivePawFraction", { it.fivePawFraction },
                below = Band(0, UNHURRIED_MS_PER_ROW),
                above = Band(0, FAST_MS_PER_ROW),
            ),
        )
    }
}
