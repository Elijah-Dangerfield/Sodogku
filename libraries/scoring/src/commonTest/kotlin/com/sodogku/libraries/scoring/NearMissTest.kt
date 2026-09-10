package com.sodogku.libraries.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "You were N points off another paw", and the four cases where saying nothing
 * is the right answer.
 *
 * The whole value of this is that it does not lie. A message telling a player
 * what would have earned them more, which then does not, costs more trust than
 * the message was ever worth.
 */
class NearMissTest {

    @Test
    fun aRunJustUnderTheNextRungReportsTheGap() {
        val par = Scoring.parScore(Size, Difficulty)
        val needed = (par * config.threePawFraction).toInt()
        val score = needed - 10

        val miss = Scoring.nearMiss(score, Size, Difficulty, completed = true)

        assertNotNull(miss)
        assertEquals(Scoring.THREE_PAWS, miss.nextPaw)
        assertEquals(10, miss.pointsShort)
    }

    @Test
    fun theGapIsToTheNextRungAndNotToTheTop() {
        // A one-paw run is told about two paws, not about five. Pointing at the
        // top of the ladder from the bottom is not encouragement.
        val par = Scoring.parScore(Size, Difficulty)
        val score = (par * config.twoPawFraction).toInt() - 5

        val miss = Scoring.nearMiss(score, Size, Difficulty, completed = true)

        assertEquals(Scoring.TWO_PAWS, miss?.nextPaw)
    }

    @Test
    fun aRunThatEarnedEveryPawIsToldNothing() {
        val par = Scoring.parScore(Size, Difficulty)

        assertNull(Scoring.nearMiss(par, Size, Difficulty, completed = true))
    }

    @Test
    fun anUnfinishedRunIsToldNothing() {
        val par = Scoring.parScore(Size, Difficulty)

        assertNull(Scoring.nearMiss(par / 2, Size, Difficulty, completed = false))
    }

    @Test
    fun aRunNowhereNearTheNextRungIsToldNothing() {
        // "You were 2,000 points off" is a scoreline, not encouragement.
        val par = Scoring.parScore(Size, Difficulty)
        val score = (par * config.twoPawFraction).toInt() - (par / 4)

        assertNull(Scoring.nearMiss(score, Size, Difficulty, completed = true))
    }

    @Test
    fun theGapNeverExceedsTheWindowItIsGatedOn() {
        // Swept, because the gate and the number come from the same arithmetic
        // and an off-by-one in either would only show at particular fractions.
        val par = Scoring.parScore(Size, Difficulty)
        var reported = 0

        for (percent in 0..100) {
            val miss = Scoring.nearMiss(par * percent / 100, Size, Difficulty, completed = true)
                ?: continue
            reported++
            assertTrue(miss.pointsShort > 0, "reported a gap of ${miss.pointsShort} at $percent%")
            assertTrue(
                miss.pointsShort <= par / 10,
                "reported ${miss.pointsShort} points at $percent%, which is not a near miss",
            )
            assertTrue(miss.nextPaw in Scoring.TWO_PAWS..Scoring.MAX_PAWS)
        }

        assertTrue(reported > 0, "nothing was ever reported, so this proves nothing")
    }

    private companion object {
        const val Size = 8
        const val Difficulty = 3
        val config = ScoringConfig.Default
    }
}
