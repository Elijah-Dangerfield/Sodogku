package com.sodogku.libraries.progress.impl

import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.db.LevelProgressDao
import com.sodogku.libraries.progress.db.LevelProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Pins the rules a level record must never break: a metric only improves, a
 * level only moves forward, attempts count starts rather than clears, and
 * clearing a level opens the next one.
 *
 * Runs against an in-memory [LevelProgressDao] rather than a real database.
 * Room's own SQL is not exercised here — a KMP Room database needs a native
 * driver that the host JVM test source set doesn't have, and the queries in
 * this table are single-row reads with no logic in them to get wrong.
 */
@OptIn(ExperimentalTime::class)
class ProgressRepositoryImplTest : CoroutineTest() {

    @Test
    fun untouchedLevel_isLocked_exceptTheFirstOne() = runUnitTest {
        val repo = repository()

        assertEquals(LevelState.Unlocked, repo.record(1).state, "level 1 opens with no row")
        assertEquals(LevelState.Locked, repo.record(2).state)
        assertEquals(0, repo.attemptsOf(2))
    }

    @Test
    fun onCompleted_keepsBestOfEachMetric_whenTheReplayIsWorse() = runUnitTest {
        val repo = repository()

        repo.onCompleted(levelId = 1, score = 900, paws = 3, timeMs = 40_000)
        repo.onCompleted(levelId = 1, score = 120, paws = 1, timeMs = 95_000)

        val record = repo.record(1)
        assertEquals(900, record.bestScore)
        assertEquals(3, record.bestPaws)
        assertEquals(40_000, record.bestTimeMs)
    }

    @Test
    fun onCompleted_takesTheBetterMetrics_whenTheReplayIsBetter() = runUnitTest {
        val repo = repository()

        repo.onCompleted(levelId = 1, score = 120, paws = 1, timeMs = 95_000)
        repo.onCompleted(levelId = 1, score = 900, paws = 3, timeMs = 40_000)

        val record = repo.record(1)
        assertEquals(900, record.bestScore)
        assertEquals(3, record.bestPaws)
        assertEquals(40_000, record.bestTimeMs, "a faster run lowers the best time")
    }

    @Test
    fun onCompleted_firstClearSetsBestTime_evenThoughZeroMeansNever() = runUnitTest {
        val repo = repository()

        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 90_000)

        assertEquals(
            90_000,
            repo.record(1).bestTimeMs,
            "an empty best time must lose to any real one, not win as the minimum",
        )
    }

    @Test
    fun onCompleted_withNoDuration_leavesTheExistingBestTimeAlone() = runUnitTest {
        val repo = repository()

        repo.onCompleted(levelId = 1, score = 100, paws = 2, timeMs = 50_000)
        repo.onCompleted(levelId = 1, score = 100, paws = 2, timeMs = 0)

        assertEquals(50_000, repo.record(1).bestTimeMs)
    }

    @Test
    fun onCompleted_stampsFirstCompletedAtOnce_andKeepsIt() = runUnitTest {
        val clock = MutableClock(atMs = 1_000L)
        val repo = repository(clock = clock)

        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 10_000)
        clock.advanceMs(5_000L)
        repo.onCompleted(levelId = 1, score = 200, paws = 2, timeMs = 9_000)

        val record = repo.record(1)
        assertEquals(1_000L, record.firstCompletedAt)
        assertEquals(6_000L, record.lastPlayedAt)
    }

    @Test
    fun onCompleted_unlocksTheNextLevel() = runUnitTest {
        val repo = repository()

        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 10_000)

        assertEquals(LevelState.Unlocked, repo.record(2).state)
        assertEquals(LevelState.Locked, repo.record(3).state, "only the next one opens")
        assertEquals(2, repo.unlockedThrough())
    }

    @Test
    fun onSkipped_marksSkipped_andStillUnlocksTheNextLevel() = runUnitTest {
        val repo = repository()

        repo.onSkipped(levelId = 4)

        assertEquals(LevelState.Skipped, repo.record(4).state)
        assertEquals(LevelState.Unlocked, repo.record(5).state)
    }

    @Test
    fun attempts_countStarts_notCompletions() = runUnitTest {
        val repo = repository()

        repo.onAttemptStarted(levelId = 1)
        repo.onAttemptStarted(levelId = 1)
        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 10_000)

        assertEquals(2, repo.attemptsOf(1), "the clear is not a third attempt")
    }

    @Test
    fun completedLevel_neverRegressesToUnlocked() = runUnitTest {
        val repo = repository()

        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 10_000)
        repo.onAttemptStarted(levelId = 1)

        assertEquals(LevelState.Completed, repo.record(1).state, "a replay is not a downgrade")

        repo.onSkipped(levelId = 1)
        assertEquals(LevelState.Completed, repo.record(1).state, "skipping a cleared level is a no-op")
    }

    @Test
    fun completingASkippedLevel_promotesItToCompleted() = runUnitTest {
        val repo = repository()

        repo.onSkipped(levelId = 2)
        repo.onCompleted(levelId = 2, score = 300, paws = 2, timeMs = 30_000)

        assertEquals(LevelState.Completed, repo.record(2).state)
    }

    @Test
    fun unlockedThrough_isTheFirstLevel_untilSomethingIsCleared() = runUnitTest {
        val repo = repository()

        assertEquals(LevelRecord.FIRST_LEVEL_ID, repo.unlockedThrough())

        repo.onAttemptStarted(levelId = 1)
        assertEquals(1, repo.unlockedThrough(), "starting a level does not open the next")

        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 10_000)
        repo.onCompleted(levelId = 2, score = 100, paws = 1, timeMs = 10_000)
        assertEquals(3, repo.unlockedThrough())
    }

    @Test
    fun observe_startsWithTheUnplayedRecord_thenTracksWrites() = runUnitTest {
        val repo = repository()

        assertEquals(LevelState.Locked, repo.observe(2).first().state)

        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 10_000)
        assertEquals(LevelState.Unlocked, repo.observe(2).first().state)
    }

    @Test
    fun all_returnsOnlyTouchedLevels() = runUnitTest {
        val repo = repository()

        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 10_000)

        assertEquals(listOf(1, 2), repo.all().map { it.levelId }, "the level it unlocked has a row too")
    }

    @Test
    fun reset_wipesEveryRecord() = runUnitTest {
        val repo = repository()

        repo.onCompleted(levelId = 1, score = 100, paws = 1, timeMs = 10_000)
        repo.reset()

        assertEquals(emptyList(), repo.all())
        assertEquals(LevelRecord.unplayed(1), repo.record(1))
    }

    private fun repository(clock: Clock = MutableClock(atMs = 1_000L)) =
        ProgressRepositoryImpl(FakeLevelProgressDao(), clock)

    private suspend fun ProgressRepositoryImpl.attemptsOf(levelId: Int): Int = record(levelId).attempts
}

@OptIn(ExperimentalTime::class)
private class MutableClock(atMs: Long) : Clock {
    private var current: Instant = Instant.fromEpochMilliseconds(atMs)
    fun advanceMs(by: Long) { current += by.milliseconds }
    override fun now(): Instant = current
}

private class FakeLevelProgressDao : LevelProgressDao {

    private val rows = MutableStateFlow<Map<Int, LevelProgressEntity>>(emptyMap())

    override suspend fun upsert(row: LevelProgressEntity) {
        rows.value = rows.value + (row.levelId to row)
    }

    override fun observe(levelId: Int): Flow<LevelProgressEntity?> = rows.map { it[levelId] }

    override suspend fun find(levelId: Int): LevelProgressEntity? = rows.value[levelId]

    override suspend fun all(): List<LevelProgressEntity> = rows.value.values.sortedBy { it.levelId }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}
