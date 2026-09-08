package com.sodogku.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.PawRating
import com.sodogku.libraries.sharing.ShareLabels
import com.sodogku.libraries.sharing.ShareResult
import com.sodogku.libraries.ui.components.feedback.ShareButton
import com.sodogku.libraries.ui.components.game.RewardBadge
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.daily_streak
import sodogku.libraries.resources.generated.resources.game_back_to_levels
import sodogku.libraries.resources.generated.resources.game_continue
import sodogku.libraries.resources.generated.resources.game_lost_title
import sodogku.libraries.resources.generated.resources.game_next_level
import sodogku.libraries.resources.generated.resources.game_watch_ad_badge
import sodogku.libraries.resources.generated.resources.game_refill_bones
import sodogku.libraries.resources.generated.resources.game_retry
import sodogku.libraries.resources.generated.resources.game_won_title
import sodogku.libraries.resources.generated.resources.share_footer
import sodogku.libraries.resources.generated.resources.share_streak
import sodogku.libraries.resources.generated.resources.share_title_daily
import sodogku.libraries.resources.generated.resources.share_title_daily_plain
import sodogku.libraries.resources.generated.resources.share_title_level

/**
 * The end of an attempt.
 *
 * The losing sheet deliberately does **not** reveal the answer. A player who is
 * shown the solution has no reason to retry, and retrying is the whole point of
 * a three-life game.
 */
@Composable
fun GameOutcomeSheet(
    state: GameState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        GamePhase.Won -> WonSheet(state, onAction, modifier)
        GamePhase.Lost -> LostSheet(state, onAction, modifier)
        GamePhase.Loading, GamePhase.Playing -> Unit
    }
}

@Composable
private fun WonSheet(state: GameState, onAction: (GameAction) -> Unit, modifier: Modifier) {
    OutcomeLayout(modifier) {
        Dog(pose = DogPose.Solved)
        Text(
            text = stringResource(Res.string.game_won_title),
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
        )
        PawRating(paws = state.paws)
        Text(
            text = state.score.total.toString(),
            typography = AppTheme.typography.Heading.H600,
            color = AppTheme.colors.accentPrimary,
        )
        // The streak is the reward for a daily, so it is shown next to the score
        // rather than left for the player to find back in the drawer.
        if (state.isDaily && state.dailyStreak > 0) {
            Text(
                text = stringResource(Res.string.daily_streak, state.dailyStreak),
                typography = AppTheme.typography.Body.B600,
                color = AppTheme.colors.textSecondary,
            )
        }
        // Above the CTA, not beside it: sharing is what a player *might* do,
        // Next level is what they will do.
        val level = state.level
        if (level != null) {
            ShareButton(
                result = ShareResult(
                    size = level.size,
                    // The region layout, never the placements. `ShareResult` has
                    // nowhere to put a solution, which is what makes "no
                    // spoilers" a property of the type rather than of care taken
                    // here.
                    regions = level.board.regions.toList(),
                    timeMs = state.elapsedMs,
                    score = state.score.total,
                    paws = state.paws,
                    bonesRemaining = state.livesRemaining,
                ),
                labels = ShareLabels(
                    title = shareTitle(state, level.id),
                    // Only the daily has a streak, and "0 day streak" under a
                    // campaign clear is worse than no line at all.
                    streak = if (state.isDaily && state.dailyStreak > 0) {
                        stringResource(Res.string.share_streak, state.dailyStreak)
                    } else {
                        null
                    },
                    footer = stringResource(Res.string.share_footer),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // There is no next daily, so the daily's win sheet leads back rather than
        // offering tomorrow.
        if (state.isDaily) {
            ButtonPrimary(
                onClick = { onAction(GameAction.Leave) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.game_back_to_levels))
            }
        } else {
            ButtonPrimary(
                onClick = { onAction(GameAction.NextLevel) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.game_next_level))
                // The badge marks the moments an ad is coming, so the tap is never a
                // surprise. It only appears when one is actually due (C7 wires the
                // frequency gate; nothing is due yet).
                if (state.adBeforeNextLevel) {
                    RewardBadge(modifier = Modifier.padding(start = Dimension.D300))
                }
            }
        }
    }
}

/** "Sodogku Daily · Sep 8" or "Sodogku · Level 137". */
@Composable
private fun shareTitle(state: GameState, levelId: Int): String {
    if (!state.isDaily) return stringResource(Res.string.share_title_level, levelId)
    val date = state.daily?.date ?: return stringResource(Res.string.share_title_daily_plain)
    return stringResource(
        Res.string.share_title_daily,
        stringResource(MonthNames[date.month.number - 1]),
        date.day,
    )
}

@Composable
private fun LostSheet(state: GameState, onAction: (GameAction) -> Unit, modifier: Modifier) {
    OutcomeLayout(modifier) {
        Dog(pose = DogPose.HardMode)
        Text(
            text = stringResource(Res.string.game_lost_title),
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
        )
        ButtonPrimary(
            onClick = { onAction(GameAction.RefillBones) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_refill_bones))
            // Refilling always costs an ad, so this one is badged unconditionally
            // — unlike Next level, where an ad is only sometimes due.
            RewardBadge(modifier = Modifier.padding(start = Dimension.D300))
        }
        ButtonSecondary(
            onClick = { onAction(GameAction.ContinueAfterLoss) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_continue))
        }
        // No starting over on the daily: one attempt per day, and the two ad
        // revives above already give a stuck player a way back into *this* one.
        if (!state.isDaily) {
            ButtonSecondary(
                onClick = { onAction(GameAction.Retry) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.game_retry))
            }
        }
        ButtonGhost(
            onClick = { onAction(GameAction.Leave) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_back_to_levels))
        }
    }
}

/**
 * The card the outcome sits on.
 *
 * It needs its own surface, not just the scrim: the first pass laid the content
 * straight onto the dimmed board and the title was dark text on a dark
 * translucent grid, effectively invisible.
 */
@Composable
private fun OutcomeLayout(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D600),
        modifier = modifier
            .padding(horizontal = Dimension.D800)
            .fillMaxWidth()
            .clip(Radii.Card)
            .background(AppTheme.colors.surfacePrimary.color)
            .padding(horizontal = Dimension.D800, vertical = Dimension.D900),
    ) {
        content()
    }
}

@Preview
@Composable
private fun WonSheetPreview() {
    PreviewContent {
        GameOutcomeSheet(state = GameState(phase = GamePhase.Won, paws = 3), onAction = {})
    }
}

@Preview
@Composable
private fun LostSheetPreview() {
    PreviewContent {
        GameOutcomeSheet(state = GameState(phase = GamePhase.Lost), onAction = {})
    }
}
