package com.sodogku.libraries.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryLogTreeTest {

    private fun tree(
        minLevel: LogLevel = LogLevel.Verbose,
        capacity: Int = 4,
        maxLineChars: Int = 200,
    ) = InMemoryLogTree(
        minLevel = minLevel,
        capacity = capacity,
        maxLineChars = maxLineChars,
        now = { "T" },
    )

    private fun entry(
        message: String,
        level: LogLevel = LogLevel.Info,
        tag: String? = "Tag",
    ) = LogEntry(
        level = level,
        tag = tag,
        message = message,
        throwable = null,
        context = LogContext.Empty,
    )

    @Test
    fun holdsTheMostRecentLinesAndDropsTheOldest() {
        val tree = tree(capacity = 3)

        listOf("one", "two", "three", "four").forEach { tree.log(entry(it)) }

        val lines = tree.snapshot().lines()
        assertEquals(3, lines.size, "capacity of 3 must bound the buffer")
        assertTrue(lines[0].endsWith("two"), "oldest surviving line should be 'two', was ${lines[0]}")
        assertTrue(lines[2].endsWith("four"), "newest line should be 'four', was ${lines[2]}")
        assertFalse(tree.snapshot().contains("one"), "'one' should have been evicted")
    }

    @Test
    fun rendersLevelAndTagAlongsideTheMessage() {
        val tree = tree()

        tree.log(entry("board rebuilt", level = LogLevel.Debug, tag = "Board"))

        assertEquals("T DEBUG Board: board rebuilt", tree.snapshot())
    }

    @Test
    fun entriesBelowTheMinimumLevelAreNeitherLoggableNorRetained() {
        val tree = tree(minLevel = LogLevel.Debug)

        assertFalse(tree.isLoggable(LogLevel.Verbose, tag = null))
        assertTrue(tree.isLoggable(LogLevel.Debug, tag = null))

        // Belt and braces: the engine consults isLoggable, but a caller that
        // does not must still not get a Verbose line into a Debug buffer.
        tree.log(entry("per frame churn", level = LogLevel.Verbose))
        tree.log(entry("kept", level = LogLevel.Debug))

        assertEquals("T DEBUG Tag: kept", tree.snapshot())
    }

    @Test
    fun aRunawayLineIsTruncatedRatherThanEvictingItsNeighbours() {
        val tree = tree(capacity = 3, maxLineChars = 40)

        tree.log(entry("before"))
        tree.log(entry("x".repeat(500)))
        tree.log(entry("after"))

        val lines = tree.snapshot().lines()
        assertEquals(3, lines.size)
        assertEquals(41, lines[1].length, "long line should be capped at maxLineChars plus the ellipsis")
        assertTrue(lines[1].endsWith("…"))
        assertTrue(lines[0].endsWith("before"), "the line before the runaway must survive")
        assertTrue(lines[2].endsWith("after"), "the line after the runaway must survive")
    }

    @Test
    fun secretsAreScrubbedBeforeTheyEverEnterTheBuffer() {
        val tree = tree(maxLineChars = 500)

        tree.log(entry("GET /config failed, authorization: Bearer sk-live-9f3ab2"))
        tree.log(entry("init dsn=https://deadbeef@o1.ingest.sentry.io/1"))
        tree.log(entry("mailto elijah@example.com bounced"))

        val dump = tree.snapshot()
        assertFalse(dump.contains("sk-live-9f3ab2"), "bearer token leaked: $dump")
        assertFalse(dump.contains("deadbeef"), "dsn leaked: $dump")
        assertFalse(dump.contains("elijah@example.com"), "email leaked: $dump")
        assertContains(dump, "GET /config failed", message = "the readable part of the line must survive")
    }

    @Test
    fun theSnapshotDoesNotConsumeTheBuffer() {
        val tree = tree()
        tree.log(entry("still here"))

        val first = tree.snapshot()
        val second = tree.snapshot()

        assertTrue(first.isNotEmpty(), "snapshot of a written buffer must not be empty")
        assertEquals(first, second, "a second report must still get context")
    }

    @Test
    fun aThrowableWithNoMessageStillProducesALine() {
        val tree = tree()

        tree.log(
            LogEntry(
                level = LogLevel.Error,
                tag = "Net",
                message = null,
                throwable = IllegalStateException("socket closed"),
                context = LogContext.Empty,
            ),
        )

        assertEquals("T ERROR Net: socket closed", tree.snapshot())
    }

    @Test
    fun clearEmptiesTheBuffer() {
        val tree = tree()
        tree.log(entry("something"))
        assertTrue(tree.snapshot().isNotEmpty())

        tree.clear()

        assertEquals("", tree.snapshot())
    }
}
