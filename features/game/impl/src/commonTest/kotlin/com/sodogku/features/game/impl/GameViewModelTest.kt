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
import com.sodogku.libraries.scoring.ScoringConfig
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    fun sniffPlacesARealDogAndSpendsACharge() = runUnitTest {
        val vm = viewModel()
        val before = vm.state.sniffs

        vm.takeAction(GameAction.SniffUsed)

        assertEquals(before - 1, vm.state.sniffs)
        assertEquals(1, vm.state.dogsPlaced)
        assertTrue(vm.state.placedCells.all { it in level.solution.cells().toSet() })
    }

    @Test
    fun sniffIsIgnoredWithNoChargesLeft() = runUnitTest {
        val vm = viewModel()
        repeat(vm.state.sniffs) { vm.takeAction(GameAction.SniffUsed) }
        val placed = vm.state.dogsPlaced

        vm.takeAction(GameAction.SniffUsed)

        assertEquals(placed, vm.state.dogsPlaced)
    }

    @Test
    fun treatRestoresALifeAfterAStrike() = runUnitTest {
        val vm = viewModel()
        vm.commit(wrongCellIn(row = 0))

        vm.takeAction(GameAction.TreatUsed)

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(0, vm.state.treats)
    }

    @Test
    fun treatCannotStockpileLivesAboveTheMaximum() = runUnitTest {
        val vm = viewModel()

        vm.takeAction(GameAction.TreatUsed)

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(1, vm.state.treats, "a wasted treat must not be spent")
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
        val vm = GameViewModel(
            PlainLevel,
            FixedAdGate(RewardOutcome.Rewarded),
            FreeEntitlementsFake(),
            clock,
            cache,
        )
        assertTrue(vm.state.haptics, "vibration is on by default")

        vm.takeAction(GameAction.ToggleHaptics)

        assertFalse(vm.state.haptics)
        assertFalse(cache.get().hapticsEnabled)
    }

    @Test
    fun colorblindModeTogglesAndPersists() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = GameViewModel(
            PlainLevel,
            FixedAdGate(RewardOutcome.Rewarded),
            FreeEntitlementsFake(),
            clock,
            cache,
        )

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

    private val clock = TestTimeSource()

    private fun viewModel(
        levelId: Int = PlainLevel,
        adGate: AdGate = FixedAdGate(RewardOutcome.Rewarded),
        entitlements: Entitlements = FreeEntitlementsFake(),
    ) = GameViewModel(levelId, adGate, entitlements, clock, InMemoryAppCache())

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
        /** Past the starter-dog band, so the board opens empty. */
        const val PlainLevel = 200

        /** Inside the starter-dog band. */
        const val StarterDogLevel = 1

        /** Long enough that the next tap starts a fresh gesture. */
        val SettleGap = 500.milliseconds

        /** Just past the commit window, so a second tap is a second note. */
        val LateGap = 400.milliseconds
    }

    /** In-memory [AppCache], so a settings toggle can be asserted without disk. */
    private class InMemoryAppCache : AppCache {
        private val state = MutableStateFlow(AppData())
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData) { state.value = value }
        override suspend fun clear() { state.value = AppData() }
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
