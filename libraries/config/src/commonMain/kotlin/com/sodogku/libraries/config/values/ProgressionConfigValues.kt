package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.JsonConfigValue
import com.sodogku.libraries.config.QaConfigValue
import kotlinx.serialization.builtins.ListSerializer
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
 * Which levels pay a free Treat, as a curve. See [paysTreatAt] for the shape and
 * why it is bands rather than a formula.
 *
 * This replaces a single "every N levels" integer. A Treat places a correct dog
 * outright, which makes it the stronger of the two boosters, and paying one on a
 * fixed clock meant the late campaign handed out sixty of them to a player who
 * was already holding a stack. One number could not say "often at first, rarely
 * later", so the number became a schedule.
 *
 * Structured, so it is tuned server-side rather than by shipping a build. That
 * matters more here than for most keys: this one is the game's whole difficulty
 * relief valve, and getting the curve right is a job for play data.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoostersTreatSchedule(appConfigMap: AppConfigMap) : JsonConfigValue<List<TreatBand>>(
    appConfigMap = appConfigMap,
    serializer = ListSerializer(TreatBand.serializer()),
) {
    override val name = "Treat schedule"
    override val path = "boosters.treatSchedule"
    override val default = DefaultTreatBands

    /** Whether clearing [level] for the first time pays a Treat. */
    fun paysTreatAt(level: Int): Boolean = value.paysTreatAt(level)
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
    BoostersTreatSchedule(appConfigMap),
    BoostersAdGrantsPerDay(appConfigMap),
    BoostersProSniffsPerAttempt(appConfigMap),
    BoostersProTreatsPerAttempt(appConfigMap),
    BoostersRefillTo(appConfigMap),
)
