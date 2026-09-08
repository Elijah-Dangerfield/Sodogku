package com.sodogku.features.paywall.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD1600
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.offline_block_body
import sodogku.libraries.resources.generated.resources.offline_block_go_pro
import sodogku.libraries.resources.generated.resources.offline_block_retry
import sodogku.libraries.resources.generated.resources.offline_block_still_offline
import sodogku.libraries.resources.generated.resources.offline_block_title

/**
 * The one screen in Sodogku that stops a player, SPEC 6.
 *
 * Back is swallowed, the same way `AccessDeniedScreen` swallows it: there is
 * nothing behind this but the board it is protecting, and a block you can
 * dismiss with the system gesture is not one. What replaces the escape hatch is
 * that the screen dismisses *itself* the moment the OS reports a network again
 * — the player never has to work out what to press.
 *
 * The copy blames the ads, not the player, and it says what Pro buys rather
 * than what being free costs. This is the highest-intent paywall moment in the
 * app and it is also the most annoying one; the tone is the difference between
 * a sale and an uninstall.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OfflineBlockScreen(
    state: OfflineBlockState,
    onAction: (OfflineBlockAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { doNothing() }

    Screen(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimension.D1000),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Dog(pose = DogPose.Thinking)

            VerticalSpacerD500()

            Text(
                text = stringResource(Res.string.offline_block_title),
                typography = AppTheme.typography.Display.D1000,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD500()

            Text(
                text = stringResource(Res.string.offline_block_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            if (state.retriedWhileOffline) {
                VerticalSpacerD500()
                Text(
                    text = stringResource(Res.string.offline_block_still_offline),
                    typography = AppTheme.typography.Body.B400,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.weight(2f))

            ButtonPrimary(
                onClick = { onAction(OfflineBlockAction.GoPro) },
                modifier = Modifier.fillMaxWidth(),
                deep = true,
            ) {
                Text(text = stringResource(Res.string.offline_block_go_pro))
            }

            VerticalSpacerD500()

            ButtonSecondary(
                onClick = { onAction(OfflineBlockAction.Retry) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.offline_block_retry))
            }

            VerticalSpacerD1600()
        }
    }
}

@Preview
@Composable
private fun OfflineBlockScreenPreview() {
    PreviewContent {
        OfflineBlockScreen(state = OfflineBlockState(), onAction = {})
    }
}

@Preview
@Composable
private fun OfflineBlockScreenPreview_StillOffline() {
    PreviewContent {
        OfflineBlockScreen(state = OfflineBlockState(retriedWhileOffline = true), onAction = {})
    }
}
