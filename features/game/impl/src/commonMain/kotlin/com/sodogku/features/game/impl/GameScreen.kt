package com.sodogku.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sodogku.libraries.scoring.ScoringConfig
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
import com.sodogku.libraries.ui.components.board.rememberPlacementPulse
import com.sodogku.libraries.ui.components.game.BoosterButton
import com.sodogku.libraries.ui.components.game.FloatingPoints
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.game.DogCounter
import com.sodogku.libraries.ui.components.game.HudPill
import com.sodogku.libraries.ui.components.game.LifeRow
import com.sodogku.libraries.ui.components.game.RuleChip
import com.sodogku.libraries.ui.components.game.RuleChipGroup
import com.sodogku.libraries.ui.components.game.RuleDiagram
import com.sodogku.libraries.ui.components.game.ScoreCounter
import com.sodogku.libraries.ui.components.icon.IconButton
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.system.focusTarget
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.scoring.Praise
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.achievements_unlocked_toast
import sodogku.libraries.resources.generated.resources.daily_streak_label
import sodogku.libraries.resources.generated.resources.game_level_label
import sodogku.libraries.resources.generated.resources.game_score_label
import sodogku.libraries.resources.generated.resources.game_rule_no_touching
import sodogku.libraries.resources.generated.resources.game_rule_one_per_line
import sodogku.libraries.resources.generated.resources.game_rule_one_per_region
import sodogku.libraries.resources.generated.resources.game_free_bones
import sodogku.libraries.resources.generated.resources.game_sniff
import sodogku.libraries.resources.generated.resources.game_treat

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
                .padding(horizontal = Dimension.D500),
        ) {
            GameHeader(
                state = state,
                levelId = level.id,
                onOpenLevels = { onAction(GameAction.LevelsOpened) },
                onSettings = { onAction(GameAction.OpenSettings) },
                onExplainBones = { onAction(GameAction.BoosterTapped(Consumable.Bone)) },
            )

            // The rules sit directly under the header rather than floating above
            // the board: they are reference material, and a gap between them and
            // the score reads as a hole on a small grid.
            RuleChips(state = state, onExplain = { dialog = GameDialog.Rules })

            Spacer(modifier = Modifier.weight(WEIGHT_FILL))

            Box(contentAlignment = Alignment.Center) {
                BoardGrid(state = state, onAction = onAction)
                FloatingPoints(
                    points = state.lastPoints,
                    praise = state.lastPraise.takeIf { it != Praise.None }?.name,
                    nonce = state.pointsNonce,
                )
            }

            Spacer(modifier = Modifier.weight(WEIGHT_FILL))

            BoosterBar(state = state, onAction = onAction)

            Spacer(modifier = Modifier.height(Dimension.D700))
        }

            // The outcome covers the board rather than replacing it: the player
            // should still see the grid they just finished (or ran out of bones
            // on) behind the sheet.
            if (state.phase == GamePhase.Won || state.phase == GamePhase.Lost) {
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
                onPick = { onAction(GameAction.GoToLevel(it)) },
                onDismiss = { onAction(GameAction.LevelsClosed) },
                daily = state.daily,
                isDailyBoard = state.isDaily,
                onPlayDaily = { onAction(GameAction.PlayDaily) },
                onUseFreeze = { onAction(GameAction.UseFreeze) },
            )
        }

        state.freezeMessage?.let { message ->
            FreezeMessageDialog(
                message = message,
                onDismiss = { onAction(GameAction.DismissFreezeMessage) },
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
                onUse = { onAction(GameAction.BoosterConfirmed(booster)) },
                onWatchAd = { onAction(GameAction.BoosterRefillRequested(booster)) },
                onDismiss = { onAction(GameAction.DismissBoosterPrompt) },
            )
        }

        GameDialogHost(
            dialog = dialog,
            state = state,
            onAction = onAction,
            onDismiss = { dialog = null },
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
            icon = Icons.Menu(null),
            onClick = onOpenLevels,
            backgroundColor = AppTheme.colors.surfacePrimary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D1000)) {
            // A daily's level id is a position in the daily pool, which means
            // nothing to the player and reads as a campaign level they have not
            // reached. The streak is the number that belongs here instead.
            if (state.isDaily) {
                HeaderStat(
                    label = stringResource(Res.string.daily_streak_label),
                    value = (state.daily?.streak ?: 0).toString(),
                )
            } else {
                HeaderStat(
                    label = stringResource(Res.string.game_level_label),
                    value = levelId.toString(),
                )
            }
            HeaderStat(
                label = stringResource(Res.string.game_score_label),
                value = null,
                content = { ScoreCounter(score = state.score.total) },
            )
        }

        IconButton(
            icon = Icons.Settings(null),
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
        DogCounter(found = state.dogsPlaced, total = state.dogsRequired)
        HudPill(
            modifier = Modifier
                .focusTarget(LivesFocusKey)
                .bounceClick(onClick = onExplainBones),
        ) {
            LifeRow(remaining = state.livesRemaining)
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
    content: @Composable () -> Unit = {},
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            typography = AppTheme.typography.Caption.C300,
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
            modifier = Modifier.weight(WEIGHT_FILL).focusTarget(RuleChipFocusKeys[0]),
            highlighted = broken == RuleDiagram.OnePerRegion,
            onClick = onExplain,
        )
        RuleChip(
            diagram = RuleDiagram.OnePerLine,
            label = stringResource(Res.string.game_rule_one_per_line),
            modifier = Modifier.weight(WEIGHT_FILL).focusTarget(RuleChipFocusKeys[1]),
            highlighted = broken == RuleDiagram.OnePerLine,
            onClick = onExplain,
        )
        RuleChip(
            diagram = RuleDiagram.NoTouching,
            label = stringResource(Res.string.game_rule_no_touching),
            modifier = Modifier.weight(WEIGHT_FILL).focusTarget(RuleChipFocusKeys[2]),
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

    BoardSurface { BoardRows(state = state, size = size, onAction = onAction) }
}

@Composable
private fun BoardRows(state: GameState, size: Int, onAction: (GameAction) -> Unit) {
    val level = state.level ?: return
    val placement = rememberPlacementPulse(state.placedCells, size)

    BoxWithConstraints {
        val cell = (maxWidth - BoardCellGap * (size - 1)) / size

        Column(verticalArrangement = Arrangement.spacedBy(BoardCellGap)) {
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
                            state = cellState(state, index),
                            size = cell,
                            colorblind = state.colorblind,
                            strikeNonce = if (state.strikeCell == index) state.strikeNonce else 0,
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
                        )
                    }
                }
            }
        }
    }
}

private fun cellState(state: GameState, cell: Int): BoardCellState = when (cell) {
    in state.placedCells -> BoardCellState.Occupied
    in state.wrongGuesses -> BoardCellState.Wrong
    in state.autoMarks, in state.manualMarks -> BoardCellState.Marked
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
@Composable
private fun BoosterBar(state: GameState, onAction: (GameAction) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D600),
    ) {
        BoosterButton(
            label = stringResource(Res.string.game_sniff),
            count = state.sniffs,
            modifier = Modifier.focusTarget(SniffFocusKey),
            enabled = state.phase == GamePhase.Playing,
            onClick = { onAction(GameAction.BoosterTapped(Consumable.Sniff)) },
        )
        BoosterButton(
            label = stringResource(Res.string.game_treat),
            count = state.treats,
            modifier = Modifier.focusTarget(TreatFocusKey),
            enabled = state.phase == GamePhase.Playing,
            onClick = { onAction(GameAction.BoosterTapped(Consumable.Treat)) },
        )
        RewardButton(
            label = stringResource(Res.string.game_free_bones),
            enabled = state.livesRemaining < ScoringConfig.MAX_LIVES,
            onClick = { onAction(GameAction.RefillBones) },
        )
    }
}

private const val WEIGHT_FILL = 1f

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
