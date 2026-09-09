package com.sodogku.libraries.config.impl.model

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.impl.serialization.ConfigJsonConverter
import com.sodogku.libraries.config.mergeWith
import com.sodogku.libraries.config.values.AdsFailureMode
import com.sodogku.libraries.config.values.AdsRewardedPlacements
import com.sodogku.libraries.config.values.DefaultTreatBands
import com.sodogku.libraries.config.values.asFallbackConfig
import com.sodogku.libraries.config.values.PaywallTriggers
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logging.KLog
import kotlinx.coroutines.runBlocking
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import sodogku.libraries.config.impl.generated.resources.Res

/**
 * The config the app runs on when there is no other: first launch, no network,
 * server never comes back. SPEC section 4.2 makes this a hard constraint rather
 * than a nicety — the app has to be fully playable, correctly monetized and
 * legally compliant off this map alone, forever.
 *
 * Two layers, lowest first:
 *
 * 1. [BundledConfigDefaults], compiled in. Always complete, cannot fail to load.
 * 2. `files/fallback_app_config.json`, a resource, layered on top. It exists so a
 *    build can ship a different starting point (a beta cohort, a QA build)
 *    without touching Kotlin, and is empty in the shipped app.
 *
 * The resource is read through [Catching] on purpose: a missing or malformed
 * bundled file must degrade to the compiled defaults rather than leave the app
 * with no config at all, which is precisely the state this class exists to
 * prevent.
 */
@SingleIn(AppScope::class)
open class FallbackConfigMap @Inject constructor(
    private val converter: ConfigJsonConverter,
) : AppConfigMap() {
    private val logger = KLog.withTag("FallbackConfigMap")

    suspend fun load(): String = Res.readBytes("files/fallback_app_config.json").decodeToString()

    override val map: Map<String, *> by lazy {
        val overlay = Catching { converter.decodeToMap(runBlocking { load() }).getOrThrow() }
            .onFailure { error -> logger.e(error) { "Unable to read bundled fallback config" } }
            .getOrDefault(emptyMap())

        BundledConfigDefaults.mergeWith(overlay) ?: BundledConfigDefaults
    }
}

/**
 * Every declared config key with its shipped value, nested the way the server
 * serves it.
 *
 * This is a second, independent statement of the numbers each `ConfiguredValue`
 * already declares as its `default`, and the duplication is the point:
 * `FallbackConfigCompletenessTest` fails if a key is missing from here or if the
 * two disagree. A key declared with no fallback is the failure SPEC section 4.2
 * names first, and it is silent at runtime — the value quietly resolves to its
 * own default and nobody finds out the map was incomplete.
 *
 * The `telemetry.*` block is carried here even though those values are declared
 * in `:libraries:telemetry:impl`: the fallback map has to be complete across the
 * whole app, not just the keys this module can see.
 */
internal val BundledConfigDefaults: Map<String, Any> = mapOf(
    "ads" to mapOf(
        "enabled" to true,
        "newUserGraceLevels" to 5,
        "newUserGraceMinutes" to 5,
        "failureMode" to AdsFailureMode.CONTINUE,
        "offlineGraceLevels" to 3,
        "offlineGraceMinutes" to 20,
        "rewardedPlacements" to AdsRewardedPlacements.AllPlacementsOn,
    ),
    "progression" to mapOf(
        "skipsPerDay" to 3,
        "skipAfterFailedAttempts" to 2,
        "lookaheadCount" to 5,
    ),
    "boosters" to mapOf(
        "startingSniffs" to 3,
        "startingTreats" to 3,
        "treatSchedule" to DefaultTreatBands.asFallbackConfig(),
        "adGrantsPerDay" to 5,
        "proSniffsPerAttempt" to 3,
        "proTreatsPerAttempt" to 3,
        "refillTo" to 3,
    ),
    "scoring" to mapOf(
        "basePerPlacement" to 10,
        "completionBase" to 25,
        "comboStep" to 0.08,
        "comboMax" to 2.0,
        "speedWindowMs" to 8_000L,
        "speedMaxMultiplier" to 1.6,
        "livesBonusRate" to 0.5,
        "difficultyBonusRate" to 0.2,
        "boosterPenaltyRate" to 0.15,
        "twoPawFraction" to 0.60,
        "threePawFraction" to 0.85,
        "nicePraiseAt" to 1.2,
        "greatPraiseAt" to 1.5,
        "excellentPraiseAt" to 1.9,
        "perfectPraiseAt" to 2.3,
    ),
    "daily" to mapOf(
        "enabled" to true,
        "freezesPerMonth" to 2,
        "restoreMaxDays" to 3,
        "restoreDaysPerMonth" to 3,
        "poolOffset" to 0,
    ),
    "paywall" to mapOf(
        "triggers" to PaywallTriggers.DefaultTriggers,
        "adStandInEnabled" to true,
        "offlineBlockEnabled" to true,
        "sessionCap" to 2,
    ),
    "legal" to mapOf(
        "termsVersion" to 1,
        "termsUrl" to "https://elijah-dangerfield.github.io/Sodogku/terms.html",
        "privacyVersion" to 1,
        "privacyUrl" to "https://elijah-dangerfield.github.io/Sodogku/privacy.html",
        "forceReacceptBelow" to 0,
    ),
    // The upgrade gates live under the namespace the admin console already
    // edits, so the kill switch is wired end to end. See AppMinSupportedVersion.
    "upgrade" to mapOf(
        "minSupportedVersionCode" to 0,
        "softUpdateVersionCode" to 0,
        "maintenanceMode" to "off",
        "maintenanceMessage" to "",
    ),
    "app" to mapOf(
        "reviewPromptAfterLevel" to 10,
    ),
    "features" to mapOf(
        "dailyChallenge" to true,
        "achievements" to true,
        "sharing" to true,
        "boosters" to true,
    ),
    "telemetry" to mapOf(
        "appEventsEnabled" to true,
        "appEventsSampleRate" to 1.0,
        "klogForwardingEnabled" to true,
    ),
)
