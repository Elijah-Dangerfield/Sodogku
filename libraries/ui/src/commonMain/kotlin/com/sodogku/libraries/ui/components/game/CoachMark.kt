package com.sodogku.libraries.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.system.AnchoredCard
import com.sodogku.libraries.ui.system.animatePlacement
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.button.ButtonGhost
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.elevation
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The card a guided lesson speaks through, hung off whatever a spotlight is
 * lighting.
 *
 * Meant to be the `content` of a `FocusScrim`, which hands over the union of the
 * lit rectangles as [anchor]. It fills the scrim and places itself, so the
 * caller never does layout maths.
 *
 * Placement is [AnchoredCard]'s, including the two rules that were learned here
 * and now apply to every anchored surface: flip above the anchor when there is
 * no room below, and never rise into the status bar. They used to live in this
 * file, alongside a second copy in `SpeechBubble` that was missing the flip.
 *
 * The travel between lessons is [animatePlacement], which is the whole reason
 * these two are separate components. A card that teleports has to be re-found by
 * the eye at every step, and the tutorial is the one place the player is being
 * asked to follow something.
 */
@Composable
fun CoachMark(
    anchor: Rect,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    skipLabel: String? = null,
    onSkip: () -> Unit = {},
) {
    AnchoredCard(anchor = anchor, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D300),
            modifier = Modifier
                .animatePlacement()
                .padding(horizontal = Dimension.D800)
                .widthIn(max = CardMaxWidth)
                .elevation(Elevation.Card, Radii.Card.shape)
                .clip(Radii.Card)
                .background(AppTheme.colors.surfacePrimary.color)
                .padding(Dimension.D700),
        ) {
            Text(
                text = title,
                typography = AppTheme.typography.Heading.H600,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                typography = AppTheme.typography.Body.B400,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (confirmLabel != null) {
                ButtonPrimary(
                    onClick = onConfirm,
                    size = ButtonSize.Small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(confirmLabel)
                }
            }
            if (skipLabel != null) {
                ButtonGhost(onClick = onSkip, size = ButtonSize.Small) {
                    Text(skipLabel)
                }
            }
        }
    }
}

private val CardMaxWidth: Dp = Dimension.D1900 * 3

@Preview
@Composable
private fun CoachMarkBelowPreview() {
    PreviewContent {
        CoachMark(
            anchor = Rect(120f, 300f, 400f, 380f),
            title = "Two taps places a dog",
            body = "Tap the lit square twice, quickly. A dog goes here.",
            confirmLabel = "Got it",
            skipLabel = "Skip tutorial",
        )
    }
}

@Preview
@Composable
private fun CoachMarkFlippedPreview() {
    PreviewContent {
        CoachMark(
            anchor = Rect(60f, 1800f, 500f, 1900f),
            title = "Treat",
            body = "A treat places one dog for you, correctly, with no bone at risk.",
            confirmLabel = "Got it",
            skipLabel = "Skip tutorial",
        )
    }
}
