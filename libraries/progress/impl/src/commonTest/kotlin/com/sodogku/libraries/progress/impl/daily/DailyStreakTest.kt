package com.sodogku.libraries.progress.impl.daily

import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyResult
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The streak rules, against the fold that produces them.
 *
 * Every assertion here names an exact number rather than "at least one" or "not
 * broken". A fold that always returned zero would satisfy every rule phrased as
 * "a missed day breaks the streak", and zero is exactly what a bug in the walk
 * produces, so the tests have to be able to tell those two apart.
 *
 * Persistence, the ad and the monthly cap are the repository's; this file only
 * knows dates and outcomes.
 */
class DailyStreakTest {

    private val today = LocalDate(2026, 9, 7)

    @Test
    fun noHistory_isNoStreak() {
        assertEquals(0, streakOn(today, emptyMap()))
    }

    @Test
    fun consecutiveCompletions_countEveryOneOfThem() {
        val history = completed(daysBack = 0..4)

        assertEquals(5, streakOn(today, history))
    }

    @Test
    fun aStreakSurvivesTheDayBeforeItIsPlayed() {
        val throughYesterday = completed(daysBack = 1..3)

        assertEquals(
            3,
            streakOn(today, throughYesterday),
            "the run is intact all day today; it only breaks once today is over",
        )
        assertEquals(
            4,
            streakOn(today, throughYesterday + completed(0..0)),
            "and playing today adds to it rather than restarting it",
        )
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        val history = completed(daysBack = 0..1) + completed(daysBack = 3..6)

        assertEquals(
            2,
            streakOn(today, history),
            "the four days behind the gap are history, not part of the current run",
        )
    }

    @Test
    fun todaysLossDoesNotEndTheRunEarly() {
        val history = completed(daysBack = 1..3) + mapOf(today to DailyOutcome.Failed)

        assertEquals(
            3,
            streakOn(today, history),
            "the day is spent, but the run through yesterday stands until midnight",
        )
    }

    @Test
    fun aFailedDayBreaksTheStreakOnceItIsPast() {
        val history = completed(daysBack = 0..0) +
            mapOf(today.minusDays(1) to DailyOutcome.Failed) +
            completed(daysBack = 2..5)

        assertEquals(1, streakOn(today, history), "losing is not missing; there is nothing to cover")
    }

    @Test
    fun aFrozenDayBridgesTheGapWithoutCountingItself() {
        val history = completed(daysBack = 0..1) +
            mapOf(today.minusDays(2) to DailyOutcome.Frozen) +
            completed(daysBack = 3..5)

        assertEquals(
            5,
            streakOn(today, history),
            "two plus three days played; the ad is not a sixth day of playing",
        )
    }

    @Test
    fun aFreezeCoversExactlyOneDay() {
        val twoDaysMissed = completed(daysBack = 0..0) +
            mapOf(today.minusDays(1) to DailyOutcome.Frozen) +
            completed(daysBack = 3..5)

        assertEquals(
            1,
            streakOn(today, twoDaysMissed),
            "the second missed day is still missed, so the older run stays cut off",
        )
    }

    @Test
    fun futureResultsAreInvisible() {
        val timeTravelled = completed(daysBack = 0..2) +
            mapOf(today.plusDays(1) to DailyOutcome.Completed, today.plusDays(2) to DailyOutcome.Completed)

        assertEquals(
            3,
            streakOn(today, timeTravelled),
            "a clock set forward and back leaves rows ahead of today; they wait their turn",
        )
    }

    @Test
    fun freezeOffer_namesTheMissedDayAndWhatItBuysBack() {
        val results = resultsFor(completed(daysBack = 0..1) + completed(daysBack = 3..9))

        val offer = freezeOfferOn(today, results, freezesPerMonth = 2)

        assertEquals(today.minusDays(2), offer?.missedDate)
        assertEquals(2, streakOn(today, results.mapValues { it.value.outcome }))
        assertEquals(9, offer?.streakIfUsed, "two days plus the seven the freeze reconnects")
        assertEquals(2, offer?.freezesRemaining)
    }

    @Test
    fun freezeOffer_isWithheldWhenItWouldBuyNothing() {
        val nothingBehindTheGap = resultsFor(completed(daysBack = 0..1))

        assertNull(
            freezeOfferOn(today, nothingBehindTheGap, freezesPerMonth = 2),
            "the day before the run was never played either; a freeze reconnects nothing",
        )
    }

    @Test
    fun freezeOffer_isWithheldAfterALoss() {
        val lostThenPlayed = resultsFor(
            completed(daysBack = 0..1) + mapOf(today.minusDays(2) to DailyOutcome.Failed) + completed(3..9)
        )

        assertNull(
            freezeOfferOn(today, lostThenPlayed, freezesPerMonth = 2),
            "an attempt was spent on that day; an ad may not undo a loss",
        )
    }

    @Test
    fun freezeOffer_countsWhatIsLeftThisMonth() {
        val onlyOneLeft = resultsFor(
            completed(daysBack = 0..1) +
                completed(daysBack = 3..4) +
                mapOf(LocalDate(2026, 9, 1) to DailyOutcome.Frozen) +
                mapOf(LocalDate(2026, 8, 2) to DailyOutcome.Frozen)
        )

        val offer = freezeOfferOn(today, onlyOneLeft, freezesPerMonth = 2)

        assertEquals(1, offer?.freezesRemaining, "August's freeze does not come out of September's two")
    }

    @Test
    fun freezeOffer_reportsAnExhaustedAllowanceRatherThanHidingIt() {
        val spent = resultsFor(
            completed(daysBack = 0..1) +
                completed(daysBack = 3..4) +
                mapOf(LocalDate(2026, 9, 1) to DailyOutcome.Frozen, LocalDate(2026, 9, 2) to DailyOutcome.Frozen)
        )

        val offer = freezeOfferOn(today, spent, freezesPerMonth = 2)

        assertTrue(offer != null, "the caller needs to know it is the cap refusing, not the streak")
        assertEquals(0, offer.freezesRemaining)
    }

    private fun completed(daysBack: IntRange): Map<LocalDate, DailyOutcome> =
        daysBack.associate { today.minusDays(it) to DailyOutcome.Completed }

    private fun resultsFor(outcomes: Map<LocalDate, DailyOutcome>): Map<LocalDate, DailyResult> =
        outcomes.mapValues { (date, outcome) ->
            DailyResult(date = date, levelIndex = 0, outcome = outcome, score = 0, paws = 0, timeMs = 0)
        }

    private fun LocalDate.minusDays(days: Int): LocalDate {
        var date = this
        repeat(days) { date = date.previousDay() }
        return date
    }

    private fun LocalDate.plusDays(days: Int): LocalDate {
        var date = this
        repeat(days) { date = date.nextDay() }
        return date
    }
}
