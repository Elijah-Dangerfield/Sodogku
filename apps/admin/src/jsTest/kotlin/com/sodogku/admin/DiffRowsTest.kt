package com.sodogku.admin

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comparing two environments, and being careful about what counts as a
 * difference.
 *
 * The console shows this before somebody promotes config from staging to
 * production, so the number of highlighted rows is the number of things they
 * are about to change. Three kinds of drift are tracked separately and only two
 * of them mean the environments disagree.
 *
 * A value overridden on one side and unset on the other differs. Rules
 * attached on one side and not the other differ, even when the resolved value
 * is currently identical, because the rule will start applying to somebody.
 * Defaults baked into different builds are reported and do not count, since
 * that is two release trains rather than a configuration change, and counting
 * it would make every row light up the week of a release.
 *
 * The row set is the union of both sides across both flags and both manifests,
 * which is what stops a key that exists only in the environment being promoted
 * *to* from vanishing out of the comparison.
 *
 * ### Not here
 *
 * Whether a value is well formed is `ValidationTest`, undoing a change is
 * `RevertTest`, and how rows are grouped and filtered is `FlagRowsTest`.
 */
class DiffRowsTest {

    private fun flag(path: String, value: Boolean, rules: List<ConfigRuleDto> = emptyList()) =
        ConfigFlagDto(path, JsonPrimitive(value), 0, rules)

    private fun manifest(path: String, default: Boolean) =
        ManifestEntryDto(path, "boolean", JsonPrimitive(default))

    @Test
    fun setOnOneSideOnlyDiffers() {
        val row = buildDiffRows(
            aFlags = listOf(flag("social.enabled", true)),
            aManifest = listOf(manifest("social.enabled", false)),
            bFlags = emptyList(),
            bManifest = listOf(manifest("social.enabled", false)),
        ).single()
        assertTrue(row.serverDiffers)
        assertTrue(row.differs)
        assertEquals("true", row.a.serverLabel)
        assertEquals("not set", row.b.serverLabel)
    }

    @Test
    fun sameValuesDoNotDiffer() {
        val row = buildDiffRows(
            aFlags = listOf(flag("social.enabled", false)),
            aManifest = listOf(manifest("social.enabled", false)),
            bFlags = listOf(flag("social.enabled", false)),
            bManifest = listOf(manifest("social.enabled", false)),
        ).single()
        assertFalse(row.differs)
    }

    @Test
    fun ruleDifferencesCount() {
        val rule = ConfigRuleDto(
            id = "r1",
            flagPath = "social.enabled",
            priority = 0,
            value = JsonPrimitive(true),
            conditions = RuleConditions(countries = setOf("US")),
            enabled = true,
        )
        val row = buildDiffRows(
            aFlags = listOf(flag("social.enabled", false, rules = listOf(rule))),
            aManifest = emptyList(),
            bFlags = listOf(flag("social.enabled", false)),
            bManifest = emptyList(),
        ).single()
        assertTrue(row.rulesDiffer)
        assertTrue(row.differs)
        assertFalse(row.serverDiffers)
    }

    @Test
    fun bakedMismatchIsFlaggedButNotADifferenceByItself() {
        val row = buildDiffRows(
            aFlags = emptyList(),
            aManifest = listOf(manifest("social.enabled", false)),
            bFlags = emptyList(),
            bManifest = listOf(manifest("social.enabled", true)),
        ).single()
        assertTrue(row.bakedDiffers)
        assertFalse(row.differs)
    }

    @Test
    fun unionCoversFlagsMissingEverywhereButOneManifest() {
        val rows = buildDiffRows(
            aFlags = listOf(flag("a.only", true)),
            aManifest = emptyList(),
            bFlags = emptyList(),
            bManifest = listOf(manifest("b.only", false)),
        )
        assertEquals(listOf("a.only", "b.only"), rows.map { it.path })
    }
}
