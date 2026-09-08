package com.sodogku.features.settings.impl

import com.sodogku.features.settings.impl.feedback.FeedbackAction
import com.sodogku.features.settings.impl.feedback.FeedbackCharLimit
import com.sodogku.features.settings.impl.feedback.FeedbackViewModel
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import com.sodogku.libraries.sodogku.FeedbackRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.sodogku.libraries.flowroutines.testing.CoroutineTest

class FeedbackViewModelTest : CoroutineTest() {

    @Test
    fun sendingForwardsTheNoteCountsItAndConfirms() = runUnitTest {
        val repository = RecordingRepository()
        val cache = InMemoryAppCache()
        val vm = FeedbackViewModel(repository, cache)

        vm.takeAction(FeedbackAction.MessageChanged("  the 9x9 levels are the best ones  "))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals("the 9x9 levels are the best ones", repository.sent.single())
        assertEquals(1, cache.get().feedbacksGiven)
        assertTrue(vm.state.sent)
        assertFalse(vm.state.isSubmitting)
    }

    @Test
    fun anEmptyNoteIsNotSent() = runUnitTest {
        val repository = RecordingRepository()
        val cache = InMemoryAppCache()
        val vm = FeedbackViewModel(repository, cache)

        vm.takeAction(FeedbackAction.MessageChanged("   "))
        vm.takeAction(FeedbackAction.Submit)

        assertTrue(repository.sent.isEmpty())
        assertEquals(0, cache.get().feedbacksGiven)
        assertFalse(vm.state.sent)
        assertTrue(vm.state.showEmptyError)
    }

    @Test
    fun theNoteIsConfirmedEvenWhenTheReportFailsToLeave() = runUnitTest {
        // Sentry may be disabled or offline. The player is not told their
        // thank-you note bounced — that only invites them to retype it into the
        // same void — and the counter still moves.
        val cache = InMemoryAppCache()
        val vm = FeedbackViewModel(FailingRepository(), cache)

        vm.takeAction(FeedbackAction.MessageChanged("the bones look like blobs"))
        vm.takeAction(FeedbackAction.Submit)

        assertTrue(vm.state.sent)
        assertEquals(1, cache.get().feedbacksGiven)
    }

    @Test
    fun typingIsCappedAtTheCharacterLimit() = runUnitTest {
        val vm = FeedbackViewModel(RecordingRepository(), InMemoryAppCache())

        vm.takeAction(FeedbackAction.MessageChanged("d".repeat(FeedbackCharLimit + 50)))

        assertEquals(FeedbackCharLimit, vm.state.message.length)
    }

    private class RecordingRepository : FeedbackRepository {
        val sent = mutableListOf<String>()
        override suspend fun submitFeedback(
            message: String,
            isBugReport: Boolean,
            logId: String?,
            errorCode: Int?,
        ): Catching<Unit> {
            sent += message
            return Catching.success(Unit)
        }
    }

    private class FailingRepository : FeedbackRepository {
        override suspend fun submitFeedback(
            message: String,
            isBugReport: Boolean,
            logId: String?,
            errorCode: Int?,
        ): Catching<Unit> = Catching.failure(IllegalStateException("no network"))
    }

    private class InMemoryAppCache : AppCache {
        private val data = MutableStateFlow(AppData())
        override val updates: Flow<AppData> = data
        override suspend fun get(): AppData = data.value
        override suspend fun set(value: AppData) { data.value = value }
        override suspend fun clear() { data.value = AppData() }
    }
}
