package com.sodogku.libraries.ui.system.color

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Draws a [RegionGlyph] centred in the current draw scope.
 *
 * Drawn rather than shipped as icons: ten more drawables for something rendered
 * a hundred times on one screen is a lot of bitmap for shapes this simple, and
 * a path scales to any cell size without a density bucket.
 */
fun DrawScope.drawRegionGlyph(
    glyph: RegionGlyph,
    color: Color,
    fraction: Float = DEFAULT_FRACTION,
) {
    val extent = minOf(size.width, size.height) * fraction
    val centre = Offset(size.width / 2f, size.height / 2f)
    val half = extent / 2f
    val stroke = Stroke(width = extent * STROKE_FRACTION)

    when (glyph) {
        RegionGlyph.Circle -> drawCircle(color, radius = half, center = centre)

        RegionGlyph.Ring -> drawCircle(color, radius = half, center = centre, style = stroke)

        RegionGlyph.Square -> drawRect(
            color,
            topLeft = Offset(centre.x - half, centre.y - half),
            size = Size(extent, extent),
        )

        RegionGlyph.Diamond -> drawPath(
            polygon(centre, listOf(0f to -half, half to 0f, 0f to half, -half to 0f)),
            color,
        )

        RegionGlyph.TriangleUp -> drawPath(
            polygon(centre, listOf(0f to -half, half to half, -half to half)),
            color,
        )

        RegionGlyph.TriangleDown -> drawPath(
            polygon(centre, listOf(0f to half, half to -half, -half to -half)),
            color,
        )

        RegionGlyph.Plus -> {
            val arm = extent * PLUS_ARM_FRACTION
            drawRect(color, Offset(centre.x - half, centre.y - arm / 2f), Size(extent, arm))
            drawRect(color, Offset(centre.x - arm / 2f, centre.y - half), Size(arm, extent))
        }

        RegionGlyph.Bar -> {
            val thickness = extent * BAR_THICKNESS_FRACTION
            drawRect(
                color,
                Offset(centre.x - half, centre.y - thickness / 2f),
                Size(extent, thickness),
            )
        }

        RegionGlyph.DoubleBar -> {
            val thickness = extent * BAR_THICKNESS_FRACTION
            val gap = extent * DOUBLE_BAR_GAP_FRACTION
            drawRect(color, Offset(centre.x - half, centre.y - gap - thickness), Size(extent, thickness))
            drawRect(color, Offset(centre.x - half, centre.y + gap), Size(extent, thickness))
        }

        RegionGlyph.Chevron -> {
            val path = Path().apply {
                moveTo(centre.x - half, centre.y - half * CHEVRON_RISE)
                lineTo(centre.x, centre.y + half * CHEVRON_RISE)
                lineTo(centre.x + half, centre.y - half * CHEVRON_RISE)
            }
            drawPath(path, color, style = stroke)
        }
    }
}

/**
 * One arm of the player's cross, as far as it has been drawn.
 *
 * A value rather than two `drawLine` calls buried in a draw scope, so the thing
 * the geometry actually promises — where the arms start, how far each has got
 * at a given progress, how thick they are — can be asserted without a canvas.
 */
data class MarkStroke(val start: Offset, val end: Offset)

/**
 * The geometry of the player's "no dog here" mark.
 *
 * Big, blunt and round-ended: [Fraction] of the square across, with arms
 * [StrokeFraction] of that thick and round caps on both ends. A thin cross in
 * the region's own ink reads as a spreadsheet tick; this reads as a friendly
 * "nope", which is what the gesture means.
 *
 * Deliberately not drawn from [RegionGlyph]. The first pass reused `Plus` for
 * this, which is also region 6's identity glyph — so in colourblind mode a
 * region-6 cell would have shown the same shape for "this is region 6" and "you
 * ruled this out". A cross is excluded from the region set precisely so it can
 * mean exactly one thing.
 */
object BoardMark {

    /** How much of the square the cross spans. */
    const val Fraction: Float = 0.60f

    /** Arm thickness, as a fraction of the cross's own extent. */
    const val StrokeFraction: Float = 0.20f

    /** Fraction of the draw spent on the first arm before the second starts. */
    const val StrokeSplit: Float = 0.55f

    fun extent(size: Size, fraction: Float = Fraction): Float =
        minOf(size.width, size.height) * fraction

    fun strokeWidth(size: Size, fraction: Float = Fraction): Float =
        extent(size, fraction) * StrokeFraction

    /**
     * The arms visible at [progress], in draw order.
     *
     * Two strokes in sequence rather than both at once. A cross that appears
     * whole reads as a state change; one that is *drawn* reads as the player
     * making a note, which is what the gesture actually is.
     */
    fun strokes(
        size: Size,
        progress: Float,
        fraction: Float = Fraction,
    ): List<MarkStroke> {
        val extent = extent(size, fraction)
        val half = extent / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        val first = (progress / StrokeSplit).coerceIn(0f, 1f)
        val second = ((progress - StrokeSplit) / (1f - StrokeSplit)).coerceIn(0f, 1f)

        return buildList {
            if (first > 0f) {
                add(
                    MarkStroke(
                        start = Offset(cx - half, cy - half),
                        end = Offset(cx - half + extent * first, cy - half + extent * first),
                    ),
                )
            }
            if (second > 0f) {
                add(
                    MarkStroke(
                        start = Offset(cx - half, cy + half),
                        end = Offset(cx - half + extent * second, cy + half - extent * second),
                    ),
                )
            }
        }
    }
}

/** Draws [BoardMark] centred in the current draw scope. */
fun DrawScope.drawBoardMark(
    color: Color,
    fraction: Float = BoardMark.Fraction,
    progress: Float = 1f,
) {
    val width = BoardMark.strokeWidth(size, fraction)
    BoardMark.strokes(size, progress, fraction).forEach { stroke ->
        drawLine(color, stroke.start, stroke.end, strokeWidth = width, cap = StrokeCap.Round)
    }
}

private fun polygon(centre: Offset, points: List<Pair<Float, Float>>): Path = Path().apply {
    points.forEachIndexed { index, (dx, dy) ->
        val x = centre.x + dx
        val y = centre.y + dy
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The bounds a glyph occupies, for callers that need to lay out around it. */
fun glyphBounds(size: Size, fraction: Float = DEFAULT_FRACTION): Rect {
    val extent = minOf(size.width, size.height) * fraction
    val centre = Offset(size.width / 2f, size.height / 2f)
    return Rect(centre, extent / 2f)
}

private const val DEFAULT_FRACTION = 0.46f
private const val STROKE_FRACTION = 0.18f
private const val PLUS_ARM_FRACTION = 0.34f
private const val BAR_THICKNESS_FRACTION = 0.26f
private const val DOUBLE_BAR_GAP_FRACTION = 0.08f
private const val CHEVRON_RISE = 0.7f
