package com.sodogku.libraries.achievements

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The unlock rules: what a history earns, when it earns it, and the two things
 * that must never happen — a badge granted twice, and a badge granted early.
 */
class AchievementEngineTest {

    @Test
    fun everyAchievementInTheCatalogCanBeEarned() {
        Achievements.catalog.forEach { achievement ->
            val state = AchievementEngine.replay(historyFor(achievement.stat, achievement.target.toInt()))

            assertTrue(
                state.isUnlocked(achievement.id),
                "${achievement.id} is in the catalog but no history earns it",
            )
        }
    }

    @Test
    fun noAchievementIsEarnedOneShortOfItsTarget() {
        // The companion to the test above, and the one that can actually fail:
        // "everything is earnable" passes just as happily against an engine that
        // unlocks the whole catalog on the first attempt.
        Achievements.catalog.forEach { achievement ->
            val state = AchievementEngine.replay(historyFor(achievement.stat, achievement.target.toInt() - 1))

            assertFalse(
                state.isUnlocked(achievement.id),
                "${achievement.id} unlocked at ${achievement.target - 1} of ${achievement.target}",
            )
        }
    }

    @Test
    fun crossingTheThreshold_announcesTheAchievementExactlyOnce() {
        val first = AchievementEngine.apply(AchievementState.Empty, result(levelId = 1))

        assertEquals(
            listOf(AchievementId.FirstSteps),
            first.newlyUnlocked.map { it.id },
            "clearing the first level earns FirstSteps",
        )

        val second = AchievementEngine.apply(first.state, result(levelId = 2))

        assertEquals(emptyList(), second.newlyUnlocked, "a second clear does not re-earn it")
        assertTrue(second.state.isUnlocked(AchievementId.FirstSteps), "and does not take it away either")
    }

    @Test
    fun applyingTheSameResultTwice_grantsNothingTheSecondTime() {
        val attempt = result(levelId = 1, strikes = 0)
        val once = AchievementEngine.apply(AchievementState.Empty, attempt)
        val twice = AchievementEngine.apply(once.state, attempt)

        assertTrue(once.newlyUnlocked.isNotEmpty(), "the first application has to earn something")
        assertEquals(emptyList(), twice.newlyUnlocked)
    }

    @Test
    fun unlockedAt_comesFromTheAttempt_notFromAClock() {
        val state = AchievementEngine.apply(
            AchievementState.Empty,
            result(finishedAt = 1_700_000_000_000),
        ).state

        assertEquals(1_700_000_000_000, state.unlocked[AchievementId.FirstSteps])
    }

    @Test
    fun anAchievementAddedLater_backFillsFromHistoryWithItsRealDate() {
        // The reason the fact log is stored instead of the counters: a catalog
        // that grows must not start everyone at zero.
        val shipped = listOf(Achievements[AchievementId.FirstSteps])
        val history = (1..12).map { result(levelId = it, finishedAt = it.toLong()) }

        val before = AchievementEngine.replay(history, catalog = shipped)
        assertEquals(setOf(AchievementId.FirstSteps), before.unlocked.keys)

        val withNewOne = shipped + Achievements[AchievementId.GoodDog]
        val after = AchievementEngine.replay(history, catalog = withNewOne)

        assertTrue(after.isUnlocked(AchievementId.GoodDog), "the tenth clear already earned it")
        assertEquals(10L, after.unlocked[AchievementId.GoodDog], "dated to the attempt that earned it")
    }

    @Test
    fun replayingAHistory_matchesFoldingItOneAttemptAtATime() {
        val history = randomHistory(Random(20260907), count = 300)

        val incremental = history.fold(AchievementState.Empty) { state, attempt ->
            AchievementEngine.apply(state, attempt).state
        }

        assertEquals(AchievementEngine.replay(history), incremental)
    }

    @Test
    fun overManyHistories_unlocksOnlyGrow_andAlwaysMatchTheCounters() {
        var everUnlocked = 0

        repeat(REPEATS) { seed ->
            val history = randomHistory(Random(seed), count = 120)
            var state = AchievementState.Empty
            var previous = state

            history.forEach { attempt ->
                val update = AchievementEngine.apply(state, attempt)
                state = update.state

                update.newlyUnlocked.forEach {
                    assertFalse(previous.isUnlocked(it.id), "${it.id} was announced twice")
                }
                assertTrue(
                    state.unlocked.keys.containsAll(previous.unlocked.keys),
                    "an unlock was taken away",
                )
                assertEquals(
                    Achievements.catalog.filter { it.isMet(state.counters) }.map { it.id }.toSet(),
                    state.unlocked.keys,
                    "the unlocked set has to be exactly what the counters justify",
                )
                previous = state
            }
            everUnlocked += state.unlocked.size
        }

        // Without this the whole property above is satisfied by an engine that
        // never unlocks anything: "no double grant" and "nothing unjustified"
        // are both vacuously true of an empty set.
        assertTrue(everUnlocked > REPEATS, "random play should be earning badges, earned $everUnlocked")
    }

    @Test
    fun everyCatalogStatOnlyEverGrows() {
        // The unlocked-matches-counters invariant above holds only because no
        // achievement is hung off a *current* streak. Cataloguing
        // `FlawlessStreak` instead of `BestFlawlessStreak` would unlock a badge
        // and then make the counters disagree with it on the next attempt.
        val stats = Achievements.catalog.map { it.stat }.toSet()
        val history = randomHistory(Random(4242), count = 200)

        var counters = AchievementCounters.Empty
        history.forEach { attempt ->
            val next = counters.fold(attempt)
            stats.forEach { stat ->
                assertTrue(next[stat] >= counters[stat], "$stat went backwards")
            }
            counters = next
        }
        assertTrue(stats.any { counters[it] > 0 }, "the generated history moved nothing at all")
    }

    @Test
    fun progress_isTheFractionOfTheTarget_andStopsAtOne() {
        val achievement = Achievements[AchievementId.GoodDog]
        val fiveClears = AchievementEngine.replay(historyFor(Stat.LevelsCleared, 5)).counters
        val twentyClears = AchievementEngine.replay(historyFor(Stat.LevelsCleared, 20)).counters

        assertEquals(0f, achievement.progress(AchievementCounters.Empty))
        assertEquals(0.5f, achievement.progress(fiveClears))
        assertEquals(1f, achievement.progress(twentyClears))
    }

    @Test
    fun theCatalogListsEveryIdExactlyOnce() {
        assertEquals(
            AchievementId.entries.toSet(),
            Achievements.catalog.map { it.id }.toSet(),
            "an id with no definition can never be earned, and a definition with no id cannot exist",
        )
        assertEquals(AchievementId.entries.size, Achievements.catalog.size, "duplicate definition")
    }

    private fun randomHistory(random: Random, count: Int): List<LevelResult> = (1..count).map { index ->
        result(
            levelId = random.nextInt(1, 500),
            mode = if (random.nextInt(DAILY_IN_N) == 0) PlayMode.Daily else PlayMode.Campaign,
            size = random.nextInt(4, 11),
            completed = random.nextInt(4) != 0,
            score = random.nextInt(0, 35_000),
            paws = random.nextInt(0, 4),
            timeMs = random.nextLong(0, 300_000),
            strikes = random.nextInt(0, 4),
            bestCombo = random.nextInt(0, 11),
            sniffsUsed = random.nextInt(0, 3),
            treatsUsed = random.nextInt(0, 3),
            isFirstClear = random.nextBoolean(),
            previousBestPaws = random.nextInt(0, 4),
            dailyStreakDays = random.nextInt(0, 40),
            localHour = random.nextInt(0, 24),
            finishedAt = index.toLong(),
        )
    }

    private companion object {
        const val REPEATS = 40
        const val DAILY_IN_N = 8
    }
}
