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
 *
 * There are two windows and they are tested apart before they are tested
 * together: most cases here pass [NoOpeningRun] or [NoBandWindow] so that a
 * failure names the window that broke rather than the pair.
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
                LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand, NoOpeningRun),
                "level $levelId opens a grid size and should carry the free dog",
            )
        }
    }

    @Test
    fun theDogRunsOutExactlyAtTheConfiguredCount() {
        BandFirstIds.forEach { first ->
            val last = first + ShippedLevelsPerBand - 1
            assertTrue(
                LevelCurve.opensWithStarterDog(last, ShippedLevelsPerBand, NoOpeningRun),
                "level $last is the last of $ShippedLevelsPerBand and should still carry it",
            )
            assertFalse(
                LevelCurve.opensWithStarterDog(last + 1, ShippedLevelsPerBand, NoOpeningRun),
                "level ${last + 1} is one past the window and should open empty",
            )
        }
    }

    @Test
    fun theWindowIsAPositionAtAGridSizeAndNotAnAbsoluteLevelId() {
        // The rule this replaced was `id <= 25`. Both halves matter: 25 is deep
        // into the 5x5 band and must open empty, and 101 is the first 7x7 and
        // must not, which no comparison against a single id can do.
        assertFalse(LevelCurve.opensWithStarterDog(25, ShippedLevelsPerBand, NoOpeningRun))
        assertTrue(LevelCurve.opensWithStarterDog(101, ShippedLevelsPerBand, NoOpeningRun))
        assertTrue(LevelCurve.opensWithStarterDog(391, ShippedLevelsPerBand, NoOpeningRun))
    }

    @Test
    fun theLastLevelAtEveryGridSizeOpensEmpty() {
        BandLastIds.forEach { levelId ->
            assertFalse(
                LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand, NoOpeningRun),
                "level $levelId closes a grid size and should be unassisted",
            )
        }
    }

    @Test
    fun aWindowWiderThanABandStopsAtTheNextGridSizesOwnCount() {
        // Twenty covers the whole ten-level 4x4 band and two thirds of the 5x5
        // one. What it must not do is keep counting from level 1 into the band
        // after that: 31 is the twenty-first 5x5 and gets nothing.
        assertTrue(LevelCurve.opensWithStarterDog(10, WideWindow, NoOpeningRun))
        assertTrue(LevelCurve.opensWithStarterDog(30, WideWindow, NoOpeningRun))
        assertFalse(LevelCurve.opensWithStarterDog(31, WideWindow, NoOpeningRun))
        assertTrue(LevelCurve.opensWithStarterDog(41, WideWindow, NoOpeningRun), "41 opens the 6x6 band")
    }

    @Test
    fun zeroOrLessHandsOutNone() {
        BandFirstIds.forEach { levelId ->
            assertFalse(LevelCurve.opensWithStarterDog(levelId, 0, NoOpeningRun))
            assertFalse(LevelCurve.opensWithStarterDog(levelId, -1, NoOpeningRun))
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
                LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand, NoOpeningRun),
                "level $levelId opens a band on a grid that did not grow",
            )
        }
        assertEquals(FirstTenByTenPosition, LevelCurve.positionAtSize(501))
    }

    @Test
    fun anIdOffTheCurveBelongsToNoBand() {
        listOf(0, -1, LevelCurve.campaignShape.size + 1, Int.MAX_VALUE).forEach { levelId ->
            assertNull(LevelCurve.positionAtSize(levelId), "level $levelId is not on the campaign curve")
            assertFalse(LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand, NoOpeningRun))
            // With the opening run live as well, because that window is an id
            // comparison and level 0 is the id most likely to slip under it.
            assertFalse(LevelCurve.opensWithStarterDog(levelId, ShippedLevelsPerBand, ShippedOpeningLevels))
        }
    }

    // ---- SD-113: the unbroken opening run. ---------------------------------

    @Test
    fun theOpeningRunCoversEveryLevelUpToItsCount() {
        (1..ShippedOpeningLevels).forEach { levelId ->
            assertTrue(
                LevelCurve.opensWithStarterDog(levelId, NoBandWindow, ShippedOpeningLevels),
                "level $levelId is inside the opening run and should carry a dog on the run alone",
            )
        }
    }

    @Test
    fun theFirstBoardThatOpensEmptyIsTheOneAfterTheRun() {
        // The number the report was about. Per-band alone put this at level 4.
        assertEquals(
            FirstEmptyBoard,
            (1..LevelCurve.campaignShape.size).first {
                !LevelCurve.opensWithStarterDog(it, ShippedLevelsPerBand, ShippedOpeningLevels)
            },
        )
    }

    @Test
    fun theOpeningRunDoesNotFollowTheGridAndTheBandWindowStillDoes() {
        // Two windows, not one widened. 17 is one past the run and not near a
        // band start, so only the run could have covered it and it does not.
        // 41 is a band start well past the run, so only the band window can
        // cover it and it still does.
        assertFalse(LevelCurve.opensWithStarterDog(FirstEmptyBoard, ShippedLevelsPerBand, ShippedOpeningLevels))
        assertTrue(LevelCurve.opensWithStarterDog(41, ShippedLevelsPerBand, ShippedOpeningLevels))
        assertFalse(LevelCurve.opensWithStarterDog(41, NoBandWindow, ShippedOpeningLevels))
    }

    @Test
    fun theRunIsNotNarrowedByTheBandItEndsIn() {
        // The 4x4 band is ten levels long and the run is sixteen. A run that
        // stopped at the band boundary would leave 11 to 16 empty, which is the
        // stretch the report was about.
        (11..16).forEach { levelId ->
            assertTrue(
                LevelCurve.opensWithStarterDog(levelId, NoBandWindow, ShippedOpeningLevels),
                "level $levelId is inside the run and should not be cut off by the 4x4 band ending",
            )
        }
    }

    @Test
    fun aRunOfZeroOrLessLeavesTheBandWindowAsTheOnlySource() {
        listOf(0, -1).forEach { run ->
            assertTrue(LevelCurve.opensWithStarterDog(1, ShippedLevelsPerBand, run))
            assertFalse(LevelCurve.opensWithStarterDog(4, ShippedLevelsPerBand, run), "run $run gave level 4 a dog")
            assertFalse(LevelCurve.opensWithStarterDog(1, NoBandWindow, run), "run $run with no band window gave one")
        }
    }

    private companion object {

        /** `progression.starterDogLevelsPerBand` as shipped. */
        const val ShippedLevelsPerBand = 3

        /** `progression.starterDogOpeningLevels` as shipped. */
        const val ShippedOpeningLevels = 16

        /**
         * The first campaign level a player ever opens with nothing placed.
         * Written out rather than derived from [ShippedOpeningLevels], so the
         * off-by-one is asserted and not assumed.
         */
        const val FirstEmptyBoard = 17

        /** Isolates one window by switching the other off. */
        const val NoOpeningRun = 0
        const val NoBandWindow = 0

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
