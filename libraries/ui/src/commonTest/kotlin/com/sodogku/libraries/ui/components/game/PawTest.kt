package com.sodogku.libraries.ui.components.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The paw's geometry and how it lands in a box.
 *
 * There is one paw in this app now — `drawPaw` and `Icons.Paw` both read
 * [Paw] — so the thing worth pinning is that the numbers describing the artwork
 * and the numbers describing where it sits agree with each other. [Paw.INK_LEFT]
 * and friends are typed in rather than derived, and a paw whose stated bounds
 * have drifted from its actual shape is off-centre in every place it is drawn
 * while still looking exactly like a paw.
 *
 * **Not covered here:** what `drawPaw` actually puts on a canvas, and what the
 * `ImageVector` arcs resolve to. Both need a real surface, and both are the same
 * five primitives at the coordinates this file already checks — the emulator
 * screenshots are the honest test of the drawing itself.
 */
class PawTest {

    @Test
    fun theStatedInkBoundsAreTheShapeTheArtworkActuallyDraws() {
        val left = minOf(
            Paw.EXTENT / 2f - Paw.PAD_RADIUS_X,
            Paw.TOES.minOf { (x, _) -> x - Paw.TOE_RADIUS },
        )
        val top = minOf(
            Paw.PAD_CENTRE_Y - Paw.PAD_RADIUS_Y,
            Paw.TOES.minOf { (_, y) -> y - Paw.TOE_RADIUS },
        )
        val right = maxOf(
            Paw.EXTENT / 2f + Paw.PAD_RADIUS_X,
            Paw.TOES.maxOf { (x, _) -> x + Paw.TOE_RADIUS },
        )
        val bottom = maxOf(
            Paw.PAD_CENTRE_Y + Paw.PAD_RADIUS_Y,
            Paw.TOES.maxOf { (_, y) -> y + Paw.TOE_RADIUS },
        )

        assertEquals(left, Paw.INK_LEFT, Tolerance)
        assertEquals(top, Paw.INK_TOP, Tolerance)
        assertEquals(right - left, Paw.INK_WIDTH, Tolerance)
        assertEquals(bottom - top, Paw.INK_HEIGHT, Tolerance)
    }

    /**
     * The whole reason `drawPaw` fits the ink rather than the SVG's viewport:
     * a caller sizing a `Box` has decided how big the paw is, and the artwork's
     * own margin would silently take a seventh of it back.
     */
    @Test
    fun aSquareBoxIsFilledEdgeToEdgeAcross() {
        val fit = Paw.fitTo(Box, Box)

        assertEquals(0f, fit.x(Paw.INK_LEFT), Tolerance)
        assertEquals(Box, fit.x(Paw.INK_LEFT + Paw.INK_WIDTH), Tolerance)
    }

    @Test
    fun aSquareBoxCentresTheShorterAxisRatherThanStretchingIt() {
        val fit = Paw.fitTo(Box, Box)
        val height = Paw.INK_HEIGHT * fit.scale
        val above = fit.y(Paw.INK_TOP)
        val below = Box - fit.y(Paw.INK_TOP + Paw.INK_HEIGHT)

        assertTrue(height < Box, "the paw is wider than it is tall, so it cannot fill a square both ways")
        assertEquals(above, below, Tolerance)
    }

    /**
     * A rectangle is the case the fit exists for. Nothing hands `drawPaw` one
     * today, which is exactly why it would go unnoticed: the next caller to try
     * would get a stretched paw and no failure anywhere.
     */
    @Test
    fun aWideBoxIsHeldToItsHeightAndCentredAcross() {
        val width = Box * 3f
        val fit = Paw.fitTo(width, Box)

        assertEquals(Box / Paw.INK_HEIGHT, fit.scale, Tolerance, "the short side has to be the limit")
        assertEquals(fit.x(Paw.INK_LEFT), width - fit.x(Paw.INK_LEFT + Paw.INK_WIDTH), Tolerance)
        assertTrue(
            Paw.INK_WIDTH * fit.scale < width,
            "a wide box must not stretch the paw across it",
        )
    }

    @Test
    fun aTallBoxIsHeldToItsWidthTheSameWay() {
        val height = Box * 3f
        val fit = Paw.fitTo(Box, height)

        assertEquals(Box / Paw.INK_WIDTH, fit.scale, Tolerance, "the short side has to be the limit")
        assertEquals(fit.y(Paw.INK_TOP), height - fit.y(Paw.INK_TOP + Paw.INK_HEIGHT), Tolerance)
        assertTrue(
            Paw.INK_HEIGHT * fit.scale < height,
            "a tall box must not stretch the paw down it",
        )
    }

    /**
     * Every toe is the same size, and the four sit in a symmetric arch. Both are
     * properties of the supplied artwork rather than of any code here, and both
     * are the kind of thing a "tidy up the constants" pass breaks by nudging one
     * number — a paw with one fat toe reads as a rendering bug.
     */
    @Test
    fun theToesAreEvenlySizedAndSymmetricAboutTheCentre() {
        val centre = Paw.EXTENT / 2f
        val offsets = Paw.TOES.map { (x, _) -> x - centre }

        assertEquals(listOf(-offsets[3], -offsets[2]), listOf(offsets[0], offsets[1]))
        assertEquals(Paw.TOES[0].second, Paw.TOES[3].second)
        assertEquals(Paw.TOES[1].second, Paw.TOES[2].second)
        assertTrue(Paw.TOES[1].second < Paw.TOES[0].second, "the inner toes sit above the outer pair")
    }

    private companion object {
        const val Box = 88f
        const val Tolerance = 0.001f
    }
}
