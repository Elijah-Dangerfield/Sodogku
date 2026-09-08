package com.sodogku.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sodogku.libraries.ui.components.game.RewardButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.features.achievements.AchievementCopy
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.feedback.UnlockToastItem
import com.sodogku.libraries.ui.components.feedback.UnlockToasts
import com.sodogku.libraries.ui.components.FullScreenLoader
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.board.BoardCell
import com.sodogku.libraries.ui.components.board.BoardCellState
import com.sodogku.libraries.ui.components.board.BoardCellGap
import com.sodogku.libraries.ui.components.board.BoardSurface
import com.sodogku.libraries.ui.components.board.dragAcrossCells
import com.sodogku.libraries.ui.components.board.rememberPlacementPulse
import com.sodogku.libraries.ui.components.game.BoosterButton
import com.sodogku.libraries.ui.components.game.FloatingPoints
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.game.DogCounter
// The Treat's identity now has two call sites — this button and the level
// pane's reward chip — so it lives in the design system rather than here.
import com.sodogku.libraries.ui.components.game.TreatColor
import com.sodogku.libraries.ui.components.game.HudPill
import com.sodogku.libraries.ui.components.game.LifeRow
import com.sodogku.libraries.ui.components.game.RuleChip
import com.sodogku.libraries.ui.components.game.RuleChipGroup
import com.sodogku.libraries.ui.components.game.RuleDiagram
import com.sodogku.libraries.ui.components.game.ScoreCounter
import com.sodogku.libraries.ui.components.icon.IconButton
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.system.coveredByOverlay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sodogku.libraries.ui.system.focusTarget
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.scoring.Praise
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.achievements_unlocked_toast
import sodogku.libraries.resources.generated.resources.daily_streak_label
import sodogku.libraries.resources.generated.resources.game_level_label
import sodogku.libraries.resources.generated.resources.game_levels_menu
import sodogku.libraries.resources.generated.resources.game_settings
import sodogku.libraries.resources.generated.resources.game_score_label
import sodogku.libraries.resources.generated.resources.game_rule_no_touching
import sodogku.libraries.resources.generated.resources.game_rule_one_per_line
import sodogku.libraries.resources.generated.resources.game_rule_one_per_region
import sodogku.libraries.resources.generated.resources.game_bones_remaining
import sodogku.libraries.resources.generated.resources.game_free_bones
import sodogku.libraries.resources.generated.resources.game_sniff
import sodogku.libraries.resources.generated.resources.game_treat
import kotlin.math.sin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import sodogku.libraries.resources.generated.resources.hint_apply
import sodogku.libraries.resources.generated.resources.hint_discard
import sodogku.libraries.resources.generated.resources.hint_found_many
import sodogku.libraries.resources.generated.resources.hint_found_one
import com.sodogku.libraries.ui.components.game.BoardControl
import com.sodogku.libraries.ui.components.game.BoardControlBone
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import androidx.compose.ui.graphics.Color

/**
 * The board and everything around it. A pure render of [GameState]; every
 * decision, including whether a tap was right, lives in [GameViewModel].
 */
@Composable
fun GameScreen(
    state: GameState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<GameDialog?>(null) }

    Screen(modifier = modifier) { padding ->
        val level = state.level
        if (level == null) {
            FullScreenLoader()
            return@Screen
        }

        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimension.D500)
                .coveredByOverlay(state.isCovered),
        ) {
            GameHeader(
                state = state,
                levelId = level.id,
                onOpenLevels = { onAction(GameAction.LevelsOpened) },
                onSettings = { onAction(GameAction.OpenSettings) },
                onExplainBones = { onAction(GameAction.BoosterTapped(Consumable.Bone)) },
                onExplainDogs = { dialog = GameDialog.Dogs },
                onExplainLevel = { dialog = GameDialog.Level },
                onExplainScore = { dialog = GameDialog.Score },
            )

            // The rules sit directly under the header rather than floating above
            // the board: they are reference material, and a gap between them and
            // the score reads as a hole on a small grid.
            RuleChips(state = state, onExplain = { dialog = GameDialog.Rules })

            Spacer(modifier = Modifier.weight(SpaceAboveBoard))

            Box(contentAlignment = Alignment.Center) {
                BoardGrid(state = state, onAction = onAction)
                FloatingPoints(
                    points = state.lastPoints,
                    praise = state.lastPraise.takeIf { it != Praise.None }?.name,
                    nonce = state.pointsNonce,
                )
            }

            Spacer(modifier = Modifier.weight(SpaceBelowBoard))

            // `boosters.enabled` off already stops the economy — a tap spends
            // nothing and an ad refill refuses — but leaving the buttons on
            // screen would advertise two controls that decline to work.
            if (state.boostersEnabled) {
                BoosterBar(state = state, onAction = onAction)
            }

            Spacer(modifier = Modifier.height(Dimension.D700))
        }

            // The outcome covers the board rather than replacing it: the player
            // should still see the grid they just finished (or ran out of bones
            // on) behind the sheet. A daily recap covers it too — the day is
            // over, so the board underneath is a backdrop and not a puzzle.
            if (state.phase != GamePhase.Playing && state.phase != GamePhase.Loading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppTheme.colors.backgroundOverlay.color),
                ) {
                    GameOutcomeSheet(state = state, onAction = onAction)
                }
            }

            // Over the sheet, not inside it. Several badges can land at once — a
            // first clear can earn First Steps, Perfect Form and Speed Demon in
            // the same second — and they must not push Next level down the card.
            // Gone entirely when the player has turned badges off.
            if (state.showAchievements && state.newBadges.isNotEmpty()) {
                UnlockToasts(
                    items = state.newBadges.map { badge ->
                        UnlockToastItem(
                            glyph = AchievementCopy.glyph(badge.id),
                            label = stringResource(Res.string.achievements_unlocked_toast),
                            title = stringResource(AchievementCopy.name(badge.id)),
                        )
                    },
                    onDismiss = {},
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(padding)
                        .padding(top = Dimension.D700),
                )
            }

            LastBoneWarning(state = state, onAction = onAction)

            SniffHint(state = state, onAction = onAction)

            // Last of the three scrims, so a lesson is never dimmed by one of
            // the others if they ever overlap.
            TutorialCoachMark(state = state, onAction = onAction)

            LevelDrawer(
                open = state.drawerOpen,
                currentLevelId = level.id.takeIf { !state.isDaily },
                unlockedThrough = state.unlockedThrough,
                canJumpAnywhere = state.isPro,
                records = state.records,
                treatBands = state.treatBands,
                onPick = { onAction(GameAction.GoToLevel(it)) },
                onDismiss = { onAction(GameAction.LevelsClosed) },
                daily = state.daily,
                isDailyBoard = state.isDaily,
                onPlayDaily = { onAction(GameAction.PlayDaily) },
                onUseFreeze = { onAction(GameAction.UseFreeze) },
                onRestoreStreak = { onAction(GameAction.RestoreStreak) },
                onOpenStreak = { onAction(GameAction.OpenStreak) },
            )
        }

        state.freezeMessage?.let { message ->
            FreezeMessageDialog(
                message = message,
                onDismiss = { onAction(GameAction.DismissFreezeMessage) },
            )
        }

        if (state.forfeitPrompt) {
            ForfeitDailyDialog(
                onConfirm = { onAction(GameAction.ForfeitDailyConfirmed) },
                onDismiss = { onAction(GameAction.DismissForfeitPrompt) },
            )
        }

        state.boosterPrompt?.let { booster ->
            BoosterPrompt(
                consumable = booster,
                held = when (booster) {
                    Consumable.Bone -> state.livesRemaining
                    Consumable.Sniff -> state.sniffs
                    Consumable.Treat -> state.treats
                },
                refillTo = state.refillTo,
                onUse = { onAction(GameAction.BoosterConfirmed(booster)) },
                onWatchAd = { onAction(GameAction.BoosterRefillRequested(booster)) },
                onDismiss = { onAction(GameAction.DismissBoosterPrompt) },
            )
        }

        // The daily explainer wins over anything the player opened themselves.
        // It cannot collide in practice — it is shown as the board loads, before
        // there is anything to tap — but one host draws one dialog, and leaving
        // the precedence implicit would make that a coincidence rather than a
        // rule.
        GameDialogHost(
            dialog = if (state.showDailyIntro) GameDialog.DailyIntro else dialog,
            state = state,
            onAction = onAction,
            onDismiss = {
                if (state.showDailyIntro) {
                    onAction(GameAction.DailyIntroDismissed)
                } else {
                    dialog = null
                }
            },
            onOpenPrivacy = { onAction(GameAction.OpenPrivacy) },
            onOpenTerms = { onAction(GameAction.OpenTerms) },
            onOpenFeedback = { onAction(GameAction.OpenFeedback) },
            appVersion = BuildInfo.versionName,
        )
    }
}

@Composable
private fun GameHeader(
    state: GameState,
    levelId: Int,
    onOpenLevels: () -> Unit,
    onSettings: () -> Unit,
    onExplainBones: () -> Unit,
    onExplainDogs: () -> Unit,
    onExplainLevel: () -> Unit,
    onExplainScore: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimension.D400),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // White circles on the cream, rather than bare glyphs. Two icons
        // floating on a page read as decoration; the same icons on discs read
        // as the two things on this screen that are buttons.
        IconButton(
            icon = Icons.Menu(stringResource(Res.string.game_levels_menu)),
            onClick = onOpenLevels,
            backgroundColor = AppTheme.colors.surfacePrimary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D1000)) {
            // A daily's level id is a position in the daily pool, which means
            // nothing to the player and reads as a campaign level they have not
            // reached. The streak is the number that belongs here instead.
            //
            // The rehearsal board has no number at all, and inventing one is the
            // trap: labelling it "Level 1" would put the player on a board that
            // is not level 1 and then swap it under them at graduation, and
            // "Level 0" is an id leaking out of a constant. It shows nothing,
            // and the first coach mark says in words what board this is.
            when {
                state.isRehearsal -> Unit
                state.isDaily -> HeaderStat(
                    label = stringResource(Res.string.daily_streak_label),
                    value = (state.daily?.streak ?: 0).toString(),
                    onClick = onExplainLevel,
                )
                else -> HeaderStat(
                    label = stringResource(Res.string.game_level_label),
                    value = levelId.toString(),
                    onClick = onExplainLevel,
                )
            }
            HeaderStat(
                label = stringResource(Res.string.game_score_label),
                value = null,
                onClick = onExplainScore,
                content = { ScoreCounter(score = state.lifetimeScore) },
            )
        }

        IconButton(
            icon = Icons.Settings(stringResource(Res.string.game_settings)),
            onClick = onSettings,
            backgroundColor = AppTheme.colors.surfacePrimary,
        )
    }

    // Two pills, centred, next to each other. Pushed to opposite edges — which
    // is what `SpaceBetween` did here — they stop being a pair and leave a hole
    // down the middle of the screen exactly where the eye travels between the
    // score and the board.
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Dimension.D400),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D400, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DogCounter(
            found = state.dogsPlaced,
            total = state.dogsRequired,
            onClick = onExplainDogs,
        )
        // The bones are drawn, not written, so merging descendants finds
        // nothing to merge — this is the one pill that has to state its own
        // name. It is also the number a player most wants read back to them.
        val bones = stringResource(Res.string.game_bones_remaining, state.livesRemaining)
        HudPill(
            modifier = Modifier
                .focusTarget(LivesFocusKey)
                .semantics { contentDescription = bones }
                .bounceClick(onClick = onExplainBones),
        ) {
            // The row is as long as the holding when the holding is bigger than
            // the usual three. `boosters.refillTo` is a config number, so an
            // operator raising it must not leave the header drawing three bones
            // over a count of five.
            LifeRow(
                remaining = state.livesRemaining,
                total = maxOf(state.livesRemaining, state.refillTo),
            )
        }
    }
}

/**
 * A stat with its label above it.
 *
 * The number is on the **Display** scale, not the heading scale. The level and
 * the score are the two things a player glances at mid-puzzle, and at heading
 * size they read as chrome — one more label in a screen already full of them.
 * Poppins is near-circular by design, so the same digits at display size stop
 * looking like a status bar and start looking like part of the game.
 *
 * The label above stays small and quiet: it is there to say which number this
 * is, once, and never to be read again.
 */
@Composable
private fun HeaderStat(
    label: String,
    value: String?,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val statLabel = if (value != null) "$label $value" else label
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            // The number is drawn by `content` for the score, so the stat has to
            // say what it is; merged children would read "Level 1" as two nodes.
            .semantics { contentDescription = statLabel }
            .bounceClick(onClick = onClick),
    ) {
        Text(
            text = label,
            // Body rather than the caption scale. The next caption step up is
            // 10sp, which is not a perceptible change from 8; at 12 the word
            // still reads as quiet chrome next to a 24sp number, and it is
            // legible, which 8sp was not.
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )
        if (value != null) {
            Text(text = value, typography = AppTheme.typography.Display.D900)
        } else {
            content()
        }
    }
}

@Composable
private fun RuleChips(state: GameState, onExplain: () -> Unit) {
    val broken = brokenRule(state)
    RuleChipGroup {
        RuleChip(
            diagram = RuleDiagram.OnePerRegion,
            label = stringResource(Res.string.game_rule_one_per_region),
            modifier = Modifier.weight(WEIGHT_FILL),
            highlighted = broken == RuleDiagram.OnePerRegion,
            onClick = onExplain,
        )
        RuleChip(
            diagram = RuleDiagram.OnePerLine,
            label = stringResource(Res.string.game_rule_one_per_line),
            modifier = Modifier.weight(WEIGHT_FILL),
            highlighted = broken == RuleDiagram.OnePerLine,
            onClick = onExplain,
        )
        RuleChip(
            diagram = RuleDiagram.NoTouching,
            label = stringResource(Res.string.game_rule_no_touching),
            modifier = Modifier.weight(WEIGHT_FILL),
            highlighted = broken == RuleDiagram.NoTouching,
            onClick = onExplain,
        )
    }
}

/**
 * Which of the three rules the player's last wrong guess ran into, or null when
 * it ran into none of them.
 *
 * Null is the common answer and it matters that it is representable: most wrong
 * guesses on a partly-solved board break no *visible* rule at all — the square
 * simply is not where the dog goes, and nothing on screen yet says why. Pointing
 * at a rule there would be inventing a reason.
 *
 * The order is deliberate and is not the order the chips are drawn in.
 * Adjacency is checked first because it is the rule players forget, and because
 * an orthogonally adjacent square also breaks the row-and-column rule — check
 * that one first and the subtler answer is never reached. A diagonal neighbour
 * breaks adjacency alone, which is exactly the case worth naming.
 */
internal fun brokenRule(state: GameState): RuleDiagram? {
    val board = state.level?.board ?: return null
    val cell = state.strikeCell?.takeIf { state.strikeNonce > 0 } ?: return null
    val placed = state.placedCells
    if (placed.isEmpty()) return null

    val row = board.rowOf(cell)
    val col = board.colOf(cell)
    return when {
        placed.any { touches(board.rowOf(it), board.colOf(it), row, col) } -> RuleDiagram.NoTouching
        placed.any { board.regionAt(it) == board.regionAt(cell) } -> RuleDiagram.OnePerRegion
        placed.any { board.rowOf(it) == row || board.colOf(it) == col } -> RuleDiagram.OnePerLine
        else -> null
    }
}

private fun touches(row: Int, col: Int, otherRow: Int, otherCol: Int): Boolean {
    val dr = row - otherRow
    val dc = col - otherCol
    return dr in -1..1 && dc in -1..1 && !(dr == 0 && dc == 0)
}

/**
 * The grid, sized to whatever width it is given.
 *
 * The cell size is derived from the available width rather than fixed, because a
 * 4x4 and a 10x10 have to fill the same space on the same phone — and a board
 * that shrinks as levels get harder would feel like a punishment.
 */
@Composable
private fun BoardGrid(state: GameState, onAction: (GameAction) -> Unit) {
    val level = state.level ?: return
    val size = level.size

    // The whole board flinches on a wrong guess, on top of the cell's own
    // shake. The cell shake says *which* square was wrong; this says the board
    // rejected something, and it is the part you see when you are looking at
    // the square you tapped rather than at the grid.
    //
    // Keyed on `strikeNonce` and not `shakeNonce`, deliberately: a tap the board
    // merely refuses, on a dog already placed, is not a mistake and does not
    // deserve the whole screen reacting to it.
    val boardShake = remember { Animatable(0f) }
    LaunchedEffect(state.strikeNonce) {
        boardShake.snapTo(0f)
        if (state.strikeNonce == 0 || state.reduceAnimations) return@LaunchedEffect
        boardShake.animateTo(1f, tween(Motion.ShakeMillis))
    }

    BoardSurface(
        size = size,
        // Read inside the layer, never in composition: the board is the most
        // expensive subtree in the app and subscribing it to every frame of a
        // shake would recompose a hundred cells sixty times a second.
        modifier = Modifier.graphicsLayer {
            val progress = boardShake.value
            translationX = sin(progress * BoardShakeCycles) * BoardShakeTravel.toPx() * (1f - progress)
        },
    ) {
        // Keyed on the level, because `nextLevel` swaps the board *in place*
        // rather than navigating. Without a key the cells are memoised by
        // position and survive the swap, and two things follow from that.
        //
        // Each cell's entrance animation is `remember { Animatable(0f) }` driven
        // by `LaunchedEffect(Unit)`, so it plays once per process: level 2
        // onward simply appeared, fully formed. And a cell that held a dog kept
        // `pop` at 1f, so the new board's `Empty` state drove it back down and
        // the *previous* level's dogs animated away on top of the new puzzle.
        //
        // On a size change only the newly added rows and columns animated, which
        // is the version of this that looks like a rendering glitch rather than
        // a missing flourish.
        key(level.id) { BoardRows(state = state, size = size, onAction = onAction) }
    }
}

@Composable
private fun BoardRows(state: GameState, size: Int, onAction: (GameAction) -> Unit) {
    val level = state.level ?: return
    // `GameState.placedCells` is a `get()` that rebuilds a set. Read once for
    // the whole grid rather than twice per square — a 10x10 asked for it two
    // hundred times per recomposition, which is a hundred throwaway sets the
    // board never needed and the sort of thing that only shows up when
    // something else makes you measure.
    val placed = state.placedCells
    val placement = rememberPlacementPulse(placed, size)

    BoxWithConstraints {
        val cell = (maxWidth - BoardCellGap * (size - 1)) / size

        Column(
            verticalArrangement = Arrangement.spacedBy(BoardCellGap),
            // On the grid rather than on each square: a stroke that has left a
            // square is no longer that square's business, and the run of squares
            // it crosses is only knowable from here. Gated exactly as the cells
            // themselves are, so the two paths cannot disagree about whether the
            // board is live.
            modifier = Modifier.dragAcrossCells(
                size = size,
                cellSize = cell,
                gap = BoardCellGap,
                enabled = state.phase == GamePhase.Playing,
                onDragStart = { onAction(GameAction.DragStarted(it)) },
                onDragEnter = { onAction(GameAction.DragCrossed(it)) },
                onDragEnd = { onAction(GameAction.DragEnded) },
            ),
        ) {
            repeat(size) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(BoardCellGap)) {
                    repeat(size) { col ->
                        val index = level.board.cellAt(row, col)
                        // Registering costs an `onGloballyPositioned` per cell,
                        // so only the squares something is currently pointing at
                        // carry one — not all 100 of a 10x10.
                        val spotlit = index in state.hintCells || index in state.tutorialCells
                        BoardCell(
                            modifier = if (spotlit) {
                                Modifier.focusTarget(cellFocusKey(index))
                            } else {
                                Modifier
                            },
                            region = level.board.regionAt(index),
                            state = cellState(state, index, placed),
                            row = row,
                            column = col,
                            size = cell,
                            colorblind = state.colorblind,
                            strikeNonce = if (state.shakeCell == index) state.shakeNonce else 0,
                            placementNonce = placement.nonce,
                            placementRole = placement.roleOf(index),
                            entranceDelayMillis = if (state.reduceAnimations) {
                                0
                            } else {
                                (row + col) * Motion.BoardWaveStepMillis
                            },
                            animationOffset = index,
                            animated = !state.reduceAnimations,
                            enabled = state.phase == GamePhase.Playing,
                            onTap = { onAction(GameAction.CellTapped(index)) },
                            onPlace = placeAt(state, index, placed, onAction),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The screen-reader placement action for one square, or null where there is
 * nothing to place.
 *
 * **Two `CellTapped`s, not a new action.** The double tap is not a gesture the
 * ViewModel is told about — it is two ordinary taps that happened to arrive
 * close together, measured against a clock in `GameViewModel.tap`. Sending both
 * halves is therefore not a trick played on the state machine, it is the same
 * input a thumb produces: the mark is made and then converted, the same events
 * fire, the same tutorial triggers advance, and a wrong guess costs the same
 * bone. Nothing here needs the ViewModel to know that a screen reader exists.
 *
 * Actions are handled in order off one channel, so the second lands microseconds
 * after the first and always inside the 320ms window. If that window ever moves
 * to a place a synthetic pair cannot reach, this becomes an action of its own.
 *
 * Null on a square that already draws a cross the board put there. Offering a
 * placement on one would announce a control for something the player can see is
 * ruled out — so the test is [GameState.visibleAutoMarks] and not the deduction
 * behind it. That matters twice over. With auto-mark off nothing is crossed off,
 * every empty square offers the action, and a screen-reader player is not
 * quietly locked out of most of the board; and an auto-mark the player tapped
 * away reads as empty, so it now offers the action too, which it did not before.
 */
internal fun placeAt(
    state: GameState,
    cell: Int,
    placed: Set<Int> = state.placedCells,
    onAction: (GameAction) -> Unit,
): (() -> Unit)? {
    if (state.phase != GamePhase.Playing) return null
    if (cell in state.visibleAutoMarks || cell in placed || cell in state.wrongGuesses) return null
    return {
        onAction(GameAction.CellTapped(cell))
        onAction(GameAction.CellTapped(cell))
    }
}

private fun cellState(state: GameState, cell: Int, placed: Set<Int>): BoardCellState = when {
    cell in placed -> BoardCellState.Occupied
    cell in state.wrongGuesses -> BoardCellState.Wrong
    cell in state.manualMarks -> BoardCellState.Marked
    // The drawn set, which is `autoMarks` less the ones the player has tapped
    // away and empty entirely when the setting is off. The deduction itself is
    // unchanged in both cases — the board simply stops saying it out loud.
    cell in state.visibleAutoMarks -> BoardCellState.Marked
    // After the committed states, deliberately: a square the sniff proposed
    // that the player has since crossed off themselves is theirs, and should
    // not weaken back to a suggestion.
    cell in state.hintCells -> BoardCellState.Proposed
    else -> BoardCellState.Empty
}

/**
 * The two boosters and the standing ad offer.
 *
 * The ad button lives here rather than in the header because this row is where a
 * stuck player is already looking. It stays on screen at all times so nobody has
 * to run out of something to discover the offer exists — but it greys out once
 * bones are full, because selling an ad for nothing is worse than not offering
 * one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoosterBar(state: GameState, onAction: (GameAction) -> Unit) {
    // A `Row` at the largest system font ran the three controls off the edge and
    // broke "Free bones" across two lines as "Free bone / s". Nothing here is
    // ordered or paired, so the honest answer to not fitting is to wrap onto a
    // second line rather than to shrink the labels or clip the offer.
    val playing = state.phase == GamePhase.Playing
    FlowRow(
        verticalArrangement = Arrangement.spacedBy(Dimension.D400),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D800, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.Top,
    ) {
        BoardControl(
            label = stringResource(Res.string.game_sniff),
            count = state.sniffs,
            modifier = Modifier.focusTarget(SniffFocusKey),
            enabled = playing,
            attention = playing && state.sniffs > 0 &&
                state.hintCells.isEmpty() && state.isStruggling,
            onClick = { onAction(GameAction.BoosterTapped(Consumable.Sniff)) },
        ) {
            Dog(pose = DogPose.Focused, size = BoardControlDog)
        }
        BoardControl(
            label = stringResource(Res.string.game_treat),
            count = state.treats,
            modifier = Modifier.focusTarget(TreatFocusKey),
            enabled = playing,
            onClick = { onAction(GameAction.BoosterTapped(Consumable.Treat)) },
        ) {
            BoardControlBone(fill = TreatColor, edge = TreatEdge)
        }
        BoardControl(
            label = stringResource(Res.string.game_free_bones),
            // Against `boosters.refillTo`, not the compile-time three: the
            // refill tops up to the config number, so comparing with a constant
            // would grey the offer out while an ad still had something to give.
            enabled = state.phase != GamePhase.Recap && state.livesRemaining < state.refillTo,
            onClick = { onAction(GameAction.RefillBones) },
        ) {
            BoardControlBone(fill = BoneGold, edge = BoneGoldEdge)
        }
    }
}

/**
 * Whether the board has enough evidence to offer help unasked.
 *
 * One wrong guess is not struggling. Everybody gets one, often deliberately —
 * the tutorial *instructs* a wrong guess — and a booster that starts waving
 * after it is reading a single mistake as a cry for help.
 *
 * Two is different: two wrong guesses on one board means the deduction has gone
 * wrong somewhere rather than a finger having slipped, and it leaves one bone,
 * so the next mistake ends the attempt. That is the moment where a hint is
 * genuinely worth more than the charge it costs.
 *
 * Down to the last bone counts on its own, because bones are global now. A
 * player can arrive at a fresh board already on one, having spent the others
 * elsewhere, and they are in the same spot without having made a mistake here.
 *
 * A time-based signal would be better than either — somebody staring at an
 * unchanged board for two minutes is struggling and may not have guessed at all
 * — and it needs an elapsed clock the board does not yet drive. Worth revisiting
 * once it does.
 */
private val GameState.isStruggling: Boolean
    get() = strikesThisAttempt >= StruggleStrikes || livesRemaining <= 1

/** Two wrong guesses on one board. One is normal; two is a pattern. */
private const val StruggleStrikes = 2

/**
 * The dog inside a board control.
 *
 * Larger than the bone beside it, because the dog art carries its own margin
 * inside the image while `drawBone` fills its box. Matching the numbers would
 * make the dog visibly the smaller of the two.
 */
private val BoardControlDog = Dimension.D1500

/**
 * The bones on the refill offer, matching the ones in the life counter.
 *
 * The same currency should be the same object. A differently coloured bone on
 * the button that refills them would read as a different thing being offered.
 */
private val BoneGold = Color(0xFFF5C043)
private val BoneGoldEdge = Color(0xFFC8871B)

/**
 * The treat, in the orange it wears everywhere else.
 *
 * Not the pale biscuit `LevelRewardChip` uses. That one is drawn *on* orange and
 * is pale so it reads against it; on a white circle it all but disappeared. Same
 * object, opposite background, opposite treatment.
 */
private val TreatEdge = Color(0xFFB35C0F)

private const val WEIGHT_FILL = 1f

/**
 * How the slack above and below the board is shared.
 *
 * Split evenly, the board centres between the rule chips and the booster row,
 * which on a 4x4 leaves a hand-sized gap under the chips and puts the grid low
 * enough that the thumb has to reach up for it. The reference sits its grid
 * about a third of the way down the page; two-to-three gets close without
 * pinning the board to a fixed offset that a 10x10 could not honour.
 */
/**
 * One colour each, held for the life of the app.
 *
 * They are not theme roles because they are not roles — they are identities. A
 * player learns "the blue one shows me squares" and "the orange one places a
 * dog" long before they read either word, and that only works if the colours
 * never move. Both are chosen against the cream page and against each other for
 * anyone who cannot separate red from green.
 */
private val SniffColor = ColorResource.Blue500.color

/** The standing ad offer. Purple, so an offer never wears a booster's clothes. */
private val AdOfferColor = ColorResource.Purple600.color

private const val SpaceAboveBoard = 1f
private const val SpaceBelowBoard = 3f

@Preview
@Composable
private fun GameScreenPreview() {
    PreviewContent {
        Text(
            text = "GameScreen renders from a loaded level",
            typography = AppTheme.typography.Body.B500,
            textAlign = TextAlign.Center,
        )
    }
}

/** How far the whole board travels on a wrong guess. Smaller than the cell's own shake. */
private val BoardShakeTravel = Dimension.D100

/** Radians swept across the shake, so it crosses centre a few times before settling. */
private const val BoardShakeCycles = 14f
