package com.sodogku.libraries.scoring

/**
 * Every number the scoring formula uses.
 *
 * Nothing here is a constant in the formula itself. These land in remote config
 * (`scoring.*`, see docs/SPEC.md section 4), because how many points a placement
 * is worth and what counts as a three-paw clear are exactly the kind of dials
 * that want retuning against real play data without an app release.
 *
 * The defaults are the shipped fallbacks, balanced on two things.
 *
 * First, placements are roughly 60% of a good run's score and the completion
 * bonus the other 40%. Tilt too far toward completion and a fast clean solve
 * scores the same as a slow scrappy one; too far toward placements and
 * finishing stops mattering.
 *
 * Second, and less obvious: the multipliers have to spread *wide* enough for the
 * paw thresholds to mean anything. The first pass used gentler numbers, and the
 * worst possible completed run still landed at 63% of par — above the two-paw
 * line — so a single paw was unreachable and the rating carried no information.
 * Compressing the range is the failure mode to watch when retuning these.
 */
data class ScoringConfig(
    /** Points for one correct placement, before size and multipliers. */
    val basePerPlacement: Int = 100,

    /** Points for finishing, before size, difficulty and lives. */
    val completionBase: Int = 250,

    /** Added to the combo multiplier per consecutive correct placement. */
    val comboStep: Double = 0.08,

    /** Ceiling on the combo multiplier, so a 10x10 streak cannot run away. */
    val comboMax: Double = 2.0,

    /** How long the speed bonus takes to decay to nothing, in milliseconds. */
    val speedWindowMs: Long = 8_000,

    /** The speed multiplier for an instant placement; it decays linearly to 1.0. */
    val speedMaxMultiplier: Double = 1.6,

    /** How much each surviving life adds to the completion bonus. */
    val livesBonusRate: Double = 0.5,

    /** How much each difficulty tier above 1 adds to the completion bonus. */
    val difficultyBonusRate: Double = 0.2,

    /**
     * Score fractions of par at which the second and third paw are awarded.
     * Finishing at all earns the first, so there is no threshold for it.
     */
    val twoPawFraction: Double = 0.60,
    val threePawFraction: Double = 0.85,

    /** Combined-multiplier cutoffs for the floating praise text. */
    val nicePraiseAt: Double = 1.2,
    val greatPraiseAt: Double = 1.5,
    val excellentPraiseAt: Double = 1.9,
    val perfectPraiseAt: Double = 2.3,
) {
    init {
        // These two are the only Ints, and they were the only fields unguarded.
        // Both land here from remote config, where a dropped minus sign gave
        // every player three paws for scoring zero: par went negative, so any
        // score cleared it.
        require(basePerPlacement > 0) { "basePerPlacement must be positive" }
        require(completionBase > 0) { "completionBase must be positive" }
        require(basePerPlacement <= MAX_POINT_VALUE && completionBase <= MAX_POINT_VALUE) {
            "point values must be at most $MAX_POINT_VALUE, so scoring cannot overflow"
        }
        require(comboMax >= 1.0) { "comboMax must be at least 1.0" }
        require(speedMaxMultiplier >= 1.0) { "speedMaxMultiplier must be at least 1.0" }
        require(speedWindowMs > 0) { "speedWindowMs must be positive" }
        require(twoPawFraction in 0.0..1.0 && threePawFraction in 0.0..1.0) {
            "paw fractions must be between 0 and 1"
        }
        require(threePawFraction >= twoPawFraction) {
            "the third paw cannot be easier to earn than the second"
        }
    }

    companion object {
        /** Lives per attempt. Three bones, matching the header. */
        const val MAX_LIVES: Int = 3

        /**
         * Ceiling on the two Int point values. `ScoreCard` multiplies them by
         * the board size as Ints before widening to Double, so a value in the
         * hundreds of millions wraps negative. A million is four orders of
         * magnitude above anything a tuning pass would plausibly want.
         */
        const val MAX_POINT_VALUE: Int = 1_000_000

        val Default: ScoringConfig = ScoringConfig()
    }
}

/** The floating text over a placement. Cosmetic, keyed off the combined multiplier. */
enum class Praise { None, Nice, Great, Excellent, Perfect }
