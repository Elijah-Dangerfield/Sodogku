package com.sodogku.libraries.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The win sheet's verdict lines up with its paws.
 *
 * The two are shown side by side, so the risk worth testing is not that a
 * boundary is off by a point — it is that they can disagree. Three paws next to
 * "you scraped that one" is the failure that would actually reach a player, and
 * it is the one a test comparing a number to a constant would miss.
 */
class StandingTest {

    @Test
    fun theVerdictNeverContradictsThePaws() {
        // Swept rather than sampled, and across every shape the campaign ships,
        // because the two thresholds are read from the same config and a
        // reordering would only show up at particular fractions of par.
        var checked = 0
        for (size in 4..10) {
            for (difficulty in 1..4) {
                val par = Scoring.parScore(size, difficulty)
                for (percent in 0..130) {
                    val score = par * percent / 100
                    val paws = Scoring.paws(score, size, difficulty, completed = true)
                    val standing = Scoring.standingFor(score, size, difficulty, completed = true)
                    checked++
                    when (standing) {
                        Standing.Flawless, Standing.Sharp ->
                            assertEquals(3, paws, "$standing at $percent% of par should be three paws")
                        Standing.Solid ->
                            assertEquals(2, paws, "Solid at $percent% of par should be two paws")
                        Standing.Scraped ->
                            assertEquals(1, paws, "Scraped at $percent% of par should be one paw")
                        null -> error("a completed run has no verdict")
                    }
                }
            }
        }
        assertTrue(checked > SWEEP_FLOOR, "only checked $checked combinations, so the sweep is not sweeping")
    }

    @Test
    fun everyVerdictIsReachable() {
        // The failure this exists for is a rating nobody can earn, which is
        // exactly what happened to the third paw: the thresholds were fine and
        // the score could not reach them. A verdict nobody sees is the same bug
        // with a different name.
        val seen = buildSet {
            for (size in 4..10) {
                for (difficulty in 1..4) {
                    val par = Scoring.parScore(size, difficulty)
                    for (percent in 0..130) {
                        Scoring.standingFor(par * percent / 100, size, difficulty, completed = true)
                            ?.let { add(it) }
                    }
                }
            }
        }

        assertEquals(Standing.entries.toSet(), seen, "these verdicts are unreachable: ${Standing.entries - seen}")
    }

    @Test
    fun anUnfinishedRunGetsNoVerdict() {
        // Not "the worst verdict". A judgement on the lose sheet is the last
        // thing anybody needs, and returning Scraped there would put one on it.
        assertNull(Scoring.standingFor(score = 5_000, size = 6, difficulty = 2, completed = false))
        assertNull(Scoring.standingFor(score = 0, size = 6, difficulty = 2, completed = false))
    }

    @Test
    fun theVerdictClimbsWithTheScore() {
        // Monotonic, so a better run can never be described worse. Holds for any
        // threshold pair, so it survives retuning.
        val ordered = (0..130).map { percent ->
            Scoring.standingFor(
                score = Scoring.parScore(8, 3) * percent / 100,
                size = 8,
                difficulty = 3,
                completed = true,
            )!!.ordinal
        }

        assertEquals(ordered.sorted(), ordered, "the verdict got worse as the score went up: $ordered")
        assertTrue(ordered.first() < ordered.last(), "the verdict never changed, so it is not measuring anything")
    }

    @Test
    fun beatingParOutrightIsItsOwnVerdict() {
        // Par prices every placement at the top speed multiplier but only the
        // starting combo, so a long combo can pass it. If that stops being true
        // this verdict quietly becomes unreachable, and the reachability test
        // above would be the only thing to notice.
        val par = Scoring.parScore(size = 8, difficulty = 3)

        assertEquals(Standing.Flawless, Scoring.standingFor(par, 8, 3, completed = true))
        assertEquals(Standing.Sharp, Scoring.standingFor(par - 1, 8, 3, completed = true))
    }

    private companion object {
        /** 7 sizes by 4 tiers by 131 steps, less a margin for arithmetic. */
        const val SWEEP_FLOOR = 3_000
    }
}
