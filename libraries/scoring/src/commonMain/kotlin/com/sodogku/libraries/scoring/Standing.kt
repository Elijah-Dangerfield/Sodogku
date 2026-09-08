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
     * Past par outright.
     *
     * Reachable, and deliberately so: par prices every placement at the *maximum*
     * speed multiplier but only the starting combo, so a player who builds a long
     * combo can exceed it. That makes this the one verdict that is genuinely
     * about doing something unusual rather than about clearing a threshold.
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
    config: ScoringConfig = ScoringConfig.Default,
): Standing? {
    if (!completed) return null
    val par = parScore(size, difficulty, config)
    return when {
        score >= par -> Standing.Flawless
        score >= par * config.threePawFraction -> Standing.Sharp
        score >= par * config.twoPawFraction -> Standing.Solid
        else -> Standing.Scraped
    }
}
