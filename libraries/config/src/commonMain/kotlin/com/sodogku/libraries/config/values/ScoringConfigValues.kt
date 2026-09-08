package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.DoubleConfigValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.LongConfigValue
import com.sodogku.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The scoring coefficients, one key each, mirroring `ScoringConfig` in
 * `:libraries:scoring`. The formula *shape* stays in the binary; only these
 * numbers are tunable, which is the whole of SPEC section 4.1 applied to scoring.
 *
 * The one thing to understand before retuning any of them: **the multipliers have
 * to spread wide, not merely exist.** The paw rating is a fraction of a par score
 * derived at runtime, so if the reachable score range is narrow, every run lands
 * in the same band and the rating carries no information. A first pass used
 * gentler numbers (combo step 0.05, speed max 1.3, lives rate 0.25) and the worst
 * run a player could physically finish still scored 63% of par — above the
 * two-paw line at 0.60 — so one paw was unreachable. Compressing the range is the
 * failure mode to watch, and it is invisible until someone checks the floor.
 */

/** Points for one correct placement, before grid size and multipliers. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringBasePerPlacement(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Base points per placement"
    override val path = "scoring.basePerPlacement"
    override val default = 100
}

/**
 * Points for finishing, before size, difficulty and lives. Balanced against
 * [ScoringBasePerPlacement] so placements are roughly 60% of a good run and the
 * completion bonus the other 40%: tilt too far toward completion and a fast clean
 * solve scores what a slow scrappy one does, too far toward placements and
 * finishing stops mattering.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringCompletionBase(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Completion base points"
    override val path = "scoring.completionBase"
    override val default = 250
}

/**
 * Added to the combo multiplier per consecutive correct placement: 1.0, 1.08,
 * 1.16 and so on. A strike resets it to 1.0. A board has only `size` placements,
 * so a flawless 10x10 tops out near 1.7 and [ScoringComboMax] is a guard against
 * a future retune rather than a number players reach.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringComboStep(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Combo step"
    override val path = "scoring.comboStep"
    override val default = 0.08
}

/** Ceiling on the combo multiplier, so a long streak on a 10x10 cannot run away. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringComboMax(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Combo max"
    override val path = "scoring.comboMax"
    override val default = 2.0
}

/**
 * How long the speed bonus takes to decay from [ScoringSpeedMaxMultiplier] to
 * 1.0, measured from the previous placement. The decay is linear rather than
 * exponential so the pressure the player feels is proportional to the clock they
 * can see.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringSpeedWindowMs(appConfigMap: AppConfigMap) : LongConfigValue(appConfigMap) {
    override val name = "Speed window (ms)"
    override val path = "scoring.speedWindowMs"
    override val default = 8_000L
}

/** The speed multiplier for an instant placement, decaying to 1.0 over the window. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringSpeedMaxMultiplier(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Speed max multiplier"
    override val path = "scoring.speedMaxMultiplier"
    override val default = 1.6
}

/**
 * What each surviving life adds to the completion bonus. At 0.5 a no-strike clear
 * multiplies the bonus by 2.5 where a one-life-left clear gets 1.5, and that gap
 * is a large part of what separates a three-paw finish from a one-paw one.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringLivesBonusRate(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Lives bonus rate"
    override val path = "scoring.livesBonusRate"
    override val default = 0.5
}

/**
 * What each difficulty tier above 1 adds to the completion bonus. Difficulty is
 * deduction depth (1 to 5), so a tier-5 board pays 1.8x a tier-1 board of the
 * same size.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringDifficultyBonusRate(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Difficulty bonus rate"
    override val path = "scoring.difficultyBonusRate"
    override val default = 0.2
}

/**
 * What one booster spent during an attempt costs the score it banks.
 *
 * Multiplicative, so at 0.15 one sniff banks 0.85 of the run and two bank 0.72,
 * and no number of them can drive a score negative. Set it to 0 to make hints
 * free again, which is also the direction a mistyped value falls: a non-numeric
 * string resolves to the default rather than to a cost nobody chose.
 *
 * It deliberately does **not** move the paw rating — see `Scoring.afterBoosters`
 * for why, and note that the tutorial itself asks the player to spend a sniff
 * and a treat.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringBoosterPenaltyRate(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Booster penalty rate"
    override val path = "scoring.boosterPenaltyRate"
    override val default = 0.15
}

/**
 * Fraction of par at which the second paw is awarded. Finishing at all earns the
 * first, so there is no threshold for it; par is derived at runtime from size and
 * difficulty rather than stored in the pack, which is what makes retuning a
 * three-paw clear a config change instead of a content release.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringTwoPawFraction(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Two paw fraction of par"
    override val path = "scoring.twoPawFraction"
    override val default = 0.60
}

/** Fraction of par at which the third paw is awarded. See [ScoringTwoPawFraction]. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringThreePawFraction(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Three paw fraction of par"
    override val path = "scoring.threePawFraction"
    override val default = 0.85
}

/**
 * Combined-multiplier cutoff for the "Nice" floating praise. The four cutoffs are
 * read against `comboMultiplier × speedMultiplier`, so 1.2 fires on a placement
 * that was either quick or part of a streak, and [ScoringPerfectPraiseAt] at 2.3
 * needs both at once.
 *
 * Praise is cosmetic. It is in config anyway because how often the board shouts
 * at the player is a feel question that wants tuning against real sessions.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringNicePraiseAt(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Praise: Nice at"
    override val path = "scoring.nicePraiseAt"
    override val default = 1.2
}

/** Combined-multiplier cutoff for "Great". See [ScoringNicePraiseAt]. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringGreatPraiseAt(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Praise: Great at"
    override val path = "scoring.greatPraiseAt"
    override val default = 1.5
}

/** Combined-multiplier cutoff for "Excellent". See [ScoringNicePraiseAt]. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringExcellentPraiseAt(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Praise: Excellent at"
    override val path = "scoring.excellentPraiseAt"
    override val default = 1.9
}

/** Combined-multiplier cutoff for "Perfect". See [ScoringNicePraiseAt]. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ScoringPerfectPraiseAt(appConfigMap: AppConfigMap) : DoubleConfigValue(appConfigMap) {
    override val name = "Praise: Perfect at"
    override val path = "scoring.perfectPraiseAt"
    override val default = 2.3
}

/** Every `scoring.*` value. Registered in [SodogkuConfigValues]. */
fun scoringConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    ScoringBasePerPlacement(appConfigMap),
    ScoringCompletionBase(appConfigMap),
    ScoringComboStep(appConfigMap),
    ScoringComboMax(appConfigMap),
    ScoringSpeedWindowMs(appConfigMap),
    ScoringSpeedMaxMultiplier(appConfigMap),
    ScoringLivesBonusRate(appConfigMap),
    ScoringDifficultyBonusRate(appConfigMap),
    ScoringBoosterPenaltyRate(appConfigMap),
    ScoringTwoPawFraction(appConfigMap),
    ScoringThreePawFraction(appConfigMap),
    ScoringNicePraiseAt(appConfigMap),
    ScoringGreatPraiseAt(appConfigMap),
    ScoringExcellentPraiseAt(appConfigMap),
    ScoringPerfectPraiseAt(appConfigMap),
)
