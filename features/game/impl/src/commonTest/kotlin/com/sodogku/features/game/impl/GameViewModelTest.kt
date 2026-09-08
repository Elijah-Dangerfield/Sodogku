package com.sodogku.features.game.impl

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdOutcome
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyRepository
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.progress.daily.FreezeOffer
import com.sodogku.libraries.progress.daily.FreezeResult
import com.sodogku.libraries.achievements.Achievement
import com.sodogku.libraries.achievements.AchievementState
import com.sodogku.libraries.achievements.AchievementsRepository
import com.sodogku.libraries.achievements.LevelResult
import com.sodogku.libraries.achievements.PlayMode
import com.sodogku.libraries.scoring.ScoringConfig
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import com.sodogku.libraries.sodogku.ConsumableRefillTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class GameViewModelTest : CoroutineTest() {

    /**
     * The default test level. Deliberately past [StarterDogLevel]: the early
     * levels open with a dog already placed and its auto-marks spread, which
     * makes them a bad fixture for testing the loop itself.
     */
    private val level: LevelDefinition = assertNotNull(LevelPacks.campaign.byId(PlainLevel))

    @Test
    fun loadsTheRequestedLevelAndStartsPlaying() = runUnitTest {
        val vm = viewModel()

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(PlainLevel, vm.state.level?.id)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun aCorrectGuessPlacesADogAndScores() = runUnitTest {
        val vm = viewModel()

        vm.commit(cellFor(row = 0))

        assertEquals(1, vm.state.dogsPlaced)
        assertTrue(vm.state.score.total > 0)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun aCorrectGuessAutoMarksTheRowColumnRegionAndNeighbours() = runUnitTest {
        val vm = viewModel()
        val cell = cellFor(row = 0)

        vm.commit(cell)

        val board = level.board
        val row = board.rowOf(cell)
        val col = board.colOf(cell)
        val marks = vm.state.autoMarks
        assertTrue((0 until board.size).filter { it != col }.all { board.cellAt(row, it) in marks })
        assertTrue((0 until board.size).filter { it != row }.all { board.cellAt(it, col) in marks })
        assertTrue(board.neighborsOf(cell).all { it in marks })
    }

    @Test
    fun aWrongGuessCostsALifeAndResetsTheCombo() = runUnitTest {
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        val earned = vm.state.score.total

        vm.commit(tappableWrongCell(vm))

        assertEquals(ScoringConfig.MAX_LIVES - 1, vm.state.livesRemaining)
        assertEquals(0, vm.state.score.combo)
        assertEquals(earned, vm.state.score.total, "a strike must not take back points")
    }

    @Test
    fun theStrikeNonceChangesSoTheSameCellCanShakeTwice() = runUnitTest {
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.commit(cell)
        val first = vm.state.strikeNonce
        vm.commit(cell)

        assertTrue(vm.state.strikeNonce != first, "a repeated wrong tap has to re-fire the shake")
    }

    @Test
    fun threeStrikesEndTheAttempt() = runUnitTest {
        val vm = viewModel()

        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }

        assertEquals(GamePhase.Lost, vm.state.phase)
        assertEquals(0, vm.state.livesRemaining)
    }

    @Test
    fun placingEveryDogWinsAndAwardsPaws() = runUnitTest {
        val vm = viewModel()

        solve(vm)

        assertEquals(GamePhase.Won, vm.state.phase)
        assertTrue(vm.state.paws >= 1, "finishing at all is worth a paw")
        assertTrue(vm.state.score.total > 0)
    }

    @Test
    fun tappingAnAutoMarkedCellDoesNothing() = runUnitTest {
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        val marked = vm.state.autoMarks.first()

        vm.takeAction(GameAction.CellTapped(marked))

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(1, vm.state.dogsPlaced)
    }

    @Test
    fun aSingleTapOnlyEverWritesTheNote() = runUnitTest {
        // The safety property of the whole interaction model: one tap can never
        // cost a life, however wrong the cell is.
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.note(cell)

        assertTrue(cell in vm.state.manualMarks)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(0, vm.state.dogsPlaced)
    }

    @Test
    fun tappingANotedCellAgainErasesIt() = runUnitTest {
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)
        vm.note(cell)

        vm.note(cell)

        assertTrue(cell !in vm.state.manualMarks)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun twoTapsOutsideTheWindowAreTwoNotesRatherThanACommit() = runUnitTest {
        // The failure this guards: a slow tap-tap on a wrong cell costing a life
        // the player never meant to spend.
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.takeAction(GameAction.CellTapped(cell))
        clock += LateGap
        vm.takeAction(GameAction.CellTapped(cell))

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertTrue(cell !in vm.state.manualMarks, "the second tap erased the note")
    }

    @Test
    fun tapsOnTwoDifferentCellsNeverCommit() = runUnitTest {
        val vm = viewModel()

        vm.takeAction(GameAction.CellTapped(wrongCellIn(row = 0)))
        vm.takeAction(GameAction.CellTapped(wrongCellIn(row = 1)))

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(2, vm.state.manualMarks.size)
    }

    @Test
    fun aWrongGuessLeavesTheCellMarked() = runUnitTest {
        // The player just proved no dog goes there. Clearing it would make the
        // strike cost information as well as a life.
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.commit(cell)

        assertTrue(cell in vm.state.manualMarks)
    }

    @Test
    fun theFirstTapOfABoosterExplainsItRatherThanSpendingIt() = runUnitTest {
        // Spending a consumable cannot be undone, so an unfamiliar button has to
        // say what it costs before it costs anything.
        val vm = viewModel()
        val before = vm.state.sniffs

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        assertEquals(Consumable.Sniff, vm.state.boosterPrompt)
        assertEquals(before, vm.state.sniffs, "the explainer must not spend anything")
    }

    @Test
    fun aLaterTapSpendsWithoutExplaining() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        val after = vm.state.sniffs

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        assertEquals(null, vm.state.boosterPrompt)
        assertEquals(after - 1, vm.state.sniffs)
    }

    @Test
    fun anEmptyBoosterAlwaysOffersTheRefill() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        repeat(vm.state.sniffs) { vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff)) }
        assertEquals(0, vm.state.sniffs)

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        assertEquals(Consumable.Sniff, vm.state.boosterPrompt)
    }

    @Test
    fun aSniffRulesSquaresOutButNeverPlacesADog() = runUnitTest {
        // The whole point of the hint: it shows where a dog cannot go. One that
        // hands over the answer ends the puzzle.
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))

        assertEquals(0, vm.state.dogsPlaced)
        assertTrue(vm.state.hintCells.isNotEmpty())
        assertTrue(
            vm.state.hintCells.none { it in level.solution.cells().toSet() },
            "a hint must never rule out a square the answer occupies",
        )
    }

    @Test
    fun aSniffWithNothingToShowKeepsItsCharge() = runUnitTest {
        // Spending a booster for no visible effect is worse than refusing it.
        // On a nearly-solved board deduction has nothing left to rule out.
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
        val before = vm.state.sniffs

        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))

        assertEquals(before, vm.state.sniffs)
        assertTrue(vm.state.hintCells.isEmpty())
    }

    @Test
    fun aTreatPlacesACorrectDogForFree() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Treat))
        val before = vm.state.treats

        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Treat))

        assertEquals(before - 1, vm.state.treats)
        assertEquals(1, vm.state.dogsPlaced)
        assertTrue(vm.state.placedCells.all { it in level.solution.cells().toSet() })
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining, "a treat risks no bone")
    }

    @Test
    fun aRefillToppedUpByAnAdNeverReducesAHolding() = runUnitTest {
        // Someone who earned five treats from level rewards and then watches an
        // ad must not be punished back down to three.
        val cache = InMemoryAppCache()
        cache.set(AppData(treats = 5))
        val vm = viewModel(cache = cache)
        assertEquals(5, vm.state.treats)

        vm.takeAction(GameAction.BoosterRefillRequested(Consumable.Treat))

        assertEquals(5, vm.state.treats)
    }

    @Test
    fun aRefillTopsAnEmptyBoosterBackToThree() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0))
        val vm = viewModel(cache = cache)

        vm.takeAction(GameAction.BoosterRefillRequested(Consumable.Sniff))

        assertEquals(ConsumableRefillTo, vm.state.sniffs)
        assertEquals(ConsumableRefillTo, cache.get().sniffs)
    }

    @Test
    fun dismissingTheRefillAdLeavesTheHoldingAlone() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0))
        val vm = viewModel(adGate = FixedAdGate(RewardOutcome.Dismissed), cache = cache)

        vm.takeAction(GameAction.BoosterRefillRequested(Consumable.Sniff))

        assertEquals(0, vm.state.sniffs)
        assertEquals(null, vm.state.boosterPrompt)
    }

    @Test
    fun continueAfterLossRestoresOneLifeAndKeepsTheBoard() = runUnitTest {
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        repeat(ScoringConfig.MAX_LIVES) {
            vm.commit(tappableWrongCell(vm))
        }
        assertEquals(GamePhase.Lost, vm.state.phase)

        vm.takeAction(GameAction.ContinueAfterLoss)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(1, vm.state.livesRemaining)
        assertEquals(1, vm.state.dogsPlaced, "the board the player earned must survive")
    }

    @Test
    fun aFailedAdStillGrantsTheContinue() = runUnitTest {
        // The load-bearing rule of the whole ad layer: only a deliberate
        // dismissal withholds a reward. An empty ad network must never be the
        // reason someone cannot finish a puzzle.
        listOf(
            RewardOutcome.NoFill,
            RewardOutcome.Offline,
            RewardOutcome.Failed("boom"),
        ).forEach { outcome ->
            val vm = viewModel(adGate = FixedAdGate(outcome))
            repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }

            vm.takeAction(GameAction.ContinueAfterLoss)

            assertEquals(GamePhase.Playing, vm.state.phase, "outcome $outcome blocked the continue")
        }
    }

    @Test
    fun dismissingTheAdWithholdsTheContinue() = runUnitTest {
        val vm = viewModel(adGate = FixedAdGate(RewardOutcome.Dismissed))
        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }

        vm.takeAction(GameAction.ContinueAfterLoss)

        assertEquals(GamePhase.Lost, vm.state.phase)
    }

    @Test
    fun proSkipsTheAdEntirely() = runUnitTest {
        val gate = FixedAdGate(RewardOutcome.Dismissed)
        val vm = viewModel(adGate = gate, entitlements = ProEntitlements())
        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }

        vm.takeAction(GameAction.ContinueAfterLoss)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(0, gate.rewardedShown, "Pro must not be asked to watch anything")
    }

    @Test
    fun retryClearsTheBoardAndRestoresEveryLife() = runUnitTest {
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        repeat(ScoringConfig.MAX_LIVES) {
            vm.commit(tappableWrongCell(vm))
        }

        vm.takeAction(GameAction.Retry)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(0, vm.state.score.total)
        assertEquals(0, vm.state.dogsPlaced)
    }

    @Test
    fun earlyLevelsOpenWithOneDogAlreadyPlaced() = runUnitTest {
        // A teaching aid more than a leg-up: the free dog fires the auto-mark
        // cascade straight away, so the rules are visible before anyone has to
        // reason about them.
        val early = assertNotNull(LevelPacks.campaign.byId(StarterDogLevel))
        val vm = viewModel(levelId = StarterDogLevel)

        assertEquals(1, vm.state.dogsPlaced)
        assertTrue(vm.state.starterDogCell in early.solution.cells().toSet())
        assertEquals(0, vm.state.score.total, "a gift must not inflate an early best score")
        assertTrue(vm.state.autoMarks.isNotEmpty())
    }

    @Test
    fun laterLevelsOpenEmpty() = runUnitTest {
        val vm = viewModel(levelId = PlainLevel)

        assertEquals(0, vm.state.dogsPlaced)
        assertEquals(null, vm.state.starterDogCell)
    }

    @Test
    fun theLastBoneWarningFiresOnceOnTheEdgeIntoOneLife() = runUnitTest {
        // On the edge, not whenever one life happens to be showing. A warning
        // that reappears on every redraw stops being read.
        val vm = viewModel()
        assertEquals(null, vm.state.warning)

        vm.commit(tappableWrongCell(vm))
        assertEquals(null, vm.state.warning, "two lives left is not a warning")

        vm.commit(tappableWrongCell(vm))
        assertEquals(GameWarning.LastBone, vm.state.warning)

        vm.takeAction(GameAction.DismissWarning)
        assertEquals(null, vm.state.warning)
    }

    @Test
    fun refillingBonesFromAnAdRestoresEveryLife() = runUnitTest {
        val vm = viewModel()
        repeat(ScoringConfig.MAX_LIVES) { vm.commit(tappableWrongCell(vm)) }
        assertEquals(GamePhase.Lost, vm.state.phase)

        vm.takeAction(GameAction.RefillBones)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(
            ScoringConfig.MAX_LIVES,
            vm.state.livesRemaining,
            "a single bone would put the player straight back here",
        )
    }

    @Test
    fun hapticsToggleAndPersist() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = viewModel(cache = cache)
        assertTrue(vm.state.haptics, "vibration is on by default")

        vm.takeAction(GameAction.ToggleHaptics)

        assertFalse(vm.state.haptics)
        assertFalse(cache.get().hapticsEnabled)
    }

    @Test
    fun colorblindModeTogglesAndPersists() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = viewModel(cache = cache)

        vm.takeAction(GameAction.ToggleColorblind)

        assertTrue(vm.state.colorblind)
        assertTrue(cache.get().colorblindMode)
    }

    @Test
    fun guessesDoNothingOnceTheAttemptIsOver() = runUnitTest {
        val vm = viewModel()
        solve(vm)

        vm.commit(wrongCellIn(row = 0))

        assertEquals(GamePhase.Won, vm.state.phase)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun openingALevelRecordsTheAttempt() = runUnitTest {
        // Counted when the level opens, not when it is cleared: an attempt the
        // player walked away from still happened.
        val progress = InMemoryProgress()

        viewModel(progress = progress)

        assertEquals(1, progress.record(PlainLevel).attempts)
    }

    @Test
    fun everyRestartCountsAsAnotherAttempt() = runUnitTest {
        val progress = InMemoryProgress()
        val vm = viewModel(progress = progress)

        vm.takeAction(GameAction.Retry)

        assertEquals(2, progress.record(PlainLevel).attempts)
    }

    @Test
    fun aWinRecordsTheScorePawsAndTime() = runUnitTest {
        val progress = InMemoryProgress()
        val vm = viewModel(progress = progress)

        solve(vm)

        val record = progress.record(PlainLevel)
        assertEquals(LevelState.Completed, record.state)
        assertEquals(vm.state.score.total, record.bestScore)
        assertEquals(vm.state.paws, record.bestPaws)
        assertEquals(vm.state.elapsedMs, record.bestTimeMs)
        assertTrue(record.bestTimeMs > 0, "a clear has to carry a duration to beat later")
    }

    @Test
    fun aWinOpensTheNextLevel() = runUnitTest {
        val vm = viewModel()

        solve(vm)

        assertEquals(PlainLevel + 1, vm.state.unlockedThrough)
    }

    @Test
    fun theDrawerUnlocksFromProgressAlone() = runUnitTest {
        // Progress is the only thing that says how far the player got. The
        // AppData stopgap this replaced is gone, and a second source would drift.
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 10, paws = 1, timeMs = 100)

        val vm = viewModel(levelId = StarterDogLevel, progress = progress)

        assertEquals(StarterDogLevel + 1, vm.state.unlockedThrough)
    }

    @Test
    fun unlockedThroughStopsAtTheEndOfThePack() = runUnitTest {
        // Clearing the last level unlocks `id + 1` in the repository, which is
        // one past everything that shipped.
        val lastLevel = LevelPacks.campaign.size
        val progress = InMemoryProgress()
        progress.onCompleted(lastLevel, score = 1, paws = 3, timeMs = 1)
        assertEquals(lastLevel + 1, progress.unlockedThrough(), "the fake has to reproduce the bug")

        val vm = viewModel(levelId = lastLevel, progress = progress)

        assertEquals(lastLevel, vm.state.unlockedThrough)
    }

    @Test
    fun openingTheDrawerLoadsEveryLevelRecord() = runUnitTest {
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 900, paws = 3, timeMs = 4_000)
        val vm = viewModel(progress = progress)
        assertTrue(vm.state.records.isEmpty(), "records cost a query, so nothing loads them early")

        vm.takeAction(GameAction.LevelsOpened)

        assertEquals(3, vm.state.records[StarterDogLevel]?.bestPaws)
        assertEquals(LevelState.Completed, vm.state.records[StarterDogLevel]?.state)
        assertEquals(null, vm.state.records[300], "an untouched level has no record at all")
    }

    @Test
    fun theDrawerOpensWithItsRecordsAndNotBefore() = runUnitTest {
        // The flag and the rows it draws move in one update. Held apart — the
        // flag in the screen's `remember`, the records here — the pane could show
        // a full list of locked levels for however long the query took.
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 900, paws = 3, timeMs = 4_000)
        val vm = viewModel(progress = progress)
        assertFalse(vm.state.drawerOpen)

        vm.takeAction(GameAction.LevelsOpened)

        assertTrue(vm.state.drawerOpen)
        assertTrue(vm.state.records.isNotEmpty(), "open with nothing to show is the bug")

        vm.takeAction(GameAction.LevelsClosed)
        assertFalse(vm.state.drawerOpen)
    }

    @Test
    fun pickingALevelClosesTheDrawer() = runUnitTest {
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 1, paws = 1, timeMs = 1)
        val vm = viewModel(levelId = StarterDogLevel, progress = progress)
        vm.takeAction(GameAction.LevelsOpened)

        vm.takeAction(GameAction.GoToLevel(StarterDogLevel + 1))

        assertEquals(StarterDogLevel + 1, vm.state.level?.id)
        assertFalse(vm.state.drawerOpen, "the pane must not survive the level it launched")
    }

    @Test
    fun theStandingAdOfferRefillsBonesWithoutEverReducingThem() = runUnitTest {
        val vm = viewModel()
        repeat(2) { vm.commit(wrongCellIn(row = it)) }
        assertTrue(
            vm.state.livesRemaining < ScoringConfig.MAX_LIVES,
            "the fixture has to actually cost lives",
        )

        vm.takeAction(GameAction.RefillBones)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)

        // Already full: the ad may still play, but it must not take anything away.
        vm.takeAction(GameAction.RefillBones)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun aClearIsHandedToTheAchievementLogWithTheFactsTheFoldNeeds() = runUnitTest {
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)
        val level = assertNotNull(vm.state.level)

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
        assertEquals(GamePhase.Won, vm.state.phase, "the fixture has to actually finish")

        val recorded = badges.recorded.single()
        assertEquals(level.id, recorded.levelId)
        assertEquals(PlayMode.Campaign, recorded.mode)
        assertEquals(level.size, recorded.size)
        assertTrue(recorded.completed)
        assertEquals(vm.state.score.total, recorded.score)
        assertEquals(vm.state.paws, recorded.paws)
        assertEquals(0, recorded.strikes, "a clean run has no strikes")
        assertEquals(level.size, recorded.bestCombo, "an unbroken run is the whole board")
        assertEquals(0, recorded.sniffsUsed)
        assertEquals(0, recorded.treatsUsed)
        assertTrue(recorded.isFirstClear, "nothing had cleared this level before")
        assertEquals(0, recorded.previousBestPaws)
        assertEquals(FixedHour, recorded.localHour)
    }

    @Test
    fun aReplayIsNotAFirstClear() = runUnitTest {
        // The reason this matters: "levels cleared" counts levels. If a replay
        // read as a first clear, one level replayed a hundred times would earn
        // the hundred-level badge.
        val badges = RecordingAchievements()
        val progress = InMemoryProgress()
        progress.onCompleted(PlainLevel, score = 5_000, paws = 2, timeMs = 9_000)
        val vm = viewModel(progress = progress, achievements = badges)
        val level = assertNotNull(vm.state.level)

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }

        val recorded = badges.recorded.single()
        assertFalse(recorded.isFirstClear)
        assertEquals(2, recorded.previousBestPaws, "the fold needs the bar this attempt had to beat")
    }

    @Test
    fun aFailedAttemptIsRecordedToo() = runUnitTest {
        // Losses are evidence about how someone plays. Recording only wins would
        // make the log a record of successes, which is a different thing.
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)

        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }
        assertEquals(GamePhase.Lost, vm.state.phase)

        val recorded = badges.recorded.single()
        assertFalse(recorded.completed)
        assertEquals(0, recorded.paws)
        assertEquals(ScoringConfig.MAX_LIVES, recorded.strikes)
        assertFalse(recorded.isFirstClear)
    }

    @Test
    fun spentBoostersAreCountedPerAttemptAndResetOnRetry() = runUnitTest {
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)
        val level = assertNotNull(vm.state.level)

        vm.takeAction(GameAction.BoosterTapped(Consumable.Treat))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Treat))
        assertEquals(ConsumableRefillTo - 1, vm.state.treats, "the fixture has to actually spend one")

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
        assertEquals(1, badges.recorded.single().treatsUsed)

        // A retry starts a new attempt, and "cleared without help" is a question
        // about this attempt, not about the session.
        vm.takeAction(GameAction.Retry)
        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
        assertEquals(0, badges.recorded.last().treatsUsed, "spends must not carry across attempts")
    }

    @Test
    fun aDailyResolvesAgainstTheDailyPackAndIgnoresTheRoutesId() = runUnitTest {
        // The two packs share a number line, so an id alone does not name a
        // board. The route's id is only a hint; the status is what decides,
        // because the board and the date it will be recorded against have to
        // come from one snapshot.
        val vm = viewModel(levelId = 1, isDaily = true, daily = FakeDaily(levelId = DailyLevel))

        val opened = assertNotNull(vm.state.level)
        assertEquals(DailyLevel, opened.id)
        assertEquals(LevelPacks.daily.byId(DailyLevel), opened)
        assertTrue(
            opened != LevelPacks.campaign.byId(DailyLevel),
            "daily $DailyLevel and campaign $DailyLevel are different boards",
        )
        assertTrue(vm.state.isDaily)
        assertEquals(GamePhase.Playing, vm.state.phase)
    }

    @Test
    fun aSpentDayDoesNotOpen() = runUnitTest {
        // One attempt per day. The repository would refuse the write anyway, so
        // opening the board would hand someone a run whose score can never land.
        val daily = FakeDaily(result = completedToday())

        val vm = viewModel(isDaily = true, daily = daily)

        assertEquals(null, vm.state.level)
        assertEquals(GamePhase.Loading, vm.state.phase)
        assertTrue(daily.writes.isEmpty())
    }

    @Test
    fun aDailyClearIsWrittenToTheDailyAndNotToCampaignProgress() = runUnitTest {
        val daily = FakeDaily(levelId = DailyLevel)
        val progress = InMemoryProgress()
        val vm = viewModel(isDaily = true, daily = daily, progress = progress)

        solveCurrent(vm)

        assertEquals(GamePhase.Won, vm.state.phase)
        val written = daily.writes.single()
        assertEquals(DailyDate, written.date)
        assertEquals(DailyOutcome.Completed, written.outcome)
        assertEquals(vm.state.score.total, written.score)
        assertEquals(vm.state.paws, written.paws)

        assertTrue(
            progress.all().isEmpty(),
            "a daily may not touch campaign progress — not even an attempt row",
        )
        assertEquals(LevelRecord.FIRST_LEVEL_ID, progress.unlockedThrough())
        assertEquals(
            LevelRecord.FIRST_LEVEL_ID,
            vm.state.unlockedThrough,
            "daily $DailyLevel must not unlock campaign $DailyLevel",
        )
    }

    @Test
    fun aDailyOpeningDoesNotWidenTheCampaignFrontier() = runUnitTest {
        // The drawer unlocks against `unlockedThrough`, and the level on screen
        // is normally proof the player reached it. It is no such proof here.
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 1, paws = 1, timeMs = 1)
        val vm = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel), progress = progress)

        vm.takeAction(GameAction.LevelsOpened)

        assertEquals(StarterDogLevel + 1, vm.state.unlockedThrough)
    }

    @Test
    fun aDailyClearReachesTheAchievementLogWithTheStreakItProduced() = runUnitTest {
        // The streak *after* the write, which is the whole point: reading it
        // before recording the clear reports the number the player had
        // yesterday, and the log has no way to notice.
        val badges = RecordingAchievements()
        val vm = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel), achievements = badges)

        solveCurrent(vm)

        val recorded = badges.recorded.single()
        assertEquals(PlayMode.Daily, recorded.mode)
        assertEquals(DailyLevel, recorded.levelId)
        assertTrue(recorded.completed)
        assertEquals(OpeningStreak + 1, recorded.dailyStreakDays)
        assertEquals(OpeningStreak + 1, vm.state.dailyStreak, "the win sheet shows the same number")
    }

    @Test
    fun aCampaignClearIsStillCampaignAndCarriesNoStreak() = runUnitTest {
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)

        solve(vm)

        val recorded = badges.recorded.single()
        assertEquals(PlayMode.Campaign, recorded.mode)
        assertEquals(0, recorded.dailyStreakDays)
    }

    @Test
    fun aLostDailyIsSpentOnlyWhenThePlayerWalksAway() = runUnitTest {
        // Writing the loss the moment the bones run out would lock the day
        // against the clear an ad revive could still earn — `daily_result` takes
        // one row per date and never updates it.
        val daily = FakeDaily(levelId = DailyLevel)
        val vm = viewModel(isDaily = true, daily = daily)

        loseCurrent(vm)
        assertEquals(GamePhase.Lost, vm.state.phase)
        assertTrue(daily.writes.isEmpty(), "the revive is still on the table")

        vm.takeAction(GameAction.Leave)

        val written = daily.writes.single()
        assertEquals(DailyOutcome.Failed, written.outcome)
        assertEquals(DailyDate, written.date)
    }

    @Test
    fun aRevivedDailyCanStillBeCleared() = runUnitTest {
        val daily = FakeDaily(levelId = DailyLevel)
        val vm = viewModel(isDaily = true, daily = daily)
        loseCurrent(vm)

        vm.takeAction(GameAction.ContinueAfterLoss)
        assertEquals(GamePhase.Playing, vm.state.phase)
        solveCurrent(vm)

        assertEquals(DailyOutcome.Completed, daily.writes.single().outcome)
    }

    @Test
    fun aDailyCannotBeRestarted() = runUnitTest {
        val vm = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel))
        loseCurrent(vm)

        vm.takeAction(GameAction.Retry)

        assertEquals(GamePhase.Lost, vm.state.phase, "a second run at today's board is a replay")
    }

    @Test
    fun aLostCampaignLevelWritesNothingToTheDaily() = runUnitTest {
        val daily = FakeDaily()
        val vm = viewModel(daily = daily)

        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }
        vm.takeAction(GameAction.Leave)

        assertTrue(daily.writes.isEmpty())
    }

    @Test
    fun theCardFollowsTheObservedStatus() = runUnitTest {
        // Observed rather than fetched, because the repository re-emits at local
        // midnight and a drawer left open has to pick the new board up.
        val vm = viewModel(daily = FakeDaily(levelId = DailyLevel))

        val status = assertNotNull(vm.state.daily)
        assertEquals(DailyDate, status.date)
        assertEquals(DailyLevel, status.levelId)
        assertEquals(OpeningStreak, status.streak)
        assertTrue(status.playable)
    }

    @Test
    fun playingTheDailyFromTheDrawerClosesIt() = runUnitTest {
        val vm = viewModel(daily = FakeDaily(levelId = DailyLevel))
        vm.takeAction(GameAction.LevelsOpened)

        vm.takeAction(GameAction.PlayDaily)

        assertFalse(vm.state.drawerOpen)
        assertEquals(PlainLevel, vm.state.level?.id, "the daily opens on its own route, not in place")
    }

    @Test
    fun everyFreezeOutcomeSaysSomething() = runUnitTest {
        // A freeze that resolves silently is indistinguishable from a crash, and
        // Declined — the player closing the ad early — is the branch most likely
        // to be read as one.
        val cases = listOf(
            FreezeResult.Applied(DailyDate, streak = 9) to FreezeMessage.Applied(9),
            FreezeResult.Declined to FreezeMessage.Declined,
            FreezeResult.NoneLeft to FreezeMessage.NoneLeft,
            FreezeResult.NothingToFreeze to FreezeMessage.NothingToFreeze,
        )
        cases.forEach { (result, expected) ->
            val daily = FakeDaily(freezeResult = result)
            val vm = viewModel(daily = daily)

            vm.takeAction(GameAction.UseFreeze)

            assertEquals(expected, vm.state.freezeMessage, "$result was not reported")
            assertEquals(1, daily.freezesRequested)

            vm.takeAction(GameAction.DismissFreezeMessage)
            assertEquals(null, vm.state.freezeMessage)
        }
    }

    @Test
    fun aFreezeThatBlowsUpIsStillAnswered() = runUnitTest {
        val vm = viewModel(daily = FakeDaily(freezeThrows = true))

        vm.takeAction(GameAction.UseFreeze)

        assertEquals(FreezeMessage.Unavailable, vm.state.freezeMessage)
    }

    private val clock = TestTimeSource()

    private fun viewModel(
        levelId: Int = PlainLevel,
        isDaily: Boolean = false,
        adGate: AdGate = FixedAdGate(RewardOutcome.Rewarded),
        entitlements: Entitlements = FreeEntitlementsFake(),
        cache: AppCache = InMemoryAppCache(),
        progress: ProgressRepository = InMemoryProgress(),
        daily: DailyRepository = FakeDaily(),
        achievements: AchievementsRepository = RecordingAchievements(),
    ) = GameViewModel(
        levelId,
        isDaily,
        adGate,
        entitlements,
        clock,
        cache,
        progress,
        daily,
        achievements,
        // Fixed rather than the system clock: `localHour` is an input to the
        // time-of-day badges, so a test that read the real clock would pass or
        // fail depending on when it ran.
        wallClock = FixedClock,
        deviceTimeZone = { TimeZone.UTC },
    )

    /**
     * Commits a guess: two taps inside the double-tap window. A single tap only
     * ever writes the player's own cross.
     */
    private fun GameViewModel.commit(cell: Int) {
        takeAction(GameAction.CellTapped(cell))
        takeAction(GameAction.CellTapped(cell))
        clock += SettleGap
    }

    /** A tap far enough after the last one that it cannot read as a commit. */
    private fun GameViewModel.note(cell: Int) {
        takeAction(GameAction.CellTapped(cell))
        clock += SettleGap
    }

    private fun solve(vm: GameViewModel) {
        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
    }

    /** Solves whatever board the ViewModel actually opened, pack and all. */
    private fun solveCurrent(vm: GameViewModel) {
        val open = assertNotNull(vm.state.level, "nothing to solve — the board never opened")
        (0 until open.size).forEach { row ->
            vm.commit(open.board.cellAt(row, open.solution[row]))
        }
    }

    /** Spends every bone on the open board. */
    private fun loseCurrent(vm: GameViewModel) {
        val open = assertNotNull(vm.state.level)
        repeat(ScoringConfig.MAX_LIVES) { strike ->
            val row = strike % open.size
            val col = (0 until open.size).first { it != open.solution[row] }
            vm.commit(open.board.cellAt(row, col))
        }
    }

    private fun completedToday(): DailyResult = DailyResult(
        date = DailyDate,
        levelIndex = DailyLevel - 1,
        outcome = DailyOutcome.Completed,
        score = 4_200,
        paws = 3,
        timeMs = 90_000,
    )

    private fun cellFor(row: Int): Int = level.board.cellAt(row, level.solution[row])

    private fun wrongCellIn(row: Int): Int {
        val col = (0 until level.size).first { it != level.solution[row] }
        return level.board.cellAt(row, col)
    }

    /**
     * A wrong cell the player could actually tap right now.
     *
     * Not the same as "any wrong cell": auto-mark rules cells out as dogs land,
     * and a tap on a marked cell is correctly ignored. Picking blind made two
     * tests assert on strikes that the game had every right to swallow.
     */
    private fun tappableWrongCell(vm: GameViewModel): Int =
        (0 until level.board.cellCount).first { cell ->
            val row = level.board.rowOf(cell)
            level.board.colOf(cell) != level.solution[row] &&
                cell !in vm.state.autoMarks &&
                cell !in vm.state.manualMarks &&
                cell !in vm.state.placedCells
        }

    private companion object {
        const val FixedHour = 12

        /** Arbitrary, and deliberately not "today" — nothing here reads a clock. */
        val DailyDate = LocalDate(2026, 9, 7)

        /**
         * A daily id that also exists in the campaign, so a test asserting the
         * right pack is asserting something. Daily 200 is a 6x6, campaign 200 an
         * 8x8.
         */
        const val DailyLevel = 200

        /** Days already in the bag when a daily test starts. */
        const val OpeningStreak = 4

        /** Arbitrary but fixed: 2026-01-01T12:00:00Z, so `localHour` is 12 in UTC. */
        val FixedClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-01-01T12:00:00Z")
        }

        /** Past the starter-dog band, so the board opens empty. */
        const val PlainLevel = 200

        /** Inside the starter-dog band. */
        const val StarterDogLevel = 1

        /** Long enough that the next tap starts a fresh gesture. */
        val SettleGap = 500.milliseconds

        /** Just past the commit window, so a second tap is a second note. */
        val LateGap = 400.milliseconds
    }

    /**
     * In-memory [DailyRepository] that keeps the three rules the game leans on.
     *
     * A date with a result is never overwritten (the primary key does that in
     * Room), `playable` follows from the result exactly as [DailyStatus] defines
     * it, and **a clear moves the streak**. The last one matters: a ViewModel
     * that read the streak before writing the clear would still look right
     * against a fake whose streak never changed.
     */
    private class FakeDaily(
        date: LocalDate = DailyDate,
        levelId: Int = DailyLevel,
        streak: Int = OpeningStreak,
        enabled: Boolean = true,
        result: DailyResult? = null,
        freezeOffer: FreezeOffer? = null,
        private val freezeResult: FreezeResult = FreezeResult.NothingToFreeze,
        private val freezeThrows: Boolean = false,
    ) : DailyRepository {

        val writes = mutableListOf<DailyResult>()
        var freezesRequested = 0
            private set

        private val state = MutableStateFlow(
            DailyStatus(
                date = date,
                packIndex = levelId - 1,
                levelId = levelId,
                result = result,
                streak = streak,
                freezeOffer = freezeOffer,
                resetsIn = 6.hours,
                enabled = enabled,
            ),
        )

        override fun observe(): Flow<DailyStatus> = state

        override suspend fun status(): DailyStatus = state.value

        override suspend fun history(): List<DailyResult> = writes.toList()

        override suspend fun onCompleted(date: LocalDate, score: Int, paws: Int, timeMs: Long) {
            write(date, DailyOutcome.Completed, score, paws, timeMs)
        }

        override suspend fun onFailed(date: LocalDate, timeMs: Long) {
            write(date, DailyOutcome.Failed, score = 0, paws = 0, timeMs = timeMs)
        }

        override suspend fun useFreeze(): FreezeResult {
            freezesRequested++
            if (freezeThrows) error("no ad service")
            return freezeResult
        }

        override suspend fun reset() {
            writes.clear()
        }

        private fun write(
            date: LocalDate,
            outcome: DailyOutcome,
            score: Int,
            paws: Int,
            timeMs: Long,
        ) {
            if (writes.any { it.date == date }) return
            val snapshot = state.value
            val result = DailyResult(date, snapshot.packIndex, outcome, score, paws, timeMs)
            writes += result
            if (date != snapshot.date) return
            state.value = snapshot.copy(
                result = result,
                streak = if (outcome == DailyOutcome.Completed) {
                    snapshot.streak + 1
                } else {
                    snapshot.streak
                },
            )
        }
    }

    /** In-memory [AppCache], so a settings toggle can be asserted without disk. */
    private class InMemoryAppCache : AppCache {
        private val state = MutableStateFlow(AppData())
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData) { state.value = value }
        override suspend fun clear() { state.value = AppData() }
    }

    /**
     * In-memory [ProgressRepository].
     *
     * Keeps the two rules the game leans on — a metric only ever improves, and
     * clearing a level opens the next — so a test asserting on either is
     * asserting the contract the Room implementation also has to meet. It keeps
     * the *unclamped* `unlockedThrough`, one past the end of the pack after the
     * last level, because clamping that is the game's job.
     */
    /** Keeps every attempt handed to it, so a test can assert on what was recorded. */
    private class RecordingAchievements : AchievementsRepository {
        val recorded = mutableListOf<LevelResult>()
        private var state = AchievementState.Empty

        override fun observe(): Flow<AchievementState> = flowOf(state)

        override suspend fun state(): AchievementState = state

        override suspend fun record(result: LevelResult): List<Achievement> {
            recorded += result
            return emptyList()
        }

        override suspend fun reset() {
            recorded.clear()
            state = AchievementState.Empty
        }
    }

    private class InMemoryProgress : ProgressRepository {
        private val records = mutableMapOf<Int, LevelRecord>()

        override fun observe(levelId: Int): Flow<LevelRecord> =
            flowOf(records[levelId] ?: LevelRecord.unplayed(levelId))

        override suspend fun record(levelId: Int): LevelRecord =
            records[levelId] ?: LevelRecord.unplayed(levelId)

        override suspend fun all(): List<LevelRecord> = records.values.sortedBy { it.levelId }

        override suspend fun unlockedThrough(): Int = records.values
            .filter { it.state != LevelState.Locked }
            .maxOfOrNull { it.levelId }
            ?: LevelRecord.FIRST_LEVEL_ID

        override suspend fun onAttemptStarted(levelId: Int) {
            val current = record(levelId)
            records[levelId] = current.copy(
                state = current.state.orUnlocked(),
                attempts = current.attempts + 1,
            )
        }

        override suspend fun onCompleted(levelId: Int, score: Int, paws: Int, timeMs: Long) {
            val current = record(levelId)
            records[levelId] = current.copy(
                state = LevelState.Completed,
                bestScore = maxOf(current.bestScore, score),
                bestPaws = maxOf(current.bestPaws, paws),
                bestTimeMs = if (current.bestTimeMs == 0L) {
                    timeMs
                } else {
                    minOf(current.bestTimeMs, timeMs)
                },
            )
            unlock(levelId + 1)
        }

        override suspend fun onSkipped(levelId: Int) {
            val current = record(levelId)
            if (current.state != LevelState.Completed) {
                records[levelId] = current.copy(state = LevelState.Skipped)
            }
            unlock(levelId + 1)
        }

        override suspend fun reset() {
            records.clear()
        }

        private suspend fun unlock(levelId: Int) {
            records[levelId] = record(levelId).let { it.copy(state = it.state.orUnlocked()) }
        }

        private fun LevelState.orUnlocked(): LevelState =
            if (this == LevelState.Locked) LevelState.Unlocked else this
    }

    private class FixedAdGate(private val outcome: RewardOutcome) : AdGate {
        var rewardedShown = 0
            private set

        override suspend fun showRewarded(placement: AdPlacement): RewardOutcome {
            rewardedShown++
            return outcome
        }

        override suspend fun showInterstitial(placement: AdPlacement): AdOutcome = AdOutcome.NotShown
        override fun preload(placement: AdPlacement) = Unit
    }

    private class FreeEntitlementsFake : Entitlements {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
        override suspend fun purchasePro() = PurchaseOutcome.Unavailable
        override suspend fun restore() = RestoreOutcome.NothingToRestore
    }

    private class ProEntitlements : Entitlements {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun purchasePro() = PurchaseOutcome.AlreadyOwned
        override suspend fun restore() = RestoreOutcome.Restored
    }
}
