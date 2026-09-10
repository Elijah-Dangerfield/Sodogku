package com.sodogku.libraries.sodogku.impl

import com.sodogku.libraries.networking.InstallIdProvider
import io.sentry.kotlin.multiplatform.Attachment
import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the `install_id` tag a feedback carrier event leaves with — the only
 * thing tying a report to the records a deletion request asks us to delete
 * (`pages/privacy.html`, "the limits of a deletion request").
 *
 * The bug worth pinning: the id hydrates asynchronously, so whether it is there
 * is a race, and the losing branch used to be "write no tag" — which reads at
 * triage exactly like a tag that was never supposed to be there. So every case
 * asserts the tag's *value*, not merely that something was written.
 *
 * NOT covered here: that `captureUserFeedback` reaches Sentry at all, or what
 * else rides on the carrier event; that needs a live SDK. Hydration itself is
 * [CachedInstallIdProviderTest]. The boot-time scope tag in
 * [SessionTelemetryBinder] is a different path and stays best-effort on
 * purpose.
 */
class FeedbackInstallIdTagTest {

    @Test
    fun resolvedInstallId_isTaggedOnTheReport() {
        val scope = RecordingScope()

        scope.tagInstallId(FakeInstallIdProvider(INSTALL_ID))

        assertEquals(INSTALL_ID, scope.getTags()[INSTALL_ID_TAG])
    }

    @Test
    fun unhydratedInstallId_isTaggedUnavailable_ratherThanLeftOff() {
        val scope = RecordingScope()

        scope.tagInstallId(FakeInstallIdProvider(null))

        assertTrue(
            INSTALL_ID_TAG in scope.getTags(),
            "a report with no install_id tag cannot be told apart from one whose tag went missing",
        )
        assertEquals(INSTALL_ID_UNAVAILABLE, scope.getTags()[INSTALL_ID_TAG])
    }

    @Test
    fun blankInstallId_countsAsNoInstallId() {
        val scope = RecordingScope()

        scope.tagInstallId(FakeInstallIdProvider("   "))

        assertEquals(INSTALL_ID_UNAVAILABLE, scope.getTags()[INSTALL_ID_TAG])
    }

    @Test
    fun providerThatThrows_stillLeavesTheReportTagged() {
        // Telemetry never costs the player their report: a provider breaking
        // its own "cheap and non-suspending" contract lands on the marker
        // instead of unwinding the capture.
        val scope = RecordingScope()

        scope.tagInstallId(ThrowingInstallIdProvider())

        assertEquals(INSTALL_ID_UNAVAILABLE, scope.getTags()[INSTALL_ID_TAG])
    }

    private class FakeInstallIdProvider(private val id: String?) : InstallIdProvider {
        override fun current(): String? = id
    }

    private class ThrowingInstallIdProvider : InstallIdProvider {
        override fun current(): String = error("simulated install id read failure")
    }

    private class RecordingScope : Scope {
        private val tags = mutableMapOf<String, String>()
        private val contexts = mutableMapOf<String, Any>()

        override var user: User? = null
        override var level: SentryLevel? = null

        override fun getTags(): MutableMap<String, String> = tags
        override fun getContexts(): MutableMap<String, Any> = contexts

        override fun setTag(key: String, value: String) {
            tags[key] = value
        }

        override fun removeTag(key: String) {
            tags.remove(key)
        }

        override fun clear() {
            tags.clear()
            contexts.clear()
        }

        override fun setExtra(key: String, value: String) = Unit
        override fun removeExtra(key: String) = Unit
        override fun addAttachment(attachment: Attachment) = Unit
        override fun clearAttachments() = Unit
        override fun addBreadcrumb(breadcrumb: Breadcrumb) = Unit
        override fun clearBreadcrumbs() = Unit
        override fun setContext(key: String, value: Any) = Unit
        override fun setContext(key: String, value: Boolean) = Unit
        override fun setContext(key: String, value: String) = Unit
        override fun setContext(key: String, value: Number) = Unit
        override fun setContext(key: String, value: Collection<*>) = Unit
        override fun setContext(key: String, value: Array<*>) = Unit
        override fun setContext(key: String, value: Char) = Unit
        override fun removeContext(key: String) = Unit
    }

    private companion object {
        // Spelled out rather than imported: the tag name is what triage types
        // into Sentry, so a rename should fail here and be re-decided.
        const val INSTALL_ID_TAG = "install_id"
        const val INSTALL_ID = "f2b8b1c0-0000-4000-8000-000000000001"
    }
}
