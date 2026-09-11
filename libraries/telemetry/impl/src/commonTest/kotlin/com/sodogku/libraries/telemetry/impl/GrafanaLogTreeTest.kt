package com.sodogku.libraries.telemetry.impl

import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import io.opentelemetry.kotlin.logging.SeverityNumber
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrafanaLogTreeTest {

    private val processor = RecordingLogRecordProcessor()

    private var enabled = true
    private var sampleRate = 1.0
    private var klogForwardingEnabled = false
    private var sessionId: String? = "session-uuid-1"
    private var installId: String? = "install-uuid-1"
    private var offline = false

    private fun plantTree() {
        KLog.plant(
            GrafanaLogTree(
                exportEnabled = { enabled },
                sampleRate = { sampleRate },
                klogForwardingEnabled = { klogForwardingEnabled },
                currentSessionId = { sessionId },
                currentInstallId = { installId },
                isOffline = { offline },
                processorFactory = { processor },
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        KLog.clearTrees()
    }

    @Test
    fun logEvent_forwardsEventNameWithCorrelationAttributes() {
        plantTree()

        KLog.logEvent("example.abandoned", "phase" to "wait", "wait_ms" to 1200L)

        assertEquals(1, processor.records.size)
        val record = processor.records.single()
        assertEquals("example.abandoned", record.eventName)
        assertEquals(SeverityNumber.INFO, record.severityNumber)
        assertEquals("session-uuid-1", record.attributes["session_id"])
        assertEquals("install-uuid-1", record.attributes["install_id"])
        assertEquals("wait", record.attributes["phase"])
        assertEquals(1200L, record.attributes["wait_ms"])
    }

    @Test
    fun klogForwardingOff_forwardsOnlyEvents() {
        plantTree()

        KLog.i("just an ordinary info line")
        KLog.w("a warn line, not forwarded with the flag off")
        KLog.e("an error line, still not forwarded")
        KLog.logEvent("example.completed", "won" to true)

        val record = processor.records.single()
        assertEquals("example.completed", record.eventName)
    }

    @Test
    fun klogForwardingOn_forwardsWarnAndAbove_asPlainLogs() {
        klogForwardingEnabled = true
        plantTree()

        KLog.withTag("AppSocket").w("reconnect attempt 3 backing off")

        val record = processor.records.single()
        assertEquals(null, record.eventName)
        assertEquals("reconnect attempt 3 backing off", record.body)
        assertEquals(SeverityNumber.WARN, record.severityNumber)
        assertEquals("AppSocket", record.attributes["tag"])
        assertEquals("session-uuid-1", record.attributes["session_id"])
        assertEquals("install-uuid-1", record.attributes["install_id"])
    }

    @Test
    fun klogForwardingOn_errorWithThrowable_carriesExceptionAttributes() {
        klogForwardingEnabled = true
        plantTree()

        KLog.e("send after close", IllegalStateException("socket already closed"))

        val record = processor.records.single()
        assertEquals(null, record.eventName)
        assertEquals("send after close", record.body)
        assertEquals(SeverityNumber.ERROR, record.severityNumber)
        assertEquals("IllegalStateException", record.attributes["exception_type"])
        assertEquals("socket already closed", record.attributes["exception_message"])
    }

    @Test
    fun klogForwardingOn_infoNonEvent_isStillNotForwarded() {
        klogForwardingEnabled = true
        plantTree()

        KLog.i("chatty info line")
        KLog.d("debug line")

        assertTrue(processor.records.isEmpty())
    }

    @Test
    fun klogForwardingOn_eventsKeepTheirEventName() {
        klogForwardingEnabled = true
        plantTree()

        KLog.logEvent("example.joined")

        assertEquals("example.joined", processor.records.single().eventName)
    }

    @Test
    fun klogForwardingOn_killSwitchStillDropsEverything() {
        enabled = false
        klogForwardingEnabled = true
        plantTree()

        KLog.w("warn line")
        KLog.logEvent("app.launched")

        assertTrue(processor.records.isEmpty())
    }

    @Test
    fun klogForwardingOn_sampledOutSession_dropsWarnLogsToo() {
        sampleRate = 0.0
        klogForwardingEnabled = true
        plantTree()

        KLog.w("warn line")

        assertTrue(processor.records.isEmpty())
    }

    @Test
    fun killSwitchOff_dropsExport() {
        enabled = false
        plantTree()

        KLog.logEvent("app.launched")

        assertTrue(processor.records.isEmpty())
    }

    @Test
    fun sampleRateZero_dropsExport() {
        sampleRate = 0.0
        plantTree()

        KLog.logEvent("app.launched")

        assertTrue(processor.records.isEmpty())
    }

    @Test
    fun samplingIsStablePerSession() {
        sampleRate = 0.5
        plantTree()

        KLog.logEvent("example.completed", "step_number" to 1)
        KLog.logEvent("example.completed", "step_number" to 2)

        val counts = processor.records.size
        assertTrue(counts == 0 || counts == 2, "a session's events are all-or-nothing, got $counts of 2")
    }

    /**
     * The half [samplingIsStablePerSession] cannot see. All-or-nothing is
     * satisfied by a tree that keeps everything and by one that keeps nothing,
     * so on its own it stays green while the rate does nothing at all, and the
     * rate is the volume dial the whole Grafana bill hangs off.
     *
     * The two ids were picked once by search and pinned: through
     * `isSessionSampledIn`'s hash they bucket to 0.428 and 0.659, so a rate of
     * 0.5 falls between them and exactly one of the two sessions exports.
     * Anything that flattens the hash, moves the comparison, or drops the rate
     * on the floor puts them back on the same side of it and fails here.
     */
    @Test
    fun theRateDecidesWhichSessionsExport() {
        sampleRate = 0.5
        sessionId = SessionUnderTheRate
        plantTree()

        KLog.logEvent("example.completed", "step_number" to 1)
        sessionId = SessionOverTheRate
        KLog.logEvent("example.completed", "step_number" to 2)

        assertEquals(
            listOf(SessionUnderTheRate),
            processor.records.map { it.attributes["session_id"] },
            "the rate is not deciding anything: both sessions were treated the same",
        )
    }

    /**
     * Loki is a shared Grafana Cloud stack, and a Warn line goes there by
     * default. A token that rode one used to be scrubbed out of the feedback
     * attachment and shipped here in the clear, under our own service name.
     */
    @Test
    fun klogForwardingOn_secretsAreScrubbedOutOfTheBodyAndTheException() {
        klogForwardingEnabled = true
        plantTree()

        KLog.w("POST /v1/logs rejected, authorization: Bearer sk-live-9f3ab2")
        KLog.e("exporter gave up", IllegalStateException("dsn=https://deadbeef@o1.ingest.sentry.io/1"))

        val forwarded = processor.records.map { it.body }.joinToString("\n") +
            processor.records.mapNotNull { it.attributes["exception_message"] }.joinToString("\n")
        assertEquals(2, processor.records.size)
        assertFalse(forwarded.contains("sk-live-9f3ab2"), "a bearer token reached Loki: $forwarded")
        assertFalse(forwarded.contains("deadbeef"), "a dsn reached Loki: $forwarded")
        assertTrue(forwarded.contains("POST /v1/logs rejected"), "the readable part of the line did not survive")
    }

    @Test
    fun isOffline_isStampedAtEmitTime_onEventsAndPlainLogs() {
        klogForwardingEnabled = true
        plantTree()

        KLog.logEvent("net.backend_unreachable", "operation" to "sync")
        offline = true
        KLog.logEvent("conn.reconnecting", "attempt" to 1)
        KLog.w("a warn line while offline")

        assertEquals(false, processor.records[0].attributes["is_offline"])
        assertEquals(true, processor.records[1].attributes["is_offline"])
        assertEquals(true, processor.records[2].attributes["is_offline"])
    }

    @Test
    fun sessionRollover_stampsNewSessionIdOnNextEvent() {
        plantTree()

        KLog.logEvent("app.launched")
        sessionId = "session-uuid-2"
        KLog.logEvent("app.foregrounded")

        assertEquals("session-uuid-1", processor.records[0].attributes["session_id"])
        assertEquals("session-uuid-2", processor.records[1].attributes["session_id"])
    }

    @Test
    fun nullInstallId_omitsAttributeInsteadOfCrashing() {
        installId = null
        plantTree()

        KLog.logEvent("app.launched")

        val record = processor.records.single()
        assertEquals(null, record.attributes["install_id"])
        assertEquals("session-uuid-1", record.attributes["session_id"])
    }

    @Test
    fun nonScalarAttribute_isStringified_andNullsAreDropped() {
        plantTree()

        KLog.logEvent(
            "purchase.failed",
            "error" to CustomError("timeout"),
            "product_id" to null,
            "attempt" to 3,
            "final" to true,
        )

        val record = processor.records.single()
        assertEquals("CustomError(reason=timeout)", record.attributes["error"])
        assertEquals(null, record.attributes["product_id"])
        assertEquals(3L, record.attributes["attempt"])
        assertEquals(true, record.attributes["final"])
    }

    private data class CustomError(val reason: String)

    private companion object {
        /** Buckets to 0.428, so a rate of 0.5 keeps it. */
        const val SessionUnderTheRate = "session-under"

        /** Buckets to 0.659, so the same rate drops it. */
        const val SessionOverTheRate = "session-over"
    }
}
