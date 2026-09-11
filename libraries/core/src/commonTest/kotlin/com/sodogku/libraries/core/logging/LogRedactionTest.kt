package com.sodogku.libraries.core.logging

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Redaction is a property of the engine, so it is tested at the engine.
 *
 * It used to be a property of one tree, and the other two (Sentry breadcrumbs
 * and the Warn-and-above bodies forwarded to Loki) carried the same line in the
 * clear. Asserting it here, on what any planted tree receives, is what makes it
 * true for a sink nobody has written yet.
 */
class LogRedactionTest {

    private val received = mutableListOf<LogEntry>()

    private val tree = object : LogTree() {
        override fun log(entry: LogEntry): LogId? {
            received += entry
            return null
        }
    }

    @AfterTest
    fun tearDown() {
        KLog.clearTrees()
    }

    @Test
    fun everyTreeGetsTheScrubbedMessage() {
        KLog.plant(tree)

        KLog.w("GET /config failed, authorization: Bearer sk-live-9f3ab2")

        val message = received.single().message.orEmpty()
        assertFalse(message.contains("sk-live-9f3ab2"), "bearer token reached a tree: $message")
        assertTrue(message.startsWith("GET /config failed"), "the readable part did not survive: $message")
    }

    @Test
    fun stringExtrasAndTagValuesAreScrubbedToo() {
        KLog.plant(tree)

        KLog.w { scope ->
            scope.tag("request", "https://api.example.com?token=sk-live-9f3ab2")
            scope.extra("header", "Authorization: Bearer sk-live-9f3ab2")
            scope.extra("status", 500)
            "request failed"
        }

        val entry = received.single()
        assertFalse(
            entry.context.tags.getValue("request").contains("sk-live-9f3ab2"),
            "a token reached a tree on a tag: ${entry.context.tags}",
        )
        assertFalse(
            (entry.context.extras.getValue("header") as String).contains("sk-live-9f3ab2"),
            "a token reached a tree on an extra: ${entry.context.extras}",
        )
        assertEquals(500, entry.context.extras["status"], "a non-string extra was mangled")
    }

    @Test
    fun theEventNameSurvivesTheScrub() {
        KLog.plant(tree)

        KLog.logEvent("ads.result", "outcome" to "Rewarded")

        assertEquals("ads.result", received.single().context.extras[EXTRA_APP_EVENT])
    }

    @Test
    fun aThrowablesMessageIsScrubbedAtTheRead() {
        KLog.plant(tree)

        // The one thing the engine cannot rewrite: the object is passed through
        // by reference so the crash reporter still gets its stack trace, and a
        // sink that renders text off it has to read the scrubbed form.
        KLog.e("upload rejected", IllegalStateException("dsn=https://deadbeef@o1.ingest.sentry.io/1"))

        val entry = received.single()
        assertTrue(entry.throwable is IllegalStateException, "the throwable itself must ride through untouched")
        assertFalse(entry.throwableMessage.orEmpty().contains("deadbeef"), "dsn leaked: ${entry.throwableMessage}")
        assertFalse(entry.throwableText.orEmpty().contains("deadbeef"), "dsn leaked: ${entry.throwableText}")
        assertTrue(entry.throwableText.orEmpty().contains("IllegalStateException"), "the type must survive")
    }

    @Test
    fun aThrowableWithNoLineOfItsOwnStillLandsScrubbed() {
        KLog.plant(tree)

        KLog.e(IllegalStateException("token=sk-live-9f3ab2"))

        val message = received.single().message.orEmpty()
        assertFalse(message.contains("sk-live-9f3ab2"), "the throwable's message became the line unscrubbed: $message")
    }
}
