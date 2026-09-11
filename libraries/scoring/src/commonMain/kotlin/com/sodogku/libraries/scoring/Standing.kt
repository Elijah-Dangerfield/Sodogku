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
 *
 * **Four words, two cuts, and the cuts are the bottom two rungs of the paw
 * ladder** — not the top two. That is worth stating because it has been wrong
 * once and the way it went wrong is the way it would go wrong again.
 *
 * The verdict has always been cut at the two-paw and three-paw lines, which is
 * what every sentence below still describes. When the rating went from three
 * paws to five, both cuts were moved up a rung to keep the *names* of the
 * config fields they read, so [Solid] started reading `threePawFraction` and
 * [Sharp] and [Flawless] started reading `fivePawFraction`. Nothing looked
 * broken: each threshold was still a real number and each verdict still had a
 * branch. But `ScoringConfig` also says, in as many words, that five paws
 * requires a clean run. [Sharp] is five paws' worth of score *with a bone
 * lost*, so those two sentences between them defined it as the empty set, and
 * measurement agreed: on a 10x10 tier 4 it needed 2.3 seconds a move, and on
 * the starter-dog 5x5 and 6x6 tier-4 boards it could not be earned at all.
 * "Sharp work" was a shipped string that never rendered.
 *
 * So the cuts are back on the rungs the words were written for, and the bands
 * they measure over every shape both curves ship are:
 *
 * ```
 * Scraped   0.48 .. 0.50   two bones spent and past the speed window
 * Solid     0.57 .. 0.63   one bone spent and slow, or two at a fair pace
 * Sharp     0.65 .. 0.81   bones spent, and still scoring what a clean run does
 * Flawless  0.69 .. 0.95   every bone kept, at any pace
 * ```
 *
 * The lesson is not about these particular numbers. It is that a verdict reads
 * `livesRemaining` and a paw does not, so a rung can be perfectly reachable
 * while the verdict cut from it is empty. Reaching for a higher fraction to
 * make a word sound rarer is how that happens.
 * `PawLadderReachabilityTest.everyVerdictCanBeEarnedOnEveryShapeTheGameShips`
 * plays every shape at every pace and is what says no.
 */
enum class Standing {
    /** Finished, but well under what a clean run pays. */
    Scraped,

    /** A solid clear: past the two-paw line, short of the third. */
    Solid,

    /** Past the three-paw line, with at least one bone spent getting there. */
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
    /** Dogs the player placed. Pass `ScoreCard.placements`; see [Scoring.parScore]. */
    placements: Int = size,
    config: ScoringConfig = ScoringConfig.Default,
): Standing? {
    if (!completed) return null
    val par = parScore(size, difficulty, placements, config)
    val sharp = score >= par * config.threePawFraction
    return when {
        sharp && livesRemaining >= ScoringConfig.MAX_LIVES -> Standing.Flawless
        sharp -> Standing.Sharp
        score >= par * config.twoPawFraction -> Standing.Solid
        else -> Standing.Scraped
    }
}
