package com.sodogku.libraries.achievements.impl

import com.sodogku.libraries.achievements.AchievementId
import com.sodogku.libraries.achievements.LevelResult
import com.sodogku.libraries.achievements.PlayMode
import com.sodogku.libraries.achievements.Stat
import com.sodogku.libraries.achievements.db.AchievementDao
import com.sodogku.libraries.achievements.db.AchievementFactEntity
import com.sodogku.libraries.achievements.db.AchievementUnlockEntity
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The storage rules the pure fold cannot enforce on its own: an attempt counts
 * once however many times it is reported, a badge is announced once however
 * many times the app restarts, and progress is whatever the stored facts say
 * rather than whatever a cached number remembers.
 *
 * Runs against an in-memory [AchievementDao] for the same reason
 * `ProgressRepositoryImplTest` does — a KMP Room database needs a native driver
 * the host JVM test source set has not got, and the fake reproduces the one
 * piece of SQL behaviour this file depends on: the unique index on `key`.
 */
class AchievementsRepositoryImplTest : CoroutineTest() {

    @Test
    fun firstClear_earnsTheFirstBadge() = runUnitTest {
        val repository = AchievementsRepositoryImpl(FakeAchievementDao())

        val earned = repository.record(result(levelId = 1, finishedAt = 10))

        assertEquals(listOf(AchievementId.FirstSteps), earned.map { it.id })
        assertEquals(10L, repository.state().unlocked[AchievementId.FirstSteps])
    }

    @Test
    fun recordingTheSameAttemptTwice_countsItOnce() = runUnitTest {
        val repository = AchievementsRepositoryImpl(FakeAchievementDao())
        val attempt = result(levelId = 1, strikes = 0, finishedAt = 10)

        repeat(10) { repository.record(attempt) }

        val state = repository.state()
        assertEquals(1, state.counters[Stat.FlawlessClears], "one attempt, however often it is reported")
        assertFalse(
            state.isUnlocked(AchievementId.FlawlessTen),
            "ten reports of one flawless clear is not a ten-clear streak",
        )
    }

    @Test
    fun distinctAttempts_doAccumulate() = runUnitTest {
        // The companion to the test above: an implementation that dropped every
        // write would pass that one perfectly.
        val repository = AchievementsRepositoryImpl(FakeAchievementDao())

        repeat(10) { index ->
            repository.record(result(levelId = index + 1, strikes = 0, finishedAt = index + 1L))
        }

        val state = repository.state()
        assertEquals(10, state.counters[Stat.FlawlessClears])
        assertTrue(state.isUnlocked(AchievementId.FlawlessTen))
        assertTrue(state.isUnlocked(AchievementId.GoodDog))
    }

    @Test
    fun aBadgeIsAnnouncedOnce_evenAcrossARestart() = runUnitTest {
        val dao = FakeAchievementDao()
        val earnedIt = AchievementsRepositoryImpl(dao).record(result(levelId = 1, finishedAt = 1))

        val afterRestart = AchievementsRepositoryImpl(dao).record(result(levelId = 2, finishedAt = 2))

        assertEquals(listOf(AchievementId.FirstSteps), earnedIt.map { it.id })
        assertEquals(emptyList(), afterRestart, "the unlock table is what stops a second toast")
    }

    @Test
    fun anAchievementTheCatalogGainedLater_isCaughtUpFromStoredFacts() = runUnitTest {
        val dao = FakeAchievementDao()
        // Nine clears that a previous release recorded and never announced
        // anything for: facts on disk, no unlock rows.
        (1..9).forEach { dao.insertFact(entity(result(levelId = it, finishedAt = it.toLong()))) }

        val earned = AchievementsRepositoryImpl(dao).record(result(levelId = 10, finishedAt = 10))

        assertEquals(
            listOf(AchievementId.FirstSteps, AchievementId.GoodDog),
            earned.map { it.id },
            "the history is re-folded, so the badge nobody had granted lands too, in catalog order",
        )
        assertEquals(1L, dao.unlocks().first { it.achievementId == "FirstSteps" }.unlockedAt)
    }

    @Test
    fun observe_tracksWritesAsTheyLand() = runUnitTest {
        val repository = AchievementsRepositoryImpl(FakeAchievementDao())

        assertEquals(0, repository.observe().first().counters[Stat.LevelsCleared])

        repository.record(result(levelId = 1, finishedAt = 1))

        val state = repository.observe().first()
        assertEquals(1, state.counters[Stat.LevelsCleared])
        assertTrue(state.isUnlocked(AchievementId.FirstSteps))
    }

    @Test
    fun anUnknownUnlockRow_isIgnoredRatherThanFatal() = runUnitTest {
        val dao = FakeAchievementDao()
        dao.insertUnlocks(listOf(AchievementUnlockEntity("BadgeFromANewerBuild", unlockedAt = 5)))
        val repository = AchievementsRepositoryImpl(dao)

        val earned = repository.record(result(levelId = 1, finishedAt = 1))

        assertEquals(listOf(AchievementId.FirstSteps), earned.map { it.id })
        assertEquals(setOf(AchievementId.FirstSteps), repository.state().unlocked.keys)
    }

    @Test
    fun reset_wipesTheFactsAndTheUnlocks() = runUnitTest {
        val dao = FakeAchievementDao()
        val repository = AchievementsRepositoryImpl(dao)
        repository.record(result(levelId = 1, finishedAt = 1))

        repository.reset()

        val state = repository.state()
        assertEquals(0, state.counters[Stat.LevelsCleared])
        assertEquals(emptyMap(), state.unlocked)
        assertEquals(
            listOf(AchievementId.FirstSteps),
            repository.record(result(levelId = 1, finishedAt = 1)).map { it.id },
            "and a wiped player earns the opening badge again",
        )
    }

    @Test
    fun aFailedAttempt_isStored_andEarnsNothing() = runUnitTest {
        val dao = FakeAchievementDao()
        val repository = AchievementsRepositoryImpl(dao)

        val earned = repository.record(result(levelId = 1, completed = false, strikes = 3, finishedAt = 1))

        assertEquals(emptyList(), earned)
        assertEquals(1, dao.facts().size, "kept anyway — it breaks streaks and a later badge may ask about it")
    }

    private fun result(
        levelId: Int = 1,
        completed: Boolean = true,
        strikes: Int = 1,
        finishedAt: Long = 0,
    ) = LevelResult(
        levelId = levelId,
        mode = PlayMode.Campaign,
        size = 4,
        completed = completed,
        score = 0,
        paws = 1,
        timeMs = 60_000,
        strikes = strikes,
        bestCombo = 0,
        sniffsUsed = 1,
        treatsUsed = 0,
        isFirstClear = true,
        previousBestPaws = 0,
        localHour = 12,
        finishedAt = finishedAt,
    )

    private fun entity(result: LevelResult) = AchievementFactEntity(
        key = result.key,
        levelId = result.levelId,
        mode = result.mode.name,
        size = result.size,
        completed = result.completed,
        score = result.score,
        paws = result.paws,
        timeMs = result.timeMs,
        strikes = result.strikes,
        bestCombo = result.bestCombo,
        sniffsUsed = result.sniffsUsed,
        treatsUsed = result.treatsUsed,
        firstClear = result.isFirstClear,
        previousBestPaws = result.previousBestPaws,
        dailyStreakDays = result.dailyStreakDays,
        localHour = result.localHour,
        finishedAt = result.finishedAt,
    )
}

/**
 * In-memory stand-in. The only SQL behaviour it reproduces on purpose is the
 * unique index on `key` — an insert of a key already present is dropped, not
 * replaced — because that is the constraint the repository leans on to make
 * recording idempotent.
 */
private class FakeAchievementDao : AchievementDao {

    private val factRows = MutableStateFlow<List<AchievementFactEntity>>(emptyList())
    private val unlockRows = MutableStateFlow<List<AchievementUnlockEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insertFact(row: AchievementFactEntity) {
        if (factRows.value.any { it.key == row.key }) return
        factRows.value = factRows.value + row.copy(id = nextId++)
    }

    override suspend fun facts(): List<AchievementFactEntity> = factRows.value.sortedBy { it.id }

    override fun observeFacts(): Flow<List<AchievementFactEntity>> =
        factRows.map { rows -> rows.sortedBy { it.id } }

    override suspend fun insertUnlocks(rows: List<AchievementUnlockEntity>) {
        val known = unlockRows.value.mapTo(mutableSetOf()) { it.achievementId }
        unlockRows.value = unlockRows.value + rows.filter { it.achievementId !in known }
    }

    override suspend fun unlocks(): List<AchievementUnlockEntity> = unlockRows.value

    override fun observeUnlocks(): Flow<List<AchievementUnlockEntity>> = unlockRows

    override suspend fun deleteAllFacts() {
        factRows.value = emptyList()
    }

    override suspend fun deleteAllUnlocks() {
        unlockRows.value = emptyList()
    }
}
