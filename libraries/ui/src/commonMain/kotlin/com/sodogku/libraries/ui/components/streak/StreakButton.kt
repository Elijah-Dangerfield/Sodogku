package com.sodogku.libraries.ui.components.streak

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.Badge
import com.sodogku.libraries.ui.components.BadgedBox
import com.sodogku.libraries.ui.components.Surface
import com.sodogku.libraries.ui.components.game.drawPaw
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The streak, as a circular button with a count on it.
 *
 * **The mark is a paw print, and it is a placeholder for a flame.** The paw is
 * the app's own vocabulary and it is already drawn ([drawPaw] in the game
 * shapes), so the button can ship without waiting on artwork. Swapping it is one
 * line in [StreakMark] once a flame exists.
 *
 * Filled when the run is alive, outlined when it is not. That is the same "keeps its
 * shape, loses its colour" rule the achievement grid uses for a locked badge, so
 * a dead streak is still legible as a streak rather than as a missing control.
 *
 * The badge is hidden at zero. A count of nought is the one number that says
 * nothing anybody wants to be told.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    BadgedBox(
        contentRadius = Radii.Round,
        badge = {
            if (streak > 0) {
                Badge {
                    // Not labelled: the count is already in `stateLabel` on the
                    // button, and a badge that announces itself makes a reader
                    // say the number twice.
                    Text(text = streak.toString())
                }
            }
        },
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = label
            stateDescription = stateLabel
            role = Role.Button
        },
    ) {
        Surface(
            color = AppTheme.colors.surfacePrimary,
            contentColor = AppTheme.colors.onSurfacePrimary,
            radius = Radii.Round,
            elevation = Elevation.Button,
            onClick = onClick,
            contentPadding = PaddingValues(Dimension.D400),
        ) {
            StreakMark(
                alive = streak > 0,
                modifier = Modifier.size(Dimension.D900),
            )
        }
    }
}

/**
 * The streak's glyph.
 *
 * One composable so there is exactly one place to change when the flame arrives.
 */
@Composable
internal fun StreakMark(alive: Boolean, modifier: Modifier = Modifier) {
    val lit = AppTheme.colors.accentPrimary.color
    val dim = AppTheme.colors.textSecondary.color
    Canvas(modifier = modifier) {
        drawPaw(color = if (alive) lit else dim, filled = alive)
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
