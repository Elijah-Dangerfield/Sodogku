package com.sodogku.libraries.ui.components.streak

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ring around today, on a square that clips its own content.
 *
 * Every case here is the same bug seen from a different side: a stroke placed
 * without allowing for the clip is not drawn wrong, it is drawn *and then
 * shaved*, so it survives review as a slightly thin border with slightly odd
 * corners. Nobody reads that as a mistake, which is why it lasted.
 */
class TodayRingTest {

    /** A 100px square clipped to Radii.Cell's twenty percent, ring at nine. */
    private val ring = todayRing(cellExtent = 100f, widthFraction = 0.09f, clipCornerRadius = 20f)

    @Test
    fun theWholeStrokeFallsInsideTheCellTheClipWillCut() {
        assertEquals(
            0f,
            ring.overhang,
            "the outside of the stroke has to land on the cell's edge, not past it",
        )
    }

    @Test
    fun theRingIsAsWideAsItAsksToBe() {
        assertEquals(9f, ring.strokeWidth)
        assertEquals(4.5f, ring.inset, "the centreline sits half a stroke in")
    }

    @Test
    fun theCornerFollowsTheClipInwardRatherThanCopyingIt() {
        assertEquals(
            15.5f,
            ring.cornerRadius,
            "the clip's corner, walked in to the stroke's centreline",
        )
    }

    @Test
    fun aCornerTighterThanTheInsetFlattensRatherThanGoingNegative() {
        val tight = todayRing(cellExtent = 100f, widthFraction = 0.09f, clipCornerRadius = 2f)
        assertEquals(0f, tight.cornerRadius)
    }

    @Test
    fun theRingScalesWithTheCellSoEveryGridSizeLooksTheSame() {
        val small = todayRing(cellExtent = 40f, widthFraction = 0.09f, clipCornerRadius = 8f)
        val large = todayRing(cellExtent = 120f, widthFraction = 0.09f, clipCornerRadius = 24f)
        assertEquals(3.6f, small.strokeWidth, absoluteTolerance = 0.0001f)
        assertEquals(10.8f, large.strokeWidth, absoluteTolerance = 0.0001f)
        assertTrue(small.overhang == 0f && large.overhang == 0f)
    }
}
