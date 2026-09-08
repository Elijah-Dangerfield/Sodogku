package com.sodogku.libraries.scoring

import kotlin.math.pow

/**
 * A run's score so far. Immutable: every transition returns a new card, so the
 * ViewModel can hold one in its state and the board can animate off the delta
 * without anything mutating underneath it.
 *
 * [combo] counts *consecutive* correct placements. It is the only thing a strike
 * takes away, which is what makes the strike hurt beyond the bone it costs.
 */
data class ScoreCard(
    val total: Int = 0,
    val combo: Int = 0,

    /**
     * The longest run this attempt reached, which [combo] cannot answer once a
     * strike has reset it. A high-water mark rather than a running total, so it
     * is meaningful after the attempt ends — which is when the achievement fold
     * asks for it.
     */
    val bestCombo: Int = 0,
    val placements: Int = 0,
) {
    companion object {
        val Empty: ScoreCard = ScoreCard()
    }
}

/** The result of one correct tap: the new card plus what to show the player. */
data class ScoredPlacement(
    val card: ScoreCard,
    val points: Int,
    val multiplier: Double,
    val praise: Praise,
)

/**
 * The scoring formula.
 *
 * Points scale with grid size so a 10x10 placement is worth more than a 4x4 one,
 * multiply up with an unbroken streak, and multiply again for answering quickly.
 * Finishing pays a bonus that scales with size, difficulty and surviving lives.
 */
object Scoring {

    /**
     * Scores one correct placement.
     *
     * [millisSinceLastPlacement] is measured from the previous placement, or
     * from the start of the attempt for the first one. Null means "no timing
     * available" and scores at the base rate rather than guessing.
     */
    fun placement(
        card: ScoreCard,
        size: Int,
        millisSinceLastPlacement: Long?,
        config: ScoringConfig = ScoringConfig.Default,
    ): ScoredPlacement {
        val combo = comboMultiplier(card.combo, config)
        val speed = speedMultiplier(millisSinceLastPlacement, config)
        val multiplier = combo * speed
        val points = (config.basePerPlacement * size * multiplier).toInt()

        return ScoredPlacement(
            card = card.copy(
                total = card.total + points,
                combo = card.combo + 1,
                bestCombo = maxOf(card.bestCombo, card.combo + 1),
                placements = card.placements + 1,
            ),
            points = points,
            multiplier = multiplier,
            praise = praiseFor(multiplier, config),
        )
    }

    /**
     * A wrong tap. Costs the streak; the life is the game's business, not
     * scoring's. [ScoreCard.bestCombo] survives on purpose — a strike ends the
     * run, it does not un-happen it.
     */
    fun strike(card: ScoreCard): ScoreCard = card.copy(combo = 0)

    /** Adds the completion bonus. Call once, when the last dog lands. */
    fun complete(
        card: ScoreCard,
        size: Int,
        difficulty: Int,
        livesRemaining: Int,
        config: ScoringConfig = ScoringConfig.Default,
    ): ScoreCard = card.copy(
        total = card.total + completionBonus(size, difficulty, livesRemaining, config),
    )

    fun completionBonus(
        size: Int,
        difficulty: Int,
        livesRemaining: Int,
        config: ScoringConfig = ScoringConfig.Default,
    ): Int {
        val difficultyFactor = 1.0 + (difficulty - 1).coerceAtLeast(0) * config.difficultyBonusRate
        val livesFactor = 1.0 + livesRemaining.coerceAtLeast(0) * config.livesBonusRate
        return (config.completionBase * size * difficultyFactor * livesFactor).toInt()
    }

    /**
     * What an attempt banks after the boosters it leaned on, from a total that
     * has already been through [complete].
     *
     * **Deliberately not part of [paws].** The rating is measured against
     * [parScore], and scaling only the score would quietly put three paws out of
     * reach of anyone who used a hint — including the player following the
     * tutorial, which *instructs* a sniff and a treat on level 2. Because the
     * cost is a multiplier and the paw thresholds are fractions of par, rating
     * the run on the pre-penalty total is exactly the same arithmetic as scaling
     * par by the same factor: `score × f ≥ par × f × 0.85` is `score ≥ par ×
     * 0.85`. So the paws say how the board was solved and the banked score says
     * what the help was worth, and neither has to know about the other.
     */
    fun afterBoosters(total: Int, boostersUsed: Int, penaltyRate: Double): Int {
        val kept = (1.0 - penaltyRate.coerceIn(0.0, 1.0)).pow(boostersUsed.coerceAtLeast(0))
        return (total * kept).toInt()
    }

    /**
     * What a strong run is worth: every placement at full combo and full speed,
     * finished without losing a life.
     *
     * This is derived rather than shipped in the pack precisely so the paw
     * thresholds move when the coefficients do. Baking it into the level data
     * would freeze what a three-paw clear means at generation time.
     */
    fun parScore(
        size: Int,
        difficulty: Int,
        config: ScoringConfig = ScoringConfig.Default,
    ): Int {
        val placements = (0 until size).sumOf { index ->
            val multiplier = comboMultiplier(index, config) * config.speedMaxMultiplier
            (config.basePerPlacement * size * multiplier).toInt()
        }
        return placements + completionBonus(size, difficulty, ScoringConfig.MAX_LIVES, config)
    }

    /**
     * Paws earned, 0 to 3. Zero means the level was never finished; finishing at
     * all is worth one, and the other two are fractions of [parScore].
     */
    fun paws(
        score: Int,
        size: Int,
        difficulty: Int,
        completed: Boolean,
        config: ScoringConfig = ScoringConfig.Default,
    ): Int {
        if (!completed) return 0
        val par = parScore(size, difficulty, config)
        return when {
            score >= par * config.threePawFraction -> THREE_PAWS
            score >= par * config.twoPawFraction -> TWO_PAWS
            else -> ONE_PAW
        }
    }

    fun comboMultiplier(streak: Int, config: ScoringConfig = ScoringConfig.Default): Double =
        (1.0 + streak.coerceAtLeast(0) * config.comboStep).coerceAtMost(config.comboMax)

    /**
     * Decays linearly from [ScoringConfig.speedMaxMultiplier] to 1.0 across the
     * window. Linear rather than exponential so the pressure a player feels is
     * proportional to the clock they can see.
     */
    fun speedMultiplier(
        millisSinceLastPlacement: Long?,
        config: ScoringConfig = ScoringConfig.Default,
    ): Double {
        if (millisSinceLastPlacement == null) return 1.0
        val elapsed = millisSinceLastPlacement.coerceAtLeast(0)
        if (elapsed >= config.speedWindowMs) return 1.0
        val remaining = 1.0 - elapsed.toDouble() / config.speedWindowMs
        return 1.0 + (config.speedMaxMultiplier - 1.0) * remaining
    }

    fun praiseFor(multiplier: Double, config: ScoringConfig = ScoringConfig.Default): Praise = when {
        multiplier >= config.perfectPraiseAt -> Praise.Perfect
        multiplier >= config.excellentPraiseAt -> Praise.Excellent
        multiplier >= config.greatPraiseAt -> Praise.Great
        multiplier >= config.nicePraiseAt -> Praise.Nice
        else -> Praise.None
    }

    const val ONE_PAW: Int = 1
    const val TWO_PAWS: Int = 2
    const val THREE_PAWS: Int = 3
}
