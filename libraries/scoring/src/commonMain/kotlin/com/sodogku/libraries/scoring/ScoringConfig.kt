package com.sodogku.libraries.scoring

/**
 * Every number the scoring formula uses.
 *
 * Nothing here is a constant in the formula itself. These land in remote config
 * (`scoring.*`, see `features.md#remote-config`), because how many points a
 * placement is worth and what counts as a three-paw clear are exactly the kind
 * of dials that want retuning against real play data without an app release.
 *
 * The defaults are the shipped fallbacks, balanced on four things.
 *
 * First, placements are roughly 60% of a good run's score and the completion
 * bonus the other 40%. Tilt too far toward completion and a fast clean solve
 * scores the same as a slow scrappy one; too far toward placements and
 * finishing stops mattering.
 *
 * Second, and less obvious: the multipliers have to spread *wide* enough for the
 * paw thresholds to mean anything. The first pass used gentler numbers, and the
 * worst possible completed run still landed at 63% of par, above the two-paw
 * line, so a single paw was unreachable and the rating carried no information.
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
 * **Then it came back a third time, and this is the one worth reading.** Going
 * from three paws to five put four cuts inside a 25-point band, and a sweep of
 * every shipped shape at every pace showed the band a clean run can actually
 * reach was narrower than that: 0.73 to 1.00 of par on a 10x10 and only 0.83 to
 * 1.00 on a 4x4. Two paws and three paws were below the *floor* of a clean run
 * on every board, so no amount of playing badly-but-cleanly could earn them and
 * no amount of playing well could avoid four. A flawless 8x8 at a considered
 * pace rated three paws. Moving the four cuts down into that band would have
 * put the rungs four points of par apart on a 4x4, which is relabelling the
 * compression rather than fixing it, so the multipliers moved instead:
 * [speedMaxMultiplier] went 1.6 to 2.0, [speedWindowMs] doubled, and
 * [livesBonusRate] went 0.5 to 0.8. A clean run now spans 0.67 to 1.00 and a
 * finished one 0.48 to 1.00.
 *
 * Fourth, and the reason the same tuning now means the same thing on every
 * board: **both halves of the score are priced per cell.** A placement pays
 * [basePerPlacement] per row and a board has one placement per row, so the
 * placement total grows with the cell count; [completionPerCell] makes finishing
 * do the same. It used to be per row, which made completion 55% of a 4x4's par
 * and 29% of a 10x10's, and since a clean run always collects the completion
 * bonus in full it is exactly the part of the score that pace cannot move. That
 * is why the reachable band was 10 points of par narrower on a 4x4 than on a
 * 10x10, and why one set of fractions could not describe both. Per cell, at a
 * fixed tier, the clean floor moves by two points of par across all seven grid
 * sizes where it used to move by ten.
 *
 * The last one is only about how the number *reads*: [basePerPlacement] and the
 * completion term started at 100 and 250, which paid a good 10x10 a little under
 * 32,000 points and had a player on level 29 carrying a six-figure career total.
 * By level 500 it would have been seven figures. Both are a tenth of that now.
 *
 * Dividing the two of them by the same factor is the only safe way to do that,
 * and it is safe *because* everything else here is a ratio. Every multiplier,
 * all four paw fractions, the praise cutoffs and the booster cost are unitless,
 * so the whole scale moves together: a score, the par it is rated against and
 * the thresholds that are fractions of par all shrink by ten and no rating
 * changes on any board. Retuning only one of the two would not be a rescale, it
 * would be the 60/40 balance above.
 *
 * What a rescale does move is anything outside this file holding an absolute
 * number of points. The three `Stat.BestScore` achievements are the only ones
 * in the game, and they are derived from [Scoring.parScore] rather than typed
 * precisely so this change cannot strand them out of reach.
 */
data class ScoringConfig(
    /** Points for one correct placement, before size and multipliers. */
    val basePerPlacement: Int = 10,

    /**
     * Points for finishing, per cell of the board, before difficulty and lives.
     *
     * Per cell and not per row, which is the same shape the placement total
     * already has: `basePerPlacement * size` points a placement, `size`
     * placements. Both halves of a run therefore grow with the board at the same
     * rate, so their ratio, and with it the width of the band a run can land in,
     * is the same on a 4x4 as on a 10x10. Per row it was not, and one set of paw
     * fractions could not describe both ends of the campaign.
     */
    val completionPerCell: Int = 4,

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
     *
     * Scaling it was necessary and was not sufficient. At 8000 the window
     * closed after 14 seconds a move on a 7x7, and `features.md#the-daily` puts
     * an ordinary daily on a 6x6 to 8x8 at three to five minutes, which is 26
     * to 43 seconds a move. Every ordinary run was still outside the window, so
     * the speed term was dead for the median player on the median board and
     * only a sprint moved the score at all. Sixteen thousand puts a
     * three-minute 7x7 inside the window and a two-minute one comfortably
     * inside it.
     */
    val speedWindowMs: Long = 16_000,

    /**
     * The speed multiplier for an instant placement; it decays linearly to 1.0.
     *
     * This number *is* the width of the band a clean run can land in: the
     * placement half of a slow clean run is worth `1 / speedMaxMultiplier` of
     * the same half of par, and the completion half is worth all of it whatever
     * the pace. At 1.6 that left a clean 4x4 unable to score below 83% of par,
     * so three of the four rungs were below anything a clean run could reach.
     */
    val speedMaxMultiplier: Double = 2.0,

    /**
     * How much each surviving life adds to the completion bonus.
     *
     * The gap between the clean band and the struck ones, and therefore where
     * the bottom two rungs live. At 0.5 a two-strike run reached the top of the
     * one-strike band with three points of par to spare, which is not enough
     * room to put a threshold in and know it will stay put.
     */
    val livesBonusRate: Double = 0.8,

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
     * The rungs are spaced against *measured play*, and each one sits in a gap
     * between two things a player can do rather than at a round number.
     * `ScoringTest.theLadderIsSpacedAgainstMeasuredPlay` prints the whole sweep
     * and is where these came from; the bands it measures, across every shipped
     * grid size and every shipped tier, are:
     *
     * ```
     * two strikes, past the speed window       0.48 .. 0.49   one paw
     * one strike, past the window              0.57 .. 0.62   two paws
     * clean, past the window                   0.67 .. 0.75   three paws
     * clean, 2500ms a row (a considered pace)  0.79 .. 0.84   four paws
     * clean, 900ms a row (fast for the board)  0.92 .. 0.94   five paws
     * ```
     *
     * So the ladder reads: you finished; you finished after mistakes; you
     * finished clean; you finished clean and briskly; you finished clean and
     * fast. **Five paws is deliberately not common.** A clean run is necessary
     * and is not sufficient, which is what the owner asked for when a flawless
     * run was landing on four.
     *
     * The previous ladder was 0.60/0.70/0.78/0.85 and three of those four cuts
     * were below the floor of the clean band on every board, so they could only
     * ever be earned by losing bones. The fix was the multipliers rather than
     * these numbers; see the class KDoc for why moving them alone would have
     * been relabelling.
     *
     * Nothing had to be migrated for this, or for the move from three paws to
     * five before it. `LevelRecord.bestPaws` is stored rather than re-derived
     * and only ever goes up, so no record is demoted by a retune, and a score
     * banked under the old coefficients simply gets beaten by the next run.
     */
    val twoPawFraction: Double = 0.53,
    val threePawFraction: Double = 0.64,
    val fourPawFraction: Double = 0.77,
    val fivePawFraction: Double = 0.85,

    /**
     * Combined-multiplier cutoffs for the floating praise text.
     *
     * Fractions of the ceiling in disguise: the combined multiplier tops out at
     * `comboMax * speedMaxMultiplier`, so these move whenever either does or the
     * top word starts firing on an ordinary placement.
     */
    val nicePraiseAt: Double = 1.5,
    val greatPraiseAt: Double = 1.9,
    val excellentPraiseAt: Double = 2.4,
    val perfectPraiseAt: Double = 2.9,
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
        require(completionPerCell > 0) { "completionPerCell must be positive" }
        require(comboStep >= 0.0) { "comboStep must not be negative" }
        require(livesBonusRate >= 0.0) { "livesBonusRate must not be negative" }
        require(difficultyBonusRate >= 0.0) { "difficultyBonusRate must not be negative" }
        require(basePerPlacement <= MAX_POINT_VALUE && completionPerCell <= MAX_POINT_VALUE) {
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
         *
         * [completionPerCell] multiplies by the size *twice*, so the widest
         * intermediate is a million cells' worth of a 10x10, a hundred million,
         * and the largest legal par is a little under a billion.
         */
        const val MAX_POINT_VALUE: Int = 1_000_000

        val Default: ScoringConfig = ScoringConfig()
    }
}

/** The floating text over a placement. Cosmetic, keyed off the combined multiplier. */
enum class Praise { None, Nice, Great, Excellent, Perfect }
