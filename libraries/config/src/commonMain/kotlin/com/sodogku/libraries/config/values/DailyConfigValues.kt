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
 * How far back a streak restore reaches: the longest run of consecutive missed
 * days it will bridge in one go.
 *
 * Three is the outer edge of "life happened" — a weekend away, a flight, a bug
 * that put someone in bed. Four is a holiday, and a player on holiday has stopped
 * playing rather than missed a day, so handing them a 90-day streak makes the
 * number a lie about them. It is also about as far back as anyone still remembers
 * what their streak was.
 *
 * Set to 1 to turn restores off entirely: a one-day gap is the freeze's, so a
 * reach of one leaves the restore nothing it is allowed to cover.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class DailyRestoreMaxDays(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Streak restore reach (days)"
    override val path = "daily.restoreMaxDays"
    override val default = 3
}

/**
 * Days a streak restore may bridge per calendar month, counted against the month
 * each bridged day falls in.
 *
 * Denominated in days rather than restores because days are what is on disk and a
 * restore is not: two restores whose runs end up adjacent leave rows
 * indistinguishable from one longer restore, so a count of restores would
 * under-report and the cap would leak.
 *
 * Three is one restore of the maximum size, which is the intent — the restore is
 * the once-a-month hammer, and the freeze (two a month, one day each) is the
 * everyday tool. Raise to six to allow two. Zero is the kill switch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class DailyRestoreDaysPerMonth(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Streak restore days per month"
    override val path = "daily.restoreDaysPerMonth"
    override val default = 3
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
    DailyRestoreMaxDays(appConfigMap),
    DailyRestoreDaysPerMonth(appConfigMap),
    DailyPoolOffset(appConfigMap),
)
