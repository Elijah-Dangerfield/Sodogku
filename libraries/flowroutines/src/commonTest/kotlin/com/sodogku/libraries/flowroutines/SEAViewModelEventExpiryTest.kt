package com.sodogku.libraries.flowroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Events are side effects, and a side effect has a shelf life.
 *
 * The channel behind `eventFlow` is UNLIMITED and its collector is
 * lifecycle-gated, so anything sent while a screen is stopped banks up rather
 * than being lost. That is the right default for a screen that is stopped for a
 * frame and the wrong one for a screen that is stopped for minutes.
 *
 * The failure this pins is real. On 2026-09-09 a lifecycle stall left the board
 * taking taps with nothing collecting; twelve `OpenAchievements` events piled up
 * over eight minutes and replayed 1.5ms apart when collection resumed, and the
 * twelve navigations crashed NavController with "Attempted to pop Destination
 * ... which is not the top of the back stack".
 */
class SEAViewModelEventExpiryTest {

    private class Subject(timeSource: TestTimeSource) :
        SEAViewModel<String, String, String>(initialStateArg = "s", timeSource = timeSource) {
        override suspend fun handleAction(action: String) = Unit
        fun emit(event: String) = sendEvent(event)
    }

    @Test
    fun aFreshEventIsDelivered() = runTest {
        val clock = TestTimeSource()
        val subject = Subject(clock)

        subject.emit("go")

        assertEquals("go", subject.eventFlow.first())
    }

    @Test
    fun anEventOlderThanTheShelfLifeIsDropped() = runTest {
        val clock = TestTimeSource()
        val subject = Subject(clock)

        subject.emit("stale")
        // Nothing was collecting for six seconds, which is what a stopped screen
        // looks like from in here.
        clock += 6.seconds
        subject.emit("fresh")

        // The stale one is gone and the queue is not blocked behind it.
        assertEquals("fresh", subject.eventFlow.first())
    }

    @Test
    fun awholeBacklogOfStaleEventsCollapsesToNothing() = runTest {
        // The shape of the crash: twelve identical navigations arriving at once.
        val clock = TestTimeSource()
        val subject = Subject(clock)

        repeat(12) { subject.emit("OpenAchievements") }
        clock += 8.seconds
        subject.emit("Marked")

        assertEquals("Marked", subject.eventFlow.first())
    }

    @Test
    fun anEventRightUnderTheShelfLifeStillCounts() = runTest {
        // The boundary matters: this is the "sent in init, collected on first
        // composition" case, and dropping it would strand a screen.
        val clock = TestTimeSource()
        val subject = Subject(clock)

        subject.emit("NavigateToHome")
        clock += 4.seconds

        assertEquals("NavigateToHome", subject.eventFlow.first())
    }
}
