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
 *
 * That failure came back at the other end, and what it looked like is worth
 * knowing: the numbers below were all fine, but [speedWindowMs] was a flat 8000
 * for every grid. Nobody places inside eight seconds on a 9x9, so the speed
 * multiplier sat at 1.0 for every player on every large board, the score lost
 * its speed term entirely, and a clean run landed at a fixed 73% of par whether
 * it took two minutes or twenty. Three paws at 0.85 of par was unreachable for
 * the whole back half of the campaign. The range has to stay wide *on the board
 * being played*, not just on paper.
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

    /**
     * How long the speed bonus takes to decay to nothing on the smallest board,
     * in milliseconds. Bigger boards get proportionally longer — see
     * [Scoring.speedWindowMsFor].
     *
     * A flat window was the bug behind "I solve fast and still get two paws".
     * Eight seconds is an age on a 4x4 and a blink on a 10x10, so above about
     * 6x6 every placement landed outside it and the speed multiplier was pinned
     * at 1.0 for everyone. Par still priced speed at the maximum, so *every*
     * clean run on a big board scored the same 73% of par however fast it was
     * played, and the third paw at 0.85 was unreachable. Scaling the window with
     * the grid is what makes "fast" mean fast *for this board*.
     */
    val speedWindowMs: Long = 8_000,

    /** The speed multiplier for an instant placement; it decays linearly to 1.0. */
    val speedMaxMultiplier: Double = 1.6,

    /** How much each surviving life adds to the completion bonus. */
    val livesBonusRate: Double = 0.5,

    /** How much each difficulty tier above 1 adds to the completion bonus. */
    val difficultyBonusRate: Double = 0.2,

    /**
     * What one booster spent during an attempt costs the score it banks.
     *
     * Multiplicative rather than a flat deduction: at 0.15 one sniff banks 0.85
     * of the run, two bank 0.72, five bank 0.44. Nothing can drive a score
     * negative, the cost is the same wherever in the attempt the booster was
     * spent, and it scales with the board — a treat on a 10x10 costs more points
     * than one on a 4x4 because the run itself is worth more.
     */
    val boosterPenaltyRate: Double = 0.15,

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
        // Above 1.0 a booster would *add* points, and below 0 it would too.
        require(boosterPenaltyRate in 0.0..1.0) {
            "boosterPenaltyRate must be between 0 and 1"
        }
    }

    companion object {
        /** Lives per attempt. Three bones, matching the header. */
        const val MAX_LIVES: Int = 3

        /**
         * The grid [speedWindowMs] is quoted against: the smallest board the
         * game ships. A 4x4 gets the configured window, a 10x10 two and a half
         * times it.
         *
         * A constant rather than a config key on purpose. It is the *shape* of
         * the relationship between thinking time and grid size, and section 4.1
         * of the spec keeps shape in the binary; the magnitude is the config
         * key next to it. Duplicated from `Board.MIN_SIZE` because scoring
         * deliberately does not depend on `:libraries:puzzle`.
         */
        const val SPEED_WINDOW_REFERENCE_SIZE: Int = 4

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
