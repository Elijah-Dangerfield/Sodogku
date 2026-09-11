package com.sodogku.libraries.core.logging

/**
 * Scrubs the handful of things that must never ride a log line off the device.
 *
 * A log line has three exits: the in-memory tail a reporter can attach to
 * feedback, the Sentry breadcrumb trail, and the Warn-and-above bodies
 * forwarded to Loki. Sodogku has no accounts and no user data, so the realistic
 * leak is not "the player's identity" but "a credential that happened to be
 * interpolated into a message": a DSN, a bearer header echoed while debugging a
 * request, a signed URL. Those are what this removes, on all three.
 *
 * Deliberately narrow. Redacting aggressively (every query string, every long
 * hex run) would eat the session ids, level ids and request paths that make a
 * tail worth reading, and a log tail nobody can read is the same as no log
 * tail. What survives is by design: session and install ids (they are the join
 * key to Grafana and Sentry), route names, timings.
 *
 * Order matters. [BEARER] runs first because [KEYED_SECRET] would otherwise
 * match `Authorization:` and stop at the space, redacting the scheme word and
 * leaving the token behind it in the clear.
 */
internal fun redactSecrets(line: String): String =
    line
        .replace(BEARER, "$1 $REDACTED")
        .replace(JWT, REDACTED)
        .replace(KEYED_SECRET, "$1$REDACTED")
        .replace(EMAIL, REDACTED)

/**
 * [redactSecrets] over everything on an entry a tree might render: the message
 * and every string in the context.
 *
 * Applied once by the engine before fan-out, and deliberately not by each tree.
 * It ran in [InMemoryLogTree] alone for a while, which is the one sink that
 * never leaves the device unless a reporter attaches it — the Sentry breadcrumb
 * trail and the Warn-and-above bodies forwarded to Loki both leave, and both saw
 * the same line in the clear. The guard was sitting on the safest of the three
 * exits.
 *
 * The throwable is left alone here; [LogEntry.throwableMessage] says why.
 * A non-string extra is left alone too: it is a number, a boolean or a domain
 * object, and the ones that stringify do so at the sink.
 */
internal fun LogEntry.scrubbed(): LogEntry = copy(
    message = message?.let(::redactSecrets),
    context = context.scrubbed(),
)

private fun LogContext.scrubbed(): LogContext {
    if (isEmpty()) return this
    return LogContext(
        tags = tags.mapValues { (_, value) -> redactSecrets(value) },
        // The event name is a constant this repo wrote, never a credential, and
        // it is the one extra on the hot path — every `logEvent` carries it.
        extras = extras.mapValues { (key, value) ->
            if (key == EXTRA_APP_EVENT || value !is String) value else redactSecrets(value)
        },
    )
}

private const val REDACTED = "<redacted>"

/**
 * `token=abc`, `"apiKey": "abc"`, `secret => abc`. Group 1 swallows the key and
 * whatever separator was used, so a reader still sees *which* credential was in
 * play and the line keeps its original punctuation; only the value dies.
 */
private val KEYED_SECRET =
    Regex(
        """\b(\w*(?:token|secret|password|passwd|pwd|api[_-]?key|apikey|auth|authorization|credential|signature|dsn)\w*\s*["']?\s*(?:=>|[=:])\s*["']?)[^\s"',&}\])]+""",
        RegexOption.IGNORE_CASE,
    )

/** `Authorization: Bearer xxx`. */
private val BEARER = Regex("""\b(Bearer|Basic)\s+[A-Za-z0-9\-._~+/]+=*""", RegexOption.IGNORE_CASE)

/** Three base64url segments. Nothing else in a log line looks like this. */
private val JWT = Regex("""\beyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]+""")

private val EMAIL = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
