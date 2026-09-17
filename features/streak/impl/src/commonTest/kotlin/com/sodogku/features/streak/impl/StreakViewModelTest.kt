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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one thing this screen must not get wrong: a page opened by a tap does not
 * move, and a page opened by a milestone does.
 *
 * Both assertions name the exact cell. "Nothing is animating" would pass against
 * a screen that had lost its animation entirely, so the two tests are written as
 * a pair and neither is true of a broken implementation.
 *
 * The same pairing covers the number. [StreakState.countUpFrom] has three
 * answers, and the third — a run of one, which after SD-121 is a player coming
 * back to a run that had broken — is the one that would otherwise be decided by
 * an arithmetic accident in the screen.
 *
 * SD-127 adds a third way in, and it is the one that needs both halves at once:
 * a lost run performs, because today's square landing in a calendar with a gap
 * in it is the only good news the page has, and it never counts, because a
 * number climbing to itself in display type while the page apologises is the
 * page congratulating itself.
 *
 * Deliberately not covered here: that the number actually climbs, and that the
 * page holds still when it is asked to. Those are claims about a composition and
 * live in `CountUpNumberTest` and `ArrivingHoldsStillTest` in `:libraries:ui`.
 * How the page *arrives* is `StreakRouteTest` in `:features:streak`. Which of
 * the four prompts is due at all is `StreakPromptsTest` in `:libraries:progress`.
 */
class StreakViewModelTest : CoroutineTest() {

    private val today = LocalDate(2026, 9, 7)

    @Test
    fun openedByTap_nothingAnimatesAndNothingIsRecorded() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 3))

        val vm = viewModel(repo, celebrating = 0)

        assertNull(vm.state.fillingIndex, "a page the player asked for is still")
        assertEquals(0, vm.state.celebrating)
        assertEquals(emptyList<StreakPrompt>(), repo.shown, "nothing happened, so nothing is spent")
        assertEquals(3, vm.state.current, "and it is a real page, not an empty one")
    }

    @Test
    fun openedByAMilestone_theNewestPlayedDayIsTheOneThatFills() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 3))

        val vm = viewModel(repo, celebrating = 3)

        val filled = vm.state.days[requireNotNull(vm.state.fillingIndex)]
        assertEquals(today, filled.date, "the day just played, not the first one in the window")
        assertEquals(StreakDayState.Completed, filled.state)
        assertTrue(filled.isToday)
    }

    @Test
    fun aCelebrationIsRecordedOnArrival() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 7))

        viewModel(repo, celebrating = 7)

        assertEquals(
            listOf<StreakPrompt>(StreakPrompt.Celebrate(7)),
            repo.shown,
            "recorded when it opens, so killing the app mid-celebration does not replay it",
        )
    }

    @Test
    fun aCelebrationWithNothingCompletedAnimatesNothingRatherThanCrashing() = runUnitTest {
        // Reachable: the table is wiped from Settings while the route is in
        // flight, or a config change closes the daily between the two reads.
        val repo = FakeStreak(summary(completedThrough = 0))

        val vm = viewModel(repo, celebrating = 7)

        assertNull(vm.state.fillingIndex)
        assertEquals(7, vm.state.celebrating, "the headline still says what it was pushed for")
    }

    @Test
    fun aResultThatLandsWhileThePageIsOpenRedrawsIt() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 3))
        val vm = viewModel(repo, celebrating = 0)
        assertEquals(3, vm.state.current)

        repo.emit(summary(completedThrough = 4))

        assertEquals(4, vm.state.current)
        assertNull(vm.state.fillingIndex, "and it still does not animate; it was opened by a tap")
    }

    @Test
    fun reduceAnimations_leavesTheMilestonePageStill() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 7))

        val vm = viewModel(
            repo,
            celebrating = 7,
            cache = InMemoryAppCache(AppData(reduceAnimations = true)),
        )

        assertNull(vm.state.fillingIndex, "the setting is honoured even on a celebration")
        assertEquals(7, vm.state.celebrating, "and the page still says what it is for")
        assertEquals(7, vm.state.current)
    }

    @Test
    fun aGrowingRunCountsUpFromTheDayBefore() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 4))

        val vm = viewModel(repo, celebrating = 4)

        assertEquals(
            3,
            vm.state.countUpFrom,
            "the flip is supposed to be from the run the player held yesterday to the one " +
                "they hold now",
        )
    }

    @Test
    fun aRunThatStartedOverLandsWithoutCountingUpFromNothing() = runUnitTest {
        // SD-121 made this reachable: a run of one is celebrated once the
        // intention moment has been spent, and that is a player coming back to a
        // run that had broken rather than a player starting out.
        val repo = FakeStreak(summary(completedThrough = 1))

        val vm = viewModel(repo, celebrating = 1)

        assertEquals(
            1,
            vm.state.countUpFrom,
            "a restart counted up from zero, so the page opens by saying in display type " +
                "that the player had nothing",
        )
    }

    @Test
    fun aPageOpenedByTapCountsNothing() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 4))

        assertNull(
            viewModel(repo, celebrating = 0).state.countUpFrom,
            "the number performed on a page the player went looking for",
        )
    }

    @Test
    fun theHapticsSettingReachesTheState() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 3))

        assertTrue(viewModel(repo, celebrating = 3).state.haptics, "on by default")
        assertTrue(
            !viewModel(
                repo,
                celebrating = 3,
                cache = InMemoryAppCache(AppData(hapticsEnabled = false)),
            ).state.haptics,
        )
    }

    @Test
    fun aLostRunIsRecordedOnArrival() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 1))

        viewModel(repo, celebrating = 0, lost = 12)

        assertEquals(
            listOf<StreakPrompt>(StreakPrompt.Lost(12)),
            repo.shown,
            "being told twice that a run you were proud of is gone is the version nobody asked for",
        )
    }

    @Test
    fun aLostRunPerformsButNeverCountsUp() = runUnitTest {
        // The two halves of what a lost page does, and they pull in opposite
        // directions. It is a ceremony, so today's square lands in a calendar
        // showing the gap beside it; it is not a celebration, so the 1 does not
        // climb to itself in display type while the page apologises.
        val repo = FakeStreak(summary(completedThrough = 1))

        val vm = viewModel(repo, celebrating = 0, lost = 12)

        assertEquals(today, vm.state.days[requireNotNull(vm.state.fillingIndex)].date)
        assertNull(vm.state.countUpFrom, "the number performed on a page about a run ending")
        assertEquals(12, vm.state.lost, "and the page still says what it was pushed for")
    }

    @Test
    fun reduceAnimations_leavesTheLostPageStillWithoutSilencingIt() = runUnitTest {
        val repo = FakeStreak(summary(completedThrough = 1))

        val vm = viewModel(
            repo,
            celebrating = 0,
            lost = 12,
            cache = InMemoryAppCache(AppData(reduceAnimations = true)),
        )

        assertNull(vm.state.fillingIndex, "the setting is honoured on this page too")
        assertEquals(12, vm.state.lost, "but the player is still told their run ended")
    }

    private fun viewModel(
        repo: StreakRepository,
        celebrating: Int,
        lost: Int = 0,
        cache: AppCache = InMemoryAppCache(),
    ) = StreakViewModel(streak = repo, appCache = cache, celebrating = celebrating, lost = lost)

    /**
     * Five weeks ending on Sunday 13 September, with [completedThrough] days
     * played up to and including today.
     */
    private fun summary(completedThrough: Int): StreakSummary {
        val start = LocalDate(2026, 8, 10)
        val days = List(WindowDays) { index ->
            val date = LocalDate.fromEpochDays(start.toEpochDays() + index)
            val playedFrom = today.toEpochDays() - completedThrough + 1
            StreakDay(
                date = date,
                state = when {
                    date > today -> StreakDayState.Future
                    completedThrough > 0 && date.toEpochDays() >= playedFrom -> StreakDayState.Completed
                    else -> StreakDayState.Missed
                },
                isToday = date == today,
            )
        }
        return StreakSummary(
            current = completedThrough,
            longest = completedThrough,
            today = today,
            days = days,
            playedToday = true,
            untilTomorrow = Duration.ZERO,
        )
    }
}

private const val WindowDays = 35

private class FakeStreak(initial: StreakSummary) : StreakRepository {

    private val summaries = MutableStateFlow(initial)
    val shown = mutableListOf<StreakPrompt>()

    fun emit(summary: StreakSummary) {
        summaries.value = summary
    }

    override fun observe(): Flow<StreakSummary> = summaries

    override suspend fun summary(): StreakSummary = summaries.value

    override suspend fun onBoardCompleted() = Unit


    override suspend fun pendingPrompt(): StreakPrompt = StreakPrompt.None

    override suspend fun onPromptShown(prompt: StreakPrompt) {
        shown += prompt
    }

    override suspend fun reset() = Unit
}
