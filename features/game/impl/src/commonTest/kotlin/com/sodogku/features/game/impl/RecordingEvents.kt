package com.sodogku.features.game.impl

import com.sodogku.libraries.core.logging.EXTRA_APP_EVENT
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.LogEntry
import com.sodogku.libraries.core.logging.LogId
import com.sodogku.libraries.core.logging.LogTree

/**
 * Captures the app events the board emits, so a test can assert on them.
 *
 * Nothing in this module asserted on a single emitted event before this existed.
 * Twelve `game.*` events feed six Grafana dashboards, and the only thing holding
 * them was `DashboardQueryContractTest`, which scans source text for names — it
 * proves an event is *spelled* the way a panel queries it, and its own docblock
 * says so. It cannot see an attribute that is missing on one branch, or present
 * and computing the wrong thing.
 *
 * `game.commit.on_marked` is why this is here. It was inverted, and pinned
 * inverted: a commit is the second of two taps, so reading the mark state at the
 * commit site reports what the *first* tap just did. Every existing check passed.
 *
 * Planting a tree rather than injecting a logger keeps production code alone —
 * `logEvent` goes through `KLog`, and this is the seam the logging library
 * already offers. Uproot in a `finally`, or the tree outlives the test and
 * collects everything the rest of the suite emits.
 */
class RecordingEvents : LogTree() {

    private val captured = mutableListOf<Pair<String, Map<String, Any?>>>()

    override fun log(entry: LogEntry): LogId? {
        val name = entry.context.extras[EXTRA_APP_EVENT] as? String ?: return null
        captured += name to entry.context.extras.filterKeys { it != EXTRA_APP_EVENT }
        return null
    }

    /** Every event emitted so far, in order, as name to attributes. */
    val all: List<Pair<String, Map<String, Any?>>> get() = captured.toList()

    /** The attributes of every `name` event, in order. */
    fun attributesOf(name: String): List<Map<String, Any?>> =
        captured.filter { it.first == name }.map { it.second }

    /**
     * The only `name` event emitted, failing loudly on none or several.
     *
     * A test that means "the one commit" and silently reads the first of three
     * is the kind of green that hides a duplicate emission.
     */
    fun single(name: String): Map<String, Any?> {
        val matches = attributesOf(name)
        check(matches.size == 1) {
            "expected exactly one $name, got ${matches.size}. All events: ${captured.map { it.first }}"
        }
        return matches.single()
    }
}

/** Runs [block] with a planted [RecordingEvents], uprooting it afterwards. */
inline fun <T> recordingEvents(block: (RecordingEvents) -> T): T {
    val events = RecordingEvents()
    KLog.plant(events)
    return try {
        block(events)
    } finally {
        KLog.uproot(events)
    }
}
