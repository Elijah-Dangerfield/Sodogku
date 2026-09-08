package com.sodogku.features.settings.impl.feedback

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.sodogku.AppCache
import me.tatarka.inject.annotations.Inject

/** How long a note may be. Long enough to describe a bug, short enough to read. */
const val FeedbackCharLimit: Int = 400

@Inject
class FeedbackViewModel(
    private val repository: FeedbackRepository,
    private val appCache: AppCache,
) : SEAViewModel<FeedbackState, FeedbackEvent, FeedbackAction>(
    initialStateArg = FeedbackState(),
) {

    override suspend fun handleAction(action: FeedbackAction) {
        when (action) {
            FeedbackAction.Back -> sendEvent(FeedbackEvent.NavigateBack)
            is FeedbackAction.MessageChanged -> action.updateMessage(action.value)
            FeedbackAction.Submit -> action.submit()
        }
    }

    private suspend fun FeedbackAction.updateMessage(value: String) {
        val limited = value.take(FeedbackCharLimit)
        updateState { it.copy(message = limited, showEmptyError = false) }
    }

    /**
     * A send is never reported as failed. The note is already logged locally by
     * the repository, and telling someone their thank-you note bounced invites
     * them to type it again into the same void. The counter and the
     * confirmation are what the player gets either way.
     */
    private suspend fun FeedbackAction.submit() {
        val message = state.message.trim()
        if (message.isEmpty()) {
            updateState { it.copy(showEmptyError = true) }
            return
        }

        updateState { it.copy(isSubmitting = true, showEmptyError = false) }

        repository.submitFeedback(message = message, isBugReport = false)
            .logOnFailure { "Feedback submission failed" }

        Catching { appCache.update { it.copy(feedbacksGiven = it.feedbacksGiven + 1) } }
            .logOnFailure { "Failed to count the feedback" }

        updateState { it.copy(isSubmitting = false, sent = true) }
    }
}

data class FeedbackState(
    val message: String = "",
    val isSubmitting: Boolean = false,
    val showEmptyError: Boolean = false,
    /** Swaps the form for the thank-you panel. */
    val sent: Boolean = false,
)

sealed interface FeedbackEvent {
    data object NavigateBack : FeedbackEvent
}

sealed interface FeedbackAction {
    data object Back : FeedbackAction
    data class MessageChanged(val value: String) : FeedbackAction
    data object Submit : FeedbackAction
}
