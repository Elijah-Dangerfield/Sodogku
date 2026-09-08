package com.sodogku.features.game.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.dialog.Dialog
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.common_close
import sodogku.libraries.resources.generated.resources.daily_freeze_applied_body
import sodogku.libraries.resources.generated.resources.daily_freeze_applied_title
import sodogku.libraries.resources.generated.resources.daily_freeze_declined_body
import sodogku.libraries.resources.generated.resources.daily_freeze_declined_title
import sodogku.libraries.resources.generated.resources.daily_freeze_none_left_body
import sodogku.libraries.resources.generated.resources.daily_freeze_none_left_title
import sodogku.libraries.resources.generated.resources.daily_freeze_nothing_body
import sodogku.libraries.resources.generated.resources.daily_freeze_nothing_title
import sodogku.libraries.resources.generated.resources.daily_freeze_unavailable_body
import sodogku.libraries.resources.generated.resources.daily_freeze_unavailable_title
import sodogku.libraries.resources.generated.resources.daily_restore_applied_body
import sodogku.libraries.resources.generated.resources.daily_restore_applied_title
import sodogku.libraries.resources.generated.resources.daily_restore_none_left_body
import sodogku.libraries.resources.generated.resources.daily_restore_none_left_title
import sodogku.libraries.resources.generated.resources.daily_restore_out_of_reach_body
import sodogku.libraries.resources.generated.resources.daily_restore_out_of_reach_title

/**
 * What the streak freeze did, whatever it did.
 *
 * All five answers get a sentence. The ad-declined branch is the reason this
 * exists at all: the player has just watched most of an ad and closed it, and a
 * screen that goes back to normal with nothing said reads as the app having
 * broken rather than as the freeze having been withheld.
 */
@Composable
fun FreezeMessageDialog(message: FreezeMessage, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Dog(
                pose = when (message) {
                    is FreezeMessage.Applied, is FreezeMessage.Restored -> DogPose.Solved
                    else -> DogPose.Thinking
                },
            )
            Text(
                text = stringResource(message.title()),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message.body(),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ButtonPrimary(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.common_close))
            }
        }
    }
}

private fun FreezeMessage.title() = when (this) {
    is FreezeMessage.Applied -> Res.string.daily_freeze_applied_title
    FreezeMessage.Declined -> Res.string.daily_freeze_declined_title
    FreezeMessage.NoneLeft -> Res.string.daily_freeze_none_left_title
    FreezeMessage.NothingToFreeze -> Res.string.daily_freeze_nothing_title
    FreezeMessage.Unavailable -> Res.string.daily_freeze_unavailable_title
    is FreezeMessage.Restored -> Res.string.daily_restore_applied_title
    FreezeMessage.RestoreNoneLeft -> Res.string.daily_restore_none_left_title
    FreezeMessage.RestoreOutOfReach -> Res.string.daily_restore_out_of_reach_title
}

@Composable
private fun FreezeMessage.body(): String = when (this) {
    is FreezeMessage.Applied -> stringResource(Res.string.daily_freeze_applied_body, streak)
    FreezeMessage.Declined -> stringResource(Res.string.daily_freeze_declined_body)
    FreezeMessage.NoneLeft -> stringResource(Res.string.daily_freeze_none_left_body)
    FreezeMessage.NothingToFreeze -> stringResource(Res.string.daily_freeze_nothing_body)
    FreezeMessage.Unavailable -> stringResource(Res.string.daily_freeze_unavailable_body)
    is FreezeMessage.Restored -> stringResource(Res.string.daily_restore_applied_body, days, streak)
    FreezeMessage.RestoreNoneLeft -> stringResource(Res.string.daily_restore_none_left_body)
    FreezeMessage.RestoreOutOfReach -> stringResource(Res.string.daily_restore_out_of_reach_body)
}

@Preview
@Composable
private fun FreezeAppliedPreview() {
    PreviewContent {
        FreezeMessageDialog(message = FreezeMessage.Applied(streak = 8), onDismiss = {})
    }
}

@Preview
@Composable
private fun FreezeDeclinedPreview() {
    PreviewContent {
        FreezeMessageDialog(message = FreezeMessage.Declined, onDismiss = {})
    }
}

@Preview
@Composable
private fun StreakRestoredPreview() {
    PreviewContent {
        FreezeMessageDialog(message = FreezeMessage.Restored(days = 3, streak = 41), onDismiss = {})
    }
}
