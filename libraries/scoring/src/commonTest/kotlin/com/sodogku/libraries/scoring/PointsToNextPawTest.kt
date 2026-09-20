package com.sodogku.libraries.scoring

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "N more points would have earned a fourth paw", for every clear that has a
 * paw left to earn.
 *
 * The board-cleared footnote reads this and not [Scoring.nearMiss], because the
 * 2026-09 handoff draws the line on every clear short of five paws, however
 * wide the gap. So the claims here are the ungated ones: the number is right
 * on every rung of every shape, and the only silence is at the top.
 *
 * Deliberately not covered: whether a gap is close enough to be encouraging.
 * That is the near miss's question, and `NearMissTest` owns it.
 */
class PointsToNextPawTest {

    @Test
    fun aRunUnderARungIsToldTheRungAndTheGap() {
        val par = Scoring.parScore(Size, Difficulty)
        val needed = ceil(par * config.fourPawFraction).toInt()
        val score = needed - 22

        val gap = Scoring.pointsToNextPaw(score, Size, Difficulty, completed = true)

        assertNotNull(gap)
        assertEquals(Scoring.FOUR_PAWS, gap.nextPaw)
        assertEquals(22, gap.pointsShort)
    }

    @Test
    fun everyRunShortOfFivePawsIsToldExactlyWhatItNeeded() {
        // The promise the footnote makes, swept over every shape the game ships
        // and every whole-percent score that finishes: a player told they were
        // N points off and scoring exactly N more lands on the rung they were
        // pointed at, and N - 1 more does not. Both halves, because a gap quoted
        // a point over passes the first and a gap quoted a point under passes
        // the second.
        var checked = 0
        val failures = mutableListOf<String>()

        everyShape { size, difficulty, placements, shape ->
            val par = Scoring.parScore(size, difficulty, placements)
            for (percent in 0..100) {
                val score = par * percent / 100
                val earned = Scoring.paws(score, size, difficulty, true, placements)
                val gap = Scoring.pointsToNextPaw(score, size, difficulty, true, placements)
                if (earned == Scoring.MAX_PAWS) {
                    if (gap != null) failures += "$shape: $score earned every paw and was told $gap"
                    continue
                }
                checked++
                if (gap == null) {
                    failures += "$shape: $score earned $earned paws and was told nothing"
                    continue
                }
                if (gap.nextPaw != earned + 1) {
                    failures += "$shape: $score earned $earned paws and was pointed at ${gap.nextPaw}"
                }
                val rated = Scoring.paws(score + gap.pointsShort, size, difficulty, true, placements)
                if (rated != gap.nextPaw) {
                    failures += "$shape: $score was told ${gap.pointsShort} points short of " +
                        "${gap.nextPaw} paws, and ${score + gap.pointsShort} rates $rated"
                }
                val justShort = Scoring.paws(score + gap.pointsShort - 1, size, difficulty, true, placements)
                if (justShort >= gap.nextPaw) {
                    failures += "$shape: $score was told ${gap.pointsShort} points short of " +
                        "${gap.nextPaw} paws, but ${score + gap.pointsShort - 1} already rates $justShort"
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.take(TOP_FAILURES).joinToString("\n"))
        assertTrue(checked > 0, "no run anywhere was short of a paw, so this proves nothing")
    }

    @Test
    fun everyRungFromOneToFourIsReachedBySomeRunOfTheSweep() {
        // The sweep above walks scores, not rungs, so a ladder retune that
        // squeezed a rung out of the whole-percent grid would pass it without
        // ever asking about that rung. Each of the four next-paws has to show
        // up at least once or the sweep is proving less than it says.
        val pointedAt = mutableSetOf<Int>()

        everyShape { size, difficulty, placements, _ ->
            val par = Scoring.parScore(size, difficulty, placements)
            for (percent in 0..100) {
                val gap = Scoring.pointsToNextPaw(par * percent / 100, size, difficulty, true, placements)
                if (gap != null) pointedAt += gap.nextPaw
            }
        }

        assertEquals(
            (Scoring.TWO_PAWS..Scoring.FIVE_PAWS).toSet(),
            pointedAt,
            "some rung was never the next paw for any score in the sweep",
        )
    }

    @Test
    fun aRunThatEarnedEveryPawIsToldNothing() {
        // At five paws there is no footnote at all. Par itself rates five.
        val par = Scoring.parScore(Size, Difficulty)

        assertNull(Scoring.pointsToNextPaw(par, Size, Difficulty, completed = true))
    }

    @Test
    fun anUnfinishedRunIsToldNothing() {
        val par = Scoring.parScore(Size, Difficulty)

        assertNull(Scoring.pointsToNextPaw(par / 2, Size, Difficulty, completed = false))
    }

    @Test
    fun aRunNowhereNearTheNextRungIsStillToldTheGap() {
        // The one place this and the near miss part ways. A quarter of par off
        // the two-paw line is a scoreline rather than a near miss, and the
        // footnote prints it anyway because the handoff wants the number on
        // every clear.
        val par = Scoring.parScore(Size, Difficulty)
        val score = (par * config.twoPawFraction).toInt() - (par / 4)

        val gap = Scoring.pointsToNextPaw(score, Size, Difficulty, completed = true)

        assertNull(Scoring.nearMiss(score, Size, Difficulty, completed = true))
        assertNotNull(gap)
        assertEquals(Scoring.TWO_PAWS, gap.nextPaw)
        assertTrue(gap.pointsShort > par / 12, "a quarter of par off was called close")
    }

    @Test
    fun whereTheNearMissSpeaksTheTwoAgree() {
        // Same number, same rung, wherever both have something to say. The
        // near miss is the gap with a gate on it, and a gate that changed the
        // number on its way through would have two screens quoting two gaps
        // for one run.
        var agreed = 0

        everyShape { size, difficulty, placements, shape ->
            val par = Scoring.parScore(size, difficulty, placements)
            for (percent in 0..100) {
                val score = par * percent / 100
                val miss = Scoring.nearMiss(score, size, difficulty, true, placements) ?: continue
                val gap = Scoring.pointsToNextPaw(score, size, difficulty, true, placements)
                assertNotNull(gap, "$shape: $score is a near miss and not a paw gap")
                assertEquals(miss.nextPaw, gap.nextPaw, "$shape: $score is pointed at two different paws")
                assertEquals(miss.pointsShort, gap.pointsShort, "$shape: $score is quoted two different gaps")
                agreed++
            }
        }

        assertTrue(agreed > 0, "no run was ever a near miss, so this proves nothing")
    }

    /** Matches `NearMissTest.everyShape`, for the same reason it exists there. */
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

    private companion object {
        const val Size = 8
        const val Difficulty = 3
        val config = ScoringConfig.Default

        const val SmallestBoard = 4
        const val BiggestBoard = 10

        /** Tier 5 is kept out of both packs, so no player ever meets one. */
        const val ShippedMaxDifficulty = 4

        const val TOP_FAILURES = 8
    }
}
