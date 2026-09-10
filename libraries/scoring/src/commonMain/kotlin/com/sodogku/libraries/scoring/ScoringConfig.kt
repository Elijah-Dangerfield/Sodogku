package com.sodogku.libraries.scoring

/**
 * Every number the scoring formula uses.
 *
 * Nothing here is a constant in the formula itself. These land in remote config
 * (`scoring.*`, see docs/SPEC.md section 4), because how many points a placement
 * is worth and what counts as a three-paw clear are exactly the kind of dials
 * that want retuning against real play data without an app release.
 *
 * The defaults are the shipped fallbacks, balanced on three things.
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
 *
 * Third, and unlike the other two this one is only about how the number *reads*:
 * [basePerPlacement] and [completionBase] started at 100 and 250, which paid a
 * good 10x10 a little under 32,000 points and had a player on level 29 carrying
 * a six-figure career total. By level 500 it would have been seven figures. Both
 * are a tenth of that now.
 *
 * Dividing the two of them by the same factor is the only safe way to do that,
 * and it is safe *because* everything else here is a ratio. Every multiplier,
 * both paw fractions, the praise cutoffs and the booster cost are unitless, so
 * the whole scale moves together: a score, the par it is rated against and the
 * thresholds that are fractions of par all shrink by ten and no rating changes
 * on any board. Retuning only one of the two would not be a rescale, it would
 * be the 60/40 balance above.
 *
 * What a rescale does move is anything outside this file holding an absolute
 * number of points. The three `Stat.BestScore` achievements are the only ones
 * in the game, and they are derived from [Scoring.parScore] rather than typed
 * precisely so this change cannot strand them out of reach.
 */
data class ScoringConfig(
    /** Points for one correct placement, before size and multipliers. */
    val basePerPlacement: Int = 10,

    /** Points for finishing, before size, difficulty and lives. */
    val completionBase: Int = 25,

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
     * Score fractions of par at which each paw above the first is awarded.
     * Finishing at all earns the first, so there is no threshold for it.
     *
     * **Five paws, not three.** The rating is a five-star scale in everything
     * but name now, and the two original fractions kept their config keys
     * (`scoring.twoPawFraction`, `scoring.threePawFraction`) so a remote
     * override does not break, while being retuned to sit in the middle of a
     * longer ladder rather than at the top of a short one.
     *
     * Nothing had to be migrated for this. Paws are derived from score and par,
     * and the score is what is stored, so an old record is simply re-rated under
     * the new ladder. A player who had three paws does not wake up with "3 out
     * of 5".
     *
     * The rungs are spaced against *measured play*, not by dividing the range
     * up neatly. `ScoringTest`'s sweep runs every shipped board shape at three
     * paces and reports what fraction of par each reaches: a quick clean run
     * lands around 90%, an unhurried one around 79%, and a slow run with two
     * strikes around 58%. A first pass at 0.45/0.65/0.82/0.95 put the top rung
     * above anything the fast pace could reach, which is the failure that sweep
     * exists to catch -- a rating nobody can earn, invisible to every worked
     * example because they each pick their own board.
     *
     * So **both original rungs stay exactly where they were**: 0.60 above the
     * worst completable run, and 0.85 at what a quick clean run scores on the
     * *hardest* shape to score well on. Those two were already validated by the
     * sweep. The two new rungs are inserted between them, which adds granularity
     * without moving either end of a ladder that was known to work.
     *
     * A pass at 0.88 for the top looked reasonable and failed on 8x8 and larger,
     * where a quick run reaches about 85% rather than the 90% a 4x4 reaches. The
     * curve does not scale uniformly with board size, which is the sort of thing
     * only the sweep knows.
     */
    val twoPawFraction: Double = 0.60,
    val threePawFraction: Double = 0.70,
    val fourPawFraction: Double = 0.78,
    val fivePawFraction: Double = 0.85,

    /** Combined-multiplier cutoffs for the floating praise text. */
    val nicePraiseAt: Double = 1.2,
    val greatPraiseAt: Double = 1.5,
    val excellentPraiseAt: Double = 1.9,
    val perfectPraiseAt: Double = 2.3,
) {
    init {
        // Every field that scales a score is guarded, because the whole set
        // arrives from remote config and `ConfiguredScoring` only falls back to
        // the shipped values when this block *throws*. A bad number that
        // constructs is a bad number that gets played.
        //
        // The comment here used to say these two Ints "were the only fields
        // unguarded", which was wrong: the three rates below had nothing on
        // them. A `livesBonusRate` of -0.5 constructs happily and inverts the
        // rating, because par's lives factor goes negative while the player's
        // stays positive, so a two-strike run scores *better* against it. That
        // is the same shape as the incident these guards were added for, where a
        // dropped minus sign gave every player three paws for scoring zero.
        require(basePerPlacement > 0) { "basePerPlacement must be positive" }
        require(completionBase > 0) { "completionBase must be positive" }
        require(comboStep >= 0.0) { "comboStep must not be negative" }
        require(livesBonusRate >= 0.0) { "livesBonusRate must not be negative" }
        require(difficultyBonusRate >= 0.0) { "difficultyBonusRate must not be negative" }
        require(basePerPlacement <= MAX_POINT_VALUE && completionBase <= MAX_POINT_VALUE) {
            "point values must be at most $MAX_POINT_VALUE, so scoring cannot overflow"
        }
        require(comboMax >= 1.0) { "comboMax must be at least 1.0" }
        require(speedMaxMultiplier >= 1.0) { "speedMaxMultiplier must be at least 1.0" }
        require(speedWindowMs > 0) { "speedWindowMs must be positive" }
        val pawFractions = listOf(twoPawFraction, threePawFraction, fourPawFraction, fivePawFraction)
        require(pawFractions.all { it in 0.0..1.0 }) {
            "paw fractions must be between 0 and 1"
        }
        // Zipped rather than four hand-written comparisons, so adding a sixth
        // paw cannot leave one pair unchecked. A ladder that is not ascending
        // silently makes a rung unreachable rather than throwing.
        require(pawFractions.zipWithNext().all { (lower, higher) -> higher >= lower }) {
            "each paw must be at least as hard to earn as the one below it"
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
