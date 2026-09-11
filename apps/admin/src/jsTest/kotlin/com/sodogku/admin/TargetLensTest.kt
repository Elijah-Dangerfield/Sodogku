package com.sodogku.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The lens has exactly one identity axis, and it is the one the server matches on.
 *
 * There used to be two: a "user id" box whose value the server deserialized and
 * threw away, sitting next to the install id it actually resolves against. The
 * console seeded new allow-lists from the dead one, so an operator could fill in
 * the field labelled "uuid (for allow/deny + rollout)", click "add rule for this
 * target", save it, and watch the preview report that the rule did not match.
 * The rule was fine. The preview was previewing a client with no identity.
 */
class TargetLensTest {

    private val row = FlagRow(
        path = "social.enabled",
        type = "boolean",
        default = JsonPrimitive(false),
        allowedValues = null,
        description = null,
        base = null,
        rules = emptyList(),
        matchedRule = null,
        resolved = JsonPrimitive(false),
        inDb = false,
    )

    @Test
    fun aRuleSeededFromTheLensAllowsTheInstallTheLensIsPreviewing() {
        val target = TargetState().apply { installId = "install-abc" }

        assertEquals(setOf("install-abc"), draftForTarget(row, target).toConditions().userAllow)
    }

    @Test
    fun anEmptyLensSeedsNoAllowlist() {
        // Otherwise "add rule for this target" with no identity set would author
        // an allowlist of one blank string, which matches nobody at all.
        assertEquals(null, draftForTarget(row, TargetState()).toConditions().userAllow)
    }

    @Test
    fun theResolveRequestCarriesTheInstallId() {
        val body = Json.encodeToJsonElement(
            ResolveRequest.serializer(),
            TargetState().apply { installId = "install-abc" }.toRequest(),
        ).jsonObject

        assertEquals("install-abc", (body["installId"] as JsonPrimitive).content)
    }

    @Test
    fun theRequestShapeHasNoIdentityFieldTheServerIgnores() {
        // Against the descriptor, not against an encoded body: an unset nullable
        // field is omitted from the JSON either way, so a body-level check here
        // would pass whether or not the dead field still existed.
        val descriptor = ResolveRequest.serializer().descriptor
        val fields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

        assertTrue("installId" in fields, "the identity axis is gone entirely: $fields")
        assertFalse("userId" in fields, "the request still declares a field the server drops: $fields")
    }
}
