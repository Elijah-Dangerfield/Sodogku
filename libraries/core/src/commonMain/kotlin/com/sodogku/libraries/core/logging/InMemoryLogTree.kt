package com.sodogku.libraries.core.logging

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlin.time.Clock

/**
 * Keeps the last [capacity] log lines in memory and nowhere else, so a
 * feedback report can carry the session that produced it.
 *
 * This is the only tree that retains what we deliberately do not ship. Sentry
 * takes Info+ as breadcrumbs in release and Grafana takes Warn+; the Debug and
 * Verbose detail that actually explains a bug is dropped by both. It is kept
 * here instead, costs one bounded allocation, and leaves the device only when
 * a reporter ticks "attach logs".
 *
 * It is a tree in its own right rather than a field on the Sentry tree
 * (where this buffer used to live) because the Sentry tree refuses every entry
 * when the DSN is unset. That is exactly a local debug build — the build where
 * someone is most likely to file a report, and where the buffer was therefore
 * always empty.
 *
 * Writes arrive from any thread, so both ends are synchronized.
 */
@OptIn(InternalCoroutinesApi::class)
class InMemoryLogTree(
    private val minLevel: LogLevel = LogLevel.Verbose,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val maxLineChars: Int = DEFAULT_MAX_LINE_CHARS,
    private val now: () -> String = { Clock.System.now().toString() },
) : LogTree() {

    private val lock = SynchronizedObject()
    private val lines = ArrayDeque<String>(capacity)

    override fun isLoggable(level: LogLevel, tag: String?): Boolean =
        level.priority >= minLevel.priority

    override fun log(entry: LogEntry): LogId? {
        if (entry.level.priority < minLevel.priority) return null

        val message = entry.message ?: entry.throwable?.message ?: NO_MESSAGE
        val rendered = "${now()} ${entry.level.name.uppercase()} ${entry.tag ?: "-"}: $message"
        val safe = redactSecrets(rendered)
        val capped = if (safe.length > maxLineChars) safe.take(maxLineChars) + "…" else safe

        synchronized(lock) {
            while (lines.size >= capacity) lines.removeFirst()
            lines.addLast(capped)
        }

        // Never claims an id: this tree is a local mirror, not a destination
        // anyone can link to.
        return null
    }

    /**
     * Newline-joined tail, oldest first. Non-clearing on purpose — a reporter
     * who files twice in a row should get context both times, and the ring
     * overwrites itself anyway.
     */
    fun snapshot(): String = synchronized(lock) { lines.joinToString("\n") }

    fun clear() {
        synchronized(lock) { lines.clear() }
    }

    companion object {
        /**
         * 500 lines is roughly a full level of play plus the navigation that
         * led into it, which is the window a "this just happened" report needs.
         * Paired with [DEFAULT_MAX_LINE_CHARS] it bounds the buffer at ~500KB
         * worst case and a small fraction of that in practice, which is cheap
         * against a game that holds bitmap-backed board state anyway.
         */
        const val DEFAULT_CAPACITY: Int = 500

        /**
         * A log line longer than this is a serialized payload someone pasted
         * into a message, not a log. Truncating keeps one of those from
         * evicting the 499 lines around it that give it meaning.
         */
        const val DEFAULT_MAX_LINE_CHARS: Int = 1000

        private const val NO_MESSAGE = "(no message)"
    }
}
