package com.sodogku.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.bounceClick
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.elevation
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.sin

/**
 * One control on the row under the board: a round white face, a picture, a word
 * beneath it, and a count.
 *
 * The row used to be coloured pills with the word inside them, each booster
 * keeping its own hue so a player learned "the blue one" before they read it.
 * The trouble with that is the row then competes with the board for colour, and
 * the board is where every colour already means something: a region. Moving the
 * controls to white circles on the cream leaves the pastels to the puzzle, and
 * puts the identifying colour in a *picture* instead of a fill.
 *
 * Which is the better carrier anyway. A bone is a bone at a glance and in any
 * language, and it is the same bone the life counter uses, so the two are
 * visibly the same currency.
 *
 * The label sits **outside** the circle rather than inside it. Inside, the word
 * has to shrink to fit a shape it does not fill, and the longest word decides
 * the size of every button.
 */
@Composable
fun BoardControl(
    label: String,
    modifier: Modifier = Modifier,
    /** Shown as a badge on the circle. Null draws no badge at all. */
    count: Int? = null,
    enabled: Boolean = true,
    /**
     * Whether to draw attention to this control.
     *
     * A slow ring around the circle and a small wobble on whatever is inside it.
     * Deliberately not a scale on the whole button: a control that grows and
     * shrinks under a thumb that is reaching for it is a control that gets
     * mis-tapped, and the row sits exactly where the hand already is.
     */
    attention: Boolean = false,
    onClick: () -> Unit = {},
    art: @Composable () -> Unit,
) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(attention, still) {
        if (!attention || still) {
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            pulse.animateTo(1f, tween(AttentionMillis, easing = LinearEasing))
            pulse.snapTo(0f)
            delay(AttentionRestMillis)
        }
    }

    val ring = AppTheme.colors.accentPrimary.color
    val face = if (enabled) AppTheme.colors.surfacePrimary.color else AppTheme.colors.surfaceDisabled.color

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D200),
        modifier = modifier.semantics {
            contentDescription = if (count == null) label else "$label, $count left"
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(FaceSize)
                    // Read in the draw phase, never in composition: a ring that
                    // recomposed this subtree sixty times a second would be a
                    // steady cost for a decoration.
                    .drawBehind {
                        val progress = pulse.value
                        if (progress <= 0f) return@drawBehind
                        // One out-and-fade, not a throb. A ring that pulsed
                        // continuously would compete with the board for the eye
                        // for as long as the booster went unused.
                        val grow = 1f + progress * RingGrowth
                        val alpha = (1f - progress) * RingAlpha
                        drawCircle(
                            color = ring.copy(alpha = alpha),
                            radius = size.minDimension / 2f * grow,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = RingWidth.toPx()),
                        )
                    }
                    .elevation(Elevation.Button, Radii.Round.shape)
                    .clip(Radii.Round)
                    .background(face)
                    .bounceClick(onClick = onClick, enabled = enabled),
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        // The picture wobbles rather than the button, so the
                        // touch target never moves.
                        rotationZ = sin(pulse.value * WobbleCycles) * WobbleDegrees * (1f - pulse.value)
                    },
                ) {
                    art()
                }
            }

            if (count != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(Radii.Round)
                        .background(
                            if (count > 0) {
                                AppTheme.colors.danger.color
                            } else {
                                AppTheme.colors.textDisabled.color
                            },
                        )
                        .padding(horizontal = Dimension.D300, vertical = Dimension.D50),
                ) {
                    Text(
                        text = count.toString(),
                        typography = AppTheme.typography.Caption.C300,
                        color = AppTheme.colors.onAccentPrimary,
                    )
                }
            }
        }

        Text(
            text = label,
            typography = AppTheme.typography.Caption.C300,
            color = if (enabled) AppTheme.colors.textSecondary else AppTheme.colors.onSurfaceDisabled,
        )
    }
}

/** A bone, sized for the middle of a [BoardControl]. */
@Composable
fun BoardControlBone(fill: Color, edge: Color, size: Dp = ArtSize) {
    Box(
        modifier = Modifier
            .size(width = size * BoneAspect, height = size)
            .drawBehind {
                // Tilted, because a bone lying flat reads as a minus sign at
                // this size.
                rotate(BoneTilt) { drawBone(fill = fill, edge = edge) }
            },
    )
}

/**
 * The circle.
 *
 * Smaller than it started, and the picture inside it larger, so the ratio moved
 * twice. A 100dp circle around a 28dp bone is mostly white: the thing that
 * identifies the control was the smallest part of it, and the row took more
 * vertical space than a row of three buttons needs. This is still comfortably
 * above the 48dp minimum touch target.
 */
private val FaceSize = Dimension.D1600

/** Roughly half the circle across, so the bone reads rather than sits in it. */
private val ArtSize = Dimension.D1200

/** Matches the aspect `drawBone` is drawn against; a bone in a square is a blob. */
private const val BoneAspect = 1.45f

private const val BoneTilt = -20f

private const val AttentionMillis = 1_100
private const val AttentionRestMillis = 2_400L

/** How far past the circle the ring travels before it fades out. */
private const val RingGrowth = 0.35f
private const val RingAlpha = 0.55f
private val RingWidth = Dimension.D50

private const val WobbleCycles = 9f
private const val WobbleDegrees = 7f

@Preview
@Composable
private fun BoardControlPreview() {
    PreviewContent {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D600)) {
            BoardControl(label = "Locate", count = 3) {
                BoardControlBone(fill = Color(0xFFFFF3E0), edge = Color(0xFFD98324))
            }
            BoardControl(label = "Treat", count = 0, enabled = false) {
                BoardControlBone(fill = Color(0xFFDEDAD6), edge = Color(0xFFB4AEA8))
            }
        }
    }
}
