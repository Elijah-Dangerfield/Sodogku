package com.sodogku.devfeedback

import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.sodogku.FeedbackKind
import com.sodogku.libraries.sodogku.FeedbackRepository
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The owner's directive channel. Everything typed here is filed to Sentry as
 * [FeedbackKind.OwnerDirective], which is what the `feedback-triage` skill
 * searches for and turns into a TODO item.
 *
 * A singleton rather than a per-open instance: the panel is an overlay that can
 * be opened from anywhere, and a half-written directive should survive
 * dismissing the panel to go look at the thing being complained about.
 */
@Inject
@SingleIn(AppScope::class)
class DevFeedbackViewModel(
    private val repository: FeedbackRepository,
) : SEAViewModel<DevFeedbackState, Unit, DevFeedbackAction>(
    initialStateArg = DevFeedbackState(),
) {

    override suspend fun handleAction(action: DevFeedbackAction) {
        when (action) {
            is DevFeedbackAction.Open -> action.updateState {
                it.copy(isOpen = true, screenshot = action.screenshot, sent = false)
            }
            DevFeedbackAction.Dismiss -> action.updateState { it.copy(isOpen = false) }
            is DevFeedbackAction.MessageChanged -> action.updateState {
                it.copy(message = action.value.take(DirectiveCharLimit))
            }
            is DevFeedbackAction.IncludeLogsChanged -> action.updateState {
                it.copy(includeLogs = action.value)
            }
            DevFeedbackAction.RemoveScreenshot -> action.updateState { it.copy(screenshot = null) }
            DevFeedbackAction.Submit -> action.submit()
        }
    }

    /**
     * Failure is not surfaced. The repository has already logged it, and the
     * only recovery a developer has is to retype the same directive into the
     * same disabled Sentry. Clearing the form and confirming is the honest
     * outcome either way.
     */
    private suspend fun DevFeedbackAction.submit() {
        val message = state.message.trim()
        if (message.isEmpty()) return

        val includeLogs = state.includeLogs
        val screenshot = state.screenshot
        updateState { it.copy(isSubmitting = true) }

        repository.submitFeedback(
            message = message,
            kind = FeedbackKind.OwnerDirective,
            screenshots = listOfNotNull(screenshot?.bytes),
            includeLogs = includeLogs,
        ).logOnFailure { "Owner directive failed to reach Sentry" }

        updateState {
            DevFeedbackState(isOpen = true, sent = true)
        }
    }
}

/** Long enough for a paragraph of intent, short enough to stay one ask. */
const val DirectiveCharLimit: Int = 1000

data class DevFeedbackState(
    val isOpen: Boolean = false,
    val message: String = "",
    /**
     * Default on. This surface only exists in builds whose only users are the
     * people building the app, so there is no stranger's privacy to protect,
     * and a directive filed without its logs is the one that costs a round trip
     * later. Turning it off is one tap for the rare case where the tail is
     * noise.
     */
    val includeLogs: Boolean = true,
    /**
     * Captured from the screen the panel slid over, before it covered it.
     * Pre-attached rather than picked, because the frame the owner was looking
     * at when they reached for the edge is almost always the one they mean.
     */
    val screenshot: Screenshot? = null,
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
) {
    val canSubmit: Boolean get() = message.isNotBlank() && !isSubmitting
}

sealed interface DevFeedbackAction {
    data class Open(val screenshot: Screenshot?) : DevFeedbackAction
    data object Dismiss : DevFeedbackAction
    data class MessageChanged(val value: String) : DevFeedbackAction
    data class IncludeLogsChanged(val value: Boolean) : DevFeedbackAction
    data object RemoveScreenshot : DevFeedbackAction
    data object Submit : DevFeedbackAction
}
