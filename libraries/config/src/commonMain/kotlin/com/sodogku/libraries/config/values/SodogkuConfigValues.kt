package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue

/**
 * Every remote config key the game declares, in one list.
 *
 * At runtime nothing reads this: each value contributes itself to the graph's
 * `Set<QaConfigValue>` through anvil multibinding and is injected directly where
 * it is used. The list exists because the *test* for SPEC section 4.2's first
 * hard constraint — every declared key has a bundled fallback — cannot ask the DI
 * graph for that set outside an app. So the values are enumerable two ways, and
 * `FallbackConfigCompletenessTest` checks this list against the bundled fallback
 * map.
 *
 * That leaves one seam: a new value class that is contributed to DI but never
 * added here is invisible to the test. Keep each area's list in the same file as
 * the classes it names so the omission is visible in the diff that creates it.
 *
 * The `telemetry.*` keys are the exception and are deliberately absent — they are
 * declared in `:libraries:telemetry:impl` (`TelemetryConfigValues`), which this
 * module cannot depend on. Their fallbacks are covered separately.
 */
object SodogkuConfigValues {

    /**
     * Constructs one instance of every declared value against [appConfigMap].
     * Instances are cheap and stateless: they resolve their value from the map on
     * each read, so a throwaway instance is a legitimate way to ask "what is this
     * key's path and default".
     */
    fun all(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> =
        adsConfigValues(appConfigMap) +
            progressionConfigValues(appConfigMap) +
            scoringConfigValues(appConfigMap) +
            dailyConfigValues(appConfigMap) +
            paywallConfigValues(appConfigMap) +
            legalConfigValues(appConfigMap) +
            appConfigValues(appConfigMap) +
            featureFlagConfigValues(appConfigMap)

    /** Every declared config path, dotted, in key-table order. */
    val paths: List<String> get() = all(NoConfigMap).map { it.path }
}

/**
 * Stands in for a config source when only a value's declared shape is wanted —
 * its path, name or default. Every lookup misses, so every value resolves to its
 * own [ConfiguredValue.default].
 */
private object NoConfigMap : AppConfigMap() {
    override val map: Map<String, Any> = emptyMap()
}
