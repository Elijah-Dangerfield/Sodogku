package com.sodogku.libraries.telemetry.impl

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How iOS answers "why did the app go away last time", from counts to one word.
 *
 * MetricKit hands over a tally for a whole day rather than a reason for one
 * exit, so the classifier picks the most severe thing in the window: a bad
 * memory access outranks a watchdog termination, which outranks a memory limit,
 * which outranks twelve ordinary quits. The ordering is asserted as a chain
 * rather than one case at a time, because getting it backwards reports every
 * crashing install as clean and nothing else notices.
 *
 * The rest is the handover to the next launch. A report is consumed exactly
 * once, so the same window cannot be attributed to two launches. An empty
 * window does not overwrite a report nobody has read yet, which is the ordering
 * that would otherwise lose the crash. A fresh install and a value on disk that
 * no longer parses both answer `Unknown`, since a guess here would be
 * indistinguishable from evidence.
 *
 * ### Not here
 *
 * The Android side of the same question is `PreviousExitReasonMappingTest`,
 * which maps a platform reason code rather than a tally. That the value reaches
 * the launch event intact is `AppLaunchedEventTest`.
 */
class MetricKitExitReportTest {

    private var stored: String? = null
    private val report = LatestExitReport(read = { stored }, write = { stored = it })

    @Test
    fun classification_picksTheMostSevereExitInTheWindow() {
        assertEquals(
            PreviousExit.Crash,
            ForegroundExitCounts(normal = 12, watchdog = 1, memoryLimit = 2, badAccess = 1).classify(),
        )
        assertEquals(
            PreviousExit.Anr,
            ForegroundExitCounts(normal = 12, watchdog = 1, memoryLimit = 2).classify(),
        )
        assertEquals(
            PreviousExit.Oom,
            ForegroundExitCounts(normal = 12, memoryLimit = 2).classify(),
        )
        assertEquals(PreviousExit.Clean, ForegroundExitCounts(normal = 12).classify())
    }

    @Test
    fun abnormalAndIllegalInstructionExits_countAsCrashes() {
        assertEquals(PreviousExit.Crash, ForegroundExitCounts(abnormal = 1).classify())
        assertEquals(PreviousExit.Crash, ForegroundExitCounts(illegalInstruction = 1).classify())
    }

    @Test
    fun report_isConsumedByExactlyOneLaunch() {
        report.record(ForegroundExitCounts(badAccess = 1))

        assertEquals(PreviousExit.Crash, report.consume())
        assertEquals(PreviousExit.Unknown, report.consume(), "a second launch must not re-report the same window")
    }

    @Test
    fun emptyWindow_doesNotOverwriteAnUnconsumedReport() {
        report.record(ForegroundExitCounts(watchdog = 1))
        report.record(ForegroundExitCounts())

        assertEquals(PreviousExit.Anr, report.consume())
    }

    @Test
    fun freshInstall_reportsUnknown() {
        assertEquals(PreviousExit.Unknown, report.consume())
    }

    @Test
    fun corruptStoredValue_fallsBackToUnknown() {
        stored = "not-a-previous-exit"

        assertEquals(PreviousExit.Unknown, report.consume())
    }
}
