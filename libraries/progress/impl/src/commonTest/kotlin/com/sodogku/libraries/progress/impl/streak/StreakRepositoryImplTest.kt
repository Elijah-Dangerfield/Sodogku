package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.DailyEnabled
import com.sodogku.libraries.config.values.FeatureDailyChallenge
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.db.DailyResultDao
import com.sodogku.libraries.progress.db.DailyResultEntity
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The wiring the two pure folds cannot see: that today comes from the player's
 * own zone, that the summary reads rows rather than a stored number, and that a
 * prompt stays answered once it has been shown.
 *
 * Every date assertion here is made in at least two zones, because the whole
 * class of bug this seam exists to prevent is a calendar that is right in UTC
 * and a day out for half the world.
 */
@OptIn(ExperimentalTime::class)
class StreakRepositoryImplTest : CoroutineTest() {

    private val dao = FakeDailyResultDao()
    private val prompts = FakeStreakPromptCache()
    private val progress = FakeProgressRepository()
    private val clock = MutableClock(Instant.parse("2026-09-07T23:30:00Z"))
    private var zone: TimeZone = TimeZone.UTC

    @Test
    fun theCalendarIsBuiltFromThePlayersLocalDate() = runUnitTest {
        // 23:30 UTC on Monday the 7th is already Tuesday the 8th in Berlin.
        dao.put(LocalDate(2026, 9, 8), DailyOutcome.Completed)

        zone = TimeZone.UTC
        val inUtc = repository().summary()

        zone = TimeZone.of("Europe/Berlin")
        val inBerlin = repository().summary()

        assertEquals(LocalDate(2026, 9, 7), inUtc.today)
        assertEquals(LocalDate(2026, 9, 8), inBerlin.today, "an hour ahead is already on tomorrow")
        assertEquals(
            StreakDayState.Future,
            inUtc.dayOn(LocalDate(2026, 9, 8)),
            "the row exists, but at UTC that day has not happened yet",
        )
        assertEquals(
            StreakDayState.Completed,
            inBerlin.dayOn(LocalDate(2026, 9, 8)),
            "and in Berlin it is today, and it is done",
        )
    }

    @Test
    fun theWindowMovesWithTheZoneRatherThanWithUtc() = runUnitTest {
        zone = TimeZone.of("Pacific/Auckland")
        clock.set(Instant.parse("2026-09-13T20:00:00Z"))
        val auckland = repository().summary()

        zone = TimeZone.of("America/Los_Angeles")
        val losAngeles = repository().summary()

        assertEquals(LocalDate(2026, 9, 14), auckland.today, "already Monday, so a new week")
        assertEquals(LocalDate(2026, 9, 13), losAngeles.today, "still Sunday, so the old one")
        assertEquals(LocalDate(2026, 8, 17), auckland.days.first().date)
        assertEquals(
            LocalDate(2026, 8, 10),
            losAngeles.days.first().date,
            "a week apart: the grid is anchored to the local week, not to a UTC one",
        )
    }

    @Test
    fun theRecordSurvivesTheRunThatSetIt() = runUnitTest {
        seed(completedDaysBack = (0..1).toList() + (5..11).toList())

        listOf(TimeZone.UTC, TimeZone.of("Pacific/Kiritimati")).forEach { candidate ->
            zone = candidate
            clock.set(Instant.parse("2026-09-07T12:00:00Z"))
            val summary = repository().summary()

            assertEquals(2, summary.current, "in $candidate")
            assertEquals(7, summary.longest, "the seven-day run is gone but the record is not")
        }
    }

    @Test
    fun thereIsNoStoredStreak_soAWipedTableWipesBoth() = runUnitTest {
        seed(completedDaysBack = (0..4).toList())
        val before = repository().summary()
        assertEquals(5, before.current)
        assertEquals(5, before.longest)

        dao.clear()

        val after = repository().summary()
        assertEquals(0, after.current)
        assertEquals(0, after.longest, "the record is folded from the same rows, not banked beside them")
    }

    @Test
    fun theLatestCompletedDayIsWhatACelebrationAnimates() = runUnitTest {
        seed(completedDaysBack = (0..2).toList())

        val summary = repository().summary()

        assertEquals(LocalDate(2026, 9, 7), summary.latestCompleted?.date)
        assertTrue(summary.latestCompleted?.isToday == true)
    }

    @Test
    fun theIntentionIsDueOnTheThirdClearAndOnlyOnce() = runUnitTest {
        progress.completedLevels = 3
        val repo = repository()

        assertEquals(StreakPrompt.Intention, repo.pendingPrompt())

        repo.onPromptShown(StreakPrompt.Intention)

        assertEquals(
            StreakPrompt.None,
            repo.pendingPrompt(),
            "recorded when it is shown, so a force-quit does not earn a second one",
        )
    }

    @Test
    fun skippedLevelsAreNotClears() = runUnitTest {
        progress.completedLevels = 2
        progress.skippedLevels = 5

        assertEquals(
            StreakPrompt.None,
            repository().pendingPrompt(),
            "skipping past three boards is not choosing to keep playing them",
        )
    }

    @Test
    fun aMilestoneIsOfferedOnceAndRecordedAsShown() = runUnitTest {
        seed(completedDaysBack = (0..6).toList())
        prompts.value = StreakPromptState(intentionShown = true)
        val repo = repository()

        val offered = repo.pendingPrompt()
        assertEquals(StreakPrompt.Celebrate(7), offered)

        repo.onPromptShown(offered)

        assertEquals(StreakPrompt.None, repo.pendingPrompt())
        assertEquals(7, prompts.value.celebratedStreak)
    }

    @Test
    fun playingTheDailyRetiresTheIntentionWithoutShowingIt() = runUnitTest {
        progress.completedLevels = 10
        seed(completedDaysBack = listOf(0))

        assertEquals(StreakPrompt.None, repository().pendingPrompt())
        assertTrue(!prompts.value.intentionShown, "nothing was shown, so nothing was recorded")
    }

    @Test
    fun aLostDailyCountsAsHavingStarted() = runUnitTest {
        progress.completedLevels = 10
        dao.put(LocalDate(2026, 9, 6), DailyOutcome.Failed)

        assertEquals(
            StreakPrompt.None,
            repository().pendingPrompt(),
            "they turned up and ran out of bones; telling them to start would deny the attempt",
        )
    }

    @Test
    fun aBridgedDayIsNotHavingPlayed() = runUnitTest {
        progress.completedLevels = 10
        dao.put(LocalDate(2026, 9, 6), DailyOutcome.Frozen)

        assertEquals(
            StreakPrompt.Intention,
            repository().pendingPrompt(),
            "a frozen day is a day nobody played, whoever paid for it",
        )
    }

    @Test
    fun killSwitchAndFeatureFlagBothCloseTheStreak() = runUnitTest {
        progress.completedLevels = 3

        assertTrue(repository().summary().enabled)
        assertTrue(!repository(dailyEnabled = false).summary().enabled)
        assertTrue(!repository(featureEnabled = false).summary().enabled)
        assertEquals(StreakPrompt.None, repository(dailyEnabled = false).pendingPrompt())
        assertEquals(StreakPrompt.None, repository(featureEnabled = false).pendingPrompt())
    }

    private fun StreakSummary.dayOn(date: LocalDate) =
        days.first { it.date == date }.state

    private fun seed(completedDaysBack: List<Int>) {
        val today = LocalDate(2026, 9, 7)
        completedDaysBack.forEach { back ->
            dao.put(today.minusDays(back), DailyOutcome.Completed)
        }
    }

    private fun LocalDate.minusDays(days: Int): LocalDate {
        var date = this
        repeat(days) { date = date.minusOneDay() }
        return date
    }

    private fun LocalDate.minusOneDay(): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() - 1)

    private fun repository(
        dailyEnabled: Boolean = true,
        featureEnabled: Boolean = true,
    ) = StreakRepositoryImpl(
        dao = dao,
        progress = progress,
        prompts = prompts,
        clock = clock,
        timeZone = DeviceTimeZone { zone },
        dailyEnabled = DailyEnabled(
            object : AppConfigMap() {
                override val map = mapOf("daily" to mapOf("enabled" to dailyEnabled))
            },
        ),
        featureEnabled = FeatureDailyChallenge(
            object : AppConfigMap() {
                override val map = mapOf("features" to mapOf("dailyChallenge" to featureEnabled))
            },
        ),
    )
}

@OptIn(ExperimentalTime::class)
private class MutableClock(private var current: Instant) : Clock {
    override fun now(): Instant = current

    fun set(instant: Instant) {
        current = instant
    }
}

private class FakeDailyResultDao : DailyResultDao {

    private val rows = MutableStateFlow<Map<String, DailyResultEntity>>(emptyMap())

    override suspend fun insertIfAbsent(row: DailyResultEntity): Long {
        if (rows.value.containsKey(row.date)) return -1L
        rows.value = rows.value + (row.date to row)
        return 1L
    }

    override fun observeAll(): Flow<List<DailyResultEntity>> = rows.map { it.values.sortedBy { r -> r.date } }

    override suspend fun all(): List<DailyResultEntity> = rows.value.values.sortedBy { it.date }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }

    fun put(date: LocalDate, outcome: DailyOutcome) {
        val row = DailyResultEntity(
            date = date.toString(),
            levelIndex = 0,
            outcome = outcome.name,
            score = 0,
            paws = 0,
            timeMs = 0,
        )
        rows.value = rows.value + (row.date to row)
    }

    fun clear() {
        rows.value = emptyMap()
    }
}

private class FakeStreakPromptCache : StreakPromptCache {

    var value = StreakPromptState()

    override val updates: Flow<StreakPromptState> = MutableStateFlow(value)

    override suspend fun get(): StreakPromptState = value

    override suspend fun set(value: StreakPromptState) {
        this.value = value
    }

    override suspend fun clear() {
        value = StreakPromptState()
    }

    override suspend fun update(transform: (StreakPromptState) -> StreakPromptState): StreakPromptState {
        value = transform(value)
        return value
    }
}

private class FakeProgressRepository : ProgressRepository {

    var completedLevels: Int = 0
    var skippedLevels: Int = 0

    override fun observe(levelId: Int): Flow<LevelRecord> = MutableStateFlow(LevelRecord.unplayed(levelId))

    override suspend fun record(levelId: Int): LevelRecord = LevelRecord.unplayed(levelId)

    override suspend fun all(): List<LevelRecord> =
        List(completedLevels) { LevelRecord.unplayed(it + 1).copy(state = LevelState.Completed) } +
            List(skippedLevels) { LevelRecord.unplayed(completedLevels + it + 1).copy(state = LevelState.Skipped) }

    override suspend fun unlockedThrough(): Int = completedLevels + skippedLevels + 1

    override suspend fun onAttemptStarted(levelId: Int) = Unit

    override suspend fun onCompleted(levelId: Int, score: Int, paws: Int, timeMs: Long) = Unit

    override suspend fun onSkipped(levelId: Int) = Unit

    override suspend fun reset() = Unit
}
