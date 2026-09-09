package com.sodogku.libraries.ui.components.streak

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Surface
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.flame

/**
 * The streak, as a flame with its count beside it.
 *
 * Lit when the run is alive, a flat grey silhouette when it is not. That is the
 * same "keeps its shape, loses its colour" rule the achievement grid uses for a
 * locked badge, so a dead streak is still legible as a streak rather than as a
 * missing control.
 *
 * The count is not a corner badge. It is set into the flame: the digits are
 * drawn twice, once as a fat stroke in the pill's own colour and once filled on
 * top, so the outline carves a clean gap wherever the number overlaps the art.
 * A pill-shaped badge floating off the corner reads as a notification — an
 * unread count, something to clear — and the streak is the opposite of that.
 *
 * The count is absent at zero, which is also what turns the pill back into a
 * circle. A count of nought is the one number that says nothing anybody wants
 * to be told.
 */
@Composable
fun StreakButton(
    streak: Int,
    /** What the control *is*. Never changes. */
    label: String,
    /**
     * What the count currently says, as a sentence a reader would want back:
     * "7 day streak", not "7".
     *
     * Split from [label] because a screen reader treats the two differently:
     * TalkBack and VoiceOver re-announce a state description on their own when
     * it changes under the reading cursor, and fold the two into one string and
     * a player whose streak just went up hears nothing. Same rule as
     * `BoardCellLabels`.
     */
    stateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AppTheme.colors.surfacePrimary,
        contentColor = AppTheme.colors.onSurfacePrimary,
        radius = Radii.Round,
        elevation = Elevation.Button,
        onClick = onClick,
        contentPadding = PaddingValues(Dimension.D400),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = label
            stateDescription = stateLabel
            role = Role.Button
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Negative, so the digits sit *on* the flame rather than next to it.
            // The knockout outline is what keeps them apart, and it needs
            // something to be knocked out of. Small: the outline already bleeds
            // half its width past the letterforms, and the two overlaps add up
            // — at six the number ate the flame down to a crescent.
            horizontalArrangement = Arrangement.spacedBy(-Dimension.D50),
        ) {
            StreakMark(alive = streak > 0, modifier = Modifier.height(Dimension.D1000))
            if (streak > 0) {
                StreakCount(streak)
            }
        }
    }
}

/**
 * The streak's glyph.
 *
 * One composable so there is exactly one place to change it, and sized by height
 * rather than by a square: the art is taller than it is wide, and `size` on a
 * square would letterbox it and leave the button lopsided.
 */
@Composable
internal fun StreakMark(alive: Boolean, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.flame),
        contentDescription = null,
        colorFilter = if (alive) null else ColorFilter.tint(AppTheme.colors.textDisabled.color),
        modifier = modifier,
    )
}

/**
 * The count, cut into whatever is behind it.
 *
 * Two passes of the same string: the stroke is drawn first in the surface
 * colour, then the fill on top. Compose strokes text centred on the glyph
 * outline, so half the width falls outside the letterform and that half is the
 * gap — which means the stroke has to be roughly twice the gap you want.
 *
 * Silent to a reader. The number is already in the button's state description,
 * and a count that announces itself makes a reader say it twice.
 */
@Composable
private fun StreakCount(streak: Int, modifier: Modifier = Modifier) {
    val style = AppTheme.typography.Heading.H700.style
    val knockout = with(LocalDensity.current) { Dimension.D300.toPx() }

    Box(modifier = modifier.clearAndSetSemantics { }) {
        BasicText(
            text = streak.toString(),
            style = style.copy(
                color = AppTheme.colors.surfacePrimary.color,
                drawStyle = Stroke(width = knockout, join = StrokeJoin.Round),
            ),
        )
        BasicText(
            text = streak.toString(),
            style = style.copy(color = AppTheme.colors.text.color),
        )
    }
}

@Preview
@Composable
private fun StreakButtonPreview() {
    PreviewContent {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D800)) {
            StreakButton(streak = 0, label = "Streak", stateLabel = "No streak", onClick = {})
            StreakButton(streak = 1, label = "Streak", stateLabel = "1 day streak", onClick = {})
            StreakButton(streak = 12, label = "Streak", stateLabel = "12 day streak", onClick = {})
            StreakButton(streak = 365, label = "Streak", stateLabel = "365 day streak", onClick = {})
        }
    }
}
