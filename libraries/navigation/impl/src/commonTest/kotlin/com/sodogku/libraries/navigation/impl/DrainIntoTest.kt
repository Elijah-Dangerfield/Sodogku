package com.sodogku.libraries.navigation.impl

import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The loop behind `NavigationRecovery.drainQueueNow`, which is the way out of
 * `SODOGKU-R`: a host lifecycle that says the view is off screen while a touch
 * proves it is not, leaving navigation commands queued forever.
 *
 * Tested against a plain list rather than a `NavHostController` on purpose. The
 * three things that can go wrong here are all about the loop and none of them
 * are about navigation: it has to take everything, it has to survive one bad
 * command, and it must not run a command twice.
 */
class DrainIntoTest {

    @Test
    fun everythingQueuedIsApplied() {
        val queue = Channel<MutableList<String>.() -> Unit>(Channel.UNLIMITED)
        queue.trySend { add("one") }
        queue.trySend { add("two") }
        queue.trySend { add("three") }

        val applied = mutableListOf<String>()

        assertEquals(3, queue.drainInto(applied))
        assertEquals(listOf("one", "two", "three"), applied)
    }

    @Test
    fun anEmptyQueueIsANoOp() {
        val queue = Channel<MutableList<String>.() -> Unit>(Channel.UNLIMITED)

        assertEquals(0, queue.drainInto(mutableListOf()))
    }

    /**
     * The one that matters for a recovery. Stopping on the first failure would
     * leave the queue half drained, which is the state this was called to end.
     */
    @Test
    fun oneFailingCommandDoesNotStopTheRest() {
        val queue = Channel<MutableList<String>.() -> Unit>(Channel.UNLIMITED)
        queue.trySend { add("before") }
        queue.trySend { error("this command is broken") }
        queue.trySend { add("after") }

        val applied = mutableListOf<String>()

        assertEquals(3, queue.drainInto(applied))
        assertEquals(listOf("before", "after"), applied)
    }

    /** A second drain finds nothing, so the gated drain cannot replay these. */
    @Test
    fun aDrainedCommandIsNotRunTwice() {
        val queue = Channel<MutableList<String>.() -> Unit>(Channel.UNLIMITED)
        queue.trySend { add("once") }

        val applied = mutableListOf<String>()
        queue.drainInto(applied)

        assertEquals(0, queue.drainInto(applied))
        assertEquals(listOf("once"), applied)
    }
}
