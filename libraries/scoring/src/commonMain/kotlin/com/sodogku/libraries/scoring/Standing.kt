package com.sodogku.libraries.scoring

/**
 * How well a finished run went, as one of a handful of verdicts.
 *
 * The ask was "Beat 84.6% of players". A real percentile needs every player's
 * score for every level on a server, which is a backend, a schema, a privacy
 * story and a cold-start problem — on day one the honest number for everybody is
 * "beat 0% of players", and it stays useless until there are enough players to
 * rank against, which is exactly when the app least needs help retaining anyone.
 *
 * A **faked** percentile is worse than either. It is a number that looks like
 * measurement, and the first person to notice that two different runs both
 * "beat 84.6%" has been told the app makes things up.
 *
 * So the verdict is about the player's own run, measured against the same par
 * the paws use. It says something true, it needs no server, and it is available
 * from the first level of a fresh install. If real percentiles ever exist they
 * can replace the copy behind this without moving the seam.
 */
enum class Standing {
    /** Finished, but well under what a clean run pays. */
    Scraped,

    /** A solid clear: past the two-paw line, short of the third. */
    Solid,

    /** Past the three-paw line. */
    Sharp,

    /**
     * Past the three-paw line *and* not a single bone lost.
     *
     * This used to mean "scored at or above par", justified here by a claim that
     * par priced only the starting combo so a long combo could beat it. That was
     * simply false: [Scoring.parScore] sums `comboMultiplier(index)` across the
     * whole board, which is the same full ramp a real run earns, and its own
     * KDoc says par "is a number no human equals". Driving the real placement
     * API confirmed it — a perfect run at **1ms** per move lands 1 to 8 points
     * under par on every shipped shape and comes out [Sharp]. Only a run with
     * literally zero elapsed milliseconds on every placement reached it.
     *
     * So the old definition was a rating nobody could ever earn, which is the
     * exact bug the third paw already had once. The tests missed it because they
     * synthesised scores as a fraction of par instead of playing a board, so
     * they proved a number was classified correctly without ever asking whether
     * a run could produce that number.
     *
     * Repricing par was the other option and it is worse: par is the scale the
     * paw thresholds are fractions of, so lowering it hands three paws to any
     * clean finish.
     *
     * Measuring mistakes instead is also just what the word means. A flawless
     * run is one without a flaw, not one that beat an unreachable ceiling.
     */
    Flawless,
}

/**
 * Where [score] stands against what this board pays for a clean, quick run.
 *
 * Deliberately the same `parScore` the paws use, rather than a second yardstick.
 * Two independent measures of the same thing is how a sheet ends up showing
 * three paws next to "you scraped that one".
 *
 * Returns null for a board that was not finished. There is no verdict on a run
 * that did not end, and returning a "worst" verdict instead would put a judgement
 * on the lose sheet, which is the last place anybody needs one.
 */
fun Scoring.standingFor(
    score: Int,
    size: Int,
    difficulty: Int,
    completed: Boolean,
    /**
     * Bones left at the end. Only [Standing.Flawless] reads it, and only to ask
     * whether any were spent, so a caller that does not track lives can pass
     * [ScoringConfig.MAX_LIVES] and get the same three verdicts as before.
     */
    livesRemaining: Int = ScoringConfig.MAX_LIVES,
    config: ScoringConfig = ScoringConfig.Default,
): Standing? {
    if (!completed) return null
    val par = parScore(size, difficulty, config)
    val sharp = score >= par * config.threePawFraction
    return when {
        sharp && livesRemaining >= ScoringConfig.MAX_LIVES -> Standing.Flawless
        sharp -> Standing.Sharp
        score >= par * config.twoPawFraction -> Standing.Solid
        else -> Standing.Scraped
    }
}
