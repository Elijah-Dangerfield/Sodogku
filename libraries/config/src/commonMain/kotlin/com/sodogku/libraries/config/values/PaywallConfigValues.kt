package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.FlagConfigValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.JsonConfigValue
import com.sodogku.libraries.config.QaConfigValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Which moments may show the Pro paywall. A list rather than a flag per moment,
 * because the interesting live-ops question is which *combination* converts, and
 * `iap.paywall_shown` is tagged with the trigger so the answer is measurable.
 *
 * The default list is short on purpose. Every entry is a moment where the player
 * is already being offered a rewarded ad, so Pro reads as the alternative to
 * watching one rather than as an interruption. The offline block is the
 * highest-intent moment in the app and the only one the spec names outright.
 * Unknown ids are ignored, so the server can add a trigger the client hasn't
 * shipped a moment for without breaking anything.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class PaywallTriggers(appConfigMap: AppConfigMap) : JsonConfigValue<List<String>>(
    appConfigMap = appConfigMap,
    serializer = ListSerializer(String.serializer()),
) {
    override val name = "Paywall triggers"
    override val path = "paywall.triggers"
    override val default = DefaultTriggers

    /** Whether the paywall may be shown at [triggerId]. */
    fun isEnabled(triggerId: String): Boolean = triggerId in value

    companion object {
        /** Offline grace is spent and play is blocked until reconnect or Pro. */
        const val OFFLINE_BLOCK = "offline_block"

        /** Third strike, alongside the rewarded continue. */
        const val CONTINUE_LEVEL = "continue_level"

        /** Skip offered after repeated failed attempts, alongside the rewarded skip. */
        const val SKIP_LEVEL = "skip_level"

        val DefaultTriggers: List<String> = listOf(OFFLINE_BLOCK, CONTINUE_LEVEL, SKIP_LEVEL)
    }
}

/**
 * Whether spending the offline grace blocks play until reconnect or Pro.
 *
 * This is the one monetization key whose default *adds* a block, and it is
 * deliberate: unlimited offline play is a headline Pro benefit, so giving it away
 * for free removes a reason to buy. It only ever fires after
 * `ads.offlineGraceLevels` / `ads.offlineGraceMinutes` are spent, and only when
 * the OS reports no network at all — our own backend being down never trips it.
 * Turn it off to disable the block entirely.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class PaywallOfflineBlockEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Offline block enabled"
    override val path = "paywall.offlineBlockEnabled"
    override val default = true
}

/**
 * Whether Pro stands in for a rewarded ad that could not be filled.
 *
 * Its own switch because the alternative was dropping `paywall.sessionCap` to
 * zero, which also silences every offer we chose to make. This one is the
 * unchosen kind: it fires on somebody else's empty inventory, so it is the one
 * most likely to need turning off in a hurry and from a distance.
 *
 * Turning it off never withholds a reward. The stand-in cannot affect the
 * outcome by construction, so the switch only decides whether a sheet appears.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class PaywallAdStandInEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Pro stands in for an unfilled ad"
    override val path = "paywall.adStandInEnabled"
    override val default = true
}

/**
 * How many times the paywall may appear in one session, across all triggers. Two
 * is the difference between an offer and a nag; a player who declined twice has
 * answered.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class PaywallSessionCap(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Paywall session cap"
    override val path = "paywall.sessionCap"
    override val default = 2
}

/** Every `paywall.*` value. Registered in [SodogkuConfigValues]. */
fun paywallConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    PaywallTriggers(appConfigMap),
    PaywallOfflineBlockEnabled(appConfigMap),
    PaywallAdStandInEnabled(appConfigMap),
    PaywallSessionCap(appConfigMap),
)
