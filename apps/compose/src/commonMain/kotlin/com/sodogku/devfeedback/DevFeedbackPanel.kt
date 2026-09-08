package com.sodogku.devfeedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Surface
import com.sodogku.libraries.ui.components.button.Button
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.button.ButtonType
import com.sodogku.libraries.ui.components.checkbox.Checkbox
import com.sodogku.libraries.ui.components.text.OutlinedTextField
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.toImageBitmap
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD700
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The directive form, sliding in over whatever the owner was looking at.
 *
 * An overlay rather than a nav destination on purpose: it has to be reachable
 * from a dialog, a bottom sheet or mid-animation, and pushing a route would
 * both disturb the back stack it is meant to describe and change the very
 * screen the attached screenshot was taken of.
 */
@Composable
fun DevFeedbackPanel(
    state: DevFeedbackState,
    onAction: (DevFeedbackAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.isOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.backgroundOverlay.color)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onAction(DevFeedbackAction.Dismiss) },
            )
        }

        AnimatedVisibility(
            visible = state.isOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            Surface(
                color = AppTheme.colors.background,
                contentColor = AppTheme.colors.onBackground,
                radius = Radii.Default,
                modifier = Modifier
                    .fillMaxWidth(PANEL_WIDTH_FRACTION)
                    .fillMaxHeight(),
            ) {
                if (state.sent) {
                    SentConfirmation(onAction)
                } else {
                    DirectiveForm(state, onAction)
                }
            }
        }
    }
}

@Composable
private fun DirectiveForm(
    state: DevFeedbackState,
    onAction: (DevFeedbackAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimension.D700),
    ) {
        Text(text = Copy.TITLE, typography = AppTheme.typography.Heading.H700)
        VerticalSpacerD300()
        Text(
            text = Copy.SUBTITLE,
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        VerticalSpacerD700()

        OutlinedTextField(
            value = state.message,
            onValueChange = { onAction(DevFeedbackAction.MessageChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimension.D1900)
                .semantics { contentDescription = Copy.MESSAGE_FIELD_DESCRIPTION },
            placeholder = { Text(Copy.PLACEHOLDER) },
            singleLine = false,
            minLines = 5,
            maxLines = 12,
        )

        VerticalSpacerD500()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAction(DevFeedbackAction.IncludeLogsChanged(!state.includeLogs)) }
                .semantics { contentDescription = Copy.INCLUDE_LOGS_DESCRIPTION },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.includeLogs,
                onCheckedChange = { onAction(DevFeedbackAction.IncludeLogsChanged(it)) },
            )
            Text(text = Copy.INCLUDE_LOGS, typography = AppTheme.typography.Body.B500)
        }

        state.screenshot?.let { shot ->
            VerticalSpacerD500()
            ScreenshotRow(shot, onRemove = { onAction(DevFeedbackAction.RemoveScreenshot) })
        }

        VerticalSpacerD700()

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = Copy.SUBMIT_DESCRIPTION },
            size = ButtonSize.Large,
            enabled = state.canSubmit,
            onClick = { onAction(DevFeedbackAction.Submit) },
        ) {
            Text(if (state.isSubmitting) Copy.SENDING else Copy.SUBMIT)
        }

        VerticalSpacerD300()

        Button(
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            type = ButtonType.Ghost,
            onClick = { onAction(DevFeedbackAction.Dismiss) },
        ) {
            Text(Copy.CANCEL)
        }
    }
}

@Composable
private fun ScreenshotRow(screenshot: Screenshot, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val bitmap = remember(screenshot) { screenshot.bytes.toImageBitmap() }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = Copy.SCREENSHOT_DESCRIPTION,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(Dimension.D1500)
                    .height(Dimension.D1900),
            )
        } else {
            Text(text = Copy.SCREENSHOT_ATTACHED, typography = AppTheme.typography.Body.B500)
        }

        Button(
            size = ButtonSize.Small,
            type = ButtonType.Ghost,
            onClick = onRemove,
            modifier = Modifier.semantics { contentDescription = Copy.REMOVE_SCREENSHOT_DESCRIPTION },
        ) {
            Text(Copy.REMOVE_SCREENSHOT)
        }
    }
}

@Composable
private fun SentConfirmation(onAction: (DevFeedbackAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Dimension.D700),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = Copy.SENT_TITLE, typography = AppTheme.typography.Heading.H700)
        VerticalSpacerD300()
        Text(
            text = Copy.SENT_BODY,
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )
        VerticalSpacerD700()
        Button(
            size = ButtonSize.Large,
            onClick = { onAction(DevFeedbackAction.Dismiss) },
            modifier = Modifier.semantics { contentDescription = Copy.DONE_DESCRIPTION },
        ) {
            Text(Copy.DONE)
        }
    }
}

/**
 * Deliberately not in `:libraries:resources`. Nothing here is ever seen by a
 * player: the panel only exists in debug and TestFlight builds, so putting this
 * copy in `strings.xml` would ask translators to localize developer tooling and
 * would make the shipped resource table larger for no one's benefit.
 */
private object Copy {
    const val TITLE = "Leave a directive"
    const val SUBTITLE = "Goes to Sentry tagged owner_directive. Triage turns it into a TODO."
    const val PLACEHOLDER = "What should change?"
    const val INCLUDE_LOGS = "Attach recent logs"
    const val SUBMIT = "File it"
    const val SENDING = "Filing…"
    const val CANCEL = "Cancel"
    const val REMOVE_SCREENSHOT = "Remove"
    const val SCREENSHOT_ATTACHED = "Screenshot attached"
    const val SENT_TITLE = "Filed"
    const val SENT_BODY = "It will show up in the next triage pass."
    const val DONE = "Done"

    const val MESSAGE_FIELD_DESCRIPTION = "Directive message"
    const val INCLUDE_LOGS_DESCRIPTION = "Attach recent logs"
    const val SUBMIT_DESCRIPTION = "File directive"
    const val REMOVE_SCREENSHOT_DESCRIPTION = "Remove screenshot"
    const val SCREENSHOT_DESCRIPTION = "Attached screenshot"
    const val DONE_DESCRIPTION = "Done"
}

/** Leaves a strip of the underlying screen visible, so it still reads as an overlay. */
private const val PANEL_WIDTH_FRACTION = 0.92f

@Preview
@Composable
private fun DevFeedbackPanelPreview() {
    PreviewContent {
        DevFeedbackPanel(
            state = DevFeedbackState(
                isOpen = true,
                message = "The reward chip should animate in after the board settles, not with it.",
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun DevFeedbackPanelSentPreview() {
    PreviewContent {
        DevFeedbackPanel(state = DevFeedbackState(isOpen = true, sent = true), onAction = {})
    }
}
