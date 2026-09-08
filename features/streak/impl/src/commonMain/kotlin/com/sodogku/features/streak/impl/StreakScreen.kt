package com.sodogku.features.streak.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.streak.StreakCalendar
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.features.streak.impl.generated.resources.Res
import sodogku.features.streak.impl.generated.resources.streak_calendar_title
import sodogku.features.streak.impl.generated.resources.streak_celebrate_body
import sodogku.features.streak.impl.generated.resources.streak_celebrate_title
import sodogku.features.streak.impl.generated.resources.streak_current_days
import sodogku.features.streak.impl.generated.resources.streak_current_none
import sodogku.features.streak.impl.generated.resources.streak_empty_body
import sodogku.features.streak.impl.generated.resources.streak_longest
import sodogku.features.streak.impl.generated.resources.streak_longest_none
import sodogku.features.streak.impl.generated.resources.streak_off_body
import sodogku.features.streak.impl.generated.resources.streak_off_title
import sodogku.features.streak.impl.generated.resources.streak_title

/**
 * The run, the record, and the last five weeks.
 *
 * **Nothing on this page moves when it is opened by tap.** There is no entrance
 * animation to suppress, because there is none to begin with: the only animated
 * thing is the single cell named by [StreakState.fillingIndex], and that is null
 * unless the page was pushed to celebrate a milestone. That is the difference
 * the owner asked for, expressed as an absence rather than as a flag somebody
 * has to remember to check.
 */
@Composable
fun StreakScreen(
    state: StreakState,
    onAction: (StreakAction) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        when {
            state.loading -> FullScreenLoader()
            !state.enabled -> DailyOff(
                modifier = Modifier.fillMaxSize().screenContentPadding(padding),
            )
            else -> StreakBody(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .screenContentPadding(padding),
            )
        }
    }
}

@Composable
private fun StreakBody(state: StreakState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        VerticalSpacerD500()

        // The dog reacts to the run rather than to the visit. A page that opens
        // with a delighted dog on a streak of nought is the app congratulating
        // somebody for nothing.
        Dog(
            pose = if (state.current > 0) DogPose.Solved else DogPose.Thinking,
            size = Dimension.D1900,
        )

        Text(
            text = if (state.current > 0) {
                stringResource(Res.string.streak_current_days, state.current)
            } else {
                stringResource(Res.string.streak_current_none)
            },
            typography = AppTheme.typography.Display.D900,
            textAlign = TextAlign.Center,
        )
        VerticalSpacerD300()

        // The record is second and quieter. It is the thing to beat, not the
        // thing the page is about, and a run of 3 under a record of 90 read at
        // the same weight is a page that opens by saying you used to be better.
        Text(
            text = if (state.longest > 0) {
                stringResource(Res.string.streak_longest, state.longest)
            } else {
                stringResource(Res.string.streak_longest_none)
            },
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        if (state.celebrating > 0) {
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.streak_celebrate_title, state.celebrating),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.streak_celebrate_body),
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
            fillingIndex = state.fillingIndex,
            modifier = Modifier.fillMaxWidth(),
        )

        // Only on a genuinely empty page. Explaining where the squares come from
        // to somebody who has already filled some in is noise.
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

/**
 * What the page says when the daily is switched off.
 *
 * It says the history survives, for the same reason the badges screen does: the
 * reading that stops somebody coming back is the one where turning a thing off
 * threw their record away.
 */
@Composable
private fun DailyOff(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = Dimension.D800),
    ) {
        Dog(pose = DogPose.Thinking)
        VerticalSpacerD500()
        Text(
            text = stringResource(Res.string.streak_off_title),
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
        )
        VerticalSpacerD300()
        Text(
            text = stringResource(Res.string.streak_off_body),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun StreakScreenPreview() {
    PreviewContent {
        StreakScreen(state = previewState(), onAction = {})
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

@Preview
@Composable
private fun StreakScreenOffPreview() {
    PreviewContent {
        StreakScreen(state = previewState().copy(enabled = false), onAction = {})
    }
}

@Suppress("MagicNumber")
private fun previewState(): StreakState {
    val start = LocalDate(2026, 8, 10)
    val today = LocalDate(2026, 9, 7)
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
                    index >= 23 -> StreakDayState.Completed
                    index == 22 -> StreakDayState.Bridged
                    index == 18 -> StreakDayState.Failed
                    index >= 12 -> StreakDayState.Completed
                    else -> StreakDayState.Missed
                },
                isToday = date == today,
            )
        },
    )
}
