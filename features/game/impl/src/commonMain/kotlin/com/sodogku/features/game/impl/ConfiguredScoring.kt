package com.sodogku.features.game.impl

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
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.scoring.ScoringConfig
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The fourteen `scoring.*` keys, assembled into the [ScoringConfig] the formula
 * takes.
 *
 * Every key is resolved on each call rather than captured into a field at
 * construction. SPEC 4.2 wants a config change to land within the hour rather
 * than within the session, and these are singletons that outlive every board.
 *
 * **An invalid set falls back to [ScoringConfig.Default] whole, not field by
 * field.** `ScoringConfig`'s `init` throws on a bad coefficient, and it has to
 * — a negative `basePerPlacement` made par negative, which handed every player
 * three paws for scoring zero. But a throw here would be a crash on the tap
 * that placed a dog, so it is caught. Rebuilding the config field by field,
 * keeping the remote values that validate and defaulting the rest, was
 * rejected: two of the rules are about *pairs* (`threePawFraction` against
 * `twoPawFraction`), the coefficients were balanced against each other, and a
 * half-remote blend is a combination nobody chose and nobody could reproduce
 * from the console. The shipped set is the one arrangement known to be sane.
 */
@Inject
@SingleIn(AppScope::class)
class ConfiguredScoring(
    private val basePerPlacement: ScoringBasePerPlacement,
    private val completionBase: ScoringCompletionBase,
    private val comboStep: ScoringComboStep,
    private val comboMax: ScoringComboMax,
    private val speedWindowMs: ScoringSpeedWindowMs,
    private val speedMaxMultiplier: ScoringSpeedMaxMultiplier,
    private val livesBonusRate: ScoringLivesBonusRate,
    private val difficultyBonusRate: ScoringDifficultyBonusRate,
    private val twoPawFraction: ScoringTwoPawFraction,
    private val threePawFraction: ScoringThreePawFraction,
    private val nicePraiseAt: ScoringNicePraiseAt,
    private val greatPraiseAt: ScoringGreatPraiseAt,
    private val excellentPraiseAt: ScoringExcellentPraiseAt,
    private val perfectPraiseAt: ScoringPerfectPraiseAt,
) {

    /**
     * The coefficients as they stand right now.
     *
     * Call this **once per scoring decision** and pass the result on. A win
     * compares a score against a par derived from the same numbers, and reading
     * the config twice across that comparison could rate a run against
     * coefficients it was never played under.
     */
    operator fun invoke(): ScoringConfig = Catching {
        ScoringConfig(
            basePerPlacement = basePerPlacement(),
            completionBase = completionBase(),
            comboStep = comboStep(),
            comboMax = comboMax(),
            speedWindowMs = speedWindowMs(),
            speedMaxMultiplier = speedMaxMultiplier(),
            livesBonusRate = livesBonusRate(),
            difficultyBonusRate = difficultyBonusRate(),
            twoPawFraction = twoPawFraction(),
            threePawFraction = threePawFraction(),
            nicePraiseAt = nicePraiseAt(),
            greatPraiseAt = greatPraiseAt(),
            excellentPraiseAt = excellentPraiseAt(),
            perfectPraiseAt = perfectPraiseAt(),
        )
    }
        .logOnFailure { "Remote scoring coefficients are invalid; scoring on the shipped set" }
        .getOrDefault(ScoringConfig.Default)
}
