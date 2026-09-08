package com.sodogku.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.ui.components.dialog.Dialog
import com.sodogku.libraries.ui.components.dialog.ModalDialogDefaults
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.PawRating
import com.sodogku.libraries.ui.components.game.ScoreCounter
import com.sodogku.libraries.ui.components.game.ScorePawBurst
import com.sodogku.libraries.sharing.ShareLabels
import com.sodogku.libraries.sharing.ShareResult
import com.sodogku.libraries.ui.components.feedback.ShareButton
import com.sodogku.libraries.ui.components.game.LevelRewardChip
import com.sodogku.libraries.ui.components.game.RewardBadge
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.daily_date
import sodogku.libraries.resources.generated.resources.daily_done
import sodogku.libraries.resources.generated.resources.daily_forfeit_body
import sodogku.libraries.resources.generated.resources.daily_forfeit_cancel
import sodogku.libraries.resources.generated.resources.daily_forfeit_confirm
import sodogku.libraries.resources.generated.resources.daily_forfeit_cta
import sodogku.libraries.resources.generated.resources.daily_forfeit_note
import sodogku.libraries.resources.generated.resources.daily_forfeit_title
import sodogku.libraries.resources.generated.resources.daily_out_of_bones
import sodogku.libraries.resources.generated.resources.daily_streak
import sodogku.libraries.resources.generated.resources.game_back_to_levels
import sodogku.libraries.resources.generated.resources.game_lost_title
import sodogku.libraries.resources.generated.resources.game_next_level
import sodogku.libraries.resources.generated.resources.game_watch_ad_badge
import sodogku.libraries.resources.generated.resources.game_refill_bones
import sodogku.libraries.resources.generated.resources.game_retry
import sodogku.libraries.resources.generated.resources.game_reward_earned
import sodogku.libraries.resources.generated.resources.game_skip_level
import sodogku.libraries.resources.generated.resources.game_skip_none_left
import sodogku.libraries.resources.generated.resources.game_skip_remaining
import sodogku.libraries.resources.generated.resources.game_time_taken
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
        GamePhase.Recap -> DailyRecapSheet(state, onAction, modifier)
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
        // The score is *earned* here rather than found: it rolls up from zero
        // while a handful of paws fly down into it from where the rating sits.
        //
        // On the sheet and not up in the header, which is the one decision worth
        // stating. The header's counter holds the lifetime total and it does
        // roll when this clear lands, but it does it behind a 70%-black scrim at
        // the top of a screen whose middle the player is reading. Paws flown
        // there would land a long way from the thing being read. This number is
        // the one the sheet is about.
        Box(contentAlignment = Alignment.Center) {
            ScoreCounter(
                score = state.attemptScore,
                countFrom = 0,
                typography = AppTheme.typography.Heading.H600,
                color = AppTheme.colors.accentPrimary,
            )
            ScorePawBurst()
        }
        // Under the score, quietly. The time is the thing a player compares
        // against themselves later; it is not what the sheet is for.
        elapsedLabel(state.elapsedMs)?.let { time ->
            Text(
                text = stringResource(Res.string.game_time_taken, time),
                typography = AppTheme.typography.Body.B600,
                color = AppTheme.colors.textSecondary,
            )
        }
        // The same chip the level pane promised, so the payout is recognisably
        // the thing that was advertised rather than a number quietly going up
        // in the booster row behind the sheet.
        if (state.treatAwarded) {
            LevelRewardChip(
                label = stringResource(Res.string.game_reward_earned),
            )
        }
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
                    score = state.attemptScore,
                    paws = state.paws,
                    // What this run did not spend, not what the player holds.
                    // Bones are one global count now, so a refill mid-board
                    // would otherwise share three intact bones after a clear
                    // that cost three.
                    bonesRemaining = state.bonesUnspent,
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
        // The one revive, and the only control on this sheet that can put bones
        // back. It used to sit above a "Keep going" that bought a single bone
        // for the same ad, which is strictly the worse of two buttons and read
        // as a free bone in a build with no ad inventory.
        ButtonPrimary(
            onClick = { onAction(GameAction.RefillBones) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_refill_bones, state.refillTo))
            // Refilling always costs an ad, so this one is badged unconditionally
            // — unlike Next level, where an ad is only sometimes due.
            RewardBadge(modifier = Modifier.padding(start = Dimension.D300))
        }
        // No starting over on the daily: one attempt per day, and the revive
        // above already gives a stuck player a way back into *this* one.
        if (!state.isDaily) {
            ButtonSecondary(
                onClick = { onAction(GameAction.Retry) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.game_retry))
            }
        }
        // Under both the revive and Start over, deliberately. This board is
        // still winnable and the offer to move past it should be the last thing
        // read, not the first — SPEC 1.6 wants a rescue, not an invitation to
        // stop thinking.
        state.skip?.let { skip -> SkipButton(skip, onAction) }
        ButtonGhost(
            onClick = { onAction(GameAction.Leave) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_back_to_levels))
        }
        // Only the daily has a day to give up, and the note above the control is
        // the point of it: this button used to be the *other* one. Tapping
        // Levels on a lost daily wrote the failure and closed the day, so the
        // sheet now says out loud that leaving costs nothing.
        if (state.isDaily) {
            Text(
                text = stringResource(Res.string.daily_forfeit_note),
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ButtonGhost(
                onClick = { onAction(GameAction.ForfeitDailyRequested) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.daily_forfeit_cta))
            }
        }
    }
}

/**
 * The day already played, opened rather than refused.
 *
 * Reaching a spent daily used to resolve to a null board and bounce the route,
 * which dropped the player into the campaign with the daily closed behind them
 * and nothing said about why. Nothing here is interactive: the day is over, and
 * the sheet exists so it can be *seen* to be over.
 */
@Composable
private fun DailyRecapSheet(state: GameState, onAction: (GameAction) -> Unit, modifier: Modifier) {
    val recap = state.dailyRecap
    OutcomeLayout(modifier) {
        val cleared = recap?.outcome != DailyOutcome.Failed
        Dog(pose = if (cleared) DogPose.Solved else DogPose.HardMode)
        Text(
            text = stringResource(
                if (cleared) Res.string.daily_done else Res.string.daily_out_of_bones,
            ),
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
        )
        if (recap != null) {
            Text(
                text = stringResource(
                    Res.string.daily_date,
                    stringResource(MonthNames[recap.date.month.number - 1]),
                    recap.date.day,
                ),
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.textSecondary,
            )
            // Recalled, not awarded. A day that was given up on has neither, and
            // three empty paws would read as a nought-out-of-three score.
            if (recap.paws > 0) PawRating(paws = recap.paws, animated = false)
            if (recap.score > 0) {
                Text(
                    text = recap.score.toString(),
                    typography = AppTheme.typography.Heading.H600,
                    color = AppTheme.colors.accentPrimary,
                )
            }
            // Recalled like everything else on this sheet, so no roll and no
            // paws. Gated on the clear as well as on the number: a failed day
            // carries the time it ran for, and "Solved in 2:10" over a board
            // nobody solved is the sheet lying about the day it exists to show.
            elapsedLabel(recap.timeMs).takeIf { cleared }?.let { time ->
                Text(
                    text = stringResource(Res.string.game_time_taken, time),
                    typography = AppTheme.typography.Body.B600,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        if (state.dailyStreak > 0) {
            Text(
                text = stringResource(Res.string.daily_streak, state.dailyStreak),
                typography = AppTheme.typography.Body.B600,
                color = AppTheme.colors.textSecondary,
            )
        }
        ButtonPrimary(
            onClick = { onAction(GameAction.Leave) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_back_to_levels))
        }
    }
}

/**
 * The confirmation in front of a forfeit.
 *
 * `daily_result` is insert-only and takes one row per date, so this write can
 * never be taken back. A one-tap control for something irreversible is how the
 * old behaviour looked from the outside, which is the failure this whole change
 * is about — the tap that spent a day was a navigation.
 */
@Composable
fun ForfeitDailyDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Dog(pose = DogPose.HardMode)
            Text(
                text = stringResource(Res.string.daily_forfeit_title),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.daily_forfeit_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            // Keeping the day is the filled button. The destructive answer is
            // the quiet one, which is the opposite of how the old accidental
            // path was weighted.
            ButtonPrimary(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.daily_forfeit_cancel))
            }
            ButtonGhost(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.daily_forfeit_confirm))
            }
        }
    }
}

/**
 * "Move past this one", as the quietest control on the sheet.
 *
 * A ghost button rather than a filled one: this is the option that ends the
 * puzzle, and the two revives above it are the ones worth reaching for first.
 *
 * With the allowance spent it stays on screen, disabled, and swaps its label
 * for the reason. The alternative — removing it — is how a player learns that a
 * feature they used yesterday has silently gone away.
 */
@Composable
private fun SkipButton(skip: SkipOffer, onAction: (GameAction) -> Unit) {
    ButtonGhost(
        onClick = { onAction(GameAction.SkipLevel) },
        enabled = skip.available,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (skip.available) {
            Text(stringResource(Res.string.game_skip_level))
            // Pro skips for free, so badging it would promise an ad that never
            // plays. The cap still applies to Pro (SPEC 1.6).
            if (!skip.free) {
                RewardBadge(modifier = Modifier.padding(start = Dimension.D300))
            }
        } else {
            Text(stringResource(Res.string.game_skip_none_left))
        }
    }
    if (skip.available) {
        Text(
            text = stringResource(Res.string.game_skip_remaining, skip.remainingToday),
            typography = AppTheme.typography.Caption.C300,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The card the outcome sits on.
 *
 * It needs its own surface, not just the scrim: the first pass laid the content
 * straight onto the dimmed board and the title was dark text on a dark
 * translucent grid, effectively invisible.
 *
 * Not a [com.sodogku.libraries.ui.components.dialog.Dialog], deliberately —
 * `Dialog` dismisses on an outside tap, and a lose sheet that vanishes when the
 * player taps the board behind it leaves them on a dead grid with no way to
 * retry. It borrows the dialog's [ModalDialogDefaults.ContentPadding] so the two
 * kinds of card do not drift apart, which is what a second copy of the numbers
 * guaranteed.
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
            .padding(ModalDialogDefaults.ContentPadding),
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

@Preview
@Composable
private fun LostDailySheetPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(phase = GamePhase.Lost, isDaily = true, refillTo = 3),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun DailyRecapSheetPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(
                phase = GamePhase.Recap,
                isDaily = true,
                dailyStreak = 12,
                dailyRecap = DailyResult(
                    date = LocalDate(2026, 9, 8),
                    levelIndex = 41,
                    outcome = DailyOutcome.Completed,
                    score = 4_200,
                    paws = 3,
                    timeMs = 90_000,
                ),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun LostSheetWithSkipPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(phase = GamePhase.Lost, skip = SkipOffer(remainingToday = 2)),
            onAction = {},
        )
    }
}
