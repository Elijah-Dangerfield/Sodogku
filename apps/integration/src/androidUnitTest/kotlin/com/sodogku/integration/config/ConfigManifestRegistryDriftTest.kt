package com.sodogku.integration.config

import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.DoubleConfigValue
import com.sodogku.libraries.config.FlagConfigValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.JsonConfigValue
import com.sodogku.libraries.config.values.TreatBand
import com.sodogku.libraries.config.LongConfigValue
import com.sodogku.libraries.config.StringConfigValue
import com.sodogku.libraries.config.impl.model.BasicMapAppConfig
import com.sodogku.libraries.config.values.SodogkuConfigValues
import com.sodogku.libraries.telemetry.impl.AppEventsEnabled
import com.sodogku.libraries.telemetry.impl.AppEventsSampleRate
import com.sodogku.libraries.telemetry.impl.KlogForwardingEnabled
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `apps/admin/config-manifest-registry.json` is a committed transcription of the
 * app's `ConfiguredValue` classes. CI stamps it with the release's version code
 * and PUTs it to `/v1/admin/config/manifest`, and from there it is the *only*
 * thing the server and the admin console know about config: it is what
 * `ConfigSchema` type-checks writes against, what the console shows as "baked
 * into v1.0.1", and what a rule's flag row is seeded from.
 *
 * So when it drifts, nothing breaks loudly. A key missing from it is a key the
 * server will accept `"six"` for and the console will render with a blank type
 * and no editor. That is the gap this test closes.
 *
 * **Why a test and not a generator.** Generating the file would mean *running*
 * `:libraries:config` code from a Gradle task, and that module has only Android
 * and iOS targets — giving it (and `:libraries:core`, `:libraries:flowroutines`)
 * a JVM target purely to feed codegen is a much larger change than reading the
 * same classes from a test that already has them on its classpath. The failure
 * message prints the exact JSON line to paste, so fixing a drift is the same
 * copy-and-paste a generator would have done, without the extra target.
 *
 * **The limitation, stated honestly.** "Every declared value" means every value
 * in [SodogkuConfigValues.all] plus the three `telemetry.*` classes named below.
 * Neither list is derived from the DI graph's `Set<QaConfigValue>` — no unit test
 * can resolve that without an app — so a value class that is contributed to DI
 * but added to neither list stays invisible here, exactly as
 * `SodogkuConfigValues`' own KDoc warns. The telemetry three are spelled out
 * because they live in `:libraries:telemetry:impl` and `:libraries:config`
 * cannot depend on it; this module can, so their paths, types and defaults are
 * read from the real classes rather than pinned as literals. A *fourth*
 * telemetry value would need adding here by hand.
 */
class ConfigManifestRegistryDriftTest {

    private val declared: List<DeclaredValue> = run {
        val noConfig = BasicMapAppConfig(emptyMap<String, Any>())
        val telemetry = listOf(
            AppEventsEnabled(noConfig),
            AppEventsSampleRate(noConfig),
            KlogForwardingEnabled(noConfig),
        )
        (SodogkuConfigValues.all(noConfig) + telemetry).map { it.asDeclaredValue() }
    }

    private val registry: List<RegistryEntry> = readRegistry()

    @Test
    fun registryListsEveryDeclaredKeyAndNothingElse() {
        val declaredPaths = declared.map { it.path }
        val registryPaths = registry.map { it.path }

        assertEquals(
            declaredPaths.size,
            declaredPaths.toSet().size,
            "Two ConfiguredValue classes declare the same path: " +
                declaredPaths.groupBy { it }.filterValues { it.size > 1 }.keys,
        )

        val missing = declared.filter { it.path !in registryPaths.toSet() }
        val extra = registryPaths.toSet() - declaredPaths.toSet()

        if (missing.isEmpty() && extra.isEmpty()) return
        fail(
            buildString {
                appendLine("$REGISTRY_NAME has drifted from the declared ConfiguredValue classes.")
                if (missing.isNotEmpty()) {
                    appendLine()
                    appendLine("ADD ${entries(missing.size)}:")
                    missing.forEach { appendLine(it.asRegistryLine()) }
                }
                if (extra.isNotEmpty()) {
                    appendLine()
                    appendLine("REMOVE ${entries(extra.size)} — no ConfiguredValue declares them:")
                    extra.sorted().forEach { appendLine("  $it") }
                }
            },
        )
    }

    @Test
    fun registryTypeDefaultAndAllowedValuesMatchTheDeclaration() {
        val registryByPath = registry.associateBy { it.path }

        val wrong = declared.mapNotNull { value ->
            val entry = registryByPath[value.path] ?: return@mapNotNull null
            val reasons = buildList {
                if (entry.type != value.type) add("type is \"${entry.type}\", declared ${value.type}")
                if (entry.default.canonical() != value.default.canonical()) {
                    add("default is ${entry.default}, declared ${value.default}")
                }
                if (entry.allowedValues?.canonical() != value.allowedValues?.canonical()) {
                    add("allowedValues are ${entry.allowedValues ?: "absent"}, declared ${value.allowedValues ?: "none"}")
                }
            }
            if (reasons.isEmpty()) null else value to reasons
        }

        if (wrong.isEmpty()) return
        fail(
            buildString {
                appendLine("$REGISTRY_NAME disagrees with the declared ConfiguredValue classes.")
                appendLine("The declaration wins — the app ships it. Replace each line:")
                wrong.forEach { (value, reasons) ->
                    appendLine()
                    reasons.forEach { appendLine("  ${value.path}: $it") }
                    appendLine(value.asRegistryLine())
                }
            },
        )
    }

    /**
     * Set comparison against a list that is itself computed passes vacuously if
     * both sides collapse to nothing — an empty registry against an empty
     * enumeration is "in sync". Both ends are pinned here: the namespaces are
     * the ones SPEC section 4.3 tabulates, and the count is a floor no plausible
     * refactor drops below.
     */
    @Test
    fun bothSidesAreNonTriviallyPopulated() {
        val namespaces = listOf(
            "ads", "progression", "boosters", "scoring", "daily",
            "paywall", "legal", "upgrade", "app", "features", "telemetry",
        )

        val declaredNamespaces = declared.map { it.path.substringBefore('.') }.toSet()
        val registryNamespaces = registry.map { it.path.substringBefore('.') }.toSet()

        assertEquals(
            emptySet(),
            namespaces.toSet() - declaredNamespaces,
            "SPEC 4.3 names these config namespaces but no ConfiguredValue declares one",
        )
        assertEquals(
            emptySet(),
            namespaces.toSet() - registryNamespaces,
            "SPEC 4.3 names these config namespaces but $REGISTRY_NAME has no entry under one",
        )
        assertTrue(
            registry.size >= MINIMUM_KEY_COUNT,
            "$REGISTRY_NAME has ${registry.size} entries. SPEC 4.3 tabulates far more than " +
                "$MINIMUM_KEY_COUNT, so this is a truncated file, not a smaller key set.",
        )
    }
}

private const val REGISTRY_NAME = "apps/admin/config-manifest-registry.json"

private fun entries(count: Int): String = if (count == 1) "1 entry" else "$count entries"

/**
 * Path handed in by `apps/integration/build.gradle.kts`. An Android unit test's
 * working directory is not something to guess at, and a wrong guess would read
 * nothing and pass.
 */
private const val REGISTRY_PROPERTY = "sodogku.configManifestRegistry"

/** A floor, not the real count — see [ConfigManifestRegistryDriftTest.bothSidesAreNonTriviallyPopulated]. */
private const val MINIMUM_KEY_COUNT = 40

private data class DeclaredValue(
    val path: String,
    val type: String,
    val default: JsonElement,
    val allowedValues: JsonArray?,
)

private data class RegistryEntry(
    val path: String,
    val type: String,
    val default: JsonElement,
    val allowedValues: JsonArray?,
)

private fun readRegistry(): List<RegistryEntry> {
    val path = System.getProperty(REGISTRY_PROPERTY)
        ?: error("$REGISTRY_PROPERTY is unset — apps/integration/build.gradle.kts should supply it")
    val file = File(path)
    if (!file.isFile) error("$REGISTRY_NAME not found at $path")

    return Json.parseToJsonElement(file.readText()).jsonArray.mapIndexed { index, element ->
        val entry = element.jsonObject
        RegistryEntry(
            path = entry.stringField("path", index),
            type = entry.stringField("type", index),
            default = entry["default"] ?: error("$REGISTRY_NAME[$index] has no \"default\""),
            allowedValues = entry["allowedValues"] as? JsonArray,
        )
    }
}

private fun JsonObject.stringField(name: String, index: Int): String =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: error("$REGISTRY_NAME[$index] has no string \"$name\"")

private fun ConfiguredValue<*>.asDeclaredValue() = DeclaredValue(
    path = path,
    type = registryType(),
    default = default.asJsonElement(),
    allowedValues = allowedValues?.let { values -> JsonArray(values.map { it.asJsonElement() }) },
)

/**
 * The `type` string the manifest schema uses, from the typed base the value
 * extends. A new base has to be taught to the registry format before it can be
 * transcribed, so an unrecognised one fails rather than guessing "json".
 */
private fun ConfiguredValue<*>.registryType(): String = when (this) {
    is FlagConfigValue -> "boolean"
    is IntConfigValue -> "int"
    is LongConfigValue -> "long"
    is DoubleConfigValue -> "double"
    is StringConfigValue -> "string"
    is JsonConfigValue<*> -> "json"
    else -> error(
        "$path extends ${this::class.simpleName}, which has no manifest type. Add one to the " +
            "validTypes set in apps/admin/build.gradle.kts, to ConfigSchema, and to this mapping.",
    )
}

private fun Any?.asJsonElement(): JsonElement = when (this) {
    null -> JsonPrimitive(null as String?)
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.asJsonElement() })
    is Iterable<*> -> JsonArray(map { it.asJsonElement() })
    // The one typed model among the structured defaults. The rest are declared
    // as plain maps and lists and fall out above; this needs its serializer, and
    // naming it here rather than reflecting over @Serializable is deliberate —
    // the `error` below is what tells whoever adds the *next* typed default that
    // the registry comparison needs teaching about it, and reflection would
    // silently swallow that.
    is TreatBand -> Json.encodeToJsonElement(TreatBand.serializer(), this)
    else -> error("A ConfiguredValue default of type ${this::class.simpleName} can't be written as JSON")
}

/**
 * Normalises numbers so the comparison is about the value, not its spelling.
 * Kotlin renders `0.60` as `0.6` and `8_000L` as `8000`, and JSON does not have
 * to agree with either — without this, a registry entry that is right would fail
 * for writing `0.60`.
 */
private fun JsonElement.canonical(): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.canonical() })
    is JsonArray -> JsonArray(map { it.canonical() })
    is JsonPrimitive -> when {
        isString -> this
        booleanOrNull != null -> JsonPrimitive(booleanOrNull)
        doubleOrNull != null -> JsonPrimitive(doubleOrNull)
        else -> this
    }
}

private fun DeclaredValue.asRegistryLine(): String = buildString {
    append("  { \"path\": ")
    append(JsonPrimitive(path))
    append(", \"type\": ")
    append(JsonPrimitive(type))
    append(", \"default\": ")
    append(default)
    allowedValues?.let { append(", \"allowedValues\": ").append(it) }
    append(", \"description\": \"…\" },")
}
