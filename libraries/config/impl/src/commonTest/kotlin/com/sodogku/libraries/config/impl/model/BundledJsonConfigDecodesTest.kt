package com.sodogku.libraries.config.impl.model

import com.sodogku.libraries.config.JsonConfigValue
import com.sodogku.libraries.config.getValueForPath
import com.sodogku.libraries.config.values.SodogkuConfigValues
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The structured fallbacks actually decode, rather than quietly falling back.
 *
 * [FallbackConfigCompletenessTest] cannot see this, and the reason is worth
 * stating plainly because it is the kind of hole that makes a green suite
 * misleading. Its `bundledFallbackAgreesWithEveryDeclaredDefault` resolves each
 * value against the bundled map and compares it to the declared default — but
 * [JsonConfigValue] returns its default when a decode fails. A misspelled field,
 * a string where a number belongs, or an object the config pipeline cannot
 * represent all produce a value *equal to the default*, so that test passes and
 * the key is dead: the admin console edits it, the client ignores it.
 *
 * There is no way to catch that by comparing decoded values, because the decoded
 * value is right either way. So this checks the raw subtree instead.
 *
 * The shape check is the general one and matters most. `Any?.toJsonElement()`
 * converts the bundled map to JSON on the way to a deserializer, and it only
 * understands `Map`, `Iterable`, `Boolean`, `Number` and `String` — anything else
 * hits its `else` branch and becomes `JsonPrimitive(toString())`. Putting a typed
 * Kotlin object into `BundledConfigDefaults` therefore compiles, reads correctly
 * in the source, and fails at runtime in a way nothing reports.
 */
class BundledJsonConfigDecodesTest {

    private val jsonValues = SodogkuConfigValues.all(BasicMapAppConfig(emptyMap<String, Any>()))
        .filterIsInstance<JsonConfigValue<*>>()

    @Test
    fun everyStructuredFallbackIsPlainCollectionsAndScalars() {
        val offenders = jsonValues.mapNotNull { value ->
            val raw = BundledConfigDefaults.getValueForPath<Any>(fullPath = value.path)
            unrepresentable(raw)?.let { "${value.path} holds a ${it::class.simpleName}: $it" }
        }

        assertTrue(
            offenders.isEmpty(),
            "These bundled JSON fallbacks contain values the config pipeline cannot convert, so they " +
                "will stringify and silently fail to decode:\n" + offenders.joinToString("\n") { "  $it" } +
                "\n\nStore plain maps, lists, numbers, booleans and strings.",
        )
    }

    @Test
    fun everyStructuredFallbackIsPresentAndNotEmpty() {
        // A `null` subtree returns the default before any decoding is attempted,
        // and an empty one usually means the path was spelled differently in the
        // fallback than in the value class.
        val empty = jsonValues.filter { value ->
            when (val raw = BundledConfigDefaults.getValueForPath<Any>(fullPath = value.path)) {
                null -> true
                is Map<*, *> -> raw.isEmpty()
                is Iterable<*> -> raw.none()
                else -> false
            }
        }

        assertTrue(empty.isEmpty(), "These structured fallbacks are missing or empty: ${empty.map { it.path }}")
    }

    @Test
    fun thereIsSomethingToCheck() {
        // The guard against the guard. If the filter above stopped matching —
        // a base class renamed, the enumeration changed shape — both tests would
        // pass over an empty list and prove nothing.
        assertTrue(
            jsonValues.size >= MIN_JSON_VALUES,
            "only found ${jsonValues.size} JsonConfigValues, so the scan is not finding them",
        )
    }

    @Test
    fun theShapeCheckRejectsATypedObject() {
        // And the guard against *that* guard: prove the walk actually reports the
        // failure it is written for, rather than returning null for everything.
        val typed = listOf(mapOf("band" to Unrepresentable))

        assertTrue(unrepresentable(typed) != null, "a typed object nested in a list was not reported")
        assertTrue(unrepresentable(listOf(mapOf("n" to 1))) == null, "plain collections were reported as bad")
    }

    /** The first value in [raw] that `toJsonElement` would stringify, or null. */
    private fun unrepresentable(raw: Any?): Any? = when (raw) {
        null, is Boolean, is Number, is String -> null
        is Map<*, *> -> raw.values.firstNotNullOfOrNull { unrepresentable(it) }
        is Iterable<*> -> raw.firstNotNullOfOrNull { unrepresentable(it) }
        else -> raw
    }

    private object Unrepresentable

    private companion object {
        /** `ads.rewardedPlacements`, `paywall.triggers`, `boosters.treatSchedule`. */
        const val MIN_JSON_VALUES = 3
    }
}
