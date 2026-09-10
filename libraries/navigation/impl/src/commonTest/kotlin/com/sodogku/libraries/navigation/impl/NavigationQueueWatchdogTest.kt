package com.sodogku.libraries.navigation.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/**
 * Every rule as one assertion, which is why the watchdog holds no coroutines.
 * A stall detector proved by racing real timers is a test that is slow when it
 * passes and quiet when it does not.
 */
@OptIn(ExperimentalTime::class)
class NavigationQueueWatchdogTest {

    private val time = TestTimeSource()
    private val watchdog = NavigationQueueWatchdog(threshold = Threshold, timeSource = time)

    @Test
    fun anEmptyQueueNeverStalls() {
        time += Threshold * 10
        assertNull(watchdog.stall())
    }

    @Test
    fun aQueueThatDrainsPromptlyNeverStalls() {
        watchdog.enqueued()
        time += Threshold / 2
        watchdog.drained()
        time += Threshold * 10

        assertNull(watchdog.stall(), "an empty queue is not a stalled one, however long it sits empty")
    }

    @Test
    fun aCommandLeftUndrainedForTheThresholdIsAStall() {
        watchdog.enqueued()
        time += Threshold

        val stall = watchdog.stall()

        assertEquals(1, stall?.depth)
        assertEquals(Threshold, stall?.idle)
    }

    @Test
    fun aCommandUndrainedForLessThanTheThresholdIsNotYetAStall() {
        watchdog.enqueued()
        time += Threshold - 1.seconds

        assertNull(watchdog.stall())
    }

    @Test
    fun aStallIsReportedOnceRatherThanOncePerCheck() {
        watchdog.enqueued()
        time += Threshold
        assertTrue(watchdog.stall() != null)

        time += Threshold * 5

        assertNull(watchdog.stall(), "a stall that keeps reporting is a stall nobody reads")
    }

    @Test
    fun moreCommandsArrivingDuringAStallDoNotPushTheDeadlineOut() {
        watchdog.enqueued()
        time += Threshold - 1.seconds
        // The player tapping again is the symptom, not progress. If this reset
        // the clock, a player jabbing at a dead router would keep the watchdog
        // permanently quiet.
        watchdog.enqueued()
        time += 1.seconds

        assertEquals(2, watchdog.stall()?.depth)
    }

    @Test
    fun aQueueMakingSlowProgressIsBusyRatherThanStuck() {
        repeat(5) {
            watchdog.enqueued()
            time += Threshold - 1.seconds
            watchdog.drained()
            assertNull(watchdog.stall())
        }
    }

    @Test
    fun drainingAfterAReportedStallSaysHowLongItWasStuck() {
        watchdog.enqueued()
        time += Threshold
        watchdog.stall()
        time += 6.seconds

        assertEquals(Threshold + 6.seconds, watchdog.drained())
    }

    @Test
    fun drainingWithoutAReportedStallSaysNothing() {
        watchdog.enqueued()
        time += Threshold - 1.seconds

        assertNull(watchdog.drained(), "a recovery from a stall nobody was told about is noise")
    }

    @Test
    fun onlyTheFirstDrainAfterAStallAnnouncesTheRecovery() {
        watchdog.enqueued()
        watchdog.enqueued()
        time += Threshold
        watchdog.stall()

        assertTrue(watchdog.drained() != null)
        assertNull(watchdog.drained())
    }

    @Test
    fun aDrainWithNothingOutstandingIsIgnoredRatherThanCounted() {
        // Letting the depth go negative would leave `isWaiting` false forever,
        // which retires the watchdog silently. Better to drop a stray drain.
        watchdog.drained()

        watchdog.enqueued()
        time += Threshold

        assertEquals(1, watchdog.stall()?.depth)
    }

    @Test
    fun isWaitingFollowsWhetherAnythingIsOutstanding() {
        assertFalse(watchdog.isWaiting)

        watchdog.enqueued()
        watchdog.enqueued()
        assertTrue(watchdog.isWaiting)

        watchdog.drained()
        assertTrue(watchdog.isWaiting, "one of two commands draining leaves one waiting")

        watchdog.drained()
        assertFalse(watchdog.isWaiting)
    }

    @Test
    fun aQueueThatStalledAndRecoveredCanStallAgain() {
        watchdog.enqueued()
        time += Threshold
        assertTrue(watchdog.stall() != null)
        watchdog.drained()

        watchdog.enqueued()
        time += Threshold

        assertTrue(watchdog.stall() != null, "one stall must not use up the watchdog for the session")
    }
}

private val Threshold = 4.seconds
