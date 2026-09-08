package com.sodogku.libraries.progress

/**
 * The way past a level the player cannot solve.
 *
 * Separate from [ProgressRepository] on purpose. That one is the player's
 * history and nothing else, and the skip is a *transaction*: a daily allowance,
 * a rewarded ad, and only then the write. Putting an ad network behind the
 * interface that answers "what has this player done" would make every caller of
 * it depend on advertising.
 *
 * The cap is on everyone, Pro included (SPEC 1.6). Pro skips for free rather
 * than more often — without that a Pro player reaches level 500 in an afternoon
 * and has nothing left to play.
 */
interface SkipRepository {

    /**
     * Skips left today. Zero means the allowance is spent, not that skipping is
     * switched off.
     */
    suspend fun remainingToday(): Int

    /**
     * Plays the ad, spends one from today's allowance and records the skip.
     *
     * The allowance is checked *before* the ad, so nobody watches thirty seconds
     * of advertising for a skip that was never available.
     */
    suspend fun skip(levelId: Int): SkipResult
}

/**
 * What came of a skip. Every branch is something the player can be told; a skip
 * that silently does nothing is indistinguishable from a crash.
 */
sealed interface SkipResult {

    /** Done. [remainingToday] is what is left *after* this one. */
    data class Skipped(val remainingToday: Int) : SkipResult

    /** Today's allowance is spent. */
    data object NoneLeft : SkipResult

    /**
     * The player closed the ad early.
     *
     * The only outcome that withholds the skip. `NoFill`, `Offline` and a failed
     * SDK all grant it — SPEC 4.2: an ad network outage may never be the reason
     * a player is stuck on a board.
     */
    data object Declined : SkipResult
}
