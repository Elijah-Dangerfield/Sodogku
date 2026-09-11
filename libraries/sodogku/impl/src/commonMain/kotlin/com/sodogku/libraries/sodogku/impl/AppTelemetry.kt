package com.sodogku.libraries.sodogku.impl

import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.Platform
import com.sodogku.libraries.core.TelemetryInfo
import com.sodogku.libraries.core.buildType
import com.sodogku.libraries.core.versionString
import com.sodogku.libraries.core.logging.InMemoryLogTree
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.LogLevel
import com.sodogku.libraries.core.logging.Logger
import com.sodogku.libraries.networking.InstallIdProvider
import com.sodogku.libraries.sodogku.FeedbackKind
import com.sodogku.libraries.sodogku.Telemetry
import com.sodogku.libraries.sodogku.impl.logging.DevConsoleWriter
import com.sodogku.libraries.sodogku.impl.logging.KermitLogTree
import com.sodogku.libraries.sodogku.impl.logging.SentryLogTree
import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity as KermitSeverity
import io.sentry.kotlin.multiplatform.Attachment
import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryOptions
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.UserFeedback
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppTelemetry(
    installIdProvider: InstallIdProvider,
) : Telemetry by ConfiguredTelemetry(
    configProvider = { SentryRuntimeConfig.forApp(BuildInfo) },
    installIdProvider = installIdProvider,
)

/**
 * The extension has no [com.sodogku.libraries.sodogku.AppCache] to read the
 * install id out of, and no UI to file feedback from, so it reports without
 * one. A carrier event from here would carry [INSTALL_ID_UNAVAILABLE], which
 * is the truthful answer rather than a gap.
 */
class IosExtensionTelemetry(
    private val configProvider: () -> SentryRuntimeConfig = { SentryRuntimeConfig.forIosExtension(BuildInfo) }
) : Telemetry by ConfiguredTelemetry(configProvider, NoInstallId)

private object NoInstallId : InstallIdProvider {
    override fun current(): String? = null
}

private class ConfiguredTelemetry(
    private val configProvider: () -> SentryRuntimeConfig,
    private val installIdProvider: InstallIdProvider,
) : Telemetry {

    private val logger: Logger = KLog.withTag("Telemetry")
    private var initialized = false

    // The planted session buffer, held so captureUserFeedback can dump it as
    // an attachment. Planted whether or not Sentry is enabled — a local debug
    // build has no DSN, and that is exactly where feedback gets written.
    private var sessionLogTree: InMemoryLogTree? = null

    override fun initialize() {
        if (initialized) return
        initialized = true

        KLog.plant(KermitLogTree())

        // Debug-only: drop Kermit's global min-severity to Verbose so nothing
        // is pre-filtered before reaching any writer. The platform writers
        // (OSLogWriter on iOS, LogcatWriter on Android) handle Info+ natively.
        // [DevConsoleWriter] adds a pretty stdout-only path for Debug-and-
        // below entries, because Android Studio's KMM plugin filters those
        // out of its Run window when running iOS apps. See the writer's
        // header for the full reasoning.
        if (BuildInfo.isDebug) {
            KermitLogger.setMinSeverity(KermitSeverity.Verbose)
            KermitLogger.addLogWriter(DevConsoleWriter())
        }

        val config = configProvider()

        // Before the enabled check on purpose: the buffer is local-only, so it
        // costs nothing to keep and is worthless if it only exists in builds
        // that can already report.
        config.logPolicy.minBufferLevel?.let { level ->
            val tree = InMemoryLogTree(minLevel = level)
            sessionLogTree = tree
            KLog.plant(tree)
        }

        if (!config.isEnabled) {
            logger.i { scope ->
                scope.tag("environment", config.environment)
                scope.tag("platform", config.platformTag)
                "Sentry disabled for ${config.environment}"
            }
            return
        }

        Catching {
            Sentry.init(config::applyTo)
        }.onFailure {
            logger.e(it) { scope ->
                scope.tag("environment", config.environment)
                scope.tag("platform", config.platformTag)
                scope.tag("build_type", config.buildTypeTag)
            }
        }.onSuccess {
            KLog.plant(
                SentryLogTree(
                    minBreadcrumbLevel = config.logPolicy.minBreadcrumbLevel,
                    minEventLevel = config.logPolicy.minEventLevel,
                )
            )
            Sentry.configureScope {
                it.setExtra("platform", config.platformTag)
                it.setExtra("build_type", config.buildTypeTag)
                it.setExtra("release_channel", BuildInfo.releaseChannel)
                // Tags (not extras) so triage can filter issues by the exact
                // commit a build shipped from.
                it.setTag(COMMIT_SHA_KEY, BuildInfo.commitSha)
                it.setTag(COMMIT_BRANCH_KEY, BuildInfo.commitBranch)
            }
            logger.i { scope ->
                scope.extra("environment", config.environment)
                scope.extra("platform", config.platformTag)
                scope.extra("build_type", config.buildTypeTag)
                "Sentry initialized for ${config.environment}"
            }
        }
    }

    override fun setCurrentRoute(route: String) {
        // Best-effort: when Sentry isn't initialized (e.g. disabled
        // environment) configureScope has no scope to mutate, so skip quietly
        // rather than logging on every navigation.
        if (!Sentry.isEnabled()) return
        Sentry.configureScope {
            // Tag = searchable/filterable in the issues list; extra = shown on
            // the event detail.
            it.setTag(ROUTE_KEY, route)
            it.setExtra(ROUTE_KEY, route)
        }
    }

    override fun setSession(sessionId: String) {
        // Best-effort, same scope-persistence reasoning as setCurrentRoute:
        // writing the tag on the scope means a later native crash (turned into
        // an event on next launch) still carries the session it happened in.
        if (!Sentry.isEnabled()) return
        Sentry.configureScope { it.setTag(SESSION_ID_KEY, sessionId) }
    }

    override fun setInstallId(installId: String) {
        if (!Sentry.isEnabled()) return
        Sentry.configureScope { it.setTag(INSTALL_ID_KEY, installId) }
    }

    override fun setContext(key: String, value: String?) {
        if (!Sentry.isEnabled()) return
        Sentry.configureScope {
            if (value.isNullOrBlank()) it.removeTag(key) else it.setTag(key, value)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun captureUserFeedback(
        message: String,
        kind: FeedbackKind,
        eventId: String?,
        errorCode: Int?,
        screenshots: List<ByteArray>,
        includeLogs: Boolean,
    ) {
        val payload = message.trim()
        if (payload.isBlank()) {
            logger.w {
                it.tag(FEEDBACK_KIND_TAG, kind.tag)
                "Ignoring empty feedback payload"
            }
            return
        }

        if (!Sentry.isEnabled()) {
            logger.w {
                it.tag(FEEDBACK_KIND_TAG, kind.tag)
                "Sentry disabled, feedback dropped"
            }
            return
        }

        val isBugReport = kind == FeedbackKind.BugReport

        // The legacy User Feedback API only persists feedback attached to an
        // event Sentry has already ingested — an empty or unknown event id is
        // silently dropped on ingest, which is why feedback never surfaced.
        // `eventId` here is our internal KLog id (or null for general feedback),
        // never a real Sentry id, so mint a carrier event via captureMessage and
        // attach the feedback to that. Mirrors Sentry's documented
        // captureMessage → captureUserFeedback flow. The KLog id / error code
        // ride along in the comment for correlation back to the logs.
        // Mint a unique id for this report and stamp it on a LOCAL scope for
        // just the carrier event: beforeSend reads it to fingerprint the event
        // into its own issue (see init), and the in-memory log buffer rides
        // along as an attachment — the fine-grained Debug/Verbose we never ship
        // as breadcrumbs, captured only when the user actually files feedback.
        // Local scope means none of this leaks onto later events.
        val logDump = if (includeLogs) sessionLogTree?.snapshot()?.takeIf { it.isNotBlank() } else null
        val feedbackId = Uuid.random().toString()
        val sentryId = Sentry.captureMessage(kind.carrierMessage) { scope ->
            scope.setTag(FEEDBACK_EVENT_TAG, feedbackId)
            // The one thing triage filters on. See [FeedbackKind] for why it is
            // a tag and not part of the message.
            scope.setTag(FEEDBACK_KIND_TAG, kind.tag)
            // Read now rather than trusting the boot-time scope. See
            // [tagInstallId] for why a feedback report cannot go out without
            // this answered one way or the other.
            scope.tagInstallId(installIdProvider)

            // Info, not the default. A feedback report is not an error and a
            // Sentry issue at error level pulls triage toward it as if it were
            // one -- it sorts with crashes, and it reads as one in the list.
            scope.level = SentryLevel.INFO

            // The message goes on the carrier event as well as into
            // captureUserFeedback, and this duplication is the fix rather than
            // an oversight.
            //
            // The legacy User Feedback API is the only one this SDK has -- 0.26
            // has no `captureFeedback`/`SentryFeedback` either, so upgrading
            // does not help -- and where its comments render depends on the
            // Sentry org's feedback settings. Reported from the field: the log
            // attachment and the screenshot were both visible on the issue and
            // the typed message was nowhere. Attachments and extras are shown on
            // the issue page unconditionally, so putting the payload there makes
            // it visible on the same page as the evidence it explains.
            scope.setExtra(FEEDBACK_MESSAGE_KEY, payload)
            scope.addAttachment(
                Attachment(payload.encodeToByteArray(), "feedback.txt", "text/plain"),
            )
            if (logDump != null) {
                scope.addAttachment(Attachment(logDump.encodeToByteArray(), "session-log.txt", "text/plain"))
            }
            // User-attached screenshots ride along as image attachments, so a
            // triager sees the report and what it's about side by side. Capped
            // and skip-empty defensively; the picker already downscales them.
            screenshots.asSequence()
                .filter { it.isNotEmpty() }
                .take(MAX_FEEDBACK_SCREENSHOTS)
                .forEachIndexed { index, bytes ->
                    scope.addAttachment(Attachment(bytes, "screenshot-${index + 1}.jpg", "image/jpeg"))
                }
        }

        val feedback = UserFeedback(sentryId).apply {
            comments = buildString {
                // Build provenance up top so triage can tell which code
                // produced the report without cross-referencing tags —
                // and whether it's already fixed on a later commit.
                append("Build: ${BuildInfo.versionString()} @ ${BuildInfo.commitSha} (${BuildInfo.commitBranch})\n")
                if (isBugReport) {
                    errorCode?.let { append("Error code: $it\n") }
                    eventId?.let { append("Log ID: $it\n") }
                }
                append('\n')
                append(payload)
            }
            // No `email`. `UserFeedback` has the field; the form has no box to
            // fill it from, and the privacy policy says so out loud.
        }

        Sentry.captureUserFeedback(feedback)

        logger.i { scope ->
            scope.tag(FEEDBACK_KIND_TAG, kind.tag)
            scope.extra("event_id", sentryId.toString())
            if (isBugReport) {
                errorCode?.let { scope.extra("error_code", it) }
            }
            scope.extra("payload_length", payload.length)
            scope.extra("attached_logs", logDump != null)
            scope.extra("attached_screenshots", screenshots.count { it.isNotEmpty() })
            "Feedback forwarded to Sentry (${kind.tag})"
        }
    }
}

/**
 * Writes the `install_id` tag onto a feedback carrier event, with a value
 * either way.
 *
 * `pages/privacy.html` sends deletion requests through the in-app feedback form
 * rather than an email address, because the report arrives keyed to this id and
 * an email matches nothing we hold. So a report is only as useful as its tag.
 *
 * The tag used to arrive here by inheritance from the scope
 * [SessionTelemetryBinder] writes at boot, which sets it only if the async
 * `AppCache` read has already landed and then only re-tries on a session
 * rollover. A report filed in the first seconds of a cold boot could carry
 * nothing, silently. Reading at capture time instead puts the read after the
 * player has navigated to the form and typed, by which point hydration is long
 * done, and writing it on the carrier's own local scope means the report
 * carries it even if the global scope never got one.
 *
 * When there genuinely is no id, the tag says [INSTALL_ID_UNAVAILABLE] rather
 * than being left off. An absent tag cannot be told apart from a tag lost in
 * transit, and the two want different responses from whoever reads the report.
 *
 * Nothing here may cost the player their report: [InstallIdProvider.current] is
 * a cached field by contract, and a throw from a provider that breaks that
 * contract lands on the same marker instead of unwinding the capture.
 */
internal fun Scope.tagInstallId(provider: InstallIdProvider) {
    val installId = Catching { provider.current() }.getOrNull()?.takeIf { it.isNotBlank() }
    setTag(INSTALL_ID_KEY, installId ?: INSTALL_ID_UNAVAILABLE)
}

/**
 * Stands in for the install id on a report filed before the id could be read.
 * Queryable (`install_id:unavailable` finds every one of them) and impossible
 * to mistake for the UUID it replaces.
 */
internal const val INSTALL_ID_UNAVAILABLE = "unavailable"

/**
 * Where the typed report lands on the carrier event, so it is readable next to
 * the attachments rather than only wherever Sentry decides to render legacy
 * user feedback.
 */
private const val FEEDBACK_MESSAGE_KEY = "feedback_message"

// Scope key for the current navigation route (set via [Telemetry.setCurrentRoute]).
// Shared by the tag and the extra so they read identically in Sentry.
private const val ROUTE_KEY = "route"

// Correlation keys mirrored on the backend (OTel span attributes + log
// fields), so the same value queries Sentry, Tempo, and Loki.
private const val SESSION_ID_KEY = "session_id"
private const val INSTALL_ID_KEY = "install_id"

// Build provenance: the exact commit + branch the installed binary was
// produced from, baked into the generated BuildConfig at build time. Lets
// triage pin a report to code and spot already-fixed-on-main issues.
private const val COMMIT_SHA_KEY = "commit_sha"
private const val COMMIT_BRANCH_KEY = "commit_branch"

// Per-feedback id stamped on the carrier event; `beforeSend` turns it into the
// event fingerprint so each feedback report is its own Sentry issue despite the
// shared "User feedback" / "Bug report" message.
private const val FEEDBACK_EVENT_TAG = "feedback_event"
private const val FEEDBACK_FINGERPRINT = "feedback"

// What the triage routine queries: `feedback_kind:owner_directive` finds every
// report the owner filed and nothing else. Values are [FeedbackKind.tag].
internal const val FEEDBACK_KIND_TAG = "feedback_kind"

// Hard cap on attached screenshots, mirrored on the UI side. Defensive: the
// picker already limits selection, this just guarantees a malformed caller
// can't flood Sentry.
private const val MAX_FEEDBACK_SCREENSHOTS = 3

data class SentryRuntimeConfig(
    val dsn: String,
    val environment: String,
    val release: String,
    val sendDefaultPii: Boolean,
    val attachStacktrace: Boolean,
    val tracesSampleRate: Double?,
    val platformTag: String,
    val buildTypeTag: String,
    val logPolicy: LogPolicy,
    val enableAutoSessionTracking: Boolean
) {
    val isEnabled: Boolean get() = dsn.isNotBlank()

    /** Maps this config onto the SDK's [SentryOptions] — the single seam
     *  between our config surface and Sentry's knobs. */
    internal fun applyTo(options: SentryOptions) {
        options.dsn = dsn
        options.environment = environment
        options.release = release
        options.sendDefaultPii = sendDefaultPii
        options.attachStackTrace = attachStacktrace
        options.enableAutoSessionTracking = enableAutoSessionTracking
        // Deliberately never touches options.sampleRate: that knob samples
        // *error events*, and every error/feedback/crash must ship. Traces
        // are the only thing we sample (statistical data, heavy volume).
        tracesSampleRate?.let { options.tracesSampleRate = it }
        // Every feedback carrier event has an identical message
        // ("User feedback" / "Bug report") and no stacktrace, so Sentry
        // would group them all into one issue. Give each its own
        // fingerprint (keyed by a per-feedback id set in
        // captureUserFeedback) so every report is its own issue —
        // individually triageable and resolvable. Other events fall
        // through untouched.
        options.beforeSend = { event ->
            event.getTag(FEEDBACK_EVENT_TAG)?.let { id ->
                event.fingerprint = mutableListOf(FEEDBACK_FINGERPRINT, id)
            }
            event
        }
    }

    data class LogPolicy(
        val minBreadcrumbLevel: LogLevel,
        val minEventLevel: LogLevel,
        /**
         * Lowest level retained by the planted
         * [com.sodogku.libraries.core.logging.InMemoryLogTree], whose tail is
         * attached to feedback reports (null = no buffer). Set *below*
         * [minBreadcrumbLevel] so it keeps the fine-grained detail we never
         * ship: debug buffers Verbose+, release buffers Debug+, which skips
         * per-frame Verbose churn.
         *
         * Read outside the `isEnabled` check in `initialize`, so the buffer
         * exists in builds with no DSN too.
         */
        val minBufferLevel: LogLevel? = null,
    )

    companion object {
        fun forApp(buildInfo: BuildInfo): SentryRuntimeConfig {
            val platformTag = when (buildInfo.platform) {
                Platform.Android -> "android"
                Platform.iOS -> "ios"
            }
            val buildTypeTag = buildInfo.buildType
            // All platforms / build types report to a single Sentry project.
            // The `environment` tag (releaseChannel-platform-buildType) and the
            // `platform` extra separate debug vs release and iOS vs Android
            // within it, so one DSN is enough. Injected at build time (CI env /
            // local.properties — see loadTelemetryMetadata in build-logic);
            // blank leaves crash reporting disabled.
            val dsn = TelemetryInfo.sentryDsn
            val environment = "${buildInfo.releaseChannel}-$platformTag-$buildTypeTag"
            // Must stay in lockstep with the release-upload step in the iOS
            // deploy lane — Sentry attaches events to releases by exact
            // string match.
            val release = "sodogku@${buildInfo.versionName}+${buildInfo.buildNumber}"
            val tracesSampleRate = if (buildInfo.isDebug) 1.0 else 0.15
            val breadcrumbLevel = if (buildInfo.isDebug) LogLevel.Debug else LogLevel.Info
            return SentryRuntimeConfig(
                dsn = dsn,
                environment = environment,
                release = release,
                sendDefaultPii = false,
                attachStacktrace = true,
                tracesSampleRate = tracesSampleRate,
                platformTag = platformTag,
                buildTypeTag = buildTypeTag,
                logPolicy = LogPolicy(
                    minBreadcrumbLevel = breadcrumbLevel,
                    minEventLevel = LogLevel.Error,
                    minBufferLevel = if (buildInfo.isDebug) LogLevel.Verbose else LogLevel.Debug,
                ),
                enableAutoSessionTracking = true
            )
        }

        fun forIosExtension(buildInfo: BuildInfo): SentryRuntimeConfig {
            val base = forApp(buildInfo)
            val environment = "${buildInfo.releaseChannel}-ios-extension-${buildInfo.buildType}"
            return base.copy(
                environment = environment,
                release = base.release + "-extension",
                tracesSampleRate = if (buildInfo.isDebug) 0.25 else 0.05,
                logPolicy = LogPolicy(
                    minBreadcrumbLevel = LogLevel.Info,
                    minEventLevel = LogLevel.Error
                ),
                enableAutoSessionTracking = false
            )
        }
    }
}
