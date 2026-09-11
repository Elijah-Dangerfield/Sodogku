package com.sodogku.libraries.scoring

import kotlin.math.ceil

/**
 * How close a run came to the next paw, when it is worth saying.
 *
 * The tempting version of this is "five seconds faster and you would have had
 * another paw", and it is a lie more often than not. Score is time *and* combo
 * *and* mistakes, so inverting it to a number of seconds means assuming the rest
 * of the run stayed identical, and a player who takes the advice and fails
 * learns that the app makes things up.
 *
 * So this reports the gap in the one unit that is always true: points. And it
 * only reports it at all when the gap is small enough that another attempt
 * plausibly closes it, because "you were 2,000 points off" is not encouragement,
 * it is a scoreline.
 */
data class NearMiss(
    /** The rating one rung above what this run earned. */
    val nextPaw: Int,

    /** Points still needed. Always positive. */
    val pointsShort: Int,
)

/**
 * The gap to the next paw, or null when there is nothing useful to say.
 *
 * Null in three cases, each of which would otherwise produce a bad message:
 * there is no rung above this run (it did not finish, or it earned every paw),
 * the score already clears the next rung, or the gap is wider than
 * [NearMissFraction] of par.
 */
fun Scoring.nearMiss(
    score: Int,
    size: Int,
    difficulty: Int,
    completed: Boolean,
    /** Dogs the player placed. Pass `ScoreCard.placements`; see [Scoring.parScore]. */
    placements: Int = size,
    config: ScoringConfig = ScoringConfig.Default,
): NearMiss? {
    val earned = paws(score, size, difficulty, completed, placements, config)
    val par = parScore(size, difficulty, placements, config)

    // The rung above whatever was earned. Indexed off the earned count rather
    // than searched for, so a ladder with a repeated fraction cannot loop.
    //
    // The `else` is the only guard needed for "there is no rung above this one",
    // and it covers both ways that happens: an unfinished run rates 0, and a
    // perfect one rates MAX_PAWS. Explicit early returns for those two were
    // written first and deleted, because mutation testing showed neither could
    // fail -- this branch caught them both, so they were unreachable code
    // pretending to be a rule.
    val nextFraction = when (earned) {
        Scoring.ONE_PAW -> config.twoPawFraction
        Scoring.TWO_PAWS -> config.threePawFraction
        Scoring.THREE_PAWS -> config.fourPawFraction
        Scoring.FOUR_PAWS -> config.fivePawFraction
        else -> return null
    }

    // Rounded up, because `paws` awards on `score >= par * fraction` and
    // compares the Double. The smallest *score* that clears the rung is
    // therefore the ceiling of the product, and this used to take the floor.
    //
    // That is not a rounding quibble, it is wrong twice, and on 221 of the 224
    // (size, tier, placements, rung) combinations the campaign ships, because
    // `par * fraction` is a whole number on only 3 of them. A score sitting
    // exactly on the floor does not earn the paw, and the old arithmetic made
    // `short` zero there, so the closest possible miss in the whole game got no
    // line at all. And every gap that *was* reported was one point light: a run
    // told it needed 10 more scored 10 more and stayed on the same rung.
    //
    // The number is the only thing this file offers, and a number the player can
    // act on and be wrong about is worse than no number.
    val needed = ceil(par * nextFraction).toInt()
    val short = needed - score
    if (short <= 0) return null
    if (short > par * NearMissFraction) return null

    return NearMiss(nextPaw = earned + 1, pointsShort = short)
}

/**
 * How close counts as close: a twelfth of par.
 *
 * Roughly the width of one rung on the shipped ladder, so "nearly" means the run
 * was inside the band it was aiming at rather than merely on the way to it. Wide
 * enough that it fires on a genuinely near miss, narrow enough that it does not
 * congratulate somebody who was never close.
 */
private const val NearMissFraction = 1.0 / 12.0
