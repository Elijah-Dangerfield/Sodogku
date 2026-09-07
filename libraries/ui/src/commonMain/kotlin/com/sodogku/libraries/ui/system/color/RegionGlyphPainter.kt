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
 * The player's "no dog here" mark: a cross.
 *
 * Deliberately not drawn from [RegionGlyph]. The first pass reused `Plus` for
 * this, which is also region 6's identity glyph — so in colourblind mode a
 * region-6 cell would have shown the same shape for "this is region 6" and "you
 * ruled this out". A cross is excluded from the region set precisely so it can
 * mean exactly one thing.
 */
fun DrawScope.drawBoardMark(
    color: Color,
    fraction: Float = MARK_FRACTION,
    progress: Float = 1f,
) {
    val extent = minOf(size.width, size.height) * fraction
    val centre = Offset(size.width / 2f, size.height / 2f)
    val half = extent / 2f
    val strokeWidth = extent * MARK_STROKE_FRACTION

    // Two strokes drawn in sequence rather than both at once. A cross that
    // appears whole reads as a state change; one that is *drawn* reads as the
    // player making a note, which is what the gesture actually is.
    val first = (progress / STROKE_SPLIT).coerceIn(0f, 1f)
    val second = ((progress - STROKE_SPLIT) / (1f - STROKE_SPLIT)).coerceIn(0f, 1f)

    if (first > 0f) {
        drawLine(
            color,
            Offset(centre.x - half, centre.y - half),
            Offset(centre.x - half + extent * first, centre.y - half + extent * first),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
    if (second > 0f) {
        drawLine(
            color,
            Offset(centre.x - half, centre.y + half),
            Offset(centre.x - half + extent * second, centre.y + half - extent * second),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
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
private const val MARK_FRACTION = 0.5f
private const val MARK_STROKE_FRACTION = 0.22f

/** Fraction of the draw spent on the first stroke before the second starts. */
private const val STROKE_SPLIT = 0.55f
