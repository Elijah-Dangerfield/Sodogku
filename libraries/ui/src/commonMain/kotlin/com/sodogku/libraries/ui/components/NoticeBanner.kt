package com.sodogku.libraries.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.IconButton
import com.sodogku.libraries.ui.components.icon.IconResource
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.Elevation
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.HorizontalSpacerD400
import com.sodogku.system.Radii
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.common_close

/**
 * A white card that floats over whatever the player was doing and says one thing.
 *
 * This is the design system's answer to "tell them, don't stop them" — the
 * dismissible half of every launch gate, and the shape a maintenance notice, a
 * soft-update suggestion or a terms change all take. It is a card on the cream
 * page rather than a coloured strip across the top: a full-bleed bar reads as
 * chrome the app always had, and this is meant to read as something that arrived.
 *
 * It carries its own status-bar inset, because the thing underneath it is a
 * `Screen` that has already consumed its own — a banner overlaid on top would
 * otherwise sit under the clock.
 *
 * **[onDismiss] is not optional by accident.** A notice with no way to close it is
 * a block wearing a banner's clothes, and the whole point of the split is that
 * one of them can be waved away. If a message genuinely must not be dismissible,
 * it belongs on a blocking screen.
 */
@Composable
fun NoticeBanner(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        // Not `Card`: its 28dp inset is sized for a page section, and a banner
        // that tall over a live board hides the thing the player is looking at.
        color = AppTheme.colors.surfacePrimary,
        contentColor = AppTheme.colors.onSurfacePrimary,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = Dimension.D500, vertical = Dimension.D400)
            .fillMaxWidth(),
        radius = Radii.Card,
        elevation = Elevation.Button,
        bounceScale = 1f,
        onClick = {},
        contentPadding = PaddingValues(start = Dimension.D700, top = Dimension.D500, bottom = Dimension.D500),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Icon(icon = icon, color = AppTheme.colors.textSecondary)
                HorizontalSpacerD400()
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    typography = AppTheme.typography.Body.B400,
                    color = AppTheme.colors.text,
                )

                if (actionLabel != null && onAction != null) {
                    ButtonGhost(
                        onClick = onAction,
                        size = ButtonSize.Small,
                        modifier = Modifier.offset(x = -Dimension.D500),
                    ) {
                        Text(text = actionLabel)
                    }
                }
            }

            IconButton(
                icon = Icons.Close(stringResource(Res.string.common_close)),
                onClick = onDismiss,
                iconColor = AppTheme.colors.textSecondary,
                size = IconButton.Size.Smallest,
            )
        }
    }
}

@Preview
@Composable
private fun NoticeBannerPreview() {
    PreviewContent {
        NoticeBanner(
            text = "There is a newer Sodogku waiting for you.",
            onDismiss = {},
            icon = Icons.Info("Notice"),
            actionLabel = "Update",
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun NoticeBannerPreview_LongMessageNoAction() {
    PreviewContent {
        NoticeBanner(
            text = "We are doing some work on the leaderboards this afternoon. " +
                "Everything else keeps working, and your progress is safe.",
            onDismiss = {},
            icon = Icons.Tools("Maintenance"),
        )
    }
}
