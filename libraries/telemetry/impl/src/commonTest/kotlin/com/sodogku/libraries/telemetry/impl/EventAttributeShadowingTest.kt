package com.sodogku.libraries.telemetry.impl

import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `is_offline` is the one key an event can collide with.
 *
 * [GrafanaLogTree] stamps `is_offline` on **every** record from
 * `AppState.isOffline` — which trips on our own backend being unreachable, not
 * just on the device losing its network. `ads.gate_shown` then emits its own
 * `is_offline` from `AppState.isDeviceOffline`, deliberately, because a backend
 * outage is not an ad-network outage.
 *
 * Two different meanings, one key, and the ad funnel reads it as the device
 * signal. Which one survives is decided by the order of two lines inside
 * `forward` — the per-record stamp goes on first and the event's extras go on
 * after — and nothing about that ordering announces itself. Swap the two blocks
 * while tidying and `ads.gate_shown` silently starts reporting backend
 * reachability under a name the dashboard reads as connectivity, with no test,
 * no warning and no visible change anywhere.
 *
 * So it is pinned. If this ever has to change, the fix is to rename the event's
 * attribute (`device_offline`) rather than to reorder the stamping.
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

        KLog.logEvent("ads.gate_shown", "placement" to "level_complete", "is_offline" to deviceOffline)

        val record = processor.records.single()
        assertEquals(
            false,
            record.attributes["is_offline"],
            "the ad gate's own device signal must survive; the tree's app-wide stamp is the one to lose",
        )
        assertEquals("level_complete", record.attributes["placement"])
    }

    @Test
    fun theCollisionOnlyAffectsEventsThatCarryTheirOwn() {
        appOffline = true
        plantTree()

        KLog.logEvent("game.level_started", "level_id" to 7)

        val record = processor.records.single()
        assertEquals(
            true,
            record.attributes["is_offline"],
            "an event with no is_offline of its own still gets the per-record stamp",
        )
    }
}
