package com.sodogku.libraries.progress.daily

import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * Everything the daily card renders, resolved for one local date.
 *
 * The card should never do date arithmetic of its own. [date], [packIndex] and
 * [resetsIn] all come from the same snapshot of the clock, so a card built from
 * one status can't show today's date next to tomorrow's board.
 */
data class DailyStatus(
    val date: LocalDate,
    /** Position in `daily.pack`, already wrapped and offset. */
    val packIndex: Int,
    /**
     * The id of the level at [packIndex]. Daily and campaign ids share a number
     * line — daily level 7 is not campaign level 7 — so anything that opens a
     * board from this needs the pack as well as the id.
     */
    val levelId: Int,
    /** Today's result, or `null` while the day is still open. */
    val result: DailyResult?,
    /** Consecutive days completed, recomputed from history on every read. */
    val streak: Int,
    /** Present only when a freeze would actually save something. */
    val freezeOffer: FreezeOffer?,
    /**
     * Present only when a restore would actually save something *and* is both in
     * reach and affordable. Never present at the same time as [freezeOffer]:
     * a one-day gap is the freeze's job and a longer one is beyond it.
     */
    val restoreOffer: RestoreOffer?,
    /** Until the local date rolls over and a new board is offered. */
    val resetsIn: Duration,
    /** `daily.enabled` and `features.dailyChallenge`, both of which gate the card. */
    val enabled: Boolean,
) {
    /**
     * A day that has any result is spent, win or lose. This is the whole of
     * one-attempt-per-day as far as the UI is concerned; the repository refuses
     * a second write regardless, so a card that ignored this could still not
     * overwrite a score.
     */
    val playable: Boolean get() = enabled && result == null
}

/**
 * A missed day a freeze would cover, offered only when covering it actually
 * reconnects the streak — spending an ad to buy nothing is worse than not being
 * asked.
 */
data class FreezeOffer(
    val missedDate: LocalDate,
    /** What [DailyStatus.streak] becomes if the offer is taken. */
    val streakIfUsed: Int,
    /** `daily.freezesPerMonth` minus the ones already spent on [missedDate]'s month. */
    val freezesRemaining: Int,
)

/** Outcome of taking a [FreezeOffer]. */
sealed interface FreezeResult {

    data class Applied(val missedDate: LocalDate, val streak: Int) : FreezeResult

    /** The player closed the ad early. The only outcome that withholds the freeze. */
    data object Declined : FreezeResult

    /** The monthly allowance is spent. */
    data object NoneLeft : FreezeResult

    /** Nothing to cover, or covering it would not extend the streak. */
    data object NothingToFreeze : FreezeResult
}

/**
 * A run of consecutive missed days a restore would bridge in one go.
 *
 * The freeze covers one day; this covers the gap the freeze cannot, which is the
 * only way a streak that has already broken comes back. The two never overlap —
 * a gap of one day is a freeze and nothing else.
 *
 * [withinReach] and [daysRemaining] are reported rather than applied, for the
 * reason [FreezeOffer.freezesRemaining] is: the caller needs to know *which*
 * refusal it is making. The card only shows the offer when it is [available].
 */
data class RestoreOffer(
    /** The missed days it would cover, oldest first. Always two or more. */
    val missedDates: List<LocalDate>,
    /** What [DailyStatus.streak] becomes if the offer is taken. */
    val streakIfUsed: Int,
    /**
     * `daily.restoreDaysPerMonth` minus the days already restored in the month
     * of the newest missed day — the month the player is standing in. For copy
     * only; [withinAllowance] is the answer, because a gap can straddle a month
     * end and each month pays for its own days.
     */
    val daysRemaining: Int,
    /** `false` when the gap is longer than `daily.restoreMaxDays`. */
    val withinReach: Boolean,
    /** `false` when some month the gap touches cannot pay for its share of it. */
    val withinAllowance: Boolean,
) {
    val days: Int get() = missedDates.size

    val available: Boolean get() = withinReach && withinAllowance
}

/** Outcome of taking a [RestoreOffer]. */
sealed interface RestoreResult {

    data class Applied(val days: Int, val streak: Int) : RestoreResult

    /** The player closed the ad early. The only outcome that withholds the restore. */
    data object Declined : RestoreResult

    /** The gap is longer than a restore reaches back. */
    data object OutOfReach : RestoreResult

    /** The monthly allowance cannot pay for this many days. */
    data object NoneLeft : RestoreResult

    /**
     * No gap, a gap of one day (which is the freeze's), or a gap whose bridging
     * would not lengthen the streak.
     */
    data object NothingToRestore : RestoreResult
}
