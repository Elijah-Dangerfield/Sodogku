package com.sodogku.libraries.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.system.color.ColorResource

@Immutable
class Elevation internal constructor(val dp: Dp) : Comparable<Elevation> {

    override fun compareTo(other: Elevation): Int = dp.compareTo(other.dp)

    companion object {
        val None = Elevation(0.dp)
        val Button = Elevation(3.dp)
        val Card = Elevation(4.dp)

        val Header = Elevation(5.dp)
        val BottomBar = Elevation(10.dp)
        val Modal = Elevation(100.dp)

        /**
         * The unlock toast's shadow, and the only blur in the app.
         *
         * Everything else that lifts off the page does it without one: buttons
         * sit on a hard slab in their own dark tone, cards draw an inset stroke.
         * The toast is the one surface that genuinely floats over live content,
         * and a hard edge under it would read as a second card rather than as a
         * thing in the air. So it is the one place the 2026-09 handoff spends a
         * real shadow, `0 10px 26px` at 0.28, and it gets a token of its own
         * rather than a shape in the [Elevation] ladder because a platform
         * elevation cannot express the offset or the colour.
         */
        val Toast = BlurShadow(offsetY = 10.dp, blur = 26.dp, alpha = 0.28f)
    }
}

/**
 * A soft shadow dropped straight down, in a colour the caller picks. See
 * [Elevation.Toast] for why there is exactly one.
 */
@Immutable
class BlurShadow internal constructor(val offsetY: Dp, val blur: Dp, val alpha: Float)

/**
 * Draws [shadow] under this element's [shape], tinted with [color] at the
 * shadow's own alpha. A `dropShadow` rather than [elevation], which routes
 * through a graphics layer and a light source and comes out black.
 */
fun Modifier.blurShadow(shadow: BlurShadow, shape: Shape, color: ColorResource): Modifier =
    dropShadow(shape) {
        radius = shadow.blur.toPx()
        offset = Offset(0f, shadow.offsetY.toPx())
        this.color = color.color
        alpha = shadow.alpha
    }

fun Modifier.elevation(
    elevation: Elevation,
    shape: Shape = RectangleShape,
    clip: Boolean = elevation.dp > 0.dp,
    color: ColorResource = ColorResource.Black
): Modifier {
    return this.shadow(
        elevation = elevation.dp,
        shape = shape,
        clip = clip,
        ambientColor = color.color,
        spotColor = color.color
    )
}
