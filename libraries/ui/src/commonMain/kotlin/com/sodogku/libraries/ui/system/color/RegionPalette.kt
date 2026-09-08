package com.sodogku.libraries.ui.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The ten region colours, indexed to match `Board.regions`.
 *
 * These are not semantic colours and deliberately do not live on [Colors]:
 * nothing in the app asks for "the rose one", it asks for region 4. Indexing is
 * the whole interface.
 *
 * Three constraints shaped the set, in this order:
 *
 * 1. **The dog has to read on every one.** The art is near-white with tan ears,
 *    so a fill it cannot be told apart from is a lost dog. The cream-and-tan
 *    part of the spectrum is left out entirely — an orange region under an
 *    orange dog is an invisible dog — and every fill keeps enough saturation to
 *    stay a *colour* rather than a tint of the page. These are pastels, but the
 *    softest of them still sits at 1.44:1 against the dog's body, which is
 *    thin: [com.sodogku.libraries.ui.components.board.BoardCell] pays the rest
 *    of the difference with a contact shadow under a placed dog, and that
 *    shadow is load-bearing, not decoration.
 * 2. **Ten regions have to be ten regions.** Softening a palette pulls every
 *    colour towards white, which is exactly the direction that makes two of
 *    them the same colour. The first pastel pass looked lovely and put two
 *    purples 14.2 apart in CIELAB, which on a 10x10 with both on screen is a
 *    board the player cannot read. This set holds a floor of 23.6, *better*
 *    than the saturated palette it replaced (21.5), because the two closest
 *    pairs were pulled apart deliberately rather than hoped about.
 *
 *    Lightness still varies as much as hue — roughly 8% of men cannot separate
 *    red from green, so a set separated only by hue collapses for them. Two of
 *    the ten (periwinkle, orchid) are deliberately deeper than the rest to keep
 *    a ladder under the pastels; the luminance span is 0.37 against the old
 *    palette's 0.42.
 * 3. **No colour-only encoding.** Even with the lightness ladder, ten colours
 *    cannot survive deuteranopia. [RegionStyle.glyph] is the real answer, and
 *    colourblind mode promotes it from decoration to the region's identity.
 */
@Immutable
data class RegionStyle(
    /** The cell fill. */
    val fill: Color,

    /**
     * The region glyph drawn on top of [fill] in colourblind mode. Derived from
     * contrast rather than chosen by hand: assigning these by eye got three of
     * the ten wrong, and they were only obviously wrong once rendered side by
     * side.
     *
     * The pastel retune moved every fill light enough that the dark ink now
     * wins on all ten, so the derivation currently returns one branch. That is
     * not a reason to delete it: it is the thing that noticed the old palette
     * needed two, and it will notice again the next time a fill is darkened.
     *
     * It stopped being the colour of the player's cross at the same time. A
     * mark has to be found at a glance across a hundred squares, and the answer
     * to "what reads on ten different pastels" is one loud colour on all of
     * them, not ten quiet ones that each merely pass. The cross is white; this
     * stays derived because a *watermark* genuinely does want the higher-
     * contrast ink for the fill it sits on.
     */
    val ink: Color,

    /** The shape that identifies this region when hue cannot. */
    val glyph: RegionGlyph,
)

/**
 * A distinct silhouette per region. Kept to shapes that stay legible at the
 * ~40dp a 10x10 cell gets, which rules out anything with fine detail.
 *
 * Deliberately no X or cross: that reads as the player's own "no dog here"
 * mark, and a region whose identity looks like a move would be cruel.
 */
enum class RegionGlyph {
    Circle,
    Ring,
    Square,
    Diamond,
    TriangleUp,
    TriangleDown,
    Plus,
    Bar,
    DoubleBar,
    Chevron,
}

object RegionPalette {

    /**
     * Indexed by region id. `Board` caps regions at ten, so this is exhaustive.
     *
     * Pastels, not the chart-legend saturation this started at. A board of
     * strong blues and hot pinks reads as data; the same board in sweet-shop
     * tones reads as a game, and — the part that is not only taste — it leaves
     * room for the player's white cross to be the loudest thing on the square,
     * which is the only mark that has to be found at a glance.
     */
    val styles: List<RegionStyle> = listOf(
        Color(0xFFF58BAA) to RegionGlyph.Circle,
        Color(0xFFF7C24E) to RegionGlyph.Ring,
        Color(0xFF7E8FE4) to RegionGlyph.Square,
        Color(0xFFC3DC6B) to RegionGlyph.Diamond,
        Color(0xFFD9B6F5) to RegionGlyph.TriangleUp,
        Color(0xFF5FD6C0) to RegionGlyph.TriangleDown,
        Color(0xFFFF9578) to RegionGlyph.Plus,
        Color(0xFF86C6F0) to RegionGlyph.Bar,
        Color(0xFF8FD48A) to RegionGlyph.DoubleBar,
        Color(0xFFC86FB0) to RegionGlyph.Chevron,
    ).map { (fill, glyph) -> RegionStyle(fill, inkFor(fill), glyph) }

    val size: Int get() = styles.size

    /**
     * The style for [region]. Wraps rather than throwing: a board with more
     * regions than the palette has colours is a content bug, and a wrapped
     * colour is a far better failure than a crash on the board screen.
     */
    operator fun get(region: Int): RegionStyle = styles[region.mod(styles.size)]
}

/**
 * Whichever of the two inks has more contrast against [fill].
 *
 * Worth computing rather than declaring: hand-picking these put light ink on
 * coral, green and plum, all three of which are dark enough that the mark nearly
 * vanished. The error was invisible in code review and obvious the moment the
 * palette was rendered as a strip, which is exactly the kind of mistake a
 * derivation removes for good.
 */
internal fun inkFor(fill: Color): Color =
    if (contrastRatio(fill.luminance(), DARK_INK_LUMINANCE) >=
        contrastRatio(fill.luminance(), LIGHT_INK_LUMINANCE)
    ) {
        DARK_INK
    } else {
        LIGHT_INK
    }

private fun contrastRatio(a: Float, b: Float): Float {
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

/**
 * The WCAG contrast ratio between two opaque colours, 1.0 to 21.0.
 *
 * Public because contrast is a claim this design system makes and claims are
 * worth being able to assert. [composite] is its other half: an ink drawn at
 * partial alpha has the contrast of what lands on screen, not of the colour it
 * was declared as, and judging the second for the first is how a legible-
 * looking palette ships an illegible watermark.
 */
fun contrastRatio(a: Color, b: Color): Float = contrastRatio(a.luminance(), b.luminance())

/**
 * How far apart two colours look, as CIELAB ΔE (1976).
 *
 * Not a contrast ratio and not an RGB distance. Contrast answers "can I read
 * this on that", which is the wrong question for two fills sitting side by side
 * — two colours can have identical luminance and still be obviously different.
 * RGB distance answers a question about numbers rather than about eyes: it puts
 * two greens further apart than a green and a blue that anybody could tell
 * apart instantly.
 *
 * The rule of thumb it is used against: ~2.3 is the just-noticeable difference,
 * and anything under about 20 is a pair of squares a player has to think about.
 */
fun perceptualDistance(a: Color, b: Color): Float {
    val (l1, a1, b1) = a.toLab()
    val (l2, a2, b2) = b.toLab()
    return sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
}

private fun Color.toLab(): Triple<Float, Float, Float> {
    val r = linearise(red)
    val g = linearise(green)
    val b = linearise(blue)
    val x = pivot((X_R * r + X_G * g + X_B * b) / WHITE_X)
    val y = pivot(Y_R * r + Y_G * g + Y_B * b)
    val z = pivot((Z_R * r + Z_G * g + Z_B * b) / WHITE_Z)
    return Triple(LAB_L_SCALE * y - LAB_L_OFFSET, LAB_A_SCALE * (x - y), LAB_B_SCALE * (y - z))
}

private fun linearise(component: Float): Float =
    if (component <= SRGB_KNEE) component / SRGB_SLOPE
    else ((component + SRGB_OFFSET) / (1f + SRGB_OFFSET)).pow(SRGB_GAMMA)

private fun pivot(t: Float): Float =
    if (t > LAB_EPSILON) t.pow(1f / 3f) else LAB_KAPPA * t + LAB_L_OFFSET / LAB_L_SCALE

private const val SRGB_KNEE = 0.04045f
private const val SRGB_SLOPE = 12.92f
private const val SRGB_OFFSET = 0.055f
private const val SRGB_GAMMA = 2.4f

private const val X_R = 0.4124f
private const val X_G = 0.3576f
private const val X_B = 0.1805f
private const val Y_R = 0.2126f
private const val Y_G = 0.7152f
private const val Y_B = 0.0722f
private const val Z_R = 0.0193f
private const val Z_G = 0.1192f
private const val Z_B = 0.9505f
private const val WHITE_X = 0.95047f
private const val WHITE_Z = 1.08883f

private const val LAB_EPSILON = 0.008856f
private const val LAB_KAPPA = 7.787f
private const val LAB_L_SCALE = 116f
private const val LAB_L_OFFSET = 16f
private const val LAB_A_SCALE = 500f
private const val LAB_B_SCALE = 200f

/** [source] drawn over [destination], as an opaque colour. */
fun composite(source: Color, destination: Color): Color {
    val alpha = source.alpha
    return Color(
        red = source.red * alpha + destination.red * (1f - alpha),
        green = source.green * alpha + destination.green * (1f - alpha),
        blue = source.blue * alpha + destination.blue * (1f - alpha),
    )
}

/** For fills light enough that a dark mark reads better. */
internal val DARK_INK = Color(0x8C1B1230)

/** For fills dark enough that a light mark reads better. */
internal val LIGHT_INK = Color(0x99FFFFFF)

/** Luminance of the inks at full opacity, which is what contrast is judged on. */
private val DARK_INK_LUMINANCE = Color(0xFF1B1230).luminance()
private val LIGHT_INK_LUMINANCE = Color(0xFFFFFFFF).luminance()

private const val CONTRAST_OFFSET = 0.05f
