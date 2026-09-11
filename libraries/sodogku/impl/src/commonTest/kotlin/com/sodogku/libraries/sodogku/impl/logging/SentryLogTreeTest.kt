package com.sodogku.libraries.sodogku.impl.logging

import com.sodogku.libraries.core.ExpectedControlFlow
import com.sodogku.libraries.core.logging.LogContext
import com.sodogku.libraries.core.logging.LogEntry
import com.sodogku.libraries.core.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What reaches Sentry, and what must not.
 *
 * Two questions, both answered without a Sentry client. Whether an entry
 * becomes an event is decided by level and by whether the throwable is control
 * flow the app expected: a deliberate short circuit thrown at error level is
 * noise that would bury real crashes, while an error with no throwable at all
 * is still a report somebody wants. Each of those four combinations gets its
 * own assertion, because a threshold check that ignored the marker interface
 * passes three of them.
 *
 * The other question is redaction, and it is a privacy claim rather than a
 * formatting one. A throwable's message is the one field the logging engine
 * cannot rewrite on its way past, so the scrub has to happen where the tree
 * reads it. A tree that goes back to reading the message directly ships a
 * bearer token off the device, and that is what the last test fails on.
 *
 * ### Not here
 *
 * Everything downstream of the decision: transport, batching and whether Sentry
 * is initialised at all. That the privacy page's claims about Sentry stay true
 * is `NoIdentitySeamsTest` in `:apps:integration`. Scrubbing on the Grafana
 * path is asserted separately in `GrafanaLogTreeTest`, since the two exporters
 * share no code.
 */
class SentryLogTreeTest {

    private val tree = SentryLogTree(
        minBreadcrumbLevel = LogLevel.Info,
        minEventLevel = LogLevel.Error,
    )

    private class ShortCircuit(message: String) : Exception(message), ExpectedControlFlow

    @Test
    fun `expected control-flow throwable at error level is not captured as an event`() {
        assertFalse(tree.shouldCaptureEvent(errorEntry(ShortCircuit("not ready yet"))))
    }

    @Test
    fun `real throwable at error level is captured as an event`() {
        assertTrue(tree.shouldCaptureEvent(errorEntry(IllegalStateException("boom"))))
    }

    @Test
    fun `error-level message without a throwable is captured as an event`() {
        assertTrue(tree.shouldCaptureEvent(errorEntry(throwable = null)))
    }

    @Test
    fun `below-threshold entries are never captured as events`() {
        assertFalse(
            tree.shouldCaptureEvent(
                LogEntry(
                    level = LogLevel.Warn,
                    tag = "test",
                    message = "warn",
                    throwable = IllegalStateException("boom"),
                    context = LogContext.Empty,
                )
            )
        )
    }

    /**
     * The breadcrumb trail and the captured event both render through this, and
     * both leave the device. Redaction is the engine's job now, but a throwable
     * is the one thing it cannot rewrite, so the scrub happens at the read, and
     * a tree that goes back to `entry.throwable?.message` fails here.
     */
    @Test
    fun `a secret in a throwable's message does not reach the rendered line`() {
        val rendered = tree.renderedMessage(
            LogEntry(
                level = LogLevel.Error,
                tag = "Net",
                message = null,
                throwable = IllegalStateException("refresh failed, authorization: Bearer sk-live-9f3ab2"),
                context = LogContext.Empty,
            ),
        )

        assertFalse(rendered.contains("sk-live-9f3ab2"), "bearer token reached Sentry: $rendered")
        assertTrue(rendered.startsWith("refresh failed"), "the readable part did not survive: $rendered")
    }

    private fun errorEntry(throwable: Throwable?): LogEntry = LogEntry(
        level = LogLevel.Error,
        tag = "test",
        message = throwable?.message ?: "error",
        throwable = throwable,
        context = LogContext.Empty,
    )
}
