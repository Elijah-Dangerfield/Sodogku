package com.sodogku.features.settings.impl

import com.sodogku.features.settings.impl.feedback.FeedbackAction
import com.sodogku.features.settings.impl.feedback.FeedbackCharLimit
import com.sodogku.features.settings.impl.feedback.FeedbackViewModel
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import com.sodogku.libraries.sodogku.FeedbackKind
import com.sodogku.libraries.sodogku.FeedbackRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.sodogku.libraries.flowroutines.testing.CoroutineTest

/**
 * The note a player sends, and the promise the screen makes about what rides
 * with it.
 *
 * The log attachment is the claim worth guarding. The screen prints a line
 * telling the player their note travels with a log of the session, and nothing
 * on this path passes that flag explicitly, so the promise rests entirely on
 * the interface default. If that assertion ever fails, the copy is the thing to
 * fix rather than the assertion, and that is written down because the opposite
 * reflex is the natural one.
 *
 * A send that fails still confirms, and the counter still moves. Sentry may be
 * disabled or the phone offline, and telling somebody their thank-you note
 * bounced only invites them to retype it into the same void.
 *
 * The two refusals are an empty note, which is not sent at all, and typing past
 * the character limit, which truncates rather than growing a report nobody will
 * read.
 *
 * ### Not here
 *
 * The owner's own in-app panel is `DevFeedbackViewModelTest` in `:apps:compose`,
 * which files a different kind and carries a screenshot. What triage does with
 * a report afterwards is the skill, held to the enum by
 * `FeedbackTriageQueryContractTest`.
 */
class FeedbackViewModelTest : CoroutineTest() {

    @Test
    fun sendingForwardsTheNoteCountsItAndConfirms() = runUnitTest {
        val repository = RecordingRepository()
        val cache = InMemoryAppCache()
        val vm = FeedbackViewModel(repository, cache)

        vm.takeAction(FeedbackAction.MessageChanged("  the 9x9 levels are the best ones  "))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals("the 9x9 levels are the best ones", repository.sent.single())
        assertEquals(FeedbackKind.Feedback, repository.kinds.single())
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
    fun aPlayerSubmissionAttachesTheSessionLogTheScreenPromises() = runUnitTest {
        // The feedback screen prints `feedback_log_notice`, which tells the
        // player their note travels with a log of the session. Nothing on this
        // path passes `includeLogs`, so the promise rests entirely on the
        // interface default. If this fails, the copy is the thing to fix, not
        // the assertion.
        val repository = RecordingRepository()
        val vm = FeedbackViewModel(repository, InMemoryAppCache())

        vm.takeAction(FeedbackAction.MessageChanged("the daily explainer showed twice"))
        vm.takeAction(FeedbackAction.Submit)

        assertTrue(repository.logsAttached.single())
    }

    @Test
    fun typingIsCappedAtTheCharacterLimit() = runUnitTest {
        val vm = FeedbackViewModel(RecordingRepository(), InMemoryAppCache())

        vm.takeAction(FeedbackAction.MessageChanged("d".repeat(FeedbackCharLimit + 50)))

        assertEquals(FeedbackCharLimit, vm.state.message.length)
    }

    private class RecordingRepository : FeedbackRepository {
        val sent = mutableListOf<String>()
        val kinds = mutableListOf<FeedbackKind>()
        val logsAttached = mutableListOf<Boolean>()
        override suspend fun submitFeedback(
            message: String,
            kind: FeedbackKind,
            logId: String?,
            errorCode: Int?,
            screenshots: List<ByteArray>,
            includeLogs: Boolean,
        ): Catching<Unit> {
            sent += message
            kinds += kind
            logsAttached += includeLogs
            return Catching.success(Unit)
        }
    }

    private class FailingRepository : FeedbackRepository {
        override suspend fun submitFeedback(
            message: String,
            kind: FeedbackKind,
            logId: String?,
            errorCode: Int?,
            screenshots: List<ByteArray>,
            includeLogs: Boolean,
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
