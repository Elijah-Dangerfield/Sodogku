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
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.puzzle.Solution
import com.sodogku.libraries.ui.components.dialog.ModalDialogDefaults
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.PawRating
import com.sodogku.libraries.ui.components.game.ScoreCounter
import com.sodogku.libraries.ui.components.game.ScorePawBurst
import com.sodogku.libraries.ui.components.game.LevelRewardChip
import com.sodogku.libraries.ui.components.game.RewardBadge
import com.sodogku.libraries.ui.components.game.Stat
import com.sodogku.libraries.ui.components.game.StatPills
import com.sodogku.libraries.scoring.Standing
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.daily_date
import sodogku.libraries.resources.generated.resources.daily_done
import sodogku.libraries.resources.generated.resources.daily_out_of_bones
import sodogku.libraries.resources.generated.resources.dogs_a11y
import sodogku.libraries.resources.generated.resources.daily_play
import sodogku.libraries.resources.generated.resources.daily_review
import sodogku.libraries.resources.generated.resources.daily_streak
import sodogku.libraries.resources.generated.resources.game_campaign_complete_body
import sodogku.libraries.resources.generated.resources.game_campaign_complete_title
import sodogku.libraries.resources.generated.resources.game_back_to_levels
import sodogku.libraries.resources.generated.resources.game_lost_title
import sodogku.libraries.resources.generated.resources.game_next_level
import sodogku.libraries.resources.generated.resources.game_watch_ad_badge
import sodogku.libraries.resources.generated.resources.game_refill_bones
import sodogku.libraries.resources.generated.resources.game_retry
import sodogku.libraries.resources.generated.resources.game_near_miss
import sodogku.libraries.resources.generated.resources.game_stat_levels
import sodogku.libraries.resources.generated.resources.game_stat_levels_spoken
import sodogku.libraries.resources.generated.resources.game_stat_levels_value
import sodogku.libraries.resources.generated.resources.game_dogs_found
import sodogku.libraries.resources.generated.resources.game_stat_dogs
import sodogku.libraries.resources.generated.resources.game_stat_mistakes
import sodogku.libraries.resources.generated.resources.game_stat_mistakes_spoken
import sodogku.libraries.resources.generated.resources.game_stat_paws
import sodogku.libraries.resources.generated.resources.game_stat_paws_spoken
import sodogku.libraries.resources.generated.resources.game_stat_score
import sodogku.libraries.resources.generated.resources.game_stat_score_spoken
import sodogku.libraries.resources.generated.resources.game_stat_time
import sodogku.libraries.resources.generated.resources.game_stat_time_spent_spoken
import sodogku.libraries.resources.generated.resources.game_stat_time_spoken
import sodogku.libraries.resources.generated.resources.game_reward_earned_treat
import sodogku.libraries.resources.generated.resources.game_skip_level
import sodogku.libraries.resources.generated.resources.game_skip_none_left
import sodogku.libraries.resources.generated.resources.game_skip_remaining
import sodogku.libraries.resources.generated.resources.game_time_new_best
import sodogku.libraries.resources.generated.resources.game_time_taken
import sodogku.libraries.resources.generated.resources.game_verdict_flawless
import sodogku.libraries.resources.generated.resources.game_verdict_scraped
import sodogku.libraries.resources.generated.resources.game_verdict_sharp
import sodogku.libraries.resources.generated.resources.game_won_title

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
        GamePhase.Won ->
            if (state.campaignComplete) {
                CampaignEndSheet(state, onAction, modifier)
            } else {
                WonSheet(state, onAction, modifier)
            }
        GamePhase.Lost -> LostSheet(state, onAction, modifier)
        GamePhase.Recap -> DailyRecapSheet(state, onAction, modifier)
        GamePhase.Loading, GamePhase.Playing -> Unit
    }
}

@Composable
private fun WonSheet(state: GameState, onAction: (GameAction) -> Unit, modifier: Modifier) {
    OutcomeLayout(modifier) {
        Dog(pose = DogPose.Solved)
        // The verdict **is** the title, rather than a line under it. The sheet
        // already carries a title, three paws, a rolling score, a time and
        // sometimes a reward chip and a streak, and the note it came back from
        // the owner with was that it is far too much text — so a run that went
        // well should say so in the space the fixed "Good dog!" was using.
        //
        // It says nothing about other players, deliberately. See `Standing`: the
        // percentile the original ask named would have to be invented, and an
        // invented measurement is worse than none.
        Text(
            text = stringResource(verdictTitle(state.standing)),
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
        // The facts, as a row rather than as more lines.
        //
        // Time used to be a sentence under the score and mistakes were not shown
        // at all. Both are measurements of the run and neither is worth a line of
        // prose: a stack asks to be read one item at a time, a row is scanned in
        // one look. Same facts, a fraction of the attention, on a sheet whose
        // standing note is that it is far too much text.
        StatPills(stats = winStats(state))

        // Only when it is true and only when it is close. See `nearMiss`: the
        // gap is in points rather than in seconds, because score is time *and*
        // combo *and* mistakes, and "five seconds faster" is a promise the
        // scoring cannot keep.
        state.nearMiss?.let { miss ->
            Text(
                text = stringResource(
                    Res.string.game_near_miss,
                    miss.pointsShort,
                    miss.nextPaw,
                ),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        // One more fact under the time, and only when the run beat the target
        // the board was showing. The miss is deliberately silent here: the
        // clock already said "Over 1:30" while the run was happening, and
        // repeating it on the sheet would turn a clear into a telling-off.
        //
        // [GameState.targetTimeMs] is the best as it stood when the attempt
        // opened, so this compares the run against the record it replaced
        // rather than against itself.
        if (state.beatBestTime) {
            elapsedLabel(state.targetTimeMs)?.let { previous ->
                Text(
                    text = stringResource(Res.string.game_time_new_best, previous),
                    typography = AppTheme.typography.Body.B600,
                    color = AppTheme.colors.text,
                )
            }
        }
        // The same chip the level pane promised, so the payout is recognisably
        // the thing that was advertised rather than a number quietly going up
        // in the booster row behind the sheet.
        if (state.treatAwarded) {
            LevelRewardChip(
                label = stringResource(Res.string.game_reward_earned_treat),
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
        // There is no next daily, so the daily's win sheet offers the pane
        // rather than tomorrow. The pane and not a pop: the daily has a deep
        // link of its own, so it is sometimes the start destination and popping
        // it closes the app (SD-54).
        if (state.isDaily) {
            ButtonPrimary(
                onClick = { onAction(GameAction.LevelsOpened) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.game_back_to_levels))
            }
        } else {
            ButtonPrimary(
                onClick = { onAction(GameAction.NextLevel) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                // No badge. Advancing plays no ad and cannot: the interstitial
                // was deleted along with every format the player does not ask
                // for, so the only thing a badge here could promise is something
                // this app decided not to do.
                Text(stringResource(Res.string.game_next_level))
            }
        }
    }
}

/**
 * The end of the campaign, which until now was the app closing.
 *
 * The last level's win sheet cannot be the ordinary one: its Next level button
 * asks for a board that does not exist, and what that used to do is pop the
 * start destination and take the app with it.
 *
 * So it reports the *campaign* the way the win sheet reports a run — as
 * measurements rather than as a speech. Three numbers, no verdict, and nothing
 * about what comes next except the one thing that genuinely does. What it will
 * not say is that more levels are coming: the pack ships inside the binary, and
 * an app that promises content it does not have has made an appointment it
 * cannot keep.
 *
 * The last board's own score is deliberately not here. It is the least
 * interesting number available at this moment, and the totals underneath it
 * already contain it.
 *
 * The daily is the only loop still running once the campaign is spent, and it
 * is what this sends the player to — but only when it is switched on. `PlayDaily`
 * refuses on a killed daily and a button that quietly does nothing is worse on
 * this sheet than on any other, because there is no other way off it. With the
 * daily off, the level pane becomes the primary control instead.
 *
 * That pane, and never a pop: popping is the thing that closed the app. This
 * sheet was the first of four to say so and is now the rule for all of them, so
 * the action that did it does not exist any more (SD-54). The drawer is drawn
 * over this sheet, so picking a level to replay starts an attempt and the ending
 * goes away with it.
 */
@Composable
private fun CampaignEndSheet(state: GameState, onAction: (GameAction) -> Unit, modifier: Modifier) {
    OutcomeLayout(modifier) {
        Dog(pose = DogPose.Solved)
        Text(
            text = stringResource(Res.string.game_campaign_complete_title),
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
        )
        // Absent rather than zeroed when the records would not read. See
        // `GameState.campaignTotals`.
        state.campaignTotals?.let { totals -> StatPills(stats = campaignStats(totals)) }
        if (state.dailyOffered) {
            Text(
                text = stringResource(Res.string.game_campaign_complete_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ButtonPrimary(
                onClick = { onAction(GameAction.PlayDaily) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                // The same two labels the drawer's card uses, and for the same
                // reason: a day already played opens on its result, and a button
                // saying Play would be offering a board that is gone.
                Text(
                    stringResource(
                        if (state.daily?.playable == true) {
                            Res.string.daily_play
                        } else {
                            Res.string.daily_review
                        },
                    ),
                )
            }
            ButtonGhost(
                onClick = { onAction(GameAction.LevelsOpened) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.game_back_to_levels))
            }
        } else {
            ButtonPrimary(
                onClick = { onAction(GameAction.LevelsOpened) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.game_back_to_levels))
            }
        }
    }
}

/**
 * The word at the top of the win sheet, one per verdict.
 *
 * Each is about the run and none is about anybody else. [Standing.Solid] keeps
 * the sheet's existing headline rather than earning a fourth string: it is the
 * middle of the range, the words are already right for it, and reusing it makes
 * the change read as the unusual runs saying something different rather than as
 * every win being re-captioned.
 *
 * Null falls back to the same line. A won sheet with no verdict is a state the
 * ViewModel does not produce — the verdict is set by the same update as the
 * phase — but a preview builds one, and the honest fallback for "we did not
 * measure this run" is the neutral congratulation, not the worst grade.
 */
internal fun verdictTitle(standing: Standing?): StringResource = when (standing) {
    Standing.Scraped -> Res.string.game_verdict_scraped
    Standing.Sharp -> Res.string.game_verdict_sharp
    Standing.Flawless -> Res.string.game_verdict_flawless
    Standing.Solid, null -> Res.string.game_won_title
}

/**
 * The end of an attempt that ran out of bones, as a report rather than a
 * consolation.
 *
 * It is the win sheet's sibling and is built to the same rule: the run is stated
 * as measurements and nothing on it commiserates. A player who has just lost is
 * not looking for a dog to feel sorry for them, and "Nice try!" over a board
 * they were two dogs from finishing is the app talking down to them.
 *
 * **Every control on it is free and none of it is final.** The revive costs an
 * ad, Start over costs the position, Skip costs an ad and one of the day's
 * allowance, and Levels costs nothing at all. There used to be a fifth — Give up
 * on today — which wrote a row that could never be taken back, and SD-49 took it
 * out rather than softening it. See `decisions.md`.
 */
@Composable
private fun LostSheet(state: GameState, onAction: (GameAction) -> Unit, modifier: Modifier) {
    OutcomeLayout(modifier) {
        Dog(pose = DogPose.HardMode)
        Text(
            text = stringResource(Res.string.game_lost_title),
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
        )
        lossStats(state).takeIf { it.isNotEmpty() }?.let { stats -> StatPills(stats = stats) }
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
        // On the daily as well, since SD-49. The owner's line was that you
        // should always be able to start from the beginning, and the daily was
        // the one board that said no. See `GameViewModel.restart` for why that
        // refusal was protecting nothing.
        ButtonSecondary(
            onClick = { onAction(GameAction.Retry) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_retry))
        }
        // Under both the revive and Start over, deliberately. This board is
        // still winnable and the offer to move past it should be the last thing
        // read, not the first — `features.md#skip` wants a rescue, not an
        // invitation to stop thinking.
        state.skip?.let { skip -> SkipButton(skip, onAction) }
        ButtonGhost(
            onClick = { onAction(GameAction.LevelsOpened) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_back_to_levels))
        }
    }
}

/**
 * The day already played, opened rather than refused.
 *
 * Reaching a spent daily used to resolve to a null board and bounce the route,
 * which dropped the player into the campaign with the daily closed behind them
 * and nothing said about why. Nothing here replays the day: it is over, and the
 * sheet exists so it can be *seen* to be over. Its one control is the way out,
 * and it opens the level pane rather than popping — this route has a deep link,
 * so it is sometimes the start destination (SD-54).
 *
 * [DailyOutcome.Failed] is now a **legacy** reading. The only thing that ever
 * wrote one was Give up on today, which SD-49 removed, so the failed branch
 * exists for days already sitting on disk rather than for any a player can still
 * produce. A day that is lost and not cleared writes no row at all and opens on
 * its board.
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
            onClick = { onAction(GameAction.LevelsOpened) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_back_to_levels))
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
            // plays. The cap still applies to Pro (`features.md#skip`).
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
        GameOutcomeSheet(
            state = GameState(phase = GamePhase.Won, paws = 3, standing = Standing.Flawless),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun WonSheetNewBestPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(
                phase = GamePhase.Won,
                paws = 3,
                standing = Standing.Sharp,
                elapsedMs = 78_000,
                targetTimeMs = 90_000,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun CampaignEndSheetPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(
                phase = GamePhase.Won,
                campaignComplete = true,
                campaignTotals = CampaignTotals(
                    levelsCleared = 997,
                    levelsTotal = 1_000,
                    paws = 4_412,
                    score = 812_340,
                ),
                daily = DailyStatus(
                    date = LocalDate(2026, 9, 10),
                    packIndex = 0,
                    levelId = 1,
                    result = null,
                    streak = 12,
                    freezeOffer = null,
                    restoreOffer = null,
                    resetsIn = PreviewDailyResetsIn,
                    enabled = true,
                ),
            ),
            onAction = {},
        )
    }
}

/** The ending with the daily switched off, which is the one-button version. */
@Preview
@Composable
private fun CampaignEndSheetWithoutTheDailyPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(phase = GamePhase.Won, campaignComplete = true),
            onAction = {},
        )
    }
}

/** A loss with nothing to report: no dogs down and no time on the clock. */
@Preview
@Composable
private fun LostSheetPreview() {
    PreviewContent {
        GameOutcomeSheet(state = GameState(phase = GamePhase.Lost), onAction = {})
    }
}

/**
 * The near miss, which is the row the sheet exists to draw.
 *
 * The board is built here rather than at file scope. Top-level properties in a
 * Kotlin file initialise together on first touch of anything in it, so a
 * preview's fixture hanging off `LevelPacks` would load the pack the first time
 * a real player finished a board.
 */
@Preview
@Composable
private fun LostSheetWithFactsPreview() {
    val level = LevelPacks.campaign.byId(LevelRecord.FIRST_LEVEL_ID)
    val twoShort = level?.let {
        (0 until it.size - 2).fold(Solution.empty(it.size)) { board, row ->
            board.withPlacement(row, it.solution[row])
        }
    }
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(
                phase = GamePhase.Lost,
                level = level,
                placed = twoShort ?: Solution.empty(1),
                elapsedMs = 252_000,
            ),
            onAction = {},
        )
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

/**
 * The three measurements of a finished campaign.
 *
 * Same row, same order of importance as [winStats]: what was done, then how
 * well, then what it was worth. Levels lead because they are the fact the sheet
 * is announcing, and they carry the total alongside them so a player who
 * skipped a board can see there is one waiting.
 *
 * Nothing here is tinted for danger. There is no number on this row where
 * smaller is better.
 */
@Composable
private fun campaignStats(totals: CampaignTotals): List<Stat> = listOf(
    Stat(
        caption = stringResource(Res.string.game_stat_levels),
        value = stringResource(
            Res.string.game_stat_levels_value,
            totals.levelsCleared,
            totals.levelsTotal,
        ),
        tint = AppTheme.colors.accentPrimary.color,
        spoken = stringResource(
            Res.string.game_stat_levels_spoken,
            totals.levelsCleared,
            totals.levelsTotal,
        ),
    ),
    Stat(
        caption = stringResource(Res.string.game_stat_paws),
        value = totals.paws.toString(),
        tint = AppTheme.colors.accentSecondary.color,
        spoken = stringResource(Res.string.game_stat_paws_spoken, totals.paws),
    ),
    Stat(
        caption = stringResource(Res.string.game_stat_score),
        value = totals.score.toString(),
        tint = AppTheme.colors.status.okay.color,
        spoken = stringResource(Res.string.game_stat_score_spoken, totals.score),
    ),
)

/**
 * A measurement an outcome sheet can report. The win sheet draws [Score], [Time]
 * and [Mistakes]; which of them a *loss* is allowed to draw is [lossFacts].
 */
internal enum class LossFact { Score, Dogs, Time, Mistakes }

/**
 * What a lost run has to say about itself.
 *
 * **Not the win sheet's row with worse numbers in it**, which is the obvious
 * build and reads as a scolding. Two of the four measurements are struck out on
 * purpose:
 *
 * - [Mistakes] is always the full set of bones, because spending them is what
 *   ended the run. A pill whose value is the same on every loss in the game
 *   carries no information and spends its space telling the player off.
 * - [Score] is whatever `Scoring.strike` left, and a lost attempt banks none of
 *   it — `GameState.lifetimeScore` drops it the moment the phase turns. Showing
 *   a number no record will ever hold is the sheet inventing a result.
 *
 * What is left is the pair that answers the question a player actually has on
 * this sheet, which is whether to reach for Start over or for Skip. [Dogs] is
 * how close the board came, and it is the one "14 of 16" worth saying: a run
 * that nearly landed reads differently from one that never got going, and the
 * player already watched both happen. [Time] is how long it took.
 *
 * Both drop out at zero rather than drawing a nought, matching [elapsedLabel]
 * and the recap sheet's paw rating. "Dogs 0/16" over a board where nothing went
 * right is the one case where the honest number really is rubbing it in, and a
 * run that ended before the clock moved has no duration to report.
 */
internal fun lossFacts(dogsPlaced: Int, elapsedMs: Long): List<LossFact> = buildList {
    if (dogsPlaced > 0) add(LossFact.Dogs)
    if (elapsedLabel(elapsedMs) != null) add(LossFact.Time)
}

/** [lossFacts], drawn. Same pills and same order of colour as the win sheet's row. */
@Composable
private fun lossStats(state: GameState): List<Stat> =
    lossFacts(state.dogsPlaced, state.elapsedMs).mapNotNull { fact ->
        when (fact) {
            LossFact.Dogs -> Stat(
                caption = stringResource(Res.string.game_stat_dogs),
                value = stringResource(
                    Res.string.game_dogs_found,
                    state.dogsPlaced,
                    state.dogsRequired,
                ),
                tint = AppTheme.colors.accentPrimary.color,
                spoken = stringResource(
                    Res.string.dogs_a11y,
                    state.dogsPlaced,
                    state.dogsRequired,
                ),
            )
            // Its own spoken form, not the win sheet's. That one reads
            // "Finished in 4:12", and a screen reader saying it over a board
            // nobody finished is the sheet lying about the run it exists to
            // report — the same trap the recap sheet gates its time on.
            LossFact.Time -> elapsedLabel(state.elapsedMs)?.let { time ->
                Stat(
                    caption = stringResource(Res.string.game_stat_time),
                    value = time,
                    tint = AppTheme.colors.accentSecondary.color,
                    spoken = stringResource(Res.string.game_stat_time_spent_spoken, time),
                )
            }
            LossFact.Score, LossFact.Mistakes -> null
        }
    }

/**
 * The four measurements of a finished run.
 *
 * Score first because it is what the sheet is about, then the two a player
 * compares against themselves, then mistakes. Mistakes last and in the danger
 * colour: it is the only one where a smaller number is better, and putting it
 * anywhere else in the row invites reading it as another thing that went well.
 *
 * Paws are not a pill. They have their own row above, drawn as paws, and a
 * pill saying "4/5" beside the picture of four paws is the same fact twice.
 */
@Composable
private fun winStats(state: GameState): List<Stat> = buildList {
    add(
        Stat(
            caption = stringResource(Res.string.game_stat_score),
            value = state.attemptScore.toString(),
            tint = AppTheme.colors.accentPrimary.color,
            spoken = stringResource(Res.string.game_stat_score_spoken, state.attemptScore),
        ),
    )
    elapsedLabel(state.elapsedMs)?.let { time ->
        add(
            Stat(
                caption = stringResource(Res.string.game_stat_time),
                value = time,
                tint = AppTheme.colors.accentSecondary.color,
                spoken = stringResource(Res.string.game_stat_time_spoken, time),
            ),
        )
    }
    add(
        Stat(
            caption = stringResource(Res.string.game_stat_mistakes),
            value = state.strikesThisAttempt.toString(),
            // Green on a clean sheet. A zero in the danger colour reads as a
            // warning about the one number that means nothing went wrong.
            tint = if (state.strikesThisAttempt == 0) {
                AppTheme.colors.status.okay.color
            } else {
                AppTheme.colors.danger.color
            },
            spoken = stringResource(Res.string.game_stat_mistakes_spoken, state.strikesThisAttempt),
        ),
    )
}

/** Long enough that the preview's card never reads as about to roll over. */
private val PreviewDailyResetsIn = 6.hours
