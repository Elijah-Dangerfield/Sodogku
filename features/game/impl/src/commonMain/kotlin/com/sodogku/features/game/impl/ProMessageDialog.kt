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
import sodogku.libraries.resources.generated.resources.paywall_already_pro
import sodogku.libraries.resources.generated.resources.paywall_purchase_failed
import sodogku.libraries.resources.generated.resources.paywall_store_unavailable
import sodogku.libraries.resources.generated.resources.pro_message_title

/**
 * What a Go Pro button has to say when the store did not sell. The same three
 * sentences the Pro sheet uses, on a dialog because the button has no sheet of
 * its own to put them on. A cancelled purchase never reaches here.
 */
@Composable
fun ProMessageDialog(message: ProPurchaseMessage, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Dog(pose = if (message == ProPurchaseMessage.AlreadyPro) DogPose.Solved else DogPose.Thinking)
            Text(
                text = stringResource(Res.string.pro_message_title),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    when (message) {
                        ProPurchaseMessage.AlreadyPro -> Res.string.paywall_already_pro
                        ProPurchaseMessage.Failed -> Res.string.paywall_purchase_failed
                        ProPurchaseMessage.StoreUnavailable -> Res.string.paywall_store_unavailable
                    },
                ),
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

@Preview
@Composable
private fun ProMessageDialogPreview() {
    PreviewContent {
        ProMessageDialog(message = ProPurchaseMessage.Failed, onDismiss = {})
    }
}
