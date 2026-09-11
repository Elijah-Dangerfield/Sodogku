package com.sodogku.libraries.progress.daily

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The daily challenge: which board today is, whether it has been spent, and how
 * long the streak is.
 *
 * **Nothing here is a counter.** The streak is folded out of the stored results
 * on every read, so there is no number on disk that a bug, a timezone change or
 * a device clock set to 1970 can leave wrong. The worst a strange clock can do
 * is make today resolve to a different day; the history it reads is unchanged
 * and the answer corrects itself the moment the clock does.
 *
 * Device-local like the rest of `:libraries:progress` — no server decides the
 * date, and no server can be asked to arbitrate a streak.
 */
interface DailyRepository {

    /**
     * The card's live state. Re-emits when a result is written **and** when the
     * local date rolls over, so a card left on screen at midnight picks up the
     * new board without the screen having to watch the clock itself.
     */
    fun observe(): Flow<DailyStatus>

    suspend fun status(): DailyStatus

    /**
     * The days the player has played or frozen, oldest first. For the share
     * sheet and the achievement fold; the card wants [status].
     */
    suspend fun history(): List<DailyResult>

    /**
     * Records a clear against the day whose board was played.
     *
     * The date is a parameter rather than "now" because an attempt that starts
     * at 23:58 and ends at 00:01 belongs to the board it was started on. Passing
     * [DailyStatus.date] back from the status the game was launched with also
     * leaves the new day genuinely unplayed, which is the generous reading and
     * the one that keeps the streak honest.
     *
     * A second call for a date that already has a result is ignored — that is
     * the one-attempt rule, and it is enforced here rather than by hiding the
     * card. It is also the *only* thing enforcing it: restarting a lost daily is
     * allowed (SD-49) precisely because the rule lives on this table rather than
     * in the controls the game offers.
     *
     * **The only writer of a played day.** A run that ends out of bones writes
     * nothing at all, so the day stays open and can still be revived, restarted
     * or simply left. There used to be an `onFailed` beside this for Give up on
     * today; that control is gone and so is the write. See `decisions.md`.
     */
    suspend fun onCompleted(date: LocalDate, score: Int, paws: Int, timeMs: Long)

    /**
     * Trades a rewarded ad for the freeze in [DailyStatus.freezeOffer], covering
     * exactly one missed day.
     *
     * The ad is shown here, next to the monthly cap, so the two halves of the
     * rule cannot drift apart in a screen that forgets one of them. Only a
     * deliberate dismissal withholds the freeze — an ad network with no fill
     * must not be what ends someone's streak.
     */
    suspend fun useFreeze(): FreezeResult

    /**
     * Trades a rewarded ad for the whole gap in [DailyStatus.restoreOffer],
     * bringing back a streak that has already broken.
     *
     * Bounded in two directions, and both bounds are the mechanic rather than a
     * detail: `daily.restoreMaxDays` is how far back it reaches, because
     * restoring a run abandoned months ago is a gift and not a restore, and
     * `daily.restoreDaysPerMonth` is how often, because a streak that cannot
     * break is not a streak.
     *
     * Shares the freeze's ad placement and its fail-open rule — only a deliberate
     * dismissal withholds it.
     */
    suspend fun restoreStreak(): RestoreResult

    suspend fun reset()
}
