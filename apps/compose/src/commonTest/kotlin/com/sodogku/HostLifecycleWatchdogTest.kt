package com.sodogku

import androidx.lifecycle.Lifecycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/**
 * Every rule as one assertion, which is why the watchdog holds no coroutines.
 * The fault it reports is a contradiction between two facts, and a test that
 * proves it by racing a real host is slow when it passes and quiet when it does
 * not.
 */
@OptIn(ExperimentalTime::class)
class HostLifecycleWatchdogTest {

    private val time = TestTimeSource()
    private val watchdog = HostLifecycleWatchdog(threshold = Threshold, timeSource = time)

    @Test
    fun aTapOnALiveHostIsNothing() {
        watchdog.hostStateChanged(Lifecycle.State.RESUMED)
        time += Threshold * 10

        assertNull(watchdog.tapped())
    }

    @Test
    fun aTapWhileTheHostHasBeenDownForTheThresholdIsTheFault() {
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold

        val blind = watchdog.tapped()

        assertEquals(Threshold, blind?.blindFor)
        assertEquals(1, blind?.taps)
    }

    @Test
    fun aTapArrivingInTheFrameTheHostWentDownIsARaceRatherThanAFault() {
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold - 1.seconds

        assertNull(watchdog.tapped(), "a press already in flight when an ad goes up is not a stall")
    }

    @Test
    fun everyTapSinceTheHostWentDownIsCounted() {
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        // The player taps twice before the threshold and gives up on the third.
        watchdog.tapped()
        watchdog.tapped()
        time += Threshold

        assertEquals(3, watchdog.tapped()?.taps)
    }

    @Test
    fun theFaultIsReportedOnceRatherThanOncePerTap() {
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold
        assertNotNull(watchdog.tapped())

        time += Threshold * 5

        assertNull(watchdog.tapped(), "a player jabbing at a dead screen is one fault, not twelve")
    }

    @Test
    fun movingFurtherDownDoesNotRestartTheClock() {
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold - 1.seconds
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += 1.seconds

        assertNotNull(watchdog.tapped(), "CREATED twice is one spell, not two")
    }

    @Test
    fun comingBackAfterAReportedFaultSaysHowLongItWasDown() {
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold
        watchdog.tapped()
        time += 6.seconds

        assertEquals(Threshold + 6.seconds, watchdog.hostStateChanged(Lifecycle.State.STARTED))
    }

    @Test
    fun aHostHeldDownWithNobodyTappingIsNeverAFault() {
        // The ordinary case, and the reason a tap is the signal rather than the
        // lifecycle: a rewarded ad holds the host down for its whole run, and
        // nothing reaches the view underneath it. Nothing is reported going
        // down, and nothing is announced coming back up.
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold * 10

        assertNull(
            watchdog.hostStateChanged(Lifecycle.State.RESUMED),
            "an ad ending is not a recovery anybody needs told about",
        )
    }

    @Test
    fun aHostThatFaultedAndRecoveredCanFaultAgain() {
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold
        assertNotNull(watchdog.tapped())
        watchdog.hostStateChanged(Lifecycle.State.RESUMED)

        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold

        assertNotNull(watchdog.tapped(), "one fault must not use up the watchdog for the session")
    }

    @Test
    fun tapsDoNotCarryOverFromOneSpellToTheNext() {
        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        watchdog.tapped()
        watchdog.tapped()
        watchdog.hostStateChanged(Lifecycle.State.RESUMED)

        watchdog.hostStateChanged(Lifecycle.State.CREATED)
        time += Threshold

        assertEquals(1, watchdog.tapped()?.taps, "the count is of this spell, not of the session")
    }
}

private val Threshold = 2.seconds
