package com.sodogku.libraries.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoringTest {

    private val config = ScoringConfig.Default

    @Test
    fun firstPlacementOnAFourByFourIsWorthBaseTimesSize() {
        // Worked example: 10 base x 4 size x 1.0 combo x 1.0 speed.
        val scored = Scoring.placement(ScoreCard.Empty, size = 4, millisSinceLastPlacement = null)

        assertEquals(40, scored.points)
        assertEquals(40, scored.card.total)
        assertEquals(1, scored.card.combo)
    }

    @Test
    fun sizeScalesEveryPlacement() {
        val small = Scoring.placement(ScoreCard.Empty, size = 4, millisSinceLastPlacement = null)
        val large = Scoring.placement(ScoreCard.Empty, size = 10, millisSinceLastPlacement = null)

        assertEquals(small.points * 10 / 4, large.points)
    }

    @Test
    fun comboRampsByOneStepPerConsecutivePlacement() {
        assertEquals(1.0, Scoring.comboMultiplier(0))
        assertEquals(1.08, Scoring.comboMultiplier(1), ABSOLUTE_TOLERANCE)
        assertEquals(1.40, Scoring.comboMultiplier(5), ABSOLUTE_TOLERANCE)
    }

    @Test
    fun comboIsCapped() {
        assertEquals(config.comboMax, Scoring.comboMultiplier(1_000))
    }

    @Test
    fun aStrikeResetsTheComboButKeepsThePoints() {
        var card = ScoreCard.Empty
        repeat(4) { card = Scoring.placement(card, size = 8, millisSinceLastPlacement = null).card }
        val earned = card.total
        assertEquals(4, card.combo)

        card = Scoring.strike(card)

        assertEquals(0, card.combo)
        assertEquals(earned, card.total, "a strike must not claw back points already earned")
    }

    @Test
    fun theNextPlacementAfterAStrikeIsBackAtBaseRate() {
        var card = ScoreCard.Empty
        repeat(4) { card = Scoring.placement(card, size = 8, millisSinceLastPlacement = null).card }
        val beforeStrike = Scoring.placement(card, size = 8, millisSinceLastPlacement = null).points

        val afterStrike = Scoring.placement(
            Scoring.strike(card),
            size = 8,
            millisSinceLastPlacement = null,
        ).points

        assertTrue(afterStrike < beforeStrike, "the streak has to be worth something")
        assertEquals(80, afterStrike)
    }

    @Test
    fun speedMultiplierDecaysLinearlyAcrossTheWindow() {
        val size = ScoringConfig.SPEED_WINDOW_REFERENCE_SIZE
        val window = Scoring.speedWindowMsFor(size)

        assertEquals(config.speedWindowMs, window, "the reference board gets the configured window")
        assertEquals(config.speedMaxMultiplier, Scoring.speedMultiplier(size, 0))
        assertEquals(1.30, Scoring.speedMultiplier(size, window / 2), ABSOLUTE_TOLERANCE)
        assertEquals(1.0, Scoring.speedMultiplier(size, window))
        assertEquals(1.0, Scoring.speedMultiplier(size, window * 10))
    }

    @Test
    fun theSpeedWindowGrowsWithTheGrid() {
        // The fix for "I solve fast and still get two paws". A flat window is
        // an age on a 4x4 and a blink on a 10x10, so above about 6x6 every
        // placement fell outside it and the multiplier was pinned at 1.0.
        val small = Scoring.speedWindowMsFor(4)
        val large = Scoring.speedWindowMsFor(10)

        assertEquals(config.speedWindowMs, small)
        assertEquals(small * 10 / 4, large)
        assertTrue(
            Scoring.speedMultiplier(10, TWELVE_SECONDS) > 1.0,
            "twelve seconds on a 10x10 has to still be worth a bonus",
        )
        assertEquals(
            1.0,
            Scoring.speedMultiplier(4, TWELVE_SECONDS),
            "the same twelve seconds on a 4x4 is not fast at all",
        )
    }

    @Test
    fun unknownTimingScoresAtBaseRateRatherThanGuessing() {
        assertEquals(1.0, Scoring.speedMultiplier(size = 8, millisSinceLastPlacement = null))
    }

    @Test
    fun negativeElapsedTimeCannotInflateTheBonus() {
        // A monotonic clock should never hand us this, but a backgrounded app
        // and a resumed timer are exactly where it would come from.
        assertEquals(config.speedMaxMultiplier, Scoring.speedMultiplier(8, -5_000))
    }

    @Test
    fun completionBonusGrowsWithSizeDifficultyAndSurvivingLives() {
        val base = Scoring.completionBonus(size = 6, difficulty = 1, livesRemaining = 0)
        val bigger = Scoring.completionBonus(size = 9, difficulty = 1, livesRemaining = 0)
        val harder = Scoring.completionBonus(size = 6, difficulty = 4, livesRemaining = 0)
        val cleaner = Scoring.completionBonus(size = 6, difficulty = 1, livesRemaining = 3)

        assertTrue(bigger > base)
        assertTrue(harder > base)
        assertTrue(cleaner > base)
    }

    @Test
    fun completionBonusWorkedExample() {
        // 25 base x 6 size x (1 + 2 tiers above one x 0.2) x (1 + 3 lives x 0.5)
        assertEquals(525, Scoring.completionBonus(size = 6, difficulty = 3, livesRemaining = 3))
    }

    @Test
    fun completeAddsTheBonusToTheRunningTotal() {
        val card = ScoreCard(total = 5_000)

        val finished = Scoring.complete(card, size = 8, difficulty = 2, livesRemaining = 2)

        assertEquals(5_000 + Scoring.completionBonus(8, 2, 2), finished.total)
    }

    @Test
    fun anUnfinishedLevelEarnsNoPaws() {
        val par = Scoring.parScore(size = 7, difficulty = 2)

        assertEquals(0, Scoring.paws(par, size = 7, difficulty = 2, completed = false))
    }

    @Test
    fun finishingAtAllEarnsOnePaw() {
        assertEquals(Scoring.ONE_PAW, Scoring.paws(0, size = 7, difficulty = 2, completed = true))
    }

    @Test
    fun parIsAThreePawScore() {
        val par = Scoring.parScore(size = 7, difficulty = 2)

        assertEquals(
            Scoring.THREE_PAWS,
            Scoring.paws(par, size = 7, difficulty = 2, completed = true),
        )
    }

    @Test
    fun pawThresholdsSitWhereTheConfigSaysTheyDo() {
        val size = 8
        val difficulty = 3
        val par = Scoring.parScore(size, difficulty)

        fun pawsAt(fraction: Double) =
            Scoring.paws((par * fraction).toInt(), size, difficulty, completed = true)

        assertEquals(Scoring.ONE_PAW, pawsAt(config.twoPawFraction - 0.05))
        assertEquals(Scoring.TWO_PAWS, pawsAt(config.twoPawFraction + 0.01))
        assertEquals(Scoring.TWO_PAWS, pawsAt(config.threePawFraction - 0.05))
        assertEquals(Scoring.THREE_PAWS, pawsAt(config.threePawFraction + 0.01))
    }

    @Test
    fun aPerfectRunClearsPar() {
        // Drives the real API rather than the formula: every placement instant,
        // no strikes, all lives intact. If this ever scores below par, three
        // paws would be unreachable and nobody would find out from a unit test
        // of the pieces.
        listOf(4, 7, 10).forEach { size ->
            var card = ScoreCard.Empty
            repeat(size) { card = Scoring.placement(card, size, millisSinceLastPlacement = 0).card }
            card = Scoring.complete(card, size, difficulty = 3, livesRemaining = ScoringConfig.MAX_LIVES)

            assertEquals(
                Scoring.THREE_PAWS,
                Scoring.paws(card.total, size, difficulty = 3, completed = true),
                "a flawless ${size}x$size run scored ${card.total} against par " +
                    "${Scoring.parScore(size, 3)}",
            )
        }
    }

    @Test
    fun theWorstCompletableRunStillOnlyEarnsOnePaw() {
        // The genuinely worst run a player can finish: two strikes spent (you
        // are out at three), every placement slow. If this scores two paws the
        // rating carries no information, which is exactly what the first pass
        // at these coefficients did.
        val size = 8
        var card = ScoreCard.Empty
        repeat(size) { index ->
            if (index < ScoringConfig.MAX_LIVES - 1) card = Scoring.strike(card)
            card = Scoring.placement(card, size, millisSinceLastPlacement = 30_000).card
        }
        card = Scoring.complete(card, size, difficulty = 3, livesRemaining = 1)

        assertEquals(
            Scoring.ONE_PAW,
            Scoring.paws(card.total, size, difficulty = 3, completed = true),
            "scored ${card.total} against par ${Scoring.parScore(size, 3)}",
        )
    }

    @Test
    fun aCleanButUnhurriedRunEarnsTwoPaws() {
        // The middle band has to be reachable too, or paws are just a
        // pass/perfect flag. Twenty seconds a move on an 8x8 is well past that
        // board's speed window, so the bonus is gone and only the clean sheet
        // is left.
        val size = 8
        var card = ScoreCard.Empty
        repeat(size) { card = Scoring.placement(card, size, millisSinceLastPlacement = 20_000).card }
        card = Scoring.complete(card, size, difficulty = 3, livesRemaining = 3)

        assertEquals(
            Scoring.TWO_PAWS,
            Scoring.paws(card.total, size, difficulty = 3, completed = true),
            "scored ${card.total} against par ${Scoring.parScore(size, 3)}",
        )
    }

    @Test
    fun allThreeRatingsAreReachableOnEveryBoardShapeTheCampaignShips() {
        // The failure this exists for is a *rating nobody can get*, which no
        // assertion about a particular score would catch: three paws was
        // unreachable above 6x6 for months while every worked example above
        // stayed green, because they all pick their own board.
        //
        // So it sweeps the shapes the packs actually contain — 4x4 to 10x10,
        // tiers 1 to 4 (tier 5 is BEYOND_DEDUCTION and never ships) — and asks
        // for each one that all three ratings have a run that earns them. The
        // paces are wall-clock per row of board, not fractions of a config
        // value, so a retune that quietly moves the window still has to keep
        // real play inside the bands.
        val failures = mutableListOf<String>()

        (SMALLEST_BOARD..BIGGEST_BOARD).forEach { size ->
            (1..SHIPPED_MAX_DIFFICULTY).forEach { difficulty ->
                listOf(
                    Triple(Scoring.THREE_PAWS, FAST_MS_PER_ROW, 0),
                    Triple(Scoring.TWO_PAWS, UNHURRIED_MS_PER_ROW, 0),
                    Triple(Scoring.ONE_PAW, SLOW_MS_PER_ROW, ScoringConfig.MAX_LIVES - 1),
                ).forEach { (expected, msPerRow, strikes) ->
                    val score = runAt(size, difficulty, msPerRow, strikes)
                    val actual = Scoring.paws(score, size, difficulty, completed = true)
                    if (actual != expected) {
                        val fraction = score.toDouble() / Scoring.parScore(size, difficulty)
                        failures += "${size}x$size tier $difficulty at ${msPerRow}ms/row with " +
                            "$strikes strike(s): expected $expected paws, got $actual " +
                            "(${(fraction * PERCENT).toInt()}% of par)"
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun theThirdPawIsWhatSpeedBuys() {
        // The half the sweep above cannot say on its own: that the gap between
        // two paws and three is the *pace*, on every board and not just the
        // small ones. Pinned as a score comparison as well as a rating, so a
        // window that stopped scaling would fail here with a readable number
        // rather than only as a missing rating.
        (SMALLEST_BOARD..BIGGEST_BOARD).forEach { size ->
            val fast = runAt(size, difficulty = 4, msPerRow = FAST_MS_PER_ROW, strikes = 0)
            val unhurried = runAt(size, difficulty = 4, msPerRow = UNHURRIED_MS_PER_ROW, strikes = 0)

            assertTrue(
                fast > unhurried,
                "on ${size}x$size the fast run scored $fast and the unhurried one $unhurried",
            )
        }
    }

    /**
     * A whole attempt at a steady pace of [msPerRow] per row of board per
     * placement, with [strikes] wrong guesses taken early, returned as the
     * total after the completion bonus.
     *
     * Per row rather than flat, because a move on a 10x10 is not the same
     * amount of work as a move on a 4x4 and a test that pretended otherwise
     * would be asking every board for the same wall clock.
     */
    private fun runAt(size: Int, difficulty: Int, msPerRow: Long, strikes: Int): Int {
        var card = ScoreCard.Empty
        repeat(size) { index ->
            if (index in 1..strikes) card = Scoring.strike(card)
            card = Scoring.placement(card, size, millisSinceLastPlacement = msPerRow * size).card
        }
        return Scoring.complete(
            card,
            size,
            difficulty,
            livesRemaining = ScoringConfig.MAX_LIVES - strikes,
        ).total
    }

    @Test
    fun placementsAndCompletionAreRoughlyBalanced() {
        // The balance the defaults are tuned for. If a future retune makes the
        // completion bonus dominate, a fast clean solve stops scoring
        // meaningfully better than a slow scrappy one.
        val size = 9
        var card = ScoreCard.Empty
        repeat(size) { card = Scoring.placement(card, size, millisSinceLastPlacement = 2_000).card }
        val fromPlacements = card.total
        val fromCompletion = Scoring.completionBonus(size, difficulty = 3, livesRemaining = 3)

        val share = fromPlacements.toDouble() / (fromPlacements + fromCompletion)
        assertTrue(share in 0.45..0.75, "placements are $share of the score, expected roughly 60%")
    }

    @Test
    fun eachBoosterCostsAShareOfWhatTheRunEarned() {
        // The worked example, so the cost is a number rather than "less".
        val rate = config.boosterPenaltyRate

        assertEquals(10_000, Scoring.afterBoosters(10_000, boostersUsed = 0, rate))
        assertEquals(8_500, Scoring.afterBoosters(10_000, boostersUsed = 1, rate))
        // 0.85² is 0.7224999… in binary floating point and the result truncates
        // like every other total in here, so this is 7,224 rather than 7,225.
        assertEquals(7_224, Scoring.afterBoosters(10_000, boostersUsed = 2, rate))
    }

    @Test
    fun noNumberOfBoostersCanTakeAScoreBelowZero() {
        // The reason the cost compounds instead of subtracting. A player who
        // leans on a booster every move of a 10x10 still banks something, and
        // nothing anywhere has to clamp a negative score it was handed.
        val banked = Scoring.afterBoosters(10_000, boostersUsed = 40, config.boosterPenaltyRate)

        assertTrue(banked >= 0, "banked $banked")
        assertTrue(banked < 100, "forty boosters should have taken nearly all of it, banked $banked")
    }

    @Test
    fun theBoosterCostDoesNotMoveThePawRating() {
        // The half that is easy to get wrong: subtract points without telling
        // par and three paws quietly stops being reachable for anyone who used
        // a hint — including every player following the tutorial, which asks
        // for a sniff and a treat on level 2.
        val size = 8
        var card = ScoreCard.Empty
        repeat(size) { card = Scoring.placement(card, size, millisSinceLastPlacement = 0).card }
        card = Scoring.complete(card, size, difficulty = 3, livesRemaining = ScoringConfig.MAX_LIVES)

        val banked = Scoring.afterBoosters(card.total, boostersUsed = 2, config.boosterPenaltyRate)

        assertTrue(banked < card.total, "two boosters have to cost something")
        assertEquals(
            Scoring.THREE_PAWS,
            Scoring.paws(card.total, size, difficulty = 3, completed = true),
            "a flawless run stays a three-paw run however much help it took",
        )
    }

    @Test
    fun aZeroPenaltyRateIsHowTheCostIsSwitchedOff() {
        // The remote-config off switch, and the direction a mistyped key falls.
        val free = ScoringConfig(boosterPenaltyRate = 0.0)

        assertEquals(10_000, Scoring.afterBoosters(10_000, boostersUsed = 5, free.boosterPenaltyRate))
    }

    @Test
    fun configRejectsABoosterCostThatWouldPayPlayersToUseThem() {
        listOf(
            "negative rate" to { ScoringConfig(boosterPenaltyRate = -0.5) },
            "rate above one" to { ScoringConfig(boosterPenaltyRate = 1.5) },
        ).forEach { (name, build) ->
            assertTrue(runCatching(build).isFailure, "$name must not be constructible")
        }
    }

    @Test
    fun praiseEscalatesWithTheMultiplier() {
        assertEquals(Praise.None, Scoring.praiseFor(1.0))
        assertEquals(Praise.Nice, Scoring.praiseFor(config.nicePraiseAt))
        assertEquals(Praise.Great, Scoring.praiseFor(config.greatPraiseAt))
        assertEquals(Praise.Excellent, Scoring.praiseFor(config.excellentPraiseAt))
        assertEquals(Praise.Perfect, Scoring.praiseFor(config.perfectPraiseAt))
    }

    @Test
    fun theBestComboSurvivesTheStrikeThatEndedIt() {
        // `combo` answers "what is my run worth right now", which is zero after a
        // strike. Nothing could answer "how long was your best run", and that is
        // the question the achievement asks once the attempt is over.
        var card = ScoreCard.Empty
        repeat(5) { card = Scoring.placement(card, size = 6, null).card }
        assertEquals(5, card.combo)
        assertEquals(5, card.bestCombo)

        card = Scoring.strike(card)
        assertEquals(0, card.combo, "a strike still ends the run")
        assertEquals(5, card.bestCombo, "but it does not un-happen it")

        repeat(2) { card = Scoring.placement(card, size = 6, null).card }
        assertEquals(5, card.bestCombo, "a shorter later run must not lower the mark")

        repeat(5) { card = Scoring.placement(card, size = 6, null).card }
        assertEquals(7, card.bestCombo, "a longer one must raise it")
    }

    @Test
    fun configRejectsThresholdsThatCannotBeSatisfied() {
        val failure = runCatching {
            ScoringConfig(twoPawFraction = 0.9, threePawFraction = 0.5)
        }

        assertTrue(failure.isFailure, "a third paw easier than the second must not be constructible")
    }

    @Test
    fun configRejectsPointValuesThatBreakScoring() {
        // These two arrive from remote config, and both were unguarded. A
        // dropped minus sign put par below zero, so every player cleared the
        // three-paw threshold by scoring nothing at all; a value in the hundreds
        // of millions overflowed the `Int * size` inside `ScoreCard` before it
        // widened to Double, and came out negative for the same reason.
        listOf(
            "negative base" to { ScoringConfig(basePerPlacement = -100) },
            "zero base" to { ScoringConfig(basePerPlacement = 0) },
            "negative completion" to { ScoringConfig(completionBase = -250) },
            "overflowing base" to { ScoringConfig(basePerPlacement = 300_000_000) },
            "overflowing completion" to { ScoringConfig(completionBase = 300_000_000) },
        ).forEach { (name, build) ->
            assertTrue(runCatching(build).isFailure, "$name must not be constructible")
        }
    }

    @Test
    fun configRejectsNegativeRates() {
        // The three rates had no guard at all, while the comment above them
        // claimed the two Ints "were the only fields unguarded". A negative rate
        // does not merely lower scores, it inverts the rating: par's factor and
        // the player's move in opposite directions, so a worse run rates better.
        listOf(
            "negative combo step" to { ScoringConfig(comboStep = -0.5) },
            "negative lives rate" to { ScoringConfig(livesBonusRate = -0.5) },
            "negative difficulty rate" to { ScoringConfig(difficultyBonusRate = -0.5) },
        ).forEach { (name, build) ->
            assertTrue(runCatching(build).isFailure, "$name must not be constructible")
        }
    }

    @Test
    fun aWorseRunIsNeverRatedBetterUnderAnyLegalConfig() {
        // The property the guards exist to protect, stated directly rather than
        // as a list of forbidden numbers. This is what a negative rate broke: an
        // 8x8 run with two strikes came out two paws while the clean run beside
        // it came out one, because par had shrunk faster than the score did.
        val clean = ratedRun(strikes = 0)
        val struck = ratedRun(strikes = ScoringConfig.MAX_LIVES - 1)

        assertTrue(
            clean >= struck,
            "a two-strike run rated $struck against a clean run's $clean",
        )
    }

    /** Paws for an 8x8 tier-3 run at a fixed pace, varying only the strikes. */
    private fun ratedRun(strikes: Int): Int {
        val size = 8
        var card = ScoreCard.Empty
        repeat(size) { index ->
            if (index < strikes) card = Scoring.strike(card)
            card = Scoring.placement(card, size, millisSinceLastPlacement = 30_000).card
        }
        val lives = ScoringConfig.MAX_LIVES - strikes
        card = Scoring.complete(card, size, difficulty = 3, livesRemaining = lives)
        return Scoring.paws(card.total, size, difficulty = 3, completed = true)
    }

    @Test
    fun theLargestLegalConfigStillScoresPositively() {
        // The ceiling is only worth having if everything under it is safe, so
        // check the corner rather than trusting the arithmetic.
        val extreme = ScoringConfig(
            basePerPlacement = ScoringConfig.MAX_POINT_VALUE,
            completionBase = ScoringConfig.MAX_POINT_VALUE,
        )

        assertTrue(Scoring.parScore(BIGGEST_BOARD, MAX_DIFFICULTY, extreme) > 0)
        assertTrue(Scoring.placement(ScoreCard.Empty, BIGGEST_BOARD, null, extreme).points > 0)
    }

    @Test
    fun everyCoefficientIsOverridable() {
        // The whole point of the config object: remote config has to be able to
        // move these without a release.
        val richer = ScoringConfig(basePerPlacement = 20)

        val scored = Scoring.placement(ScoreCard.Empty, size = 5, null, richer)

        assertEquals(100, scored.points)
        assertTrue(
            Scoring.parScore(5, 2, richer) > Scoring.parScore(5, 2),
            "par has to move with the coefficients, or the paw thresholds drift",
        )
    }

    private companion object {
        const val ABSOLUTE_TOLERANCE = 1e-9

        /** The widest board the campaign ships, and the deepest tier it rates. */
        const val BIGGEST_BOARD = 10
        const val MAX_DIFFICULTY = 5

        /** The narrowest board that has any legal placement at all. */
        const val SMALLEST_BOARD = 4

        /**
         * Tier 5 is `Difficulty.BEYOND_DEDUCTION` — a board no reasoning
         * solves — and `LevelPackVerificationTest` keeps it out of both packs,
         * so no player ever meets one and the sweep does not rate one.
         */
        const val SHIPPED_MAX_DIFFICULTY = 4

        /**
         * Three paces, in milliseconds per row of board per placement, chosen
         * as descriptions of play rather than as fractions of a coefficient:
         * on a 10x10 they are 9, 25 and 40 seconds a move, or a minute and a
         * half, four minutes and six and a half for the whole board.
         */
        const val FAST_MS_PER_ROW = 900L
        const val UNHURRIED_MS_PER_ROW = 2_500L
        const val SLOW_MS_PER_ROW = 4_000L

        const val TWELVE_SECONDS = 12_000L
        const val PERCENT = 100
    }
}
