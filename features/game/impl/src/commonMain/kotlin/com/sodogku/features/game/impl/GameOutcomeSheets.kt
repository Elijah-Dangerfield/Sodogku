package com.sodogku.features.game.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.focusTarget
import com.sodogku.libraries.ui.components.celebration.Arriving
import com.sodogku.libraries.ui.components.celebration.RayBurst
import com.sodogku.libraries.ui.components.celebration.beatDelayMillis
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Surface
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.button.ButtonStyle
import com.sodogku.libraries.ui.components.button.ProButton
import com.sodogku.libraries.ui.components.button.ProLink
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
import com.sodogku.libraries.ui.components.game.LevelRewardChip
import com.sodogku.libraries.ui.components.game.RewardBadge
import com.sodogku.libraries.ui.components.game.Stat
import com.sodogku.libraries.ui.components.game.StatChipRoll
import com.sodogku.libraries.ui.components.game.StatChipRow
import com.sodogku.libraries.ui.components.game.StatChipSpec
import com.sodogku.libraries.ui.components.game.StatPills
import com.sodogku.libraries.ui.components.game.UnearnedPaw
import com.sodogku.libraries.scoring.PawGap
import com.sodogku.libraries.scoring.Scoring
import com.sodogku.libraries.scoring.Standing
import com.sodogku.libraries.ui.components.text.DisplayNumeralStrokeWidth
import com.sodogku.libraries.ui.components.text.OutlinedText
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.daily_date
import sodogku.libraries.resources.generated.resources.daily_done
import sodogku.libraries.resources.generated.resources.dogs_a11y
import sodogku.libraries.resources.generated.resources.daily_play
import sodogku.libraries.resources.generated.resources.daily_review
import sodogku.libraries.resources.generated.resources.daily_streak
import sodogku.libraries.resources.generated.resources.game_campaign_complete_body
import sodogku.libraries.resources.generated.resources.game_campaign_complete_title
import sodogku.libraries.resources.generated.resources.game_back_to_levels
import sodogku.libraries.resources.generated.resources.game_cleared_level
import sodogku.libraries.resources.generated.resources.game_cleared_level_best
import sodogku.libraries.resources.generated.resources.game_lost_title
import sodogku.libraries.resources.generated.resources.game_next_level
import sodogku.libraries.resources.generated.resources.game_points_to_next_paw
import sodogku.libraries.resources.generated.resources.game_watch_ad_badge
import sodogku.libraries.resources.generated.resources.game_refill_bones
import sodogku.libraries.resources.generated.resources.game_retry
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
import sodogku.libraries.resources.generated.resources.game_time_taken
import sodogku.libraries.resources.generated.resources.paw_ordinal_fifth
import sodogku.libraries.resources.generated.resources.paw_ordinal_fourth
import sodogku.libraries.resources.generated.resources.paw_ordinal_second
import sodogku.libraries.resources.generated.resources.paw_ordinal_third
import sodogku.libraries.resources.generated.resources.game_verdict_flawless
import sodogku.libraries.resources.generated.resources.game_verdict_scraped
import sodogku.libraries.resources.generated.resources.game_verdict_sharp
import sodogku.libraries.resources.generated.resources.game_won_title

/**
 * The end of an attempt.
 *
 * Two shapes, not one. A clear is a **full-screen celebration** that slides up
 * over the board and arrives a piece at a time ([WinCelebration]); everything
 * else is a card on a scrim with the grid still visible behind it
 * ([ScrimmedOutcome]). That split is the whole of SD-123: a win is a moment and
 * wants the screen, a loss and a recap are reports and want the board they are
 * reporting on left in view.
 *
 * The losing sheet deliberately does **not** reveal the answer. A player who is
 * shown the solution has no reason to retry, and retrying is the whole point of
 * a three-life game.
 *
 * ### Why none of these is a `bottomSheet<>` destination
 *
 * They read like sheets and they are not routed like sheets, which is worth
 * stating because the obvious change is to make them destinations.
 *
 * A `bottomSheet<>` is a floating window: it is dismissible by construction —
 * `BottomSheetDestination` installs a `BackHandler` that closes it, and the
 * scrim takes a tap — and it draws *above* everything in the nav host. Both are
 * disqualifying here. Dismissing an outcome leaves the player on a finished,
 * dead board with no control on it, which is the same argument `OfflineBlockRoute`
 * already makes for staying a `screen<>`. And the level pane is drawn over these
 * (`LevelDrawer`, `GameScreen`), which is how a daily's win sheet and the
 * campaign ending are left at all — in a window above the host, the pane would
 * open *behind* the celebration and the one way out would be unreachable.
 *
 * The other half of the answer is the view model. Next level does not navigate:
 * `GameViewModel.nextLevel` swaps the board in place, and Levels opens the pane
 * on this same screen. A destination of its own gets its own `ViewModelStore`,
 * and the nested graph that would let two destinations share one is not
 * available here — `GameRoute` is the app's start destination and a nav graph's
 * start destination must be a direct child of it.
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
                ScrimmedOutcome(modifier) { CampaignEndSheet(state, onAction) }
            } else {
                WinCelebration(state, onAction, modifier)
            }
        GamePhase.Lost -> ScrimmedOutcome(modifier) { LostSheet(state, onAction) }
        GamePhase.Recap -> ScrimmedOutcome(modifier) { DailyRecapSheet(state, onAction) }
        GamePhase.Loading, GamePhase.Playing -> Unit
    }
}

/**
 * One thing a clear has to say about itself, in the order it is said.
 *
 * The list is the celebration's script: the first five are always in it, the
 * next three are earned, and the position in the list is also **when** each one
 * lands, so the sequence and the content are one decision rather than two that
 * can drift.
 *
 * Its siblings are conditional in ways that are invisible from the code drawing
 * them, which is why this is a function rather than a run of `if`s inside a
 * composable — the same move [lossFacts] made, for the same reason.
 *
 * The order is the 2026-09 handoff's board-cleared row, top to bottom, with
 * the two lines the design does not draw slotted where they do not break it.
 * A new best time is not a beat any more: it is a second sentence on [Sub].
 * The score is not a beat of its own either; it is the first of the [Chips],
 * still rolling up from zero with its burst, because the design puts the
 * number in a chip and the number is what the page is about.
 *
 * The button is deliberately **not** a beat. It is the control, not the
 * celebration, and it is live and legible from the first frame: a player
 * clearing ten easy boards in a row must not wait on a flourish to tap Next.
 */
internal enum class WinBeat {
    /** The dog on its ray burst. */
    Hero,
    Verdict,
    /** "Level 23 cleared", with the best-run sentence folded in when earned. */
    Sub,
    Paws,
    /** Score, time, mistakes. */
    Chips,

    /**
     * The daily's streak line, directly under the chips. Not on the design,
     * which has no daily; kept as its own line rather than as a third
     * sentence on [Sub], because the daily's sub is already a full sentence
     * and the streak is the daily's reward rather than a fact about the run.
     */
    Streak,

    /** "22 more points would have earned a fourth paw." On every clear short of five. */
    Footnote,

    /**
     * The level reward chip, between the footnote and the button. Not on the
     * design either; it is the payout the level pane promised, and dropping it
     * would be dropping something the player earned.
     */
    Treat,
}

/**
 * What this clear gets to say, in order. See [WinBeat].
 *
 * Every beat scheduled here draws something. [WinBeat.Footnote] is gated on
 * the gap being resolved rather than on the paw count, because the two are one
 * decision made where the config is: [GameState.pawGap] is null at five paws
 * and nowhere else. Re-checking the count here would be a second guard on a
 * case the first has already refused, and no test could ever fail on it.
 */
internal fun winBeats(state: GameState): List<WinBeat> = buildList {
    add(WinBeat.Hero)
    add(WinBeat.Verdict)
    add(WinBeat.Sub)
    add(WinBeat.Paws)
    add(WinBeat.Chips)
    if (state.isDaily && state.dailyStreak > 0) add(WinBeat.Streak)
    if (state.pawGap != null) add(WinBeat.Footnote)
    if (state.treatAwarded) add(WinBeat.Treat)
}

/** Which sentence sits under the verdict. See [winSub]. */
internal enum class WinSub {
    /** The daily has no level number; it reuses the recap's line. */
    Daily,

    /** "Level N cleared." */
    Level,

    /** "Level N cleared. That is your best run yet." */
    LevelBest,
}

/**
 * The line under the verdict, decided over plain values so it can be tested.
 *
 * The daily is checked first and not merely because it has no number: it also
 * has no target time, so [GameState.beatBestTime] cannot be true there today,
 * and this is what keeps that true if a daily ever grows a clock to chase. A
 * best-run claim on a board the player cannot replay would be a promise about
 * a race that never happens.
 */
internal fun winSub(state: GameState): WinSub = when {
    state.isDaily -> WinSub.Daily
    state.beatBestTime -> WinSub.LevelBest
    else -> WinSub.Level
}

/**
 * A clear, as a page that arrives rather than a card that is already there.
 *
 * The panel covers the board instead of floating over it. That reverses the
 * older note about keeping the grid visible under the outcome, and on purpose:
 * the grid is a finished puzzle by now, and the ask was for the Duolingo lesson
 * ending, which is a screen and not a dialog. The two sheets that are still
 * *reports* — a loss, a recap — keep the scrim and keep the board, because there
 * the board is still the subject.
 *
 * **The panel eases and the contents spring.** Everything else in this app uses
 * `Motion.Pop`, which overshoots; a full-display panel that overshoots slides
 * clear off the top of the screen and shows the board underneath it for a few
 * frames. So the bounce lives in each piece as it lands, which is where it can
 * be seen anyway.
 */
@Composable
private fun WinCelebration(state: GameState, onAction: (GameAction) -> Unit, modifier: Modifier) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    val risen = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) {
        if (still) {
            risen.snapTo(1f)
            return@LaunchedEffect
        }
        risen.animateTo(1f, tween(PanelRiseMillis, easing = FastOutSlowInEasing))
    }

    Surface(
        color = AppTheme.colors.background,
        contentColor = AppTheme.colors.text,
        radius = Radii.None,
        modifier = modifier
            .fillMaxSize()
            // Read in the layer, never in composition. This subtree holds a
            // rolling counter, a paw burst and a row of stats, and reading the
            // rise here would recompose all of it on every frame of the slide.
            .graphicsLayer { translationY = (1f - risen.value) * size.height },
    ) {
        // The panel runs the full height of the display, so it owns its own
        // insets. The top one goes above the band, so the burst does not turn
        // under the clock; the bottom one goes under the button, and the
        // handoff's 34 already allows for a home indicator, so the larger of
        // the two rather than both.
        val insets = WindowInsets.systemBars.asPaddingValues()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding()),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // Scrolls when it has to. The script is five beats on most
                // clears and eight on the best one a daily can produce, and the
                // first of those is a 250dp band — on a short display, or at a
                // large system font size, a fixed column drops whatever is at
                // the bottom. The button is outside this one so it cannot be
                // the thing that goes. Top-aligned rather than centred, as the
                // board is: the band's 60 from the status bar is a measurement,
                // not slack.
                modifier = Modifier
                    .weight(ScriptWeight)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                winBeats(state).forEachIndexed { order, beat ->
                    Arriving(order, modifier = Modifier.fillMaxWidth()) { WinBeatContent(beat, state, order) }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    start = ScreenGutter,
                    top = ButtonTop,
                    end = ScreenGutter,
                    bottom = insets.calculateBottomPadding().coerceAtLeast(ButtonBottom),
                ),
            ) {
                OnwardButton(state = state, onAction = onAction, modifier = Modifier)
                // Under the one button, as a line rather than a second button:
                // the moment a player is most pleased with the game is the
                // moment to mention Pro, and a filled button would fight Next
                // level for the eye. Absent with the rest of the offer.
                state.proOffer?.let { offer ->
                    ProLink(
                        priceLabel = offer.priceLabel,
                        enabled = !state.proPurchasing,
                        onClick = { onAction(GameAction.BuyPro(ProButtonSource.Cleared)) },
                        modifier = Modifier.padding(top = Dimension.D300),
                    )
                }
            }
        }
    }
}

/**
 * One beat, drawn, with the handoff's gap above it. [order] is passed down
 * rather than recovered, because the score chip's roll has to wait for its
 * own arrival and only the script knows when that is.
 */
@Composable
private fun WinBeatContent(beat: WinBeat, state: GameState, order: Int) {
    when (beat) {
        // The burst is bigger than the band and is not clipped to it, exactly
        // as the board draws it: the rays overrun the band by 25 top and bottom
        // and the headline sits over their tips.
        WinBeat.Hero -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = BandTop)
                .height(BandHeight),
        ) {
            RayBurst()
            Dog(pose = DogPose.Solved, size = HeroDogSize)
        }
        // The verdict **is** the title, rather than a line under it. It says
        // nothing about other players, deliberately. See `Standing`: the
        // percentile the original ask named would have to be invented, and an
        // invented measurement is worse than none.
        //
        // Amber with the cream stroke, the same treatment as the streak count:
        // the one headline in the app that is set in the brand colour rather
        // than in ink, because it is a cheer rather than a heading.
        WinBeat.Verdict -> OutlinedText(
            text = stringResource(verdictTitle(state.standing)),
            typography = AppTheme.typography.Display.D1100,
            color = AppTheme.colors.accentBrand,
            strokeColor = AppTheme.colors.textOutline,
            strokeWidth = DisplayNumeralStrokeWidth,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = HeadlineTop, start = ScreenGutter, end = ScreenGutter),
        )
        // The best-run sentence rides here rather than as a line of its own.
        // The miss is deliberately silent: the clock already said "Over 1:30"
        // while the run was happening, and repeating it on the page would turn
        // a clear into a telling-off. [GameState.targetTimeMs] is the best as
        // it stood when the attempt opened, so this compares the run against
        // the record it replaced rather than against itself.
        WinBeat.Sub -> WinSubLine(state, modifier = Modifier.padding(top = SubTop, start = ScreenGutter, end = ScreenGutter))
        // One paw at a time. Five landing together is a rating appearing;
        // five landing in sequence is five things being awarded, which is what
        // the player just did. The stroked unearned paw is the celebration's
        // own: the missing paws are what the footnote is about, so they are
        // drawn as shapes rather than as a faint ghost.
        WinBeat.Paws -> PawRating(
            paws = state.paws,
            size = PawSize,
            gap = PawSpacing,
            unearned = UnearnedPaw.Stroked,
            staggerMillis = PawStaggerMillis,
            modifier = Modifier.padding(top = PawsTop),
        )
        // The facts, as a row of chips rather than as more lines. The score is
        // the first of them and it is *earned* rather than found: it rolls up
        // from zero while a handful of paws fly down into it from where the
        // rating sits. Both wait for the chip to be on screen, or the roll
        // happens behind an alpha of zero and the number fades in already
        // landed.
        //
        // On the celebration and not up in the header, which is the one
        // decision worth stating. The header's counter holds the lifetime total
        // and it does roll when this clear lands, but the header is behind this
        // panel. This number is the one the page is about.
        //
        // Marked for the unlock toast, which hangs under this row while the
        // celebration is up. Under the lives pill, where it hangs on a board
        // in play, it landed on the dog.
        WinBeat.Chips -> StatChipRow(
            chips = winChips(state, scoreDelayMillis = beatDelayMillis(order)),
            modifier = Modifier
                .padding(top = ChipsTop, start = ScreenGutter, end = ScreenGutter)
                .focusTarget(WinChipsFocusKey),
        )
        // The streak is the reward for a daily, so it is shown under the score
        // rather than left for the player to find back in the drawer.
        WinBeat.Streak -> Text(
            text = stringResource(Res.string.daily_streak, state.dailyStreak),
            typography = AppTheme.typography.Body.B700,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = FootnoteTop, start = FootnoteGutter, end = FootnoteGutter),
        )
        // The gap is in points rather than in seconds, because score is time
        // *and* combo *and* mistakes, and "five seconds faster" is a promise
        // the scoring cannot keep. See `pointsToNextPaw`.
        WinBeat.Footnote -> state.pawGap?.let { gap ->
            Text(
                text = pluralStringResource(
                    Res.plurals.game_points_to_next_paw,
                    gap.pointsShort,
                    gap.pointsShort,
                    stringResource(pawOrdinal(gap.nextPaw)),
                ),
                typography = AppTheme.typography.Body.B700,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = FootnoteTop, start = FootnoteGutter, end = FootnoteGutter),
            )
        }
        // The same chip the level pane promised, so the payout is recognisably
        // the thing that was advertised rather than a number quietly going up
        // in the booster row behind the panel.
        WinBeat.Treat -> LevelRewardChip(
            label = stringResource(Res.string.game_reward_earned_treat),
            modifier = Modifier.padding(top = FootnoteTop),
        )
    }
}

/** [winSub], drawn. The level number is the same one the board's header shows. */
@Composable
private fun WinSubLine(state: GameState, modifier: Modifier) {
    val text = when (winSub(state)) {
        WinSub.Daily -> stringResource(Res.string.daily_done)
        WinSub.Level -> stringResource(Res.string.game_cleared_level, state.level?.id ?: return)
        WinSub.LevelBest -> stringResource(Res.string.game_cleared_level_best, state.level?.id ?: return)
    }
    Text(
        text = text,
        typography = AppTheme.typography.Body.B700,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * The word for the paw the footnote points at. [PawGap.nextPaw] is 2 to 5 by
 * construction: there is no rung above the fifth, and a run that earned none
 * did not finish and has no celebration.
 */
internal fun pawOrdinal(paw: Int): StringResource = when (paw) {
    Scoring.TWO_PAWS -> Res.string.paw_ordinal_second
    Scoring.THREE_PAWS -> Res.string.paw_ordinal_third
    Scoring.FOUR_PAWS -> Res.string.paw_ordinal_fourth
    else -> Res.string.paw_ordinal_fifth
}

/**
 * The way on, at the bottom of the page and live from the first frame.
 *
 * There is no next daily, so the daily's celebration offers the pane rather than
 * tomorrow. The pane and not a pop: the daily has a deep link of its own, so it
 * is sometimes the start destination and popping it closes the app (SD-54).
 *
 * The hero size is the handoff's one button on a full-screen moment. The
 * decision above it is untouched.
 */
@Composable
private fun OnwardButton(state: GameState, onAction: (GameAction) -> Unit, modifier: Modifier) {
    if (state.isDaily) {
        ButtonPrimary(
            onClick = { onAction(GameAction.LevelsOpened) },
            size = ButtonSize.Hero,
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_back_to_levels))
        }
    } else {
        ButtonPrimary(
            onClick = { onAction(GameAction.NextLevel) },
            size = ButtonSize.Hero,
            modifier = modifier.fillMaxWidth(),
        ) {
            // No badge, though advancing can now play an ad: the interstitial
            // floor (SD-148) fires here one time in `ads.interstitialEveryLevels`
            // for a free player who has seen none. The badge means "watch this
            // and get that", and there is no that. A badge that lit only when
            // the floor was met would be an honest alternative; it is not built.
            Text(stringResource(Res.string.game_next_level))
        }
    }
}

/**
 * The scrim the two report sheets and the campaign ending sit on.
 *
 * The board stays visible underneath: a player who ran out of bones should still
 * see the grid they ran out on, and a recap's board is a backdrop rather than a
 * puzzle. A clear does not use this — see [WinCelebration].
 */
@Composable
private fun ScrimmedOutcome(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.backgroundOverlay.color),
    ) {
        content()
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
private fun CampaignEndSheet(state: GameState, onAction: (GameAction) -> Unit) {
    OutcomeLayout {
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
private fun LostSheet(state: GameState, onAction: (GameAction) -> Unit) {
    OutcomeLayout {
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
        // The other answer to "watch an ad": the same tap the sheet's Pro
        // offer makes, without the sheet. Outlined, so the revive stays the
        // thing this screen is for.
        state.proOffer?.let { offer ->
            ProButton(
                priceLabel = offer.priceLabel,
                size = ButtonSize.Large,
                style = ButtonStyle.Outlined,
                enabled = !state.proPurchasing,
                onClick = { onAction(GameAction.BuyPro(ProButtonSource.LostSheet)) },
                modifier = Modifier.fillMaxWidth(),
            )
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
 * **Every day that reaches this sheet was finished**, which is why nothing here
 * commiserates and why the time is no longer gated on anything. It used to have
 * a second face: a [DailyOutcome.Failed] row drew "Out of bones for today" over
 * a dimmed board, with the way out as its only control and no way back into the
 * puzzle. That is the dead end SD-111 was filed about, and it is gone at the
 * source — `toResult` no longer reads a `Failed` row as a result, so a day given
 * up on by an older build opens on its board from the beginning like any other
 * unplayed day. The sheet cannot be reached without a result, and the three
 * outcomes that remain all mean the day is over.
 */
@Composable
private fun DailyRecapSheet(state: GameState, onAction: (GameAction) -> Unit) {
    val recap = state.dailyRecap
    OutcomeLayout {
        Dog(pose = DogPose.Solved)
        Text(
            text = stringResource(Res.string.daily_done),
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
            // Recalled, not awarded. Still gated on the number: a frozen or a
            // restored day is worth zero, and three empty paws would read as a
            // nought-out-of-three score rather than as a day nobody played.
            if (recap.paws > 0) PawRating(paws = recap.paws, animated = false)
            if (recap.score > 0) {
                Text(
                    text = recap.score.toString(),
                    typography = AppTheme.typography.Heading.H600,
                    color = AppTheme.colors.accentPrimary,
                )
            }
            // Recalled like everything else on this sheet, so no roll and no
            // paws. [elapsedLabel] is null at zero, which is what keeps a frozen
            // or restored day from claiming it was solved in no time at all.
            elapsedLabel(recap.timeMs)?.let { time ->
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
 * The card a report sits on: a loss, a daily recap, and the campaign ending.
 *
 * Not a clear, since SD-123 — that has the screen to itself in [WinCelebration].
 * These three keep the card because the board behind them is still the subject.
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
private fun OutcomeLayout(content: @Composable () -> Unit) {
    Surface(
        color = AppTheme.colors.surfacePrimary,
        contentColor = AppTheme.colors.onSurfacePrimary,
        modifier = Modifier
            .padding(horizontal = Dimension.D800)
            .fillMaxWidth(),
        radius = Radii.Card,
        contentPadding = ModalDialogDefaults.ContentPadding,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D600),
        ) {
            content()
        }
    }
}

/**
 * The celebration takes the whole page, so the preview is given one at the
 * handoff's frame. Under [LocalInspectionMode] every beat is already landed,
 * which is the point of the rule, and also what makes this preview worth
 * looking at.
 *
 * The board is loaded inside the preview rather than at file scope, for the
 * reason [LostSheetWithFactsPreview] gives.
 */
@Preview(heightDp = 844)
@Composable
private fun WonSheetPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(
                phase = GamePhase.Won,
                level = LevelPacks.campaign.byId(LevelRecord.FIRST_LEVEL_ID),
                paws = 3,
                standing = Standing.Sharp,
                elapsedMs = 123_000,
                strikesThisAttempt = 1,
                pawGap = PawGap(nextPaw = 4, pointsShort = 22),
            ),
            onAction = {},
        )
    }
}

/** A flawless five-paw clear that beat its best: no footnote, and the sub says so. */
@Preview(heightDp = 844)
@Composable
private fun WonSheetNewBestPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(
                phase = GamePhase.Won,
                level = LevelPacks.campaign.byId(LevelRecord.FIRST_LEVEL_ID),
                paws = 5,
                standing = Standing.Flawless,
                elapsedMs = 78_000,
                targetTimeMs = 90_000,
            ),
            onAction = {},
        )
    }
}

/** The daily's clear: every optional beat at once, and the pane as the way on. */
@Preview(heightDp = 844)
@Composable
private fun WonDailySheetPreview() {
    PreviewContent {
        GameOutcomeSheet(
            state = GameState(
                phase = GamePhase.Won,
                isDaily = true,
                paws = 2,
                standing = Standing.Solid,
                elapsedMs = 78_000,
                dailyStreak = 12,
                treatAwarded = true,
                pawGap = PawGap(nextPaw = 3, pointsShort = 140),
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
 * Same order of importance as the clear's [winChips]: what was done, then how
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

/** [lossFacts], drawn. The same order of colour as the clear's [winChips], as pills rather than chips. */
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
 * The three measurements of a finished run, as the 2026-09 handoff's chips.
 *
 * Score first because it is what the sheet is about, then time, then mistakes.
 * Mistakes last and in the danger colour: it is the only one where a smaller
 * number is better, and putting it anywhere else in the row invites reading it
 * as another thing that went well. Red on a zero too, which the pills this
 * replaced greened; the handoff draws one red chip and a chip that changed
 * colour would be the row keeping a rule the design dropped.
 *
 * Time drops out when [elapsedLabel] has nothing, the same refusal the recap
 * makes: a run with no clock on it has no time to report.
 *
 * Paws are not a chip. They have their own row above, drawn as paws, and a
 * chip saying "4/5" beside the picture of four paws is the same fact twice.
 *
 * The glyphs are the characters the design draws. The design system has a
 * clock and a cross but no bolt, and a row that set two of three in icons
 * would be two rows.
 */
@Composable
private fun winChips(state: GameState, scoreDelayMillis: Int): List<StatChipSpec> = buildList {
    add(
        StatChipSpec(
            label = stringResource(Res.string.game_stat_score),
            value = state.attemptScore.toString(),
            tint = AppTheme.colors.accentBrand,
            labelInk = AppTheme.colors.onAccentBrand,
            valueInk = AppTheme.colors.accentBrandInk,
            spoken = stringResource(Res.string.game_stat_score_spoken, state.attemptScore),
            glyph = ScoreGlyph,
            roll = StatChipRoll(to = state.attemptScore, startDelayMillis = scoreDelayMillis),
        ),
    )
    elapsedLabel(state.elapsedMs)?.let { time ->
        add(
            StatChipSpec(
                label = stringResource(Res.string.game_stat_time),
                value = time,
                tint = AppTheme.colors.accentSecondary,
                labelInk = AppTheme.colors.onAccentSecondary,
                valueInk = AppTheme.colors.accentSecondary,
                spoken = stringResource(Res.string.game_stat_time_spoken, time),
                glyph = TimeGlyph,
            ),
        )
    }
    add(
        StatChipSpec(
            label = stringResource(Res.string.game_stat_mistakes),
            value = state.strikesThisAttempt.toString(),
            tint = AppTheme.colors.danger,
            labelInk = AppTheme.colors.onAccentPrimary,
            valueInk = AppTheme.colors.danger,
            spoken = stringResource(Res.string.game_stat_mistakes_spoken, state.strikesThisAttempt),
            glyph = MistakesGlyph,
        ),
    )
}

private const val ScoreGlyph = "⚡"
private const val TimeGlyph = "⏱"
private const val MistakesGlyph = "❌"

/** Long enough that the preview's card never reads as about to roll over. */
private val PreviewDailyResetsIn = 6.hours

/**
 * How long the panel takes to cover the board.
 *
 * Past the ~300ms [Motion] holds feedback to, and argued the same way
 * `Motion.PlacementPulseMillis` is: this is the reward rather than the response
 * to a tap, it happens once per board, and the button under it is live the whole
 * time it runs.
 */
private const val PanelRiseMillis = 340

/** The same gap inside the paw rating, a touch longer because there are only five. */
private const val PawStaggerMillis = 110

/** The script takes the slack; the button keeps the bottom of the page. */
private const val ScriptWeight = 1f

/*
 * The board-cleared measurements, from the handoff's row 03. Everything on
 * the scale is a rung; everything off it is the nearest rung plus the
 * difference, the way the streak ceremony writes its own, rather than
 * snapped, because these were tuned together on the board.
 */

/** The band starts 60 below the status bar. Off the scale by two. */
private val BandTop = Dimension.D1400 + Dimension.D50

/** A 250 band, two and a half of the biggest rung, the way the burst behind it is three. */
private val BandHeight = Dimension.D1900 * 2.5f

/** The handoff's 128 dog: the biggest rung plus the 28 one. */
private val HeroDogSize = Dimension.D1900 + Dimension.D1000

private val HeadlineTop = Dimension.D850
private val SubTop = Dimension.D400
private val PawsTop = Dimension.D850

/** 36 paws at a 12 gap. The size is off the scale by two. */
private val PawSize = Dimension.D1100 + Dimension.D50
private val PawSpacing = Dimension.D500

/** `padding: 26px 16px 0` on the chip row. The top is off the scale by two. */
private val ChipsTop = Dimension.D900 + Dimension.D50
private val ScreenGutter = Dimension.D700

/** `padding: 24px 40px 0` on the footnote, and the same gap over the lines that share its block. */
private val FootnoteTop = Dimension.D900
private val FootnoteGutter = Dimension.D1200

/**
 * `padding: 0 16px 34px` on the button. The top is not the handoff's zero:
 * the button is anchored to the bottom by the script's weight, so this only
 * ever shows when the script is tall enough to scroll, and then it is the gap
 * that stops the last beat touching the button.
 */
private val ButtonTop = Dimension.D700
private val ButtonBottom = Dimension.D1100
