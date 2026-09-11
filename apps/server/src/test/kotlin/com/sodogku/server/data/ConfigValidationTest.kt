package com.sodogku.server.data

import com.sodogku.server.domain.ManifestEntry
import com.sodogku.server.domain.RuleConditions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What the admin console is allowed to save, checked without a database.
 *
 * Remote config is the live-ops lever, so a value that gets past validation
 * reaches every installed client. The type checks are therefore about near
 * misses rather than nonsense: a quoted `"true"` where a boolean belongs, a
 * `"5"` where an integer belongs. Both look right in a text field and both
 * deserialise to something the client will not read.
 *
 * An unknown path passes on purpose, and that is the one decision here somebody
 * might call a bug. A key can only be added to the manifest by shipping a
 * build, and refusing unknown paths would mean no flag could be set before the
 * release it belongs to had gone out. The cost is that a typo in a path saves
 * quietly, which the client-side manifest guards catch instead.
 *
 * Rule conditions are bounds and ordering: a rollout outside nought to one
 * hundred, a minimum version above its maximum. Each of those describes a rule
 * that will simply never match, which is indistinguishable from a rule that was
 * never saved.
 *
 * ### Not here
 *
 * Which rule wins when several match is `AppConfigTargetingEngineTest`. Storing
 * any of this is the Postgres repository tests. That the key set agrees with
 * the client's is `ConfigManifestRegistryDriftTest` in `:apps:integration`.
 */
class ConfigValidationTest {

    private fun entry(path: String, type: String, allowed: List<String>? = null) = ManifestEntry(
        path = path,
        type = type,
        default = JsonPrimitive(false),
        description = null,
        allowedValues = allowed?.let { JsonArray(it.map(::JsonPrimitive)) },
    )

    private fun schema(vararg entries: ManifestEntry) = ConfigSchema.from(entries.toList())

    @Test
    fun boolean_rejectsNonBoolean_acceptsBoolean() {
        val s = schema(entry("social.enabled", "boolean"))
        assertNotNull(s.validateValue("social.enabled", JsonPrimitive(6)))
        assertNotNull(s.validateValue("social.enabled", JsonPrimitive("true"))) // quoted string, not a bool
        assertNull(s.validateValue("social.enabled", JsonPrimitive(true)))
    }

    @Test
    fun int_rejectsStringAndDecimal_acceptsInt() {
        val s = schema(entry("upgrade.minSupportedVersionCode", "int"))
        assertNotNull(s.validateValue("upgrade.minSupportedVersionCode", JsonPrimitive("5")))
        assertNull(s.validateValue("upgrade.minSupportedVersionCode", JsonPrimitive(5)))
    }

    @Test
    fun string_enum_enforcesAllowedValues() {
        val s = schema(entry("upgrade.maintenanceMode", "string", listOf("off", "banner", "blocking")))
        assertNull(s.validateValue("upgrade.maintenanceMode", JsonPrimitive("blocking")))
        assertNotNull(s.validateValue("upgrade.maintenanceMode", JsonPrimitive("banana")))
        assertNotNull(s.validateValue("upgrade.maintenanceMode", JsonPrimitive(3))) // not even a string
    }

    @Test
    fun unknownPath_passes_soNewFlagsArentBlocked() {
        val s = schema(entry("social.enabled", "boolean"))
        assertNull(s.validateValue("brand.newFlag", JsonPrimitive("anything")))
    }

    @Test
    fun ruleConditions_boundsAndOrdering() {
        assertNotNull(validateRuleConditions(RuleConditions(rolloutPercent = 150)))
        assertNull(validateRuleConditions(RuleConditions(rolloutPercent = 50)))
        assertNotNull(validateRuleConditions(RuleConditions(minVersionCode = 100, maxVersionCode = 50)))
        assertNotNull(validateRuleConditions(RuleConditions(minAppVersion = "2.0.0", maxAppVersion = "1.0.0")))
        assertNull(validateRuleConditions(RuleConditions(minAppVersion = "1.0.0", maxAppVersion = "2.0.0")))
    }
}
