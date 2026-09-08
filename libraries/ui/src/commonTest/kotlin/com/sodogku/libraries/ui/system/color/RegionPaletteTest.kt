package com.sodogku.libraries.ui.system.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The palette makes three promises the board depends on, and all three are the
 * kind that stay true right up until somebody adjusts one colour by eye.
 */
class RegionPaletteTest {

    @Test
    fun everyInkIsTheHigherContrastOfTheTwoCandidates() {
        RegionPalette.styles.forEachIndexed { region, style ->
            val chosen = contrastRatio(style.ink.opaque(), style.fill)
            val rejected = contrastRatio(style.ink.other().opaque(), style.fill)
            assertTrue(
                chosen >= rejected,
                "region $region picked the worse ink: ${style.ink} scores $chosen against " +
                    "${style.fill}, while the other candidate scores $rejected",
            )
        }
    }

    /**
     * The derivation currently answers "dark" for all ten, which makes the
     * assertion above true of a hardcoded `DARK_INK` as well. This is what
     * separates the two: it feeds the function a fill dark enough that only a
     * light ink can win, and a constant would fail it.
     */
    @Test
    fun aDarkFillFlipsTheInkToLight() {
        assertEquals(LIGHT_INK, inkFor(Color(0xFF15122B)))
        assertEquals(DARK_INK, inkFor(Color(0xFFF7C24E)))
    }

    /**
     * Colourblind mode is the palette's answer to ten hues nobody can hold
     * apart, so the glyph has to survive the fill it is drawn on. The floor is
     * the *composited* ratio — the ink is drawn at partial alpha over the fill,
     * and judging the declared colour instead would let a watermark that is
     * invisible on screen pass.
     */
    @Test
    fun everyRegionGlyphClearsTheWatermarkFloor() {
        RegionPalette.styles.forEachIndexed { region, style ->
            val drawn = style.ink.copy(alpha = style.ink.alpha * ColourblindGlyphAlpha)
            val ratio = contrastRatio(composite(drawn, style.fill), style.fill)
            assertTrue(
                ratio >= GlyphContrastFloor,
                "region $region's glyph lands at $ratio against its fill, under $GlyphContrastFloor",
            )
        }
    }

    /**
     * Two regions that look the same are a board that cannot be solved, and a
     * pastel palette is one careless nudge away from producing a pair. 20 is
     * roughly where two large flat colours stop being obviously different; the
     * shipped set holds 23.6, and the saturated palette this replaced held 21.5.
     */
    @Test
    fun noTwoFillsAreCloserThanTheSeparationFloor() {
        val fills = RegionPalette.styles.map { it.fill }
        val closest = fills.indices.flatMap { i ->
            (i + 1..fills.lastIndex).map { j -> Triple(i, j, perceptualDistance(fills[i], fills[j])) }
        }.minBy { it.third }

        assertTrue(
            closest.third >= SeparationFloor,
            "regions ${closest.first} and ${closest.second} are only ${closest.third} apart in " +
                "CIELAB, under the floor of $SeparationFloor",
        )
    }

    /**
     * The lightness ladder, which is what carries a red-green colourblind
     * player when hue does not. Asserted as a span rather than per-colour: any
     * single fill can be any lightness, what matters is that the set is not all
     * one.
     */
    @Test
    fun theFillsSpanARangeOfLightness() {
        val luminances = RegionPalette.styles.map { it.fill.luminance() }
        val span = luminances.max() - luminances.min()
        assertTrue(span >= LuminanceSpanFloor, "the fills span only $span of luminance")
    }

    /** Out-of-range regions wrap rather than throwing on the board screen. */
    @Test
    fun regionIndicesWrapInBothDirections() {
        assertEquals(RegionPalette.styles[0], RegionPalette[RegionPalette.size])
        assertEquals(RegionPalette.styles[RegionPalette.size - 1], RegionPalette[-1])
    }

    private fun Color.opaque() = copy(alpha = 1f)

    private fun Color.other() = if (this == DARK_INK) LIGHT_INK else DARK_INK

    private companion object {
        /** Mirrors `BoardCell.GlyphAlpha`. */
        const val ColourblindGlyphAlpha = 0.60f
        const val GlyphContrastFloor = 1.70f
        const val SeparationFloor = 20f
        const val LuminanceSpanFloor = 0.30f
    }
}
