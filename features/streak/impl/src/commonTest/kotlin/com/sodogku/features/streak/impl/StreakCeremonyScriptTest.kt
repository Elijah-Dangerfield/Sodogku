package com.sodogku.features.streak.impl

import com.sodogku.libraries.ui.components.text.countUpMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The order of the ceremony: the number first, the strip's day second.
 *
 * The handoff has the week strip's new day pop in after the number settles,
 * and "after" is a number of milliseconds decided here rather than inside a
 * `LaunchedEffect`, so it can be read. What the strip does when its turn comes
 * is `WeekStripHoldsStillTest` in `:libraries:ui`.
 */
class StreakCeremonyScriptTest {

    @Test
    fun theStripWaitsForTheWholeClimb() {
        // A longer climb pushes the strip back by exactly the extra digits.
        val short = stripPopMillis(countUpFrom = 3, value = 4)
        val long = stripPopMillis(countUpFrom = 1, value = 4)

        assertEquals(
            countUpMillis(1, 4) - countUpMillis(3, 4),
            long - short,
            "the strip's day is scheduled off something other than the number's climb",
        )
        assertTrue(long > short, "and a longer climb does not move it at all")
    }

    @Test
    fun aNumberThatDoesNotClimbStillLandsBeforeTheStrip() {
        // A restart and a lost run have nothing to count, but the number still
        // waits for the page and then thumps, and the strip waits for that.
        assertTrue(
            stripPopMillis(countUpFrom = null, value = 4) > numberStartMillis(),
            "the strip and the number landed on the same frame",
        )
        assertEquals(
            stripPopMillis(countUpFrom = null, value = 4),
            stripPopMillis(countUpFrom = 4, value = 4),
            "a run of one and a lost run are the same script: nothing to count",
        )
    }
}
