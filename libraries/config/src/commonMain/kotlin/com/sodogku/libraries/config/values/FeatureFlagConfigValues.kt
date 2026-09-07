package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.FlagConfigValue
import com.sodogku.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * One flag per shippable-but-hideable feature, so anything can be dark-launched
 * or pulled without a release.
 *
 * All four default **on**. A dark launch is a config change made deliberately
 * before the feature is ready; the bundled fallback is what a player gets on a
 * first launch with no network, and that has to be the whole game. Defaulting a
 * feature off would mean an install that never reaches the server is permanently
 * missing it.
 */

/** The Daily Challenge tab and map card. Separate from `daily.enabled`, which kills the mechanic. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class FeatureDailyChallenge(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Feature: daily challenge"
    override val path = "features.dailyChallenge"
    override val default = true
}

/** Achievements screen and the unlock toasts that go with it. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class FeatureAchievements(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Feature: achievements"
    override val path = "features.achievements"
    override val default = true
}

/**
 * Share sheet and the rendered result card. Worth its own switch because sharing
 * is the one feature that puts our artwork on someone else's timeline.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class FeatureSharing(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Feature: sharing"
    override val path = "features.sharing"
    override val default = true
}

/**
 * The Sniff and Treat bar on the board. Off hides the buttons and the economy
 * with them; bones and the three-strike rule are game rules, not a feature, and
 * are unaffected.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class FeatureBoosters(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Feature: boosters"
    override val path = "features.boosters"
    override val default = true
}

/** Every `features.*` value. Registered in [SodogkuConfigValues]. */
fun featureFlagConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    FeatureDailyChallenge(appConfigMap),
    FeatureAchievements(appConfigMap),
    FeatureSharing(appConfigMap),
    FeatureBoosters(appConfigMap),
)
