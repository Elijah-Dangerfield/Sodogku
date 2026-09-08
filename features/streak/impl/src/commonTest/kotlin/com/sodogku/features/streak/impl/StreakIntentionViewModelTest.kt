package com.sodogku.features.streak.impl

import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.daily.DailyRepository
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.progress.daily.FreezeResult
import com.sodogku.libraries.progress.daily.RestoreResult
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.progress.streak.StreakSummary
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * The moment the player cannot skip, and the two ways out of it.
 *
 * The test that matters is the last one: a screen with no back button, whose
 * only exit is a board, is a trap the moment the board cannot be resolved.
 */
class StreakIntentionViewModelTest : CoroutineTest() {

    @Test
    fun itIsRecordedAsShownBeforeThePlayerDoesAnything() = runUnitTest {
        val streak = RecordingStreak()

        viewModel(streak = streak)

        assertEquals(
            listOf<StreakPrompt>(StreakPrompt.Intention),
            streak.shown,
            "there is no way past this screen, so a force-quit must not earn a second one",
        )
    }

    @Test
    fun nothingIsOfferedUntilThePawIsFull() = runUnitTest {
        val vm = viewModel()

        assertTrue(!vm.state.started)

        vm.takeAction(StreakIntentionAction.Filled)

        assertTrue(vm.state.started)
    }

    @Test
    fun playingOpensTodaysDailyRatherThanABoardCapturedEarlier() = runUnitTest {
        val vm = viewModel(daily = FakeDaily(levelId = 42))

        vm.takeAction(StreakIntentionAction.Play)

        assertEquals(StreakIntentionEvent.OpenDaily(42), vm.eventFlow.first())
    }

    @Test
    fun aDisabledDailyClosesTheScreenRatherThanTrappingThePlayer() = runUnitTest {
        val vm = viewModel(daily = FakeDaily(enabled = false))

        vm.takeAction(StreakIntentionAction.Play)

        assertEquals(
            StreakIntentionEvent.Close,
            vm.eventFlow.first(),
            "no board to send them to, and no back button either",
        )
    }

    @Test
    fun theHapticsSettingReachesTheState() = runUnitTest {
        // The paw's fill is the only thing in the app that buzzes outside a
        // board, and `LocalHaptics` defaults to silent, so a setting that never
        // arrives is a moment that is quiet for everybody.
        assertTrue(viewModel().state.haptics, "on by default")
        assertTrue(
            !viewModel(cache = InMemoryAppCache(AppData(hapticsEnabled = false))).state.haptics,
        )
    }

    @Test
    fun laterClosesWithoutOpeningAnything() = runUnitTest {
        val vm = viewModel()

        vm.takeAction(StreakIntentionAction.Later)

        assertEquals(StreakIntentionEvent.Close, vm.eventFlow.first())
    }

    private fun viewModel(
        streak: StreakRepository = RecordingStreak(),
        daily: DailyRepository = FakeDaily(),
        cache: AppCache = InMemoryAppCache(),
    ) = StreakIntentionViewModel(streak = streak, daily = daily, appCache = cache)
}

private class RecordingStreak : StreakRepository {

    val shown = mutableListOf<StreakPrompt>()

    override fun observe(): Flow<StreakSummary> = emptyFlow()

    override suspend fun summary(): StreakSummary = StreakSummary(
        current = 0,
        longest = 0,
        today = LocalDate(2026, 9, 7),
        days = emptyList(),
        enabled = true,
    )

    override suspend fun pendingPrompt(): StreakPrompt = StreakPrompt.None

    override suspend fun onPromptShown(prompt: StreakPrompt) {
        shown += prompt
    }

    override suspend fun reset() = Unit
}

private class FakeDaily(
    private val levelId: Int = 1,
    private val enabled: Boolean = true,
) : DailyRepository {

    override fun observe(): Flow<DailyStatus> = emptyFlow()

    override suspend fun status(): DailyStatus = DailyStatus(
        date = LocalDate(2026, 9, 7),
        packIndex = 0,
        levelId = levelId,
        result = null,
        streak = 0,
        freezeOffer = null,
        restoreOffer = null,
        resetsIn = 1.hours,
        enabled = enabled,
    )

    override suspend fun history(): List<DailyResult> = emptyList()

    override suspend fun onCompleted(date: LocalDate, score: Int, paws: Int, timeMs: Long) = Unit

    override suspend fun onFailed(date: LocalDate, timeMs: Long) = Unit

    override suspend fun useFreeze(): FreezeResult = FreezeResult.NothingToFreeze

    override suspend fun restoreStreak(): RestoreResult = RestoreResult.NothingToRestore

    override suspend fun reset() = Unit
}
