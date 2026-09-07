package com.sodogku.features.game.impl

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
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
import com.sodogku.libraries.sodogku.AppCache
import kotlin.time.ComparableTimeMark
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
    /**
     * Injected rather than reaching for [TimeSource.Monotonic] directly, because
     * both the speed bonus and double-tap recognition are timing decisions, and
     * a test cannot assert on either against a clock it does not control.
     */
    private val clock: TimeSource.WithComparableMarks,
    private val appCache: AppCache,
) : SEAViewModel<GameState, GameEvent, GameAction>(initialStateArg = GameState()) {

    private val logger = KLog.withTag("Game")

    private var attemptStartedAt = clock.markNow()
    private var lastPlacementAt = clock.markNow()
    private var attemptNumber = 1
    private var lastTappedCell: Int? = null
    private var warnedAboutLastBone = false
    private var lastTapAt: ComparableTimeMark? = null

    init {
        takeAction(GameAction.Load)
    }

    override suspend fun handleAction(action: GameAction) {
        when (action) {
            GameAction.Load -> action.load()
            is GameAction.CellTapped -> action.tap(action.cell)
            GameAction.SniffUsed -> action.sniff()
            GameAction.TreatUsed -> action.treat()
            GameAction.Retry -> action.restart()
            GameAction.ContinueAfterLoss -> action.continueAfterLoss()
            GameAction.Leave -> sendEvent(GameEvent.NavigateBack)
            GameAction.DismissWarning -> action.updateState { it.copy(warning = null) }
            GameAction.RefillBones -> action.refillBones()
            GameAction.ToggleColorblind -> action.toggleColorblind()
            GameAction.ToggleHaptics -> action.toggleHaptics()
            GameAction.ToggleReduceAnimations -> action.toggleReduceAnimations()
            GameAction.NextLevel -> action.nextLevel()
            is GameAction.GoToLevel -> action.goToLevel(action.levelId)
            GameAction.OpenPrivacy -> sendEvent(GameEvent.OpenPrivacy)
            GameAction.OpenTerms -> sendEvent(GameEvent.OpenTerms)
            GameAction.OpenFeedback -> sendEvent(GameEvent.OpenFeedback)
            is GameAction.TimerTick -> action.updateState { it.copy(elapsedMs = elapsedMs()) }
        }
    }

    private suspend fun GameAction.load() {
        val settings = Catching { appCache.get() }
            .logOnFailure { "Failed to read game settings" }
            .getOrNull()
        updateState {
            it.copy(
                colorblind = settings?.colorblindMode == true,
                haptics = settings?.hapticsEnabled != false,
                reduceAnimations = settings?.reduceAnimations == true,
                isPro = entitlements.isPro.value,
                unlockedThrough = settings?.currentLevel ?: 1,
            )
        }

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
        lastTappedCell = null
        lastTapAt = null
        warnedAboutLastBone = false
        logger.logEvent(
            "game.level_started",
            "level_id" to level.id,
            "size" to level.size,
            "difficulty" to level.difficulty,
            "attempt_number" to attemptNumber,
        )
        // The starter dog is folded into this one update rather than applied by
        // a second one. `state` reads a derived flow that lags `updateState` by
        // a dispatch, so a follow-up that re-read `state` would see the board as
        // it was before this update landed.
        val starterRow = 0
        val giveStarter = level.id <= StarterDogThroughLevel
        val opening = Solution.empty(level.size).let {
            if (giveStarter) it.withPlacement(starterRow, level.solution[starterRow]) else it
        }

        updateState {
            GameState(
                level = level,
                placed = opening,
                autoMarks = if (giveStarter) level.board.autoMarkedCells(opening) else emptySet(),
                starterDogCell = if (giveStarter) {
                    level.board.cellAt(starterRow, level.solution[starterRow])
                } else {
                    null
                },
                phase = GamePhase.Playing,
                livesRemaining = ScoringConfig.MAX_LIVES,
                sniffs = StartingSniffs,
                treats = StartingTreats,
                colorblind = it.colorblind,
                haptics = it.haptics,
                reduceAnimations = it.reduceAnimations,
                isPro = it.isPro,
                unlockedThrough = maxOf(it.unlockedThrough, level.id),
            )
        }
    }

    /**
     * The core interaction, and the reason the safe gesture is the cheap one.
     *
     * A single tap only ever writes or erases the player's own note — it can
     * never cost a life. Committing to a dog takes a *second* tap inside
     * [DoubleTapWindowMs], so the destructive action is deliberate.
     *
     * The second tap is recognised here rather than by the cell's gesture
     * detector on purpose: registering `onDoubleTap` in Compose withholds the
     * first tap until the double-tap timeout elapses, which would put ~300ms of
     * lag on the gesture players use most. Instead the cross draws instantly and
     * a follow-up tap converts it.
     */
    private suspend fun GameAction.tap(cell: Int) {
        if (state.phase != GamePhase.Playing) return
        if (cell in state.placedCells) return

        val now = clock.markNow()
        val isSecondTap = lastTappedCell == cell &&
            lastTapAt?.let { now - it }?.inWholeMilliseconds?.let { it <= DoubleTapWindowMs } == true
        lastTappedCell = cell
        lastTapAt = now

        if (isSecondTap) {
            lastTappedCell = null
            commit(cell)
        } else {
            toggleMark(cell)
        }
    }

    /** Writes or erases the player's own cross. Free, and never a life. */
    private suspend fun GameAction.toggleMark(cell: Int) {
        if (cell in state.autoMarks) return
        sendEvent(GameEvent.Marked(cell))
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

    /** The committed guess. This is the only path that can cost a life. */
    private suspend fun GameAction.commit(cell: Int) {
        val level = state.level ?: return
        if (cell in state.autoMarks) return

        val row = level.board.rowOf(cell)
        val correct = level.solution[row] == level.board.colOf(cell)
        if (correct) place(cell) else strike(cell)
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

        // The finished card is handed on rather than re-read from `state`, which
        // lags this update by a dispatch — re-reading would drop the points for
        // the very placement that won the level.
        if (placed.isComplete) win(level, scored.card)
    }

    /**
     * A wrong guess. The cell is left *marked*, not cleared: the player has just
     * proved no dog goes there, and throwing that away would make the strike
     * cost information as well as a life.
     */
    private suspend fun GameAction.strike(cell: Int) {
        val remaining = state.livesRemaining - 1
        updateState {
            it.copy(
                score = Scoring.strike(it.score),
                livesRemaining = remaining,
                manualMarks = it.manualMarks + cell,
                strikeCell = cell,
                strikeNonce = it.strikeNonce + 1,
            )
        }
        sendEvent(GameEvent.Struck(cell))
        when {
            remaining <= 0 -> lose()
            remaining == 1 && !warnedAboutLastBone -> {
                warnedAboutLastBone = true
                updateState { it.copy(warning = GameWarning.LastBone) }
            }
        }
    }

    private suspend fun GameAction.win(level: LevelDefinition, earned: ScoreCard) {
        val finished = Scoring.complete(
            earned,
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
        sendEvent(GameEvent.Won)
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

    /**
     * Trades an ad for a full set of bones and puts the board back in play.
     *
     * Deliberately restores *all* of them rather than one, unlike the continue:
     * this is the offer made to someone who has already run out, and handing
     * them a single bone would put them right back here on the next guess.
     */
    private suspend fun GameAction.refillBones() {
        val granted = entitlements.isPro.value ||
            adGate.showRewarded(AdPlacement.BoosterGrant) != RewardOutcome.Dismissed
        if (!granted) return

        lastPlacementAt = clock.markNow()
        warnedAboutLastBone = false
        logger.logEvent("game.bones_refilled", "level_id" to (state.level?.id ?: 0))
        updateState {
            it.copy(phase = GamePhase.Playing, livesRemaining = ScoringConfig.MAX_LIVES)
        }
    }

    private suspend fun GameAction.toggleColorblind() {
        val next = !state.colorblind
        updateState { it.copy(colorblind = next) }
        Catching { appCache.update { data -> data.copy(colorblindMode = next) } }
            .logOnFailure { "Failed to persist colorblind mode" }
    }

    private suspend fun GameAction.toggleHaptics() {
        val next = !state.haptics
        updateState { it.copy(haptics = next) }
        Catching { appCache.update { data -> data.copy(hapticsEnabled = next) } }
            .logOnFailure { "Failed to persist haptics setting" }
    }

    private suspend fun GameAction.toggleReduceAnimations() {
        val next = !state.reduceAnimations
        updateState { it.copy(reduceAnimations = next) }
        Catching { appCache.update { data -> data.copy(reduceAnimations = next) } }
            .logOnFailure { "Failed to persist reduce-animations setting" }
    }

    /**
     * Advances to the next level in the pack and records how far the player has
     * reached, which is both what the app opens on and what unlocks the drawer.
     */
    private suspend fun GameAction.nextLevel() {
        val current = state.level ?: return
        val next = LevelPacks.campaign.byId(current.id + 1)
        if (next == null) {
            sendEvent(GameEvent.NavigateBack)
            return
        }
        attemptNumber = 1
        Catching {
            appCache.update { data ->
                data.copy(currentLevel = maxOf(data.currentLevel, next.id))
            }
        }.logOnFailure { "Failed to record level progress" }
        startAttempt(next)
    }

    private suspend fun GameAction.goToLevel(levelId: Int) {
        val target = LevelPacks.campaign.byId(levelId) ?: return
        if (!entitlements.isPro.value && levelId > state.unlockedThrough) return
        attemptNumber = 1
        startAttempt(target)
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

        /** Levels that open with one dog already placed, as a teaching aid. */
        const val StarterDogThroughLevel = 25

        /**
         * How long after a tap a second one on the same cell counts as a commit.
         * Matches the platform double-tap timeout closely enough to feel native
         * without inheriting its latency.
         */
        const val DoubleTapWindowMs = 320L
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

    /** Region glyphs on, for players who cannot separate the fills by hue. */
    val colorblind: Boolean = false,

    /** Vibration on marks, placements and strikes. */
    val haptics: Boolean = true,

    /** Stills instead of animated dogs, and a shorter board entrance. */
    val reduceAnimations: Boolean = false,

    /** How far the player has reached; the level drawer unlocks up to it. */
    val unlockedThrough: Int = 1,

    /** Pro can jump to any level in the drawer, not just the ones reached. */
    val isPro: Boolean = false,

    /**
     * True when advancing will play an ad first, so the win sheet can badge the
     * button rather than springing one on the player. Wired to the config-driven
     * frequency gate in C7; nothing sets it yet.
     */
    val adBeforeNextLevel: Boolean = false,

    /** The free dog on early levels, so the UI can mark it as not the player's doing. */
    val starterDogCell: Int? = null,

    /** A one-shot spotlight the player has to dismiss. */
    val warning: GameWarning? = null,
) {
    val placedCells: Set<Int> get() = placed.cells().toSet()

    val dogsPlaced: Int get() = placed.placedCount

    val dogsRequired: Int get() = level?.size ?: 0
}

sealed interface GameEvent {
    data object NavigateBack : GameEvent

    /** For sound and haptics; the cell animates itself. */
    data class PlacedDog(val cell: Int) : GameEvent

    data class Marked(val cell: Int) : GameEvent

    data object Won : GameEvent
    data object OpenPrivacy : GameEvent
    data object OpenTerms : GameEvent
    data object OpenFeedback : GameEvent

    data class Struck(val cell: Int) : GameEvent
}

/** Something the game wants to stop and point at. */
enum class GameWarning { LastBone }

sealed interface GameAction {
    data object Load : GameAction
    data class CellTapped(val cell: Int) : GameAction
    data object SniffUsed : GameAction
    data object TreatUsed : GameAction
    data object Retry : GameAction
    data object ContinueAfterLoss : GameAction
    data object Leave : GameAction
    data object DismissWarning : GameAction
    data object RefillBones : GameAction
    data object ToggleColorblind : GameAction
    data object ToggleHaptics : GameAction
    data object ToggleReduceAnimations : GameAction
    data object NextLevel : GameAction
    data class GoToLevel(val levelId: Int) : GameAction
    data object OpenPrivacy : GameAction
    data object OpenTerms : GameAction
    data object OpenFeedback : GameAction
    data class TimerTick(val at: Long) : GameAction
}
