package com.sodogku.features.onboarding.impl

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.app_name
import sodogku.libraries.resources.generated.resources.onboarding_skip_tutorial
import sodogku.libraries.resources.generated.resources.onboarding_start_tutorial
import sodogku.libraries.resources.generated.resources.onboarding_tagline

/**
 * First-launch welcome. Two ways out, both into the game: learn the rules on
 * the first three levels, or skip straight in. All routing lives in
 * [OnboardingViewModel] — this composable is a pure render of the state.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    WelcomeContent(
        state = state,
        onAction = onAction,
        dogAlpha = 1f,
        restAlpha = 1f,
    )
}

/**
 * This screen with nothing drawn but its dog, for the iOS splash to fade out of.
 *
 * The splash exists so that launch reads as one screen rather than two: its dog
 * stands exactly where the welcome screen draws its own, so when the splash
 * fades the only thing that happens is the title, the tagline and the buttons
 * arriving *around* a dog that never moved. A shared offset constant used to
 * carry that promise, and it only held while both columns kept the same shape.
 * Laying out the real column and hiding all but the dog makes the two agree by
 * construction instead, including after somebody edits this screen.
 *
 * What is hidden here is drawn but [inert] — a zero-alpha button is still a
 * button until it is told otherwise.
 */
@Composable
fun OnboardingDogHandoff(dogAlpha: Float) {
    WelcomeContent(
        state = OnboardingState(),
        onAction = {},
        dogAlpha = dogAlpha,
        restAlpha = 0f,
        modifier = Modifier.inert(),
    )
}

/**
 * The column is centred rather than hung off the top: the dog plus its copy plus
 * two buttons is well short of a phone's height, and anchoring the stack under
 * the status bar left a growing pool of dead space at the bottom as screens got
 * taller — which is what made the dog read as sitting too high. Centring the
 * stack, not the dog: a dog at true centre pushes the buttons off the bottom.
 */
@Composable
private fun WelcomeContent(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
    dogAlpha: Float,
    restAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Screen(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimension.D800),
        ) {
            Dog(
                pose = DogPose.Solved,
                modifier = Modifier.alpha(dogAlpha),
            )

            // Everything the splash does not show, behind one alpha, so that
            // anything added here later is hidden there too without being told.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(restAlpha),
            ) {
                Spacer(modifier = Modifier.height(Dimension.D600))
                Text(
                    text = stringResource(Res.string.app_name),
                    typography = AppTheme.typography.Heading.H800,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Dimension.D400))
                Text(
                    text = stringResource(Res.string.onboarding_tagline),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(Dimension.D1200))

                ButtonPrimary(
                    onClick = { onAction(OnboardingAction.Start) },
                    enabled = !state.isFinishing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.onboarding_start_tutorial))
                }

                Spacer(modifier = Modifier.height(Dimension.D500))

                ButtonGhost(
                    onClick = { onAction(OnboardingAction.SkipTutorial) },
                    enabled = !state.isFinishing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.onboarding_skip_tutorial))
                }
            }
        }
    }
}

/**
 * Drawn, but reachable by nothing: not a finger, not focus, not a screen reader.
 *
 * Alpha is a drawing property and every input system ignores it, so the splash's
 * invisible copy of this screen would otherwise take taps that land on the real
 * buttons underneath, sit in the keyboard focus order, and give VoiceOver a
 * second "Start tutorial" to read out. Three systems, three modifiers:
 *
 * - taps are consumed on the initial pass, which runs parent to child, so no
 *   descendant gesture detector ever sees an unconsumed down;
 * - the subtree is a focus group that cancels any attempt to enter it, since
 *   `canFocus = false` on its own only skips the group node, not its children;
 * - semantics are hidden for the subtree, which is what maps to VoiceOver's
 *   `accessibilityElementsHidden`.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.inert(): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
    .focusProperties { onEnter = { cancelFocusChange() } }
    .focusGroup()
    .semantics { hideFromAccessibility() }

@Preview
@Composable
private fun OnboardingScreenPreview() {
    PreviewContent {
        OnboardingScreen(
            state = OnboardingState(),
            onAction = {},
        )
    }
}
