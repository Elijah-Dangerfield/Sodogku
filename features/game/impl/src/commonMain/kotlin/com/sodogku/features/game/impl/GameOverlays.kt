package com.sodogku.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.components.game.CoachMark
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.FocusScrim
import com.sodogku.libraries.ui.system.FocusTargetKey
import com.sodogku.libraries.ui.system.Spotlight
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.board_action_mark
import sodogku.libraries.resources.generated.resources.board_action_place
import sodogku.libraries.resources.generated.resources.game_last_bone_body
import sodogku.libraries.resources.generated.resources.game_last_bone_title
import sodogku.libraries.resources.generated.resources.hint_title
import sodogku.libraries.resources.generated.resources.tutorial_auto_mark_body
import sodogku.libraries.resources.generated.resources.tutorial_auto_mark_title
import sodogku.libraries.resources.generated.resources.tutorial_bones_body
import sodogku.libraries.resources.generated.resources.tutorial_bones_title
import sodogku.libraries.resources.generated.resources.tutorial_got_it
import sodogku.libraries.resources.generated.resources.tutorial_graduation_body
import sodogku.libraries.resources.generated.resources.tutorial_graduation_title
import sodogku.libraries.resources.generated.resources.tutorial_mark_square_body
import sodogku.libraries.resources.generated.resources.tutorial_mark_square_title
import sodogku.libraries.resources.generated.resources.tutorial_place_and_watch_body
import sodogku.libraries.resources.generated.resources.tutorial_place_and_watch_title
import sodogku.libraries.resources.generated.resources.tutorial_place_dog_body
import sodogku.libraries.resources.generated.resources.tutorial_place_dog_title
import sodogku.libraries.resources.generated.resources.tutorial_rule_line_body
import sodogku.libraries.resources.generated.resources.tutorial_rule_line_title
import sodogku.libraries.resources.generated.resources.tutorial_rule_region_body
import sodogku.libraries.resources.generated.resources.tutorial_rule_region_title
import sodogku.libraries.resources.generated.resources.tutorial_rule_touching_body
import sodogku.libraries.resources.generated.resources.tutorial_rule_touching_title
import sodogku.libraries.resources.generated.resources.tutorial_skip
import sodogku.libraries.resources.generated.resources.tutorial_sniff_body
import sodogku.libraries.resources.generated.resources.tutorial_sniff_title
import sodogku.libraries.resources.generated.resources.tutorial_starter_dog_body
import sodogku.libraries.resources.generated.resources.tutorial_starter_dog_body_manual
import sodogku.libraries.resources.generated.resources.tutorial_starter_dog_title
import sodogku.libraries.resources.generated.resources.tutorial_target_action
import sodogku.libraries.resources.generated.resources.tutorial_treat_body
import sodogku.libraries.resources.generated.resources.tutorial_treat_title
import sodogku.libraries.resources.generated.resources.tutorial_try_wrong_body
import sodogku.libraries.resources.generated.resources.tutorial_try_wrong_title
import sodogku.libraries.resources.generated.resources.tutorial_wrong_explained_body
import sodogku.libraries.resources.generated.resources.tutorial_wrong_explained_title
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import sodogku.libraries.resources.generated.resources.hint_apply
import sodogku.libraries.resources.generated.resources.hint_discard
import sodogku.libraries.resources.generated.resources.hint_found_many
import sodogku.libraries.resources.generated.resources.hint_found_one
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth

/** The thing the last-bone warning points at. */
val LivesFocusKey = FocusTargetKey("game.lives")

/** One focus key per board square, so a spotlight can light several at once. */
fun cellFocusKey(cell: Int) = FocusTargetKey("game.cell.$cell")

val SniffFocusKey = FocusTargetKey("game.booster.sniff")
val TreatFocusKey = FocusTargetKey("game.booster.treat")

/**
 * Dims the board and lights up the bones when the player is down to their last
 * one.
 *
 * Fires once per attempt, on the *edge* into one life rather than whenever one
 * life happens to be showing — a warning that reappears every time the board
 * redraws is noise, and noise is how a warning stops being read.
 */
@Composable
fun BoxScope.LastBoneWarning(state: GameState, onAction: (GameAction) -> Unit) {
    FocusScrim(
        spotlight = state.warning?.let {
            Spotlight(targets = setOf(LivesFocusKey), dismissOnOutsideTap = true)
        },
        onDismiss = { onAction(GameAction.DismissWarning) },
    ) { anchor ->
        SpeechBubble(anchorBottomPx = anchor.bottom) {
            Text(
                text = stringResource(Res.string.game_last_bone_title),
                typography = AppTheme.typography.Heading.H600,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.game_last_bone_body),
                typography = AppTheme.typography.Body.B400,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The sniff's answer: the squares it ruled out, lit through the scrim, and the
 * one tap that keeps them.
 *
 * It shows where a dog *cannot* go, never where one does. A hint that hands over
 * the answer ends the puzzle; one that rules squares out leaves the deduction
 * intact and shows the technique that found them.
 *
 * The decision lives **in** the bubble rather than in a bar under the board, and
 * that is not a layout preference. The scrim is modal: buttons outside it are
 * dimmed and unreachable, so a player would have had to dismiss the highlight to
 * reach the thing the highlight was asking about. Putting the choice next to the
 * lit squares also means the eye is already where the answer is.
 *
 * Tapping the scrim leaves the squares alone rather than keeping them. Dismissing
 * by accident is the common mistake, and the recoverable outcome is the one that
 * marks nothing: the sniff can be looked at again, but crosses the player did not
 * ask for have to be found and undone one at a time.
 */
@Composable
fun BoxScope.SniffHint(state: GameState, onAction: (GameAction) -> Unit) {
    FocusScrim(
        spotlight = state.hintCells
            .takeIf { it.isNotEmpty() }
            ?.let { cells -> Spotlight(targets = cells.map(::cellFocusKey).toSet()) },
        onDismiss = { onAction(GameAction.DiscardHint) },
    ) {
        SpeechBubble(anchorBottomPx = 0f) {
            Text(
                text = stringResource(Res.string.hint_title),
                typography = AppTheme.typography.Heading.H600,
                textAlign = TextAlign.Center,
            )
            Text(
                // The count, because on a 10x10 the lit squares are spread out
                // and "ruled out 6" is what says the charge was worth taking.
                text = if (state.hintCells.size == 1) {
                    stringResource(Res.string.hint_found_one)
                } else {
                    stringResource(Res.string.hint_found_many, state.hintCells.size)
                },
                typography = AppTheme.typography.Body.B400,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            // Stacked, not side by side. The bubble is narrower than the
            // screen and two buttons in a row truncated the second one to
            // "LEAV..." on a 1080p phone. This is also what every dialog in the
            // app does, so the shape is already familiar.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimension.D300),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ButtonPrimary(
                    onClick = { onAction(GameAction.ApplyHint) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.hint_apply))
                }
                ButtonGhost(
                    onClick = { onAction(GameAction.DiscardHint) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.hint_discard))
                }
            }
        }
    }
}

/**
 * The guided run over [TutorialBoard], drawn entirely from [GameState.tutorial].
 *
 * Every decision — which step, which squares, whether it is finished — was made
 * in the ViewModel. This maps a step to a spotlight and a piece of copy, and
 * that is the whole of it.
 *
 * Two shapes of step, and the difference matters:
 *
 * - **A step you read.** Tap anywhere to move on, so it can never trap anyone.
 * - **A step you do.** The lit square stays live and everything else is dead,
 *   which is SPEC 10's "only the correct cell is tappable". An outside tap is
 *   swallowed rather than dismissing, because dismissing would clear a lesson
 *   the player has not done yet — and the skip button is right there.
 */
@Composable
fun BoxScope.TutorialCoachMark(state: GameState, onAction: (GameAction) -> Unit) {
    val step = state.tutorial
    // Held so the card does not vanish a frame before the scrim finishes fading
    // out on the last step.
    val shown = rememberLastNonNull(step)
    val live = shown != null && Tutorial.triggerFor(shown) != TutorialTrigger.Tap
    val cellByKey = state.tutorialCells.associateBy(::cellFocusKey)
    val targetLabel = spokenTarget(state, shown.takeIf { live })

    FocusScrim(
        spotlight = step?.let {
            Spotlight(
                targets = targetsFor(it, state.tutorialCells),
                dismissOnOutsideTap = !live,
                targetsAreLive = live,
                targetLabel = targetLabel,
            )
        },
        onDismiss = { onAction(GameAction.TutorialAdvance) },
        // The scrim covers the board, so the lit square's own tap handler never
        // runs. Its taps arrive here instead and go in as the same action the
        // cell would have sent — including the second one, so the double tap
        // that places a dog still works through the lesson.
        onTargetTap = { key -> cellByKey[key]?.let { onAction(GameAction.CellTapped(it)) } },
        // The same square, reached without a finger. A screen reader has no
        // double tap to give — its activation is one indivisible act — so the
        // gesture the step is waiting for is synthesised here, exactly as
        // `placeAt` does for an ordinary board square: two `CellTapped`s land on
        // one channel microseconds apart and `GameViewModel`'s own 320ms window
        // reads them as a commit. Nothing in the state machine needs to know a
        // screen reader exists.
        onTargetActivate = { key ->
            cellByKey[key]?.let { cell ->
                onAction(GameAction.CellTapped(cell))
                if (shown != null && Tutorial.triggerFor(shown) != TutorialTrigger.Marked) {
                    onAction(GameAction.CellTapped(cell))
                }
            }
        },
    ) { anchor ->
        if (shown == null) return@FocusScrim
        CoachMark(
            anchor = anchor,
            title = stringResource(titleOf(shown)),
            body = stringResource(bodyOf(shown, state.autoMarkVisible)),
            confirmLabel = stringResource(Res.string.tutorial_got_it).takeIf { !live },
            onConfirm = { onAction(GameAction.TutorialAdvance) },
            skipLabel = stringResource(Res.string.tutorial_skip),
            onSkip = { onAction(GameAction.SkipTutorial) },
        )
    }
}

/**
 * The last non-null value of [value].
 *
 * Written and read in the same composition, in that order, so nothing reads a
 * stale slot. It exists only so the exit animation has something to draw.
 */
@Composable
private fun rememberLastNonNull(value: TutorialStep?): TutorialStep? {
    val holder = remember { mutableStateOf(value) }
    if (value != null) holder.value = value
    return holder.value
}

/**
 * What the spotlight lights.
 *
 * The three rule steps used to point at their chip under the header, and that
 * is the whole of what was wrong with them: a chip is a diagram of a rule, and
 * a player learning the game does not yet know it is a diagram of *this* board.
 * They fall through to [cells] now, which `Tutorial.cellsFor` fills with the
 * column, the colour block or the ring the rule actually ruled out — squares
 * with crosses already on them, around the dog that put them there.
 *
 * Only the four things that are not on the board keep a key of their own.
 */
private fun targetsFor(step: TutorialStep, cells: Set<Int>): Set<FocusTargetKey> = when (step) {
    TutorialStep.Bones, TutorialStep.WrongExplained -> setOf(LivesFocusKey)
    TutorialStep.Sniff -> setOf(SniffFocusKey)
    TutorialStep.Treat -> setOf(TreatFocusKey)
    TutorialStep.Graduation -> emptySet()
    else -> cells.map(::cellFocusKey).toSet()
}

/**
 * What the lit square is called, for a player who cannot see it lit.
 *
 * Reuses the board's own vocabulary rather than minting tutorial copy: the
 * square announces itself in the same words `BoardCellLabels` uses, so "row 2,
 * column 1" means the same thing inside the lesson as it does after it, and the
 * action is the one the board's own semantics offer. Null on a step that only
 * shows something — there is nothing to activate, so there is nothing to name.
 *
 * A gated step lights exactly one square (`cellsFor` returns `setOfNotNull`),
 * which is why one label covers the whole spotlight.
 */
@Composable
private fun spokenTarget(state: GameState, step: TutorialStep?): String? {
    val board = state.level?.board ?: return null
    val cell = state.tutorialCells.singleOrNull() ?: return null
    val action = when (step?.let(Tutorial::triggerFor)) {
        TutorialTrigger.Marked -> stringResource(Res.string.board_action_mark)
        TutorialTrigger.Placed, TutorialTrigger.Struck -> stringResource(Res.string.board_action_place)
        else -> return null
    }
    // One-based, because that is what a person counts in — the same conversion
    // `BoardCellLabels.describe` makes, and for the same reason.
    return stringResource(
        Res.string.tutorial_target_action,
        action,
        board.rowOf(cell) + 1,
        board.colOf(cell) + 1,
    )
}

private fun titleOf(step: TutorialStep): StringResource = when (step) {
    TutorialStep.StarterDog -> Res.string.tutorial_starter_dog_title
    TutorialStep.RuleRegion -> Res.string.tutorial_rule_region_title
    TutorialStep.RuleLine -> Res.string.tutorial_rule_line_title
    TutorialStep.RuleTouching -> Res.string.tutorial_rule_touching_title
    TutorialStep.MarkSquare -> Res.string.tutorial_mark_square_title
    TutorialStep.PlaceDog -> Res.string.tutorial_place_dog_title
    TutorialStep.Bones -> Res.string.tutorial_bones_title
    TutorialStep.PlaceAndWatch -> Res.string.tutorial_place_and_watch_title
    TutorialStep.AutoMark -> Res.string.tutorial_auto_mark_title
    TutorialStep.Sniff -> Res.string.tutorial_sniff_title
    TutorialStep.Treat -> Res.string.tutorial_treat_title
    TutorialStep.TryAWrongOne -> Res.string.tutorial_try_wrong_title
    TutorialStep.WrongExplained -> Res.string.tutorial_wrong_explained_title
    TutorialStep.Graduation -> Res.string.tutorial_graduation_title
}

/**
 * [autoMark] reaches exactly one lesson.
 *
 * `Tutorial.scriptFor` drops the two steps that are *about* auto-mark when it is
 * off, but [TutorialStep.StarterDog] survives and its copy ends "and every
 * square it rules out is crossed off" — pointed at a board where none of them
 * are. Every other card here says the same thing either way.
 */
private fun bodyOf(step: TutorialStep, autoMark: Boolean): StringResource = when (step) {
    TutorialStep.StarterDog -> if (autoMark) {
        Res.string.tutorial_starter_dog_body
    } else {
        Res.string.tutorial_starter_dog_body_manual
    }
    TutorialStep.RuleRegion -> Res.string.tutorial_rule_region_body
    TutorialStep.RuleLine -> Res.string.tutorial_rule_line_body
    TutorialStep.RuleTouching -> Res.string.tutorial_rule_touching_body
    TutorialStep.MarkSquare -> Res.string.tutorial_mark_square_body
    TutorialStep.PlaceDog -> Res.string.tutorial_place_dog_body
    TutorialStep.Bones -> Res.string.tutorial_bones_body
    TutorialStep.PlaceAndWatch -> Res.string.tutorial_place_and_watch_body
    TutorialStep.AutoMark -> Res.string.tutorial_auto_mark_body
    TutorialStep.Sniff -> Res.string.tutorial_sniff_body
    TutorialStep.Treat -> Res.string.tutorial_treat_body
    TutorialStep.TryAWrongOne -> Res.string.tutorial_try_wrong_body
    TutorialStep.WrongExplained -> Res.string.tutorial_wrong_explained_body
    TutorialStep.Graduation -> Res.string.tutorial_graduation_body
}

/**
 * A card that hangs just below whatever the spotlight is lighting, so the copy
 * and the thing it is describing read as one object.
 */
@Composable
private fun BoxScope.SpeechBubble(
    anchorBottomPx: Float,
    content: @Composable () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Never higher than MinTop: an anchor at the very top of the screen (a
    // spotlight with no single thing to hang off) would otherwise put the card
    // under the status bar.
    val top = maxOf(with(density) { anchorBottomPx.toDp() } + Dimension.D600, MinTop)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D300),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = top)
            .padding(horizontal = Dimension.D800)
            .widthIn(max = BubbleMaxWidth)
            .clip(Radii.Card)
            .background(AppTheme.colors.surfacePrimary.color)
            .padding(Dimension.D700),
    ) {
        content()
    }
}

private val BubbleMaxWidth = Dimension.D1900 * 3

/** Clears the status bar and the header when there is no anchor to hang from. */
private val MinTop = Dimension.D1900
