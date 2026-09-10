package com.sodogku.libraries.levels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The free opening dog, stated as arithmetic rather than played.
 *
 * [BandFirstIds] is written out rather than summed from [LevelCurve.campaign],
 * so this is a second, independent statement of where the grid grows. A test
 * that derived the boundaries from the same list it is checking would agree with
 * any re-curve, including an accidental one.
 */
class LevelCurveStarterDogTest {

    @Test
    fun theNamedIdsReallyAreTheFirstLevelOfEachGridSize() {
        val shape = LevelCurve.campaignShape

        BandFirstIds.zip(BandSizes).forEach { (levelId, size) ->
            assertEquals(size, shape[levelId - 1].size, "level $levelId should be the first ${size}x$size")
            if (levelId > 1) {
                assertTrue(
                    shape[levelId - 2].size < size,
                    "level ${levelId - 1} should still be on the smaller grid",
                )
            }
        }
    }

    @Test
    fun everyGridSizeOpensWithTheDog() {
        BandFirstIds.forEach { levelId ->
            assertTrue(
                LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand),
                "level $levelId opens a grid size and should carry the free dog",
            )
        }
    }

    @Test
    fun theDogRunsOutExactlyAtTheConfiguredCount() {
        BandFirstIds.forEach { first ->
            val last = first + ShippedLevelsPerBand - 1
            assertTrue(
                LevelCurve.opensWithStarterDog(last, ShippedLevelsPerBand),
                "level $last is the last of $ShippedLevelsPerBand and should still carry it",
            )
            assertFalse(
                LevelCurve.opensWithStarterDog(last + 1, ShippedLevelsPerBand),
                "level ${last + 1} is one past the window and should open empty",
            )
        }
    }

    @Test
    fun theWindowIsAPositionAtAGridSizeAndNotAnAbsoluteLevelId() {
        // The rule this replaced was `id <= 25`. Both halves matter: 25 is deep
        // into the 5x5 band and must open empty, and 101 is the first 7x7 and
        // must not, which no comparison against a single id can do.
        assertFalse(LevelCurve.opensWithStarterDog(25, ShippedLevelsPerBand))
        assertTrue(LevelCurve.opensWithStarterDog(101, ShippedLevelsPerBand))
        assertTrue(LevelCurve.opensWithStarterDog(391, ShippedLevelsPerBand))
    }

    @Test
    fun theLastLevelAtEveryGridSizeOpensEmpty() {
        BandLastIds.forEach { levelId ->
            assertFalse(
                LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand),
                "level $levelId closes a grid size and should be unassisted",
            )
        }
    }

    @Test
    fun aWindowWiderThanABandStopsAtTheNextGridSizesOwnCount() {
        // Twenty covers the whole ten-level 4x4 band and two thirds of the 5x5
        // one. What it must not do is keep counting from level 1 into the band
        // after that: 31 is the twenty-first 5x5 and gets nothing.
        assertTrue(LevelCurve.opensWithStarterDog(10, WideWindow))
        assertTrue(LevelCurve.opensWithStarterDog(30, WideWindow))
        assertFalse(LevelCurve.opensWithStarterDog(31, WideWindow))
        assertTrue(LevelCurve.opensWithStarterDog(41, WideWindow), "41 opens the 6x6 band")
    }

    @Test
    fun zeroOrLessHandsOutNone() {
        BandFirstIds.forEach { levelId ->
            assertFalse(LevelCurve.opensWithStarterDog(levelId, 0))
            assertFalse(LevelCurve.opensWithStarterDog(levelId, -1))
        }
    }

    @Test
    fun positionIsCountedFromTheFirstLevelAtThatGridSize() {
        BandFirstIds.zip(BandLastIds).forEach { (first, last) ->
            assertEquals(0, LevelCurve.positionAtSize(first))
            assertEquals(last - first, LevelCurve.positionAtSize(last))
        }
    }

    @Test
    fun theDogDoesNotComeBackWhenOnlyTheBandChanges() {
        // The 10x10 stretch is six bands long and one grid wide. A window
        // counted from each band's start would hand out three more free dogs at
        // 501, 601, 701, 801 and 901: fifteen head starts on the boards where
        // the player needs one least, none of them announced by a bigger grid.
        AppendedBandFirstIds.forEach { levelId ->
            assertFalse(
                LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand),
                "level $levelId opens a band on a grid that did not grow",
            )
        }
        assertEquals(FirstTenByTenPosition, LevelCurve.positionAtSize(501))
    }

    @Test
    fun anIdOffTheCurveBelongsToNoBand() {
        listOf(0, -1, LevelCurve.campaignShape.size + 1, Int.MAX_VALUE).forEach { levelId ->
            assertNull(LevelCurve.positionAtSize(levelId), "level $levelId is not on the campaign curve")
            assertFalse(LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand))
        }
    }

    private companion object {

        /** `progression.starterDogLevelsPerBand` as shipped. */
        const val ShippedLevelsPerBand = 3

        /** Wider than the first band and narrower than the second. */
        const val WideWindow = 20

        /** The first level at each grid size, 4x4 through 10x10. */
        val BandFirstIds = listOf(1, 11, 41, 101, 181, 281, 391)

        /** The last level at each grid size. Ten runs to the end of the pack. */
        val BandLastIds = listOf(10, 40, 100, 180, 280, 390, 1000)

        /**
         * The first level of every 10x10 band after the first. Written out
         * rather than derived, for the reason [BandFirstIds] is: a list summed
         * from the curve agrees with any re-curve, accidental ones included.
         */
        val AppendedBandFirstIds = listOf(501, 601, 701, 801, 901)

        /** Level 501 is the 111th board at 10x10, and the 110th past the first. */
        const val FirstTenByTenPosition = 110

        val BandSizes = listOf(4, 5, 6, 7, 8, 9, 10)
    }
}
