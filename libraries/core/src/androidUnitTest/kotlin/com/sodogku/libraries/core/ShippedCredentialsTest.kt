package com.sodogku.libraries.core

import com.sodogku.buildinfo.SodogkuBuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Everything in `SodogkuBuildConfig` is a string constant baked into every
 * binary the stores get. That is fine for the version metadata and it is the
 * intended delivery route for the telemetry keys, which the app actually uses.
 * It is not fine for anything else.
 *
 * This existed because the template's Supabase project ref and anon key were
 * still being written here long after accounts were deleted, so every release
 * shipped a credential for a service the app never contacted. Nothing broke;
 * that is the problem. A dead credential produces no symptom, it just sits in
 * the binary until a security review asks about it and nobody can say why it
 * is there.
 *
 * So the list is closed rather than filtered. A new entry here means a new
 * value in every shipped build, and that should cost one deliberate edit.
 */
class ShippedCredentialsTest {

    @Test
    fun theBuildConfigShipsOnlyTheFieldsWeMeantToShip() {
        val actual = fieldNames()

        assertEquals(
            EXPECTED_FIELDS,
            actual,
            "SodogkuBuildConfig no longer matches the list of values we knowingly compile into " +
                "every binary. Unexpected: ${actual - EXPECTED_FIELDS}. Missing: ${EXPECTED_FIELDS - actual}. " +
                "If the new field is a credential, the app must actually use it, and the key must come " +
                "from CI secrets or local.properties rather than a default in build-logic.",
        )
    }

    @Test
    fun theScanCanSeeTheFieldsAndWouldNoticeANewOne() {
        // The guard against the guard. Reflection that stopped finding fields
        // would leave the test above comparing two empty sets.
        val names = fieldNames()

        assertTrue(names.contains("APPLICATION_ID"), "reflection is not reading SodogkuBuildConfig's constants")
        assertTrue(names.size > 1, "only found ${names.size} field, so the scan is not walking the object")
    }

    private fun fieldNames(): Set<String> =
        SodogkuBuildConfig::class.java.declaredFields
            .filter { it.type == String::class.java || it.type == Int::class.javaPrimitiveType }
            .map { it.name }
            .toSet()

    private companion object {
        val EXPECTED_FIELDS = setOf(
            "APPLICATION_ID",
            "VERSION_NAME",
            "VERSION_CODE",
            "RELEASE_CHANNEL",
            "BUILD_NUMBER",
            "COMMIT_SHA",
            "COMMIT_BRANCH",
            "GRAFANA_OTLP_BASE_URL",
            "GRAFANA_OTLP_INSTANCE_ID",
            "GRAFANA_LOGS_WRITE_TOKEN",
            "SENTRY_DSN",
        )
    }
}
