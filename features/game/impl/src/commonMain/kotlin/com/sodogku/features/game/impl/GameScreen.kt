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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.FullScreenLoader
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.board.BoardCell
import com.sodogku.libraries.ui.components.board.BoardCellState
import com.sodogku.libraries.ui.components.game.BoosterButton
import com.sodogku.libraries.ui.components.game.FloatingPoints
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.game.DogCounter
import com.sodogku.libraries.ui.components.game.LifeRow
import com.sodogku.libraries.ui.components.game.RuleChip
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
import sodogku.libraries.resources.generated.resources.game_level_label
import sodogku.libraries.resources.generated.resources.game_score_label
import sodogku.libraries.resources.generated.resources.game_rule_no_touching
import sodogku.libraries.resources.generated.resources.game_rule_one_per_line
import sodogku.libraries.resources.generated.resources.game_rule_one_per_region
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
    var drawerOpen by remember { mutableStateOf(false) }

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
                onOpenLevels = {
                    drawerOpen = true
                    onAction(GameAction.LevelsOpened)
                },
                onSettings = { dialog = GameDialog.Settings },
                onExplainBones = { onAction(GameAction.BoosterTapped(Consumable.Bone)) },
            )

            // The rules sit directly under the header rather than floating above
            // the board: they are reference material, and a gap between them and
            // the score reads as a hole on a small grid.
            RuleChips(onExplain = { dialog = GameDialog.Rules })

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

            LastBoneWarning(state = state, onAction = onAction)

            SniffHint(state = state, onAction = onAction)

            LevelDrawer(
                open = drawerOpen,
                currentLevelId = level.id,
                unlockedThrough = state.unlockedThrough,
                canJumpAnywhere = state.isPro,
                records = state.records,
                onPick = {
                    drawerOpen = false
                    onAction(GameAction.GoToLevel(it))
                },
                onDismiss = { drawerOpen = false },
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
        IconButton(icon = Icons.Menu(null), onClick = onOpenLevels)

        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D1000)) {
            HeaderStat(
                label = stringResource(Res.string.game_level_label),
                value = levelId.toString(),
            )
            HeaderStat(
                label = stringResource(Res.string.game_score_label),
                value = null,
                content = { ScoreCounter(score = state.score.total) },
            )
        }

        IconButton(icon = Icons.Settings(null), onClick = onSettings)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Dimension.D400),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DogCounter(found = state.dogsPlaced, total = state.dogsRequired)
        LifeRow(
            remaining = state.livesRemaining,
            modifier = Modifier
                .focusTarget(LivesFocusKey)
                .bounceClick(onClick = onExplainBones),
        )
    }
}

/**
 * A stat with its label above it. The number is the display face at heading
 * weight, because the level and the score are the two things a player glances
 * at mid-puzzle and neither should need looking for.
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
            typography = AppTheme.typography.Body.B400,
            color = AppTheme.colors.textSecondary,
        )
        if (value != null) {
            Text(text = value, typography = AppTheme.typography.Heading.H700)
        } else {
            content()
        }
    }
}

@Composable
private fun RuleChips(onExplain: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        RuleChip(
            diagram = RuleDiagram.OnePerRegion,
            label = stringResource(Res.string.game_rule_one_per_region),
            onClick = onExplain,
        )
        RuleChip(
            diagram = RuleDiagram.OnePerLine,
            label = stringResource(Res.string.game_rule_one_per_line),
            onClick = onExplain,
        )
        RuleChip(
            diagram = RuleDiagram.NoTouching,
            label = stringResource(Res.string.game_rule_no_touching),
            onClick = onExplain,
        )
    }
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

    BoxWithConstraints {
        val cell = (maxWidth - CellGap * (size - 1)) / size

        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            repeat(size) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    repeat(size) { col ->
                        val index = level.board.cellAt(row, col)
                        val hinted = index in state.hintCells
                        BoardCell(
                            modifier = if (hinted) {
                                Modifier.focusTarget(hintKeyFor(index))
                            } else {
                                Modifier
                            },
                            region = level.board.regionAt(index),
                            state = cellState(state, index),
                            size = cell,
                            colorblind = state.colorblind,
                            strikeNonce = if (state.strikeCell == index) state.strikeNonce else 0,
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

@Composable
private fun BoosterBar(state: GameState, onAction: (GameAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D600)) {
        BoosterButton(
            label = stringResource(Res.string.game_sniff),
            count = state.sniffs,
            enabled = state.phase == GamePhase.Playing,
            onClick = { onAction(GameAction.BoosterTapped(Consumable.Sniff)) },
        )
        BoosterButton(
            label = stringResource(Res.string.game_treat),
            count = state.treats,
            enabled = state.phase == GamePhase.Playing,
            onClick = { onAction(GameAction.BoosterTapped(Consumable.Treat)) },
        )
    }
}

private const val WEIGHT_FILL = 1f

/** Gutter between cells. Small enough that the board reads as one object. */
private val CellGap = Dimension.D100

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
