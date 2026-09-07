package com.sodogku.features.onboarding.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
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
    Screen(
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimension.D800),
            ) {
                Spacer(modifier = Modifier.height(Dimension.D1200))
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
