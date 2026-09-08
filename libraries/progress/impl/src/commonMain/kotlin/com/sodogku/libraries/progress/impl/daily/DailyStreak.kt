package com.sodogku.libraries.progress.impl.daily

import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.progress.daily.FreezeOffer
import com.sodogku.libraries.progress.daily.RestoreOffer
import kotlinx.datetime.LocalDate

/**
 * The streak, folded out of the stored results every time it is asked for.
 *
 * A stored counter would be one dropped write or one bad clock away from a number
 * nobody can reconstruct, and the player would have no way to tell us it was
 * wrong. This walks backwards from [today] instead, so it is a pure function of
 * what is on disk and it re-derives correctly after any bug we later fix.
 *
 * Rules, in the order they bite:
 *
 * - **Today does not have to be done yet.** A run through yesterday still counts
 *   all day today; it only breaks once today is over. So the walk starts at today
 *   when today is completed, and at yesterday otherwise — including when today
 *   was played and lost, which spends the day without ending the run early.
 * - **A bridged day bridges, it does not count.** The streak is "days you
 *   played", and an ad is not a day you played. Two adjacent runs joined by a
 *   freeze or a restore are one run of their own lengths, never one longer.
 * - **A failed day ends it.** Not missed, so not something a freeze or a restore
 *   may cover.
 * - **Future-dated results are invisible**, which is what a clock set forward and
 *   back leaves behind. The walk only ever moves backwards from today, so they
 *   sit on disk unread until the date catches up with them.
 */
internal fun streakOn(today: LocalDate, outcomes: Map<LocalDate, DailyOutcome>): Int {
    var day = if (outcomes[today] == DailyOutcome.Completed) today else today.previousDay()
    var streak = 0
    while (true) {
        when (outcomes[day]) {
            DailyOutcome.Completed -> streak++
            DailyOutcome.Frozen, DailyOutcome.Restored -> Unit
            else -> return streak
        }
        day = day.previousDay()
    }
}

/**
 * The day a freeze would cover: the first *missed* day the streak walk runs into.
 *
 * Missed means no row at all. A day the player attempted and lost is not missed
 * and cannot be bought back — they had their turn, and letting an ad undo a loss
 * would make the bones on the daily meaningless.
 */
internal fun missedDayBefore(today: LocalDate, outcomes: Map<LocalDate, DailyOutcome>): LocalDate? {
    var day = if (outcomes[today] == DailyOutcome.Completed) today else today.previousDay()
    while (true) {
        when (outcomes[day]) {
            DailyOutcome.Completed, DailyOutcome.Frozen, DailyOutcome.Restored -> day = day.previousDay()
            DailyOutcome.Failed -> return null
            null -> return day
        }
    }
}

/**
 * The offer to make, or `null` when there is nothing worth offering.
 *
 * "Worth" is defined against [streakOn] itself rather than by a second walk with
 * its own idea of the rules: the offer stands exactly when applying it would
 * produce a longer streak. That is why a lone missed day with nothing behind it
 * is not offered — freezing it buys the player nothing, and charging an ad for
 * nothing is how a rewarded placement teaches people to ignore it.
 *
 * The monthly cap is *reported*, not applied: an offer with no freezes left still
 * comes back so the caller can say which of the two reasons it is refusing. The
 * card hides it; `useFreeze` answers `NoneLeft`.
 */
internal fun freezeOfferOn(
    today: LocalDate,
    results: Map<LocalDate, DailyResult>,
    freezesPerMonth: Int,
): FreezeOffer? {
    val outcomes = results.mapValues { it.value.outcome }
    val missed = missedDayBefore(today, outcomes) ?: return null

    val streakIfUsed = streakOn(today, outcomes + (missed to DailyOutcome.Frozen))
    if (streakIfUsed <= streakOn(today, outcomes)) return null

    val used = results.values.count { it.outcome == DailyOutcome.Frozen && it.date.inSameMonthAs(missed) }
    return FreezeOffer(
        missedDate = missed,
        streakIfUsed = streakIfUsed,
        freezesRemaining = (freezesPerMonth - used).coerceAtLeast(0),
    )
}

/**
 * The unbroken run of missed days the streak walk stops on, newest first.
 *
 * Walks at most [maxDays] + 1 days. The extra one is what makes "too long"
 * distinguishable from "exactly at the limit" without walking a player's whole
 * absence — someone who last played in March has an unbounded gap behind them,
 * and the walk must not go looking for its far end.
 */
internal fun missedRunBefore(
    today: LocalDate,
    outcomes: Map<LocalDate, DailyOutcome>,
    maxDays: Int,
): List<LocalDate> {
    val newest = missedDayBefore(today, outcomes) ?: return emptyList()
    val run = mutableListOf(newest)
    var day = newest.previousDay()
    while (run.size <= maxDays && outcomes[day] == null) {
        run += day
        day = day.previousDay()
    }
    return run
}

/**
 * The restore to offer, or `null` when there is nothing a restore could do.
 *
 * Deliberately silent about one-day gaps: that is a freeze, the freeze is
 * cheaper and more plentiful, and two buttons for the same missed day is a choice
 * the player has no information to make. The two offers are mutually exclusive by
 * construction, not by a rule someone has to remember.
 *
 * Both bounds are *reported*, not applied, so the caller can name which refusal
 * it is making. The status filters on [RestoreOffer.available].
 *
 * [daysPerMonth] is denominated in **days**, not restores, because a restore is
 * not identifiable from the rows it leaves: two restores whose runs end up
 * adjacent are indistinguishable from one longer one, so any count of "restores
 * this month" would be a count that under-reports. Days are exactly what is on
 * disk. Each month a run touches pays for its own days.
 */
internal fun restoreOfferOn(
    today: LocalDate,
    results: Map<LocalDate, DailyResult>,
    maxDays: Int,
    daysPerMonth: Int,
): RestoreOffer? {
    val outcomes = results.mapValues { it.value.outcome }
    val run = missedRunBefore(today, outcomes, maxDays)
    if (run.size < MinRestoreDays) return null

    val streakIfUsed = streakOn(today, outcomes + run.associateWith { DailyOutcome.Restored })
    if (streakIfUsed <= streakOn(today, outcomes)) return null

    return RestoreOffer(
        missedDates = run.reversed(),
        streakIfUsed = streakIfUsed,
        daysRemaining = restoreDaysLeftIn(run.first(), results, daysPerMonth),
        withinReach = run.size <= maxDays,
        withinAllowance = run.groupBy { it.year to it.month }.all { (_, days) ->
            restoreDaysLeftIn(days.first(), results, daysPerMonth) >= days.size
        },
    )
}

private fun restoreDaysLeftIn(
    month: LocalDate,
    results: Map<LocalDate, DailyResult>,
    daysPerMonth: Int,
): Int {
    val used = results.values.count {
        it.outcome == DailyOutcome.Restored && it.date.inSameMonthAs(month)
    }
    return (daysPerMonth - used).coerceAtLeast(0)
}

/** One missed day is a freeze. A restore starts where the freeze runs out. */
private const val MinRestoreDays = 2
