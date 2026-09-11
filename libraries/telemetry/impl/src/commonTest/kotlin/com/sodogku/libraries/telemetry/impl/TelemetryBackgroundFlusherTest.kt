package com.sodogku.libraries.telemetry.impl

import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app going to the background is the last chance to upload what it logged.
 *
 * Two opposite mistakes, and both of them are quiet. Not flushing loses
 * everything a session buffered, because the process may never be foregrounded
 * again. Flushing unconditionally builds the whole OTLP stack on an edge that
 * nothing ever logged through, which on a cold launch straight to background is
 * work a player pays for and no dashboard ever sees.
 *
 * So the flusher is asserted from both ends: a background edge after a real log
 * call flushes once, and a background edge with a planted tree that nothing has
 * logged through flushes zero times.
 *
 * ### Not here
 *
 * What the tree does with a record, including sampling and offline handling, is
 * `GrafanaLogTreeTest`. Failures inside the exporter are
 * `FailSafeLogRecordExporterTest`.
 */
class TelemetryBackgroundFlusherTest : CoroutineTest() {

    private class FlushCountingProcessor : LogRecordProcessor {
        var flushCount = 0
        override fun onEmit(log: ReadWriteLogRecord, context: Context) = Unit
        override suspend fun forceFlush(): OperationResultCode {
            flushCount++
            return OperationResultCode.Success
        }

        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private val processor = FlushCountingProcessor()

    @AfterTest
    fun tearDown() {
        KLog.clearTrees()
    }

    private fun plantTree(): GrafanaLogTree {
        val tree = GrafanaLogTree(
            exportEnabled = { true },
            sampleRate = { 1.0 },
            klogForwardingEnabled = { false },
            currentSessionId = { "session-uuid-1" },
            currentInstallId = { "install-uuid-1" },
            isOffline = { false },
            processorFactory = { processor },
        )
        KLog.plant(tree)
        return tree
    }

    @Test
    fun backgroundEdge_flushesThePlantedTree() = runUnitTest {
        val flusher = TelemetryBackgroundFlusher(AppCoroutineScope(dispatchers))
        flusher.onBackground(AppEvent.OnBackground)

        flusher.tree = plantTree()
        KLog.logEvent("example.completed")
        flusher.onBackground(AppEvent.OnBackground)

        assertEquals(1, processor.flushCount)
    }

    @Test
    fun flush_neverConstructsAnSdkThatNothingLoggedThrough() = runUnitTest {
        val flusher = TelemetryBackgroundFlusher(AppCoroutineScope(dispatchers))
        flusher.tree = plantTree()

        flusher.onBackground(AppEvent.OnBackground)

        assertEquals(0, processor.flushCount)
    }
}
