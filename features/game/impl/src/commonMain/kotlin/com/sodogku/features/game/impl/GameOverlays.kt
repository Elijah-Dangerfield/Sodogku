package com.sodogku.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.FocusScrim
import com.sodogku.libraries.ui.system.FocusTargetKey
import com.sodogku.libraries.ui.system.Spotlight
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.game_last_bone_body
import sodogku.libraries.resources.generated.resources.game_last_bone_title
import sodogku.libraries.resources.generated.resources.settings_colorblind
import sodogku.libraries.resources.generated.resources.settings_colorblind_body
import sodogku.libraries.resources.generated.resources.settings_done

/** The thing the last-bone warning points at. */
val LivesFocusKey = FocusTargetKey("game.lives")

/** The board, for hint spotlights. */
val BoardFocusKey = FocusTargetKey("game.board")

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
 * A card that hangs just below whatever the spotlight is lighting, so the copy
 * and the thing it is describing read as one object.
 */
@Composable
private fun BoxScope.SpeechBubble(
    anchorBottomPx: Float,
    content: @Composable () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D300),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = with(density) { anchorBottomPx.toDp() } + Dimension.D600)
            .padding(horizontal = Dimension.D800)
            .widthIn(max = BubbleMaxWidth)
            .clip(Radii.Card)
            .background(AppTheme.colors.surfacePrimary.color)
            .padding(Dimension.D700),
    ) {
        content()
    }
}

/**
 * The settings a player can reach mid-puzzle.
 *
 * Deliberately in-place rather than a navigation push: leaving the board to
 * change a display setting and coming back is a worse experience than a sheet,
 * and the board state has to survive it either way.
 */
@Composable
fun BoxScope.GameSettingsSheet(
    state: GameState,
    onAction: (GameAction) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .matchParentSize()
            .background(AppTheme.colors.backgroundOverlay.color),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimension.D600),
            modifier = Modifier
                .padding(horizontal = Dimension.D800)
                .fillMaxWidth()
                .clip(Radii.Card)
                .background(AppTheme.colors.surfacePrimary.color)
                .padding(Dimension.D800),
        ) {
            Text(
                text = stringResource(Res.string.settings_colorblind),
                typography = AppTheme.typography.Heading.H600,
            )
            Text(
                text = stringResource(Res.string.settings_colorblind_body),
                typography = AppTheme.typography.Body.B400,
                color = AppTheme.colors.textSecondary,
            )
            com.sodogku.libraries.ui.components.Switch(
                checked = state.colorblind,
                onCheckedChange = { onAction(GameAction.ToggleColorblind) },
            )
            ButtonPrimary(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.settings_done))
            }
        }
    }
}

private val BubbleMaxWidth = Dimension.D1900 * 3
