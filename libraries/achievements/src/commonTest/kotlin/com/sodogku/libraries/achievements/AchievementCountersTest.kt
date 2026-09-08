package com.sodogku.libraries.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fold is the single definition of "how a finished attempt moves the
 * numbers", so it is pinned here by counter *shape* — accumulator, high-water
 * mark, streak, first-time-only — plus the order-dependence that makes
 * re-deriving a player's whole history from the stored log correct.
 */
class AchievementCountersTest {

    @Test
    fun accumulators_countOnlyQualifyingClears() {
        val counters = foldAll(
            result(levelId = 1, strikes = 0),
            result(levelId = 2, strikes = 2),
            result(levelId = 3, sniffsUsed = 0, treatsUsed = 0),
        )

        assertEquals(3, counters[Stat.LevelsCleared])
        assertEquals(1, counters[Stat.FlawlessClears])
        assertEquals(1, counters[Stat.ComebackClears])
        assertEquals(1, counters[Stat.BoosterFreeClears], "the flawless clear still spent a sniff")
    }

    @Test
    fun neutralResult_movesNothingButTheLevelCount() {
        val counters = foldAll(result())

        val moved = Stat.entries.filter { counters[it] != 0L }
        assertEquals(
            listOf(Stat.LevelsCleared, Stat.LargestGridCleared),
            moved,
            "the fixture clears one 4x4 and must not do anything else by accident",
        )
    }

    @Test
    fun levelsCleared_countsLevels_notClears() {
        val counters = foldAll(
            result(levelId = 7, isFirstClear = true),
            result(levelId = 7, isFirstClear = false),
            result(levelId = 8, isFirstClear = true),
        )

        assertEquals(2, counters[Stat.LevelsCleared], "replaying level 7 is not progress through the campaign")
    }

    @Test
    fun threePawClears_countOnce_perLevel() {
        val counters = foldAll(
            result(levelId = 1, paws = 3, previousBestPaws = 0),
            result(levelId = 1, paws = 3, previousBestPaws = 3),
            result(levelId = 2, paws = 2, previousBestPaws = 0),
            result(levelId = 2, paws = 3, previousBestPaws = 2),
        )

        assertEquals(2, counters[Stat.ThreePawClears], "level 2 counts when it improves to three")
    }

    @Test
    fun flawlessStreak_resetsOnAStrike_andTracksTheBest() {
        val counters = foldAll(
            result(strikes = 0), result(strikes = 0), result(strikes = 0),
            result(strikes = 1),
            result(strikes = 0), result(strikes = 0),
        )

        assertEquals(2, counters[Stat.FlawlessStreak], "the run since the strike")
        assertEquals(3, counters[Stat.BestFlawlessStreak], "the high-water before it")
    }

    @Test
    fun flawlessStreak_isBrokenByAFailedAttempt() {
        val counters = foldAll(
            result(strikes = 0), result(strikes = 0),
            result(completed = false, strikes = 3),
            result(strikes = 0),
        )

        assertEquals(1, counters[Stat.FlawlessStreak], "running out of bones is not a flawless run")
        assertEquals(2, counters[Stat.BestFlawlessStreak])
    }

    @Test
    fun failedAttempt_earnsNothing() {
        val counters = foldAll(result(completed = false, paws = 3, score = 90_000, size = 10, strikes = 3))

        assertEquals(0, counters[Stat.LevelsCleared])
        assertEquals(0, counters[Stat.ThreePawClears])
        assertEquals(0, counters[Stat.BestScore])
        assertEquals(0, counters[Stat.LargestGridCleared], "a board you did not finish is not a board you cleared")
    }

    @Test
    fun highWaterMarks_keepTheBest_notTheLast() {
        val counters = foldAll(
            result(size = 9, score = 18_000, bestCombo = 9),
            result(size = 4, score = 900, bestCombo = 4),
        )

        assertEquals(18_000, counters[Stat.BestScore])
        assertEquals(9, counters[Stat.BestCombo])
        assertEquals(9, counters[Stat.LargestGridCleared], "playing an easy level does not shrink the best")
    }

    @Test
    fun sprintClears_needARealClock() {
        val counters = foldAll(
            result(timeMs = 29_999),
            result(timeMs = 30_001),
            result(timeMs = 0),
        )

        assertEquals(1, counters[Stat.SprintClears], "0ms means the attempt had no clock, not that it was instant")
    }

    @Test
    fun bigBoardSprint_needsBothTheSizeAndTheClock() {
        val counters = foldAll(
            result(size = 7, timeMs = 45_000),
            result(size = 6, timeMs = 45_000),
            result(size = 10, timeMs = 61_000),
        )

        assertEquals(1, counters[Stat.BigBoardSprintClears])
        assertEquals(0, counters[Stat.SprintClears], "a minute is fast for a 7x7 and slow for the sprint")
    }

    @Test
    fun timeOfDayWindows_doNotOverlap_andDoNotSpillOver() {
        val hours = (0..23).map { hour -> hour to foldAll(result(localHour = hour)) }

        val night = hours.filter { (_, counters) -> counters[Stat.NightClears] == 1L }.map { it.first }
        val dawn = hours.filter { (_, counters) -> counters[Stat.DawnClears] == 1L }.map { it.first }

        assertEquals(listOf(1, 2, 3, 4), night)
        assertEquals(listOf(5, 6, 7), dawn)
    }

    @Test
    fun dailyStreak_isWatermarked_fromWhatTheDailyFeatureReports() {
        val counters = foldAll(
            result(mode = PlayMode.Daily, dailyStreakDays = 12),
            result(mode = PlayMode.Daily, dailyStreakDays = 1),
        )

        assertEquals(2, counters[Stat.DailiesCleared])
        assertEquals(12, counters[Stat.BestDailyStreak], "breaking a streak does not lower the best one")
    }

    @Test
    fun dailyClears_doNotCountAsCampaignLevels() {
        val counters = foldAll(result(mode = PlayMode.Daily))

        assertEquals(0, counters[Stat.LevelsCleared])
        assertEquals(1, counters[Stat.DailiesCleared])
    }

    @Test
    fun fold_returnsANewSnapshot_andNeverMutatesTheReceiver() {
        val base = AchievementCounters.Empty.fold(result())
        val before = Stat.entries.associateWith { base[it] }

        base.fold(result(levelId = 2))

        assertEquals(before, Stat.entries.associateWith { base[it] })
    }

    @Test
    fun everyStatIsReachable() {
        // A counter no attempt can move is a counter no achievement can be
        // earned from, and it would look exactly like a working one.
        Stat.entries.forEach { stat ->
            val counters = historyFor(stat, value = 2).fold(AchievementCounters.Empty) { acc, r -> acc.fold(r) }
            assertTrue(counters[stat] > 0, "$stat is never incremented by any history")
        }
    }
}
