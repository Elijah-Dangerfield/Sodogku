package com.sodogku.libraries.progress

/**
 * One entry: [points] were banked at [atMillis], device wall clock.
 *
 * The points, not the total. Every other number in this module is a *best* that
 * only moves upward, and a best cannot answer "how much of that arrived this
 * week" — a level cleared for 5,000 in March and replayed for 6,000 today
 * contributed 1,000 today, and nothing in `level_progress` remembers that.
 * `daily_result` is worse off still: it keeps a local date and no time at all.
 */
data class ScoreEvent(val atMillis: Long, val points: Int)

/**
 * An append-only record of *when* points were earned, and the only thing in the
 * app that can answer a question with a start date in it.
 *
 * ## Why this is a second store and not a query over the first
 *
 * [LifetimeScore.banked] folds the tables that already exist, and the KDoc there
 * argues at length that a stored tally has no witness. This is not a tally. It
 * is the witness: rows are written once, never updated, and the number anybody
 * reads out of it is still a fold. The two coexist because they answer different
 * questions — the lifetime total is the sum of every best, and this is the sum
 * of every increment inside a window, and neither can be derived from the other.
 *
 * ## What it is deliberately not
 *
 * It is not authoritative. If a bug or a crash loses an entry the lifetime total
 * is untouched and the campaign is untouched; the only consequence is a weekly
 * leaderboard score that is low for at most one window. Nothing branches on it
 * and no screen reads it, which is why it can afford to be pruned.
 *
 * There is no `reset()` for the same reason. `ProgressRepository.reset()` and
 * `DailyRepository.reset()` each wipe half of what pays into this, and a reset
 * that took one half would be wrong in a way nothing could see. Left alone, the
 * ledger disagrees with a wiped campaign for one window and then stops, because
 * a window only ever looks a week back.
 *
 * ## Pruning
 *
 * [bankedSince] reads the whole table, so the table has to stay small.
 * [RETENTION_MILLIS] is far longer than any window anyone would submit against
 * — the longest recurrence Game Center will accept is 30 days — so pruning can
 * never take a row the current window still wanted.
 */
interface ScoreLedger {

    /**
     * Records that [points] have just been banked. Zero or fewer is not an
     * event and writes nothing: a replay that failed to beat an old best earned
     * the player nothing, and a row saying so would only be read back and added
     * to zero.
     */
    suspend fun bank(points: Int)

    /** Everything banked at or after [startMillis]. */
    suspend fun bankedSince(startMillis: Long): Int

    companion object {
        /**
         * How far back the ledger is kept. Six times the longest window Game
         * Center allows, so the margin is not a judgement call.
         */
        const val RETENTION_MILLIS: Long = 180L * 24 * 60 * 60 * 1000
    }
}
