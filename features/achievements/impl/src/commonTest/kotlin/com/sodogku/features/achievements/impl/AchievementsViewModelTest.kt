package com.sodogku.features.achievements.impl

import com.sodogku.libraries.achievements.Achievement
import com.sodogku.libraries.achievements.AchievementCounters
import com.sodogku.libraries.achievements.AchievementId
import com.sodogku.libraries.achievements.AchievementState
import com.sodogku.libraries.achievements.Achievements
import com.sodogku.libraries.achievements.AchievementsRepository
import com.sodogku.libraries.achievements.LevelResult
import com.sodogku.libraries.achievements.PlayMode
import com.sodogku.libraries.achievements.Stat
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class AchievementsViewModelTest : CoroutineTest() {

    @Test
    fun theGridHoldsEveryBadgeInTheCatalogInCatalogOrder() = runUnitTest {
        val vm = viewModel()

        assertEquals(
            Achievements.catalog.map { it.id },
            vm.state.badges.map { it.id },
            "the grid is the catalog, in the order the catalog declares",
        )
        assertEquals(Achievements.catalog.size, vm.state.totalCount)
    }

    @Test
    fun theShelvesHoldEveryBadgeExactlyOnce_inTheCatalogsOwnOrder() = runUnitTest {
        // The grid draws `sections`, not `badges`, so a badge that fell out of
        // the grouping would be invisible while every count still added up.
        val vm = viewModel()

        assertEquals(
            Achievements.sections.map { it.group },
            vm.state.sections.map { it.group },
            "the shelves are the catalog's, in the catalog's order",
        )
        assertEquals(
            Achievements.catalog.map { it.id },
            vm.state.sections.flatMap { section -> section.badges.map { it.id } },
        )
        vm.state.sections.forEach { section ->
            assertTrue(
                section.badges.all { it.group == section.group },
                "${section.group} holds a badge from somewhere else",
            )
        }
    }

    @Test
    fun earnedBadgesReadAsEarnedAndTheRestDoNot() = runUnitTest {
        val vm = viewModel(
            FakeAchievements(
                AchievementState(
                    counters = counters(Stat.LevelsCleared to 4L),
                    unlocked = mapOf(AchievementId.FirstSteps to 1_700_000_000_000L),
                ),
            ),
        )

        assertTrue(vm.badge(AchievementId.FirstSteps).unlocked)
        assertFalse(vm.badge(AchievementId.GoodDog).unlocked)
        assertEquals(1, vm.state.earnedCount)
    }

    @Test
    fun progressIsTheCounterOverTheTarget() = runUnitTest {
        // Four levels cleared is 4/10 of the way to Good Dog and 4/100 of the
        // way to Best in Show, off the same counter.
        val vm = viewModel(
            FakeAchievements(AchievementState(counters = counters(Stat.LevelsCleared to 4L))),
        )

        val goodDog = vm.badge(AchievementId.GoodDog)
        assertEquals(4L, goodDog.current)
        assertEquals(10L, goodDog.target)
        assertEquals(0.4f, goodDog.progress)

        val bestInShow = vm.badge(AchievementId.BestInShow)
        assertEquals(4L, bestInShow.current)
        assertEquals(100L, bestInShow.target)
        assertEquals(0.04f, bestInShow.progress)
    }

    @Test
    fun progressOnAnEarnedBadgeStopsAtItsTarget() = runUnitTest {
        // The counter keeps climbing forever. "342 / 10" under a badge earned
        // months ago reads as a bug rather than as a boast.
        val vm = viewModel(
            FakeAchievements(
                AchievementState(
                    counters = counters(Stat.LevelsCleared to 342L),
                    unlocked = mapOf(AchievementId.GoodDog to 1L),
                ),
            ),
        )

        assertEquals(10L, vm.badge(AchievementId.GoodDog).current)
        assertEquals(1f, vm.badge(AchievementId.GoodDog).progress)
    }

    @Test
    fun aHiddenBadgeGivesNothingAwayUntilItIsEarned() = runUnitTest {
        // The counters say a night clear has happened. A mystery badge that
        // reported "1 / 1" — or even "0 / 1" — would tell the reader exactly
        // what to go and try, which is the half of the surprise worth keeping.
        val vm = viewModel(
            FakeAchievements(AchievementState(counters = counters(Stat.NightClears to 1L))),
        )

        val nightOwl = vm.badge(AchievementId.NightOwl)
        assertTrue(nightOwl.mystery)
        assertEquals(0L, nightOwl.current, "a mystery badge reports no progress at all")
        assertEquals(0f, nightOwl.progress)
    }

    @Test
    fun aHiddenBadgeStopsBeingAMysteryOnceEarned() = runUnitTest {
        val vm = viewModel(
            FakeAchievements(
                AchievementState(
                    counters = counters(Stat.NightClears to 1L),
                    unlocked = mapOf(AchievementId.NightOwl to 1L),
                ),
            ),
        )

        assertFalse(vm.badge(AchievementId.NightOwl).mystery)
        assertTrue(vm.badge(AchievementId.NightOwl).unlocked)
    }

    @Test
    fun theSettingsToggleHidesTheGridAndNothingElse() = runUnitTest {
        val repository = FakeAchievements(
            AchievementState(unlocked = mapOf(AchievementId.FirstSteps to 1L)),
        )
        val vm = viewModel(repository, InMemoryAppCache(AppData(achievementsVisible = false)))

        assertFalse(vm.state.visible, "the grid is hidden")
        assertTrue(
            vm.badge(AchievementId.FirstSteps).unlocked,
            "the history is still folded while badges are hidden — the toggle is display only",
        )
        // The view model has no way to stop recording, and this is the assertion
        // that says so out loud: the repository is only ever read from here.
        assertTrue(repository.recorded.isEmpty())
    }

    @Test
    fun switchingBadgesBackOnShowsWhatWasEarnedWhileTheyWereOff() = runUnitTest {
        // The reason the toggle deliberately does not reach the repository. A
        // player turns badges off, plays for a month, turns them back on: the
        // grid has to show that month, not a blank slate.
        val repository = FakeAchievements(
            AchievementState(unlocked = mapOf(AchievementId.FirstSteps to 1L)),
        )
        val cache = InMemoryAppCache(AppData(achievementsVisible = false))
        val vm = viewModel(repository, cache)
        assertFalse(vm.state.visible)

        repository.record(sampleResult())
        repository.history.value = AchievementState(
            counters = counters(Stat.LevelsCleared to 12L),
            unlocked = mapOf(
                AchievementId.FirstSteps to 1L,
                AchievementId.GoodDog to 2L,
                AchievementId.SpeedDemon to 3L,
            ),
        )
        cache.set(AppData(achievementsVisible = true))

        assertTrue(vm.state.visible)
        assertEquals(3, vm.state.earnedCount)
        assertTrue(vm.badge(AchievementId.GoodDog).unlocked)
        assertTrue(vm.badge(AchievementId.SpeedDemon).unlocked)
    }

    @Test
    fun aBadgeEarnedWhileTheScreenIsOpenAppears() = runUnitTest {
        val repository = FakeAchievements()
        val vm = viewModel(repository)
        assertEquals(0, vm.state.earnedCount)

        repository.history.value = AchievementState(
            counters = counters(Stat.LevelsCleared to 1L),
            unlocked = mapOf(AchievementId.FirstSteps to 1L),
        )

        assertEquals(1, vm.state.earnedCount)
        assertTrue(vm.badge(AchievementId.FirstSteps).unlocked)
    }

    @Test
    fun tappingABadgeOpensItsDetailAndClosingPutsItBack() = runUnitTest {
        val vm = viewModel()

        vm.takeAction(AchievementsAction.Select(AchievementId.Pedigree))
        assertEquals(AchievementId.Pedigree, vm.state.selected?.id)
        assertEquals(50L, vm.state.selected?.target)

        vm.takeAction(AchievementsAction.CloseDetail)
        assertNull(vm.state.selected)
    }

    @Test
    fun theOpenDetailFollowsTheBadgeWhenItIsEarned() = runUnitTest {
        // `selected` is derived from `badges` rather than copied out of it, so
        // an unlock that lands with the sheet open flips the sheet too.
        val repository = FakeAchievements()
        val vm = viewModel(repository)
        vm.takeAction(AchievementsAction.Select(AchievementId.FirstSteps))
        assertFalse(vm.state.selected!!.unlocked)

        repository.history.value = AchievementState(
            unlocked = mapOf(AchievementId.FirstSteps to 1L),
        )

        assertTrue(vm.state.selected!!.unlocked)
    }

    @Test
    fun backLeavesTheScreen() = runUnitTest {
        val vm = viewModel()

        vm.takeAction(AchievementsAction.Back)

        assertEquals(AchievementsEvent.NavigateBack, vm.eventFlow.first())
    }

    /**
     * Opening the grid is what clears the board's trophy badge, and this write
     * is the only thing that does it. Left out, a badge earned once would sit on
     * the trophy for the life of the install.
     */
    @Test
    fun lookingAtTheGridMarksWhatIsOnItAsSeen() = runUnitTest {
        val cache = InMemoryAppCache()
        val repository = FakeAchievements(
            AchievementState(unlocked = mapOf(AchievementId.FirstSteps to NewestUnlock)),
        )

        viewModel(repository = repository, cache = cache)

        assertEquals(NewestUnlock, cache.get().achievementsSeenAt)
    }

    /**
     * With badges switched off this screen is an explanation rather than a grid,
     * so nothing on it has been seen. Marking it anyway would bury every badge
     * earned while they were off on the day they are switched back on.
     */
    @Test
    fun aGridNobodyCanSeeMarksNothing() = runUnitTest {
        val cache = InMemoryAppCache(AppData(achievementsVisible = false))
        val repository = FakeAchievements(
            AchievementState(unlocked = mapOf(AchievementId.FirstSteps to NewestUnlock)),
        )

        viewModel(repository = repository, cache = cache)

        assertEquals(0L, cache.get().achievementsSeenAt)
    }

    /** Two screens can write this, and the later one must not uncount a badge. */
    @Test
    fun theWatermarkOnlyEverMovesForward() = runUnitTest {
        val cache = InMemoryAppCache(AppData(achievementsSeenAt = NewestUnlock))
        val repository = FakeAchievements(
            AchievementState(unlocked = mapOf(AchievementId.FirstSteps to OlderUnlock)),
        )

        viewModel(repository = repository, cache = cache)

        assertEquals(NewestUnlock, cache.get().achievementsSeenAt)
    }

    private fun viewModel(
        repository: AchievementsRepository = FakeAchievements(),
        cache: AppCache = InMemoryAppCache(),
    ) = AchievementsViewModel(achievements = repository, appCache = cache)

    private fun AchievementsViewModel.badge(id: AchievementId): Badge =
        requireNotNull(state.badges.firstOrNull { it.id == id }) { "$id is missing from the grid" }

    private fun counters(vararg pairs: Pair<Stat, Long>) = AchievementCounters(pairs.toMap())

    private fun sampleResult(): LevelResult = LevelResult(
        levelId = 1,
        mode = PlayMode.Campaign,
        size = 4,
        completed = true,
        score = 100,
        paws = 3,
        timeMs = 1_000,
        strikes = 0,
        bestCombo = 4,
        sniffsUsed = 0,
        treatsUsed = 0,
        isFirstClear = true,
        previousBestPaws = 0,
        localHour = 12,
        finishedAt = 1L,
    )

    private class FakeAchievements(
        initial: AchievementState = AchievementState.Empty,
    ) : AchievementsRepository {
        val history = MutableStateFlow(initial)

        /** Every attempt handed to this fake, whatever the display setting says. */
        val recorded = mutableListOf<LevelResult>()

        override fun observe(): Flow<AchievementState> = history
        override suspend fun state(): AchievementState = history.value
        override suspend fun record(result: LevelResult): List<Achievement> {
            recorded += result
            return emptyList()
        }
        override suspend fun reset() {
            history.value = AchievementState.Empty
        }
    }

    private class InMemoryAppCache(initial: AppData = AppData()) : AppCache {
        private val data = MutableStateFlow(initial)
        override val updates: Flow<AppData> = data
        override suspend fun get(): AppData = data.value
        override suspend fun set(value: AppData) { data.value = value }
        override suspend fun clear() { data.value = AppData() }
    }

    private companion object {
        /** Two unlock times. The watermark is a comparison, so only order matters. */
        const val OlderUnlock = 1_000L
        const val NewestUnlock = 2_000L
    }
}
