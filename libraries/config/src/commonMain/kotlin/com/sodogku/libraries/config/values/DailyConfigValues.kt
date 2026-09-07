package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.FlagConfigValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Kill switch for the Daily Challenge. On by default and expected to stay on: the
 * daily is the strongest retention mechanic in the genre and it runs entirely off
 * the bundled pool, so a config outage has no reason to take it away. The switch
 * exists for a content problem — a board that turns out to be broken or
 * offensive — not for load shedding.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class DailyEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Daily challenge enabled"
    override val path = "daily.enabled"
    override val default = true
}

/**
 * Streak freezes a player may use per calendar month, each covering one missed
 * day for a rewarded ad. Two is scarce enough that the streak still means
 * something and forgiving enough that one bad week does not end a 90-day run.
 * This is the single most reliable ad impression in the app.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class DailyFreezesPerMonth(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Streak freezes per month"
    override val path = "daily.freezesPerMonth"
    override val default = 2
}

/**
 * Shifts the selection into the 730-board daily pool
 * (`daysSinceEpoch(localDate) + poolOffset` modulo pool size). Zero until the
 * pool wraps, at which point moving the offset re-orders which boards come round
 * next without shipping a pack. Also the escape hatch if a specific day's board
 * has to be skipped.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class DailyPoolOffset(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Daily pool offset"
    override val path = "daily.poolOffset"
    override val default = 0
}

/** Every `daily.*` value. Registered in [SodogkuConfigValues]. */
fun dailyConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    DailyEnabled(appConfigMap),
    DailyFreezesPerMonth(appConfigMap),
    DailyPoolOffset(appConfigMap),
)
