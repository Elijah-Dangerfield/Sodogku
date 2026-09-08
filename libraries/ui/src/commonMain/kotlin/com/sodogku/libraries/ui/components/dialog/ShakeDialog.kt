package com.sodogku.libraries.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.Button
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.button.ButtonStyle
import com.sodogku.libraries.ui.components.button.ButtonType
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.shake_body
import sodogku.libraries.resources.generated.resources.shake_dismiss
import sodogku.libraries.resources.generated.resources.shake_report
import sodogku.libraries.resources.generated.resources.shake_title

// Debug-only CTA label — dev-facing, so a constant rather than a string
// resource (the button never renders in release builds).
private const val NetworkInspectorCta = "Network inspector"

/**
 * "You shook your phone, did you mean to report something?"
 *
 * The copy is fixed. It used to be picked at random from a few hundred
 * generated lines that tried to sound sentient, escalating with how many times
 * you had shaken and how hard, addressing you by name, commenting on the hour.
 * A dialog that opens by accident and says "Is this love? Probably not." is a
 * bug report the player does not file.
 *
 * The body says *why* the dialog appeared, which is the one thing it has to do:
 * a shake is easy to trigger by accident, and a bare "Report a problem" from a
 * phone in a pocket reads as something being wrong with the app.
 */
@Composable
fun ShakeDialog(
    onDismiss: () -> Unit,
    onReportBug: () -> Unit,
    modifier: Modifier = Modifier,
    state: DialogState = rememberDialogState(),
    // Debug-only: when non-null, an extra action opens the on-device network
    // inspector. Release callers leave this null so the button never shows.
    onOpenNetworkInspector: (() -> Unit)? = null,
) {
    BasicDialog(
        state = state,
        onDismissRequest = onDismiss,
        modifier = modifier,
        topContent = {
            Text(
                text = stringResource(Res.string.shake_title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        content = {
            Text(
                text = stringResource(Res.string.shake_body),
                typography = AppTheme.typography.Body.B600,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        bottomContent = {
            Column {
                Button(
                    onClick = {
                        state.dismiss()
                        onReportBug()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    type = ButtonType.Danger,
                ) {
                    Text(stringResource(Res.string.shake_report))
                }

                if (onOpenNetworkInspector != null) {
                    Spacer(modifier = Modifier.height(Dimension.D500))
                    Button(
                        onClick = {
                            state.dismiss()
                            onOpenNetworkInspector()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        size = ButtonSize.Medium,
                        type = ButtonType.Secondary,
                    ) {
                        Text(NetworkInspectorCta)
                    }
                }

                Spacer(modifier = Modifier.height(Dimension.D500))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    style = ButtonStyle.Text,
                ) {
                    Text(stringResource(Res.string.shake_dismiss))
                }
            }
        },
    )
}

@Preview
@Composable
private fun ShakeDialogPreview() {
    PreviewContent {
        ShakeDialog(onDismiss = {}, onReportBug = {})
    }
}

@Preview
@Composable
private fun ShakeDialogPreviewWithInspector() {
    PreviewContent {
        ShakeDialog(onDismiss = {}, onReportBug = {}, onOpenNetworkInspector = {})
    }
}
