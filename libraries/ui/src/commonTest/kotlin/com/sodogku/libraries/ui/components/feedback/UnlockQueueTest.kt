package com.sodogku.libraries.ui.components.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rule behind "one toast at a time": the head is what shows, a dismiss
 * takes exactly one away, and an empty queue has nothing to show and nothing to
 * lose. What a composition does with the head is `UnlockToastsQueueTest`.
 */
class UnlockQueueTest {

    @Test
    fun theHeadIsTheFirstItemAndOnlyTheFirst() {
        val queue = UnlockQueue(listOf(First, Second, Third))

        assertEquals(First, queue.head, "the queue is showing something other than the first unlock")
    }

    @Test
    fun oneDismissAdvancesExactlyOne() {
        val queue = UnlockQueue(listOf(First, Second, Third)).advance()

        assertEquals(Second, queue.head, "a dismiss skipped or repeated an unlock")
        assertEquals(listOf(Second, Third), queue.pending, "a dismiss took away something other than the head")
    }

    @Test
    fun anEmptyQueueHasNoHeadAndStaysEmptyWhenAdvanced() {
        val queue = UnlockQueue(emptyList())

        assertNull(queue.head)
        assertTrue(queue.isEmpty)
        assertTrue(queue.advance().isEmpty, "advancing past the end conjured an unlock")
    }

    @Test
    fun dismissingTheLastOneEmptiesTheQueue() {
        val queue = UnlockQueue(listOf(First)).advance()

        assertTrue(queue.isEmpty, "the last dismiss left the queue reporting more to show")
        assertNull(queue.head)
    }

    private companion object {
        val First = UnlockToastItem(glyph = "🐾", label = "Badge unlocked", title = "First Steps")
        val Second = UnlockToastItem(glyph = "✨", label = "Badge unlocked", title = "Perfect Form")
        val Third = UnlockToastItem(glyph = "⚡", label = "Badge unlocked", title = "Speed Demon")
    }
}
