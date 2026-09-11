package com.sodogku.libraries.telemetry.impl

import android.app.ApplicationExitInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Android's exit reason codes, folded down to the four words a dashboard reads.
 *
 * `ApplicationExitInfo` has more than a dozen reasons and the app reports four
 * buckets, so most of the work is deciding what does *not* count. A dependency
 * dying, an initialisation failure and excessive resource use all fall to
 * `Unknown` on purpose: each has a plausible argument for being a crash, and
 * counting them as one would inflate the crash rate with exits no fix would
 * ever move.
 *
 * The signalled case goes the other way. It arrives when the system or a
 * developer stops the process, which looks violent and is clean, and reading it
 * as a crash would make every debugger detach a reported one.
 *
 * This lives in `androidUnitTest` because the constants are platform ones.
 *
 * ### Not here
 *
 * The iOS answer to the same question is `MetricKitExitReportTest`, which folds
 * a day of counts rather than mapping a code.
 */
class PreviousExitReasonMappingTest {

    @Test
    fun crashes_mapToCrash() {
        assertEquals(PreviousExit.Crash, previousExitForReason(ApplicationExitInfo.REASON_CRASH))
        assertEquals(PreviousExit.Crash, previousExitForReason(ApplicationExitInfo.REASON_CRASH_NATIVE))
    }

    @Test
    fun anr_mapsToAnr() {
        assertEquals(PreviousExit.Anr, previousExitForReason(ApplicationExitInfo.REASON_ANR))
    }

    @Test
    fun lowMemory_mapsToOom() {
        assertEquals(PreviousExit.Oom, previousExitForReason(ApplicationExitInfo.REASON_LOW_MEMORY))
    }

    @Test
    fun userAndSelfExits_mapToClean() {
        assertEquals(PreviousExit.Clean, previousExitForReason(ApplicationExitInfo.REASON_EXIT_SELF))
        assertEquals(PreviousExit.Clean, previousExitForReason(ApplicationExitInfo.REASON_USER_REQUESTED))
        assertEquals(PreviousExit.Clean, previousExitForReason(ApplicationExitInfo.REASON_USER_STOPPED))
        assertEquals(PreviousExit.Clean, previousExitForReason(ApplicationExitInfo.REASON_SIGNALED))
    }

    @Test
    fun everythingElse_mapsToUnknown() {
        assertEquals(PreviousExit.Unknown, previousExitForReason(ApplicationExitInfo.REASON_UNKNOWN))
        assertEquals(PreviousExit.Unknown, previousExitForReason(ApplicationExitInfo.REASON_DEPENDENCY_DIED))
        assertEquals(PreviousExit.Unknown, previousExitForReason(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE))
        assertEquals(PreviousExit.Unknown, previousExitForReason(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE))
        assertEquals(PreviousExit.Unknown, previousExitForReason(ApplicationExitInfo.REASON_OTHER))
    }
}
