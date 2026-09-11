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
        assertEquals(1.50, Scoring.speedMultiplier(size, window / 2), ABSOLUTE_TOLERANCE)
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
            Scoring.speedMultiplier(10, TWENTY_FOUR_SECONDS) > 1.0,
            "twenty-four seconds on a 10x10 has to still be worth a bonus",
        )
        assertEquals(
            1.0,
            Scoring.speedMultiplier(4, TWENTY_FOUR_SECONDS),
            "the same twenty-four seconds on a 4x4 is not fast at all",
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
        // 4 per cell x 36 cells x (1 + 2 tiers above one x 0.2) x (1 + 3 lives x 0.8)
        assertEquals(685, Scoring.completionBonus(size = 6, difficulty = 3, livesRemaining = 3))
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
    fun parIsATopRatedScore() {
        // Par is what a clean, quick run scores, and the top rung sits just
        // under it. If par did not earn every paw, the rating would top out
        // somewhere the player cannot see.
        val par = Scoring.parScore(size = 7, difficulty = 2)

        assertEquals(
            Scoring.MAX_PAWS,
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

        // Each rung checked on both sides, so a `>` slipping to `>=` or a rung
        // being read out of order shows up as a specific paw rather than as a
        // vague drift.
        assertEquals(Scoring.ONE_PAW, pawsAt(config.twoPawFraction - 0.05))
        assertEquals(Scoring.TWO_PAWS, pawsAt(config.twoPawFraction + 0.01))
        assertEquals(Scoring.TWO_PAWS, pawsAt(config.threePawFraction - 0.05))
        assertEquals(Scoring.THREE_PAWS, pawsAt(config.threePawFraction + 0.01))
        assertEquals(Scoring.THREE_PAWS, pawsAt(config.fourPawFraction - 0.05))
        assertEquals(Scoring.FOUR_PAWS, pawsAt(config.fourPawFraction + 0.01))
        assertEquals(Scoring.FOUR_PAWS, pawsAt(config.fivePawFraction - 0.05))
        assertEquals(Scoring.FIVE_PAWS, pawsAt(config.fivePawFraction + 0.01))
    }

    @Test
    fun aPerfectRunClearsPar() {
        // Drives the real API rather than the formula: every placement instant,
        // no strikes, all lives intact. If this ever scores below par, the top
        // rating would be unreachable and nobody would find out from a unit test
        // of the pieces.
        listOf(4, 7, 10).forEach { size ->
            var card = ScoreCard.Empty
            repeat(size) { card = Scoring.placement(card, size, millisSinceLastPlacement = 0).card }
            card = Scoring.complete(card, size, difficulty = 3, livesRemaining = ScoringConfig.MAX_LIVES)

            assertEquals(
                Scoring.MAX_PAWS,
                Scoring.paws(card.total, size, difficulty = 3, completed = true),
                "a flawless ${size}x$size run scored ${card.total} against par " +
                    "${Scoring.parScore(size, 3)}",
            )
        }
    }

    @Test
    fun theWorstCompletableRunStillOnlyEarnsOnePaw() {
        // The genuinely worst run a player can finish: two strikes spent (you
        // are out at three), every placement past the board's speed window. If
        // this scores two paws the rating carries no information, which is
        // exactly what the first pass at these coefficients did.
        //
        // The pace is quoted per row so it stays past the window when the window
        // is retuned. It was a flat 30,000ms, which stopped being slow the moment
        // `speedWindowMs` doubled and an 8x8's window reached 32 seconds.
        val size = 8
        var card = ScoreCard.Empty
        repeat(size) { index ->
            if (index < ScoringConfig.MAX_LIVES - 1) card = Scoring.strike(card)
            card = Scoring.placement(card, size, millisSinceLastPlacement = SLOW_MS_PER_ROW * size).card
        }
        card = Scoring.complete(card, size, difficulty = 3, livesRemaining = 1)

        assertEquals(
            Scoring.ONE_PAW,
            Scoring.paws(card.total, size, difficulty = 3, completed = true),
            "scored ${card.total} against par ${Scoring.parScore(size, 3)}",
        )
    }

    @Test
    fun aCleanButUnhurriedRunLandsInTheMiddle() {
        // The middle of the ladder has to be reachable too, or paws are just a
        // pass/perfect flag. Twenty seconds a move on an 8x8 spends most of that
        // board's speed window, so most of the bonus is gone and what is left is
        // mainly the clean sheet.
        //
        // Asserted as a band rather than a rung. With five paws the exact rung a
        // clean unhurried run lands on depends on the shape, and pinning it
        // would pin the test to a tuning; what has to stay true is that it is
        // neither the floor nor the ceiling.
        val size = 8
        var card = ScoreCard.Empty
        repeat(size) { card = Scoring.placement(card, size, millisSinceLastPlacement = 20_000).card }
        card = Scoring.complete(card, size, difficulty = 3, livesRemaining = 3)

        val paws = Scoring.paws(card.total, size, difficulty = 3, completed = true)

        assertTrue(
            paws > Scoring.ONE_PAW && paws < Scoring.MAX_PAWS,
            "a clean unhurried run rated $paws, scoring ${card.total} against par " +
                "${Scoring.parScore(size, 3)}",
        )
    }

    @Test
    fun everyRatingIsEarnableAndThePacesLandInOrder() {
        // The failure this exists for is a *rating nobody can get*, which no
        // assertion about a particular score would catch: the top rating was
        // unreachable above 6x6 for months while every worked example above
        // stayed green, because they all pick their own board.
        //
        // Three questions, and they are different. First: does every rung on the
        // ladder have a run somewhere that earns it? Second: on each individual
        // board, does going faster rate at least as well as going slower, and
        // does a clean run rate at least as well as a struck one? Third: are the
        // two ends nailed down, so a quick clean run always tops out and the
        // worst completable run always bottoms out?
        //
        // Deliberately *not* "this pace earns exactly this rating" in the middle
        // of the ladder. Retuning the rungs is allowed; making one unreachable,
        // or making the order come apart, is not.
        val earned = mutableSetOf<Int>()
        val failures = mutableListOf<String>()

        everyShape { size, difficulty, placements, shape ->
            val fast = ratingAt(size, difficulty, placements, FAST_MS_PER_ROW, strikes = 0)
            val unhurried = ratingAt(size, difficulty, placements, UNHURRIED_MS_PER_ROW, strikes = 0)
            val slow = ratingAt(size, difficulty, placements, SLOW_MS_PER_ROW, strikes = 0)
            val struck = ratingAt(size, difficulty, placements, UNHURRIED_MS_PER_ROW, strikes = 1)
            val struckAndSlow = ratingAt(size, difficulty, placements, SLOW_MS_PER_ROW, strikes = 1)
            val worst = ratingAt(size, difficulty, placements, SLOW_MS_PER_ROW, ScoringConfig.MAX_LIVES - 1)
            earned += listOf(fast, unhurried, slow, struck, struckAndSlow, worst)

            if (fast != Scoring.MAX_PAWS) {
                failures += "$shape: a quick clean run earned $fast, " +
                    "so the top rating is unreachable there"
            }
            if (worst != Scoring.ONE_PAW) {
                failures += "$shape: the worst completable run earned $worst"
            }
            if (slow < Scoring.THREE_PAWS) {
                failures += "$shape: a flawless run earned $slow for being slow, " +
                    "and a clean sheet is worth more than that"
            }
            if (fast < unhurried || unhurried < slow) {
                failures += "$shape: ratings do not fall with pace ($fast, $unhurried, $slow)"
            }
            if (unhurried < struck || slow < struckAndSlow) {
                failures += "$shape: a strike rated better than the clean run beside it " +
                    "($unhurried against $struck, $slow against $struckAndSlow)"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        assertEquals(
            (Scoring.ONE_PAW..Scoring.MAX_PAWS).toSet(),
            earned,
            "a rung of the ladder was never earned anywhere in the sweep",
        )
    }

    @Test
    fun theLadderIsSpacedAgainstMeasuredPlay() {
        // The sweep the thresholds were chosen from, kept as a test so the next
        // person retuning them does not have to run it again. For every shipped
        // grid size, every shipped tier and both board shapes, it plays a whole
        // attempt at three paces and three strike counts and records what
        // fraction of par the run reached.
        //
        // Then it asserts the thing the fractions are *for*: that each rung sits
        // in a gap between two bands rather than inside one. The bug this catches
        // is the one that has now happened three times. The ladder went to five
        // rungs inside the same 25 points of par, and the band a clean run could
        // actually reach was narrower than that at every size and narrowest of
        // all on a 4x4, so two of the rungs were below anything a clean run could
        // score and a flawless 8x8 at a considered pace rated three paws.
        //
        // Two different assertions, because the two directions are not equally
        // strict. **A rung has to be reachable on every shape**, starter-dog
        // boards included, or somebody cannot earn it: that is checked against
        // the whole sweep. Whether a rung is also *distinguishing*, meaning no
        // run in the band below can reach it, is checked on full boards only. A
        // board that hands out a dog has one fewer placement against the same
        // completion bonus, so its bands sit a little higher and overlap the
        // full-board ones by a point or two of par. That overlap is a property
        // of the shape and not a tuning error.
        //
        // The whole table goes in the failure message, because a broken tuning is
        // only diagnosable next to the numbers it was measured against.
        val rows = mutableListOf<String>()
        val everyShapeBands = mutableMapOf<String, MutableList<Double>>()
        val fullBoardBands = mutableMapOf<String, MutableList<Double>>()

        listOf(0, 1, ScoringConfig.MAX_LIVES - 1).forEach { strikes ->
            listOf(FAST_MS_PER_ROW, UNHURRIED_MS_PER_ROW, SLOW_MS_PER_ROW).forEach { pace ->
                val key = bandKey(strikes, pace)
                everyShape { size, difficulty, placements, shape ->
                    val score = runAt(size, difficulty, placements, pace, strikes)
                    val par = Scoring.parScore(size, difficulty, placements)
                    val fraction = score.toDouble() / par
                    everyShapeBands.getOrPut(key) { mutableListOf() } += fraction
                    if (placements == size) fullBoardBands.getOrPut(key) { mutableListOf() } += fraction
                    rows += "$shape, $key: ${percent(fraction)}% of par, " +
                        "${Scoring.paws(score, size, difficulty, completed = true, placements)} paws"
                }
            }
        }

        val failures = mutableListOf<String>()
        fun cut(name: String, threshold: Double, below: String, above: String) {
            val ceilingBelow = fullBoardBands.getValue(below).max()
            val floorAbove = everyShapeBands.getValue(above).min()
            if (ceilingBelow >= threshold) {
                failures += "$name at $threshold does not separate anything: a full-board run " +
                    "in the '$below' band already reaches ${percent(ceilingBelow)}% of par"
            }
            if (floorAbove < threshold) {
                failures += "$name at $threshold is out of reach: the weakest run in the " +
                    "'$above' band only reaches ${percent(floorAbove)}% of par"
            }
        }

        cut(
            "twoPawFraction", config.twoPawFraction,
            below = bandKey(2, SLOW_MS_PER_ROW), above = bandKey(1, SLOW_MS_PER_ROW),
        )
        cut(
            "threePawFraction", config.threePawFraction,
            below = bandKey(1, SLOW_MS_PER_ROW), above = bandKey(0, SLOW_MS_PER_ROW),
        )
        cut(
            "fourPawFraction", config.fourPawFraction,
            below = bandKey(0, SLOW_MS_PER_ROW), above = bandKey(0, UNHURRIED_MS_PER_ROW),
        )
        cut(
            "fivePawFraction", config.fivePawFraction,
            below = bandKey(0, UNHURRIED_MS_PER_ROW), above = bandKey(0, FAST_MS_PER_ROW),
        )

        val table = everyShapeBands.entries.joinToString("\n") { (key, values) ->
            val full = fullBoardBands.getValue(key)
            "  $key: ${percent(full.min())}% .. ${percent(full.max())}% of par on a full board, " +
                "${percent(values.min())}% .. ${percent(values.max())}% counting starter-dog boards"
        }
        assertTrue(
            failures.isEmpty(),
            failures.joinToString("\n") + "\n\nmeasured bands:\n" + table +
                "\n\nevery run:\n" + rows.joinToString("\n"),
        )
    }

    private fun bandKey(strikes: Int, msPerRow: Long): String =
        "$strikes strike(s) at ${msPerRow}ms a row"

    @Test
    fun aStarterDogIsNotChargedToTheRunThatDidNotPlaceIt() {
        // `LevelCurve.opensWithStarterDog` puts a dog down on the first few
        // levels of every band, so the player places `size - 1` of them. Par
        // counted the whole board either way, and the placement it charged for
        // was the last one, which carries the highest combo: a flawless run on
        // one of those boards came out around a tenth of par short and lost a
        // paw for it. Every level that opens a grid size had the same tax.
        (SMALLEST_BOARD..BIGGEST_BOARD).forEach { size ->
            val placements = size - 1
            val whole = Scoring.parScore(size, difficulty = 3)
            val short = Scoring.parScore(size, difficulty = 3, placements = placements)

            assertTrue(short < whole, "par has to drop when the board hands a dog out")
            assertEquals(
                Scoring.MAX_PAWS,
                Scoring.paws(
                    runAt(size, difficulty = 3, placements = placements, FAST_MS_PER_ROW, strikes = 0),
                    size,
                    difficulty = 3,
                    completed = true,
                    placements = placements,
                ),
                "a flawless quick run on a ${size}x$size that opened with a starter dog",
            )
        }
    }

    private fun ratingAt(size: Int, difficulty: Int, placements: Int, msPerRow: Long, strikes: Int): Int =
        Scoring.paws(
            runAt(size, difficulty, placements, msPerRow, strikes),
            size,
            difficulty,
            completed = true,
            placements = placements,
        )

    /**
     * Every shape a player can actually be handed: each shipped grid size, each
     * shipped tier, and both `size` placements and `size - 1` for the boards
     * that open with a starter dog.
     */
    private fun everyShape(body: (size: Int, difficulty: Int, placements: Int, shape: String) -> Unit) {
        (SMALLEST_BOARD..BIGGEST_BOARD).forEach { size ->
            (1..SHIPPED_MAX_DIFFICULTY).forEach { difficulty ->
                listOf(size, size - 1).forEach { placements ->
                    val dog = if (placements < size) ", starter dog" else ""
                    body(size, difficulty, placements, "${size}x$size tier $difficulty$dog")
                }
            }
        }
    }

    /**
     * A whole attempt at a steady pace of [msPerRow] per row of board per
     * placement, with [strikes] wrong guesses taken early, returned as the
     * total after the completion bonus.
     *
     * Per row rather than flat, because a move on a 10x10 is not the same
     * amount of work as a move on a 4x4 and a test that pretended otherwise
     * would be asking every board for the same wall clock. It is also the unit
     * the speed window is quoted in, so the same number is the same fraction of
     * the window on every board.
     */
    private fun runAt(
        size: Int,
        difficulty: Int,
        placements: Int,
        msPerRow: Long,
        strikes: Int,
        config: ScoringConfig = ScoringConfig.Default,
    ): Int {
        var card = ScoreCard.Empty
        repeat(placements) { index ->
            if (index in 1..strikes) card = Scoring.strike(card)
            card = Scoring.placement(card, size, msPerRow * size, config).card
        }
        return Scoring.complete(
            card,
            size,
            difficulty,
            livesRemaining = ScoringConfig.MAX_LIVES - strikes,
            config = config,
        ).total
    }

    private fun percent(fraction: Double): Int = (fraction * PERCENT).toInt()

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
            Scoring.MAX_PAWS,
            Scoring.paws(card.total, size, difficulty = 3, completed = true),
            "a flawless run keeps its rating however much help it took",
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
            "negative completion" to { ScoringConfig(completionPerCell = -250) },
            "overflowing base" to { ScoringConfig(basePerPlacement = 300_000_000) },
            "overflowing completion" to { ScoringConfig(completionPerCell = 300_000_000) },
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
    fun configRejectsCoefficientsWithNoCeiling() {
        // Every one of these constructed and got played. The floors were the
        // half of the problem anybody had thought about; an operator typing a
        // zero too many into the console is the other half, and it lands in the
        // same place — a rating that runs backwards, with a clean run on one paw
        // and a two-strike run on five.
        //
        // Named one at a time rather than swept, because the value of this list
        // is that a new coefficient added without a ceiling is a line somebody
        // has to notice is missing.
        listOf(
            "runaway combo step" to { ScoringConfig(comboStep = 1e9) },
            "runaway combo max" to { ScoringConfig(comboMax = 1e12) },
            "runaway lives rate" to { ScoringConfig(livesBonusRate = 1e8) },
            "runaway difficulty rate" to { ScoringConfig(difficultyBonusRate = 1e8) },
            "runaway speed multiplier" to { ScoringConfig(speedMaxMultiplier = 1e9) },
            "runaway speed window" to { ScoringConfig(speedWindowMs = Long.MAX_VALUE) },
            "runaway praise cutoff" to { ScoringConfig(nicePraiseAt = 1e9) },
            // Above 1.0 is above par, and par is the formula's exact ceiling, so
            // the rung is not merely hard, it is arithmetically unreachable.
            // That is the compression bug arriving from the console. The
            // ascending check does not catch it: 5.0 is still above 0.77.
            "paw fraction above par" to { ScoringConfig(fivePawFraction = 5.0) },
            "negative praise cutoff" to { ScoringConfig(perfectPraiseAt = -1.0) },
            "overflowing base" to { ScoringConfig(basePerPlacement = ScoringConfig.MAX_POINT_VALUE + 1) },
            "overflowing completion" to { ScoringConfig(completionPerCell = ScoringConfig.MAX_POINT_VALUE + 1) },
        ).forEach { (name, build) ->
            assertTrue(runCatching(build).isFailure, "$name must not be constructible")
        }
    }

    @Test
    fun configRejectsValuesThatAreNotNumbers() {
        // Infinity and NaN arrive the same way every other bad number does, and
        // neither is stopped by a floor. Infinity saturates every placement at
        // `Int.MAX_VALUE` and the total wraps negative; NaN makes every
        // comparison in `paws` false, so every run bottoms out at one paw.
        //
        // There is no `isFinite()` in the guard and there does not need to be:
        // a range check rejects both, because every comparison with NaN is false
        // and Infinity fails the ceiling. This is what says that stays true.
        listOf(
            "infinite speed multiplier" to { ScoringConfig(speedMaxMultiplier = Double.POSITIVE_INFINITY) },
            "infinite lives rate" to { ScoringConfig(livesBonusRate = Double.POSITIVE_INFINITY) },
            "infinite combo max" to { ScoringConfig(comboMax = Double.POSITIVE_INFINITY) },
            "not-a-number combo step" to { ScoringConfig(comboStep = Double.NaN) },
            "not-a-number paw fraction" to { ScoringConfig(fivePawFraction = Double.NaN) },
            "not-a-number booster rate" to { ScoringConfig(boosterPenaltyRate = Double.NaN) },
        ).forEach { (name, build) ->
            assertTrue(runCatching(build).isFailure, "$name must not be constructible")
        }
    }

    @Test
    fun everyLegalConfigIsOneAPlayerCouldPlayOn() {
        // The ceilings only mean something if the corner under them is still
        // arithmetic. This walks it: every coefficient at its limit at once, on
        // the biggest board at the deepest tier, which is the largest par the
        // guards permit.
        //
        // `theLargestLegalConfigStillScoresPositively` checked the two Ints at
        // their ceiling and nothing else, so it passed while a `comboMax` of 1e9
        // wrapped par negative beside it. The ceiling on the Ints came down from
        // a million to ten thousand for exactly this: at a million the corner
        // reaches 137 billion against an `Int.MAX_VALUE` of 2.15 billion, and no
        // amount of guarding the Ints alone fixes that, because what overflows
        // is the product with the multipliers.
        val extreme = ScoringConfig(
            basePerPlacement = ScoringConfig.MAX_POINT_VALUE,
            completionPerCell = ScoringConfig.MAX_POINT_VALUE,
            comboStep = ScoringConfig.MAX_RATE,
            comboMax = ScoringConfig.MAX_RATE,
            speedWindowMs = ScoringConfig.MAX_SPEED_WINDOW_MS,
            speedMaxMultiplier = ScoringConfig.MAX_RATE,
            livesBonusRate = ScoringConfig.MAX_RATE,
            difficultyBonusRate = ScoringConfig.MAX_RATE,
        )

        val par = Scoring.parScore(BIGGEST_BOARD, MAX_DIFFICULTY, config = extreme)
        assertTrue(par > 0, "the largest legal par came out $par")
        assertTrue(
            Scoring.placement(ScoreCard.Empty, BIGGEST_BOARD, null, extreme).points > 0,
            "a placement under the largest legal config scored nothing",
        )
        assertTrue(
            Scoring.completionBonus(BIGGEST_BOARD, MAX_DIFFICULTY, ScoringConfig.MAX_LIVES, extreme) > 0,
            "the completion bonus under the largest legal config came out negative",
        )

        // And it still rates in order, which is the property the guards are
        // actually for. A config that scores positive and rates backwards is the
        // incident, not the near miss.
        val failures = mutableListOf<String>()
        everyShape { size, difficulty, placements, shape ->
            val ratings = listOf(0, 1, ScoringConfig.MAX_LIVES - 1).map { strikes ->
                Scoring.paws(
                    runAt(size, difficulty, placements, SLOW_MS_PER_ROW, strikes, extreme),
                    size,
                    difficulty,
                    completed = true,
                    placements = placements,
                    config = extreme,
                )
            }
            if (ratings != ratings.sortedDescending()) {
                failures += "$shape: a strike rated better than the clean run beside it ($ratings)"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
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
            card = Scoring.placement(card, size, millisSinceLastPlacement = SLOW_MS_PER_ROW * size).card
        }
        val lives = ScoringConfig.MAX_LIVES - strikes
        card = Scoring.complete(card, size, difficulty = 3, livesRemaining = lives)
        return Scoring.paws(card.total, size, difficulty = 3, completed = true)
    }

    @Test
    fun everyCoefficientIsOverridable() {
        // The whole point of the config object: remote config has to be able to
        // move these without a release.
        val richer = ScoringConfig(basePerPlacement = 20)

        val scored = Scoring.placement(ScoreCard.Empty, size = 5, null, richer)

        assertEquals(100, scored.points)
        assertTrue(
            Scoring.parScore(5, 2, config = richer) > Scoring.parScore(5, 2),
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
         * on a 10x10 they are 9, 25 and 60 seconds a move, or a minute and a
         * half, four minutes and ten for the whole board.
         *
         * Per row, because the speed window is per row too
         * ([Scoring.speedWindowMsFor] is `speedWindowMs * size / 4`, so 4000ms
         * a row at the shipped default). A pace quoted this way therefore means
         * the same fraction of the window on every board, which is the only way
         * the sweep can compare a 4x4 with a 10x10. [SLOW_MS_PER_ROW] is past
         * the window on every size, so it is the floor of what a run can score
         * rather than merely a slow number.
         */
        const val FAST_MS_PER_ROW = 900L
        const val UNHURRIED_MS_PER_ROW = 2_500L
        const val SLOW_MS_PER_ROW = 6_000L

        const val TWENTY_FOUR_SECONDS = 24_000L
        const val PERCENT = 100
    }
}
