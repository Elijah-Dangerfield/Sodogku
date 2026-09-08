package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.FlagConfigValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.JsonConfigValue
import com.sodogku.libraries.config.QaConfigValue
import com.sodogku.libraries.config.StringConfigValue
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Master switch for advertising. False means no ad calls at all — no preload, no
 * request, no consent prompt on behalf of the ad SDK. It exists for the day a
 * network misbehaves or a store policy question needs ads off within the hour
 * rather than within a review cycle, so read it at the point of use rather than
 * caching it at screen entry.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Ads enabled"
    override val path = "ads.enabled"
    override val default = true
}

/**
 * No ads at all before this level. Day-0 ad exposure is the biggest single driver
 * of first-session churn in this genre, and level 5 is roughly where a new player
 * has decided whether the game is for them. The grace is deliberately expressed in
 * levels *and* minutes ([AdsNewUserGraceMinutes]) because a slow player and a fast
 * player fail different halves of the same intent.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsNewUserGraceLevels(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "New user grace (levels)"
    override val path = "ads.newUserGraceLevels"
    override val default = 5
}

/** The wall-clock half of the new-user grace. See [AdsNewUserGraceLevels]. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsNewUserGraceMinutes(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "New user grace (minutes)"
    override val path = "ads.newUserGraceMinutes"
    override val default = 5
}







/**
 * What happens on the third strike. `CONTINUE` offers a rewarded ad to restore a
 * life and keep the board, which is what shipped casual puzzle games do and what
 * never strands a player. `LOCK` is the competitor's harsher model — the level
 * locks until a rewarded ad reopens it — kept behind this key so it can be A/B
 * tested without a release. We ship `CONTINUE`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsFailureMode(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Failure mode"
    override val path = "ads.failureMode"
    override val default = CONTINUE
    override val allowedValues = listOf(CONTINUE, LOCK)

    companion object {
        /** Third strike offers a rewarded continue and never blocks the player. */
        const val CONTINUE = "CONTINUE"

        /** Third strike locks the level until a rewarded ad reopens it. */
        const val LOCK = "LOCK"
    }
}

/**
 * Levels a free player may finish after an ad gate could not be served offline,
 * before the blocking screen appears. Counted from the first unservable gate, not
 * from going offline, and spent counters reset on a successful ad view rather
 * than on reconnect. Three levels or [AdsOfflineGraceMinutes], whichever comes
 * first.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsOfflineGraceLevels(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Offline grace (levels)"
    override val path = "ads.offlineGraceLevels"
    override val default = 3
}

/** The wall-clock half of the offline grace. See [AdsOfflineGraceLevels]. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsOfflineGraceMinutes(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Offline grace (minutes)"
    override val path = "ads.offlineGraceMinutes"
    override val default = 20
}

/**
 * Per-placement enable switches for rewarded ads, keyed by the placement ids in
 * SPEC section 5.3. Structured rather than one key per placement so a new
 * placement needs no new config schema.
 *
 * Read it through [isEnabled], never by indexing: an unknown or missing key
 * resolves to **enabled**. Every rewarded placement is something the player asked
 * for and gets paid for, so the failure direction is "the reward is available",
 * never "the button does nothing".
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsRewardedPlacements(appConfigMap: AppConfigMap) : JsonConfigValue<Map<String, Boolean>>(
    appConfigMap = appConfigMap,
    serializer = MapSerializer(String.serializer(), Boolean.serializer()),
) {
    override val name = "Rewarded placements"
    override val path = "ads.rewardedPlacements"
    override val default = AllPlacementsOn

    /** Whether [placementId] may show a rewarded ad. Unknown ids are enabled. */
    fun isEnabled(placementId: String): Boolean = value[placementId] ?: true

    companion object {
        const val CONTINUE_LEVEL = "continue_level"
        const val BOOSTER_GRANT = "booster_grant"
        const val SKIP_LEVEL = "skip_level"
        const val STREAK_FREEZE = "streak_freeze"

        val AllPlacementsOn: Map<String, Boolean> = mapOf(
            CONTINUE_LEVEL to true,
            BOOSTER_GRANT to true,
            SKIP_LEVEL to true,
            STREAK_FREEZE to true,
        )
    }
}

/** Every `ads.*` value, in key-table order. Registered in [SodogkuConfigValues]. */
fun adsConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    AdsEnabled(appConfigMap),
    AdsNewUserGraceLevels(appConfigMap),
    AdsNewUserGraceMinutes(appConfigMap),
    AdsFailureMode(appConfigMap),
    AdsOfflineGraceLevels(appConfigMap),
    AdsOfflineGraceMinutes(appConfigMap),
    AdsRewardedPlacements(appConfigMap),
)
