@file:Suppress("MagicNumber")

package com.sodogku.system

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class Radius private constructor(val shape: RoundedCornerShape) {
    internal constructor(cornerSize: CornerSize) : this(RoundedCornerShape(cornerSize))

    val cornerSize: CornerSize
        get() = shape.topStart.takeUnless { it == SquareCornerSize }
            ?: shape.topEnd.takeUnless { it == SquareCornerSize }
            ?: shape.bottomEnd.takeUnless { it == SquareCornerSize }
            ?: shape.bottomStart

    override fun equals(other: Any?): Boolean = this === other || other is Radius && shape == other.shape
    override fun hashCode(): Int = shape.hashCode()
    override fun toString(): String = "Radius(cornerSize=$cornerSize)"
}

fun Radius.cornerRadius(density: Density, size: Size): Float {
    return when (val corner = cornerSize) {
        is CornerSize -> {
            // CornerSize can be absolute (Dp) or percentage
            // You need density and size to resolve it
            corner.toPx(size, density)
        }
    }
}

object Radii {
    val Round = Radius(CornerSize(percent = 50))
    val R300 = Radius(CornerSize(DimensionResource.D300.dp))
    val R400 = Radius(CornerSize(DimensionResource.D400.dp))
    val R600 = Radius(CornerSize(DimensionResource.D600.dp))
    val R900 = Radius(CornerSize(DimensionResource.D900.dp))
    val None = Radius(SquareCornerSize)

    val Default get() = None
    val Button get() = Radius(CornerSize(percent = 25))
    val IconButton get() = Round
    val Banner get() = R400
    val Header get() = None
    val Card get() = R400

    /**
     * One board square, as a proportion rather than a fixed corner.
     *
     * A 4x4 gives a cell three times the size of a 10x10's, and 8dp of corner on
     * both means the easy boards look like a spreadsheet and the hard ones look
     * like sweets. Twenty percent holds the same shape at every board size,
     * which is the only reading of "the board looks like this" that survives
     * five different grid sizes.
     *
     * A `val`, not a `get()` like its neighbours. A hundred cells ask for this
     * on every recomposition of the board, and a getter that builds a new
     * `Radius` each time hands `Modifier.clip` a shape that is never equal to
     * the last one — a hundred modifier nodes rebuilt per frame for a constant.
     */
    val Cell = Radius(CornerSize(percent = 20))

    /**
     * The card the grid sits on. Deliberately larger than [Card]: it has to read
     * as an object the board is *resting on* rather than as another panel, and a
     * 10dp corner on something 350dp wide barely registers.
     */
    val Board get() = R900
}


fun Modifier.clip(radius: Radius): Modifier = clip(radius.shape)

private val SquareCornerSize = CornerSize(0.dp)


