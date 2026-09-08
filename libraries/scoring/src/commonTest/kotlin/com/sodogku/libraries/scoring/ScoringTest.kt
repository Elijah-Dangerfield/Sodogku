package com.sodogku.libraries.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoringTest {

    private val config = ScoringConfig.Default

    @Test
    fun firstPlacementOnAFourByFourIsWorthBaseTimesSize() {
        // Worked example: 100 base x 4 size x 1.0 combo x 1.0 speed.
        val scored = Scoring.placement(ScoreCard.Empty, size = 4, millisSinceLastPlacement = null)

        assertEquals(400, scored.points)
        assertEquals(400, scored.card.total)
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
        assertEquals(800, afterStrike)
    }

    @Test
    fun speedMultiplierDecaysLinearlyAcrossTheWindow() {
        assertEquals(config.speedMaxMultiplier, Scoring.speedMultiplier(0))
        assertEquals(1.30, Scoring.speedMultiplier(config.speedWindowMs / 2), ABSOLUTE_TOLERANCE)
        assertEquals(1.0, Scoring.speedMultiplier(config.speedWindowMs))
        assertEquals(1.0, Scoring.speedMultiplier(config.speedWindowMs * 10))
    }

    @Test
    fun unknownTimingScoresAtBaseRateRatherThanGuessing() {
        assertEquals(1.0, Scoring.speedMultiplier(null))
    }

    @Test
    fun negativeElapsedTimeCannotInflateTheBonus() {
        // A monotonic clock should never hand us this, but a backgrounded app
        // and a resumed timer are exactly where it would come from.
        assertEquals(config.speedMaxMultiplier, Scoring.speedMultiplier(-5_000))
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
        // 250 base x 6 size x (1 + 2 tiers above one x 0.2) x (1 + 3 lives x 0.5)
        assertEquals(5250, Scoring.completionBonus(size = 6, difficulty = 3, livesRemaining = 3))
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
        // pass/perfect flag.
        val size = 8
        var card = ScoreCard.Empty
        repeat(size) { card = Scoring.placement(card, size, millisSinceLastPlacement = 12_000).card }
        card = Scoring.complete(card, size, difficulty = 3, livesRemaining = 3)

        assertEquals(
            Scoring.TWO_PAWS,
            Scoring.paws(card.total, size, difficulty = 3, completed = true),
            "scored ${card.total} against par ${Scoring.parScore(size, 3)}",
        )
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
        val doubled = ScoringConfig(basePerPlacement = 200)

        val scored = Scoring.placement(ScoreCard.Empty, size = 5, null, doubled)

        assertEquals(1_000, scored.points)
        assertTrue(
            Scoring.parScore(5, 2, doubled) > Scoring.parScore(5, 2),
            "par has to move with the coefficients, or the paw thresholds drift",
        )
    }

    private companion object {
        const val ABSOLUTE_TOLERANCE = 1e-9

        /** The widest board the campaign ships, and the deepest tier it rates. */
        const val BIGGEST_BOARD = 10
        const val MAX_DIFFICULTY = 5
    }
}
