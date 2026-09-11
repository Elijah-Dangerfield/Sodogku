package com.sodogku.libraries.flowroutines

import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.LogEntry
import com.sodogku.libraries.core.logging.LogId
import com.sodogku.libraries.core.logging.LogLevel
import com.sodogku.libraries.core.logging.LogTree
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A state that cannot be written to the saved state handle is a missing nicety,
 * not a fault, and it must never reach Sentry.
 *
 * `SEAViewModel.onCleared` offers the state to `SavedStateHandle`, which takes
 * primitives and Parcelables. A plain Kotlin data class is neither, so the write
 * throws for most screens in this app, every time one is cleared. That is the
 * expected path rather than the exceptional one: surviving process death is
 * worth having where somebody has gone to the trouble, and worth nothing
 * anywhere else.
 *
 * It was not always expected. Until 2026-09-09 the failure was logged through
 * `logOnFailure`, which logs at error, and error is the level that becomes a
 * Sentry issue. So every player who finished onboarding filed a crash report
 * about a feature they were not using, and one of them is Sentry SODOGKU-1.
 *
 * **Not here:** whether the state is actually restored. `SavedStateHandle` in a
 * unit test is a fresh map with no platform behind it, so a round trip here
 * would prove the map works rather than that process death does. That belongs in
 * an instrumented test if it is ever worth having.
 */
class SEAViewModelSavedStateTest {

    /** State that `SavedStateHandle` will refuse, which is the normal case here. */
    private data class NotSavable(val value: Int)

    private class Subject : SEAViewModel<NotSavable, String, String>(
        initialStateArg = NotSavable(1),
    ) {
        override suspend fun handleAction(action: String) = Unit

        /** `onCleared` is protected on `ViewModel`, so the test reaches it from inside. */
        fun clear() = onCleared()
    }

    private class Recorder : LogTree() {
        val entries = mutableListOf<LogEntry>()
        override fun isLoggable(level: LogLevel, tag: String?) = true
        override fun log(entry: LogEntry): LogId? {
            entries += entry
            return null
        }
    }

    private val recorder = Recorder()

    @BeforeTest
    fun plant() {
        KLog.clearTrees()
        KLog.plant(recorder)
    }

    @AfterTest
    fun uproot() {
        KLog.clearTrees()
    }

    @Test
    fun clearingAViewModelWhoseStateCannotBeSavedReportsNothingAboveDebug() {
        Subject().clear()

        val loud = recorder.entries.filter { it.level.priority > LogLevel.Debug.priority }
        assertTrue(
            loud.isEmpty(),
            "clearing a view model with unsavable state logged above debug, which is what " +
                "reaches Sentry: ${loud.map { "${it.level} ${it.message}" }}",
        )
    }

    /**
     * The line still exists, at a level nothing exports. Deleting it would make
     * the test above pass for the wrong reason, and a developer wondering why
     * their state did not survive a process death deserves the answer in the
     * local log.
     */
    @Test
    fun theReasonIsStillWrittenDownWhereADeveloperCanFindIt() {
        Subject().clear()

        val said = recorder.entries.filter { it.level == LogLevel.Debug }
        assertEquals(1, said.size, "expected one debug line, got ${said.map { it.message }}")
        assertTrue(
            said.single().message.orEmpty().contains("not savable"),
            "the line does not say why: ${said.single().message}",
        )
    }

    /**
     * The guard against this guard. If `onCleared` stopped attempting the write
     * at all, both tests above would pass while proving nothing, because a
     * savable state is the case that is supposed to be quiet.
     */
    @Test
    fun aStateTheHandleAcceptsIsSavedAndSaysNothing() {
        class Savable : SEAViewModel<String, String, String>(initialStateArg = "kept") {
            override suspend fun handleAction(action: String) = Unit
            fun clear() = onCleared()
        }

        Savable().clear()

        assertTrue(
            recorder.entries.isEmpty(),
            "a savable state should log nothing at all: ${recorder.entries.map { it.message }}",
        )
    }
}
