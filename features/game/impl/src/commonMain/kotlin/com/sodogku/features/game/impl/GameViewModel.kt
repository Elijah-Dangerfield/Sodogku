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
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.puzzle.HintFinder
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.puzzle.autoMarkedCells
import com.sodogku.libraries.scoring.Praise
import com.sodogku.libraries.scoring.ScoreCard
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.ScoringConfig
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.ConsumableRefillTo
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
    private val progress: ProgressRepository,
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
            is GameAction.BoosterTapped -> action.boosterTapped(action.consumable)
            is GameAction.BoosterConfirmed -> action.spend(action.consumable)
            is GameAction.BoosterRefillRequested -> action.refill(action.consumable)
            GameAction.DismissBoosterPrompt -> action.updateState { it.copy(boosterPrompt = null) }
            GameAction.Retry -> action.restart()
            GameAction.ContinueAfterLoss -> action.continueAfterLoss()
            GameAction.Leave -> sendEvent(GameEvent.NavigateBack)
            GameAction.DismissWarning -> action.updateState {
                it.copy(warning = null, hintCells = emptySet())
            }
            GameAction.RefillBones -> action.refillBones()
            GameAction.ToggleColorblind -> action.toggleColorblind()
            GameAction.ToggleHaptics -> action.toggleHaptics()
            GameAction.ToggleReduceAnimations -> action.toggleReduceAnimations()
            GameAction.NextLevel -> action.nextLevel()
            GameAction.LevelsOpened -> action.loadRecords()
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
                sniffs = settings?.sniffs ?: ConsumableRefillTo,
                treats = settings?.treats ?: ConsumableRefillTo,
                explainedBoosters = settings?.explainedBoosters
                    ?.mapNotNull { name -> Consumable.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                    .orEmpty(),
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
        // Recorded when the level opens rather than when it is cleared: an
        // abandoned attempt still happened, and it is what unlocks the level's
        // own row so `unlockedThrough` can see it.
        Catching { progress.onAttemptStarted(level.id) }
            .logOnFailure { "Failed to record the start of level ${level.id}" }
        val unlocked = unlockedThrough()

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
                sniffs = it.sniffs,
                treats = it.treats,
                explainedBoosters = it.explainedBoosters,
                colorblind = it.colorblind,
                haptics = it.haptics,
                reduceAnimations = it.reduceAnimations,
                isPro = it.isPro,
                records = it.records,
                unlockedThrough = maxOf(unlocked, level.id),
            )
        }
    }

    /**
     * How far the drawer opens.
     *
     * The repository unlocks `levelId + 1` on a clear and has no idea where the
     * pack ends, so clearing level 500 reports 501. Clamping belongs here, at
     * the only place that knows what shipped.
     */
    private suspend fun unlockedThrough(): Int = Catching { progress.unlockedThrough() }
        .logOnFailure { "Failed to read unlocked progress" }
        .getOrNull()
        ?.let(LevelPacks::clampToCampaign)
        ?: LevelRecord.FIRST_LEVEL_ID

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
                wrongGuesses = it.wrongGuesses + cell,
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
            "attempt_number" to attemptNumber,
        )
        // Every metric here is a *best*, not a last: the repository keeps the
        // better of what it holds and what this attempt scored, so a replay can
        // never cost the player a three-paw clear. It also opens the next level,
        // which is why nothing else writes an unlock.
        Catching { progress.onCompleted(level.id, finished.total, paws, duration) }
            .logOnFailure { "Failed to record the clear of level ${level.id}" }
        val unlocked = unlockedThrough()

        sendEvent(GameEvent.Won)
        updateState {
            it.copy(
                phase = GamePhase.Won,
                score = finished,
                paws = paws,
                elapsedMs = duration,
                unlockedThrough = maxOf(it.unlockedThrough, unlocked),
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
     * Advances to the next level in the pack.
     *
     * It writes no unlock of its own. The clear that got the player here already
     * opened the next level in the repository, and starting the attempt records
     * the rest — two writers for one fact is how the two disagree.
     */
    private suspend fun GameAction.nextLevel() {
        val current = state.level ?: return
        val next = LevelPacks.campaign.byId(current.id + 1)
        if (next == null) {
            sendEvent(GameEvent.NavigateBack)
            return
        }
        attemptNumber = 1
        startAttempt(next)
    }

    /**
     * The drawer's per-level history, read when it opens rather than observed.
     *
     * 500 rows that only change when an attempt ends do not need a live query
     * behind them, and the drawer is the only thing that reads them.
     */
    private suspend fun GameAction.loadRecords() {
        val records = Catching { progress.all() }
            .logOnFailure { "Failed to read level records" }
            .getOrNull()
            .orEmpty()
            .associateBy { it.levelId }
        val unlocked = unlockedThrough()
        updateState {
            it.copy(
                records = records,
                unlockedThrough = maxOf(unlocked, it.level?.id ?: LevelRecord.FIRST_LEVEL_ID),
            )
        }
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
     * The single entry point for every booster button.
     *
     * First tap of a booster always explains it, whatever the count. Spending a
     * consumable is irreversible, and the first time someone taps an unfamiliar
     * button they should learn what it costs before it happens. After that a tap
     * spends one, or offers the ad when they are out.
     */
    private suspend fun GameAction.boosterTapped(consumable: Consumable) {
        if (state.phase != GamePhase.Playing) return
        val explained = consumable in state.explainedBoosters
        if (!explained || countOf(consumable) <= 0) {
            updateState { it.copy(boosterPrompt = consumable) }
            return
        }
        spend(consumable)
    }

    private suspend fun GameAction.spend(consumable: Consumable) {
        if (countOf(consumable) <= 0) return
        markExplained(consumable)
        when (consumable) {
            Consumable.Sniff -> useSniff()
            Consumable.Treat -> useTreat()
            // Bones are spent by guessing wrong, never by tapping. The button is
            // an explainer and a refill offer, nothing else.
            Consumable.Bone -> updateState { it.copy(boosterPrompt = null) }
        }
    }

    /**
     * Tops the consumable back up to [ConsumableRefillTo] for an ad.
     *
     * Never *reduces* a holding: a player who earned five treats from level
     * rewards and watches an ad should not be punished down to three.
     */
    private suspend fun GameAction.refill(consumable: Consumable) {
        markExplained(consumable)
        val granted = entitlements.isPro.value ||
            adGate.showRewarded(AdPlacement.BoosterGrant) != RewardOutcome.Dismissed
        if (!granted) {
            updateState { it.copy(boosterPrompt = null) }
            return
        }

        val topped = maxOf(countOf(consumable), ConsumableRefillTo)
        logger.logEvent(
            "game.booster_refilled",
            "booster" to consumable.name.lowercase(),
            "to" to topped,
        )
        persistCounts(consumable, topped)
        updateState {
            val next = when (consumable) {
                Consumable.Bone -> it.copy(livesRemaining = topped, phase = GamePhase.Playing)
                Consumable.Sniff -> it.copy(sniffs = topped)
                Consumable.Treat -> it.copy(treats = topped)
            }
            next.copy(boosterPrompt = null)
        }
        if (consumable == Consumable.Bone) {
            warnedAboutLastBone = false
            lastPlacementAt = clock.markNow()
        }
    }

    /**
     * The hint. Shows where a dog *cannot* go rather than where one does: a hint
     * that hands over the answer ends the puzzle, one that rules squares out
     * teaches the technique that found them.
     */
    private suspend fun GameAction.useSniff() {
        val level = state.level ?: return
        val known = state.autoMarks + state.placedCells + state.wrongGuesses
        val ruledOut = HintFinder
            .ruledOutCells(level.board, state.placed, limit = SniffRevealLimit * SniffSearchSlack)
            .filterNot { cell -> cell in known }
            .take(SniffRevealLimit)
            .toSet()

        // A booster that costs a charge and shows nothing is worse than one that
        // refuses. If deduction has nothing left to add, close the prompt and
        // keep the sniff.
        if (ruledOut.isEmpty()) {
            logger.logEvent("game.booster_no_op", "booster" to "sniff", "level_id" to level.id)
            updateState { it.copy(boosterPrompt = null) }
            return
        }

        logger.logEvent("game.booster_used", "booster" to "sniff", "level_id" to level.id)
        persistCounts(Consumable.Sniff, state.sniffs - 1)
        updateState {
            it.copy(
                sniffs = it.sniffs - 1,
                boosterPrompt = null,
                hintCells = ruledOut,
            )
        }
    }

    /** The free placement. Costs a treat, no bone, and no risk. */
    private suspend fun GameAction.useTreat() {
        val level = state.level ?: return
        val cell = HintFinder.nextCell(level.board, state.placed) ?: run {
            updateState { it.copy(boosterPrompt = null) }
            return
        }

        logger.logEvent("game.booster_used", "booster" to "treat", "level_id" to level.id)
        persistCounts(Consumable.Treat, state.treats - 1)
        updateState { it.copy(treats = it.treats - 1, boosterPrompt = null) }
        place(cell)
    }

    private fun countOf(consumable: Consumable): Int = when (consumable) {
        Consumable.Bone -> state.livesRemaining
        Consumable.Sniff -> state.sniffs
        Consumable.Treat -> state.treats
    }

    private suspend fun GameAction.markExplained(consumable: Consumable) {
        if (consumable in state.explainedBoosters) return
        updateState { it.copy(explainedBoosters = it.explainedBoosters + consumable) }
        Catching {
            appCache.update { it.copy(explainedBoosters = it.explainedBoosters + consumable.name) }
        }.logOnFailure { "Failed to persist booster explainer" }
    }

    private suspend fun GameAction.persistCounts(consumable: Consumable, count: Int) {
        Catching {
            appCache.update {
                when (consumable) {
                    Consumable.Bone -> it.copy(bones = count)
                    Consumable.Sniff -> it.copy(sniffs = count)
                    Consumable.Treat -> it.copy(treats = count)
                }
            }
        }.logOnFailure { "Failed to persist $consumable count" }
    }

    private fun elapsedMs(): Long = attemptStartedAt.elapsedNow().inWholeMilliseconds

    private companion object {
        /** Levels that open with one dog already placed, as a teaching aid. */
        const val StarterDogThroughLevel = 25

        /** How many squares one sniff rules out. Enough to unstick, not to solve. */
        const val SniffRevealLimit = 4

        /** Search wider than we show, since auto-marked cells get filtered out. */
        const val SniffSearchSlack = 6

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

    /**
     * Squares that cost a bone. Tracked apart from [manualMarks] so they stay
     * red: a square someone paid for reads differently from one they worked out.
     */
    val wrongGuesses: Set<Int> = emptySet(),
    val livesRemaining: Int = ScoringConfig.MAX_LIVES,
    val score: ScoreCard = ScoreCard.Empty,
    val paws: Int = 0,
    val elapsedMs: Long = 0,
    val sniffs: Int = 0,
    val treats: Int = 0,

    /** Boosters whose first-use explainer the player has already seen. */
    val explainedBoosters: Set<Consumable> = emptySet(),

    /** The booster whose explainer or refill offer is open, if any. */
    val boosterPrompt: Consumable? = null,
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
    val unlockedThrough: Int = LevelRecord.FIRST_LEVEL_ID,

    /**
     * What the player has done with each level they have touched, keyed by id.
     * Filled when the drawer opens; levels with no entry have never been played.
     */
    val records: Map<Int, LevelRecord> = emptyMap(),

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

    /** Squares a sniff has ruled out, spotlit until the player taps away. */
    val hintCells: Set<Int> = emptySet(),
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
    /** Tapping a booster button. May explain, use, or offer a refill. */
    data class BoosterTapped(val consumable: Consumable) : GameAction

    /** Confirmed from the explainer: spend one. */
    data class BoosterConfirmed(val consumable: Consumable) : GameAction

    /** Confirmed from the explainer: watch an ad to refill. */
    data class BoosterRefillRequested(val consumable: Consumable) : GameAction

    data object DismissBoosterPrompt : GameAction
    data object Retry : GameAction
    data object ContinueAfterLoss : GameAction
    data object Leave : GameAction
    data object DismissWarning : GameAction
    data object RefillBones : GameAction
    data object ToggleColorblind : GameAction
    data object ToggleHaptics : GameAction
    data object ToggleReduceAnimations : GameAction
    data object NextLevel : GameAction

    /** The drawer was opened, so its per-level records need reading. */
    data object LevelsOpened : GameAction
    data class GoToLevel(val levelId: Int) : GameAction
    data object OpenPrivacy : GameAction
    data object OpenTerms : GameAction
    data object OpenFeedback : GameAction
    data class TimerTick(val at: Long) : GameAction
}
