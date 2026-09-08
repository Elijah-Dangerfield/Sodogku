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
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dialog.Dialog
import com.sodogku.libraries.ui.components.game.RuleDiagram
import com.sodogku.libraries.ui.components.game.drawBone
import com.sodogku.libraries.ui.components.game.drawRuleDiagram
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.RegionPalette
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.bones_body
import sodogku.libraries.resources.generated.resources.bones_title
import sodogku.libraries.resources.generated.resources.common_close
import sodogku.libraries.resources.generated.resources.game_rule_no_touching
import sodogku.libraries.resources.generated.resources.game_rule_one_per_line
import sodogku.libraries.resources.generated.resources.game_rule_one_per_region
import sodogku.libraries.resources.generated.resources.level_difficulty_1
import sodogku.libraries.resources.generated.resources.level_difficulty_2
import sodogku.libraries.resources.generated.resources.level_difficulty_3
import sodogku.libraries.resources.generated.resources.level_difficulty_4
import sodogku.libraries.resources.generated.resources.level_difficulty_5
import sodogku.libraries.resources.generated.resources.level_explainer_body
import sodogku.libraries.resources.generated.resources.level_explainer_daily_body
import sodogku.libraries.resources.generated.resources.level_explainer_daily_title
import sodogku.libraries.resources.generated.resources.level_explainer_difficulty
import sodogku.libraries.resources.generated.resources.level_explainer_grid
import sodogku.libraries.resources.generated.resources.level_explainer_title
import sodogku.libraries.resources.generated.resources.rules_body
import sodogku.libraries.resources.generated.resources.rules_line_body
import sodogku.libraries.resources.generated.resources.rules_region_body
import sodogku.libraries.resources.generated.resources.rules_title
import sodogku.libraries.resources.generated.resources.rules_touch_body
import sodogku.libraries.resources.generated.resources.score_explainer_body
import sodogku.libraries.resources.generated.resources.score_explainer_paws
import sodogku.libraries.resources.generated.resources.score_explainer_title

/** Which explainer is open, if any. */
enum class GameDialog { Rules, Bones, Level, Score }

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
                GameDialog.Level -> LevelContent(state)
                GameDialog.Score -> ScoreContent()
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
    title: StringResource,
    body: StringResource,
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

/**
 * What the number under "Level" means.
 *
 * A daily board has no campaign position — the header shows the streak there
 * instead of a level id — so the same tap gets the streak's explainer rather
 * than a level number that would be a position in a hidden pool.
 */
@Composable
private fun LevelContent(state: GameState) {
    val size = state.level?.size ?: 0
    if (state.isDaily) {
        Text(
            text = stringResource(Res.string.level_explainer_daily_title),
            typography = AppTheme.typography.Heading.H700,
        )
        Body(stringResource(Res.string.level_explainer_daily_body, size))
        return
    }

    val id = state.level?.id ?: 0
    Text(
        text = stringResource(Res.string.level_explainer_title, id),
        typography = AppTheme.typography.Heading.H700,
    )
    Body(stringResource(Res.string.level_explainer_body, id, LevelPacks.campaign.size))
    Body(stringResource(Res.string.level_explainer_grid, size))
    Text(
        text = stringResource(
            Res.string.level_explainer_difficulty,
            stringResource(difficultyLabel(state.level?.difficulty ?: 1)),
        ),
        typography = AppTheme.typography.Body.B600,
    )
}

/**
 * The generator's 1-to-5 deduction depth in words.
 *
 * Exhaustive over the tiers the solver can report rather than a lookup with a
 * fallback, so a sixth tier fails to compile here instead of silently showing
 * every hard board as "Fiendish".
 */
internal fun difficultyLabel(difficulty: Int): StringResource = when (difficulty) {
    DifficultyGentle -> Res.string.level_difficulty_1
    DifficultyEasy -> Res.string.level_difficulty_2
    DifficultyTricky -> Res.string.level_difficulty_3
    DifficultyTough -> Res.string.level_difficulty_4
    else -> Res.string.level_difficulty_5
}

private const val DifficultyGentle = 1
private const val DifficultyEasy = 2
private const val DifficultyTricky = 3
private const val DifficultyTough = 4

/**
 * How the score is built, in the order a player earns it.
 *
 * Deliberately no numbers: every coefficient is remote config (`scoring.*`), so
 * a copied-in "100 points per dog" is a sentence that goes stale the first time
 * anyone retunes the formula, in a build nobody would think to re-check.
 */
@Composable
private fun ScoreContent() {
    Text(
        text = stringResource(Res.string.score_explainer_title),
        typography = AppTheme.typography.Heading.H700,
    )
    Body(stringResource(Res.string.score_explainer_body))
    Body(stringResource(Res.string.score_explainer_paws))
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )
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
