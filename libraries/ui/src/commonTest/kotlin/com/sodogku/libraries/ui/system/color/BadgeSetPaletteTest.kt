package com.sodogku.libraries.ui.system.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sodogku.libraries.achievements.AchievementGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The badge-set palette's two promises, computed rather than read off the
 * table in its KDoc: every set's label is legible on its own disc, and every
 * set is drawn in the same key, a pale disc under a saturated fill.
 *
 * Both are the kind of claim that holds right up until somebody warms one
 * colour by eye. The handoff's own campaign ink is the proof: it reads fine
 * beside its disc in a browser and measures 4.25:1, which is why the shipped
 * value is a step darker and why [theHandoffsOwnCampaignInkWouldFailThis]
 * exists. Every assertion here is "the ratio is high enough", and that is also
 * what a ratio function that had quietly started returning twenty-one would
 * report.
 *
 * Coverage of the groups is not asserted. The palette's `when` has no `else`,
 * so a group without colours is a build that fails one module earlier than
 * this test runs.
 */
class BadgeSetPaletteTest {

    @Test
    fun everySetsInkClearsTheReadingFloorOnItsOwnDisc() {
        AchievementGroup.entries.forEach { group ->
            val style = defaultBadgeSets[group]
            val ratio = contrastRatio(style.ink.color, style.disc.color)
            assertTrue(
                ratio >= InkOnDiscFloor,
                "$group's label ink lands at $ratio against its disc, under the $InkOnDiscFloor a " +
                    "12sp label needs",
            )
        }
    }

    @Test
    fun theHandoffsOwnCampaignInkWouldFailThis() {
        val ratio = contrastRatio(Color(0xFF4A7A2A), Color(0xFFDFF0C9))

        assertTrue(
            ratio < InkOnDiscFloor,
            "the handoff's campaign ink measures $ratio on its disc, so it clears the floor and " +
                "the shipped value was darkened for nothing, or the ratio is not being measured",
        )
    }

    /**
     * The disc goes behind a badge still being earned and the fill goes behind
     * one that is; the earned state has to read as *more*, and a fill lighter
     * than its disc would invert that on one shelf only.
     */
    @Test
    fun everySetsFillIsDeeperThanItsDisc() {
        AchievementGroup.entries.forEach { group ->
            val style = defaultBadgeSets[group]
            assertTrue(
                style.fill.color.luminance() < style.disc.color.luminance(),
                "$group's fill is lighter than its disc, so an earned badge on that shelf would " +
                    "read as less than a locked one",
            )
        }
    }

    /** Nine sets are nine sets. A copy-pasted triple would give two shelves one colour. */
    @Test
    fun noTwoSetsShareAFill() {
        val byFill = AchievementGroup.entries.groupBy { defaultBadgeSets[it].fill.color }

        assertEquals(
            AchievementGroup.entries.size,
            byFill.size,
            "these shelves share a fill: ${byFill.filterValues { it.size > 1 }.values}",
        )
    }

    private companion object {
        /** WCAG AA for text under 18pt. */
        const val InkOnDiscFloor = 4.5f
    }
}
