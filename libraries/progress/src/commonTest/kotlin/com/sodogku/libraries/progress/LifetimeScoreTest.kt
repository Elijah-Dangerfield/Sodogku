package com.sodogku.libraries.progress

import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyResult
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LifetimeScoreTest {

    @Test
    fun campaignAndDailyPayIntoTheSameNumber() {
        val total = LifetimeScore.banked(
            levels = listOf(cleared(1, 1_000), cleared(2, 2_500)),
            dailies = listOf(day(1, 4_000), day(2, 3_000)),
        )

        assertEquals(10_500, total, "one score means the daily counts too")
    }

    @Test
    fun aLevelThatWasStartedAndNeverClearedAddsNothing() {
        val total = LifetimeScore.banked(
            levels = listOf(cleared(1, 1_000), LevelRecord.unplayed(2)),
            dailies = emptyList(),
        )

        assertEquals(1_000, total)
    }

    @Test
    fun replayingAClearedLevelOnlyMovesTheTotalOnceTheOldBestIsPassed() {
        // The bug this rule exists to stop. Level 2 is worth 2,500 and it is
        // already in `banked`; an attempt at it may only ever replace that
        // 2,500, never stack on top of it.
        val levels = listOf(cleared(1, 1_000), cleared(2, 2_500))
        val banked = LifetimeScore.banked(levels, dailies = emptyList())
        val held = LifetimeScore.bankedForLevel(levels, levelId = 2)

        assertEquals(3_500, LifetimeScore.withAttempt(banked, held, attemptScore = 0))
        assertEquals(3_500, LifetimeScore.withAttempt(banked, held, attemptScore = 1_000))
        assertEquals(3_500, LifetimeScore.withAttempt(banked, held, attemptScore = 2_500))
        assertEquals(3_600, LifetimeScore.withAttempt(banked, held, attemptScore = 2_600))
    }

    @Test
    fun aFirstAttemptAtAFreshLevelAddsEveryPointAsItIsEarned() {
        // The other direction, and the reason the rule is not simply "ignore
        // the attempt": a level with no record has banked nothing, so the whole
        // attempt is new and the number has to climb with it.
        val levels = listOf(cleared(1, 1_000))
        val banked = LifetimeScore.banked(levels, dailies = emptyList())
        val held = LifetimeScore.bankedForLevel(levels, levelId = 2)

        assertEquals(0, held, "level 2 has never been cleared")
        assertEquals(1_400, LifetimeScore.withAttempt(banked, held, attemptScore = 400))
        assertEquals(2_200, LifetimeScore.withAttempt(banked, held, attemptScore = 1_200))
    }

    @Test
    fun todaysDailyIsHeldOutOfTheTotalTheSameWayALevelIs() {
        // A daily cannot normally be replayed, but the arithmetic must not
        // depend on that — a revived attempt after a loss reaches the same code.
        val dailies = listOf(day(1, 4_000), day(2, 3_000))
        val banked = LifetimeScore.banked(levels = emptyList(), dailies = dailies)
        val held = LifetimeScore.bankedForDaily(dailies, date = date(2))

        assertEquals(3_000, held)
        assertEquals(7_000, LifetimeScore.withAttempt(banked, held, attemptScore = 500))
        assertEquals(9_000, LifetimeScore.withAttempt(banked, held, attemptScore = 5_000))
    }

    @Test
    fun anUnplayedDayHoldsNothingBackFromTheTotal() {
        val dailies = listOf(day(1, 4_000))

        assertEquals(0, LifetimeScore.bankedForDaily(dailies, date = date(9)))
    }

    @Test
    fun onlyWhatWasBankedInsideTheWindowCounts() {
        // The whole weekly board in one assertion. The player is worth 10,000
        // all told and 900 of it landed this week, and it is the 900 that is
        // submitted — a board fed the lifetime total would rank a newcomer
        // against six months of somebody else's play.
        val ledger = listOf(
            ScoreEvent(atMillis = MONDAY - 1, points = 9_100),
            ScoreEvent(atMillis = MONDAY + 1, points = 400),
            ScoreEvent(atMillis = MONDAY + 2, points = 500),
        )

        assertEquals(900, LifetimeScore.bankedSince(ledger, MONDAY))
    }

    @Test
    fun aPointBankedInTheInstantTheWindowOpenedIsInsideIt() {
        // The boundary, and the reason it is inclusive rather than a matter of
        // taste: Game Center hands back the instant the window *opened*, so a
        // score stamped with that instant belongs to the window that opened and
        // not to the one that closed. An exclusive test loses it from both.
        val ledger = listOf(ScoreEvent(atMillis = MONDAY, points = 750))

        assertEquals(750, LifetimeScore.bankedSince(ledger, MONDAY))
    }

    @Test
    fun aPointBankedTheMillisecondBeforeTheWindowIsOutsideIt() {
        val ledger = listOf(ScoreEvent(atMillis = MONDAY - 1, points = 750))

        assertEquals(0, LifetimeScore.bankedSince(ledger, MONDAY))
    }

    @Test
    fun aPlayerWhoHasNotPlayedThisWeekScoresZero() {
        // Not a redundant case: zero is the value `RealLeaderboards` refuses to
        // send, so a fold that returned the lifetime total for an idle week
        // would put a stale number on the board instead of leaving it alone.
        val ledger = listOf(ScoreEvent(atMillis = MONDAY - 100, points = 9_100))

        assertEquals(0, LifetimeScore.bankedSince(ledger, MONDAY))
    }

    private fun cleared(levelId: Int, score: Int): LevelRecord = LevelRecord(
        levelId = levelId,
        state = LevelState.Completed,
        bestScore = score,
        bestPaws = 2,
        bestTimeMs = 60_000,
        attempts = 1,
        firstCompletedAt = 1L,
        lastPlayedAt = 1L,
    )

    private fun day(dayOfMonth: Int, score: Int): DailyResult = DailyResult(
        date = date(dayOfMonth),
        levelIndex = dayOfMonth,
        outcome = DailyOutcome.Completed,
        score = score,
        paws = 3,
        timeMs = 90_000,
    )

    private fun date(dayOfMonth: Int): LocalDate = LocalDate(2026, 9, dayOfMonth)

    private companion object {
        /** An arbitrary instant standing in for one Game Center handed back. */
        const val MONDAY = 1_757_376_000_000L
    }
}
