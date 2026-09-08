package com.sodogku.features.game.impl

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.config.values.BoostersRefillTo
import com.sodogku.libraries.config.values.BoostersStartingSniffs
import com.sodogku.libraries.config.values.BoostersStartingTreats
import com.sodogku.libraries.config.values.FeatureAchievements
import com.sodogku.libraries.config.values.FeatureBoosters
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.flowroutines.collectIn
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.achievements.Achievement
import com.sodogku.libraries.achievements.AchievementsRepository
import com.sodogku.libraries.achievements.LevelResult
import com.sodogku.libraries.achievements.PlayMode
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.daily.DailyRepository
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.daily.FreezeResult
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.puzzle.HintFinder
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.puzzle.autoMarkedCells
import com.sodogku.libraries.scoring.Praise
import com.sodogku.libraries.scoring.ScoreCard
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.ScoringConfig
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.BoardSnapshot
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
    /**
     * Which pack [levelId] is from. Fixed for the life of the ViewModel, because
     * it is the route that survives a process death — a mode this could switch
     * into would be lost the moment the app was backgrounded mid-puzzle.
     */
    @Assisted private val isDaily: Boolean,
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
    private val daily: DailyRepository,
    private val achievements: AchievementsRepository,
    /**
     * Wall clock, not [clock]. The monotonic one cannot answer "what time of day
     * is it", which is what the time-of-day badges and the attempt's timestamp
     * need, and it is not comparable across a process death.
     */
    private val wallClock: Clock,
    private val deviceTimeZone: DeviceTimeZone,
    /**
     * The `scoring.*` coefficients. Asked for a fresh set at each scoring
     * decision rather than held, so a retune lands on the next board rather
     * than the next install — see [ConfiguredScoring].
     */
    private val scoringConfig: ConfiguredScoring,
    private val startingSniffs: BoostersStartingSniffs,
    private val startingTreats: BoostersStartingTreats,
    private val refillTo: BoostersRefillTo,
    private val achievementsEnabled: FeatureAchievements,
    private val boostersEnabled: FeatureBoosters,
) : SEAViewModel<GameState, GameEvent, GameAction>(initialStateArg = GameState()) {

    private val logger = KLog.withTag("Game")

    private var attemptStartedAt = clock.markNow()
    private var lastPlacementAt = clock.markNow()
    private var attemptNumber = 1

    /**
     * Play time this attempt had already accumulated before it was resumed.
     *
     * The monotonic clock restarts with the process, so a resumed board has to
     * carry its own history. Adding it here rather than storing a wall-clock
     * start is what keeps the hours the app spent closed out of the timer.
     */
    private var elapsedBeforeResume = 0L
    private var lastTappedCell: Int? = null
    private var warnedAboutLastBone = false
    private var lastTapAt: ComparableTimeMark? = null

    /**
     * Consumables spent *this attempt*. The holdings in state only ever say what
     * is left, and "cleared it without help" is a question about what was spent.
     * Reset by [startAttempt] rather than accumulated across retries.
     */
    private var sniffsUsed = 0
    private var treatsUsed = 0

    /**
     * The guided run over levels 1 to 3.
     *
     * All of this is held in fields rather than read back off [state], which
     * lags `updateState` by a dispatch — a step that advanced off a stale
     * pointer would show the same coach mark twice and then skip one.
     */
    private var tutorialActive = false
    private var tutorialScript: List<TutorialStep> = emptyList()
    private var tutorialIndex = 0

    /**
     * Levels whose script has already run in this ViewModel, so losing level 1
     * and starting over does not replay seven coach marks. A process death
     * still resets it, which is the right answer: someone who left mid-lesson
     * has not had the lesson.
     */
    private val guidedLevels = mutableSetOf<Int>()

    /** SPEC 10: level 3 forgives one wrong guess while it is being taught. */
    private var freeMistakeAvailable = false

    /**
     * The level's history as it stood *before* this attempt touched it.
     *
     * Read at the start, because `onCompleted` overwrites it and the achievement
     * fold needs to know whether this was the first clear. Counting clears rather
     * than levels would let a replay walk "100 levels" up forever.
     */
    private var recordBeforeAttempt: LevelRecord = LevelRecord.unplayed(levelId)

    /**
     * The day whose board is being played, captured when the attempt opens.
     *
     * Every daily write is keyed on this rather than on "now", so an attempt that
     * starts at 23:58 and ends at 00:01 counts for the board it was started on
     * and leaves the new day genuinely unplayed.
     */
    private var dailyDate: LocalDate? = null

    init {
        takeAction(GameAction.Load)

        // The display settings are *observed*, not read once at load. The gear
        // opens a real screen now, so a player flips colourblind mode or reduce
        // animations and comes straight back to the board — and reading `AppData`
        // once meant the switch moved and the board did not change until the
        // next launch. Measured on a device: zero pixels changed.
        //
        // Only the three that change what is on screen. The consumable counts
        // are deliberately absent: this ViewModel is their writer, and echoing
        // its own writes back in would fight the spend it just made.
        appCache.updates
            .map { DisplaySettings(it.colorblindMode, it.hapticsEnabled, it.reduceAnimations) }
            .distinctUntilChanged()
            .onEach { display ->
                takeAction(GameAction.DisplaySettingsChanged(display))
            }
            .launchIn(viewModelScope)
        // The card lives in the drawer of every board, daily or not, and the
        // repository re-emits at local midnight — so a drawer left open past
        // midnight picks up the new board without this screen watching a clock.
        daily.observe().collectIn(viewModelScope) { takeAction(GameAction.DailyChanged(it)) }
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
            GameAction.Leave -> action.leave()
            GameAction.DismissWarning -> action.updateState {
                it.copy(warning = null, hintCells = emptySet())
            }
            GameAction.RefillBones -> action.refillBones()
            GameAction.ToggleColorblind -> action.toggleColorblind()
            GameAction.ToggleHaptics -> action.toggleHaptics()
            GameAction.ToggleReduceAnimations -> action.toggleReduceAnimations()
            GameAction.NextLevel -> action.nextLevel()
            GameAction.LevelsOpened -> action.loadRecords()
            GameAction.LevelsClosed -> action.updateState { it.copy(drawerOpen = false) }
            is GameAction.GoToLevel -> action.goToLevel(action.levelId)
            is GameAction.DailyChanged -> action.updateState { it.copy(daily = action.status) }
            GameAction.PlayDaily -> action.playDaily()
            GameAction.UseFreeze -> action.useFreeze()
            GameAction.DismissFreezeMessage -> action.updateState { it.copy(freezeMessage = null) }
            GameAction.OpenPrivacy -> sendEvent(GameEvent.OpenPrivacy)
            GameAction.OpenTerms -> sendEvent(GameEvent.OpenTerms)
            GameAction.OpenFeedback -> sendEvent(GameEvent.OpenFeedback)
            GameAction.OpenSettings -> sendEvent(GameEvent.OpenSettings)
            GameAction.TutorialAdvance -> action.tutorialTapped()
            GameAction.SkipTutorial -> action.skipTutorial()
            is GameAction.TimerTick -> action.updateState { it.copy(elapsedMs = elapsedMs()) }
            is GameAction.DisplaySettingsChanged -> action.updateState {
                it.copy(
                    colorblind = action.settings.colorblind,
                    haptics = action.settings.haptics,
                    reduceAnimations = action.settings.reduceAnimations,
                )
            }
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
                showAchievements = settings?.achievementsVisible != false,
                isPro = entitlements.isPro.value,
                boostersEnabled = boostersEnabled(),
                refillTo = refillTo(),
                // Null is "never granted any", which is what a fresh install
                // looks like — so the opening grant comes from config rather
                // than from a default baked into the record that stores it.
                sniffs = settings?.sniffs ?: startingSniffs(),
                treats = settings?.treats ?: startingTreats(),
                explainedBoosters = settings?.explainedBoosters
                    ?.mapNotNull { name -> Consumable.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                    .orEmpty(),
            )
        }

        // Read here and held in a field, not re-read per level: the flag is
        // written the moment the tutorial ends, and `startAttempt` for the next
        // level runs before that write has any chance to land.
        tutorialActive = !isDaily && settings?.hasCompletedTutorial != true

        val level = if (isDaily) todaysBoard() else LevelPacks.campaign.byId(levelId)
        if (level == null) {
            logger.e { "No level $levelId in the $modeName pack" }
            sendEvent(GameEvent.NavigateBack)
            return
        }
        // Only the attempt for *this* board comes back. A snapshot of some other
        // level is left alone rather than discarded: the player may well return
        // to it, and this screen has no business deciding that for them.
        val saved = settings?.boardInProgress
            ?.takeIf { it.levelId == level.id && it.isDaily == isDaily }
            ?.takeIf { it.placements.size == level.size }
        startAttempt(level, resume = saved)
    }

    /**
     * The daily board, resolved from the repository rather than from [levelId].
     *
     * The route's id came from a card that was drawn at some earlier moment, and
     * the board and the date the result is written against have to come from one
     * snapshot of the clock — otherwise a screen opened a second before midnight
     * records yesterday's board against today.
     *
     * Returns null when the day is already spent. One attempt per day is the
     * repository's rule and it would refuse the write anyway, but letting someone
     * play a board whose score can never be recorded is worse than not opening
     * it.
     */
    private suspend fun todaysBoard(): LevelDefinition? {
        val status = Catching { daily.status() }
            .logOnFailure { "Failed to read the daily status" }
            .getOrNull()
            ?: return null
        if (!status.playable) {
            logger.i { "The daily for ${status.date} is already spent" }
            return null
        }
        dailyDate = status.date
        return LevelPacks.daily.byId(status.levelId)
    }

    private suspend fun GameAction.startAttempt(
        level: LevelDefinition,
        resume: BoardSnapshot? = null,
    ) {
        attemptStartedAt = clock.markNow()
        lastPlacementAt = attemptStartedAt
        lastTappedCell = null
        lastTapAt = null
        warnedAboutLastBone = false
        sniffsUsed = resume?.sniffsUsed ?: 0
        treatsUsed = resume?.treatsUsed ?: 0
        elapsedBeforeResume = resume?.elapsedMs ?: 0L
        attemptNumber = resume?.attemptNumber ?: attemptNumber
        // Campaign progress is keyed on level id alone, and the two packs share
        // that number line, so every read and every write here is skipped for a
        // daily. Reading campaign level 7's record for daily level 7 would be
        // wrong quietly; writing it would hand out a campaign unlock.
        recordBeforeAttempt = if (isDaily) {
            LevelRecord.unplayed(level.id)
        } else {
            Catching { progress.record(level.id) }
                .logOnFailure { "Failed to read the prior record for level ${level.id}" }
                .getOrNull()
                ?: LevelRecord.unplayed(level.id)
        }
        logger.logEvent(
            "game.level_started",
            "level_id" to level.id,
            "size" to level.size,
            "difficulty" to level.difficulty,
            "attempt_number" to attemptNumber,
            "mode" to modeName,
        )
        // Recorded when the level opens rather than when it is cleared: an
        // abandoned attempt still happened, and it is what unlocks the level's
        // own row so `unlockedThrough` can see it.
        if (!isDaily) {
            Catching { progress.onAttemptStarted(level.id) }
                .logOnFailure { "Failed to record the start of level ${level.id}" }
        }
        val unlocked = unlockedThrough()

        // The starter dog is folded into this one update rather than applied by
        // a second one. `state` reads a derived flow that lags `updateState` by
        // a dispatch, so a follow-up that re-read `state` would see the board as
        // it was before this update landed.
        val starterRow = 0
        // A resumed board already has whatever the starter dog gave it, and
        // re-granting it would place a second dog in row 0.
        val giveStarter = resume == null && level.id <= StarterDogThroughLevel
        val opening = when {
            resume != null -> Solution(resume.placements.toIntArray())
            giveStarter -> Solution.empty(level.size).withPlacement(starterRow, level.solution[starterRow])
            else -> Solution.empty(level.size)
        }
        // Auto-marks are derived from the placements rather than restored, so a
        // snapshot cannot disagree with the board it describes.
        val openingMarks = if (opening.placedCount > 0) {
            level.board.autoMarkedCells(opening)
        } else {
            emptySet()
        }

        tutorialScript = if (tutorialActive && level.id !in guidedLevels) {
            Tutorial.scriptFor(level.id)
        } else {
            emptyList()
        }
        tutorialIndex = 0
        freeMistakeAvailable = tutorialScript.isNotEmpty() && level.id == Tutorial.FREE_MISTAKE_LEVEL
        val lesson = openFrame(level, opening, openingMarks)

        updateBoard {
            GameState(
                level = level,
                placed = opening,
                autoMarks = openingMarks,
                starterDogCell = if (giveStarter) {
                    level.board.cellAt(starterRow, level.solution[starterRow])
                } else {
                    null
                },
                phase = GamePhase.Playing,
                manualMarks = resume?.manualMarks.orEmpty(),
                wrongGuesses = resume?.wrongGuesses.orEmpty(),
                livesRemaining = resume?.livesRemaining ?: ScoringConfig.MAX_LIVES,
                score = resume?.let { saved ->
                    ScoreCard(
                        total = saved.score,
                        combo = saved.combo,
                        bestCombo = saved.bestCombo,
                        placements = saved.placementCount,
                    )
                } ?: ScoreCard.Empty,
                elapsedMs = resume?.elapsedMs ?: 0L,
                sniffs = it.sniffs,
                treats = it.treats,
                explainedBoosters = it.explainedBoosters,
                colorblind = it.colorblind,
                haptics = it.haptics,
                reduceAnimations = it.reduceAnimations,
                showAchievements = it.showAchievements,
                boostersEnabled = it.boostersEnabled,
                refillTo = it.refillTo,
                isPro = it.isPro,
                records = it.records,
                unlockedThrough = campaignFrontier(unlocked, level.id),
                daily = it.daily,
                isDaily = isDaily,
                tutorial = lesson.step,
                tutorialCells = lesson.cells,
            )
        }
    }

    /**
     * The lesson to show as a level opens.
     *
     * Separate from [advanceTutorial] only because there is no step to check a
     * trigger against yet; both funnel into [resolveFrame], which is the single
     * place that decides what is on screen.
     */
    private fun openFrame(
        level: LevelDefinition,
        placed: Solution,
        autoMarks: Set<Int>,
    ): TutorialFrame {
        if (tutorialScript.isEmpty()) return TutorialFrame.None
        val frame = resolveFrame(level, placed, autoMarks, justMarked = emptySet())
        if (frame.step == null) guidedLevels += level.id
        return frame
    }

    /**
     * The next step that can actually be shown, and the squares it points at.
     *
     * A gesture step whose target square does not exist on this board — the
     * player already crossed it off, or auto-mark swallowed it — is **skipped**
     * rather than shown. Showing it would put the scrim up over a board with
     * nothing tappable and no way forward, which is the one failure this
     * tutorial is not allowed to have.
     */
    private fun resolveFrame(
        level: LevelDefinition,
        placed: Solution,
        autoMarks: Set<Int>,
        justMarked: Set<Int>,
    ): TutorialFrame {
        while (tutorialIndex < tutorialScript.size) {
            val step = tutorialScript[tutorialIndex]
            val cells = Tutorial.cellsFor(step, level, placed, autoMarks, justMarked)
            if (Tutorial.triggerFor(step) == TutorialTrigger.Tap || cells.isNotEmpty()) {
                logger.logEvent(
                    "tutorial.step_viewed",
                    "step" to step.name,
                    "level_id" to level.id,
                )
                return TutorialFrame(step, cells)
            }
            tutorialIndex++
        }
        return TutorialFrame.None
    }

    /**
     * Moves the guided run on, if [trigger] is what the current step was waiting
     * for.
     *
     * Every board fact this needs arrives as a parameter. The callers are all
     * mid-transition — the placement that fired the auto-marks has not reached
     * `state` yet — and a step that read the board back off `state` would point
     * at the board as it was before the move that earned the step.
     */
    private suspend fun GameAction.advanceTutorial(
        trigger: TutorialTrigger,
        level: LevelDefinition,
        placed: Solution,
        autoMarks: Set<Int>,
        justMarked: Set<Int> = emptySet(),
    ) {
        val current = tutorialScript.getOrNull(tutorialIndex) ?: return
        if (Tutorial.triggerFor(current) != trigger) return

        tutorialIndex++
        val frame = resolveFrame(level, placed, autoMarks, justMarked)
        if (frame.step == null) {
            guidedLevels += level.id
            if (level.id == Tutorial.LAST_LEVEL) completeTutorial(skipped = false, at = current)
        }
        updateState { it.copy(tutorial = frame.step, tutorialCells = frame.cells) }
    }

    /** The coach mark's own dismissal, and the "Got it" button under it. */
    private suspend fun GameAction.tutorialTapped() {
        val level = state.level ?: return
        advanceTutorial(TutorialTrigger.Tap, level, state.placed, state.autoMarks)
    }

    /**
     * The way out, from any step.
     *
     * It clears the step *and* the script, so the scrim comes down and the board
     * is immediately playable. A skip that only hid the card would leave the
     * next trigger re-showing a lesson the player already refused.
     */
    private suspend fun GameAction.skipTutorial() {
        val at = tutorialScript.getOrNull(tutorialIndex)
        completeTutorial(skipped = true, at = at)
        updateState { it.copy(tutorial = null, tutorialCells = emptySet()) }
    }

    private suspend fun completeTutorial(skipped: Boolean, at: TutorialStep?) {
        tutorialActive = false
        tutorialScript = emptyList()
        tutorialIndex = 0
        freeMistakeAvailable = false
        logger.logEvent(
            "tutorial.completed",
            "skipped" to skipped,
            "last_step" to (at?.name ?: "none"),
        )
        Catching { appCache.update { it.copy(hasCompletedTutorial = true) } }
            .logOnFailure { "Failed to record the tutorial as finished" }
    }

    /**
     * How far the campaign has opened, given a level that is now on screen.
     *
     * A daily board's id says nothing about campaign progress — daily level 7 is
     * not campaign level 7 — so it may never widen the frontier the drawer
     * unlocks against.
     */
    private fun campaignFrontier(unlocked: Int, currentLevelId: Int): Int =
        if (isDaily) unlocked else maxOf(unlocked, currentLevelId)

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
    /**
     * A single tap on a square that is not a dog.
     *
     * Everything a player can put on the board they can take back off, with two
     * exceptions they cannot: a placed dog, and the red square a wrong guess
     * left behind. Both were paid for — one with a correct answer, one with a
     * bone — and letting either be tidied away would let the board forget
     * something the player is supposed to carry.
     *
     * An auto-mark is neither. It is a convenience the game derived, and a
     * square the player taps expecting a cross to come off should have the cross
     * come off. It goes into [GameState.clearedMarks] rather than out of
     * [GameState.autoMarks], because auto-marks are recomputed from the
     * placements on every move and anything removed from them would come
     * straight back.
     */
    private suspend fun GameAction.toggleMark(cell: Int) {
        // Paid for with a bone. It stays.
        if (cell in state.wrongGuesses) return
        if (cell in state.autoMarks) {
            updateBoard {
                it.copy(
                    clearedMarks = if (cell in it.clearedMarks) {
                        it.clearedMarks - cell
                    } else {
                        it.clearedMarks + cell
                    },
                )
            }
            return
        }
        val level = state.level
        val placed = state.placed
        val marks = state.autoMarks
        sendEvent(GameEvent.Marked(cell))
        updateBoard {
            it.copy(
                manualMarks = if (cell in it.manualMarks) {
                    it.manualMarks - cell
                } else {
                    it.manualMarks + cell
                },
            )
        }
        if (level != null) advanceTutorial(TutorialTrigger.Marked, level, placed, marks)
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

        val scored = Scoring.placement(state.score, level.size, since, scoringConfig())
        val placed = state.placed.withPlacement(row, level.board.colOf(cell))
        val marksBefore = state.autoMarks
        val marks = level.board.autoMarkedCells(placed)

        updateBoard {
            it.copy(
                placed = placed,
                autoMarks = marks,
                manualMarks = it.manualMarks - cell,
                score = scored.card,
                lastPoints = scored.points,
                lastPraise = scored.praise,
                pointsNonce = it.pointsNonce + 1,
            )
        }
        sendEvent(GameEvent.PlacedDog(cell))

        // Before the win check, so a placement that both finishes the lesson and
        // finishes the board leaves the coach mark behind rather than under the
        // outcome sheet.
        advanceTutorial(
            TutorialTrigger.Placed,
            level,
            placed,
            marks,
            justMarked = marks - marksBefore,
        )

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
        // The guided level asks the player to get one wrong on purpose, so that
        // one is on the house. Everything else about a strike still happens:
        // the square goes red, the combo breaks, and the lesson that follows
        // says what it would normally have cost.
        val forgiven = freeMistakeAvailable
        freeMistakeAvailable = false
        val level = state.level
        val placed = state.placed
        val marks = state.autoMarks
        val remaining = if (forgiven) state.livesRemaining else state.livesRemaining - 1
        updateBoard {
            it.copy(
                score = Scoring.strike(it.score),
                livesRemaining = remaining,
                wrongGuesses = it.wrongGuesses + cell,
                strikeCell = cell,
                strikeNonce = it.strikeNonce + 1,
            )
        }
        sendEvent(GameEvent.Struck(cell))
        if (level != null) advanceTutorial(TutorialTrigger.Struck, level, placed, marks)
        when {
            remaining <= 0 -> lose(remaining)
            remaining == 1 && !warnedAboutLastBone -> {
                warnedAboutLastBone = true
                updateBoard { it.copy(warning = GameWarning.LastBone) }
            }
        }
    }

    private suspend fun GameAction.win(level: LevelDefinition, earned: ScoreCard) {
        // One resolve for the whole finish. The paw rating compares the score
        // against a par derived from these same numbers, so asking twice could
        // rate a run against coefficients it was never played under.
        val scoring = scoringConfig()
        val finished = Scoring.complete(
            earned,
            level.size,
            level.difficulty,
            state.livesRemaining,
            scoring,
        )
        val paws = Scoring.paws(
            finished.total,
            level.size,
            level.difficulty,
            completed = true,
            config = scoring,
        )
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
            "mode" to modeName,
        )
        val streak = if (isDaily) {
            recordDailyClear(finished.total, paws, duration)
        } else {
            // Every metric here is a *best*, not a last: the repository keeps the
            // better of what it holds and what this attempt scored, so a replay
            // can never cost the player a three-paw clear. It also opens the next
            // level, which is why nothing else writes an unlock — and why a daily
            // must not come through here at all.
            Catching { progress.onCompleted(level.id, finished.total, paws, duration) }
                .logOnFailure { "Failed to record the clear of level ${level.id}" }
            NoStreak
        }
        val unlocked = unlockedThrough()
        val earnedBadges = recordAttempt(
            level,
            finished,
            paws,
            duration,
            completed = true,
            livesRemaining = state.livesRemaining,
            dailyStreakDays = streak,
        )

        sendEvent(GameEvent.Won)
        updateBoard {
            it.copy(
                phase = GamePhase.Won,
                score = finished,
                paws = paws,
                elapsedMs = duration,
                unlockedThrough = maxOf(it.unlockedThrough, unlocked),
                newBadges = earnedBadges,
                dailyStreak = streak,
            )
        }
    }

    /**
     * Locks in today's result and reports the streak it leaves behind.
     *
     * The streak is re-read *after* the write and handed back as a value, rather
     * than pulled off `state.daily` later: the observed status arrives on its own
     * dispatch, so the win sheet would otherwise show the streak as it stood
     * before the clear that produced it.
     */
    private suspend fun recordDailyClear(score: Int, paws: Int, duration: Long): Int {
        val date = dailyDate ?: return NoStreak
        Catching { daily.onCompleted(date, score, paws, duration) }
            .logOnFailure { "Failed to record the daily clear for $date" }
        val streak = currentStreak()
        logger.logEvent("daily.completed", "date" to date.toString(), "streak" to streak, "score" to score)
        return streak
    }

    private suspend fun currentStreak(): Int = Catching { daily.status().streak }
        .logOnFailure { "Failed to read the daily streak" }
        .getOrNull()
        ?: NoStreak

    /**
     * Hands the finished attempt to the achievement log and reports what it
     * unlocked.
     *
     * Failed attempts are recorded too. A run that ended on the last bone is
     * still evidence about how the player plays, and some badges are about
     * persistence rather than success — dropping the losses would make the log
     * a record of wins, which is a different and much less useful thing.
     *
     * Never throws: a badge is a garnish, and no failure here may cost someone
     * the level they just cleared.
     */
    private suspend fun recordAttempt(
        level: LevelDefinition,
        card: ScoreCard,
        paws: Int,
        duration: Long,
        completed: Boolean,
        livesRemaining: Int,
        dailyStreakDays: Int,
    ): List<Achievement> {
        val now = wallClock.now()
        val result = LevelResult(
            levelId = level.id,
            mode = if (isDaily) PlayMode.Daily else PlayMode.Campaign,
            size = level.size,
            completed = completed,
            score = card.total,
            paws = paws,
            timeMs = duration,
            strikes = ScoringConfig.MAX_LIVES - livesRemaining,
            bestCombo = card.bestCombo,
            sniffsUsed = sniffsUsed,
            treatsUsed = treatsUsed,
            isFirstClear = completed && recordBeforeAttempt.state != LevelState.Completed,
            previousBestPaws = recordBeforeAttempt.bestPaws,
            dailyStreakDays = dailyStreakDays,
            localHour = now.toLocalDateTime(deviceTimeZone.current()).hour,
            finishedAt = now.toEpochMilliseconds(),
        )
        val earned = Catching { achievements.record(result) }
            .logOnFailure { "Failed to record the attempt at level ${level.id}" }
            .getOrNull()
            .orEmpty()
        // The log is written either way. `features.achievements` hides the
        // badges, and a dark launch that also stopped recording would hand
        // everyone an empty grid on the day it was switched back on — the same
        // reason the Settings toggle is display-only.
        return if (achievementsEnabled()) earned else emptyList()
    }

    /**
     * [livesRemaining] is passed in rather than read back off `state`. The
     * caller's `updateState` has not landed yet — `state` reads a derived flow
     * that lags it by a dispatch — so reading it here reported one strike fewer
     * than the player actually took, which the achievement log then believed.
     */
    private suspend fun GameAction.lose(livesRemaining: Int) {
        val level = state.level ?: return
        logger.logEvent(
            "game.level_failed",
            "level_id" to level.id,
            "duration_ms" to elapsedMs(),
            "dogs_placed" to state.placed.placedCount,
            "attempt_number" to attemptNumber,
            "mode" to modeName,
        )
        val duration = elapsedMs()
        // Read, not written. A lost daily is not spent here: the player can still
        // trade an ad for the board back, and `daily_result` takes one row per
        // day, so writing the failure now would lock a loss over a clear they
        // went on to earn. The day is spent by [leave] instead.
        //
        // The streak itself is unaffected either way — a run through yesterday
        // stands all day today, including after today has been played and lost.
        val streak = if (isDaily) currentStreak() else NoStreak
        val earnedBadges = recordAttempt(
            level,
            Scoring.strike(state.score),
            paws = 0,
            duration,
            completed = false,
            livesRemaining = livesRemaining,
            dailyStreakDays = streak,
        )
        updateBoard {
            it.copy(
                phase = GamePhase.Lost,
                elapsedMs = duration,
                newBadges = earnedBadges,
                dailyStreak = streak,
            )
        }
    }

    /**
     * Leaving, and the moment a lost daily becomes a spent one.
     *
     * Walking away from the loss sheet is the player declining the revive, and it
     * is the only point at which a failed daily is final. Force-quitting there
     * instead leaves the day open, which is a loophole and a deliberate one: the
     * daily is device-local with no leaderboard, and nothing else in it tries to
     * stop someone cheating themselves either.
     */
    private suspend fun GameAction.leave() {
        val date = dailyDate
        if (isDaily && state.phase == GamePhase.Lost && date != null) {
            Catching { daily.onFailed(date, elapsedMs()) }
                .logOnFailure { "Failed to record the daily loss for $date" }
        }
        sendEvent(GameEvent.NavigateBack)
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
        updateBoard { it.copy(phase = GamePhase.Playing, livesRemaining = 1) }
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
        updateBoard {
            it.copy(
                phase = GamePhase.Playing,
                // Never downward, matching `refill`: a player holding more than
                // the floor should not be punished for watching an ad.
                livesRemaining = maxOf(it.livesRemaining, ScoringConfig.MAX_LIVES),
            )
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
        // There is no next daily. Tomorrow's board is tomorrow's.
        val next = if (isDaily) null else LevelPacks.campaign.byId(current.id + 1)
        if (next == null) {
            // Clearing level 500 used to close the app: `NavigateBack` pops the
            // start destination, and the start destination is the board. The
            // last thing a player who finished the campaign should get is the
            // home screen disappearing.
            if (!isDaily && current.id >= LevelPacks.lastCampaignLevelId) {
                updateState { it.copy(campaignComplete = true, phase = GamePhase.Won) }
                return
            }
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
                unlockedThrough = campaignFrontier(
                    unlocked,
                    it.level?.id ?: LevelRecord.FIRST_LEVEL_ID,
                ),
                // Opened together with the data, in one update. Flipping the flag
                // first would show a pane of locked rows for a frame while the
                // records were still loading.
                drawerOpen = true,
            )
        }
    }

    private suspend fun GameAction.goToLevel(levelId: Int) {
        val target = LevelPacks.campaign.byId(levelId) ?: return
        if (!entitlements.isPro.value && levelId > state.unlockedThrough) return
        updateState { it.copy(drawerOpen = false) }
        // Crossing packs cannot be a swap. Which pack this ViewModel plays is
        // fixed at construction because the route is what a process death
        // restores, so leaving the daily means a new route.
        if (isDaily) {
            sendEvent(GameEvent.OpenLevel(levelId))
            return
        }
        // Picking the level you are already on is a way of closing the pane, not
        // a request to start over. Restarting here threw away every mark and
        // placement of an attempt in progress, which is what a player reported
        // after opening the pane mid-puzzle and tapping the row they were on.
        if (levelId == state.level?.id && state.phase == GamePhase.Playing) return
        attemptNumber = 1
        startAttempt(target)
    }

    /** Opens today's board on its own route, from the card in the drawer. */
    private suspend fun GameAction.playDaily() {
        val status = state.daily ?: return
        if (!status.playable) return
        updateState { it.copy(drawerOpen = false) }
        logger.logEvent("daily.started", "date" to status.date.toString(), "streak" to status.streak)
        sendEvent(GameEvent.OpenDaily(status.levelId))
    }

    /**
     * Trades an ad for a missed day, and says what happened either way.
     *
     * The repository shows the ad and owns the monthly cap, so this only routes
     * the answer. Every branch surfaces something: a freeze that silently does
     * nothing is indistinguishable from a crash, and [FreezeResult.Declined] —
     * the player closing the ad early — is the branch most likely to be read as
     * one.
     */
    private suspend fun GameAction.useFreeze() {
        val result = Catching { daily.useFreeze() }
            .logOnFailure { "Failed to use a streak freeze" }
            .getOrNull()
        val message = when (result) {
            is FreezeResult.Applied -> {
                logger.logEvent("daily.freeze_used", "streak" to result.streak)
                FreezeMessage.Applied(result.streak)
            }
            FreezeResult.Declined -> FreezeMessage.Declined
            FreezeResult.NoneLeft -> FreezeMessage.NoneLeft
            FreezeResult.NothingToFreeze -> FreezeMessage.NothingToFreeze
            null -> FreezeMessage.Unavailable
        }
        updateState { it.copy(freezeMessage = message) }
    }

    private suspend fun GameAction.restart() {
        val level = state.level ?: return
        // One attempt per day. Starting over would be a second run at a board
        // whose score is already committed to a date.
        if (isDaily) return
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
     *
     * `features.boosters` is read here rather than trusted from [GameState], so
     * a switch thrown mid-session stops the economy on the next tap instead of
     * on the next board. Bones are exempt: three strikes is a game rule, and
     * this path is only their explainer.
     */
    private suspend fun GameAction.boosterTapped(consumable: Consumable) {
        if (state.phase != GamePhase.Playing) return
        if (consumable != Consumable.Bone && !boostersEnabled()) return
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
     * Tops the consumable back up to `boosters.refillTo` for an ad.
     *
     * Never *reduces* a holding: a player who earned five treats from level
     * rewards and watches an ad should not be punished down to three.
     */
    private suspend fun GameAction.refill(consumable: Consumable) {
        if (consumable != Consumable.Bone && !boostersEnabled()) return
        markExplained(consumable)
        val granted = entitlements.isPro.value ||
            adGate.showRewarded(AdPlacement.BoosterGrant) != RewardOutcome.Dismissed
        if (!granted) {
            updateState { it.copy(boosterPrompt = null) }
            return
        }

        val topped = maxOf(countOf(consumable), refillTo())
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
        // `manualMarks` belongs here as much as the rest. Without it a player who
        // crosses squares off by hand can spend a sniff and be shown the very
        // squares they already reasoned out — which is a charge taken for
        // nothing, and it lands on exactly the careful player manual marking was
        // built for.
        val known = state.autoMarks + state.manualMarks + state.placedCells + state.wrongGuesses
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
        sniffsUsed++
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
        treatsUsed++
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

    /**
     * `updateState`, plus a write of whatever it just produced.
     *
     * The new state is captured inside the transform rather than read back
     * afterwards. `state` is a derived flow that lags the source of truth by a
     * dispatch, so a save that read it back would persist the board as it was
     * one move ago — which is the exact failure this is here to prevent, and the
     * fifth time that lag has bitten in this file.
     *
     * Used for anything that changes what is on the board or ends the attempt.
     * A timer tick or a dialog opening goes through plain `updateState`, because
     * writing the file every second buys nothing.
     */
    private suspend fun GameAction.updateBoard(f: (GameState) -> GameState) {
        var next: GameState? = null
        updateState { current -> f(current).also { next = it } }
        next?.let { saveBoard(it) }
    }

    private fun elapsedMs(): Long =
        elapsedBeforeResume + attemptStartedAt.elapsedNow().inWholeMilliseconds

    /**
     * Writes the attempt to disk, or clears the slot when there is nothing worth
     * coming back to.
     *
     * Called after every move rather than on a lifecycle callback. A crash and a
     * force-quit both skip `onStop`, and force-quitting mid-puzzle is the first
     * thing a motivated player tries — so the only save that can be relied on is
     * the one that already happened.
     *
     * Never throws. Losing a snapshot costs a resume; letting the write take down
     * the placement that triggered it costs the game.
     */
    private suspend fun saveBoard(state: GameState) {
        val level = state.level
        val snapshot = if (level == null || state.phase != GamePhase.Playing) {
            null
        } else {
            BoardSnapshot(
                levelId = level.id,
                isDaily = isDaily,
                placements = state.placed.columnByRow.toList(),
                manualMarks = state.manualMarks,
                wrongGuesses = state.wrongGuesses,
                livesRemaining = state.livesRemaining,
                score = state.score.total,
                combo = state.score.combo,
                bestCombo = state.score.bestCombo,
                placementCount = state.score.placements,
                elapsedMs = elapsedMs(),
                attemptNumber = attemptNumber,
                sniffsUsed = sniffsUsed,
                treatsUsed = treatsUsed,
            ).takeUnless { it.isEmpty }
        }
        Catching {
            appCache.update { data ->
                // Only ever write over this board's own slot. Opening a fresh
                // level produces a `null` snapshot on its first frame, and
                // writing that unconditionally threw away the half-finished
                // level the player had left behind — which is the thing the
                // whole feature exists to keep.
                val held = data.boardInProgress
                val ours = held == null ||
                    (held.levelId == level?.id && held.isDaily == isDaily)
                if (ours) data.copy(boardInProgress = snapshot) else data
            }
        }.logOnFailure { "Failed to save the board" }
    }

    private val modeName: String get() = if (isDaily) "daily" else "campaign"

    private companion object {
        /** What a campaign attempt reports for a number only the daily has. */
        const val NoStreak = 0

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

    /** What an ad tops a booster up to, so the prompt's copy matches the tap. */
    val refillTo: Int = 3,

    /**
     * True once the last campaign level is cleared, so the win sheet can say so
     * instead of offering a next level that does not exist.
     */
    val campaignComplete: Boolean = false,

    /**
     * Auto-marks the player has tapped away.
     *
     * Held as an exclusion rather than by removing them from [autoMarks],
     * because auto-marks are recomputed from [placed] on every move — anything
     * taken out of that set would reappear on the next placement.
     */
    val clearedMarks: Set<Int> = emptySet(),

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

    /**
     * The Settings toggle for badges. Display only — `AchievementsRepository`
     * keeps recording either way, so this gates the toast and nothing else. A
     * player who turns them back on sees real history rather than a blank grid.
     */
    val showAchievements: Boolean = true,

    /**
     * `features.boosters`, for the row of Sniff and Treat buttons. True by
     * default so a board built before the flag is read — a preview, a test, a
     * first launch with no network — has the economy rather than losing it.
     * Bones are unaffected either way: three strikes is a game rule.
     */
    val boostersEnabled: Boolean = true,

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

    /**
     * The guided lesson on screen, or null when the player is on their own.
     * Drives the whole coach mark: the screen maps the step to a spotlight and
     * a piece of copy and renders nothing else of its own.
     */
    val tutorial: TutorialStep? = null,

    /**
     * The board squares [tutorial] is pointing at, empty for a step that points
     * at chrome instead. Held here rather than derived in the screen because
     * which square a lesson picks depends on the answer, and the screen has no
     * business knowing it.
     */
    val tutorialCells: Set<Int> = emptySet(),

    /** Squares a sniff has ruled out, spotlit until the player taps away. */
    val hintCells: Set<Int> = emptySet(),

    /**
     * Badges this attempt just unlocked, in catalog order. Empty is the normal
     * answer; the outcome sheet shows them and nothing else needs to.
     */
    val newBadges: List<Achievement> = emptyList(),

    /**
     * Whether the level pane is showing. In state rather than in the screen's
     * `remember` because everything the pane draws — [records], [unlockedThrough],
     * [isPro] — is loaded here, and a flag that lives apart from the data it
     * gates can be true while the data behind it is still empty.
     */
    val drawerOpen: Boolean = false,

    /**
     * Today's daily, or null before the first status arrives. One snapshot of the
     * clock: date, board, streak, freeze offer and reset countdown all agree with
     * each other, and the card does no date arithmetic of its own.
     */
    val daily: DailyStatus? = null,

    /** Whether *this* board is the daily, rather than a campaign level. */
    val isDaily: Boolean = false,

    /**
     * The streak as of this attempt, for the outcome sheet. Set from the value
     * the write returned rather than read back off [daily], which arrives on its
     * own dispatch and would show the streak from before the clear.
     */
    val dailyStreak: Int = 0,

    /** The answer to a freeze the player just asked for. */
    val freezeMessage: FreezeMessage? = null,
) {
    val placedCells: Set<Int> get() = placed.cells().toSet()

    val dogsPlaced: Int get() = placed.placedCount

    val dogsRequired: Int get() = level?.size ?: 0
}

/**
 * What came of a streak freeze. Every [FreezeResult] maps to one of these, plus
 * [Unavailable] for a repository call that threw — five answers, five things the
 * player can be told, and no silent branch.
 */
sealed interface FreezeMessage {
    data class Applied(val streak: Int) : FreezeMessage
    data object Declined : FreezeMessage
    data object NoneLeft : FreezeMessage
    data object NothingToFreeze : FreezeMessage
    data object Unavailable : FreezeMessage
}

sealed interface GameEvent {
    data object NavigateBack : GameEvent

    /** Today's daily, on its own route, from the card in the drawer. */
    data class OpenDaily(val levelId: Int) : GameEvent

    /** A campaign level picked from the drawer of a board in the other pack. */
    data class OpenLevel(val levelId: Int) : GameEvent

    /** For sound and haptics; the cell animates itself. */
    data class PlacedDog(val cell: Int) : GameEvent

    data class Marked(val cell: Int) : GameEvent

    data object Won : GameEvent
    data object OpenSettings : GameEvent
    data object OpenPrivacy : GameEvent
    data object OpenTerms : GameEvent
    data object OpenFeedback : GameEvent

    data class Struck(val cell: Int) : GameEvent
}

/**
 * The settings the board renders from.
 *
 * A value class rather than three parameters so the flow can be
 * `distinctUntilChanged` on it — otherwise every unrelated `AppData` write, and
 * this ViewModel makes several per move, would re-dispatch an action.
 */
data class DisplaySettings(
    val colorblind: Boolean,
    val haptics: Boolean,
    val reduceAnimations: Boolean,
)

/** Something the game wants to stop and point at. */
enum class GameWarning { LastBone }

/** What the coach mark is showing, and where it is pointing. */
data class TutorialFrame(val step: TutorialStep?, val cells: Set<Int>) {
    companion object {
        val None = TutorialFrame(step = null, cells = emptySet())
    }
}

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

    data object LevelsClosed : GameAction
    data class GoToLevel(val levelId: Int) : GameAction

    /** A new daily snapshot: a result was written, or the local date rolled over. */
    data class DailyChanged(val status: DailyStatus) : GameAction

    data object PlayDaily : GameAction
    data object UseFreeze : GameAction
    data object DismissFreezeMessage : GameAction
    data object OpenSettings : GameAction

    /** The three settings that change what is on the board, as they change. */
    data class DisplaySettingsChanged(val settings: DisplaySettings) : GameAction
    data object OpenPrivacy : GameAction
    data object OpenTerms : GameAction
    data object OpenFeedback : GameAction

    /** The coach mark was tapped, or its button was. Only moves a `Tap` step. */
    data object TutorialAdvance : GameAction

    /** Out of the guided run for good, from whichever step is showing. */
    data object SkipTutorial : GameAction

    data class TimerTick(val at: Long) : GameAction
}
