package com.sodogku.libraries.ui.components.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Twelve pale bars fanning out from behind a hero, turning slowly.
 *
 * The rays behind the dog on a cleared board. Not `drawStarburst`, which is the
 * one-cell flash behind a landed dog and a different shape: that one blooms and
 * fades in under half a second, this one is scenery that stays up as long as
 * the page does. It scales in on [Motion.Pop] and then turns at a few degrees
 * a second, which is slow enough to be felt rather than watched.
 *
 * Both movements are read inside [graphicsLayer] and never in composition; a
 * spin that ran through recomposition would redraw the number, the stat chips
 * and the button sixty times a second for as long as the page was open.
 *
 * Still under [LocalReduceAnimations] and [LocalInspectionMode]: no spin at all,
 * and drawn at full size from the first frame rather than growing into it. A
 * screenshot of a burst caught at scale zero is a screenshot of nothing.
 *
 * The bars are drawn to the 300-unit box the handoff draws them in and scaled
 * with [size], so a smaller burst is the same picture rather than thinner bars
 * further apart.
 */
@Composable
fun RayBurst(
    modifier: Modifier = Modifier,
    color: ColorResource = AppTheme.colors.accentBrandSoft,
    size: Dp = DefaultExtent,
) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    val ray = color.color

    val arrived = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) {
        if (still) {
            arrived.snapTo(1f)
            return@LaunchedEffect
        }
        arrived.snapTo(0f)
        arrived.animateTo(1f, Motion.Pop)
    }

    // No infinite transition at all when still, rather than one held at zero:
    // a paused infinite animation is still a pending frame to a screenshot
    // test waiting for idle.
    val angle: State<Float>? = if (still) {
        null
    } else {
        rememberInfiniteTransition(label = "RayBurst").animateFloat(
            initialValue = 0f,
            targetValue = FullTurnDegrees,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = TurnMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "RayBurstTurn",
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = angle?.value ?: 0f
                val scale = arrived.value
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val unit = this.size.minDimension / FrameExtent
                val width = RayWidth * unit
                val length = RayLength * unit
                val corner = CornerRadius(RayCorner * unit)
                val topLeft = Offset(this.size.width / 2f - width / 2f, RayInset * unit)
                repeat(Rays) { index ->
                    rotate(degrees = index * FullTurnDegrees / Rays) {
                        drawRoundRect(color = ray, topLeft = topLeft, size = Size(width, length), cornerRadius = corner)
                    }
                }
            },
    )
}

/** The box the handoff draws the burst in, and the unit everything below is in. */
private const val FrameExtent = 300f
private const val Rays = 12
private const val RayWidth = 8f
private const val RayLength = 44f
private const val RayCorner = 4f
private const val RayInset = 8f

private const val FullTurnDegrees = 360f

/** One full turn. Four degrees a second: present, and not something the eye tracks. */
private const val TurnMillis = 90_000

private val DefaultExtent = Dimension.D1900 * 3

@Preview
@Composable
private fun RayBurstPreview() {
    PreviewContent {
        Box(contentAlignment = Alignment.Center) {
            RayBurst()
            Dog(pose = DogPose.Solved, size = PreviewDogSize)
        }
    }
}

private val PreviewDogSize = Dimension.D1900 + Dimension.D1000
