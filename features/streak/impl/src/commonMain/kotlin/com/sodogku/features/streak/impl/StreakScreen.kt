package com.sodogku.features.streak.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.FullScreenLoader
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonAccent
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.button.ButtonStyle
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.streak.StreakCalendar
import com.sodogku.libraries.ui.components.streak.StreakHero
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.streak_calendar_title
import sodogku.libraries.resources.generated.resources.streak_day_label
import sodogku.libraries.resources.generated.resources.streak_empty_body
import sodogku.libraries.resources.generated.resources.streak_keep_it_today
import sodogku.libraries.resources.generated.resources.streak_longest
import sodogku.libraries.resources.generated.resources.streak_longest_none
import sodogku.libraries.resources.generated.resources.streak_over_body
import sodogku.libraries.resources.generated.resources.streak_over_cta
import sodogku.libraries.resources.generated.resources.streak_over_day_label
import sodogku.libraries.resources.generated.resources.streak_over_kicker
import sodogku.libraries.resources.generated.resources.streak_title
import sodogku.libraries.resources.generated.resources.streak_update_body
import sodogku.libraries.resources.generated.resources.streak_update_cta
import sodogku.libraries.resources.generated.resources.streak_update_kicker

/**
 * One route, two pages, chosen by [StreakState.ceremony].
 *
 * A ceremony, a run that grew or a run that broke, is the handoff's band
 * layout ([StreakCeremonyLayout]): a full-screen moment with one thing to tap.
 * A plain visit, the player tapping the flame, is the status page it has
 * always been: the run, the record, and the last five weeks. The owner chose
 * to keep that page rather than fold it into the ceremony, because what a
 * player who came looking wants is the calendar, and the ceremony has no room
 * for one.
 *
 * **Nothing on the status page moves.** There is no entrance and the week
 * strip is handed no day to pop; both halves of that are decided in
 * [StreakState] so something can test them.
 *
 * The slide up is not here either. A page cannot rise over a board the
 * navigator has already taken away, so the entrance belongs to `StreakRoute`,
 * which asks for a slide up and for the board underneath to hold still.
 */
@Composable
fun StreakScreen(
    state: StreakState,
    onAction: (StreakAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading -> Screen(modifier = modifier.fillMaxSize()) { FullScreenLoader() }
        state.lost > 0 -> StreakLost(state, onAction, modifier)
        state.ceremony -> StreakUpdate(state, onAction, modifier)
        else -> StreakStatusPage(state, onAction, modifier)
    }
}

/** The day a finished board pushed the run up. Blue band, amber number, one amber button. */
@Composable
private fun StreakUpdate(state: StreakState, onAction: (StreakAction) -> Unit, modifier: Modifier) {
    StreakCeremonyLayout(
        moment = StreakMoment.Update,
        kicker = stringResource(Res.string.streak_update_kicker),
        number = state.current,
        countUpFrom = state.countUpFrom,
        dayLabel = pluralStringResource(Res.plurals.streak_day_label, state.current, state.current),
        body = pluralStringResource(Res.plurals.streak_update_body, state.current, state.current, state.current + 1),
        week = state.days.toWeekStrip(state.weekStrip),
        justLanded = state.justLanded,
        modifier = modifier,
    ) {
        ButtonPrimary(
            onClick = { onAction(StreakAction.Back) },
            accent = ButtonAccent.Brand,
            size = ButtonSize.Hero,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.streak_update_cta))
        }
    }
}

/**
 * The first board after a break. Brown band, and the number is the run that
 * ended, in the grey of a thing that is over.
 *
 * **One action, and it is a dismissal.** The run is already one (the board
 * they just finished), so "start from one" is the page acknowledging what has
 * happened, not offering a choice. The handoff draws a blue `SPEND A FREEZE`
 * above the ghost; the freeze is not built (SD-143, and the design in
 * `docs/design/streak-freeze.md`, where the freeze may spend itself and this
 * screen becomes the place that says so). When it is, the primary goes in
 * the slot above the ghost, gated on the balance the design describes.
 */
@Composable
private fun StreakLost(state: StreakState, onAction: (StreakAction) -> Unit, modifier: Modifier) {
    StreakCeremonyLayout(
        moment = StreakMoment.Lost,
        kicker = stringResource(Res.string.streak_over_kicker),
        number = state.lost,
        countUpFrom = state.countUpFrom,
        dayLabel = stringResource(Res.string.streak_over_day_label),
        body = stringResource(Res.string.streak_over_body),
        week = state.days.toWeekStrip(state.weekStrip),
        justLanded = state.justLanded,
        modifier = modifier,
    ) {
        // The freeze's slot. See the KDoc above; SD-143.
        ButtonSecondary(
            onClick = { onAction(StreakAction.Back) },
            size = ButtonSize.Hero,
            style = ButtonStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.streak_over_cta))
        }
    }
}

/** The page a player opens by tapping the flame: the run, the record, and the last five weeks. */
@Composable
private fun StreakStatusPage(state: StreakState, onAction: (StreakAction) -> Unit, modifier: Modifier) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.streak_title),
                onNavigateBack = { onAction(StreakAction.Back) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(padding),
        ) {
            VerticalSpacerD500()

            StreakHero(
                streak = state.current,
                week = state.days.toWeekStrip(state.weekStrip),
                dayLabel = pluralStringResource(Res.plurals.streak_day_label, state.current, state.current),
            )

            VerticalSpacerD500()

            // The record is quieter than the run. It is the thing to beat, not
            // the thing the page is about, and a run of 3 under a record of 90
            // read at the same weight is a page that opens by saying you used
            // to be better.
            Text(
                text = if (state.longest > 0) {
                    pluralStringResource(Res.plurals.streak_longest, state.longest, state.longest)
                } else {
                    stringResource(Res.string.streak_longest_none)
                },
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )

            if (!state.playedToday && state.current > 0) {
                // The one thing a status page can say that the calendar cannot:
                // how long is left. Only when there is a run to lose and it has
                // not been kept yet, because on any other day it is a countdown
                // to nothing.
                VerticalSpacerD500()
                Text(
                    text = stringResource(Res.string.streak_keep_it_today, state.hoursLeftLabel),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            VerticalSpacerD800()

            Text(
                text = stringResource(Res.string.streak_calendar_title),
                typography = AppTheme.typography.Heading.H600,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            VerticalSpacerD300()

            StreakCalendar(
                days = state.days.map { it.toCell() },
                weekdayLabels = WeekdayInitials.map { stringResource(it) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Only on a genuinely empty page. Explaining where the squares come
            // from to somebody who has already filled some in is noise.
            if (state.longest == 0) {
                VerticalSpacerD500()
                Text(
                    text = stringResource(Res.string.streak_empty_body),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            VerticalSpacerD800()
        }
    }
}

@Preview
@Composable
private fun StreakScreenPreview() {
    PreviewContent {
        StreakScreen(state = previewState(), onAction = {})
    }
}

/**
 * The ceremony. Under `LocalInspectionMode` the number is already at its new
 * value and today's day is already on the strip, which is both the point of
 * that rule and what makes this worth looking at.
 */
@Preview
@Composable
private fun StreakScreenCelebratingPreview() {
    PreviewContent {
        StreakScreen(state = previewState().copy(celebrating = 6), onAction = {})
    }
}

/**
 * The run that broke. The 12 it was, greyed, with the gap sitting in the week
 * underneath and today already ticked.
 */
@Preview
@Composable
private fun StreakScreenLostPreview() {
    PreviewContent {
        StreakScreen(
            state = previewState().copy(current = 1, lost = 12),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun StreakScreenEmptyPreview() {
    PreviewContent {
        StreakScreen(
            state = previewState().copy(
                current = 0,
                longest = 0,
                days = previewState().days.map { it.copy(state = StreakDayState.Missed) },
            ),
            onAction = {},
        )
    }
}

@Suppress("MagicNumber")
private fun previewState(): StreakState {
    val start = LocalDate(2026, 8, 10)
    val today = LocalDate(2026, 9, 9)
    return StreakState(
        loading = false,
        current = 6,
        longest = 21,
        today = today,
        days = List(35) { index ->
            val date = LocalDate.fromEpochDays(start.toEpochDays() + index)
            StreakDay(
                date = date,
                state = when {
                    date > today -> StreakDayState.Future
                    index >= 25 -> StreakDayState.Completed
                    index == 24 -> StreakDayState.Bridged
                    index >= 12 -> StreakDayState.Completed
                    else -> StreakDayState.Missed
                },
                isToday = date == today,
            )
        },
    )
}
