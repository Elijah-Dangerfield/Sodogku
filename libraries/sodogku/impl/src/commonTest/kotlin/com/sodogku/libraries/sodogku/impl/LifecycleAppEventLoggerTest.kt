package com.sodogku.libraries.sodogku.impl

import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.core.logging.EXTRA_APP_EVENT
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.LogEntry
import com.sodogku.libraries.core.logging.LogId
import com.sodogku.libraries.core.logging.LogTree
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * How long the player had the app open, measured across foreground edges.
 *
 * Session length is a number product decisions get made on, and every way of
 * getting it wrong here produces a plausible number rather than an obvious one.
 * So the clock is a `TestTimeSource` and the arithmetic is asserted directly: a
 * fraction of a second truncates rather than rounding up, and a second
 * foreground restarts the count instead of accumulating, which is the bug that
 * would make every session look longer the more days somebody played.
 *
 * A background with no foreground before it omits the attribute entirely. That
 * happens on a process the system started without showing anything, and
 * reporting zero there would drag the average down with sessions that never
 * existed. An absent attribute is a gap in a dashboard; a zero is a lie in one.
 *
 * ### Not here
 *
 * Where lifecycle edges come from is platform code. What is done with the
 * emitted event, including whether it is exported at all, is
 * `GrafanaLogTreeTest`, and that the attribute name matches the dashboards is
 * `DashboardQueryContractTest`.
 */
class LifecycleAppEventLoggerTest {

    private val tree = RecordingTree()
    private val timeSource = TestTimeSource()
    private val logger = LifecycleAppEventLogger(timeSource)

    private fun plantTree() = KLog.plant(tree)

    @AfterTest
    fun tearDown() {
        KLog.clearTrees()
    }

    @Test
    fun backgrounded_carriesWholeSecondsSinceForeground() {
        plantTree()

        logger.onForeground(AppEvent.OnForeground(isColdBoot = true))
        timeSource += 95.seconds + 400.milliseconds
        logger.onBackground(AppEvent.OnBackground)

        val entry = tree.eventEntries("app.backgrounded").single()
        assertEquals(95L, entry.context.extras["session_duration_sec"])
    }

    @Test
    fun eachForeground_restartsTheClock() {
        plantTree()

        logger.onForeground(AppEvent.OnForeground(isColdBoot = true))
        timeSource += 10.seconds
        logger.onBackground(AppEvent.OnBackground)
        timeSource += 300.seconds
        logger.onForeground(AppEvent.OnForeground(isColdBoot = false))
        timeSource += 5.seconds
        logger.onBackground(AppEvent.OnBackground)

        val durations = tree.eventEntries("app.backgrounded").map { it.context.extras["session_duration_sec"] }
        assertEquals(listOf<Any?>(10L, 5L), durations)
    }

    @Test
    fun backgroundWithoutForeground_omitsTheAttribute() {
        plantTree()

        logger.onBackground(AppEvent.OnBackground)

        val entry = tree.eventEntries("app.backgrounded").single()
        assertFalse("session_duration_sec" in entry.context.extras)
    }

    private class RecordingTree : LogTree() {
        val entries = mutableListOf<LogEntry>()

        override fun log(entry: LogEntry): LogId? {
            entries += entry
            return null
        }

        fun eventEntries(eventName: String): List<LogEntry> =
            entries.filter { it.context.extras[EXTRA_APP_EVENT] == eventName }
    }
}
