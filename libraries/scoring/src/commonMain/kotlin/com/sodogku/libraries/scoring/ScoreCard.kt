package com.sodogku.libraries.scoring

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
                placements = card.placements + 1,
            ),
            points = points,
            multiplier = multiplier,
            praise = praiseFor(multiplier, config),
        )
    }

    /** A wrong tap. Costs the streak; the life is the game's business, not scoring's. */
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
