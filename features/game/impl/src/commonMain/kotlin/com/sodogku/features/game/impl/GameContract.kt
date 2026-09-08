package com.sodogku.features.game.impl

import com.sodogku.libraries.achievements.Achievement
import com.sodogku.libraries.achievements.AchievementsRepository
import com.sodogku.libraries.config.values.TreatBand
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LifetimeScore
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.progress.daily.FreezeResult
import com.sodogku.libraries.progress.daily.RestoreResult
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.scoring.Praise
import com.sodogku.libraries.scoring.ScoreCard
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.ScoringConfig
import com.sodogku.libraries.sharing.ShareText
import kotlinx.coroutines.flow.distinctUntilChanged



/**
 * What the game screen renders from, what it sends in, and what it sends out.
 *
 * Split out of `GameViewModel.kt` once that file passed 1600 lines. Nothing here
 * has behaviour — it is the vocabulary the screen and the ViewModel share, and
 * keeping it apart means reading "what can this screen do" no longer means
 * scrolling past how it does it.
 */

/**
 * Where the attempt is. Everything the screen renders keys off this.
 *
 * [Recap] is the odd one out: it is not an attempt at all. It is the daily route
 * opened on a day that has already been played, so the player can get back to a
 * board they finished or gave up on instead of being bounced into the campaign.
 * Nothing on it is interactive.
 */
enum class GamePhase { Loading, Playing, Won, Lost, Recap }

data class GameState(
    val level: LevelDefinition? = null,
    val placed: Solution = Solution.empty(1),
    /**
     * Every square the dogs in [placed] rule out — **what the game knows**, not
     * what the player has been shown.
     *
     * The two used to be one set, and separating them is the whole of R8. With
     * "Cross off squares for me" switched off nothing here is drawn, but all of
     * it is still true: `Board.autoMarkedCells` is the same cascade the
     * difficulty engine rated the level against, and the sniff has to reason
     * from the real deduction state or it starts giving worse advice to exactly
     * the players who asked for less help. What is drawn is [visibleAutoMarks],
     * and every reader has to pick one deliberately.
     */
    val autoMarks: Set<Int> = emptySet(),
    val manualMarks: Set<Int> = emptySet(),

    /**
     * `AppData.autoMarkEnabled` — whether [autoMarks] are drawn.
     *
     * False by default, matching `AppData.autoMarkEnabled`, so a board built
     * before the cache has been read shows the game as it is played. Nothing
     * about the puzzle changes when this is false; see [visibleAutoMarks].
     */
    val autoMarkVisible: Boolean = false,

    /** What an ad tops a booster up to, so the prompt's copy matches the tap. */
    val refillTo: Int = 3,

    /**
     * `boosters.treatSchedule`, so the level pane marks the rows that pay.
     *
     * Empty by default rather than the config default. The pane promising a
     * reward the game has not confirmed is the bug this whole reward path
     * exists to close, and a value baked in here would be a second answer to a
     * question config already answers — the failure that made
     * `boosters.startingSniffs` unwirable. An empty schedule pays nothing, so
     * the pre-config state marks no rows at all.
     */
    val treatBands: List<TreatBand> = emptyList(),

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

    /**
     * Bones held, which is **one count across every board** (SPEC 1.4) and lives
     * on `AppData.bones`. Mirrored here so the header can draw it; the board
     * never owns it, and opening a level does not top it up.
     *
     * The default is what a board built before the cache has been read shows —
     * a preview or a test — not a grant.
     */
    val livesRemaining: Int = ScoringConfig.MAX_LIVES,

    /**
     * Wrong guesses *this attempt*, which since bones went global is a different
     * question from [livesRemaining].
     *
     * The completion bonus, the paw rating and the achievement log are all
     * priced on how cleanly this board was solved, so they read this. Pricing
     * them on the holding instead would make the stash worth points and turn an
     * ad refill into a score multiplier.
     */
    val strikesThisAttempt: Int = 0,

    /** What this attempt has earned, before the boosters it leaned on. */
    val score: ScoreCard = ScoreCard.Empty,

    /**
     * Every point the player has banked, this board's own contribution
     * included. Read when the board opens and not touched again — the attempt
     * on screen is added by [lifetimeScore] rather than folded in here.
     */
    val lifetimeBanked: Int = 0,

    /**
     * What *this* board had already banked when the attempt opened: the level's
     * best score, or today's daily result. Held apart from [lifetimeBanked] so
     * a replay can replace it instead of adding to it.
     */
    val bankedForThisBoard: Int = 0,

    /** Sniffs and treats spent this attempt, which is what they cost. */
    val boostersUsed: Int = 0,

    /**
     * `scoring.boosterPenaltyRate` as it stood for this attempt.
     *
     * Zero by default rather than the config default, for the reason
     * [treatBands] is: a board built before config has been read — a
     * preview, a test, the first frame — must not price help the operator has
     * not confirmed.
     */
    val boosterPenaltyRate: Double = 0.0,
    val paws: Int = 0,
    val elapsedMs: Long = 0,
    val sniffs: Int = 0,
    val treats: Int = 0,

    /** Boosters whose first-use explainer the player has already seen. */
    val explainedBoosters: Set<Consumable> = emptySet(),

    /** The booster whose explainer or refill offer is open, if any. */
    val boosterPrompt: Consumable? = null,
    val phase: GamePhase = GamePhase.Loading,

    /**
     * The last wrong guess, for `brokenRule` to explain.
     *
     * Separate from [shakeCell] because the two answer different questions.
     * This one is "which placement broke a rule, and which rule", and only a
     * real strike has an answer. The shake is just the board saying no, and
     * plenty of taps deserve that without anything being broken.
     */
    val strikeNonce: Int = 0,
    val strikeCell: Int? = null,

    /**
     * The cell to shake, and a nonce so the same one can shake twice running.
     *
     * Bumped by a strike *and* by a tap the board simply refuses, such as one on
     * a dog that is already placed. Refusing silently is what made the earlier
     * double-tap bugs so hard to read from the outside: a control that does
     * nothing is indistinguishable from a control that is broken.
     */
    val shakeNonce: Int = 0,
    val shakeCell: Int? = null,

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

    /**
     * Squares a sniff has ruled out, drawn as faint crosses awaiting one tap.
     *
     * A **proposal**, not a result. It used to be a spotlight that faded, which
     * left the player holding the answer in their head and the board looking
     * exactly as it had before the charge was taken. Painting the crosses and
     * asking to keep them puts the hint on the board and keeps it a decision.
     */
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
     * Whether to explain the daily, which happens exactly once ever.
     *
     * Held in state rather than as local dialog state like the header
     * explainers, because those open on a tap and this one opens on a fact
     * about the player that only the ViewModel can see. It is also the one
     * dialog here whose dismissal writes something down.
     */
    val showDailyIntro: Boolean = false,

    /**
     * Whether the board on screen is [TutorialBoard] rather than a level.
     *
     * The screen needs to know for one reason and the state for another. The
     * header drops the level number, because the rehearsal board has no level
     * number to show; and [lifetimeScore] drops this attempt, because a
     * rehearsal banks nothing and a headline number that climbs during the
     * tutorial and falls back afterwards is a lie the player will notice.
     */
    val isRehearsal: Boolean = false,

    /**
     * The streak as of this attempt, for the outcome sheet. Set from the value
     * the write returned rather than read back off [daily], which arrives on its
     * own dispatch and would show the streak from before the clear.
     */
    val dailyStreak: Int = 0,

    /** The answer to a freeze the player just asked for. */
    val freezeMessage: FreezeMessage? = null,

    /**
     * The stored result of a day that is already spent, set only in
     * [GamePhase.Recap]. This is what the daily route shows instead of refusing
     * to open, which is what used to strand a player in the campaign.
     */
    val dailyRecap: DailyResult? = null,

    /**
     * True while the "give up on today" confirmation is up.
     *
     * Forfeiting writes an insert-only row and cannot be undone, so it asks.
     * That is the whole point of R16: the day is spent by a decision, never by
     * navigating away.
     */
    val forfeitPrompt: Boolean = false,

    /**
     * The Skip option, or null when this attempt has not earned one. Set on the
     * loss that qualifies, and gone again the moment a fresh attempt opens.
     */
    val skip: SkipOffer? = null,

    /**
     * True when the clear just paid a Treat, so the win sheet can say so. The
     * count in [treats] already includes it.
     */
    val treatAwarded: Boolean = false,
) {
    /**
     * What this attempt would bank: what it earned, less the boosters it spent.
     * The win sheet and the share text both show this rather than [score],
     * because it is the number that reaches the player's record.
     */
    val attemptScore: Int
        get() = Scoring.afterBoosters(score.total, boostersUsed, boosterPenaltyRate)

    /**
     * The one score in the game, as it stands right now.
     *
     * Everything the player has banked, with this board counted **once**: an
     * attempt only moves the number by however much it beats this board's own
     * best, so replaying a cleared level cannot pay twice.
     *
     * A lost attempt banks nothing, so it contributes nothing — the number
     * falls back to what was already earned the moment the bones run out,
     * rather than leaving points on screen that no record will ever hold.
     *
     * A rehearsal banks nothing either, and for exactly the same reason. The
     * header holds still at the player's real total for the whole tutorial
     * instead of climbing three dogs' worth and then dropping back when level 1
     * opens.
     */
    val lifetimeScore: Int
        get() = LifetimeScore.withAttempt(
            banked = lifetimeBanked,
            bankedForThisBoard = bankedForThisBoard,
            attemptScore = if (phase == GamePhase.Lost || isRehearsal) 0 else attemptScore,
        )

    /**
     * Bones this attempt did not spend, which is what the share card draws.
     *
     * Derived from [strikesThisAttempt] rather than read off [livesRemaining],
     * because the holding is global: a player who refilled mid-board would
     * otherwise share three intact bones after a run that cost them three.
     */
    val bonesUnspent: Int
        get() = (ScoringConfig.MAX_LIVES - strikesThisAttempt).coerceAtLeast(0)

    /**
     * The auto-marks a player can actually see crossed off — **what the board
     * is showing**, as against the deduction in [autoMarks].
     *
     * Anything that answers a question about the *screen* reads this: how a
     * square draws, whether a screen reader is offered a placement on it, and
     * whether a commit landed on a square that already looked ruled out.
     * Anything that answers a question about the *puzzle* reads [autoMarks].
     * Confusing the two is silent in both directions — hints quietly degrade,
     * or the board offers controls on squares that are already crossed off — so
     * the distinction is stated rather than left to be inferred from a name.
     *
     * [clearedMarks] comes off here rather than out of [autoMarks] because
     * auto-marks are recomputed from [placed] on every move, so anything
     * removed from that set would reappear on the next placement.
     */
    val visibleAutoMarks: Set<Int>
        get() = if (autoMarkVisible) autoMarks - clearedMarks else emptySet()

    val placedCells: Set<Int> get() = placed.cells().toSet()

    val dogsPlaced: Int get() = placed.placedCount

    val dogsRequired: Int get() = level?.size ?: 0

    /**
     * Whether something is on top of the board.
     *
     * Every one of these draws over the grid and takes its taps — the spotlights
     * through `FocusScrim`, the outcome sheet and the level pane by covering it.
     * None of that reaches semantics, so a screen reader would keep offering a
     * hundred squares that a thumb cannot touch. Listed exhaustively rather than
     * summarised, because the failure of a missing entry is silent: the board
     * simply stays reachable when it should not.
     */
    val isCovered: Boolean
        get() = warning != null ||
            tutorial != null ||
            hintCells.isNotEmpty() ||
            drawerOpen ||
            boosterPrompt != null ||
            freezeMessage != null ||
            forfeitPrompt ||
            phase == GamePhase.Won ||
            phase == GamePhase.Lost ||
            phase == GamePhase.Recap
}

/**
 * The Skip on the lose sheet.
 *
 * Carries the allowance rather than just a boolean so the button can say how
 * many are left — the cap is the surprising part of this feature, and a player
 * who finds out about it by tapping a button that does nothing has been told
 * badly.
 */
data class SkipOffer(
    val remainingToday: Int,

    /** Pro skips without watching anything, so the button drops its Ad badge. */
    val free: Boolean = false,
) {
    val available: Boolean get() = remainingToday > 0
}

/**
 * What came of a streak save, freeze or restore. Every [FreezeResult] and every
 * [RestoreResult] maps to one of these, plus [Unavailable] for a repository call
 * that threw — no branch of either is silent.
 *
 * The two share this type, and the dialog, because they share a placement and an
 * outcome: an ad was watched and a streak did or did not come back. Only the
 * branches whose *copy* differs are separate — [Restored] names a number of days,
 * and its two refusals are about a run rather than a day.
 */
sealed interface FreezeMessage {
    data class Applied(val streak: Int) : FreezeMessage
    data object Declined : FreezeMessage
    data object NoneLeft : FreezeMessage
    data object NothingToFreeze : FreezeMessage
    data object Unavailable : FreezeMessage

    data class Restored(val days: Int, val streak: Int) : FreezeMessage

    /** The monthly allowance of restored days cannot pay for this gap. */
    data object RestoreNoneLeft : FreezeMessage

    /** The gap is longer than `daily.restoreMaxDays`. */
    data object RestoreOutOfReach : FreezeMessage
}

sealed interface GameEvent {
    data object NavigateBack : GameEvent

    /** Today's daily, on its own route, from the card in the drawer. */
    data class OpenDaily(val levelId: Int) : GameEvent

    /**
     * The streak wants the screen, at the one pause where that is acceptable.
     *
     * Emitted after the win sheet's state is built rather than instead of it, so
     * a player who closes the ceremony lands back on their finished board rather
     * than on nothing.
     */
    data object OpenStreakIntention : GameEvent

    /** A milestone worth a page. [streak] is the run being celebrated. */
    data class OpenStreak(val streak: Int) : GameEvent

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

    /**
     * Belongs here rather than being read once when the board opens, for the
     * reason the other three do: the gear opens a real screen, so a player
     * flips this and comes straight back to the board. It is a display setting
     * despite changing what the board looks like mid-puzzle — the deduction
     * underneath does not move, so nothing has to be recomputed when it lands.
     */
    val autoMark: Boolean,
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

    /** Back to wherever this board was opened from. Never spends anything. */
    data object Leave : GameAction
    data object DismissWarning : GameAction

    /** Keep the squares the sniff proposed, as the player's own crosses. */
    data object ApplyHint : GameAction

    /** Throw the sniff's proposal away without marking anything. */
    data object DiscardHint : GameAction

    /**
     * Trade an ad for a full set of bones. The one way back from zero, from the
     * lose sheet and from the standing offer on the board alike.
     */
    data object RefillBones : GameAction

    /** The persisted bone count moved, here or on another open board. */
    data class BonesChanged(val bones: Int) : GameAction

    /** "Give up on today" was tapped. Opens the confirmation, writes nothing. */
    data object ForfeitDailyRequested : GameAction

    /** Confirmed: spend the day, then leave. */
    data object ForfeitDailyConfirmed : GameAction

    data object DismissForfeitPrompt : GameAction

    /** Trade an ad for the level, after enough attempts have failed. */
    data object SkipLevel : GameAction
    data object NextLevel : GameAction

    /** The drawer was opened, so its per-level records need reading. */
    data object LevelsOpened : GameAction

    data object LevelsClosed : GameAction
    data class GoToLevel(val levelId: Int) : GameAction

    /** A new daily snapshot: a result was written, or the local date rolled over. */
    data class DailyChanged(val status: DailyStatus) : GameAction

    data object PlayDaily : GameAction

    /** The daily explainer was closed. Writes the flag, so it never returns. */
    data object DailyIntroDismissed : GameAction
    data object UseFreeze : GameAction
    data object RestoreStreak : GameAction

    /** The streak badge in the level pane was tapped. */
    data object OpenStreak : GameAction
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

    /**
     * The app went away or came back.
     *
     * The puzzle clock runs on a monotonic source, which does not stop when the
     * app does, so a player who takes a phone call mid-board would be charged
     * the call. This is the edge that stops and restarts it. It replaced
     * `TimerTick`, which was dispatched by nobody: the elapsed field was never
     * rendered mid-attempt, so a per-second tick bought a recomposition a second
     * and no pixels.
     */
    data class VisibilityChanged(val foreground: Boolean) : GameAction
}

/**
 * A finished run as `m:ss`, or null when there is no run to show.
 *
 * One formatter for the win sheet and the level pane, and the same one the share
 * card prints, so the time a player reads on the sheet is the time they post.
 *
 * Null rather than "0:00" for a board with no time on it. An unplayed level, a
 * day that was given up on and a record written before times were kept all hold
 * zero, and a clock reading nought reads as a run that took no time rather than
 * as one that never happened.
 */
internal fun elapsedLabel(millis: Long): String? =
    if (millis <= 0L) null else ShareText.duration(millis)
