package com.sodogku.libraries.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.ui.system.glossy
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The colour a Treat wears, wherever it appears.
 *
 * Lives here rather than at the board's call site because the reward chip and
 * the Treat button are now two places saying the same thing, and a player who
 * has learned "the orange one" in the booster row has to meet the same orange
 * in the level pane or the chip is decoration rather than a promise.
 */
val TreatColor: Color = ColorResource.Orange600.color

/**
 * "Clearing this level pays a Treat", as a chip on a level row.
 *
 * The pane is 500 rows and the rewards are the reason to scroll it, so this has
 * to read as a prize from across the row rather than as a decoration on it —
 * which is what the bare glyph it replaces did. It gets the same candy
 * treatment as the booster buttons ([com.sodogku.libraries.ui.system.glossy]):
 * a lit top, a shaded base, and the Treat's own orange.
 *
 * [claimed] is the state that stops the chip lying. The reward is paid once, on
 * the first clear, so a level already cleared has nothing left to give — it
 * keeps the chip so the column stays aligned down the list, but flat, muted and
 * unmistakably spent.
 *
 * The label is spelled out rather than left as a `+1` next to a glyph. The
 * three consumables share one shape and two of them are already drawn as small
 * gold objects, so a picture of a reward is a picture of "one of the three
 * things"; the word is the only version that says *which*.
 */
@Composable
fun LevelRewardChip(
    label: String,
    modifier: Modifier = Modifier,
    claimed: Boolean = false,
    color: Color = TreatColor,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D200),
        modifier = modifier
            .clip(Radii.Round)
            .then(
                if (claimed) {
                    Modifier.background(AppTheme.colors.surfaceDisabled.color)
                } else {
                    Modifier.glossy(color)
                },
            )
            .padding(
                start = Dimension.D400,
                end = Dimension.D400,
                top = Dimension.D200,
                // Extra below, so the label sits on the lit face rather than on
                // the shaded band `glossy` draws along the bottom.
                bottom = Dimension.D300,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(width = Dimension.D700 * BoneAspect, height = Dimension.D700)
                .drawBehind {
                    if (claimed) {
                        drawBone(fill = SpentTreat, edge = SpentTreatEdge)
                    } else {
                        drawBone(fill = TreatBiscuit, edge = TreatBiscuitEdge)
                    }
                },
        )
        Text(
            text = label,
            typography = AppTheme.typography.Caption.C300,
            color = if (claimed) AppTheme.colors.onSurfaceDisabled else AppTheme.colors.onAccentPrimary,
        )
    }
}

/** Matches the aspect `drawBone` is designed against; a bone in a square is a blob. */
private const val BoneAspect = 1.45f

/**
 * Read against the orange behind it, not against the cream page — so this is
 * the pale one and the life row's bone is the gold one, even though both are
 * the same shape.
 */
private val TreatBiscuit = Color(0xFFFFF3E0)
private val TreatBiscuitEdge = Color(0xFFD98324)
private val SpentTreat = Color(0xFFDEDAD6)
private val SpentTreatEdge = Color(0xFFB4AEA8)

@Preview
@Composable
private fun LevelRewardChipPreview() {
    PreviewContent {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
            LevelRewardChip(label = "+1 Treat")
            LevelRewardChip(label = "+1 Treat", claimed = true)
        }
    }
}
