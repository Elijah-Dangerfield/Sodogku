package com.sodogku

import com.sodogku.libraries.core.ShakeDetector
import com.sodogku.libraries.core.ShakeEvent
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.navigation.NavigationOptions
import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.navigation.Router
import com.sodogku.libraries.navigation.ShakeDialogRoute
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a shake reaches the report dialog, and — the part that was broken —
 * whether it still does the second time.
 *
 * The suppression flag used to be set the moment the handler asked to navigate
 * and cleared by an `onDialogDismissed()` that nothing called, so shake-to-report
 * worked exactly once per process. The tests here pin both halves: a shake while
 * the dialog is up is swallowed, and every route out of the dialog re-arms it.
 *
 * NOT covered here: the accelerometer maths (`ShakeRecognizerTest` in
 * `:libraries:core`), and the binding of start/stop to the app's visibility,
 * which is a `LifecycleStartEffect` in `App.kt` and needs a real lifecycle to
 * mean anything.
 */
class ShakeHandlerTest : CoroutineTest() {

    @Test
    fun aShakeOpensTheDialog() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        ShakeHandler(detector, router).start()

        detector.shake()

        assertEquals(1, router.navigations.size)
        assertTrue(router.navigations.single() is ShakeDialogRoute)
    }

    @Test
    fun theGestureStillWorksAfterTheDialogHasBeenDismissedOnce() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = ShakeHandler(detector, router)
        handler.start()

        detector.shake()
        handler.onDialogShown()
        handler.onDialogDismissed()

        detector.shake()

        assertEquals(2, router.navigations.size, "shake-to-report is not a one-shot")
    }

    @Test
    fun aShakeWhileTheDialogIsUpIsSwallowed() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = ShakeHandler(detector, router)
        handler.start()

        detector.shake()
        handler.onDialogShown()
        detector.shake()
        detector.shake()

        assertEquals(1, router.navigations.size)
    }

    @Test
    fun aNavigationThatNeverBecomesADialogDoesNotDisableTheGesture() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = ShakeHandler(detector, router)
        handler.start()

        // The router drops navigation while a blocking error screen is up, so
        // the dialog is never shown and `onDialogShown` never runs. A flag set
        // at navigate time would latch on here and stay on forever.
        detector.shake()
        detector.shake()

        assertEquals(2, router.navigations.size)
    }

    @Test
    fun stoppingEndsTheSensorAndTheCollection() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = ShakeHandler(detector, router)
        handler.start()

        handler.stop()
        detector.shake()

        assertFalse(detector.isRunning)
        assertEquals(0, router.navigations.size)
    }

    @Test
    fun restartingDoesNotStackASecondCollector() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = ShakeHandler(detector, router)

        // One backgrounding and one resume. Before `stop` cancelled the
        // collection, this left two collectors on the stream.
        handler.start()
        handler.stop()
        handler.start()

        detector.shake()

        assertTrue(detector.isRunning, "the sensor is listening again after a resume")
        assertEquals(1, router.navigations.size, "one shake is one dialog")
    }

    @Test
    fun startingTwiceDoesNotStackASecondCollector() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = ShakeHandler(detector, router)

        handler.start()
        handler.start()

        detector.shake()

        assertEquals(1, router.navigations.size)
    }

    @Test
    fun aShakeEmittedWhileStoppedIsNotDeliveredOnTheNextStart() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = ShakeHandler(detector, router)

        handler.start()
        handler.stop()
        detector.shake()
        handler.start()

        assertEquals(
            0,
            router.navigations.size,
            "a shake with nobody listening is gone, not queued for the next resume",
        )
    }
}

/**
 * Mirrors the shape both real detectors use: broadcast, no replay, and an event
 * emitted with no subscriber is dropped rather than held.
 */
private class FakeShakeDetector : ShakeDetector {

    private val events = MutableSharedFlow<ShakeEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val shakeEvents: SharedFlow<ShakeEvent> = events

    private var starts = 0
    private var stops = 0

    val isRunning: Boolean get() = starts > stops

    override fun start() {
        starts++
    }

    override fun stop() {
        stops++
    }

    fun shake() {
        events.tryEmit(ShakeEvent(timestampMs = 0))
    }
}

private class RecordingRouter : Router {

    val navigations = mutableListOf<Route>()

    override fun navigate(route: Route, options: NavigationOptions) {
        navigations += route
    }

    override fun goBack() = Unit

    override fun popBackTo(route: Route, inclusive: Boolean) = Unit

    override fun openWebLink(url: String) = Unit
}
