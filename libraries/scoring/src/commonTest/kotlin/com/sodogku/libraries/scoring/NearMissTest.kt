package com.sodogku.libraries.scoring

import kotlin.math.ceil
import kotlin.math.floor
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
        // `needed` is the ceiling, not the floor, and that is the whole of the
        // fix this test moved for. It used to compute the rung the same way the
        // code did, with `.toInt()`, so it agreed with the bug and asserted 10
        // for a gap that was really 11.
        val par = Scoring.parScore(Size, Difficulty)
        val needed = ceil(par * config.threePawFraction).toInt()
        val score = needed - 10

        val miss = Scoring.nearMiss(score, Size, Difficulty, completed = true)

        assertNotNull(miss)
        assertEquals(Scoring.THREE_PAWS, miss.nextPaw)
        assertEquals(10, miss.pointsShort)
    }

    @Test
    fun scoringWhatTheGapAskedForEarnsTheRungItNamed() {
        // The promise the number makes, swept over every shape the game ships
        // and every score that gets a line at all. A player who is told they
        // were N points off and scores exactly N more has to end up on the rung
        // they were pointed at, or the message was a lie.
        //
        // It failed on 221 of the 224 (size, tier, placements, rung) combinations
        // in the campaign, because `par * fraction` is a whole number on only 3
        // of them and the gap was computed from the floor. One point light, every
        // time, on every board but three.
        var checked = 0
        val failures = mutableListOf<String>()

        everyShape { size, difficulty, placements, shape ->
            val par = Scoring.parScore(size, difficulty, placements)
            for (percent in 0..100) {
                val score = par * percent / 100
                val miss = Scoring.nearMiss(score, size, difficulty, true, placements) ?: continue
                checked++
                val rated = Scoring.paws(score + miss.pointsShort, size, difficulty, true, placements)
                if (rated != miss.nextPaw) {
                    failures += "$shape: $score was told ${miss.pointsShort} points short of " +
                        "${miss.nextPaw} paws, and ${score + miss.pointsShort} rates $rated"
                }
                // And exactly N, not merely enough. A gap quoted a point or two
                // over still lands the player on the right rung, so the check
                // above passes and the number is still wrong — which is the
                // whole complaint about the version that quoted one point under.
                val justShort = Scoring.paws(score + miss.pointsShort - 1, size, difficulty, true, placements)
                if (justShort >= miss.nextPaw) {
                    failures += "$shape: $score was told ${miss.pointsShort} points short of " +
                        "${miss.nextPaw} paws, but ${score + miss.pointsShort - 1} already rates $justShort"
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.take(TOP_FAILURES).joinToString("\n"))
        assertTrue(checked > 0, "no run anywhere was told anything, so this proves nothing")
    }

    @Test
    fun theClosestPossibleMissIsNeverPassedOverInSilence() {
        // A score of `floor(par * fraction)` is the nearest a run can come to a
        // rung without earning it, so it is the one run that most deserves a
        // line. It got none: the gap came out zero there, which reads as "you
        // already cleared it" and returns null.
        //
        // Either outcome is correct, and which one is not this function's call:
        // if the product is a whole number the score earns the paw, and if it is
        // not the score is one point short and should be told so. Silence is the
        // only wrong answer.
        var onTheLine = 0
        val failures = mutableListOf<String>()

        everyShape { size, difficulty, placements, shape ->
            val par = Scoring.parScore(size, difficulty, placements)
            RUNGS.forEach { (rung, fraction) ->
                val score = floor(par * fraction(config)).toInt()
                onTheLine++
                val earned = Scoring.paws(score, size, difficulty, true, placements)
                if (earned >= rung) return@forEach
                if (Scoring.nearMiss(score, size, difficulty, true, placements) == null) {
                    failures += "$shape: $score is the closest a run can get to $rung paws " +
                        "without earning them, and it was told nothing"
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.take(TOP_FAILURES).joinToString("\n"))
        assertTrue(onTheLine > 0, "no rung was ever landed on, so this proves nothing")
    }

    /**
     * Every shape a player can be handed: each shipped grid size, each shipped
     * tier, and both `size` placements and `size - 1` for the boards that open
     * with a starter dog. Matches `ScoringTest.everyShape`.
     */
    private fun everyShape(body: (size: Int, difficulty: Int, placements: Int, shape: String) -> Unit) {
        for (size in SmallestBoard..BiggestBoard) {
            for (difficulty in 1..ShippedMaxDifficulty) {
                for (placements in listOf(size, size - 1)) {
                    val dog = if (placements < size) ", starter dog" else ""
                    body(size, difficulty, placements, "${size}x$size tier $difficulty$dog")
                }
            }
        }
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

        const val SmallestBoard = 4
        const val BiggestBoard = 10

        /** Tier 5 is kept out of both packs, so no player ever meets one. */
        const val ShippedMaxDifficulty = 4

        /** Enough of a failure list to diagnose from without printing 224 lines. */
        const val TOP_FAILURES = 8

        /** Each rung above the first, with the fraction of par that earns it. */
        val RUNGS: List<Pair<Int, (ScoringConfig) -> Double>> = listOf(
            Scoring.TWO_PAWS to { it: ScoringConfig -> it.twoPawFraction },
            Scoring.THREE_PAWS to { it: ScoringConfig -> it.threePawFraction },
            Scoring.FOUR_PAWS to { it: ScoringConfig -> it.fourPawFraction },
            Scoring.FIVE_PAWS to { it: ScoringConfig -> it.fivePawFraction },
        )
    }
}
