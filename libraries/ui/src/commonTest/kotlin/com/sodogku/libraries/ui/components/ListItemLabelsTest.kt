package com.sodogku.libraries.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a toggle row says, which is the only thing a screen reader gets from one.
 *
 * A toggle row clears the semantics of everything inside it so that the row
 * itself can be named, so its label is not a summary of the visible text: it is
 * a replacement for it. Dropping the supporting sentence here would be silent
 * on screen and total to a listener.
 */
class ListItemLabelsTest {

    @Test
    fun theSupportingSentenceIsSpokenAfterTheHeadline() {
        assertEquals(
            "Vibration. A small buzz when you mark a square.",
            spokenRowLabel(Format, "Vibration", "A small buzz when you mark a square."),
        )
    }

    /** A row with nothing to add says its name and stops, rather than trailing punctuation. */
    @Test
    fun aRowWithNoSupportingTextIsJustItsName() {
        assertEquals("Vibration", spokenRowLabel(Format, "Vibration", supportingText = null))
    }

    /**
     * The two halves go through a translatable format, so a translation is free
     * to reorder them. Concatenation would pin the order to English.
     */
    @Test
    fun theFormatDecidesTheOrder() {
        assertEquals(
            "second, first",
            spokenRowLabel("%2\$s, %1\$s", "first", "second"),
        )
    }

    private companion object {
        const val Format = "%1\$s. %2\$s"
    }
}
