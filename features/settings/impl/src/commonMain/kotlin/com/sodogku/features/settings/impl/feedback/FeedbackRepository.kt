package com.sodogku.features.settings.impl.feedback

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.sodogku.Telemetry
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Where a player's note goes.
 *
 * There is no Sodogku feedback backend — the app has no accounts and the server
 * serves remote config and nothing else. The note rides to Sentry as a user
 * feedback report attached to a carrier event, along with the build, the commit
 * and the in-memory session log. That is a real destination a human reads, not
 * a dropped call, but it is Sentry rather than something of ours.
 */
interface FeedbackRepository {
    suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String? = null,
        errorCode: Int? = null,
    ): Catching<Unit>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FeedbackRepositoryImpl @Inject constructor(
    private val telemetry: Telemetry,
) : FeedbackRepository {
    override suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String?,
        errorCode: Int?,
    ): Catching<Unit> = Catching {
        telemetry.captureUserFeedback(
            message = message,
            isBugReport = isBugReport,
            eventId = logId,
            errorCode = errorCode,
        )
    }
}
