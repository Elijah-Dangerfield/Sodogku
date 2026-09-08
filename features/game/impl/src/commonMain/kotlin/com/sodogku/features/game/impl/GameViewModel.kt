package com.sodogku.features.game.impl

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.config.values.BoostersProSniffsPerAttempt
import com.sodogku.libraries.config.values.BoostersProTreatsPerAttempt
import com.sodogku.libraries.config.values.BoostersRefillTo
import com.sodogku.libraries.config.values.BoostersStartingSniffs
import com.sodogku.libraries.config.values.BoostersStartingTreats
import com.sodogku.libraries.config.values.BoostersTreatSchedule
import com.sodogku.libraries.config.values.FeatureAchievements
import com.sodogku.libraries.config.values.FeatureBoosters
import com.sodogku.libraries.config.values.ProgressionSkipAfterFailedAttempts
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
import com.sodogku.libraries.progress.LifetimeScore
import com.sodogku.libraries.progress.daily.DailyRepository
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.daily.FreezeResult
import com.sodogku.libraries.progress.daily.RestoreResult
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.progress.SkipRepository
import com.sodogku.libraries.progress.SkipResult
import com.sodogku.libraries.puzzle.HintFinder
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.puzzle.autoMarkedCells
import com.sodogku.libraries.scoring.Praise
import com.sodogku.libraries.scoring.ScoreCard
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.ScoringConfig
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.sodogku.AppEvents
import com.sodogku.libraries.sodogku.BoardSnapshot
import com.sodogku.libraries.sodogku.ConsumableRefillTo
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject


/**
 * What one qualifying first clear pays.
 *
 * One, because `boosters.treatSchedule` already tunes how generous the
 * ladder is, and two dials for one number is how the two end up disagreeing.
 * File-level rather than in the ViewModel's companion because the level pane
 * prints it, and a pane advertising a different number from the one the game
 * pays is precisely the bug this reward exists to fix.
 */
internal const val LevelRewardTreats: Int = 1

/**
 * The lifetime total split in two: everything banked, and the slice of it this
 * board already holds. Kept apart so an attempt can *replace* its board's
 * contribution rather than stack on it.
 */
private data class BankedScore(val lifetime: Int, val thisBoard: Int)

/**
 * One read of the daily repository, carrying everything the route needs: which
 * board today is, which date it will be recorded against, and whether the day
 * has already been spent. Held together so the board and the date can never come
 * from two sides of midnight.
 */
private data class DailyBoard(
    val level: LevelDefinition,
    val date: LocalDate,
    /** Non-null once the day is played, cleared or given up on. */
    val result: DailyResult?,
    val streak: Int,
)

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
    /** The daily skip allowance, the ad behind it, and the write. */
    private val skips: SkipRepository,
    private val daily: DailyRepository,
    private val achievements: AchievementsRepository,
    private val streak: StreakRepository,
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
    /** How often a first clear pays a Treat. Zero or less pays none. */
    private val treatSchedule: BoostersTreatSchedule,
    private val proSniffsPerAttempt: BoostersProSniffsPerAttempt,
    private val proTreatsPerAttempt: BoostersProTreatsPerAttempt,
    private val skipAfterFailedAttempts: ProgressionSkipAfterFailedAttempts,
    private val achievementsEnabled: FeatureAchievements,
    private val boostersEnabled: FeatureBoosters,
    /**
     * Foreground and background edges, which is the only thing on this screen
     * that cares the app can go away. See [GameAction.VisibilityChanged].
     */
    private val appEvents: AppEvents,
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
     *
     * It carries the *foreground* pauses too. Backgrounding folds the run so far
     * into this and stops the clock; coming back re-marks [attemptStartedAt]. A
     * process death was never the only way the app goes away, and the monotonic
     * source keeps running through the other one.
     */
    private var elapsedBeforeResume = 0L

    /**
     * True while the app is in the background, so [elapsedMs] reads the
     * accumulator alone.
     *
     * A flag rather than a nullable [attemptStartedAt]: the mark is also the
     * baseline for the speed bonus, and making it null for a background would
     * push that decision through every call site to answer a question it does
     * not have.
     */
    private var clockPaused = false
    private var lastTappedCell: Int? = null
    private var warnedAboutLastBone = false
    private var lastTapAt: ComparableTimeMark? = null

    /**
     * Whether the square was crossed off when the player started a double tap.
     *
     * A field rather than a read at the commit site, because the commit runs on
     * the *second* tap and the first one has already changed the answer. Same
     * shape as [lastTappedCell] and [lastTapAt]: state that belongs to a gesture
     * in progress rather than to the board.
     */
    private var markedBeforeFirstTap = false

    /**
     * Consumables spent *this attempt*. The holdings in state only ever say what
     * is left, and "cleared it without help" is a question about what was spent.
     * Reset by [startAttempt] rather than accumulated across retries.
     */
    private var sniffsUsed = 0
    private var treatsUsed = 0

    /**
     * Wrong guesses *this attempt*, which stopped being derivable from the bone
     * count the moment bones went global.
     *
     * `MAX_LIVES - livesRemaining` used to answer this. It cannot any more: the
     * holding crosses boards and an ad refill moves it, so a player who topped
     * up mid-level would finish reading as a clean sheet — worth a bigger
     * completion bonus and a Perfect Form badge they did not earn. Held in a
     * field rather than read back off `state`, for the reason everything else in
     * this file is.
     */
    private var strikesThisAttempt = 0

    /**
     * The guided run over levels 1 to 3.
     *
     * All of this is held in fields rather than read back off [state], which
     * lags `updateState` by a dispatch — a step that advanced off a stale
     * pointer would show the same coach mark twice and then skip one.
     */
    private val tutorial = TutorialRunner(logger)

    /**
     * True while [TutorialBoard] is on screen instead of a real level.
     *
     * It is the one switch that makes the rehearsal a rehearsal: no attempt is
     * recorded, no bone is spent, no snapshot is written, nothing is banked and
     * no `game.*` event fires. Held in a field rather than read back off
     * [state], for the reason everything else in this file is — [startAttempt]
     * has to know before its own `updateState` lands, because that update saves
     * the board.
     */
    private var rehearsing = false

    /**
     * `AppData.autoMarkEnabled`, mirrored into [GameState.autoMarkVisible] for
     * the screen.
     *
     * Held in a field as well because [startAttempt] needs it *before* its own
     * `updateState` lands: `tutorial.begin` picks the curriculum from it and
     * `tutorial.openingFrame` needs the marks the player can see, and `state`
     * is a derived flow that lags `updateState` by a dispatch. Same reason
     * [rehearsing] is a field.
     *
     * It changes **nothing about the deduction**. [GameState.autoMarks] is
     * computed from the placements either way; this only decides whether the
     * board draws them.
     */
    private var autoMark = true

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
        // Only the four that change what is on screen. The consumable counts
        // are deliberately absent: this ViewModel is their writer, and echoing
        // its own writes back in would fight the spend it just made.
        appCache.updates
            .map {
                DisplaySettings(
                    it.colorblindMode,
                    it.hapticsEnabled,
                    it.reduceAnimations,
                    it.autoMarkEnabled,
                )
            }
            .distinctUntilChanged()
            .onEach { display ->
                takeAction(GameAction.DisplaySettingsChanged(display))
            }
            .launchIn(viewModelScope)
        // Bones are the exception to the paragraph above, and they have to be.
        // The daily opens on its own route, so a campaign board sits on the
        // backstack while it is played — two live ViewModels over one economy.
        // Without this the backstacked board keeps the count it had when it was
        // left, and its next strike writes that stale number back over whatever
        // the daily spent. Echoing our own write costs nothing: `persistCounts`
        // stores the value state already holds, so the round trip is a no-op.
        appCache.updates
            .map { it.bones }
            .distinctUntilChanged()
            .onEach { bones -> takeAction(GameAction.BonesChanged(bones)) }
            .launchIn(viewModelScope)
        // The card lives in the drawer of every board, daily or not, and the
        // repository re-emits at local midnight — so a drawer left open past
        // midnight picks up the new board without this screen watching a clock.
        daily.observe().collectIn(viewModelScope) { takeAction(GameAction.DailyChanged(it)) }
        // `live()`, not the replaying stream. These are edges: a board opened
        // seconds after a foreground would otherwise be handed that foreground
        // as its first event, and the daily opens on its own route over a
        // campaign board, so two ViewModels would each replay it.
        appEvents.live().collectIn(viewModelScope) { event ->
            when (event) {
                is AppEvent.OnForeground -> takeAction(GameAction.VisibilityChanged(true))
                is AppEvent.OnBackground -> takeAction(GameAction.VisibilityChanged(false))
                else -> Unit
            }
        }
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
            GameAction.Leave -> sendEvent(GameEvent.NavigateBack)
            GameAction.DismissWarning -> action.updateState {
                it.copy(warning = null, hintCells = emptySet())
            }
            GameAction.ApplyHint -> action.applyHint()
            GameAction.DiscardHint -> action.updateState { it.copy(hintCells = emptySet()) }
            GameAction.RefillBones -> action.refillBones()
            is GameAction.BonesChanged -> action.updateState {
                it.copy(livesRemaining = action.bones)
            }
            GameAction.ForfeitDailyRequested -> action.updateState { it.copy(forfeitPrompt = true) }
            GameAction.ForfeitDailyConfirmed -> action.forfeitDaily()
            GameAction.DismissForfeitPrompt -> action.updateState { it.copy(forfeitPrompt = false) }
            GameAction.SkipLevel -> action.skipLevel()
            GameAction.NextLevel -> action.nextLevel()
            GameAction.LevelsOpened -> action.loadRecords()
            GameAction.LevelsClosed -> action.updateState { it.copy(drawerOpen = false) }
            is GameAction.GoToLevel -> action.goToLevel(action.levelId)
            is GameAction.DailyChanged -> action.updateState { it.copy(daily = action.status) }
            GameAction.PlayDaily -> action.playDaily()
            GameAction.DailyIntroDismissed -> action.dismissDailyIntro()
            GameAction.UseFreeze -> action.useFreeze()
            GameAction.RestoreStreak -> action.restoreStreak()
            // Zero, so the page opens still. The celebrating number is only ever
            // set by the ceremony path; a page the player asked for animates
            // nothing.
            GameAction.OpenStreak -> sendEvent(GameEvent.OpenStreak(streak = 0))
            GameAction.DismissFreezeMessage -> action.updateState { it.copy(freezeMessage = null) }
            GameAction.OpenPrivacy -> sendEvent(GameEvent.OpenPrivacy)
            GameAction.OpenTerms -> sendEvent(GameEvent.OpenTerms)
            GameAction.OpenFeedback -> sendEvent(GameEvent.OpenFeedback)
            GameAction.OpenSettings -> sendEvent(GameEvent.OpenSettings)
            GameAction.TutorialAdvance -> action.tutorialTapped()
            GameAction.SkipTutorial -> action.skipTutorial()
            is GameAction.VisibilityChanged -> holdClock(paused = !action.foreground)
            is GameAction.DisplaySettingsChanged -> {
                // The field and the state are written together, and the field
                // first: it is what the tutorial reads before an update lands.
                autoMark = action.settings.autoMark
                action.updateState {
                    it.copy(
                        colorblind = action.settings.colorblind,
                        haptics = action.settings.haptics,
                        reduceAnimations = action.settings.reduceAnimations,
                        autoMarkVisible = action.settings.autoMark,
                    )
                }
            }
        }
    }

    private suspend fun GameAction.load() {
        val settings = Catching { appCache.get() }
            .logOnFailure { "Failed to read game settings" }
            .getOrNull()
        // Into the field before the update, because `startAttempt` below reads
        // it and `state` does not carry this write until the next dispatch.
        autoMark = settings?.autoMarkEnabled != false
        updateState {
            it.copy(
                colorblind = settings?.colorblindMode == true,
                haptics = settings?.hapticsEnabled != false,
                autoMarkVisible = autoMark,
                reduceAnimations = settings?.reduceAnimations == true,
                showAchievements = settings?.achievementsVisible != false,
                isPro = entitlements.isPro.value,
                boostersEnabled = boostersEnabled(),
                refillTo = refillTo(),
                treatBands = treatSchedule(),
                // Null is "never granted any", which is what a fresh install
                // looks like — so the opening grant comes from config rather
                // than from a default baked into the record that stores it.
                sniffs = settings?.sniffs ?: startingSniffs(),
                treats = settings?.treats ?: startingTreats(),
                // Read, never granted. Bones are one count across every board,
                // so opening one is not an occasion to hand any out — that was
                // the whole of the bug, and `startAttempt` used to be where it
                // lived.
                livesRemaining = settings?.bones ?: ConsumableRefillTo,
                explainedBoosters = settings?.explainedBoosters
                    ?.mapNotNull { name -> Consumable.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                    .orEmpty(),
                // Once ever, and on the recap route as well as the play route:
                // a player whose first daily visit is a board they already
                // finished has the same question about it.
                showDailyIntro = isDaily && settings?.hasSeenDailyIntro != true,
            )
        }

        // Read here and held in a field, not re-read per board: the flag is
        // written the moment the tutorial ends, and `startAttempt` for the real
        // level runs before that write has any chance to land.
        tutorial.arm(
            hasCompletedTutorial = settings?.hasCompletedTutorial == true,
            onFirstLevel = !isDaily && levelId == LevelRecord.FIRST_LEVEL_ID,
        )

        if (isDaily) {
            loadDaily()
            return
        }
        val level = LevelPacks.campaign.byId(levelId)
        if (level == null) {
            logger.e { "No level $levelId in the $modeName pack" }
            sendEvent(GameEvent.NavigateBack)
            return
        }
        // The lesson happens on a board of its own, in front of the level the
        // route asked for. Nothing is navigated: the same screen swaps the board
        // under itself when the script ends, so a process death mid-lesson
        // restores the route the player actually meant to be on.
        if (tutorial.shouldRehearse) {
            startAttempt(TutorialBoard.level, rehearsal = true)
            return
        }
        startAttempt(level, resume = savedBoardFor(level))
    }

    /**
     * Puts the level the route asked for on screen, now that the lesson is over.
     *
     * **No resume.** The rehearsal wrote no snapshot of its own, and whatever
     * was in the slot belongs to a board the player left before they were ever
     * taught to play — handing them a half-finished level as their graduation is
     * not what "you know how to play, here is level one" means.
     */
    private suspend fun GameAction.leaveRehearsal() {
        rehearsing = false
        val level = LevelPacks.campaign.byId(levelId)
        if (level == null) {
            logger.e { "No level $levelId to hand the tutorial back to" }
            sendEvent(GameEvent.NavigateBack)
            return
        }
        startAttempt(level, resume = null)
    }

    /**
     * Today's board, or today's result if the day is already spent.
     *
     * A spent day used to resolve to null here and bounce the route straight
     * back, which is what put a player who tapped Levels on a lost daily into
     * the campaign with no way back in. One attempt per day is still the rule —
     * the repository refuses a second write regardless — but "you cannot play
     * this again" and "you cannot look at it again" are different sentences and
     * only the first one was meant.
     */
    private suspend fun GameAction.loadDaily() {
        val today = dailyBoard()
        if (today == null) {
            logger.e { "No daily board to open" }
            sendEvent(GameEvent.NavigateBack)
            return
        }
        dailyDate = today.date
        val spent = today.result
        if (spent != null) {
            showDailyRecap(today.level, spent, today.streak)
            return
        }
        startAttempt(today.level, resume = savedBoardFor(today.level))
    }

    /**
     * The daily board, resolved from the repository rather than from [levelId].
     *
     * The route's id came from a card that was drawn at some earlier moment, and
     * the board and the date the result is written against have to come from one
     * snapshot of the clock — otherwise a screen opened a second before midnight
     * records yesterday's board against today. Everything the caller needs comes
     * out of that one snapshot rather than from a second `status()` call.
     */
    private suspend fun dailyBoard(): DailyBoard? {
        val status = Catching { daily.status() }
            .logOnFailure { "Failed to read the daily status" }
            .getOrNull()
            ?: return null
        val level = LevelPacks.daily.byId(status.levelId) ?: return null
        return DailyBoard(level, status.date, status.result, status.streak)
    }

    /**
     * A day that has already been played, rendered rather than replayed.
     *
     * Plain `updateState`, not [updateBoard]: there is no attempt here and the
     * saved-board slot must not be touched. A day that was forfeited has already
     * had its snapshot cleared, and a day that was cleared never had one.
     */
    private suspend fun GameAction.showDailyRecap(
        level: LevelDefinition,
        result: DailyResult,
        streak: Int,
    ) {
        val banked = bankedScores(level)
        logger.logEvent(
            "daily.reviewed",
            "date" to result.date.toString(),
            "outcome" to result.outcome.name,
        )
        updateState {
            it.copy(
                level = level,
                placed = Solution.empty(level.size),
                autoMarks = emptySet(),
                phase = GamePhase.Recap,
                isDaily = true,
                dailyRecap = result,
                dailyStreak = streak,
                paws = result.paws,
                elapsedMs = result.timeMs,
                lifetimeBanked = banked.lifetime,
                bankedForThisBoard = banked.thisBoard,
            )
        }
    }

    /**
     * The stored attempt at [level], if it is this one's.
     *
     * A snapshot of some *other* level is left alone rather than discarded — the
     * player may well go back to it, and opening a different board is not a
     * decision about that one.
     *
     * The size check is not paranoia about the pack: it is what stops a snapshot
     * written before a level was regenerated from being loaded onto a board with
     * a different number of rows.
     */
    private suspend fun savedBoardFor(level: LevelDefinition): BoardSnapshot? =
        Catching { appCache.get().boardInProgress }
            .logOnFailure { "Failed to read the saved board" }
            .getOrNull()
            ?.takeIf { it.levelId == level.id && it.isDaily == isDaily }
            ?.takeIf { it.placements.size == level.size }

    private suspend fun GameAction.startAttempt(
        level: LevelDefinition,
        resume: BoardSnapshot? = null,
        /**
         * True only for [TutorialBoard]. Set before anything else here, because
         * the `updateBoard` at the bottom writes a snapshot and a rehearsal must
         * not hold the one in-progress slot against a real board.
         */
        rehearsal: Boolean = false,
    ) {
        rehearsing = rehearsal
        attemptStartedAt = clock.markNow()
        lastPlacementAt = attemptStartedAt
        lastTappedCell = null
        lastTapAt = null
        warnedAboutLastBone = false
        sniffsUsed = resume?.sniffsUsed ?: 0
        treatsUsed = resume?.treatsUsed ?: 0
        strikesThisAttempt = resume?.strikesTaken ?: 0
        elapsedBeforeResume = resume?.elapsedMs ?: 0L
        attemptNumber = resume?.attemptNumber ?: attemptNumber
        // Campaign progress is keyed on level id alone, and the two packs share
        // that number line, so every read and every write here is skipped for a
        // daily. Reading campaign level 7's record for daily level 7 would be
        // wrong quietly; writing it would hand out a campaign unlock.
        recordBeforeAttempt = if (isDaily || rehearsal) {
            LevelRecord.unplayed(level.id)
        } else {
            Catching { progress.record(level.id) }
                .logOnFailure { "Failed to read the prior record for level ${level.id}" }
                .getOrNull()
                ?: LevelRecord.unplayed(level.id)
        }
        // The rehearsal emits no `game.*` events at all. Its board id is not a
        // level id, so a `game.level_started` for it would land in the campaign
        // funnel as a level nobody can play and a difficulty tier nobody
        // generated. What the tutorial has to say, it says through
        // `tutorial.step_viewed` and `tutorial.completed`.
        if (!rehearsal) {
            logger.logEvent(
                "game.level_started",
                "level_id" to level.id,
                "size" to level.size,
                "difficulty" to level.difficulty,
                "attempt_number" to attemptNumber,
                "mode" to modeName,
                // On every board event, because it splits the funnel rather
                // than describing one attempt. Everything downstream — clear
                // rate, time, score, retries — is a comparison between the
                // players who kept the crosses and the players who took the
                // bookkeeping back, and a segment that only exists on the
                // completion event cannot answer the retention question that
                // motivated it.
                "auto_mark" to autoMark,
            )
        }
        // Recorded when the level opens rather than when it is cleared: an
        // abandoned attempt still happened, and it is what unlocks the level's
        // own row so `unlockedThrough` can see it.
        if (!isDaily && !rehearsal) {
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
        // re-granting it would place a second dog in row 0. The rehearsal board
        // always gets one — every rule lesson is read off it.
        val giveStarter = resume == null && (rehearsal || level.id <= StarterDogThroughLevel)
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

        if (rehearsal) tutorial.begin(autoMark)
        // The frame is handed what the player can see, not the cascade: a
        // lesson points at squares on screen. `clearedMarks` is empty on a
        // board that is only now opening, so this is the whole of
        // `visibleAutoMarks` for it.
        val lesson = tutorial.openingFrame(
            level,
            opening,
            if (autoMark) openingMarks else emptySet(),
        )
        grantProBoosters()
        val banked = bankedScores(level)

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
                // Carried, not granted. This is the line the whole of R15 turns
                // on: it used to read `ScoringConfig.MAX_LIVES`, so every start
                // of every board — a retry, the next level, a jump from the
                // pane, the daily — handed out a free set of three.
                livesRemaining = it.livesRemaining,
                strikesThisAttempt = strikesThisAttempt,
                // A board opened with nothing to spend meets the offer straight
                // away rather than on the guess that ends it. The prompt's
                // refill goes through the same fail-open ad path as every other
                // one, so this is a wall with a door in it and never a lock.
                boosterPrompt = if (it.livesRemaining <= 0) Consumable.Bone else null,
                score = resume?.let { saved ->
                    ScoreCard(
                        total = saved.score,
                        combo = saved.combo,
                        bestCombo = saved.bestCombo,
                        placements = saved.placementCount,
                    )
                } ?: ScoreCard.Empty,
                elapsedMs = resume?.elapsedMs ?: 0L,
                lifetimeBanked = banked.lifetime,
                bankedForThisBoard = banked.thisBoard,
                // A resumed attempt carries the boosters it already spent, so
                // backgrounding a board cannot launder the help it took.
                boostersUsed = sniffsUsed + treatsUsed,
                boosterPenaltyRate = scoringConfig().boosterPenaltyRate,
                sniffs = it.sniffs,
                treats = it.treats,
                explainedBoosters = it.explainedBoosters,
                colorblind = it.colorblind,
                haptics = it.haptics,
                reduceAnimations = it.reduceAnimations,
                autoMarkVisible = it.autoMarkVisible,
                showAchievements = it.showAchievements,
                boostersEnabled = it.boostersEnabled,
                refillTo = it.refillTo,
                treatBands = it.treatBands,
                isPro = it.isPro,
                records = it.records,
                unlockedThrough = campaignFrontier(unlocked, level.id),
                daily = it.daily,
                isDaily = isDaily,
                // Carried, because this builds a fresh GameState rather than
                // copying one: anything not named here is silently reset. The
                // explainer is set in `load` and would otherwise vanish before
                // the first frame, and come back on the next retry.
                showDailyIntro = it.showDailyIntro,
                isRehearsal = rehearsal,
                tutorial = lesson.step,
                tutorialCells = lesson.cells,
            )
        }
    }

    /**
     * Pro's opening boosters, which **lift a holding to a floor and never
     * replace it** (SPEC 5.1).
     *
     * The other reading — set the count to `boosters.proSniffsPerAttempt` at the
     * start of every attempt — is the one that has to be argued against, because
     * "starts every attempt with 3" sounds like an assignment. It would take
     * boosters *away*: a Pro player holding nine Treats from level rewards would
     * open their next board with three and never understand where six went. A
     * paying customer losing something they earned is the worst failure
     * available here, and it is the failure an assignment ships.
     *
     * A floor also cannot be farmed. It never adds to a holding that already
     * clears it, so restarting a level twenty times leaves a Pro player with
     * exactly what one attempt gives them — which is the difference between "you
     * never run out" and "you can print these". Level rewards remain the only
     * way to hold more, for Pro and free players alike.
     *
     * Same shape as [refill] and [refillBones], which is the point: `refillTo`,
     * the ad refill and this are three floors and no caps.
     */
    private suspend fun GameAction.grantProBoosters() {
        if (!entitlements.isPro.value) return
        var granted: Pair<Int, Int>? = null
        updateState {
            val next = it.copy(
                sniffs = maxOf(it.sniffs, proSniffsPerAttempt()),
                treats = maxOf(it.treats, proTreatsPerAttempt()),
            )
            granted = next.sniffs to next.treats
            next
        }
        val (sniffs, treats) = granted ?: return
        persistCounts(Consumable.Sniff, sniffs)
        persistCounts(Consumable.Treat, treats)
    }



    /**
     * Moves the guided run on, if [trigger] is what the current step was waiting
     * for — and not before the mark the player just made has drawn itself.
     *
     * **The trigger check is the gate.** A step that asks for a cross moves on a
     * cross and on nothing else: the coach mark carries no "Got it" button, the
     * scrim declines to dismiss on an outside tap, and a `Tap` arriving here for
     * a `Marked` step returns without doing anything. The way out of a step the
     * player does not want is Skip, which is on every card.
     *
     * Every board fact this needs arrives as a parameter, and that matters more
     * now that there is a suspension in the middle: `state` lags `updateState`
     * by a dispatch *and* is a moving target across a delay, so reading the
     * board back off it here would point the next lesson at whatever happened
     * during the wait.
     *
     * [visibleMarks] is `GameState.visibleAutoMarks` and not the deduction
     * behind it: a lesson points at squares on a screen, so it has to know what
     * the screen is showing.
     */
    private suspend fun GameAction.advanceTutorial(
        trigger: TutorialTrigger,
        level: LevelDefinition,
        placed: Solution,
        visibleMarks: Set<Int>,
        justMarked: Set<Int> = emptySet(),
    ) {
        val current = tutorial.currentStep ?: return
        if (Tutorial.triggerFor(current) != trigger) return

        // Hold the spotlight where it is until the cross has finished drawing or
        // the dog has finished landing. The player did what they were asked; the
        // lesson is the seeing of it, not the advancing past it.
        delay(Tutorial.settleMillis(trigger))

        val finished = tutorial.advance()
        val frame = if (finished) {
            TutorialFrame.None
        } else {
            tutorial.frameFor(level, placed, visibleMarks, justMarked)
        }
        if (frame.step != null) {
            updateState { it.copy(tutorial = frame.step, tutorialCells = frame.cells) }
            return
        }
        completeTutorial(skipped = false, at = current)
        leaveRehearsal()
    }

    /** The coach mark's own dismissal, and the "Got it" button under it. */
    private suspend fun GameAction.tutorialTapped() {
        val level = state.level ?: return
        advanceTutorial(TutorialTrigger.Tap, level, state.placed, state.visibleAutoMarks)
    }

    /**
     * The way out, from any step — and it has to work from *every* one, because
     * the gated steps have no other exit.
     *
     * It clears the script and then leaves the rehearsal entirely. Clearing the
     * card alone would drop the player onto the demo board: a puzzle that is not
     * in the campaign, cannot be finished, and banks nothing. A skip means "let
     * me play", so it hands over the same level 1 that finishing does.
     */
    private suspend fun GameAction.skipTutorial() {
        val at = tutorial.currentStep
        completeTutorial(skipped = true, at = at)
        if (rehearsing) {
            leaveRehearsal()
            return
        }
        updateState { it.copy(tutorial = null, tutorialCells = emptySet()) }
    }

    private suspend fun completeTutorial(skipped: Boolean, at: TutorialStep?) {
        tutorial.stop()
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
     * The lifetime total as it stands before this attempt, and the part of it
     * that belongs to the board about to be played.
     *
     * Both packs pay into one number — the user's ask was a single score, and a
     * daily board is a board. Read once per attempt rather than observed: the
     * only thing that moves it while a board is open is the attempt itself, and
     * [GameState.lifetimeScore] adds that back without another read.
     *
     * A failure to read either half reports zero rather than throwing. A wrong
     * headline number is a bad frame; a throw here is a level that will not
     * open.
     */
    private suspend fun bankedScores(level: LevelDefinition): BankedScore {
        val levels = Catching { progress.all() }
            .logOnFailure { "Failed to read level records for the lifetime score" }
            .getOrNull()
            .orEmpty()
        val days = Catching { daily.history() }
            .logOnFailure { "Failed to read daily results for the lifetime score" }
            .getOrNull()
            .orEmpty()
        val date = dailyDate
        return BankedScore(
            lifetime = LifetimeScore.banked(levels, days),
            thisBoard = when {
                !isDaily -> LifetimeScore.bankedForLevel(levels, level.id)
                date != null -> LifetimeScore.bankedForDaily(days, date)
                else -> 0
            },
        )
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
        // A dog that is already placed stays placed, but the board says so
        // rather than ignoring the tap. `strikeCell` is deliberately cleared:
        // nothing was broken here, so the rule chip explaining the last real
        // mistake should stop pointing at it.
        if (cell in state.placedCells) {
            nudge(cell)
            return
        }
        // A square that already cost a bone is finished, and the guard belongs
        // here rather than in `toggleMark` alone.
        //
        // It used to live only there, so the first tap on a red square did
        // nothing and the second one reached `commit`, which has no such guard,
        // and spent *another* bone on a square the player was already told was
        // wrong. The first tap doing nothing is exactly what makes the second
        // one likely: it reads as a control that did not register. At one bone
        // left it ended the attempt.
        //
        // The accessibility path never had this bug — `GameScreen` refuses the
        // placement action on `wrongGuesses` — so the two paths disagreed about
        // what a red square is. They agree now: it is inert.
        if (cell in state.wrongGuesses) {
            nudge(cell)
            return
        }

        val now = clock.markNow()
        val isSecondTap = lastTappedCell == cell &&
            lastTapAt?.let { now - it }?.inWholeMilliseconds?.let { it <= DoubleTapWindowMs } == true
        lastTappedCell = cell
        lastTapAt = now

        if (isSecondTap) {
            lastTappedCell = null
            commit(cell, wasMarked = markedBeforeFirstTap)
        } else {
            // Read *before* `toggleMark` runs, because it is about to change the
            // answer. A commit is the second of two taps, and the first one has
            // already either written a manual cross or cleared an auto one — so
            // asking after the fact reports the exact opposite of what happened,
            // and reports it consistently rather than at random.
            markedBeforeFirstTap = cell in state.visibleAutoMarks || cell in state.manualMarks
            toggleMark(cell)
        }
    }

    /**
     * The board saying no, at no cost.
     *
     * A tap the rules refuse — on a dog already placed, or on a square that
     * already cost a bone — used to return silently. Silence is the worst
     * possible answer: it is exactly what a broken control looks like, and two
     * of the bugs found on this screen were reported as "the tap did nothing"
     * when the tap was being deliberately ignored.
     */
    private suspend fun GameAction.nudge(cell: Int) {
        updateState {
            it.copy(
                shakeCell = cell,
                shakeNonce = it.shakeNonce + 1,
                // Nothing was broken, so stop explaining the last thing that was.
                strikeCell = null,
            )
        }
        sendEvent(GameEvent.Struck(cell))
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
     *
     * That branch is gated on the setting rather than on
     * [GameState.visibleAutoMarks], and the difference is the whole reason both
     * exist. A cross the player has already tapped away is out of the visible
     * set but is still an auto-mark, and tapping it again has to put it back —
     * so this asks "is the board drawing auto-marks at all", not "is this
     * square currently crossed off". With the setting off, none of these
     * squares show anything and a tap on one writes the player's own cross like
     * any other.
     */
    private suspend fun GameAction.toggleMark(cell: Int) {
        // Paid for with a bone. It stays.
        if (cell in state.wrongGuesses) return
        if (state.autoMarkVisible && cell in state.autoMarks) {
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
        val marks = state.visibleAutoMarks
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
    /**
     * A deliberate placement: the second of two taps inside the double-tap
     * window.
     *
     * An auto-marked square used to return here without doing anything, which
     * meant a player could not place a dog illegally at all — and those squares
     * are precisely where an illegal placement lives, since a placed dog
     * auto-marks its own row, column, region and neighbours. Reported from a
     * device as "the double click did nothing", which is exactly right: it did
     * nothing, and said nothing about why.
     *
     * The guard was protective — don't let someone spend a bone on a square we
     * already crossed out for them — but a double tap is a deliberate act, not a
     * slip, and a control that silently refuses is worse than one that costs
     * something. A single tap still toggles a mark harmlessly.
     */
    private suspend fun GameAction.commit(cell: Int, wasMarked: Boolean) {
        val level = state.level ?: return

        val row = level.board.rowOf(cell)
        val correct = level.solution[row] == level.board.colOf(cell)
        // Not on the rehearsal board. Its id is not a level id, and its taps are
        // dictated by a script — folding them in would answer "how often do
        // players commit on a square already crossed off" with the tutorial's
        // own answer, on a board nobody chose to play.
        if (!rehearsing) {
            logger.logEvent(
                "game.commit",
                "level_id" to level.id,
                "correct" to correct,
                // Was this square already crossed out **on screen** when they
                // committed? A rise here means the crosses are not reading as
                // "ruled out", which is a legibility problem rather than a
                // difficulty one — so it is the drawn set and not the deduction,
                // and a square the player tapped the cross off no longer counts.
                // Handed in from `tap`, not read here. See the capture site:
                // by the time this runs the first of the two taps has already
                // flipped it.
                "on_marked" to wasMarked,
                "mode" to modeName,
                // Without this, `on_marked` means two different things in one
                // series: with auto-mark off it can only ever be a cross the
                // player drew, which is a far rarer event. The legibility signal
                // is only readable split by this.
                "auto_mark" to autoMark,
            )
        }
        if (correct) place(cell) else strike(cell)
    }

    private suspend fun GameAction.place(cell: Int) {
        val level = state.level ?: return
        val row = level.board.rowOf(cell)
        val since = lastPlacementAt.elapsedNow().inWholeMilliseconds
        lastPlacementAt = clock.markNow()

        val scored = Scoring.placement(state.score, level.size, since, scoringConfig())
        val placed = state.placed.withPlacement(row, level.board.colOf(cell))
        // The cascade, computed whether or not the board is drawing it: the
        // sniff and the level's own difficulty rating both reason over this,
        // and a setting about what is on screen may not move either.
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
        //
        // The lesson is handed the crosses that *appeared on screen*, not the
        // eliminations that happened. `clearedMarks` survives a placement
        // untouched and can only ever hold squares that were already marked, so
        // subtracting `marksBefore` alone leaves exactly the new crosses — and
        // with auto-mark off there are none, which is why the two steps built on
        // this are not in that curriculum at all.
        val visibleMarks = if (autoMark) marks - state.clearedMarks else emptySet()
        advanceTutorial(
            TutorialTrigger.Placed,
            level,
            placed,
            visibleMarks,
            justMarked = visibleMarks - marksBefore,
        )

        // The finished card is handed on rather than re-read from `state`, which
        // lags this update by a dispatch — re-reading would drop the points for
        // the very placement that won the level.
        //
        // The rehearsal cannot get here: its script places three dogs on a
        // five-row board and then hands over to level 1. The guard is here
        // because `win` writes progress, achievements and a level record, and
        // the cost of being wrong about that is a player credited with clearing
        // a level that does not exist.
        if (placed.isComplete && !rehearsing) win(level, scored.card)
    }

    /**
     * A wrong guess. The cell is left *marked*, not cleared: the player has just
     * proved no dog goes there, and throwing that away would make the strike
     * cost information as well as a life.
     *
     * The bone comes out of the one global count and is written to disk in the
     * same breath. A campaign strike and a daily strike spend the same pool,
     * which is the point: the two used to have three each.
     */
    private suspend fun GameAction.strike(cell: Int) {
        // The lesson asks the player to get one wrong on purpose, so nothing on
        // the rehearsal board is charged for. Everything else about a strike
        // still happens — the square goes red, the combo breaks — and the step
        // that follows says what it would normally have cost.
        //
        // This used to be a one-shot `freeMistakeAvailable` on campaign level 3,
        // which had to be armed, spent and disarmed in three different places.
        // A whole board that costs nothing needs none of that, and cannot get
        // out of step with itself.
        val forgiven = rehearsing
        val level = state.level
        val placed = state.placed
        val marks = state.visibleAutoMarks
        // Floored, because a board can now legitimately open at zero and a
        // negative holding would be persisted and then refilled *up to* itself.
        val remaining = if (forgiven) {
            state.livesRemaining
        } else {
            (state.livesRemaining - 1).coerceAtLeast(0)
        }
        if (!forgiven) strikesThisAttempt++
        val strikes = strikesThisAttempt
        updateBoard {
            it.copy(
                score = Scoring.strike(it.score),
                livesRemaining = remaining,
                strikesThisAttempt = strikes,
                wrongGuesses = it.wrongGuesses + cell,
                strikeCell = cell,
                strikeNonce = it.strikeNonce + 1,
                shakeCell = cell,
                shakeNonce = it.shakeNonce + 1,
            )
        }
        if (!forgiven) persistCounts(Consumable.Bone, remaining)
        sendEvent(GameEvent.Struck(cell))
        if (level != null) advanceTutorial(TutorialTrigger.Struck, level, placed, marks)
        when {
            // `forgiven`, not `remaining`. A rehearsal strike costs nothing, so
            // `remaining` is simply whatever the player was already holding —
            // and a player who entered the tutorial on their last bone would
            // otherwise "lose" the demo board on the step that *tells them to
            // guess wrong*. That wrote a `LevelResult` for level 0 into the
            // achievement log and a `game.level_failed` for a board nobody
            // chose to play. Every other write on this screen is gated on the
            // rehearsal; this one was missed because it is reached through a
            // number rather than through a branch.
            forgiven -> Unit
            remaining <= 0 -> lose(strikes)
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
        // Bones *this attempt* did not spend, not bones held. The holding
        // crosses boards and an ad moves it, so pricing the completion bonus on
        // it would make a refill a score multiplier and a stash worth points.
        val bonesUnspent = (ScoringConfig.MAX_LIVES - strikesThisAttempt).coerceAtLeast(0)
        val finished = Scoring.complete(
            earned,
            level.size,
            level.difficulty,
            bonesUnspent,
            scoring,
        )
        // Rated on what the run earned, banked on what it earned *net of help*.
        // The paw thresholds are fractions of par, and the booster cost is a
        // multiplier, so rating the pre-penalty total is identical to scaling
        // par by the same factor — and it keeps three paws reachable for the
        // player the tutorial has just told to spend a sniff and a treat.
        val paws = Scoring.paws(
            finished.total,
            level.size,
            level.difficulty,
            completed = true,
            config = scoring,
        )
        val boostersUsed = sniffsUsed + treatsUsed
        val banked = Scoring.afterBoosters(finished.total, boostersUsed, scoring.boosterPenaltyRate)
        val duration = elapsedMs()

        logger.logEvent(
            "game.level_completed",
            "level_id" to level.id,
            "size" to level.size,
            "difficulty" to level.difficulty,
            "duration_ms" to duration,
            "score" to banked,
            "paws" to paws,
            "strikes_used" to strikesThisAttempt,
            // What the score above is net of. Without them a drop in median
            // score reads as a difficulty change rather than as players leaning
            // harder on hints, and the two want opposite fixes.
            "sniffs_used" to sniffsUsed,
            "treats_used" to treatsUsed,
            "attempt_number" to attemptNumber,
            "mode" to modeName,
            "auto_mark" to autoMark,
        )
        val streak = if (isDaily) {
            recordDailyClear(banked, paws, duration)
        } else {
            // Every metric here is a *best*, not a last: the repository keeps the
            // better of what it holds and what this attempt scored, so a replay
            // can never cost the player a three-paw clear. It also opens the next
            // level, which is why nothing else writes an unlock — and why a daily
            // must not come through here at all.
            Catching { progress.onCompleted(level.id, banked, paws, duration) }
                .logOnFailure { "Failed to record the clear of level ${level.id}" }
            NoStreak
        }
        val unlocked = unlockedThrough()
        val earnedBadges = recordAttempt(
            level,
            finished,
            banked,
            paws,
            duration,
            completed = true,
            strikes = strikesThisAttempt,
            dailyStreakDays = streak,
        )
        // Before the sheet is built, so the sheet can say so and the count it
        // shows already includes the Treat. `progress.onCompleted` above has
        // already overwritten the record, which is why the first-clear question
        // is answered from the snapshot taken when the level opened.
        val reward = grantLevelReward(level, firstClear = levelNeverCleared)

        sendEvent(GameEvent.Won)
        updateBoard {
            it.copy(
                phase = GamePhase.Won,
                score = finished,
                // The rate this clear was priced at, so the sheet's number and
                // the row just written to disk cannot disagree because config
                // moved mid-attempt.
                boostersUsed = boostersUsed,
                boosterPenaltyRate = scoring.boosterPenaltyRate,
                paws = paws,
                elapsedMs = duration,
                unlockedThrough = maxOf(it.unlockedThrough, unlocked),
                newBadges = earnedBadges,
                dailyStreak = streak,
                treatAwarded = reward,
            )
        }
        offerStreakCeremony()
    }

    /**
     * Hands the screen to the streak, if it is owed anything.
     *
     * Asked here and nowhere else. A finished board is the one pause in this
     * app where a full-screen interruption is not taking something away: the
     * puzzle is over, the sheet is up, and the player has already stopped.
     * Anywhere mid-attempt and it costs them their place.
     *
     * After the win sheet's state is written, so closing the ceremony lands
     * back on the finished board rather than on nothing.
     *
     * Not on the daily: the daily is the thing the streak is *about*, so a
     * celebration on top of the daily's own recap would be two pages about one
     * board. There is deliberately no rehearsal check — `win` is already gated
     * on `!rehearsing` and this is only reachable from inside it, so a second
     * guard here would be a line no test could ever fail.
     */
    private suspend fun GameAction.offerStreakCeremony() {
        if (isDaily) return
        val prompt = Catching { streak.pendingPrompt() }
            .logOnFailure { "Failed to read the streak prompt" }
            .getOrNull()
            ?: return
        when (prompt) {
            StreakPrompt.None -> Unit
            StreakPrompt.Intention -> sendEvent(GameEvent.OpenStreakIntention)
            is StreakPrompt.Celebrate -> sendEvent(GameEvent.OpenStreak(prompt.streak))
        }
    }

    /**
     * The Treat `boosters.treatSchedule` says this level pays out.
     *
     * **First clear only.** A replay pays nothing, because a level that paid
     * every time it was finished would be a treat printer with no ad in front of
     * it — pick the shortest 4x4 in the pack and farm it. The record is read as
     * it stood when the level *opened* ([recordBeforeAttempt]); the clear above
     * has already moved it to `Completed`, so asking now would answer "no" for
     * every level in the game.
     *
     * The holding goes **above** the refill floor and stays there. That is the
     * whole point of the reward: `boosters.refillTo` caps what an ad tops you up
     * to, not what you are allowed to own (SPEC 1.5), so a player who clears
     * levels accumulates a stash and a player who watches ads does not.
     *
     * Campaign only. The daily has no level ladder to count against, and its
     * ids are positions in a different pack — daily 200 is not campaign 200.
     */
    private suspend fun GameAction.grantLevelReward(
        level: LevelDefinition,
        firstClear: Boolean,
    ): Boolean {
        if (isDaily || !firstClear) return false
        if (!treatSchedule.paysTreatAt(level.id)) return false

        var held = 0
        updateState {
            held = it.treats + LevelRewardTreats
            it.copy(treats = held)
        }
        persistCounts(Consumable.Treat, held)
        logger.logEvent(
            "game.level_reward_granted",
            "level_id" to level.id,
            "booster" to "treat",
            "held" to held,
        )
        return true
    }

    /**
     * Whether the level had never been cleared before this attempt opened.
     *
     * Read off the snapshot rather than the live record, because `onCompleted`
     * overwrites it during the win. Both the achievement fold and the level
     * reward hang off this, and they must agree.
     */
    private val levelNeverCleared: Boolean
        get() = recordBeforeAttempt.state != LevelState.Completed

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
        /**
         * The attempt's score net of the boosters it spent — the same number
         * that reaches the record — rather than [ScoreCard.total], so a badge
         * for a big score cannot be bought with treats.
         */
        score: Int,
        paws: Int,
        duration: Long,
        completed: Boolean,
        /**
         * Wrong guesses this attempt made. Passed rather than derived from the
         * bone count, which since R15 is a global holding and says nothing about
         * how cleanly *this* board was played — every "without losing a bone"
         * badge hangs off this number.
         */
        strikes: Int,
        dailyStreakDays: Int,
    ): List<Achievement> {
        val now = wallClock.now()
        val result = LevelResult(
            levelId = level.id,
            mode = if (isDaily) PlayMode.Daily else PlayMode.Campaign,
            size = level.size,
            completed = completed,
            score = score,
            paws = paws,
            timeMs = duration,
            strikes = strikes,
            bestCombo = card.bestCombo,
            sniffsUsed = sniffsUsed,
            treatsUsed = treatsUsed,
            isFirstClear = completed && levelNeverCleared,
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
     * [strikes] is passed in rather than read back off `state`. The caller's
     * `updateState` has not landed yet — `state` reads a derived flow that lags
     * it by a dispatch — so reading it here reported one strike fewer than the
     * player actually took, which the achievement log then believed.
     */
    private suspend fun GameAction.lose(strikes: Int) {
        val level = state.level ?: return
        logger.logEvent(
            "game.level_failed",
            "level_id" to level.id,
            // Without the tier here, only *clears* report one, so a board hard
            // enough to lose on is under-represented in every calibration panel
            // — which is the exact direction a mis-rating would hide in.
            "difficulty" to level.difficulty,
            "duration_ms" to elapsedMs(),
            "dogs_placed" to state.placed.placedCount,
            "attempt_number" to attemptNumber,
            "mode" to modeName,
            "auto_mark" to autoMark,
        )
        val duration = elapsedMs()
        // Read, not written. A lost daily is not spent here: the player can still
        // trade an ad for the board back, and `daily_result` takes one row per
        // day, so writing the failure now would lock a loss over a clear they
        // went on to earn. Nor is it spent on the way out any more — [leave]
        // used to write it, which turned tapping Levels into a forfeit nobody
        // asked for. Only [forfeitDaily] spends the day.
        //
        // The streak itself is unaffected either way — a run through yesterday
        // stands all day today, including after today has been played and lost.
        val streak = if (isDaily) currentStreak() else NoStreak
        val lost = Scoring.strike(state.score)
        val earnedBadges = recordAttempt(
            level,
            lost,
            Scoring.afterBoosters(lost.total, sniffsUsed + treatsUsed, state.boosterPenaltyRate),
            paws = 0,
            duration,
            completed = false,
            strikes = strikes,
            dailyStreakDays = streak,
        )
        val skip = skipOffer(level)
        updateBoard {
            it.copy(
                phase = GamePhase.Lost,
                elapsedMs = duration,
                newBadges = earnedBadges,
                dailyStreak = streak,
                skip = skip,
            )
        }
    }

    /**
     * Whether this loss earns the Skip option, and what it costs.
     *
     * **Attempts, not failures.** `level_progress` counts starts, and adding a
     * failure column would mean a schema bump on a database that still rebuilds
     * itself destructively — it would cost every player their campaign to make
     * this number one better. Attempts over-count, and by more than it looks:
     * `onAttemptStarted` fires again every time a level is *resumed*, so
     * backgrounding a board twice counts as two attempts. Seen on device — a
     * level re-entered after a process death offered the skip on its first real
     * loss. That error direction is deliberate. The skip is a rescue behind an
     * ad and a daily cap, and being early with a rescue is the cheap mistake.
     *
     * Offered at zero remaining as well, disabled and saying why. A player who
     * has just failed twice and is about to fail again should find out the
     * option exists and is spent for today, rather than meet a sheet that
     * quietly looks the same as it did an hour ago.
     */
    private suspend fun skipOffer(level: LevelDefinition): SkipOffer? {
        // No skip on the daily: there is no next board to advance to, and the
        // day is one attempt by definition.
        if (isDaily) return null
        val after = skipAfterFailedAttempts()
        if (after <= 0 || recordBeforeAttempt.attempts + 1 < after) return null
        // Clearing level 500 has nowhere to skip to.
        if (level.id >= LevelPacks.lastCampaignLevelId) return null

        val remaining = Catching { skips.remainingToday() }
            .logOnFailure { "Failed to read the skip allowance" }
            .getOrNull()
            ?: return null
        return SkipOffer(remainingToday = remaining, free = entitlements.isPro.value)
    }

    /**
     * Trades an ad for the level, and opens the next one.
     *
     * The repository owns the order — allowance, then ad, then write — so this
     * only routes the answer. [SkipResult.Declined] is the player closing the ad
     * a second ago and the lose sheet is still in front of them unchanged, so it
     * says nothing; [SkipResult.NoneLeft] re-renders the offer as spent, because
     * it means the allowance ran out somewhere other than on this screen.
     */
    private suspend fun GameAction.skipLevel() {
        val level = state.level ?: return
        if (isDaily) return

        val result = Catching { skips.skip(level.id) }
            .logOnFailure { "Failed to skip level ${level.id}" }
            .getOrNull()
        when (result) {
            is SkipResult.Skipped -> {
                logger.logEvent(
                    "game.level_skipped",
                    "level_id" to level.id,
                    "attempt_number" to attemptNumber,
                    "skips_left_today" to result.remainingToday,
                )
                nextLevel()
            }
            SkipResult.NoneLeft -> updateState {
                it.copy(skip = it.skip?.copy(remainingToday = 0))
            }
            SkipResult.Declined, null -> Unit
        }
    }

    /**
     * Gives today's board up, which is the **only** way a lost daily is spent.
     *
     * It used to be [GameAction.Leave], and that is the bug: tapping Levels on a
     * lost daily wrote `onFailed`, which spends the day, so a player who wanted
     * to go and look at something landed in the campaign with the daily closed
     * behind them. `daily_result` is insert-only and the write is final, so the
     * decision has to be one the player makes on purpose — hence the
     * confirmation in front of this, and a plain [GameAction.Leave] that writes
     * nothing at all.
     *
     * The saved board goes with it. Nothing will ever resume a day that is
     * spent, and a snapshot left behind holds the one in-progress slot against
     * whatever board the player opens next.
     */
    private suspend fun GameAction.forfeitDaily() {
        val date = dailyDate
        val level = state.level
        if (!isDaily || date == null || level == null) return
        if (state.phase != GamePhase.Lost) return

        val dogsPlaced = state.placed.placedCount
        Catching { daily.onFailed(date, elapsedMs()) }
            .logOnFailure { "Failed to record the daily loss for $date" }
        logger.logEvent(
            "daily.forfeited",
            "date" to date.toString(),
            "dogs_placed" to dogsPlaced,
        )
        clearSavedBoard(level.id)
        updateState { it.copy(forfeitPrompt = false) }
        sendEvent(GameEvent.NavigateBack)
    }

    /**
     * Trades an ad for a full set of bones and puts the board back in play.
     *
     * **The one way back from zero**, and the reason there is only one. The lose
     * sheet used to carry this *and* a "Keep going" that restored a single bone
     * for the same ad — strictly the worse of two buttons sitting directly under
     * the better one, and in a build with no ad inventory it read as a free bone
     * for nothing, which is exactly what a player reported. Restoring the whole
     * set is what the offer has to do anyway: handing someone who has already
     * run out a single bone puts them right back here on the next guess.
     *
     * **It cannot fail closed** (SPEC 4.2). Every non-dismissal outcome grants —
     * no fill, no network, no SDK, ads switched off in config, a config server
     * nobody can reach. With one global count that guarantee stops being
     * politeness and becomes the thing that keeps a player at zero from being
     * stuck across the whole game, so the only way to leave this without bones
     * is to close the ad yourself.
     */
    private suspend fun GameAction.refillBones() {
        // Nothing to revive on a day that is already spent.
        if (state.phase == GamePhase.Recap) return
        val levelId = state.level?.id ?: 0
        // SPEC 5.3 names `continue_level` as "third strike, restore, keep the
        // board", and that is precisely what this is from the lose sheet. The
        // standing offer on a board still in play is a booster grant.
        val placement = if (state.phase == GamePhase.Lost) {
            AdPlacement.ContinueLevel
        } else {
            AdPlacement.BoosterGrant
        }
        val granted = entitlements.isPro.value ||
            adGate.showRewarded(placement) != RewardOutcome.Dismissed
        if (!granted) return

        lastPlacementAt = clock.markNow()
        warnedAboutLastBone = false
        var topped = 0
        updateBoard {
            // Never downward, matching `refill` and Pro's opening boosters: a
            // player holding more than the floor is not punished for watching
            // an ad. Captured inside the transform, because `state` lags this
            // by a dispatch and reading it back would persist the old count.
            topped = maxOf(it.livesRemaining, refillTo())
            it.copy(
                phase = GamePhase.Playing,
                livesRemaining = topped,
                boosterPrompt = null,
            )
        }
        persistCounts(Consumable.Bone, topped)
        logger.logEvent(
            "game.bones_refilled",
            "level_id" to levelId,
            "to" to topped,
            "placement" to placement.name,
        )
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
        startAttempt(next, resume = savedBoardFor(next))
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
        startAttempt(target, resume = savedBoardFor(target))
    }

    /**
     * Opens the daily on its own route, from the card in the drawer.
     *
     * A spent day opens too, on its result rather than its board. The refusal
     * that used to live here was the second half of R16: the card stopped doing
     * anything the moment the day was over, so a player thrown out of a daily by
     * the old forfeit-on-leave had no way back into it and no explanation. The
     * kill switch is still honoured — a daily that is switched off has no route
     * to open.
     */
    private suspend fun GameAction.playDaily() {
        val status = state.daily ?: return
        if (!status.enabled) return
        updateState { it.copy(drawerOpen = false) }
        // Only a real attempt is a start. Reviewing a finished day emits
        // `daily.reviewed` from the route instead, so the funnel keeps counting
        // attempts rather than visits.
        if (status.playable) {
            logger.logEvent(
                "daily.started",
                "date" to status.date.toString(),
                "streak" to status.streak,
            )
        }
        sendEvent(GameEvent.OpenDaily(status.levelId))
    }

    /**
     * Closes the daily explainer and makes sure it never comes back.
     *
     * The write is the whole point, so it is not fire-and-forget: a failure
     * here means the player is told the same thing again next time, which is
     * mildly annoying rather than harmful, and worth a log line either way.
     */
    private suspend fun GameAction.dismissDailyIntro() {
        updateState { it.copy(showDailyIntro = false) }
        Catching { appCache.update { it.copy(hasSeenDailyIntro = true) } }
            .logOnFailure { "Failed to record the daily explainer as seen" }
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

    /**
     * Trades an ad for a whole run of missed days, and says what happened.
     *
     * Routes into the same dialog as the freeze on purpose: from where the player
     * is standing these are one feature with two sizes, and giving the bigger one
     * its own surface would be two ways of saying "your streak is back".
     */
    private suspend fun GameAction.restoreStreak() {
        val result = Catching { daily.restoreStreak() }
            .logOnFailure { "Failed to restore a streak" }
            .getOrNull()
        val message = when (result) {
            is RestoreResult.Applied -> {
                logger.logEvent("daily.streak_restored", "days" to result.days, "streak" to result.streak)
                FreezeMessage.Restored(result.days, result.streak)
            }
            RestoreResult.Declined -> FreezeMessage.Declined
            RestoreResult.NoneLeft -> FreezeMessage.RestoreNoneLeft
            RestoreResult.OutOfReach -> FreezeMessage.RestoreOutOfReach
            RestoreResult.NothingToRestore -> FreezeMessage.NothingToFreeze
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
        // No resume. Retry is the one path that deliberately throws the board
        // away, and handing it back would make the button do nothing.
        startAttempt(level, resume = null)
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
        // Bones always prompt. The other two are spent by tapping, so once the
        // player knows what they do a tap should just use one — but a bone is
        // only ever spent by guessing wrong, and its pill is an explainer and a
        // refill offer.
        //
        // Without this it fell through to `spend(Bone)`, which does nothing but
        // clear a prompt that was never opened. So the pill worked once, and
        // then silently stopped for the rest of the install while keeping its
        // press animation and its accessibility label.
        if (consumable == Consumable.Bone || !explained || countOf(consumable) <= 0) {
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
        //
        // `autoMarks` and **not** `visibleAutoMarks`. This is the deduction the
        // hint reasons from, not the picture on screen, and the two came apart
        // in R8. Reading the drawn set would mean a player with auto-mark off
        // got a *different* hint from the same board — worse advice for asking
        // for less help, which is the exact trade the setting is not making.
        // `HintFinder` excludes the cascade on its own, so this line is belt and
        // braces there; it is load-bearing for the other three sets.
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
            logger.logEvent(
                "game.booster_no_op",
                "booster" to "sniff",
                "level_id" to level.id,
                // A rise in this event is a difficulty signal, and without the
                // tier it cannot say which tier.
                "difficulty" to level.difficulty,
            )
            updateState { it.copy(boosterPrompt = null) }
            return
        }

        logger.logEvent("game.booster_used", "booster" to "sniff", "level_id" to level.id)
        sniffsUsed++
        persistCounts(Consumable.Sniff, state.sniffs - 1)
        // `updateBoard`, not `updateState`, and that is the whole fix.
        // `persistCounts` writes the holding to `AppData`; only `updateBoard`
        // writes the *attempt*, which is where `sniffsUsed` lives. Spending a
        // sniff and force-quitting before the next move therefore restored an
        // attempt that had never taken any help, banking a bigger score than was
        // earned and under-reporting `sniffs_used`.
        //
        // `useTreat` was safe by accident: it ends in `place`, which already
        // goes through `updateBoard`.
        updateBoard {
            it.copy(
                sniffs = it.sniffs - 1,
                // Counted from the fields rather than incremented in state:
                // these two are the same number, and a `+ 1` here would drift
                // from the count the win writes.
                boostersUsed = sniffsUsed + treatsUsed,
                boosterPrompt = null,
                hintCells = ruledOut,
            )
        }
    }

    /**
     * Keeps the squares the sniff proposed.
     *
     * They become `manualMarks` rather than anything of their own, so from here
     * on they are the player's crosses and behave like every other one: tappable
     * away, saved with the board, and unaffected by the auto-mark setting. The
     * sniff's job ends at proposing.
     *
     * Goes through `updateBoard` so the marks reach the snapshot. Spending a
     * sniff and quitting used to lose the help entirely, which was half of an
     * earlier bug; losing it *after* the player accepted it would be worse.
     */
    private suspend fun GameAction.applyHint() {
        val proposed = state.hintCells
        if (proposed.isEmpty()) return
        updateBoard {
            it.copy(
                manualMarks = it.manualMarks + proposed,
                hintCells = emptySet(),
            )
        }
        logger.logEvent(
            "game.hint_applied",
            "squares" to proposed.size,
            "level_id" to (state.level?.id ?: -1),
            "mode" to modeName,
        )
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
        updateState {
            it.copy(
                treats = it.treats - 1,
                boostersUsed = sniffsUsed + treatsUsed,
                boosterPrompt = null,
            )
        }
        // After the count, so the placement this treat pays for is already
        // priced as bought help by the time it lands on the board.
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
        // The rehearsal is never written down. There is one in-progress slot for
        // the whole app, and a demo board holding it would evict a real level
        // somebody left half-finished — and then be resumed on the next launch
        // as a board with no id in either pack.
        if (rehearsing) return
        next?.let { saveBoard(it) }
    }

    private fun elapsedMs(): Long = elapsedBeforeResume +
        if (clockPaused) 0L else attemptStartedAt.elapsedNow().inWholeMilliseconds

    /**
     * Stops the puzzle clock while the app is away, and starts it again on the
     * way back.
     *
     * Nothing is written to disk here. The snapshot is saved after every move
     * and carries [elapsedMs] as it stood then, so a process death in the
     * background costs the player the seconds since their last placement. That
     * is an under-count, which is the direction a timing error should fail in.
     *
     * Idempotent in both directions. Android dispatches `onStart` on the way
     * into the first foreground as well as on every return, so the resume half
     * runs once before the player has done anything, and a second background
     * must not fold the same span in twice.
     */
    private fun holdClock(paused: Boolean) {
        if (paused == clockPaused) return
        if (paused) {
            elapsedBeforeResume += attemptStartedAt.elapsedNow().inWholeMilliseconds
        } else {
            attemptStartedAt = clock.markNow()
        }
        clockPaused = paused
    }

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
        // A **lost daily** is kept, unlike a lost campaign level. Leaving the
        // loss sheet no longer spends the day, so the position has to survive
        // the walk out — otherwise reopening the daily hands back an empty
        // board, which is the fresh run one-attempt-per-day exists to refuse.
        // The campaign has Start over and nothing to protect.
        val worthKeeping = state.phase == GamePhase.Playing ||
            (state.phase == GamePhase.Lost && isDaily)
        val snapshot = if (level == null || !worthKeeping) {
            null
        } else {
            BoardSnapshot(
                levelId = level.id,
                isDaily = isDaily,
                placements = state.placed.columnByRow.toList(),
                manualMarks = state.manualMarks,
                wrongGuesses = state.wrongGuesses,
                strikesTaken = state.strikesThisAttempt,
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
                val held = data.boardInProgress
                val ours = held == null ||
                    (held.levelId == level?.id && held.isDaily == isDaily)
                // A real board always takes the slot; only a **clearing** write
                // is held back when the slot belongs to somebody else. The
                // guard used to cover both, which fixed one bug and opened
                // another: opening a fresh level produces a `null` snapshot on
                // its first frame and writing that unconditionally threw away
                // the half-finished level the player had left behind — but
                // refusing every write meant the new board was never saved at
                // all while the old snapshot sat there. Found holding onto R16:
                // a daily opened over an unfinished campaign level could not
                // save its own loss, so the day had nothing to come back to.
                if (snapshot != null || ours) data.copy(boardInProgress = snapshot) else data
            }
        }.logOnFailure { "Failed to save the board" }
    }

    /**
     * Drops the saved board, but only if it is this one's.
     *
     * There is one slot for the whole app, so a ViewModel that cleared it
     * blindly would throw away whatever board another route had left in it.
     */
    private suspend fun clearSavedBoard(levelId: Int) {
        Catching {
            appCache.update { data ->
                val held = data.boardInProgress
                if (held?.levelId == levelId && held.isDaily == isDaily) {
                    data.copy(boardInProgress = null)
                } else {
                    data
                }
            }
        }.logOnFailure { "Failed to clear the saved board" }
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
