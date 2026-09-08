package com.sodogku.libraries.config.values

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Treat curve pays out the way the economy assumes it does.
 *
 * This is a config value, which means the shipped defaults are only one of the
 * schedules that will ever run — a tuning pass can push any array of bands into
 * `boosters.treatSchedule` and the client will use it. So the tests split in
 * two: what the *rule* does with an arbitrary schedule, including hostile ones,
 * and what the *shipped numbers* actually pay across a 500-level campaign.
 *
 * The second half is the one that matters for the ask. "Front-loaded, then
 * sparser" is a claim about totals, and a claim about totals is only checked by
 * counting.
 */
class TreatScheduleTest {

    @Test
    fun theShippedCurveIsDenserEarlyAndThinnerLate() {
        val schedule = DefaultTreatBands

        val firstTwenty = (1..20).count { schedule.paysTreatAt(it) }
        val lastThreeHundred = (201..500).count { schedule.paysTreatAt(it) }

        // The old flat rule paid every fifth level everywhere: four across the
        // first twenty and sixty across the last three hundred.
        assertTrue(firstTwenty > FLAT_RULE_FIRST_TWENTY, "the opening should pay more than it used to, not less")
        assertTrue(
            lastThreeHundred < FLAT_RULE_LAST_THREE_HUNDRED / 2,
            "the late campaign still pays $lastThreeHundred, which is not sparse",
        )
    }

    @Test
    fun theRateOnlyEverSlows() {
        // The property the ask is really asking for. Any two adjacent stretches
        // of the campaign, and the later one pays no more often than the earlier
        // one. This holds for any monotone schedule, so it survives retuning.
        val schedule = DefaultTreatBands
        val perHundred = (0 until 5).map { hundred ->
            val range = (hundred * HUNDRED + 1)..((hundred + 1) * HUNDRED)
            range.count { schedule.paysTreatAt(it) }
        }

        assertEquals(
            perHundred.sortedDescending(),
            perHundred,
            "grants per hundred levels should never climb; got $perHundred",
        )
        assertTrue(perHundred.first() > perHundred.last(), "the curve is flat, so it is not a curve")
    }

    @Test
    fun theWholeCampaignPaysFarLessThanTheFlatRuleDid() {
        val total = (1..CAMPAIGN_LEVELS).count { DefaultTreatBands.paysTreatAt(it) }

        assertEquals(EXPECTED_TOTAL, total, "the shipped curve's payout moved; retune deliberately or fix the bands")
    }

    @Test
    fun aBandAppliesUntilTheNextOneStarts() {
        val schedule = listOf(
            TreatBand(fromLevel = 1, everyNLevels = 2),
            TreatBand(fromLevel = 10, everyNLevels = 5),
        )

        assertTrue(schedule.paysTreatAt(EIGHT), "8 is in the every-two band")
        assertFalse(schedule.paysTreatAt(TWELVE), "12 is in the every-five band, so it should not pay")
        assertTrue(schedule.paysTreatAt(FIFTEEN), "15 is a multiple of five in the every-five band")
    }

    @Test
    fun bandsOutOfOrderStillResolveCorrectly() {
        // The schedule arrives as a hand-editable JSON array from a server. An
        // implementation that walked the list and took the first match would
        // read this one backwards and pay on the wrong curve.
        val sorted = DefaultTreatBands
        val shuffled = DefaultTreatBands.reversed()

        val sortedPayouts = (1..CAMPAIGN_LEVELS).filter { sorted.paysTreatAt(it) }
        val shuffledPayouts = (1..CAMPAIGN_LEVELS).filter { shuffled.paysTreatAt(it) }

        assertEquals(sortedPayouts, shuffledPayouts)
        assertTrue(sortedPayouts.isNotEmpty(), "both schedules paid nothing, so the comparison proves nothing")
    }

    @Test
    fun nonsenseFromTheServerPaysNothingRatherThanCrashing() {
        // Every one of these is a plausible bad remote value, and the failure
        // mode being avoided is specific: `level % 0` throws, and a crash on the
        // level-complete path is worse than a missing free Treat.
        assertFalse(emptyList<TreatBand>().paysTreatAt(FIFTEEN), "an empty schedule has no rate to apply")
        assertFalse(
            listOf(TreatBand(fromLevel = 1, everyNLevels = 0)).paysTreatAt(FIFTEEN),
            "a zero rate must not divide",
        )
        assertFalse(
            listOf(TreatBand(fromLevel = 1, everyNLevels = -3)).paysTreatAt(FIFTEEN),
            "a negative rate must not pay",
        )
        assertFalse(
            DefaultTreatBands.paysTreatAt(0),
            "level 0 is below every band and is not a level anyone plays",
        )
    }

    @Test
    fun theShippedScheduleIsWellFormed() {
        // Guards the defaults themselves rather than the rule. A band added with
        // a duplicate or descending `fromLevel` would silently shadow its
        // neighbour, and a zero rate would make a whole stretch pay nothing.
        val bands = DefaultTreatBands

        assertEquals(bands.sortedBy { it.fromLevel }, bands, "the shipped bands are out of order")
        assertEquals(bands.distinctBy { it.fromLevel }.size, bands.size, "two shipped bands start at the same level")
        assertTrue(bands.all { it.everyNLevels > 0 }, "a shipped band pays never")
        assertEquals(
            bands.map { it.everyNLevels }.sorted(),
            bands.map { it.everyNLevels },
            "the shipped rates should widen with the campaign, not narrow",
        )
    }

    private companion object {
        const val CAMPAIGN_LEVELS = 500
        const val HUNDRED = 100

        /** 3, 6, 9, 12, 15, 18, then every sixth, twelfth and twenty-fifth. */
        const val EXPECTED_TOTAL = 34

        const val FLAT_RULE_FIRST_TWENTY = 4
        const val FLAT_RULE_LAST_THREE_HUNDRED = 60

        const val EIGHT = 8
        const val TWELVE = 12
        const val FIFTEEN = 15
    }
}
