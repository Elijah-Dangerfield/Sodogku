package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.db.PlayDayDao
import com.sodogku.libraries.progress.db.PlayDayEntity
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

    private val dao = FakePlayDayDao()
    private val prompts = FakeStreakPromptCache()
    private val progress = FakeProgressRepository()
    private val clock = MutableClock(Instant.parse("2026-09-07T23:30:00Z"))
    private var zone: TimeZone = TimeZone.UTC

    @Test
    fun theCalendarIsBuiltFromThePlayersLocalDate() = runUnitTest {
        // 23:30 UTC on Monday the 7th is already Tuesday the 8th in Berlin.
        dao.put(LocalDate(2026, 9, 8))

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
    fun theIntentionIsDueOnTheSecondBoardAndOnlyOnce() = runUnitTest {
        progress.completedLevels = 2
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
    fun theIntentionSpendsTheDaySoTheNextBoardIsQuiet() = runUnitTest {
        // The page prints the run, so it is that run's celebration. Without the
        // day going with it, the next board of the same day opens a second page
        // about the day the player was just congratulated for.
        seed(completedDaysBack = listOf(0))
        progress.completedLevels = 2
        val repo = repository()

        repo.onPromptShown(repo.pendingPrompt())

        assertEquals(LocalDate(2026, 9, 7), prompts.value.celebratedOn)
        assertEquals(StreakPrompt.None, repo.pendingPrompt())
    }

    @Test
    fun aRunThatStartedOverIsCelebratedRatherThanPassedOverAsBrandNew() = runUnitTest {
        // SD-121. Played Monday, missed Tuesday, played Wednesday: a run of one
        // again, and a player who had the intention moment long ago.
        seed(completedDaysBack = listOf(0, 2))
        progress.completedLevels = 16
        prompts.value = StreakPromptState(intentionShown = true)

        assertEquals(StreakPrompt.Celebrate(1), repository().pendingPrompt())
    }

    @Test
    fun aSecondRestartAtOneIsCelebratedToo() = runUnitTest {
        // The rule has to key on the day rather than on the run's length, or a
        // player whose runs are all one day long is congratulated once and then
        // never again: the number is 1 every time they come back.
        seed(completedDaysBack = listOf(0))
        progress.completedLevels = 16
        prompts.value = StreakPromptState(
            intentionShown = true,
            celebratedOn = LocalDate(2026, 9, 2),
        )

        assertEquals(StreakPrompt.Celebrate(1), repository().pendingPrompt())
    }

    @Test
    fun skippedLevelsAreNotClears() = runUnitTest {
        progress.completedLevels = 1
        progress.skippedLevels = 5

        assertEquals(
            StreakPrompt.None,
            repository().pendingPrompt(),
            "skipping past boards is not choosing to keep playing them",
        )
    }

    @Test
    fun aCelebrationIsOfferedOnceAndRecordedAsShown() = runUnitTest {
        seed(completedDaysBack = (0..6).toList())
        prompts.value = StreakPromptState(intentionShown = true)
        val repo = repository()

        val offered = repo.pendingPrompt()
        assertEquals(StreakPrompt.Celebrate(7), offered)

        repo.onPromptShown(offered)

        assertEquals(StreakPrompt.None, repo.pendingPrompt())
        assertEquals(LocalDate(2026, 9, 7), prompts.value.celebratedOn)
    }

    @Test
    fun finishingAnyBoardMarksTheDay() = runUnitTest {
        // The whole point of the decoupling: no daily involved anywhere.
        val repo = repository()
        assertEquals(0, repo.summary().current)

        repo.onBoardCompleted()

        val after = repo.summary()
        assertEquals(1, after.current)
        assertTrue(after.playedToday)
    }

    @Test
    fun asecondBoardOnTheSameDayChangesNothing() = runUnitTest {
        val repo = repository()
        repo.onBoardCompleted()
        repo.onBoardCompleted()
        repo.onBoardCompleted()

        assertEquals(1, repo.summary().current, "a day is a day however many boards it held")
    }

    @Test
    fun theCountdownIsToTheNextLocalMidnight() = runUnitTest {
        // 21:00 UTC is 23:00 in Berlin, which is two hours from UTC's midnight
        // and one from Berlin's.
        clock.set(Instant.parse("2026-09-07T21:00:00Z"))

        zone = TimeZone.UTC
        assertEquals(3, repository().summary().untilTomorrow.inWholeHours)

        zone = TimeZone.of("Europe/Berlin")
        assertEquals(
            1,
            repository().summary().untilTomorrow.inWholeHours,
            "the countdown is to the player's midnight, not to UTC's",
        )
    }

    private fun StreakSummary.dayOn(date: LocalDate) =
        days.first { it.date == date }.state

    private fun seed(completedDaysBack: List<Int>) {
        val today = LocalDate(2026, 9, 7)
        completedDaysBack.forEach { back ->
            dao.put(today.minusDays(back))
        }
    }

    private fun LocalDate.minusDays(days: Int): LocalDate {
        var date = this
        repeat(days) { date = date.minusOneDay() }
        return date
    }

    private fun LocalDate.minusOneDay(): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() - 1)

    private fun repository() = StreakRepositoryImpl(
        dao = dao,
        progress = progress,
        prompts = prompts,
        clock = clock,
        timeZone = DeviceTimeZone { zone },
    )
}

@OptIn(ExperimentalTime::class)
private class MutableClock(private var current: Instant) : Clock {
    override fun now(): Instant = current

    fun set(instant: Instant) {
        current = instant
    }
}

private class FakePlayDayDao : PlayDayDao {

    private val rows = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun insertIfAbsent(row: PlayDayEntity): Long {
        val existed = row.date in rows.value
        rows.value = rows.value + row.date
        return if (existed) -1L else 1L
    }

    override fun observeAll(): Flow<List<PlayDayEntity>> =
        rows.map { dates -> dates.sorted().map(::PlayDayEntity) }

    override suspend fun all(): List<PlayDayEntity> = rows.value.sorted().map(::PlayDayEntity)

    override suspend fun deleteAll() {
        rows.value = emptySet()
    }

    fun put(date: LocalDate) {
        rows.value = rows.value + date.toString()
    }

    fun clear() {
        rows.value = emptySet()
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
