package com.sodogku.features.streak.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.core.doNothing
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.streak.StreakHero
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.features.streak.impl.generated.resources.Res
import sodogku.features.streak.impl.generated.resources.streak_day_label
import sodogku.features.streak.impl.generated.resources.streak_intention_body
import sodogku.features.streak.impl.generated.resources.streak_intention_cta
import sodogku.features.streak.impl.generated.resources.streak_intention_title

/**
 * The one moment in the app that will not let the player past.
 *
 * No top bar, no close, and the back press is swallowed at the entry point.
 * What makes that defensible rather than hostile is how little it asks and how
 * little it takes: one tap, on one button, on a screen showing a run the player
 * has already earned.
 *
 * **One button now.** There used to be a paw to fill and then two choices, "play
 * today's puzzle" and "maybe later", which made the moment a fork in a road the
 * player had not asked to be standing on. There is nothing here to decline: the
 * streak exists whether or not they tap, and the tap is an acknowledgement, not
 * a contract. Duolingo's version is the same shape and it is the right one.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StreakIntentionScreen(
    state: StreakIntentionState,
    onAction: (StreakIntentionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture and the button both. Without this the system back pops the
    // route and the moment is skippable on Android by the most habitual gesture
    // there is, which is not much of a "non-skippable".
    BackHandler { doNothing() }

    Screen(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .screenContentPadding(padding)
                .padding(horizontal = Dimension.D800),
        ) {
            // The question comes first, above the number, because the number is
            // the answer to it. Reversed, the page states a fact and then asks
            // something that sounds like doubt.
            Text(
                text = stringResource(Res.string.streak_intention_title),
                typography = AppTheme.typography.Heading.H600,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD500()

            StreakHero(
                streak = state.streak,
                week = state.week.toWeekStrip(),
                dayLabel = pluralStringResource(Res.plurals.streak_day_label, state.streak, state.streak),
                // Slammed in, because the player has just earned it. This is the
                // one screen where `countUpFrom` is the number below the run
                // rather than the previous day's: there is no previous day.
                countUpFrom = state.streak - 1,
            )

            VerticalSpacerD800()

            Text(
                text = stringResource(Res.string.streak_intention_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD800()

            ButtonPrimary(
                onClick = { onAction(StreakIntentionAction.Commit) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.streak_intention_cta))
            }
        }
    }
}

@Preview
@Composable
private fun StreakIntentionPreview() {
    PreviewContent {
        StreakIntentionScreen(
            state = StreakIntentionState(
                loaded = true,
                streak = 1,
                week = previewWeek(),
            ),
            onAction = {},
        )
    }
}

private fun previewWeek(): List<StreakDay> {
    val today = LocalDate(2026, 9, 9)
    return (0..6).map { index ->
        val date = LocalDate.fromEpochDays(today.toEpochDays() - (2 - index))
        StreakDay(
            date = date,
            state = when {
                date > today -> StreakDayState.Future
                date == today -> StreakDayState.Completed
                else -> StreakDayState.Missed
            },
            isToday = date == today,
        )
    }
}
