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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameViewModelTest : CoroutineTest() {

    private val level: LevelDefinition = assertNotNull(LevelPacks.campaign.byId(1))

    @Test
    fun loadsTheRequestedLevelAndStartsPlaying() = runUnitTest {
        val vm = viewModel()

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(1, vm.state.level?.id)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun aCorrectTapPlacesADogAndScores() = runUnitTest {
        val vm = viewModel()

        vm.takeAction(GameAction.CellTapped(cellFor(row = 0)))

        assertEquals(1, vm.state.dogsPlaced)
        assertTrue(vm.state.score.total > 0)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun aCorrectTapAutoMarksTheRowColumnRegionAndNeighbours() = runUnitTest {
        val vm = viewModel()
        val cell = cellFor(row = 0)

        vm.takeAction(GameAction.CellTapped(cell))

        val board = level.board
        val row = board.rowOf(cell)
        val col = board.colOf(cell)
        val marks = vm.state.autoMarks
        assertTrue((0 until board.size).filter { it != col }.all { board.cellAt(row, it) in marks })
        assertTrue((0 until board.size).filter { it != row }.all { board.cellAt(it, col) in marks })
        assertTrue(board.neighborsOf(cell).all { it in marks })
    }

    @Test
    fun aWrongTapCostsALifeAndResetsTheCombo() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.CellTapped(cellFor(row = 0)))
        val earned = vm.state.score.total

        vm.takeAction(GameAction.CellTapped(tappableWrongCell(vm)))

        assertEquals(ScoringConfig.MAX_LIVES - 1, vm.state.livesRemaining)
        assertEquals(0, vm.state.score.combo)
        assertEquals(earned, vm.state.score.total, "a strike must not take back points")
    }

    @Test
    fun theStrikeNonceChangesSoTheSameCellCanShakeTwice() = runUnitTest {
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.takeAction(GameAction.CellTapped(cell))
        val first = vm.state.strikeNonce
        vm.takeAction(GameAction.CellTapped(cell))

        assertTrue(vm.state.strikeNonce != first, "a repeated wrong tap has to re-fire the shake")
    }

    @Test
    fun threeStrikesEndTheAttempt() = runUnitTest {
        val vm = viewModel()

        repeat(ScoringConfig.MAX_LIVES) { vm.takeAction(GameAction.CellTapped(wrongCellIn(row = it))) }

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
        vm.takeAction(GameAction.CellTapped(cellFor(row = 0)))
        val marked = vm.state.autoMarks.first()

        vm.takeAction(GameAction.CellTapped(marked))

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(1, vm.state.dogsPlaced)
    }

    @Test
    fun longPressMarksAndUnmarksWithoutRisk() = runUnitTest {
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.takeAction(GameAction.CellLongPressed(cell))
        assertTrue(cell in vm.state.manualMarks)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining, "marking must never cost a life")

        vm.takeAction(GameAction.CellLongPressed(cell))
        assertTrue(cell !in vm.state.manualMarks)
    }

    @Test
    fun tappingAManualMarkClearsItRatherThanRiskingALife() = runUnitTest {
        // Someone who marked a cell by mistake should be able to undo it without
        // being punished for correcting themselves.
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)
        vm.takeAction(GameAction.CellLongPressed(cell))

        vm.takeAction(GameAction.CellTapped(cell))

        assertTrue(cell !in vm.state.manualMarks)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
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
        vm.takeAction(GameAction.CellTapped(wrongCellIn(row = 0)))

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
        vm.takeAction(GameAction.CellTapped(cellFor(row = 0)))
        repeat(ScoringConfig.MAX_LIVES) {
            vm.takeAction(GameAction.CellTapped(tappableWrongCell(vm)))
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
            repeat(ScoringConfig.MAX_LIVES) { vm.takeAction(GameAction.CellTapped(wrongCellIn(row = it))) }

            vm.takeAction(GameAction.ContinueAfterLoss)

            assertEquals(GamePhase.Playing, vm.state.phase, "outcome $outcome blocked the continue")
        }
    }

    @Test
    fun dismissingTheAdWithholdsTheContinue() = runUnitTest {
        val vm = viewModel(adGate = FixedAdGate(RewardOutcome.Dismissed))
        repeat(ScoringConfig.MAX_LIVES) { vm.takeAction(GameAction.CellTapped(wrongCellIn(row = it))) }

        vm.takeAction(GameAction.ContinueAfterLoss)

        assertEquals(GamePhase.Lost, vm.state.phase)
    }

    @Test
    fun proSkipsTheAdEntirely() = runUnitTest {
        val gate = FixedAdGate(RewardOutcome.Dismissed)
        val vm = viewModel(adGate = gate, entitlements = ProEntitlements())
        repeat(ScoringConfig.MAX_LIVES) { vm.takeAction(GameAction.CellTapped(wrongCellIn(row = it))) }

        vm.takeAction(GameAction.ContinueAfterLoss)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(0, gate.rewardedShown, "Pro must not be asked to watch anything")
    }

    @Test
    fun retryClearsTheBoardAndRestoresEveryLife() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.CellTapped(cellFor(row = 0)))
        repeat(ScoringConfig.MAX_LIVES) {
            vm.takeAction(GameAction.CellTapped(tappableWrongCell(vm)))
        }

        vm.takeAction(GameAction.Retry)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(0, vm.state.dogsPlaced)
        assertEquals(0, vm.state.score.total)
    }

    @Test
    fun tapsDoNothingOnceTheAttemptIsOver() = runUnitTest {
        val vm = viewModel()
        solve(vm)

        vm.takeAction(GameAction.CellTapped(wrongCellIn(row = 0)))

        assertEquals(GamePhase.Won, vm.state.phase)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    private fun viewModel(
        levelId: Int = 1,
        adGate: AdGate = FixedAdGate(RewardOutcome.Rewarded),
        entitlements: Entitlements = FreeEntitlementsFake(),
    ) = GameViewModel(levelId, adGate, entitlements)

    private fun solve(vm: GameViewModel) {
        (0 until level.size).forEach { row -> vm.takeAction(GameAction.CellTapped(cellFor(row))) }
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
