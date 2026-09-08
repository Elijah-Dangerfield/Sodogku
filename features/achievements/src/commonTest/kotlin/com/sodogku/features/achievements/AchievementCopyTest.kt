package com.sodogku.features.achievements

import com.sodogku.libraries.achievements.AchievementGroup
import com.sodogku.libraries.achievements.AchievementId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The exhaustive `when`s in [AchievementCopy] guarantee every badge has *a*
 * name, a description and a face. They cannot notice the mistake that actually
 * happens when seventy-three rows get typed out: a copy-pasted line that leaves
 * two badges sharing one badge's words.
 *
 * Nothing here resolves a string. `StringResource` compares by key, which is
 * exactly the question being asked and needs no Compose harness to ask it.
 */
class AchievementCopyTest {

    @Test
    fun everyBadgeHasItsOwnName() {
        assertNoDuplicates(AchievementId.entries.associateWith { AchievementCopy.name(it) })
    }

    @Test
    fun everyBadgeHasItsOwnDescription() {
        assertNoDuplicates(AchievementId.entries.associateWith { AchievementCopy.description(it) })
    }

    @Test
    fun everyBadgeHasItsOwnFace() {
        assertNoDuplicates(AchievementId.entries.associateWith { AchievementCopy.glyph(it) })
    }

    @Test
    fun everyShelfHasItsOwnHeading() {
        assertNoDuplicates(AchievementGroup.entries.associateWith { AchievementCopy.groupName(it) })
    }

    @Test
    fun aNameIsNeverUsedAsADescription() {
        // The other half of the copy-paste: a row that takes the name resource
        // twice reads fine in the diff and puts the title in both slots.
        val names = AchievementId.entries.map { AchievementCopy.name(it) }.toSet()
        val bodies = AchievementId.entries.map { AchievementCopy.description(it) }.toSet()

        assertEquals(emptySet(), names intersect bodies)
    }

    private fun <K, V> assertNoDuplicates(byKey: Map<K, V>) {
        val shared = byKey.entries
            .groupBy({ it.value }, { it.key })
            .filterValues { it.size > 1 }

        assertEquals(emptyMap(), shared, "these badges share copy")
    }
}
