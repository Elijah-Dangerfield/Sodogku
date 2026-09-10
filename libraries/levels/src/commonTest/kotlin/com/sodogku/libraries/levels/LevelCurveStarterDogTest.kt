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
    fun everyBandOpensWithTheDog() {
        BandFirstIds.forEach { levelId ->
            assertTrue(
                LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand),
                "level $levelId opens a band and should carry the free dog",
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
    fun theWindowIsAPositionInABandAndNotAnAbsoluteLevelId() {
        // The rule this replaced was `id <= 25`. Both halves matter: 25 is deep
        // into the 5x5 band and must open empty, and 101 is the first 7x7 and
        // must not, which no comparison against a single id can do.
        assertFalse(LevelCurve.opensWithStarterDog(25, ShippedLevelsPerBand))
        assertTrue(LevelCurve.opensWithStarterDog(101, ShippedLevelsPerBand))
        assertTrue(LevelCurve.opensWithStarterDog(391, ShippedLevelsPerBand))
    }

    @Test
    fun theLastLevelOfEveryBandOpensEmpty() {
        BandLastIds.forEach { levelId ->
            assertFalse(
                LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand),
                "level $levelId closes a band and should be unassisted",
            )
        }
    }

    @Test
    fun aWindowWiderThanABandStopsAtTheNextBandsOwnCount() {
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
    fun positionIsCountedFromTheStartOfTheBand() {
        BandFirstIds.zip(BandLastIds).forEach { (first, last) ->
            assertEquals(0, LevelCurve.positionInBand(first))
            assertEquals(last - first, LevelCurve.positionInBand(last))
        }
    }

    @Test
    fun anIdOffTheCurveBelongsToNoBand() {
        listOf(0, -1, LevelCurve.campaignShape.size + 1, Int.MAX_VALUE).forEach { levelId ->
            assertNull(LevelCurve.positionInBand(levelId), "level $levelId is not on the campaign curve")
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

        val BandLastIds = listOf(10, 40, 100, 180, 280, 390, 500)

        val BandSizes = listOf(4, 5, 6, 7, 8, 9, 10)
    }
}
