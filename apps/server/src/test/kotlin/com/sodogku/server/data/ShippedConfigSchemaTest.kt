package com.sodogku.server.data

import com.sodogku.server.domain.ManifestEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ConfigValidationTest] proves [ConfigSchema] behaves against hand-written
 * entries. This proves it protects **the keys the app actually ships**, by
 * building the schema from the same `config-manifest-registry.json` CI uploads
 * after a deploy.
 *
 * That distinction is the whole point of the file. `validateValue` waves through
 * any path it has no entry for — deliberately, so a flag can be created ahead of
 * the build that declares it — which means the registry being incomplete is
 * indistinguishable from the type check being switched off for the keys it
 * misses. Until C7 the registry listed four `upgrade.*` keys, so every ads,
 * daily, paywall and feature key was in exactly that state.
 *
 * What that cost, concretely: the client resolves a boolean through
 * `rawValue.toString().toBoolean()`, and **`"banana".toBoolean()` is `false`**.
 * A mistyped string on `daily.enabled` or `features.sharing` does not fall back
 * to the shipped default and does not log — it turns the feature off on every
 * device that fetches it. Numeric keys are luckier (an unparseable number
 * resolves to null and falls back to the default), which is why the boolean case
 * gets its own test.
 */
class ShippedConfigSchemaTest {

    private val entries = shippedManifestEntries()
    private val schema = ConfigSchema.from(entries)

    private fun pathsOfType(type: String) = entries.filter { it.type == type }.map { it.path }

    @Test
    fun everyBooleanKeyRejectsAStringThatWouldSilentlyResolveToFalse() {
        val booleans = pathsOfType("boolean")

        assertTrue(
            booleans.containsAll(listOf("ads.enabled", "daily.enabled", "features.sharing")),
            "The registry has no entry for the kill switches this test exists to protect: $booleans",
        )
        booleans.forEach { path ->
            assertNotNull(schema.validateValue(path, JsonPrimitive("false")), "$path accepted the string \"false\"")
            assertNotNull(schema.validateValue(path, JsonPrimitive("banana")), "$path accepted the string \"banana\"")
            assertNotNull(schema.validateValue(path, JsonPrimitive(0)), "$path accepted the number 0")
            assertNull(schema.validateValue(path, JsonPrimitive(false)))
        }
    }

    @Test
    fun everyNumericKeyRejectsAQuotedNumber() {
        val numbers = pathsOfType("int") + pathsOfType("long") + pathsOfType("double")

        assertTrue(
            numbers.containsAll(listOf("ads.offlineGraceLevels", "scoring.comboStep", "paywall.sessionCap")),
            "The registry has no entry for the tuning keys this test exists to protect: $numbers",
        )
        numbers.forEach { path ->
            assertNotNull(schema.validateValue(path, JsonPrimitive("3")), "$path accepted the string \"3\"")
            assertNotNull(schema.validateValue(path, JsonPrimitive(true)), "$path accepted a boolean")
        }
        pathsOfType("int").forEach { path ->
            assertNotNull(schema.validateValue(path, JsonPrimitive(1.5)), "$path accepted a decimal")
            assertNull(schema.validateValue(path, JsonPrimitive(1)))
        }
    }

    @Test
    fun everyEnumKeyAcceptsOnlyItsDeclaredValues() {
        val enums = entries.filter { it.allowedValues != null }

        assertEquals(
            setOf("ads.failureMode", "upgrade.maintenanceMode"),
            enums.map { it.path }.toSet(),
            "The set of enum-valued config keys changed. Both of these decide whether a player " +
                "can keep playing, so a third one arriving is worth reading this test for.",
        )
        enums.forEach { entry ->
            val allowed = entry.allowedValues!!.jsonArray.map { (it as JsonPrimitive).content }
            allowed.forEach { value ->
                assertNull(schema.validateValue(entry.path, JsonPrimitive(value)), "${entry.path} rejected $value")
            }
            assertNotNull(schema.validateValue(entry.path, JsonPrimitive("banana")))
        }
    }

    /**
     * The honest statement of what is *not* protected. `json`-typed entries carry
     * variable-length structure, so the schema can only say "some JSON" — a
     * garbage value reaches the client, where [com.sodogku.libraries.config.JsonConfigValue]
     * catches the decode failure and falls back to the bundled default. That is a
     * fail-open path, which is why it is acceptable; pinning the set here means a
     * new composite key can't quietly join it.
     */
    @Test
    fun theOnlyKeysThatAcceptAnyShapeAreTheStructuredOnes() {
        // An object is the one shape no scalar type accepts, so what survives it
        // is exactly what the schema declines to check. A string probe would not
        // do: free-text keys like `legal.termsUrl` accept any string by design.
        val nonsense = JsonObject(mapOf("definitely" to JsonPrimitive("not the right shape")))
        val unprotected = entries.map { it.path }.filter { schema.validateValue(it, nonsense) == null }

        assertEquals(
            setOf("ads.rewardedPlacements", "paywall.triggers"),
            unprotected.toSet(),
            "A config key accepts any value the admin console cares to send. For a `json` key " +
                "that is expected (the client's decode falls back to the default); for anything " +
                "else it means the entry's type is wrong or missing.",
        )
    }

    @Test
    fun theSchemaCoversTheWholeShippedKeySet() {
        assertTrue(
            entries.size >= MINIMUM_KEY_COUNT,
            "Only ${entries.size} keys in the shipped registry. Every key missing from it is a key " +
                "the admin console can write any value to. See ConfigManifestRegistryDriftTest.",
        )
        assertEquals(
            entries.size,
            entries.map { it.path }.toSet().size,
            "Duplicate paths in the shipped registry — the later one silently wins the schema.",
        )
    }
}

private const val MINIMUM_KEY_COUNT = 40

private const val REGISTRY_PROPERTY = "sodogku.configManifestRegistry"

/**
 * The committed registry, parsed into the domain type the upload route builds.
 * Reads the real file rather than a fixture so a key added to one and not the
 * other is caught here as well as by `ConfigManifestRegistryDriftTest`.
 */
internal fun shippedManifestEntries(): List<ManifestEntry> =
    Json.parseToJsonElement(shippedRegistryJson()).jsonArray.map { element ->
        val entry: JsonObject = element.jsonObject
        ManifestEntry(
            path = (entry.getValue("path") as JsonPrimitive).content,
            type = (entry.getValue("type") as JsonPrimitive).content,
            default = entry.getValue("default"),
            description = (entry["description"] as? JsonPrimitive)?.content,
            allowedValues = entry["allowedValues"] as? JsonArray,
        )
    }

/** The committed registry verbatim — the `entries` array of a manifest upload. */
internal fun shippedRegistryJson(): String {
    val path = System.getProperty(REGISTRY_PROPERTY)
        ?: error("$REGISTRY_PROPERTY is unset — apps/server/build.gradle.kts should supply it")
    val file = File(path)
    if (!file.isFile) error("config-manifest-registry.json not found at $path")
    return file.readText()
}
