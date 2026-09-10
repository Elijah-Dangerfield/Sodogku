package com.sodogku.libraries.ui.components.board

import com.sodogku.libraries.ui.system.color.RegionPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The only logic between a board square and what a screen reader says about it.
 *
 * Every assertion here is on a string a person hears and nobody sees, which is
 * the reason to pin them: a wrong row number or a state that never updates
 * looks exactly like a right one in a screenshot.
 */
class BoardCellLabelsTest {

    @Test
    fun positionsAreSpokenOneBased() {
        assertEquals("Row 1, column 1, pink", labels.describe(0, 0, 0, colorblind = false))
        assertEquals("Row 10, column 7, teal", labels.describe(9, 6, 5, colorblind = false))
    }

    /**
     * The colours and the glyphs are two lists of ten, and reading the wrong one
     * is a silent failure — every index is a valid name in both. This is what
     * separates them.
     */
    @Test
    fun colourblindModeNamesTheGlyphRatherThanTheHue() {
        assertEquals("Row 1, column 1, pink", labels.describe(0, 0, 0, colorblind = false))
        assertEquals("Row 1, column 1, circle", labels.describe(0, 0, 0, colorblind = true))
    }

    /**
     * A region with no name throws, the same way a region with no colour does.
     *
     * This test used to assert the opposite. The old rule was that wrapping
     * beats crashing on the board screen, and it is wrong in exactly the place
     * it sounds most reasonable: a wrapped label tells a screen-reader player
     * that two different regions are the same one, on a board whose only rule
     * is one dog per region. Nobody files that. They file "I'm stuck".
     */
    @Test
    fun aRegionWithNoNameThrowsRatherThanBorrowingAnother() {
        assertFailsWith<IllegalArgumentException> {
            labels.describe(0, 0, RegionPalette.size, colorblind = false)
        }
        assertFailsWith<IllegalArgumentException> {
            labels.describe(0, 0, RegionPalette.size, colorblind = true)
        }
        assertFailsWith<IllegalArgumentException> { labels.describe(0, 0, -1, colorblind = false) }
    }

    /**
     * The two `board_region_*` / `board_glyph_*` sets are hand-maintained lists
     * in `strings.xml` and nothing tied their length to the number of regions a
     * board can have. An eleventh colour without an eleventh name used to mean
     * region 10 announced as region 0, silently.
     */
    @Test
    fun aLabelSetShortOfARegionIsRefusedOnTheSpot() {
        assertFailsWith<IllegalArgumentException> { labels.copy(regions = labels.regions.dropLast(1)) }
        assertFailsWith<IllegalArgumentException> { labels.copy(glyphs = labels.glyphs.dropLast(1)) }
    }

    /**
     * The same rule against the strings the app actually ships, rather than
     * against the fixture above. The check in the constructor only fires once
     * something builds a set; this one fires on the set `rememberBoardCellLabels`
     * is going to build, without a composition to run it in.
     */
    @Test
    fun theShippedStringSetsHaveOneEntryPerRegion() {
        assertEquals(RegionPalette.size, RegionNameResources.size, "board_region_* is the wrong length")
        assertEquals(RegionPalette.size, GlyphNameResources.size, "board_glyph_* is the wrong length")
        // By key, because two reads of the same `Res.string.x` are two objects.
        // A copy-paste that names the same string twice leaves two regions
        // sharing a word and the list still the right length.
        assertEquals(
            RegionNameResources.size,
            RegionNameResources.map { it.key }.toSet().size,
            "two regions are announced by the same string",
        )
        assertEquals(
            GlyphNameResources.size,
            GlyphNameResources.map { it.key }.toSet().size,
            "two glyphs are announced by the same string",
        )
    }

    /**
     * Four states, four different things to say. Asserted as *distinct* rather
     * than one by one: the failure worth catching is two states collapsing onto
     * one phrase, which leaves a player unable to tell a square they crossed off
     * from one that cost them a bone.
     */
    @Test
    fun everyCellStateSaysSomethingDifferent() {
        val spoken = BoardCellState.entries.map(labels::stateOf)
        assertEquals(BoardCellState.entries.size, spoken.toSet().size, "states share a phrase: $spoken")
        assertTrue(spoken.none { it.isBlank() }, "a state has no phrase: $spoken")
    }

    /** A translation may reorder or drop arguments; the numbers still have to follow theirs. */
    @Test
    fun placeholdersAreFilledPositionallyRatherThanInOrder() {
        assertEquals("b then a", "%2\$s then %1\$s".fill("a", "b"))
        assertEquals("7 and 7", "%1\$d and %1\$d".fill(7))
        assertEquals("just a", "just %1\$s".fill("a", "unused"))
    }

    /**
     * The one input that would quietly corrupt every later substitution: an
     * argument that itself looks like a placeholder. Left alone, a naive
     * left-to-right replace would expand it on the *next* pass and put the wrong
     * word in the sentence.
     */
    @Test
    fun anArgumentThatLooksLikeAPlaceholderIsNotExpandedAgain() {
        assertEquals("%2\$s and second", "%1\$s and %2\$s".fill("%2\$s", "second"))
    }

    private companion object {
        val labels = BoardCellLabels(
            regions = listOf(
                "pink", "yellow", "periwinkle", "lime", "lilac",
                "teal", "coral", "sky blue", "green", "orchid",
            ),
            glyphs = listOf(
                "circle", "ring", "square", "diamond", "triangle up",
                "triangle down", "plus", "bar", "double bar", "chevron",
            ),
            empty = "empty",
            marked = "crossed off",
            proposed = "suggested, not yet kept",
            wrong = "wrong guess, cost a bone",
            dog = "dog",
            cellFormat = "Row %1\$d, column %2\$d, %3\$s",
            markAction = "Cross off",
            clearAction = "Clear",
            placeAction = "Place a dog",
        )
    }
}
