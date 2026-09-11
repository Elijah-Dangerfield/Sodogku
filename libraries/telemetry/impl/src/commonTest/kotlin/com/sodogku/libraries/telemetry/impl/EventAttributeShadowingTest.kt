package com.sodogku.libraries.telemetry.impl

import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `is_offline` is the one key an event could collide with, and **no production
 * event does**. This pins the rule rather than a live collision.
 *
 * [GrafanaLogTree] stamps `is_offline` on **every** record from
 * `AppState.isOffline` — which trips on our own backend being unreachable, not
 * just on the device losing its network. `ads.gate_shown` wants the narrower
 * device signal, and it takes the other half of this rule: it emits
 * `device_offline` and collides with nothing.
 *
 * What is pinned here is what *would* happen if an event reached for the same
 * key. Which value survives is decided by the order of two lines inside
 * `forward`, the per-record stamp going on first and the event's extras after,
 * and nothing about that ordering announces itself. An event added
 * tomorrow with its own `is_offline`, or a tidy-up that swaps the two blocks,
 * changes what a dashboard reads under a name it thinks it understands, with no
 * warning and no visible change anywhere.
 *
 * The events below are fixtures for that rule, not a description of what the
 * app emits. If an event ever genuinely needs the device signal, give it
 * `device_offline` as `ads.gate_shown` does, rather than reordering the stamps.
 */
class EventAttributeShadowingTest {

    private val processor = RecordingLogRecordProcessor()

    private var deviceOffline = false
    private var appOffline = false

    @AfterTest
    fun tearDown() {
        KLog.clearTrees()
    }

    private fun plantTree() {
        KLog.plant(
            GrafanaLogTree(
                exportEnabled = { true },
                sampleRate = { 1.0 },
                klogForwardingEnabled = { false },
                currentSessionId = { "session-uuid-1" },
                currentInstallId = { "install-uuid-1" },
                isOffline = { appOffline },
                processorFactory = { processor },
            ),
        )
    }

    @Test
    fun anEventsOwnIsOfflineWinsOverThePerRecordStamp() {
        appOffline = true
        deviceOffline = false
        plantTree()

        // An invented event, on purpose: no production event carries its own
        // `is_offline`, and using a real name here would read as documentation
        // of an event that does.
        KLog.logEvent("example.gated", "placement" to "continue_level", "is_offline" to deviceOffline)

        val record = processor.records.single()
        assertEquals(
            false,
            record.attributes["is_offline"],
            "an event's own value must survive; the tree's app-wide stamp is the one to lose",
        )
        assertEquals("continue_level", record.attributes["placement"])
    }

    @Test
    fun theCollisionOnlyAffectsEventsThatCarryTheirOwn() {
        appOffline = true
        plantTree()

        KLog.logEvent("example.started", "level_id" to 7)

        val record = processor.records.single()
        assertEquals(
            true,
            record.attributes["is_offline"],
            "an event with no is_offline of its own still gets the per-record stamp",
        )
    }
}
