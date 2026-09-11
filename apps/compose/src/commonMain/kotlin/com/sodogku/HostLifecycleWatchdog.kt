package com.sodogku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sodogku.libraries.core.logging.KLog
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Notices the app taking taps it cannot act on.
 *
 * Everything a screen does is gated on a lifecycle at STARTED: state is read
 * with `collectAsStateWithLifecycle`, events arrive through `ObserveEvents`, and
 * `DelegatingRouter` drains its queue under `observeWithLifecycle`. Each of
 * those reads a `LocalLifecycleOwner` that resolves, directly or through a
 * `NavBackStackEntry`, to the host. Below STARTED all three stop at once and
 * none of them says so, while the view stays in the hierarchy and keeps
 * receiving touches. That is SD-26 exactly: the board holds its last frame,
 * buttons do nothing, and the only thing still logging is the tap itself.
 *
 * **A tap is the whole signal, and that is deliberate.** A host below STARTED is
 * ordinary — the app is backgrounded, or a rewarded ad is on top of it — and
 * reporting that would be one error per ad. What is never ordinary is a *touch*
 * reaching our view while the host says the view is not on screen, because a
 * covered view is not touchable. The contradiction is the fault, so nothing is
 * reported until a tap lands in one.
 *
 * The existing `NavigationQueueWatchdog` cannot see this. It arms on an enqueued
 * command, and in the pure form of this fault nothing is ever enqueued: the tap
 * produces a view model event, the event is never delivered because delivery is
 * the thing that stopped, and the router is never asked for anything. SD-26's
 * own log is eleven taps with no `Enqueuing navigation` line after the first.
 *
 * Reported once per spell. A player who has given up will tap a dozen more
 * times and each one is the same fault.
 *
 * Mutable state behind a lock because the lifecycle observer and the pointer
 * handler are both on main today and neither promises to stay there.
 */
@OptIn(InternalCoroutinesApi::class)
internal class HostLifecycleWatchdog(
    private val threshold: Duration = BlindThreshold,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private val lock = SynchronizedObject()

    /** When the host last fell below STARTED, or null while it is at or above. */
    private var blindSince: TimeMark? = null

    private var taps = 0

    /** True exactly while a spell has been reported and not yet recovered. */
    private var reported = false

    /**
     * Records where the host lifecycle has moved to.
     *
     * Returns how long the host was below STARTED when this ends a spell that
     * was reported, and null otherwise, so a recovery is only announced for a
     * fault somebody was told about.
     */
    fun hostStateChanged(state: Lifecycle.State): Duration? = synchronized(lock) {
        if (!state.isAtLeast(Lifecycle.State.STARTED)) {
            // Only the first step below STARTED starts the clock. CREATED to
            // DESTROYED is not a fresh spell, and restarting on it would push
            // the deadline out for as long as the states keep moving.
            if (blindSince == null) {
                blindSince = timeSource.markNow()
                taps = 0
            }
            return@synchronized null
        }
        val was = blindSince ?: return@synchronized null
        blindSince = null
        if (!reported) return@synchronized null
        reported = false
        was.elapsedNow()
    }

    /**
     * Records a press landing on the app's own view, and returns the fault to
     * report if this one proves there is a fault.
     *
     * The [threshold] is here rather than in [hostStateChanged] because of the
     * frame the ad is being presented in: a press already in flight can land
     * just after the host goes down, and that is a race rather than a stall.
     */
    fun tapped(): Blind? = synchronized(lock) {
        val since = blindSince ?: return@synchronized null
        taps++
        if (reported) return@synchronized null
        val blindFor = since.elapsedNow()
        if (blindFor < threshold) return@synchronized null
        reported = true
        Blind(blindFor = blindFor, taps = taps)
    }

    /** [taps] presses landed while the host had been below STARTED for [blindFor]. */
    data class Blind(val blindFor: Duration, val taps: Int)
}

/**
 * Watches every press that passes the root for one landing on a host that
 * cannot act on it, and says so at error level so it reaches Sentry.
 *
 * Placed at the root and reading on [PointerEventPass.Initial] so it sees a
 * press before any child does, and consuming nothing, so no behaviour changes.
 * A screen that has stopped recomposing still hit-tests, which is the only
 * reason this can observe the fault at all.
 */
@Composable
internal fun Modifier.reportingTapsThatGoNowhere(): Modifier {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val watchdog = remember { HostLifecycleWatchdog() }
    val logger = remember { KLog.withTag("HostLifecycle") }

    DisposableEffect(lifecycle, watchdog) {
        val observer = LifecycleEventObserver { _, event ->
            watchdog.hostStateChanged(event.targetState)?.let { blindFor ->
                logger.i { "Compose host is back at STARTED after $blindFor below it" }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return pointerInput(watchdog) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                watchdog.tapped()?.let { blind ->
                    logger.e {
                        "${blind.taps} press(es) reached the app while the Compose host has been " +
                            "below STARTED for ${blind.blindFor}. Nothing gated on that lifecycle " +
                            "is running: no screen collects its state, no view model event is " +
                            "delivered, and the navigation queue cannot drain. Host lifecycle is " +
                            "${lifecycle.currentState}."
                    }
                }
            }
        }
    }
}

/**
 * How long a host may sit below STARTED before a press landing on it counts as
 * the fault rather than as a race.
 *
 * Only has to cover the presentation of something that legitimately takes the
 * screen — the frame in which a rewarded ad goes up, with a press already on its
 * way down. Anything longer than that and the view is either covered, in which
 * case no press arrives, or it is not, in which case the host is wrong.
 */
private val BlindThreshold = 2.seconds
