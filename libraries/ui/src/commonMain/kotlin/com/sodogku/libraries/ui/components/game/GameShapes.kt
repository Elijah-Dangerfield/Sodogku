package com.sodogku.libraries.ui.components.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * The small game shapes, drawn rather than shipped as drawables.
 *
 * A bone, a paw and three rule diagrams are a handful of circles and rounded
 * rectangles each. Shipping them as bitmaps would mean five more assets across
 * three density buckets to say what twenty lines of geometry says, and they
 * would not scale with the type they sit beside.
 */

/**
 * A bone: a bar with two lobes at each end.
 *
 * Drawn in a box wider than it is tall. A bone in a square reads as a blob,
 * which is exactly what the first pass produced — the lobes were large enough
 * relative to the bar that they merged into one lump.
 */
fun DrawScope.drawBone(fill: Color, edge: Color) {
    val width = size.width
    val height = size.height
    val lobe = height * BONE_LOBE_FRACTION
    val barHeight = height * BONE_BAR_FRACTION
    val centreY = height / 2f
    val spread = lobe * BONE_LOBE_SPREAD

    // Outline first, fill inset over it: drawing an edge as a stroke would trace
    // the seams between the bar and the lobes and make the bone look welded.
    val rim = lobe * BONE_RIM_FRACTION
    listOf(edge to 0f, fill to rim).forEach { (color, inset) ->
        drawRoundRect(
            color = color,
            topLeft = Offset(lobe, centreY - barHeight / 2f + inset),
            size = Size(width - lobe * 2f, barHeight - inset * 2f),
            cornerRadius = CornerRadius((barHeight - inset * 2f) / 2f),
        )
        listOf(lobe, width - lobe).forEach { x ->
            drawCircle(color, radius = lobe - inset, center = Offset(x, centreY - spread))
            drawCircle(color, radius = lobe - inset, center = Offset(x, centreY + spread))
        }
    }
}

/**
 * The burst behind a dog that has just landed: a warm bloom with tapered rays
 * fanning out of it.
 *
 * [elapsed] runs 0 to 1 over the life of the animation. The burst expands and
 * fades at the same time, which is what stops it reading as a lens flare stuck
 * to the square — a thing that grows *and* dims looks like energy leaving,
 * which is the moment being drawn.
 *
 * Drawn as flat triangles rather than a blurred sprite. This is at most one
 * cell out of a hundred and only for [com.sodogku.system.Motion.PlacementPulseMillis],
 * so the cheap version is the one worth having: eight paths and two circles,
 * no bitmap, no shader compile the first time a player places a dog.
 */
fun DrawScope.drawStarburst(color: Color, elapsed: Float) {
    val fade = (1f - elapsed).coerceIn(0f, 1f)
    if (fade <= 0f) return

    val centre = Offset(size.width / 2f, size.height / 2f)
    val extent = minOf(size.width, size.height)
    val reach = extent * (BURST_START_REACH + elapsed * (BURST_END_REACH - BURST_START_REACH))

    drawCircle(
        color = color.copy(alpha = color.alpha * fade * BURST_BLOOM_ALPHA),
        radius = reach * BURST_BLOOM_SCALE,
        center = centre,
    )

    val ray = color.copy(alpha = color.alpha * fade)
    val inner = reach * BURST_RAY_INNER
    repeat(BURST_RAYS) { index ->
        val angle = index * TAU / BURST_RAYS + BURST_RAY_PHASE
        val spread = TAU / BURST_RAYS * BURST_RAY_WIDTH
        drawPath(
            Path().apply {
                moveTo(centre.x + cos(angle) * reach, centre.y + sin(angle) * reach)
                lineTo(centre.x + cos(angle - spread) * inner, centre.y + sin(angle - spread) * inner)
                lineTo(centre.x + cos(angle + spread) * inner, centre.y + sin(angle + spread) * inner)
                close()
            },
            ray,
        )
    }
}

/** A paw print: one pad and four toes. */
fun DrawScope.drawPaw(color: Color, filled: Boolean) {
    val width = size.width
    val height = size.height
    val padWidth = width * PAW_PAD_WIDTH
    val padHeight = height * PAW_PAD_HEIGHT
    val toe = width * PAW_TOE_RADIUS
    val style = if (filled) null else Stroke(width = width * PAW_OUTLINE)

    fun circle(centre: Offset, radius: Float) {
        if (style == null) drawCircle(color, radius, centre) else drawCircle(color, radius, centre, style = style)
    }

    if (style == null) {
        drawRoundRect(
            color = color,
            topLeft = Offset((width - padWidth) / 2f, height - padHeight - height * PAW_PAD_BOTTOM),
            size = Size(padWidth, padHeight),
            cornerRadius = CornerRadius(padWidth / 2f, padHeight / 2f),
        )
    } else {
        drawRoundRect(
            color = color,
            topLeft = Offset((width - padWidth) / 2f, height - padHeight - height * PAW_PAD_BOTTOM),
            size = Size(padWidth, padHeight),
            cornerRadius = CornerRadius(padWidth / 2f, padHeight / 2f),
            style = style,
        )
    }

    val toeY = height * PAW_TOE_ROW
    val outerY = height * PAW_TOE_OUTER_ROW
    circle(Offset(width * PAW_TOE_X0, outerY), toe * PAW_TOE_OUTER_SCALE)
    circle(Offset(width * PAW_TOE_X1, toeY), toe)
    circle(Offset(width * PAW_TOE_X2, toeY), toe)
    circle(Offset(width * PAW_TOE_X3, outerY), toe * PAW_TOE_OUTER_SCALE)
}

/** Which rule a [RuleChip] illustrates. */
enum class RuleDiagram {
    /** One dog per colour region. */
    OnePerRegion,

    /** One dog per row and column. */
    OnePerLine,

    /** Dogs cannot touch, including diagonally. */
    NoTouching,
}

/**
 * A 3x3 miniature showing the rule: a dark pip is the dog, and the shading is
 * what the rule rules out.
 *
 * The colour rule gets actual colours from the board palette rather than a
 * monochrome shade. The first pass drew it as a shaded *column*, which is not
 * what a region is at all — it said "one dog per column" twice and never said
 * anything about colour.
 */
fun DrawScope.drawRuleDiagram(
    diagram: RuleDiagram,
    ink: Color,
    regionColors: List<Color> = emptyList(),
) {
    val cell = minOf(size.width, size.height) / GRID
    val gap = cell * DIAGRAM_GAP
    val radius = CornerRadius(cell * DIAGRAM_RADIUS)

    fun paint(row: Int, col: Int, color: Color) {
        drawRoundRect(
            color = color,
            topLeft = Offset(col * cell + gap / 2f, row * cell + gap / 2f),
            size = Size(cell - gap, cell - gap),
            cornerRadius = radius,
        )
    }

    fun pip(row: Int, col: Int) {
        drawCircle(
            color = ink,
            radius = cell * PIP_RADIUS,
            center = Offset(col * cell + cell / 2f, row * cell + cell / 2f),
        )
    }

    val dogRow = 1
    val dogCol = 1

    for (row in 0 until GRID_CELLS) {
        for (col in 0 until GRID_CELLS) {
            val color = when (diagram) {
                RuleDiagram.OnePerRegion -> {
                    val group = REGION_GROUPS[row][col]
                    regionColors.getOrNull(group) ?: ink.copy(alpha = ink.alpha * EMPTY_ALPHA)
                }
                RuleDiagram.OnePerLine ->
                    if (row == dogRow || col == dogCol) shade(ink, RULED_OUT_ALPHA) else shade(ink, EMPTY_ALPHA)
                RuleDiagram.NoTouching ->
                    if (row != dogRow || col != dogCol) shade(ink, RULED_OUT_ALPHA) else shade(ink, EMPTY_ALPHA)
            }
            paint(row, col, color)
        }
    }
    pip(dogRow, dogCol)
}

private fun shade(ink: Color, alpha: Float) = ink.copy(alpha = ink.alpha * alpha)

/**
 * Three contiguous regions across the miniature, so the colour rule shows
 * shapes rather than stripes. The centre cell (where the pip goes) belongs to
 * group 1, so the diagram reads "this colour already has its dog".
 */
private val REGION_GROUPS = arrayOf(
    intArrayOf(0, 1, 1),
    intArrayOf(0, 1, 2),
    intArrayOf(0, 2, 2),
)

private const val GRID = 3f
private const val GRID_CELLS = 3
private const val PIP_RADIUS = 0.17f
private const val DIAGRAM_GAP = 0.16f
private const val DIAGRAM_RADIUS = 0.22f
private const val RULED_OUT_ALPHA = 0.5f
private const val EMPTY_ALPHA = 0.14f

private const val TAU = 6.2831855f
private const val BURST_RAYS = 8
private const val BURST_RAY_PHASE = 0.3927f
private const val BURST_RAY_WIDTH = 0.22f
private const val BURST_RAY_INNER = 0.30f
private const val BURST_START_REACH = 0.30f
private const val BURST_END_REACH = 0.86f
private const val BURST_BLOOM_SCALE = 0.62f
private const val BURST_BLOOM_ALPHA = 0.55f

private const val BONE_LOBE_FRACTION = 0.21f
private const val BONE_BAR_FRACTION = 0.30f
private const val BONE_LOBE_SPREAD = 0.95f
private const val BONE_RIM_FRACTION = 0.22f

private const val PAW_PAD_WIDTH = 0.56f
private const val PAW_PAD_HEIGHT = 0.40f
private const val PAW_PAD_BOTTOM = 0.06f
private const val PAW_TOE_RADIUS = 0.13f
private const val PAW_TOE_ROW = 0.22f
private const val PAW_TOE_OUTER_ROW = 0.34f
private const val PAW_TOE_OUTER_SCALE = 0.86f
private const val PAW_TOE_X0 = 0.16f
private const val PAW_TOE_X1 = 0.39f
private const val PAW_TOE_X2 = 0.61f
private const val PAW_TOE_X3 = 0.84f
private const val PAW_OUTLINE = 0.07f
