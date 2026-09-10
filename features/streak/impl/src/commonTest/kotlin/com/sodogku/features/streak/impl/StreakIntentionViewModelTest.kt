package com.sodogku.features.streak.impl

import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.progress.streak.StreakSummary
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The one-off "you have a streak, keep it" moment.
 *
 * Rewritten with the screen. The old version had a paw to fill and two ways out,
 * one of which launched the daily, and its tests were mostly about that fork.
 * There is no fork now: the streak exists whether or not the player taps, and
 * the tap is an acknowledgement.
 */
class StreakIntentionViewModelTest : CoroutineTest() {

    @Test
    fun itRecordsItselfAsShownImmediately() = runUnitTest {
        // Before the player does anything, and that is the point: the screen has
        // no way past it except through it, so a force-quit un-recording it
        // would make killing the app the only exit.
        val streak = RecordingStreak()

        viewModel(streak)

        assertEquals(1, streak.shown.size)
        assertEquals(StreakPrompt.Intention, streak.shown.single())
    }

    @Test
    fun itShowsTheRunThePlayerAlreadyHas() = runUnitTest {
        val state = viewModel(RecordingStreak(current = 1)).state

        assertEquals(1, state.streak)
        assertEquals(7, state.week.size, "the week under the number came through")
    }

    @Test
    fun aFailedReadStillShowsADayRatherThanZero() = runUnitTest {
        // This screen only opens off a finished board, so the run is at least a
        // day. "0 day streak" under "can you keep this up?" would be the app
        // contradicting itself because a read failed.
        assertEquals(1, viewModel(RecordingStreak(current = 0)).state.streak)
    }

    @Test
    fun theOneButtonCloses() = runUnitTest {
        val vm = viewModel(RecordingStreak())

        vm.takeAction(StreakIntentionAction.Commit)

        assertEquals(StreakIntentionEvent.Close, vm.eventFlow.first())
    }

    @Test
    fun theHapticsSettingIsCarriedToTheScreen() = runUnitTest {
        // Read, not applied. The design system decides what buzzes; this only
        // has to not lose the player's answer.
        assertTrue(viewModel(RecordingStreak()).state.haptics, "on by default")
        assertTrue(
            !viewModel(RecordingStreak(), InMemoryAppCache(AppData(hapticsEnabled = false))).state.haptics,
        )
    }

    private fun viewModel(
        streak: StreakRepository,
        cache: AppCache = InMemoryAppCache(),
    ) = StreakIntentionViewModel(streak = streak, appCache = cache)
}

private class RecordingStreak(private val current: Int = 1) : StreakRepository {

    val shown = mutableListOf<StreakPrompt>()

    override fun observe(): Flow<StreakSummary> = emptyFlow()

    override suspend fun summary(): StreakSummary = StreakSummary(
        current = current,
        longest = current,
        today = Today,
        days = week(),
        playedToday = true,
        untilTomorrow = Duration.ZERO,
    )

    override suspend fun onBoardCompleted() = Unit

    override suspend fun pendingPrompt(): StreakPrompt = StreakPrompt.None

    override suspend fun onPromptShown(prompt: StreakPrompt) {
        shown += prompt
    }

    override suspend fun reset() = Unit

    private fun week(): List<StreakDay> = (0..6).map { index ->
        val date = LocalDate.fromEpochDays(Today.toEpochDays() - (2 - index))
        StreakDay(
            date = date,
            state = if (date <= Today) StreakDayState.Completed else StreakDayState.Future,
            isToday = date == Today,
        )
    }

    private companion object {
        val Today = LocalDate(2026, 9, 9)
    }
}
