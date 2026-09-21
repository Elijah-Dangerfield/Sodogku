package com.sodogku.libraries.ui.components.button

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.Dimension
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.pro_button
import sodogku.libraries.resources.generated.resources.pro_button_pitch
import sodogku.libraries.resources.generated.resources.pro_button_priced

/**
 * The one-tap way into Sodogku Pro.
 *
 * A button rather than a link to the Pro sheet, because the sheet is a step in
 * the way for a player who has already decided. The tap starts the store's own
 * purchase flow; the price on the label is the store's, and the label has no
 * number in it until the store has answered (`features.md#pro`: never a typed
 * price).
 *
 * Brand amber with dark ink, the app's own colour, so it reads as the app
 * offering something rather than as the blue of a primary action or the purple
 * that means an ad. [enabled] is false while a purchase is in flight, which is
 * how a second tap is dropped rather than queued.
 */
@Composable
fun ProButton(
    priceLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Small,
    style: ButtonStyle = ButtonStyle.Filled,
    enabled: Boolean = true,
) {
    ButtonPrimary(
        onClick = onClick,
        accent = ButtonAccent.Brand,
        size = size,
        style = style,
        enabled = enabled,
        modifier = modifier,
    ) {
        Text(proLabel(priceLabel))
    }
}

/**
 * The same tap as [ProButton], drawn as a line under a screen's one button:
 * the pitch, then the price. For the cleared screen, where a second filled
 * button would fight Next level for the eye.
 */
@Composable
fun ProLink(
    priceLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ButtonGhost(
        onClick = onClick,
        size = ButtonSize.Small,
        enabled = enabled,
        modifier = modifier,
    ) {
        Text("${stringResource(Res.string.pro_button_pitch)} · ${proLabel(priceLabel)}")
    }
}

@Composable
private fun proLabel(priceLabel: String?): String =
    if (priceLabel == null) {
        stringResource(Res.string.pro_button)
    } else {
        stringResource(Res.string.pro_button_priced, priceLabel)
    }

@Preview
@Composable
private fun ProButtonPreview() {
    PreviewContent {
        Column {
            ProButton(priceLabel = "$4.99", onClick = {})
            ProButton(priceLabel = null, onClick = {}, modifier = Modifier.fillMaxWidth(), size = ButtonSize.Medium)
            ProButton(priceLabel = "$4.99", onClick = {}, style = ButtonStyle.Outlined, enabled = false)
            ProLink(priceLabel = "$4.99", onClick = {})
        }
    }
}
