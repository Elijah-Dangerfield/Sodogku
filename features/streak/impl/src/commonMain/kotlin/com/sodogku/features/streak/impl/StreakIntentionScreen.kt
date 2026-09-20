package com.sodogku.features.streak.impl

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.sodogku.libraries.core.doNothing
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.text.Text
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.streak_day_label
import sodogku.libraries.resources.generated.resources.streak_intention_body
import sodogku.libraries.resources.generated.resources.streak_intention_cta
import sodogku.libraries.resources.generated.resources.streak_intention_kicker

/**
 * The one moment in the app that will not let the player past.
 *
 * No top bar, no close, and the back press is swallowed at the entry point.
 * What makes that defensible rather than hostile is how little it asks and how
 * little it takes: one tap, on one button, on a screen showing a run the player
 * has already earned.
 *
 * The same band layout as the other two ceremonies, in amber, with `DAY ONE`
 * in the band. **One button.** There used to be a paw to fill and then two
 * choices, which made the moment a fork in a road the player had not asked to
 * be standing on. There is nothing here to decline: the streak exists whether
 * or not they tap, and the tap is an acknowledgement, not a contract.
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

    StreakCeremonyLayout(
        moment = StreakMoment.Intention,
        kicker = stringResource(Res.string.streak_intention_kicker),
        number = state.streak,
        // Slammed in from the number below, because the player has just earned
        // it. This is the one screen where counting from zero is honest: there
        // is no previous day, and nothing was lost.
        countUpFrom = state.streak - 1,
        dayLabel = pluralStringResource(Res.plurals.streak_day_label, state.streak, state.streak),
        body = stringResource(Res.string.streak_intention_body),
        week = state.week.toWeekStrip(state.weekStrip),
        justLanded = state.justLanded,
        modifier = modifier,
    ) {
        ButtonPrimary(
            onClick = { onAction(StreakIntentionAction.Commit) },
            size = ButtonSize.Hero,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.streak_intention_cta))
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
