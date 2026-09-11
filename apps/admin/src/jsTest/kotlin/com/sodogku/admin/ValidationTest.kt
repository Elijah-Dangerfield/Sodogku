package com.sodogku.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The checks that run in the browser before anything is sent, mirroring the
 * server's.
 *
 * Duplicated rules are usually a smell, and here they are the design: the
 * server is the authority and refuses bad writes, while this half exists so the
 * operator finds out in the form rather than through a failed request with a
 * path in it. The cost is drift, so the mirrored rules are asserted together in
 * one test that names each of them, which is the place a reader can compare the
 * list against the server's.
 *
 * Typed parsing leans on the near misses, because those are what somebody
 * actually types: `6` for a boolean, `1.5` for an integer, an unquoted word
 * where a JSON string belongs, and an empty field, which is the most common
 * input of all and the one that most easily parses as something.
 *
 * Version comparison is by precedence rather than by text, so ten sorts above
 * nine and a two-part version equals its three-part spelling. Compared as
 * strings, a rollout gated on version ten would silently never match.
 *
 * ### Not here
 *
 * The authoritative checks are `ConfigValidationTest` in `:apps:server`. The
 * console's own detekt exemptions mean its copy is English-only and not
 * player-facing, so `VerifyStrings` does not apply to it.
 */
class ValidationTest {

    @Test
    fun typedParsing_acceptsMatchingTypes() {
        assertNull(parseTypedValue("boolean", "true").problem)
        assertNull(parseTypedValue("int", "42").problem)
        assertNull(parseTypedValue("long", "300000").problem)
        assertNull(parseTypedValue("double", "1.5").problem)
        assertNull(parseTypedValue("string", "\"off\"").problem)
        assertNull(parseTypedValue("json", "{\"a\":1}").problem)
        assertNull(parseTypedValue(null, "[1,2]").problem)
    }

    @Test
    fun typedParsing_rejectsMismatches() {
        assertNotNull(parseTypedValue("boolean", "6").problem)
        assertNotNull(parseTypedValue("int", "1.5").problem)
        assertNotNull(parseTypedValue("int", "\"banana\"").problem)
        assertNotNull(parseTypedValue("string", "6").problem)
        assertNotNull(parseTypedValue("json", "{not json").problem)
        assertNotNull(parseTypedValue("int", "").problem)
    }

    @Test
    fun semver_comparesByPrecedence() {
        assertTrue(compareSemver("1.0.1", "1.0.2") < 0)
        assertTrue(compareSemver("1.10.0", "1.9.0") > 0)
        assertEquals(0, compareSemver("1.0", "1.0.0"))
    }

    private fun draft(configure: RuleDraft.() -> Unit): RuleDraft = RuleDraft().apply(configure)

    @Test
    fun ruleDraft_valid() {
        val problems = validateRuleDraft(
            draft {
                priority = "1"
                value = "true"
                minAppVersion = "1.0.0"
                maxAppVersion = "2.0.0"
                rolloutPercent = "50"
            },
            flagType = "boolean",
        )
        assertEquals(emptyList(), problems)
    }

    @Test
    fun ruleDraft_catchesEveryMirroredServerRule() {
        val problems = validateRuleDraft(
            draft {
                priority = "x"
                value = "banana"
                minAppVersion = "2.0.0"
                maxAppVersion = "1.0.0"
                minVersionCode = "9"
                maxVersionCode = "3"
                rolloutPercent = "150"
            },
            flagType = "boolean",
        )
        assertTrue(problems.any { "Priority" in it })
        assertTrue(problems.any { it.startsWith("Value:") })
        assertTrue(problems.any { "App version min is above max" in it })
        assertTrue(problems.any { "Build code min is above max" in it })
        assertTrue(problems.any { "Rollout" in it })
    }

    @Test
    fun ruleDraft_rejectsMalformedSemver() {
        val problems = validateRuleDraft(draft { value = "true"; minAppVersion = "banana" }, flagType = "boolean")
        assertTrue(problems.any { "look like" in it })
    }
}
