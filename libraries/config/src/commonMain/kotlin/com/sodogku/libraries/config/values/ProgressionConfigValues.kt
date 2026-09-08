package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Skips a player may take per day, Pro included. The cap is on everyone on
 * purpose: without it a Pro player skips to level 500 in an afternoon and has
 * nothing left to play.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ProgressionSkipsPerDay(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Skips per day"
    override val path = "progression.skipsPerDay"
    override val default = 3
}

/**
 * Failed attempts on a level before Skip is offered. Two is late enough that the
 * option reads as a rescue rather than an invitation to stop thinking, and early
 * enough that nobody is stuck on one board for a whole session.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ProgressionSkipAfterFailedAttempts(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Skip after failed attempts"
    override val path = "progression.skipAfterFailedAttempts"
    override val default = 2
}

/**
 * How many unplayed levels are visible as silhouettes past the current one on the
 * level map; everything beyond is a dimmed placeholder. Purely a progressive-
 * disclosure dial, which is exactly why it is remote — widening or narrowing the
 * ladder is the kind of thing worth trying against retention data rather than
 * arguing about before release.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ProgressionLookaheadCount(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Level map lookahead"
    override val path = "progression.lookaheadCount"
    override val default = 5
}

/**
 * Sniffs a brand-new player starts with. Three matches the bone count and the
 * refill size: the three consumables deliberately share one shape, because three
 * different economies would be three things to learn before the puzzle.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoostersStartingSniffs(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Starting sniffs"
    override val path = "boosters.startingSniffs"
    override val default = 3
}

/** Treats a brand-new player starts with. Three, like everything else. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoostersStartingTreats(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Starting treats"
    override val path = "boosters.startingTreats"
    override val default = 3
}

/**
 * Levels between free Treat grants. A Treat places a correct dog outright, which
 * makes it the stronger of the two boosters, so it is granted on a slower clock
 * than the Sniff. Five levels is slow enough that holding a few means something
 * and fast enough that a stuck player can see the next one coming.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoostersTreatEveryNLevels(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Treat every N levels"
    override val path = "boosters.treatEveryNLevels"
    override val default = 5
}

/**
 * Rewarded-ad booster grants a player may take per day. This caps the *refill*,
 * not the holding — a stash earned from clearing levels is never reduced by it.
 * Five is a ceiling on ad-farming, not on play.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoostersAdGrantsPerDay(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Ad booster grants per day"
    override val path = "boosters.adGrantsPerDay"
    override val default = 5
}

/** Sniffs Pro starts every attempt with, refreshed per attempt rather than per level. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoostersProSniffsPerAttempt(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Pro sniffs per attempt"
    override val path = "boosters.proSniffsPerAttempt"
    override val default = 3
}

/** Treats Pro starts every attempt with. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoostersProTreatsPerAttempt(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Pro treats per attempt"
    override val path = "boosters.proTreatsPerAttempt"
    override val default = 3
}

/**
 * What one rewarded ad tops a consumable up to.
 *
 * A *floor*, never a cap: level rewards can push a holding above it, and a
 * refill leaves those alone. So this is the number that decides how generous the
 * ad is, not how much a player is allowed to own.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoostersRefillTo(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Ad refills up to"
    override val path = "boosters.refillTo"
    override val default = 3
}

/** Every `progression.*` and `boosters.*` value. Registered in [SodogkuConfigValues]. */
fun progressionConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    ProgressionSkipsPerDay(appConfigMap),
    ProgressionSkipAfterFailedAttempts(appConfigMap),
    ProgressionLookaheadCount(appConfigMap),
    BoostersStartingSniffs(appConfigMap),
    BoostersStartingTreats(appConfigMap),
    BoostersTreatEveryNLevels(appConfigMap),
    BoostersAdGrantsPerDay(appConfigMap),
    BoostersProSniffsPerAttempt(appConfigMap),
    BoostersProTreatsPerAttempt(appConfigMap),
    BoostersRefillTo(appConfigMap),
)
