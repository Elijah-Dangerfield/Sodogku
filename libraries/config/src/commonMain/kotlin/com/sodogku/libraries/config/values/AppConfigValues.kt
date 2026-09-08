package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.QaConfigValue
import com.sodogku.libraries.config.StringConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Version code below which the app hard-blocks and demands an update. Zero blocks
 * nobody, and that is the correct fallback: a force-update gate is the one config
 * value that can brick every install at once, so it must never be reachable by
 * accident, by a partial config, or by a fresh database with no rows.
 *
 * The `upgrade.` prefix rather than `app.` is not cosmetic. The admin console's
 * kill-switch panel and its manifest registry were already wired to this exact
 * path, so under the name the spec originally gave it the one control that has
 * to work in an emergency would have edited a key no client reads.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AppMinSupportedVersion(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Min supported version code"
    override val path = "upgrade.minSupportedVersionCode"
    override val default = 0
}

/**
 * Version code below which the app suggests an update without blocking. Same
 * zero-means-nobody rule as [AppMinSupportedVersion]; this one is the polite
 * step that usually precedes it by a release or two.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AppSoftUpdateVersion(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Soft update version code"
    override val path = "upgrade.softUpdateVersionCode"
    override val default = 0
}

/**
 * Message shown when the app is in maintenance. Shares the admin console's
 * `upgrade.` namespace for the same reason [AppMinSupportedVersion] does. Empty means no maintenance, which
 * is what a missing or unreachable config resolves to — the game is fully local,
 * so there is never a backend reason to stop someone playing.
 *
 * This is deliberately raw text rather than a string resource key: it is written
 * during an incident, in whatever wording the incident needs, and a key would
 * only ever resolve to copy that was written before the incident existed.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AppMaintenanceMessage(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Maintenance message"
    override val path = "upgrade.maintenanceMessage"
    override val default = ""
}

/**
 * Level after which the store review prompt may be asked for. Both platforms
 * ration review prompts, so the one ask should land right after a win, late
 * enough that the player has an opinion. Ten is the end of the tutorial band plus
 * a few 5x5 clears.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AppReviewPromptAfterLevel(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Review prompt after level"
    override val path = "app.reviewPromptAfterLevel"
    override val default = 10
}

/**
 * How hard the maintenance gate bites: `off`, `banner`, or `blocking`.
 *
 * Kept separate from [AppMaintenanceMessage] so an incident can put a notice in
 * front of players without stopping them playing — the game is entirely local,
 * so there is almost never a backend reason to lock someone out. `off` is the
 * fallback for the usual reason: an unreachable config must not be able to
 * close the app.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AppMaintenanceMode(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Maintenance mode"
    override val path = "upgrade.maintenanceMode"
    override val default = MAINTENANCE_OFF
    override val allowedValues = listOf(MAINTENANCE_OFF, MAINTENANCE_BANNER, MAINTENANCE_BLOCKING)

    companion object {
        const val MAINTENANCE_OFF = "off"
        const val MAINTENANCE_BANNER = "banner"
        const val MAINTENANCE_BLOCKING = "blocking"
    }
}

/** Every `app.*` and `upgrade.*` value. Registered in [SodogkuConfigValues]. */
fun appConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    AppMinSupportedVersion(appConfigMap),
    AppSoftUpdateVersion(appConfigMap),
    AppMaintenanceMessage(appConfigMap),
    AppMaintenanceMode(appConfigMap),
    AppReviewPromptAfterLevel(appConfigMap),
)
