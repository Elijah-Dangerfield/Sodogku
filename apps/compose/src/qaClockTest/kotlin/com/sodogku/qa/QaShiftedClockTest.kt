package com.sodogku.qa

import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.storage.FileManager
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The debug-only day-shift clock.
 *
 * Two things are worth a test here and the rest is not. The first is that a
 * shift is *calendar* arithmetic rather than a multiple of 24 hours, which is
 * only visible on a DST day. The second is that the offset is on disk, because
 * a shift that died with the process could not test a cold boot, which is half
 * of what the tool exists for.
 *
 * The wiring — which build gets this class bound as `Clock` — is not testable
 * from here; it is a source-set question, and the evidence is that the release
 * KSP output binds `SystemClock` and generates nothing for this class at all.
 * What the QA panel does with the shift lives in `QaToolsViewModelTest`.
 */
@OptIn(ExperimentalTime::class)
class QaShiftedClockTest {

    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "qa-shift" / Random.nextInt().toString()

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test
    fun shiftingMovesTheDateAndKeepsTheTimeOfDay() {
        val zone = TimeZone.of("Europe/London")
        val at = Instant.parse("2026-09-10T23:58:00Z")

        val moved = at.shiftedByDays(2, zone).toLocalDateTime(zone)

        assertEquals("2026-09-13", moved.date.toString())
        assertEquals(at.toLocalDateTime(zone).time, moved.time, "the countdown has to stay honest")
    }

    @Test
    fun shiftingBackwardsIsTheSameArithmeticInReverse() {
        val zone = TimeZone.of("Europe/London")
        val at = Instant.parse("2026-03-02T09:15:00Z")

        val moved = at.shiftedByDays(-5, zone).toLocalDateTime(zone)

        assertEquals("2026-02-25", moved.date.toString())
    }

    @Test
    fun aShiftOverSpringForwardMovesOneDayRatherThanTwentyFourHours() {
        // London springs forward at 01:00 on 2026-03-29, so that day is 23
        // hours long. Adding 24 hours to 23:30 the night before lands on the
        // 30th; adding a calendar day lands on the 29th, which is what "+1 day"
        // has to mean.
        val zone = TimeZone.of("Europe/London")
        val at = Instant.parse("2026-03-28T23:30:00Z")

        val moved = at.shiftedByDays(1, zone).toLocalDateTime(zone)

        assertEquals("2026-03-29", moved.date.toString())
    }

    @Test
    fun aShiftOfZeroLeavesTheInstantExactlyAlone() {
        val at = Instant.parse("2026-09-10T12:00:00Z")

        assertEquals(at, at.shiftedByDays(0, TimeZone.of("Europe/London")))
    }

    @Test
    fun theShiftIsReadBackByANewInstance() {
        val files = TempFileManager(root)

        QaShiftedClock(files, UtcZone).shiftBy(3)

        assertEquals(3, QaShiftedClock(files, UtcZone).shiftedDays, "a cold boot has to stay shifted")
    }

    @Test
    fun shiftsAccumulateAcrossInstances() {
        val files = TempFileManager(root)

        QaShiftedClock(files, UtcZone).shiftBy(-7)
        QaShiftedClock(files, UtcZone).shiftBy(2)

        assertEquals(-5, QaShiftedClock(files, UtcZone).shiftedDays)
    }

    @Test
    fun clearingPutsItBackOnRealTimeForGood() {
        val files = TempFileManager(root)

        QaShiftedClock(files, UtcZone).shiftBy(4)
        QaShiftedClock(files, UtcZone).clearShift()

        assertEquals(0, QaShiftedClock(files, UtcZone).shiftedDays)
    }

    @Test
    fun aClockThatHasNeverBeenShiftedReadsZero() {
        assertEquals(0, QaShiftedClock(TempFileManager(root), UtcZone).shiftedDays)
    }
}

private val UtcZone = DeviceTimeZone { TimeZone.UTC }

private class TempFileManager(private val root: Path) : FileManager {
    override fun createFile(name: String): Path {
        FileSystem.SYSTEM.createDirectories(root)
        return root / name
    }

    override fun deleteFile(name: String) = FileSystem.SYSTEM.delete(root / name)

    override fun deleteAll() = FileSystem.SYSTEM.deleteRecursively(root)
}
