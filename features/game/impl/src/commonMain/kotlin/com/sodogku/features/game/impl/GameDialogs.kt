package com.sodogku.features.game.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.sodogku.libraries.ui.components.Switch
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dialog.Dialog
import com.sodogku.libraries.ui.components.game.RuleDiagram
import com.sodogku.libraries.ui.components.game.drawBone
import com.sodogku.libraries.ui.components.game.drawRuleDiagram
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.RegionPalette
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.bones_body
import sodogku.libraries.resources.generated.resources.bones_title
import sodogku.libraries.resources.generated.resources.common_close
import sodogku.libraries.resources.generated.resources.game_rule_no_touching
import sodogku.libraries.resources.generated.resources.game_rule_one_per_line
import sodogku.libraries.resources.generated.resources.game_rule_one_per_region
import sodogku.libraries.resources.generated.resources.rules_body
import sodogku.libraries.resources.generated.resources.rules_line_body
import sodogku.libraries.resources.generated.resources.rules_region_body
import sodogku.libraries.resources.generated.resources.rules_title
import sodogku.libraries.resources.generated.resources.rules_touch_body
import sodogku.libraries.resources.generated.resources.settings_colorblind
import sodogku.libraries.resources.generated.resources.settings_colorblind_body
import sodogku.libraries.resources.generated.resources.settings_feedback
import sodogku.libraries.resources.generated.resources.settings_haptics
import sodogku.libraries.resources.generated.resources.settings_haptics_body
import sodogku.libraries.resources.generated.resources.settings_privacy
import sodogku.libraries.resources.generated.resources.settings_reduce_animations
import sodogku.libraries.resources.generated.resources.settings_reduce_animations_body
import sodogku.libraries.resources.generated.resources.settings_terms
import sodogku.libraries.resources.generated.resources.settings_title
import sodogku.libraries.resources.generated.resources.settings_version

/** Which explainer is open, if any. */
enum class GameDialog { Rules, Bones, Settings }

/**
 * Everything the board can put in front of the player.
 *
 * All of these go through the design system's [Dialog] rather than a hand-rolled
 * overlay. The first pass built its own `Box` with a scrim and lost tap-outside
 * to dismiss, back-press handling and the exit animation — all of which the DS
 * dialog already had.
 */
@Composable
fun GameDialogHost(
    dialog: GameDialog?,
    state: GameState,
    onAction: (GameAction) -> Unit,
    onDismiss: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenFeedback: () -> Unit,
    appVersion: String,
) {
    if (dialog == null) return

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (dialog) {
                GameDialog.Rules -> RulesContent()
                GameDialog.Bones -> BonesContent()
                GameDialog.Settings -> SettingsContent(
                    state = state,
                    onAction = onAction,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenTerms = onOpenTerms,
                    onOpenFeedback = onOpenFeedback,
                    appVersion = appVersion,
                )
            }
            ButtonPrimary(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.common_close))
            }
        }
    }
}

@Composable
private fun RulesContent() {
    Text(text = stringResource(Res.string.rules_title), typography = AppTheme.typography.Heading.H700)
    Text(
        text = stringResource(Res.string.rules_body),
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )
    RuleRow(RuleDiagram.OnePerRegion, Res.string.game_rule_one_per_region, Res.string.rules_region_body)
    RuleRow(RuleDiagram.OnePerLine, Res.string.game_rule_one_per_line, Res.string.rules_line_body)
    RuleRow(RuleDiagram.NoTouching, Res.string.game_rule_no_touching, Res.string.rules_touch_body)
}

@Composable
private fun RuleRow(
    diagram: RuleDiagram,
    title: org.jetbrains.compose.resources.StringResource,
    body: org.jetbrains.compose.resources.StringResource,
) {
    val ink = AppTheme.colors.text.color
    val regions = listOf(RegionPalette[0].fill, RegionPalette[3].fill, RegionPalette[7].fill)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .size(Dimension.D1300)
                .drawBehind { drawRuleDiagram(diagram, ink, regions) },
        ) {}
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
            Text(text = stringResource(title), typography = AppTheme.typography.Body.B600)
            Text(
                text = stringResource(body),
                typography = AppTheme.typography.Body.B400,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun BonesContent() {
    val gold = androidx.compose.ui.graphics.Color(0xFFF5C043)
    val edge = androidx.compose.ui.graphics.Color(0xFFC8871B)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Column(
                modifier = Modifier
                    .size(width = Dimension.D1300, height = Dimension.D900)
                    .drawBehind { drawBone(gold, edge) },
            ) {}
        }
    }
    Text(text = stringResource(Res.string.bones_title), typography = AppTheme.typography.Heading.H700)
    Text(
        text = stringResource(Res.string.bones_body),
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )
}

@Composable
private fun SettingsContent(
    state: GameState,
    onAction: (GameAction) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenFeedback: () -> Unit,
    appVersion: String,
) {
    Text(text = stringResource(Res.string.settings_title), typography = AppTheme.typography.Heading.H700)

    SettingToggle(
        title = stringResource(Res.string.settings_colorblind),
        body = stringResource(Res.string.settings_colorblind_body),
        checked = state.colorblind,
        onToggle = { onAction(GameAction.ToggleColorblind) },
    )
    SettingToggle(
        title = stringResource(Res.string.settings_haptics),
        body = stringResource(Res.string.settings_haptics_body),
        checked = state.haptics,
        onToggle = { onAction(GameAction.ToggleHaptics) },
    )
    SettingToggle(
        title = stringResource(Res.string.settings_reduce_animations),
        body = stringResource(Res.string.settings_reduce_animations_body),
        checked = state.reduceAnimations,
        onToggle = { onAction(GameAction.ToggleReduceAnimations) },
    )

    ButtonGhost(onClick = onOpenFeedback, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.settings_feedback))
    }
    ButtonGhost(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.settings_privacy))
    }
    ButtonGhost(onClick = onOpenTerms, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.settings_terms))
    }
    Text(
        text = stringResource(Res.string.settings_version, appVersion),
        typography = AppTheme.typography.Caption.C300,
        color = AppTheme.colors.textSecondary,
    )
}

@Composable
private fun SettingToggle(
    title: String,
    body: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimension.D100),
            modifier = Modifier.weight(WeightFill),
        ) {
            Text(text = title, typography = AppTheme.typography.Body.B600)
            Text(
                text = body,
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.textSecondary,
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

private const val WeightFill = 1f
