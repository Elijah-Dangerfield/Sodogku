package com.sodogku.libraries.ui.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The ten region colours, indexed to match `Board.regions`.
 *
 * These are not semantic colours and deliberately do not live on [Colors]:
 * nothing in the app asks for "the rose one", it asks for region 4. Indexing is
 * the whole interface.
 *
 * Three constraints shaped the set, in this order:
 *
 * 1. **The dog has to read on every one.** The art is cream with dark features,
 *    so pale fills swallow it. Every fill here is a saturated mid-tone, and the
 *    cream-and-tan part of the spectrum is left out entirely — an orange region
 *    under an orange dog is an invisible dog.
 * 2. **Lightness varies as much as hue.** Roughly 8% of men cannot separate red
 *    from green, so a set that is only hue-separated collapses for them. These
 *    span light amber and lime through to deep indigo and violet.
 * 3. **No colour-only encoding.** Even with the lightness ladder, ten colours
 *    cannot survive deuteranopia. [RegionStyle.glyph] is the real answer, and
 *    colourblind mode promotes it from decoration to the region's identity.
 */
@Immutable
data class RegionStyle(
    /** The cell fill. */
    val fill: Color,

    /**
     * Marks and glyphs drawn on top of [fill]. Derived from contrast rather than
     * chosen by hand: assigning these by eye got three of the ten wrong, and
     * they were only obviously wrong once rendered side by side.
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

    /** Indexed by region id. `Board` caps regions at ten, so this is exhaustive. */
    val styles: List<RegionStyle> = listOf(
        Color(0xFFE8547C) to RegionGlyph.Circle,
        Color(0xFFF5B93D) to RegionGlyph.Ring,
        Color(0xFF5566D6) to RegionGlyph.Square,
        Color(0xFFB5D64A) to RegionGlyph.Diamond,
        Color(0xFF9457C9) to RegionGlyph.TriangleUp,
        Color(0xFF35BFAE) to RegionGlyph.TriangleDown,
        Color(0xFFF4714F) to RegionGlyph.Plus,
        Color(0xFF56AEEF) to RegionGlyph.Bar,
        Color(0xFF3D9E63) to RegionGlyph.DoubleBar,
        Color(0xFFC563AE) to RegionGlyph.Chevron,
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
private fun inkFor(fill: Color): Color =
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

/** For fills light enough that a dark mark reads better. */
private val DARK_INK = Color(0x8C1B1230)

/** For fills dark enough that a light mark reads better. */
private val LIGHT_INK = Color(0x99FFFFFF)

/** Luminance of the inks at full opacity, which is what contrast is judged on. */
private val DARK_INK_LUMINANCE = Color(0xFF1B1230).luminance()
private val LIGHT_INK_LUMINANCE = Color(0xFFFFFFFF).luminance()

private const val CONTRAST_OFFSET = 0.05f
