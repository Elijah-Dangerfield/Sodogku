package com.sodogku.devfeedback

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.sodogku.FeedbackKind
import com.sodogku.libraries.sodogku.FeedbackRepository
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The in-app panel the owner files work from, and what rides along with a note.
 *
 * The attachments are the point. A directive with no log tail and no screenshot
 * is a sentence somebody has to reproduce from, so the log box defaults on, the
 * screenshot the panel was opened over is sent by default, and both are
 * asserted at the repository rather than on state. Removing the screenshot
 * removes it from the submission too, which is the half that would otherwise
 * only clear the preview.
 *
 * Two refusals shape the form. Whitespace is not a directive and is not filed.
 * Typing past the character limit truncates rather than growing an attachment
 * nothing will read.
 *
 * A failed send still confirms, and that is a deliberate choice rather than
 * swallowed error handling: Sentry is usually disabled on a local build, and
 * retyping the same directive into the same disabled Sentry is not a recovery.
 * Asking for one would train the owner to ignore the panel.
 *
 * Filing clears the form, including the log preference, so the next directive
 * starts from the defaults rather than inheriting the last one's.
 *
 * ### Not here
 *
 * How a report is uploaded, and what the triage skill does with it afterwards,
 * are outside this view model. That the skill's queries match the tags is
 * `FeedbackTriageQueryContractTest`.
 */
class DevFeedbackViewModelTest : CoroutineTest() {

    @Test
    fun filingSendsTheDirectiveWithItsLogsAndScreenshot() = runUnitTest {
        val repository = RecordingRepository()
        val vm = DevFeedbackViewModel(repository)
        val shot = Screenshot(byteArrayOf(1, 2, 3))

        vm.takeAction(DevFeedbackAction.Open(shot))
        vm.takeAction(DevFeedbackAction.MessageChanged("  make the handle bigger  "))
        vm.takeAction(DevFeedbackAction.Submit)

        val sent = repository.sent.single()
        assertEquals("make the handle bigger", sent.message)
        assertEquals(FeedbackKind.OwnerDirective, sent.kind)
        assertTrue(sent.includeLogs, "the checkbox defaults on, so logs must ride along")
        assertContentEquals(byteArrayOf(1, 2, 3), sent.screenshots.single())
    }

    @Test
    fun theLogTailIsWithheldWhenTheBoxIsUnticked() = runUnitTest {
        val repository = RecordingRepository()
        val vm = DevFeedbackViewModel(repository)

        vm.takeAction(DevFeedbackAction.Open(screenshot = null))
        assertTrue(vm.state.includeLogs, "logs are attached unless the reporter says otherwise")

        vm.takeAction(DevFeedbackAction.IncludeLogsChanged(false))
        vm.takeAction(DevFeedbackAction.MessageChanged("board flickers on rotate"))
        vm.takeAction(DevFeedbackAction.Submit)

        assertFalse(repository.sent.single().includeLogs)
    }

    @Test
    fun aRemovedScreenshotIsNotSent() = runUnitTest {
        val repository = RecordingRepository()
        val vm = DevFeedbackViewModel(repository)

        vm.takeAction(DevFeedbackAction.Open(Screenshot(byteArrayOf(9))))
        vm.takeAction(DevFeedbackAction.RemoveScreenshot)
        vm.takeAction(DevFeedbackAction.MessageChanged("copy tweak on the win dialog"))
        vm.takeAction(DevFeedbackAction.Submit)

        assertNull(vm.state.screenshot)
        assertTrue(repository.sent.single().screenshots.isEmpty())
    }

    @Test
    fun anEmptyDirectiveIsNotFiled() = runUnitTest {
        val repository = RecordingRepository()
        val vm = DevFeedbackViewModel(repository)

        vm.takeAction(DevFeedbackAction.Open(screenshot = null))
        vm.takeAction(DevFeedbackAction.MessageChanged("   "))
        vm.takeAction(DevFeedbackAction.Submit)

        assertTrue(repository.sent.isEmpty())
        assertFalse(vm.state.sent)
    }

    @Test
    fun theFormIsClearedAfterFilingSoTheNextDirectiveStartsBlank() = runUnitTest {
        val vm = DevFeedbackViewModel(RecordingRepository())

        vm.takeAction(DevFeedbackAction.Open(Screenshot(byteArrayOf(4))))
        vm.takeAction(DevFeedbackAction.IncludeLogsChanged(false))
        vm.takeAction(DevFeedbackAction.MessageChanged("first ask"))
        vm.takeAction(DevFeedbackAction.Submit)

        assertTrue(vm.state.sent)
        assertEquals("", vm.state.message)
        assertNull(vm.state.screenshot)
        assertTrue(vm.state.includeLogs, "the log preference resets with the form")
    }

    @Test
    fun aFailedSendStillConfirms() = runUnitTest {
        // Sentry may be disabled locally. Retyping the same directive into the
        // same disabled Sentry is not a recovery, so the form does not ask for
        // one.
        val vm = DevFeedbackViewModel(FailingRepository())

        vm.takeAction(DevFeedbackAction.Open(screenshot = null))
        vm.takeAction(DevFeedbackAction.MessageChanged("the dog blinks too often"))
        vm.takeAction(DevFeedbackAction.Submit)

        assertTrue(vm.state.sent)
        assertFalse(vm.state.isSubmitting)
    }

    @Test
    fun typingIsCappedAtTheCharacterLimit() = runUnitTest {
        val vm = DevFeedbackViewModel(RecordingRepository())

        vm.takeAction(DevFeedbackAction.MessageChanged("x".repeat(DirectiveCharLimit + 200)))

        assertEquals(DirectiveCharLimit, vm.state.message.length)
    }

    private class Submission(
        val message: String,
        val kind: FeedbackKind,
        val screenshots: List<ByteArray>,
        val includeLogs: Boolean,
    )

    private class RecordingRepository : FeedbackRepository {
        val sent = mutableListOf<Submission>()
        override suspend fun submitFeedback(
            message: String,
            kind: FeedbackKind,
            logId: String?,
            errorCode: Int?,
            screenshots: List<ByteArray>,
            includeLogs: Boolean,
        ): Catching<Unit> {
            sent += Submission(message, kind, screenshots, includeLogs)
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
        ): Catching<Unit> = Catching.failure(IllegalStateException("Sentry disabled"))
    }
}
