package com.sodogku.libraries.navigation.impl

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Notices when the navigation queue stops draining.
 *
 * `DelegatingRouter` sends into an unlimited channel and drains it under
 * `repeatOnLifecycle(STARTED)`, so a send always succeeds and the execution
 * always might not. That asymmetry is what made SD-26 so hard to look at: the
 * log said `Enqueuing navigation: navigate to AchievementsRoute` and then said
 * nothing ever again, and there was no way to tell a command that had run from
 * one still sitting in the channel.
 *
 * This does not fix a stall. It makes one arrive as an error with a lifecycle
 * state attached instead of as a player reporting that taps do nothing.
 *
 * **A stall is no progress, not a full queue.** The condition is that nothing
 * has drained for [threshold] while something is waiting, rather than that
 * anything is waiting at all. A router working through a burst of ten commands
 * is busy, not stuck, and reporting it would train whoever reads the errors to
 * ignore them.
 *
 * Reported once per stall. The crash that closed the other half of SD-26 came
 * from twelve banked events draining 1.5ms apart, and a watchdog that fires per
 * check would turn the same stall into a hundred identical reports.
 *
 * Mutable state behind a lock because sends come from any thread and drains come
 * from the main one.
 */
@OptIn(InternalCoroutinesApi::class)
internal class NavigationQueueWatchdog(
    private val threshold: Duration = StallThreshold,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private val lock = SynchronizedObject()

    private var depth = 0

    /** When the queue last moved, either by taking work or by finishing some. */
    private var lastProgressAt: TimeMark? = null

    /** Non-null exactly while a stall has been reported and not yet recovered. */
    private var stalledSince: TimeMark? = null

    /** Whether there is anything to watch. Nothing waiting means nothing to poll. */
    val isWaiting: Boolean get() = synchronized(lock) { depth > 0 }

    fun enqueued(): Unit = synchronized(lock) {
        depth++
        // Only the first command into an empty queue starts the clock. Later
        // arrivals are not progress and must not push the deadline out, or a
        // player tapping repeatedly at a dead router would keep the watchdog
        // permanently quiet, which is precisely the case it exists for.
        if (depth == 1) lastProgressAt = timeSource.markNow()
    }

    /**
     * Records a command actually running.
     *
     * Returns how long the queue was stuck when this drain ends a stall that was
     * reported, and null otherwise, so a recovery is only ever announced for a
     * stall somebody was told about.
     */
    fun drained(): Duration? = synchronized(lock) {
        // A drain with nothing outstanding is a bookkeeping mistake somewhere
        // else. Ignore it rather than letting the count go negative, which would
        // leave `isWaiting` false forever and silently retire the watchdog.
        if (depth == 0) return@synchronized null
        depth--
        // Measured from the last time the queue moved, not from the moment the
        // stall was reported. The report is late by design, and a recovery that
        // subtracted the threshold would understate every stall by four seconds.
        val stuckFor = lastProgressAt?.elapsedNow()
        lastProgressAt = timeSource.markNow()
        if (stalledSince == null) return@synchronized null
        stalledSince = null
        stuckFor
    }

    /**
     * The stall to report, or null if there is nothing new to say.
     *
     * Called on a poll, so it has to be cheap and it has to be quiet: the second
     * call during one stall returns null.
     */
    fun stall(): Stall? = synchronized(lock) {
        if (depth == 0 || stalledSince != null) return@synchronized null
        val idle = lastProgressAt?.elapsedNow() ?: return@synchronized null
        if (idle < threshold) return@synchronized null
        stalledSince = timeSource.markNow()
        Stall(depth = depth, idle = idle)
    }

    /** A queue with [depth] commands in it that has not moved for [idle]. */
    data class Stall(val depth: Int, val idle: Duration)
}

/**
 * How long a still queue is allowed to be before it counts as stuck.
 *
 * Longer than any legitimate pause. The drain waits on
 * `awaitGraphAttachment`, which is a frame plus a 100ms settle, and a
 * backgrounded app is below STARTED on purpose and comes back in well under
 * this. Short enough that a player who has tapped four times and given up is
 * still in the same session as the report.
 */
private val StallThreshold = 4.seconds
