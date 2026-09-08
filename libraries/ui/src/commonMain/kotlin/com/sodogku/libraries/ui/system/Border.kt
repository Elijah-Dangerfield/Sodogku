package com.sodogku.libraries.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.system.AppTheme
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.Radii
import com.sodogku.system.Radius

@Immutable
data class Border(val color: ColorResource, val width: Dp = StandardBorderWidth) {
    companion object {
        val Standard: Border
            @ReadOnlyComposable
            @Composable
            get() = Border(AppTheme.colors.border)
    }
}

val StandardBorderWidth = 1.dp

/**
 * A border, in the shape the thing being bordered actually is.
 *
 * [radius] defaults to square, which is what this used to do unconditionally and
 * is why it needs saying: a rounded card that also clips its content leaves a
 * square border's four corners *outside* the clip, so they are cut off and the
 * card ends up with four blue lines and four bare corners. It is a two-pixel
 * bug that only exists at the corners and only on a rounded surface, which is
 * exactly the kind that survives review.
 *
 * Pass the same radius the surface is clipped to, and put this **before** the
 * clip in the chain — `Modifier.border` draws its stroke on top of the content,
 * so a clip that comes after it shaves the stroke's outer edge instead.
 */
fun Modifier.border(border: Border, radius: Radius = Radii.None): Modifier = this.border(
    width = border.width,
    color = border.color.color,
    shape = radius.shape
)

@Composable
fun Modifier.dashedBorder(
    strokeWidth: Dp = 1.dp,
    dashSize: Float = 10f,
    gapSize: Float = dashSize,
    color: ColorResource = AppTheme.colors.border,
    radius: Radius = Radii.None,
) = drawWithCache {
    val strokeWidthPx = strokeWidth.toPx()
    val cornerRadiusPx = radius.cornerSize.toPx(size, this)

    onDrawWithContent {
        drawContent()

        drawRoundRect(
            color = color.color,
            style = Stroke(
                width = strokeWidthPx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashSize, gapSize), 0f)
            ),
            cornerRadius = CornerRadius(cornerRadiusPx)
        )
    }
}