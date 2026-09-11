package com.sodogku.libraries.core.logging

import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmInline
import kotlin.native.ObjCName

/**
 * Immutable snapshot of a log event delivered to planted log trees.
 */
data class LogEntry(
    val level: LogLevel,
    val tag: String?,
    val message: String?,
    val throwable: Throwable?,
    val context: LogContext
) {
    /**
     * [throwable]'s own message, and its `toString()`, scrubbed of secrets.
     *
     * The engine scrubs [message] and [context] once before any tree sees them,
     * but a throwable travels by reference and cannot be rewritten: wrapping it
     * to scrub the message would cost the stack trace, which is the reason it is
     * being sent at all. So every sink that renders *text* off a throwable reads
     * it through these rather than off the object. The object itself still
     * reaches the crash reporter unaltered, which is the one place it has to.
     */
    val throwableMessage: String? get() = throwable?.message?.let(::redactSecrets)

    /** See [throwableMessage]. The type-and-message form, for a log body. */
    val throwableText: String? get() = throwable?.toString()?.let(::redactSecrets)
}

/**
 * Represents an optional identifier produced by one of the planted log trees (for example a Sentry
 * event id). We keep it as a simple inline value so it can cross multiplatform boundaries easily.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("LogId", exact = true)
@JvmInline
value class LogId(val raw: String) {
    override fun toString(): String = raw

    companion object {
        fun from(value: String?): LogId? = value?.takeIf { it.isNotBlank() }?.let(::LogId)
    }
}