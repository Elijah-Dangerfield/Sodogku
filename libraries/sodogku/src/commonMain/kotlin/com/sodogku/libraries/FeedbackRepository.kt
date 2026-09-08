package com.sodogku.libraries.sodogku

import com.sodogku.libraries.core.Catching
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Where a player's written report goes — both the feedback page in
 * `:features:settings` and the bug reporter in `:features:home`.
 *
 * There is no Sodogku feedback backend. The app has no accounts and the server
 * serves remote config and nothing else, so a note rides to **Sentry** as a user
 * feedback report attached to a carrier event, along with the build, the commit
 * and the buffered session log. A human reads it; it is just not our own
 * inbox. If Sentry is disabled (or the DSN is unset) the note is logged locally
 * and goes no further — see [Telemetry.captureUserFeedback].
 *
 * It lives here rather than in either feature because both features need it and
 * an `impl` module may not depend on another feature's `impl`.
 */
interface FeedbackRepository {
    suspend fun submitFeedback(
        message: String,
        kind: FeedbackKind,
        logId: String? = null,
        errorCode: Int? = null,
        screenshots: List<ByteArray> = emptyList(),
        includeLogs: Boolean = true,
    ): Catching<Unit>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class FeedbackRepositoryImpl(
    private val telemetry: Telemetry,
) : FeedbackRepository {
    override suspend fun submitFeedback(
        message: String,
        kind: FeedbackKind,
        logId: String?,
        errorCode: Int?,
        screenshots: List<ByteArray>,
        includeLogs: Boolean,
    ): Catching<Unit> = Catching {
        telemetry.captureUserFeedback(
            message = message,
            kind = kind,
            eventId = logId,
            errorCode = errorCode,
            screenshots = screenshots,
            includeLogs = includeLogs,
        )
    }
}
