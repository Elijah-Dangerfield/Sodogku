package com.sodogku.libraries.telemetry.impl

import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.model.ReadableLogRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Telemetry that cannot reach the gateway must not take the app down with it.
 *
 * The repro is airplane mode: the OTLP exporter throws an unresolved-host
 * error from inside `export`, and the batch processor runs it on a coroutine
 * with no handler, so a failed upload becomes a crash in a build that is
 * otherwise fine. The wrapper turns any throwable into a `Failure` result, on
 * all three entry points, because a crash caused by reporting is the one bug
 * that hides every other bug behind it.
 *
 * Cancellation is deliberately the exception to that. Swallowing it would leave
 * a dead scope's coroutine running and break structured concurrency, so it
 * still propagates, and that is asserted rather than assumed.
 *
 * ### Not here
 *
 * What gets exported, and how it is shaped, is `OtlpJsonLogRecordExporterTest`
 * and `GrafanaLogTreeTest`. This file only cares that nothing escapes.
 */
class FailSafeLogRecordExporterTest {

    private class ThrowingExporter(private val throwable: Throwable) : LogRecordExporter {
        override suspend fun export(telemetry: List<ReadableLogRecord>): OperationResultCode =
            throw throwable

        override suspend fun forceFlush(): OperationResultCode = throw throwable

        override suspend fun shutdown(): OperationResultCode = throw throwable
    }

    @Test
    fun exporterException_becomesFailureInsteadOfEscaping() = runTest {
        // The airplane-mode repro: the OTLP exporter throws UnknownHostException
        // from inside export and the batch processor's coroutine has no handler.
        val exporter = FailSafeLogRecordExporter(
            ThrowingExporter(RuntimeException("Unable to resolve host otlp-gateway")),
        )

        assertEquals(OperationResultCode.Failure, exporter.export(emptyList()))
        assertEquals(OperationResultCode.Failure, exporter.forceFlush())
        assertEquals(OperationResultCode.Failure, exporter.shutdown())
    }

    @Test
    fun successfulExport_passesThrough() = runTest {
        val exporter = FailSafeLogRecordExporter(
            object : LogRecordExporter {
                override suspend fun export(telemetry: List<ReadableLogRecord>): OperationResultCode =
                    OperationResultCode.Success

                override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

                override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
            },
        )

        assertEquals(OperationResultCode.Success, exporter.export(emptyList()))
    }

    @Test
    fun cancellation_stillPropagates() = runTest {
        val exporter = FailSafeLogRecordExporter(ThrowingExporter(CancellationException("scope died")))

        assertFailsWith<CancellationException> { exporter.export(emptyList()) }
    }
}
