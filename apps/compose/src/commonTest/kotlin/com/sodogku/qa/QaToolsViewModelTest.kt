package com.sodogku.qa

import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.devfeedback.DevFeedbackFabCache
import com.sodogku.devfeedback.DevFeedbackFabState
import com.sodogku.libraries.storage.Cache
import com.sodogku.libraries.storage.impl.cache.InMemoryCache
import com.sodogku.libraries.progress.db.PlayDayDao
import com.sodogku.libraries.progress.db.PlayDayEntity
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.progress.streak.StreakSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The QA panel's side of the day shift: what it reports, what it writes, and
 * what it does in a build that has no shiftable clock to talk to.
 *
 * The clock implementation itself is not here — it is debug-only source and is
 * covered by `QaShiftedClockTest`. What this file owns is the wiring: that the
 * panel finds the override through the injected `Clock`, that the date the rest
 * of the tools use moves with it, and that the whole section stays quiet when
 * there is nothing to find. The seeding and prompt-reset tools predate this
 * change and are exercised through their own repositories.
 */
@OptIn(ExperimentalTime::class)
class QaToolsViewModelTest : CoroutineTest() {

    @Test
    fun aBuildWithNoOverrideCannotShiftTheDay() = runUnitTest {
        val vm = viewModel(clock = FixedClock(Noon))

        assertFalse(vm.state.canShiftDay, "there is no shiftable clock in a release graph")
        assertEquals(Today, vm.state.today)
    }

    @Test
    fun shiftAndClearAreHarmlessWithNoOverride() = runUnitTest {
        val vm = viewModel(clock = FixedClock(Noon))

        vm.takeAction(QaToolsAction.ShiftDay(3))
        vm.takeAction(QaToolsAction.ClearDayShift)

        assertEquals(Today, vm.state.today, "nothing to move, so nothing moved")
        assertEquals(0, vm.state.dayShift)
    }

    @Test
    fun shiftingMovesTheDateThePanelReports() = runUnitTest {
        val vm = viewModel(clock = FakeShiftableClock(Noon))

        assertTrue(vm.state.canShiftDay)

        vm.takeAction(QaToolsAction.ShiftDay(2))

        assertEquals(2, vm.state.dayShift)
        assertEquals("2026-09-12", vm.state.today)
    }

    @Test
    fun shiftsAccumulateAndCanGoBackwards() = runUnitTest {
        val vm = viewModel(clock = FakeShiftableClock(Noon))

        vm.takeAction(QaToolsAction.ShiftDay(7))
        vm.takeAction(QaToolsAction.ShiftDay(-9))

        assertEquals(-2, vm.state.dayShift)
        assertEquals("2026-09-08", vm.state.today)
    }

    @Test
    fun clearingPutsThePanelBackOnRealTime() = runUnitTest {
        val vm = viewModel(clock = FakeShiftableClock(Noon))

        vm.takeAction(QaToolsAction.ShiftDay(4))
        vm.takeAction(QaToolsAction.ClearDayShift)

        assertEquals(0, vm.state.dayShift)
        assertEquals(Today, vm.state.today)
    }

    @Test
    fun markingTodayPlayedRecordsTheShiftedDay() = runUnitTest {
        val days = FakePlayDayDao()
        val vm = viewModel(clock = FakeShiftableClock(Noon), days = days)

        vm.takeAction(QaToolsAction.ShiftDay(1))
        vm.takeAction(QaToolsAction.MarkTodayPlayed)

        assertContentEquals(listOf("2026-09-11"), days.rows.map { it.date })
    }

    @Test
    fun seedingBackdatesFromTheShiftedDay() = runUnitTest {
        val days = FakePlayDayDao()
        val vm = viewModel(clock = FakeShiftableClock(Noon), days = days)

        vm.takeAction(QaToolsAction.ShiftDay(-1))
        vm.takeAction(QaToolsAction.SeedStreak(3))

        assertContentEquals(
            listOf("2026-09-06", "2026-09-07", "2026-09-08"),
            days.rows.map { it.date }.sorted(),
            "three days ending the day before the shifted today",
        )
    }

    private fun viewModel(
        clock: Clock,
        days: PlayDayDao = FakePlayDayDao(),
    ) = QaToolsViewModel(
        playDays = days,
        streak = SilentStreakRepository,
        // The FAB switch shares this screen and not these tests. An in-memory
        // cache keeps it out of the way without a file on disk.
        feedbackFab = FakeFeedbackFabCache(),
        clock = clock,
        timeZone = DeviceTimeZone { TimeZone.UTC },
    )
}

private const val Today = "2026-09-10"
private val Noon = Instant.parse("2026-09-10T12:00:00Z")

@OptIn(ExperimentalTime::class)
private class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

/**
 * Stands in for `QaShiftedClock` without its file. The arithmetic is the real
 * one's — a calendar day in the zone, keeping the time of day — because a fake
 * that added 24 hours would agree with the view model on every day of the year
 * except the two that matter.
 */
@OptIn(ExperimentalTime::class)
private class FakeShiftableClock(private val at: Instant) : ShiftableClock {
    private var offset = 0

    override val shiftedDays: Int get() = offset

    override fun now(): Instant {
        val local = at.toLocalDateTime(TimeZone.UTC)
        return LocalDateTime(local.date.plus(DatePeriod(days = offset)), local.time)
            .toInstant(TimeZone.UTC)
    }

    override fun shiftBy(days: Int) {
        offset += days
    }

    override fun clearShift() {
        offset = 0
    }
}

private class FakePlayDayDao : PlayDayDao {
    val rows = mutableListOf<PlayDayEntity>()

    override suspend fun insertIfAbsent(row: PlayDayEntity): Long {
        if (rows.none { it.date == row.date }) rows += row
        return 1
    }

    override fun observeAll(): Flow<List<PlayDayEntity>> = flowOf(rows.toList())

    override suspend fun all(): List<PlayDayEntity> = rows.toList()

    override suspend fun deleteAll() {
        rows.clear()
    }
}

private object SilentStreakRepository : StreakRepository {
    override fun observe(): Flow<StreakSummary> = flowOf()

    override suspend fun summary(): StreakSummary = StreakSummary(
        current = 0,
        longest = 0,
        today = LocalDate.parse(Today),
        days = emptyList(),
        playedToday = false,
        untilTomorrow = kotlin.time.Duration.ZERO,
    )

    override suspend fun onBoardCompleted() = Unit

    override suspend fun pendingPrompt(): StreakPrompt = StreakPrompt.None

    override suspend fun onPromptShown(prompt: StreakPrompt) = Unit

    override suspend fun reset() = Unit
}

private class FakeFeedbackFabCache :
    DevFeedbackFabCache,
    Cache<DevFeedbackFabState> by InMemoryCache({ DevFeedbackFabState() })
