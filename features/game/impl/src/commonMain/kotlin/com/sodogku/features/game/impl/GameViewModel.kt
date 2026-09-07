package com.sodogku.features.game.impl

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.puzzle.HintFinder
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.puzzle.autoMarkedCells
import com.sodogku.libraries.scoring.Praise
import com.sodogku.libraries.scoring.ScoreCard
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.ScoringConfig
import kotlin.time.TimeSource
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/** Where the attempt is. Everything the screen renders keys off this. */
enum class GamePhase { Loading, Playing, Won, Lost }

/**
 * Drives one attempt at one puzzle.
 *
 * Two things are deliberately *not* here. Ad frequency, booster economics and
 * scoring coefficients all live in config (C7), so this reads numbers rather
 * than deciding them. And the puzzle itself is answered by `:libraries:puzzle` —
 * a tap is right or wrong by array lookup against the shipped solution, never by
 * solving at tap time.
 */
@Inject
class GameViewModel(
    @Assisted private val levelId: Int,
    private val adGate: AdGate,
    private val entitlements: Entitlements,
) : SEAViewModel<GameState, GameEvent, GameAction>(initialStateArg = GameState()) {

    private val logger = KLog.withTag("Game")

    private val clock = TimeSource.Monotonic
    private var attemptStartedAt = clock.markNow()
    private var lastPlacementAt = clock.markNow()
    private var attemptNumber = 1

    init {
        takeAction(GameAction.Load)
    }

    override suspend fun handleAction(action: GameAction) {
        when (action) {
            GameAction.Load -> action.load()
            is GameAction.CellTapped -> action.tap(action.cell)
            is GameAction.CellLongPressed -> action.mark(action.cell)
            GameAction.SniffUsed -> action.sniff()
            GameAction.TreatUsed -> action.treat()
            GameAction.Retry -> action.restart()
            GameAction.ContinueAfterLoss -> action.continueAfterLoss()
            GameAction.Leave -> sendEvent(GameEvent.NavigateBack)
            is GameAction.TimerTick -> action.updateState { it.copy(elapsedMs = elapsedMs()) }
        }
    }

    private suspend fun GameAction.load() {
        val level = LevelPacks.campaign.byId(levelId)
        if (level == null) {
            logger.e { "No level $levelId in the campaign pack" }
            sendEvent(GameEvent.NavigateBack)
            return
        }
        startAttempt(level)
    }

    private suspend fun GameAction.startAttempt(level: LevelDefinition) {
        attemptStartedAt = clock.markNow()
        lastPlacementAt = attemptStartedAt
        logger.logEvent(
            "game.level_started",
            "level_id" to level.id,
            "size" to level.size,
            "difficulty" to level.difficulty,
            "attempt_number" to attemptNumber,
        )
        updateState {
            GameState(
                level = level,
                placed = Solution.empty(level.size),
                phase = GamePhase.Playing,
                livesRemaining = ScoringConfig.MAX_LIVES,
                sniffs = StartingSniffs,
                treats = StartingTreats,
            )
        }
    }

    /**
     * The core interaction. A tap on a marked cell clears the mark rather than
     * risking a life — otherwise a player who marked a cell by mistake would be
     * punished for correcting themselves.
     */
    private suspend fun GameAction.tap(cell: Int) {
        val level = state.level ?: return
        if (state.phase != GamePhase.Playing) return
        if (cell in state.placedCells) return

        if (cell in state.manualMarks) {
            updateState { it.copy(manualMarks = it.manualMarks - cell) }
            return
        }
        if (cell in state.autoMarks) return

        val row = level.board.rowOf(cell)
        val correct = level.solution[row] == level.board.colOf(cell)
        if (correct) place(cell) else strike(cell)
    }

    /**
     * The player's own "no dog here" note, on a cell auto-mark could not rule
     * out. Free and reversible: marking is how someone records a deduction, and
     * charging a life for thinking would be the wrong game.
     */
    private suspend fun GameAction.mark(cell: Int) {
        if (state.phase != GamePhase.Playing) return
        if (cell in state.placedCells || cell in state.autoMarks) return
        updateState {
            it.copy(
                manualMarks = if (cell in it.manualMarks) {
                    it.manualMarks - cell
                } else {
                    it.manualMarks + cell
                },
            )
        }
    }

    private suspend fun GameAction.place(cell: Int) {
        val level = state.level ?: return
        val row = level.board.rowOf(cell)
        val since = lastPlacementAt.elapsedNow().inWholeMilliseconds
        lastPlacementAt = clock.markNow()

        val scored = Scoring.placement(state.score, level.size, since)
        val placed = state.placed.withPlacement(row, level.board.colOf(cell))

        updateState {
            it.copy(
                placed = placed,
                autoMarks = level.board.autoMarkedCells(placed),
                manualMarks = it.manualMarks - cell,
                score = scored.card,
                lastPoints = scored.points,
                lastPraise = scored.praise,
                pointsNonce = it.pointsNonce + 1,
            )
        }
        sendEvent(GameEvent.PlacedDog(cell))

        if (placed.isComplete) win()
    }

    private suspend fun GameAction.strike(cell: Int) {
        val remaining = state.livesRemaining - 1
        updateState {
            it.copy(
                score = Scoring.strike(it.score),
                livesRemaining = remaining,
                strikeCell = cell,
                strikeNonce = it.strikeNonce + 1,
            )
        }
        sendEvent(GameEvent.Struck(cell))
        if (remaining <= 0) lose()
    }

    private suspend fun GameAction.win() {
        val level = state.level ?: return
        val finished = Scoring.complete(
            state.score,
            level.size,
            level.difficulty,
            state.livesRemaining,
        )
        val paws = Scoring.paws(finished.total, level.size, level.difficulty, completed = true)
        val duration = elapsedMs()

        logger.logEvent(
            "game.level_completed",
            "level_id" to level.id,
            "size" to level.size,
            "difficulty" to level.difficulty,
            "duration_ms" to duration,
            "score" to finished.total,
            "paws" to paws,
            "strikes_used" to (ScoringConfig.MAX_LIVES - state.livesRemaining),
            "sniffs_used" to (StartingSniffs - state.sniffs),
            "attempt_number" to attemptNumber,
        )
        updateState {
            it.copy(
                phase = GamePhase.Won,
                score = finished,
                paws = paws,
                elapsedMs = duration,
            )
        }
    }

    private suspend fun GameAction.lose() {
        val level = state.level ?: return
        logger.logEvent(
            "game.level_failed",
            "level_id" to level.id,
            "duration_ms" to elapsedMs(),
            "dogs_placed" to state.placed.placedCount,
            "attempt_number" to attemptNumber,
        )
        updateState { it.copy(phase = GamePhase.Lost, elapsedMs = elapsedMs()) }
    }

    /**
     * Restores one life and hands the board back untouched.
     *
     * Every non-dismissal outcome grants the continue. An ad network with no
     * inventory, or a player on a train, must never be the reason someone
     * cannot finish a puzzle they are most of the way through.
     */
    private suspend fun GameAction.continueAfterLoss() {
        val granted = if (entitlements.isPro.value) {
            true
        } else {
            when (adGate.showRewarded(AdPlacement.ContinueLevel)) {
                RewardOutcome.Dismissed -> false
                else -> true
            }
        }
        if (!granted) return

        lastPlacementAt = clock.markNow()
        logger.logEvent("game.continued", "level_id" to (state.level?.id ?: 0))
        updateState { it.copy(phase = GamePhase.Playing, livesRemaining = 1) }
    }

    private suspend fun GameAction.restart() {
        val level = state.level ?: return
        attemptNumber++
        startAttempt(level)
    }

    /**
     * Reveals the cell the *shallowest* remaining deduction proves, so a hint
     * teaches a technique instead of handing over a square.
     */
    private suspend fun GameAction.sniff() {
        val level = state.level ?: return
        if (state.phase != GamePhase.Playing || state.sniffs <= 0) return
        val cell = HintFinder.nextCell(level.board, state.placed) ?: return

        logger.logEvent("game.booster_used", "booster" to "sniff", "level_id" to level.id)
        updateState { it.copy(sniffs = it.sniffs - 1) }
        place(cell)
    }

    private suspend fun GameAction.treat() {
        if (state.phase != GamePhase.Playing || state.treats <= 0) return
        if (state.livesRemaining >= ScoringConfig.MAX_LIVES) return

        logger.logEvent("game.booster_used", "booster" to "treat")
        updateState { it.copy(treats = it.treats - 1, livesRemaining = it.livesRemaining + 1) }
    }

    private fun elapsedMs(): Long = attemptStartedAt.elapsedNow().inWholeMilliseconds

    private companion object {
        const val StartingSniffs = 3
        const val StartingTreats = 1
    }
}

data class GameState(
    val level: LevelDefinition? = null,
    val placed: Solution = Solution.empty(1),
    val autoMarks: Set<Int> = emptySet(),
    val manualMarks: Set<Int> = emptySet(),
    val livesRemaining: Int = ScoringConfig.MAX_LIVES,
    val score: ScoreCard = ScoreCard.Empty,
    val paws: Int = 0,
    val elapsedMs: Long = 0,
    val sniffs: Int = 0,
    val treats: Int = 0,
    val phase: GamePhase = GamePhase.Loading,

    /** Bumped per wrong tap so the same cell can shake twice in a row. */
    val strikeNonce: Int = 0,
    val strikeCell: Int? = null,

    /** Bumped per placement so two identically-scored taps both animate. */
    val pointsNonce: Int = 0,
    val lastPoints: Int = 0,
    val lastPraise: Praise = Praise.None,
) {
    val placedCells: Set<Int> get() = placed.cells().toSet()

    val dogsPlaced: Int get() = placed.placedCount

    val dogsRequired: Int get() = level?.size ?: 0
}

sealed interface GameEvent {
    data object NavigateBack : GameEvent

    /** For sound and haptics; the cell animates itself. */
    data class PlacedDog(val cell: Int) : GameEvent

    data class Struck(val cell: Int) : GameEvent
}

sealed interface GameAction {
    data object Load : GameAction
    data class CellTapped(val cell: Int) : GameAction
    data class CellLongPressed(val cell: Int) : GameAction
    data object SniffUsed : GameAction
    data object TreatUsed : GameAction
    data object Retry : GameAction
    data object ContinueAfterLoss : GameAction
    data object Leave : GameAction
    data class TimerTick(val at: Long) : GameAction
}
