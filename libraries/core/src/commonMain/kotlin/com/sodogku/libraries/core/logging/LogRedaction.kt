package com.sodogku.libraries.core.logging

/**
 * Scrubs the handful of things that must never ride a log line off the device.
 *
 * The in-memory tail is attached to feedback reports, which land in a bug
 * tracker a human browses. Sodogku has no accounts and no user data, so the
 * realistic leak is not "the player's identity" but "a credential that happened
 * to be interpolated into a message" — a DSN, a bearer header echoed while
 * debugging a request, a signed URL. Those are what this removes.
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
