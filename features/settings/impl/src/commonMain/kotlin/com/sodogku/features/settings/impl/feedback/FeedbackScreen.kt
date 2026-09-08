package com.sodogku.features.settings.impl.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.text.OutlinedTextField
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import com.sodogku.system.VerticalSpacerD1200
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.feedback_done
import sodogku.libraries.resources.generated.resources.feedback_empty_error
import sodogku.libraries.resources.generated.resources.feedback_label
import sodogku.libraries.resources.generated.resources.feedback_placeholder
import sodogku.libraries.resources.generated.resources.feedback_prompt
import sodogku.libraries.resources.generated.resources.feedback_send
import sodogku.libraries.resources.generated.resources.feedback_sending
import sodogku.libraries.resources.generated.resources.feedback_sent_body
import sodogku.libraries.resources.generated.resources.feedback_sent_title
import sodogku.libraries.resources.generated.resources.settings_feedback

/**
 * One text field and a send button.
 *
 * The confirmation is a panel on this screen rather than a snackbar over the
 * previous one: a player who has just typed a paragraph deserves to see it was
 * received before the screen goes away, and a toast that fires during the
 * transition is the one people miss.
 */
@Composable
fun FeedbackScreen(
    state: FeedbackState,
    onAction: (FeedbackAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.settings_feedback),
                onNavigateBack = { onAction(FeedbackAction.Back) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(paddingValues = padding, includeImePadding = true),
        ) {
            if (state.sent) {
                SentPanel(onDone = { onAction(FeedbackAction.Back) })
            } else {
                FeedbackForm(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun FeedbackForm(
    state: FeedbackState,
    onAction: (FeedbackAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val canSubmit = state.message.isNotBlank() && !state.isSubmitting

    val send = {
        focusManager.clearFocus(force = true)
        onAction(FeedbackAction.Submit)
    }

    VerticalSpacerD800()

    Text(
        text = stringResource(Res.string.feedback_prompt),
        typography = AppTheme.typography.Body.B700,
        color = AppTheme.colors.textSecondary,
    )

    VerticalSpacerD500()

    OutlinedTextField(
        value = state.message,
        onValueChange = { onAction(FeedbackAction.MessageChanged(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimension.D1900),
        label = { Text(stringResource(Res.string.feedback_label)) },
        placeholder = { Text(stringResource(Res.string.feedback_placeholder)) },
        singleLine = false,
        minLines = 6,
        maxLines = 10,
        isError = state.showEmptyError,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (canSubmit) send() }),
    )

    VerticalSpacerD500()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val length = state.message.length
        Text(
            text = "$length/$FeedbackCharLimit",
            typography = AppTheme.typography.Body.B500,
            color = if (length >= FeedbackCharLimit) {
                AppTheme.colors.danger
            } else {
                AppTheme.colors.textSecondary
            },
        )
    }

    if (state.showEmptyError) {
        VerticalSpacerD500()
        Text(
            text = stringResource(Res.string.feedback_empty_error),
            color = AppTheme.colors.danger,
            textAlign = TextAlign.Start,
        )
    }

    VerticalSpacerD800()

    ButtonPrimary(
        modifier = Modifier.fillMaxWidth(),
        enabled = canSubmit,
        onClick = send,
    ) {
        Text(
            if (state.isSubmitting) {
                stringResource(Res.string.feedback_sending)
            } else {
                stringResource(Res.string.feedback_send)
            },
        )
    }

    VerticalSpacerD500()
}

@Composable
private fun SentPanel(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VerticalSpacerD1200()

        Dog(pose = DogPose.Solved)

        VerticalSpacerD800()

        Text(
            text = stringResource(Res.string.feedback_sent_title),
            typography = AppTheme.typography.Heading.H800,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        VerticalSpacerD500()

        Text(
            text = stringResource(Res.string.feedback_sent_body),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        VerticalSpacerD1200()

        ButtonPrimary(modifier = Modifier.fillMaxWidth(), onClick = onDone) {
            Text(stringResource(Res.string.feedback_done))
        }
    }
}

@Preview
@Composable
private fun FeedbackScreenEmptyPreview() {
    PreviewContent {
        FeedbackScreen(state = FeedbackState(), onAction = {})
    }
}

@Preview
@Composable
private fun FeedbackScreenTypedPreview() {
    PreviewContent {
        FeedbackScreen(
            state = FeedbackState(message = "The 9x9 levels are the best ones."),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun FeedbackScreenSentPreview() {
    PreviewContent {
        FeedbackScreen(state = FeedbackState(sent = true), onAction = {})
    }
}
