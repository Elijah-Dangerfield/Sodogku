package com.sodogku.libraries.config.impl.model

import com.sodogku.libraries.config.getValueForPath
import com.sodogku.libraries.config.values.SodogkuConfigValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC section 4.2, first hard constraint: **every declared key has a bundled
 * fallback.** The app has to be fully playable, correctly monetized and legally
 * compliant on a first launch with no network, forever, if the server never comes
 * back.
 *
 * The runtime source of truth for "every declared key" is the graph's
 * `Set<QaConfigValue>` multibinding, which a unit test has no way to resolve —
 * there is no app and no DI. [SodogkuConfigValues] is the second enumeration of
 * the same set, hand-maintained next to the value classes, and this test holds it
 * against [BundledConfigDefaults].
 *
 * A key with no fallback fails silently in production: the value resolves to its
 * own `default` and nothing anywhere reports that the map was incomplete. So the
 * failure message here names the offending paths and says what to do about them.
 */
class FallbackConfigCompletenessTest {

    private val declared = SodogkuConfigValues.all(BasicMapAppConfig(emptyMap<String, Any>()))

    @Test
    fun everyDeclaredKeyHasABundledFallback() {
        val missing = declared
            .map { it.path }
            .filter { path -> BundledConfigDefaults.getValueForPath<Any>(fullPath = path) == null }

        assertTrue(
            missing.isEmpty(),
            "These config keys are declared but have no bundled fallback: $missing. " +
                "Add each one to BundledConfigDefaults in FallbackConfigMap.kt — SPEC 4.2 " +
                "requires the app to run correctly off the bundled map alone.",
        )
    }

    @Test
    fun bundledFallbackAgreesWithEveryDeclaredDefault() {
        val fallbackMap = BasicMapAppConfig(BundledConfigDefaults)

        val disagreements = SodogkuConfigValues.all(fallbackMap)
            .zip(declared)
            .filter { (fromFallback, fromDefault) -> fromFallback.value != fromDefault.value }
            .map { (fromFallback, fromDefault) ->
                "${fromFallback.path}: fallback=${fromFallback.value} default=${fromDefault.value}"
            }

        assertTrue(
            disagreements.isEmpty(),
            "The bundled fallback and the declared default disagree for: $disagreements. " +
                "They are two statements of the same shipped number and must match.",
        )
    }

    /**
     * The `telemetry.*` keys are declared in `:libraries:telemetry:impl`, which
     * this module cannot depend on, so they cannot ride [SodogkuConfigValues].
     * Their paths are pinned here instead — the fallback map has to be complete
     * across the whole app, not just the part of it this module can see.
     */
    @Test
    fun telemetryKeysDeclaredInAnotherModuleAlsoHaveFallbacks() {
        val telemetryPaths = listOf(
            "telemetry.appEventsEnabled",
            "telemetry.appEventsSampleRate",
            "telemetry.klogForwardingEnabled",
        )

        val missing = telemetryPaths
            .filter { path -> BundledConfigDefaults.getValueForPath<Any>(fullPath = path) == null }

        assertTrue(missing.isEmpty(), "Telemetry keys with no bundled fallback: $missing")
    }

    @Test
    fun fallbackMapCarriesNoKeyNobodyDeclares() {
        val declaredPaths = declared.map { it.path }.toSet() + setOf(
            "telemetry.appEventsEnabled",
            "telemetry.appEventsSampleRate",
            "telemetry.klogForwardingEnabled",
        )

        val orphans = BundledConfigDefaults.flattenedPaths() - declaredPaths

        assertEquals(
            emptySet(),
            orphans,
            "The bundled fallback carries keys no ConfiguredValue reads. Either a value " +
                "was deleted and its fallback left behind, or a path is misspelled on one side.",
        )
    }
}

/**
 * Dotted leaf paths of a nested config map. A leaf is anything that is not a
 * nested `Map`, so a structured value such as `ads.rewardedPlacements` — whose
 * own contents are a map keyed by placement id, not by config path — has to stop
 * the walk. Those paths are listed explicitly.
 */
private fun Map<String, *>.flattenedPaths(prefix: String = ""): Set<String> =
    entries.flatMap { (key, value) ->
        val path = if (prefix.isEmpty()) key else "$prefix.$key"
        when {
            path in StructuredPaths -> listOf(path)
            value is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (value as Map<String, *>).flattenedPaths(path).toList()
            }
            else -> listOf(path)
        }
    }.toSet()

private val StructuredPaths = setOf("ads.rewardedPlacements", "paywall.triggers")
