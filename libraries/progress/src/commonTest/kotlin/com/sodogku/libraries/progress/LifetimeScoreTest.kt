package com.sodogku.libraries.progress

import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyResult
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One number for everything the player has ever banked, and the window a weekly
 * board reads out of it.
 *
 * `withAttempt` is pinned in both directions, and the testing doc cites it as
 * the reason mutation checking is a house rule here: each direction alone
 * passes a different wrong implementation. That a replay does not double count
 * passes against "ignore the attempt entirely". That the number climbs on a
 * fresh level passes against "always add". Only the pair says what the function
 * does, so neither test is removable and neither is redundant.
 *
 * Campaign levels and dailies pay into the same total, and a daily is held out
 * of it the same way a level is. The daily case is not hypothetical arithmetic
 * even though a day cannot normally be replayed: a revived attempt after a loss
 * reaches the same code.
 *
 * The windowed half is the weekly leaderboard. Its boundary is inclusive, and
 * the reason is external rather than aesthetic: Game Center hands back the
 * instant a window *opened*, so a score stamped with exactly that instant
 * belongs to the window that opened and an exclusive comparison drops it from
 * both. The idle week returning zero matters for the same reason, since zero is
 * the value the submitter refuses to send, and a fold that fell back to the
 * lifetime total would stamp a stale number onto the board.
 *
 * ### Not here
 *
 * What an attempt is worth is `:libraries:scoring`. When a submission actually
 * goes out is the leaderboard section of `GameViewModelTest`.
 */
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
