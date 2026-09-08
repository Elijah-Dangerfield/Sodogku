package com.sodogku.integration.feedback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the `feedback-triage` skill's Sentry queries against the enum that
 * produces the tag values it queries for.
 *
 * The failure this exists to catch is silent in both directions and visible in
 * neither. Rename `FeedbackKind.OwnerDirective`'s tag from `owner_directive` to
 * something tidier and the app keeps building, keeps sending, and Sentry keeps
 * accepting, but `feedback_kind:owner_directive` starts matching nothing. The
 * triage run reports "no new feedback" and is believed, because that is also
 * what a quiet week looks like. Nothing about a directive that vanishes into a
 * tag nobody queries produces an error anywhere.
 *
 * So both ends are read as text from the real artifacts: the enum source, and
 * the skill markdown that a triage agent actually follows. If either side is
 * renamed without the other, this fails.
 *
 * **What it proves.** That every `feedback_kind:<value>` the skill searches for
 * is a value the app can emit, and that every value the app can emit is searched
 * for by the skill. It does not prove the tag is set on the event; that is
 * `AppTelemetry.captureUserFeedback`'s job and is asserted at the call site.
 * A rename is the mistake people actually make, and a rename is what this
 * catches.
 */
class FeedbackTriageQueryContractTest {

    @Test
    fun everyKindTheAppCanEmitIsQueriedByTheSkill() {
        val emitted = emittedTags()
        val queried = queriedTags()

        assertTrue(emitted.isNotEmpty(), "parsed no tags out of ${KIND_SOURCE}; the reader is broken")
        assertTrue(queried.isNotEmpty(), "parsed no queries out of ${SKILL}; the reader is broken")

        val unqueried = emitted - queried
        assertTrue(
            unqueried.isEmpty(),
            "The app can file feedback tagged ${unqueried.joinToString()} but the triage skill never " +
                "searches for it, so those reports are invisible to triage. Add the query to $SKILL.",
        )
    }

    @Test
    fun theSkillDoesNotSearchForKindsTheAppCannotEmit() {
        val emitted = emittedTags()
        val queried = queriedTags()

        val unmatchable = queried - emitted
        assertTrue(
            unmatchable.isEmpty(),
            "The triage skill searches feedback_kind:${unmatchable.joinToString()} but no FeedbackKind " +
                "produces that tag, so the query silently returns nothing. Fix it in $SKILL or $KIND_SOURCE.",
        )
    }

    @Test
    fun theEnumStillDefinesAllThreeChannels() {
        // A floor with a name: the split between an owner's instruction and a
        // player's report is the whole reason the tag exists. Collapsing them
        // back into one value would pass both tests above.
        assertEquals(
            setOf("owner_directive", "bug_report", "feedback"),
            emittedTags(),
            "the three feedback channels are load-bearing for triage; see $KIND_SOURCE",
        )
    }

    @Test
    fun bothReadersCanActuallyFail() {
        // The two tests above compare two parsed sets. A reader that silently
        // returns nothing would make them pass, so prove each reader finds a
        // value that is definitely present.
        assertTrue("owner_directive" in emittedTags(), "the enum reader found nothing recognisable")
        assertTrue("owner_directive" in queriedTags(), "the skill reader found nothing recognisable")
    }

    private fun emittedTags(): Set<String> =
        TAG_DECLARATION.findAll(read(KIND_SOURCE)).map { it.groupValues[1] }.toSet()

    private fun queriedTags(): Set<String> =
        SKILL_QUERY.findAll(read(SKILL)).map { it.groupValues[1] }.toSet()

    private fun read(relativePath: String): String {
        val root = System.getProperty("sodogku.repoRoot")
            ?: error("sodogku.repoRoot is unset. apps/integration/build.gradle.kts supplies it")
        val file = File(root, relativePath)
        require(file.isFile) { "$relativePath not found under $root" }
        return file.readText()
    }

    private companion object {
        const val KIND_SOURCE =
            "libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/FeedbackKind.kt"
        const val SKILL = ".claude/skills/feedback-triage/SKILL.md"

        /** `tag = "owner_directive"` in an enum entry's constructor call. */
        val TAG_DECLARATION = Regex("""tag\s*=\s*"([a-z_]+)"""")

        /** `feedback_kind:owner_directive` anywhere in the skill's prose or tables. */
        val SKILL_QUERY = Regex("""feedback_kind:([a-z_]+)""")
    }
}
