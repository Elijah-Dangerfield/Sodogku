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
    fun everyVerdictIsReachableByActuallyPlayingABoard() {
        // Rewritten, because the version that shipped could not fail. It swept
        // `par * percent / 100` up to 130% and asked how each number was
        // classified — which proves the arithmetic and says nothing about
        // whether a run can produce those numbers. It could not: `Flawless`
        // meant "at or above par", and par is the formula's exact ceiling, so
        // only a board solved in zero elapsed milliseconds ever reached it. A
        // rating nobody can earn is the third-paw bug with a different name, and
        // the test written to catch that bug had the same hole in it.
        //
        // So this one plays. Every verdict below comes out of `placement` and
        // `complete`, the same calls the game makes.
        // Swept rather than sampled, over the two things a player actually
        // varies: how fast they move and how many bones they spend. Hand-picked
        // runs would need re-picking every time a coefficient moves, and picking
        // them is how the last version of this ended up proving nothing.
        val seen = buildSet {
            for (size in 4..10) {
                for (difficulty in 1..4) {
                    for (perMoveMs in listOf(1L, 1_000L, 3_000L, 10_000L, 60_000L)) {
                        for (strikes in 0 until ScoringConfig.MAX_LIVES) {
                            add(play(size, difficulty, perMoveMs, strikes))
                        }
                    }
                }
            }
        }

        assertEquals(Standing.entries.toSet(), seen, "these verdicts are unreachable: ${Standing.entries - seen}")
    }

    @Test
    fun aRunAtHumanSpeedCanStillBeFlawless() {
        // The specific regression. One millisecond a move is already impossible;
        // the old definition needed *zero*. If Flawless ever again depends on
        // beating par rather than on keeping every bone, this fails.
        val quick = play(size = 8, difficulty = 3, perMoveMs = 250)

        assertEquals(Standing.Flawless, quick, "a clean 250ms-a-move run was not flawless")
    }

    @Test
    fun aBoneSpentCostsTheFlawlessVerdictAndNothingElse() {
        // Flawless is the only verdict that reads lives. A strike should drop
        // this run one step, not to the bottom.
        val clean = play(size = 8, difficulty = 3, perMoveMs = 250)
        val struck = play(size = 8, difficulty = 3, perMoveMs = 250, strikes = 1)

        assertEquals(Standing.Flawless, clean)
        assertEquals(Standing.Sharp, struck)
    }

    @Test
    fun parIsExactlyWhatAPerfectRunScores() {
        // The invariant the old KDoc got wrong, pinned so prose and code cannot
        // drift again. Par claims to be the formula's ceiling; this is the run
        // that reaches it. `aPerfectRunClearsPar` asserted only three paws, so
        // par could have sat 17% above any achievable score without failing.
        for (size in 4..10) {
            var card = ScoreCard.Empty
            repeat(size) { card = Scoring.placement(card, size, millisSinceLastPlacement = 0).card }
            card = Scoring.complete(card, size, difficulty = 3, livesRemaining = ScoringConfig.MAX_LIVES)

            assertEquals(
                Scoring.parScore(size, 3),
                card.total,
                "par is not the ceiling on a ${size}x$size board",
            )
        }
    }

    /**
     * A finished run at a given pace, rated the way the game rates it.
     *
     * [strikes] both spends bones and breaks the combo, because a strike does
     * both and modelling only the bone overstates the score. That is what the
     * first version of this helper got wrong, and the reachability test above
     * caught it: without the combo break, no run was bad enough to be Scraped.
     */
    private fun play(
        size: Int,
        difficulty: Int,
        perMoveMs: Long,
        strikes: Int = 0,
    ): Standing {
        var card = ScoreCard.Empty
        repeat(size) { index ->
            if (index < strikes) card = Scoring.strike(card)
            card = Scoring.placement(card, size, millisSinceLastPlacement = perMoveMs).card
        }
        val livesRemaining = ScoringConfig.MAX_LIVES - strikes
        card = Scoring.complete(card, size, difficulty, livesRemaining)
        return Scoring.standingFor(card.total, size, difficulty, completed = true, livesRemaining = livesRemaining)!!
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
    fun parIsNotAThresholdAnyVerdictUses() {
        // Replaces a test that asserted the opposite and was wrong on its own
        // premise ("a long combo can pass par"). Par prices the full combo ramp,
        // so nothing passes it. Kept as a guard rather than deleted: if a future
        // edit reintroduces a score-beats-par branch, this is what says no.
        val par = Scoring.parScore(size = 8, difficulty = 3)

        assertEquals(
            Scoring.standingFor(par, 8, 3, completed = true, livesRemaining = ScoringConfig.MAX_LIVES - 1),
            Scoring.standingFor(par - 1, 8, 3, completed = true, livesRemaining = ScoringConfig.MAX_LIVES - 1),
            "crossing par changed the verdict, so par is being used as a threshold again",
        )
    }

    private companion object {
        /** 7 sizes by 4 tiers by 131 steps, less a margin for arithmetic. */
        const val SWEEP_FLOOR = 3_000
    }
}
