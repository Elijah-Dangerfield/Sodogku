package com.sodogku.libraries.ui.components.board

import com.sodogku.libraries.ui.system.color.RegionPalette
import kotlin.test.Test
import kotlin.test.assertEquals
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
     * `RegionPalette` wraps rather than throwing on an out-of-range region,
     * because a board with more regions than the palette has colours is a
     * content bug and a crash on the board screen is a worse answer. The label
     * has to survive the same input the fill does, or colourblind mode is the
     * one mode that crashes.
     */
    @Test
    fun regionIndicesWrapTheSameWayTheFillsDo() {
        assertEquals(
            labels.describe(0, 0, 0, colorblind = false),
            labels.describe(0, 0, RegionPalette.size, colorblind = false),
        )
        assertEquals(
            labels.describe(0, 0, RegionPalette.size - 1, colorblind = false),
            labels.describe(0, 0, -1, colorblind = false),
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
