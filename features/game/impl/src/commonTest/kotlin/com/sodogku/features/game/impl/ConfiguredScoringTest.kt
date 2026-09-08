package com.sodogku.features.game.impl

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.ScoringBasePerPlacement
import com.sodogku.libraries.config.values.ScoringComboMax
import com.sodogku.libraries.config.values.ScoringComboStep
import com.sodogku.libraries.config.values.ScoringCompletionBase
import com.sodogku.libraries.config.values.ScoringDifficultyBonusRate
import com.sodogku.libraries.config.values.ScoringExcellentPraiseAt
import com.sodogku.libraries.config.values.ScoringGreatPraiseAt
import com.sodogku.libraries.config.values.ScoringLivesBonusRate
import com.sodogku.libraries.config.values.ScoringNicePraiseAt
import com.sodogku.libraries.config.values.ScoringPerfectPraiseAt
import com.sodogku.libraries.config.values.ScoringSpeedMaxMultiplier
import com.sodogku.libraries.config.values.ScoringSpeedWindowMs
import com.sodogku.libraries.config.values.ScoringThreePawFraction
import com.sodogku.libraries.config.values.ScoringTwoPawFraction
import com.sodogku.libraries.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The fourteen `scoring.*` keys, and the one thing that can go wrong with them
 * that nothing else in the config layer can: `ScoringConfig` validates in its
 * `init` and **throws**. Every other configured value is a number the caller
 * uses as-is.
 *
 * So there are three properties here, and the middle one is the one a hardcoded
 * implementation would sail through. "The default is used when config is empty"
 * passes just as well against a function that returns `ScoringConfig.Default`
 * and never reads a key at all.
 */
class ConfiguredScoringTest {

    @Test
    fun withNoConfigTheShippedCoefficientsAreUsed() {
        assertEquals(ScoringConfig.Default, ConfiguredScoring(configOf()).invoke())
    }

    @Test
    fun everyKeyIsActuallyRead() {
        // One assertion per key, because the failure this guards against is a
        // single field left off the constructor call — which fourteen keys and
        // a copy-pasted list makes very easy. A test that only checked one
        // coefficient would pass with the other thirteen ignored.
        val tuned = ConfiguredScoring(
            configOf(
                "scoring.basePerPlacement" to 111,
                "scoring.completionBase" to 222,
                "scoring.comboStep" to 0.11,
                "scoring.comboMax" to 3.0,
                "scoring.speedWindowMs" to 4_000,
                "scoring.speedMaxMultiplier" to 2.5,
                "scoring.livesBonusRate" to 0.75,
                "scoring.difficultyBonusRate" to 0.33,
                "scoring.twoPawFraction" to 0.4,
                "scoring.threePawFraction" to 0.9,
                "scoring.nicePraiseAt" to 1.1,
                "scoring.greatPraiseAt" to 1.4,
                "scoring.excellentPraiseAt" to 1.7,
                "scoring.perfectPraiseAt" to 2.1,
            ),
        ).invoke()

        assertEquals(111, tuned.basePerPlacement)
        assertEquals(222, tuned.completionBase)
        assertEquals(0.11, tuned.comboStep)
        assertEquals(3.0, tuned.comboMax)
        assertEquals(4_000L, tuned.speedWindowMs)
        assertEquals(2.5, tuned.speedMaxMultiplier)
        assertEquals(0.75, tuned.livesBonusRate)
        assertEquals(0.33, tuned.difficultyBonusRate)
        assertEquals(0.4, tuned.twoPawFraction)
        assertEquals(0.9, tuned.threePawFraction)
        assertEquals(1.1, tuned.nicePraiseAt)
        assertEquals(1.4, tuned.greatPraiseAt)
        assertEquals(1.7, tuned.excellentPraiseAt)
        assertEquals(2.1, tuned.perfectPraiseAt)
        assertNotEquals(ScoringConfig.Default, tuned)
    }

    @Test
    fun aDroppedMinusSignDoesNotCrashTheBoard() {
        // The exact write `ScoringConfig`'s `require` was added for: a negative
        // base makes par negative, so every score clears three paws. It throws,
        // and a throw on the tap that placed a dog is a crash mid-level.
        val broken = ConfiguredScoring(configOf("scoring.basePerPlacement" to -100)).invoke()

        assertEquals(ScoringConfig.Default, broken)
    }

    @Test
    fun aPairThatContradictsItselfFallsBackToo() {
        // Not a field anything could reject on its own: both fractions are in
        // range, and only their order is wrong. It is the reason the fallback
        // is whole-set rather than per-field.
        val inverted = ConfiguredScoring(
            configOf(
                "scoring.twoPawFraction" to 0.9,
                "scoring.threePawFraction" to 0.5,
            ),
        ).invoke()

        assertEquals(ScoringConfig.Default, inverted)
    }

    @Test
    fun oneBadCoefficientDiscardsTheGoodOnesWithIt() {
        // The stated decision, pinned: a half-remote blend is a combination
        // nobody chose. If this ever starts returning 500, someone has made the
        // fallback field-by-field and the paw thresholds are being applied to
        // coefficients that were never balanced against them.
        val mixed = ConfiguredScoring(
            configOf(
                "scoring.basePerPlacement" to 500,
                "scoring.completionBase" to 0,
            ),
        ).invoke()

        assertEquals(ScoringConfig.Default, mixed)
        assertEquals(ScoringConfig.Default.basePerPlacement, mixed.basePerPlacement)
    }

    @Test
    fun aValueOfTheWrongTypeResolvesToItsDefaultRatherThanZero() {
        val typo = ConfiguredScoring(configOf("scoring.comboMax" to "banana")).invoke()

        assertEquals(ScoringConfig.Default, typo)
    }

    private fun configOf(vararg values: Pair<String, Any>): ConfiguredScoring.Companion.Nothing? = null

    private fun ConfiguredScoring(config: AppConfigMap) = ConfiguredScoring(
        ScoringBasePerPlacement(config),
        ScoringCompletionBase(config),
        ScoringComboStep(config),
        ScoringComboMax(config),
        ScoringSpeedWindowMs(config),
        ScoringSpeedMaxMultiplier(config),
        ScoringLivesBonusRate(config),
        ScoringDifficultyBonusRate(config),
        ScoringTwoPawFraction(config),
        ScoringThreePawFraction(config),
        ScoringNicePraiseAt(config),
        ScoringGreatPraiseAt(config),
        ScoringExcellentPraiseAt(config),
        ScoringPerfectPraiseAt(config),
    )
}
