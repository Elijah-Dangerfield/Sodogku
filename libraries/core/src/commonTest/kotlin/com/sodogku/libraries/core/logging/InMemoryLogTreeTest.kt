package com.sodogku.libraries.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ring buffer a bug report carries, and the four ways it lets somebody
 * down.
 *
 * Its whole reason to exist is the last few hundred lines before something went
 * wrong, so the failures are all about what is missing when a reporter finally
 * looks. It drops the oldest line rather than the newest. A runaway line is
 * truncated in place rather than evicting its neighbours, which is the bug that
 * loses exactly the context around a five hundred character dump. A snapshot
 * does not consume the buffer, so a second report still gets something. And an
 * entry below the minimum level is refused at the write as well as at
 * `isLoggable`, because a caller that checks neither must not get verbose churn
 * into a debug buffer.
 *
 * The scrub test goes through `KLog` rather than straight into the tree, and
 * that is deliberate. Redaction moved into the engine so the two sinks that
 * leave the device would get it too, which means the tree alone no longer
 * scrubs anything. Asserting on the path a real line takes is the only way this
 * still proves what it claims.
 *
 * The rendered format is pinned literally, since a reporter reads it and
 * nothing else parses it.
 *
 * ### Not here
 *
 * The other two trees. What Sentry captures is `SentryLogTreeTest`, and what
 * Grafana exports is `GrafanaLogTreeTest`, both of which scrub on their own
 * paths.
 */
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
        // Through `KLog`, not straight into the tree. The scrub moved to the
        // engine so that the two sinks that leave the device get it too, and
        // what this test is for is the buffer a reporter can attach, so it has
        // to be asserted over the path a real line takes to reach it.
        val tree = tree(maxLineChars = 500)
        KLog.plant(tree)

        try {
            KLog.i("GET /config failed, authorization: Bearer sk-live-9f3ab2")
            KLog.i("init dsn=https://deadbeef@o1.ingest.sentry.io/1")
            KLog.i("mailto elijah@example.com bounced")
        } finally {
            KLog.clearTrees()
        }

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
