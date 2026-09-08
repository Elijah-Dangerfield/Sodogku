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
    fun neutralResult_movesNothingButTheLevelCountAndTheClock() {
        val counters = foldAll(result())

        val moved = Stat.entries.filter { counters[it] != 0L }
        assertEquals(
            listOf(Stat.LevelsCleared, Stat.LargestGridCleared, Stat.MinutesPlayed),
            moved,
            "the fixture clears one 4x4 in a minute and must not do anything else by accident",
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
    fun aRematchCountsOnlyAfterTheLevelHasBeatenYou() {
        val straightThrough = foldAll(result(levelId = 1))
        assertEquals(0, straightThrough[Stat.RedemptionClears], "winning first time is not a rematch")

        val counters = foldAll(
            result(levelId = 1, completed = false, strikes = 3),
            result(levelId = 1),
            result(levelId = 1),
        )

        assertEquals(1, counters[Stat.RedemptionClears], "and replaying the level again is not a second one")
    }

    @Test
    fun aRematchIsNotConfusedAcrossThePacks() {
        // Campaign 7 and daily 7 are different boards on a shared number line,
        // so a loss on one must not arm a rematch on the other.
        val counters = foldAll(
            result(levelId = 7, mode = PlayMode.Campaign, completed = false, strikes = 3),
            result(levelId = 7, mode = PlayMode.Daily),
        )

        assertEquals(0, counters[Stat.RedemptionClears])
    }

    @Test
    fun minutesPlayed_addUpAcrossEveryAttempt_andSurviveTruncation() {
        // Thirty half-minute attempts is fifteen minutes. Rounding each one to
        // whole minutes as it lands would report zero.
        val counters = (1..30).fold(AchievementCounters.Empty) { acc, index ->
            acc.fold(result(levelId = index, timeMs = 30_000))
        }

        assertEquals(15, counters[Stat.MinutesPlayed])
    }

    @Test
    fun minutesPlayed_countALostAttempt_andIgnoreAMissingClock() {
        val counters = foldAll(
            result(completed = false, strikes = 3, timeMs = 120_000),
            result(timeMs = 0),
        )

        assertEquals(2, counters[Stat.MinutesPlayed], "time spent losing is still time spent")
    }

    @Test
    fun theOtherTwoStreaksBreakOnTheSameThingsTheFlawlessOneDoes() {
        val counters = foldAll(
            result(paws = 3, sniffsUsed = 0),
            result(paws = 3, sniffsUsed = 0),
            result(completed = false, paws = 0, strikes = 3, sniffsUsed = 0),
            result(paws = 3, sniffsUsed = 0),
        )

        assertEquals(1, counters[Stat.ThreePawStreak])
        assertEquals(2, counters[Stat.BestThreePawStreak])
        assertEquals(1, counters[Stat.BoosterFreeStreak])
        assertEquals(2, counters[Stat.BestBoosterFreeStreak])
    }

    @Test
    fun theBigBoardCountersNeedTheBoardToBeBig() {
        val counters = foldAll(
            result(size = 6, strikes = 0, timeMs = 45_000),
            result(size = 7, strikes = 0, timeMs = 45_000),
            result(size = 10, strikes = 2, timeMs = 200_000),
        )

        assertEquals(2, counters[Stat.BigBoardClears], "a 6x6 is not a big board")
        assertEquals(1, counters[Stat.MaxBoardClears])
        assertEquals(1, counters[Stat.FlawlessBigBoardClears])
        assertEquals(1, counters[Stat.BigBoardComebackClears])
        assertEquals(1, counters[Stat.MaxBoardSprintClears])
        assertEquals(1, counters[Stat.BigBoardSprintClears], "200s is not a minute")
    }

    @Test
    fun theDailyCountersIgnoreTheCampaignAndViceVersa() {
        val counters = foldAll(
            result(mode = PlayMode.Campaign, strikes = 0, paws = 3),
            result(mode = PlayMode.Daily, strikes = 0, paws = 3),
        )

        assertEquals(1, counters[Stat.DailyFlawlessClears])
        assertEquals(1, counters[Stat.DailyThreePawClears])
        assertEquals(2, counters[Stat.FlawlessClears], "the daily is still a clean clear")
    }

    @Test
    fun assistedClears_needBothKindsOfHelp() {
        val counters = foldAll(
            result(sniffsUsed = 2, treatsUsed = 0),
            result(sniffsUsed = 0, treatsUsed = 2),
            result(sniffsUsed = 1, treatsUsed = 1),
        )

        assertEquals(1, counters[Stat.AssistedClears])
    }

    @Test
    fun aFlashClearIsAlsoASprint_butNotTheOtherWayRound() {
        val counters = foldAll(result(timeMs = 9_000), result(timeMs = 20_000))

        assertEquals(1, counters[Stat.FlashClears])
        assertEquals(2, counters[Stat.SprintClears])
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
