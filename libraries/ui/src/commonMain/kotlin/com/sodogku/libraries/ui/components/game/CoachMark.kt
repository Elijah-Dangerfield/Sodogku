package com.sodogku.libraries.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.Elevation
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
 * Two placement rules, both learned the hard way:
 *
 * - **It flips above the anchor when there is no room below.** Hanging under a
 *   target near the bottom of the screen — the booster row, say — pushes the
 *   card off the display, and a lesson nobody can read is worse than no lesson.
 * - **It never rises above [MinTop].** A spotlight with nothing to hang off
 *   reports an anchor at the origin, and an unclamped card lands under the
 *   status bar.
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val anchorTop = with(density) { anchor.top.toDp() }
        val anchorBottom = with(density) { anchor.bottom.toDp() }
        // Measured against a reserve rather than the card's real height: the
        // height is not known until after layout, and a card that jumps on its
        // second frame reads as a glitch.
        val below = anchorBottom + Gap + CardReserve <= maxHeight

        val placement = if (below) {
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxOf(anchorBottom + Gap, MinTop))
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxOf(maxHeight - anchorTop + Gap, MinBottom))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D300),
            modifier = placement
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

/** Breathing room between the lit thing and the card describing it. */
private val Gap: Dp = Dimension.D600

/** Clears the status bar and the header when there is no anchor to hang from. */
private val MinTop: Dp = Dimension.D1900

/** Keeps a flipped card clear of the gesture bar. */
private val MinBottom: Dp = Dimension.D1200

/**
 * What the card is assumed to need, for the "is there room below" test. Two
 * lines of body, a button and a skip link fit inside it comfortably.
 */
private val CardReserve: Dp = Dimension.D1900 * 2

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
