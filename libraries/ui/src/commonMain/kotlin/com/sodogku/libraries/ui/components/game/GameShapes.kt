package com.sodogku.libraries.ui.components.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
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

/**
 * The paw print, in the units of `art/source/icons/paw.svg`.
 *
 * There is exactly one paw in this app and this is it. It is drawn two ways —
 * straight onto a canvas by [drawPaw], and as an [androidx.compose.ui.graphics.vector.ImageVector]
 * by `Icons.Paw` — and both read their geometry from here, so the paw on a
 * booster button and the paw in a rating cannot drift apart by somebody
 * retuning one of them.
 *
 * Kept in the SVG's own 120-unit box rather than normalised to 0..1, so the
 * numbers can be checked against the source file by eye.
 *
 * **The two renderers frame it differently, on purpose.** The artwork leaves a
 * margin inside its 120-unit box, which is what an icon wants — every other
 * entry in `Icons` carries its own optical padding, and a paw that ran to the
 * edge of its viewport would sit visibly larger than the glyph beside it.
 * [drawPaw] is handed an exact box by a caller who has already decided how big
 * the paw should be, so it fits [INK_WIDTH] by [INK_HEIGHT] instead and fills
 * what it is given.
 */
internal object Paw {
    const val EXTENT = 120f
    const val PAD_CENTRE_Y = 79f
    const val PAD_RADIUS_X = 30f
    const val PAD_RADIUS_Y = 24f
    const val TOE_RADIUS = 12f

    /** Toe centres, outer-left to outer-right. The outer pair sits lower. */
    val TOES = listOf(
        28f to 47f,
        49f to 31f,
        71f to 31f,
        92f to 47f,
    )

    /**
     * Line weight for the hollow paw, in the same units.
     *
     * Not from the SVG, which is a solid shape — the rating draws an unearned
     * paw as an outline and needs a number. Six units against [INK_WIDTH] is
     * seven percent of the box, which is what the outline weighed before the
     * geometry changed: the shape is the thing being replaced here, and a line
     * that quietly got a third heavier at the same time would be a second change
     * hiding inside the first.
     */
    const val OUTLINE = 6f

    /** Where the ink actually starts and how far it runs, in the same units. */
    const val INK_LEFT = 16f
    const val INK_TOP = 19f
    const val INK_WIDTH = 88f
    const val INK_HEIGHT = 84f

    /**
     * Where the paw's own coordinates land inside a [width] by [height] box.
     *
     * Its own function because the offsets are the part that goes wrong quietly.
     * A paw drawn at the right size in the wrong place still looks like a paw,
     * and the two call sites that would catch it — a rating five across and a
     * button one across — both look plausible while off-centre.
     */
    fun fitTo(width: Float, height: Float): Fit {
        val scale = minOf(width / INK_WIDTH, height / INK_HEIGHT)
        return Fit(
            scale = scale,
            originX = (width - INK_WIDTH * scale) / 2f - INK_LEFT * scale,
            originY = (height - INK_HEIGHT * scale) / 2f - INK_TOP * scale,
        )
    }

    /** The paw's coordinate space placed in a draw box. */
    data class Fit(val scale: Float, val originX: Float, val originY: Float) {
        fun x(value: Float): Float = originX + value * scale
        fun y(value: Float): Float = originY + value * scale
    }
}

/**
 * A paw print: one pad and four toes.
 *
 * [filled] false draws the same shape hollow, which is how an unearned paw in
 * the rating is spelled.
 *
 * The ink is scaled uniformly to fit the draw box and centred in it, so a caller
 * can hand this a rectangle without the paw stretching, and a caller who hands
 * it a square gets a paw that fills the square.
 */
fun DrawScope.drawPaw(color: Color, filled: Boolean) {
    val fit = Paw.fitTo(size.width, size.height)
    val style: DrawStyle = if (filled) Fill else Stroke(width = Paw.OUTLINE * fit.scale)

    drawOval(
        color = color,
        topLeft = Offset(
            fit.x(Paw.EXTENT / 2f - Paw.PAD_RADIUS_X),
            fit.y(Paw.PAD_CENTRE_Y - Paw.PAD_RADIUS_Y),
        ),
        size = Size(Paw.PAD_RADIUS_X * 2f * fit.scale, Paw.PAD_RADIUS_Y * 2f * fit.scale),
        style = style,
    )

    Paw.TOES.forEach { (x, y) ->
        drawCircle(
            color = color,
            radius = Paw.TOE_RADIUS * fit.scale,
            center = Offset(fit.x(x), fit.y(y)),
            style = style,
        )
    }
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

