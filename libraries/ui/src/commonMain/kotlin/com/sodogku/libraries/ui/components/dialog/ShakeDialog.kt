package com.sodogku.libraries.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.Button
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.button.ButtonStyle
import com.sodogku.libraries.ui.components.button.ButtonType
import com.sodogku.libraries.ui.components.text.Text
import org.jetbrains.compose.ui.tooling.preview.Preview

// Debug-only CTA label — dev-facing, so a constant rather than a string
// resource (the button never renders in release builds).
private const val NetworkInspectorCta = "Network inspector"

@Composable
fun ShakeDialog(
    headline: String,
    subtext: String?,
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
                text = headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (subtext != null) {
                    Spacer(modifier = Modifier.height(Dimension.D300))
                    Text(
                        text = subtext,
                        typography = AppTheme.typography.Body.B600,
                        color = AppTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VerticalSpacerD500()
                }
            }
        },
        bottomContent = {
            Column{
                Button(
                    onClick = {
                        state.dismiss()
                        onReportBug()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    type = ButtonType.Danger,
                ) {
                    Text("Report a bug")
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
                    style = ButtonStyle.Text
                ) {
                    Text("Dismiss")
                }
            }
        }
    )
}

@Preview
@Composable
private fun ShakeDialogPreview_WithSubtext() {
    PreviewContent {
        ShakeDialog(
            headline = "I felt that.",
            subtext = "Testing the waters?",
            onDismiss = {},
            onReportBug = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_NoSubtext() {
    PreviewContent {
        ShakeDialog(
            headline = "Whoa.",
            subtext = null,
            onDismiss = {},
            onReportBug = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_WithInspector() {
    PreviewContent {
        ShakeDialog(
            headline = "I felt that.",
            subtext = "Testing the waters?",
            onDismiss = {},
            onReportBug = {},
            onOpenNetworkInspector = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_LongMessage() {
    PreviewContent {
        ShakeDialog(
            headline = "You really like shaking me.",
            subtext = "I've lost count.",
            onDismiss = {},
            onReportBug = {},
        )
    }
}
