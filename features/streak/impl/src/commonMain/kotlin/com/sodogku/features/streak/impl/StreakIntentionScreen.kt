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
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSecondary
import com.sodogku.libraries.ui.components.streak.StreakIntentionMark
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.features.streak.impl.generated.resources.Res
import sodogku.features.streak.impl.generated.resources.streak_intention_body
import sodogku.features.streak.impl.generated.resources.streak_intention_cta
import sodogku.features.streak.impl.generated.resources.streak_intention_filled
import sodogku.features.streak.impl.generated.resources.streak_intention_later
import sodogku.features.streak.impl.generated.resources.streak_intention_mark
import sodogku.features.streak.impl.generated.resources.streak_intention_title
import sodogku.features.streak.impl.generated.resources.streak_intention_waiting

/**
 * The one moment in the app that will not let the player past.
 *
 * No top bar, no close, and the back press is swallowed at the entry point.
 * What makes that defensible rather than hostile is how little it asks: one tap,
 * on a big target, on a screen with one idea on it. The two buttons appear only
 * *after* the paw is filled, so before the tap there is nothing to read past and
 * nothing to weigh up.
 *
 * The second button matters. "Non-skippable" is about the moment, not about the
 * puzzle. A player interrupted three levels into their first session may well
 * not want to start a fourth board right now, and marching them into one is how
 * a nice moment becomes the reason they close the app.
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
            Text(
                text = stringResource(Res.string.streak_intention_title),
                typography = AppTheme.typography.Display.D900,
                textAlign = TextAlign.Center,
            )
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.streak_intention_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD800()

            StreakIntentionMark(
                label = stringResource(Res.string.streak_intention_mark),
                stateLabel = if (state.started) {
                    stringResource(Res.string.streak_intention_filled)
                } else {
                    stringResource(Res.string.streak_intention_waiting)
                },
                onFilled = { onAction(StreakIntentionAction.Filled) },
            )

            VerticalSpacerD800()

            // Held out of the tree until the paw is full rather than shown
            // disabled. A greyed button under an untapped paw invites a tap on
            // the wrong thing, and the whole moment is one gesture long.
            if (state.started) {
                ButtonPrimary(
                    onClick = { onAction(StreakIntentionAction.Play) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.streak_intention_cta))
                }
                VerticalSpacerD500()
                ButtonSecondary(
                    onClick = { onAction(StreakIntentionAction.Later) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.streak_intention_later))
                }
            }
        }
    }
}

@Preview
@Composable
private fun StreakIntentionPreview() {
    PreviewContent {
        StreakIntentionScreen(state = StreakIntentionState(), onAction = {})
    }
}

@Preview
@Composable
private fun StreakIntentionStartedPreview() {
    PreviewContent {
        StreakIntentionScreen(state = StreakIntentionState(started = true), onAction = {})
    }
}
