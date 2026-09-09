package com.sodogku.libraries.ui.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where an anchored card lands.
 *
 * This is the part of a tooltip that is worth testing and the part that used to
 * be untestable: it lived inside a composable, tangled up with alignment
 * modifiers and a guessed card height, so the only way to check it was to look
 * at a screen. Two copies of it existed and they disagreed -- the speech bubble
 * had no flip rule at all, so a bottom anchor pushed it off the display.
 */
class AnchoredCardTest {

    @Test
    fun itHangsBelowTheAnchorWhenThereIsRoom() {
        val top = place(anchorTop = 300f, anchorBottom = 400f, cardHeight = 200f)

        assertEquals(400f + Gap, top)
    }

    @Test
    fun itFlipsAboveTheAnchorWhenThereIsNotRoomBelow() {
        // The booster row: lit near the bottom, and a card hung under it would
        // be off the screen.
        val top = place(anchorTop = 1_600f, anchorBottom = 1_700f, cardHeight = 300f)

        assertEquals(1_600f - Gap - 300f, top, "card should sit a gap above the anchor")
        assertTrue(top + 300f < 1_600f, "flipped card must clear the anchor entirely")
    }

    @Test
    fun itNeverRisesIntoTheStatusBar() {
        // A spotlight with nothing to hang off reports an anchor at the origin.
        // Unclamped, the flip branch would put this at a negative offset.
        val top = place(anchorTop = 0f, anchorBottom = 0f, cardHeight = 400f)

        assertTrue(top >= MinTop, "landed at $top, above the $MinTop floor")
    }

    @Test
    fun itNeverRunsPastTheGestureBar() {
        // The anchor has to be right down on the bottom edge for this to bite.
        // A more comfortable anchor lands inside the ceiling on its own, which
        // is how the first version of this test passed with the clamp deleted.
        val cardHeight = 300f
        val top = place(anchorTop = 1_990f, anchorBottom = 2_000f, cardHeight = cardHeight)

        assertTrue(
            top + cardHeight <= Available - MinBottom,
            "card bottom ${top + cardHeight} is past the ${Available - MinBottom} ceiling",
        )
    }

    @Test
    fun aCardTallerThanTheScreenPinsToTheTop() {
        // Degenerate, and it has a right answer. The title is at the top of the
        // card and the buttons are at the bottom; if something has to be cut
        // off, it should be the buttons, because a player who cannot see the
        // title does not know what they are being told.
        val top = place(anchorTop = 500f, anchorBottom = 600f, cardHeight = Available * 2)

        assertEquals(MinTop, top)
    }

    @Test
    fun theFlipDecisionAccountsForTheCardsOwnHeight() {
        // The bug the old version could not have: it tested the anchor against a
        // fixed reserve, so two cards of very different heights under the same
        // anchor were placed on the same side. A tall card has to flip sooner.
        val anchorTop = 1_000f
        val anchorBottom = 1_100f

        val shortCard = place(anchorTop, anchorBottom, cardHeight = 200f)
        val tallCard = place(anchorTop, anchorBottom, cardHeight = 800f)

        assertTrue(shortCard > anchorBottom, "a short card still fits below")
        assertTrue(tallCard + 800f <= anchorTop, "a tall card under the same anchor has to flip")
    }

    private fun place(anchorTop: Float, anchorBottom: Float, cardHeight: Float): Float =
        anchoredCardTop(
            anchorTop = anchorTop,
            anchorBottom = anchorBottom,
            cardHeight = cardHeight,
            availableHeight = Available,
            gap = Gap,
            minTop = MinTop,
            minBottom = MinBottom,
        )

    private companion object {
        /** A tall phone in pixels, so the numbers above read like a real screen. */
        const val Available = 2_000f
        const val Gap = 40f
        const val MinTop = 120f
        const val MinBottom = 80f
    }
}
