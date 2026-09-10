package com.sodogku.qa

import com.sodogku.SystemClock
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.storage.FileManager
import kotlinx.atomicfu.atomic
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import okio.FileSystem
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The debug-only clock the QA panel can move, bound in place of [SystemClock].
 *
 * This file is not in `commonMain`. It is compiled into the Android debug
 * variant and into iOS builds that have not opted out, and into nothing else —
 * the release graph resolves `Clock` to [SystemClock] with no override present
 * to replace it.
 *
 * **The offset is on disk, not in memory.** Half of what a day shift is for is
 * a cold boot: a run that should have broken overnight, a calendar that should
 * have redrawn, a countdown that should have reset. An offset that died with
 * the process could not test any of them. It is a single integer in one file,
 * read once in the constructor and written on the button press, so the read is
 * synchronous — `now()` cannot suspend, and a `Cache` hydrated on a coroutine
 * would hand out real time for the first frames after launch, which is exactly
 * the moment being tested.
 *
 * The file is not `AppData`, for the reason `StreakPromptCache` gives about
 * itself: this is machinery, and it should disappear with a data wipe rather
 * than ride along with the player's settings.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = Clock::class,
    replaces = [SystemClock::class],
)
@Inject
class QaShiftedClock(
    fileManager: FileManager,
    private val timeZone: DeviceTimeZone,
) : ShiftableClock {

    private val file = fileManager.createFile(FileName)

    /**
     * Belt and braces for iOS, where this file is kept out of a build by an
     * env var rather than by the build type (Android has the `androidDebug`
     * source set and needs no flag). A release binary that somehow linked it
     * still starts unshifted, and the only screen that can move it is
     * unreachable outside a debug build.
     */
    private val days = atomic(if (BuildInfo.isDebug) readShift() else 0)

    override val shiftedDays: Int get() = days.value

    override fun now(): Instant = Clock.System.now().shiftedByDays(days.value, timeZone.current())

    override fun shiftBy(days: Int) = writeShift(this.days.value + days)

    override fun clearShift() = writeShift(0)

    private fun writeShift(value: Int) {
        days.value = value
        Catching { FileSystem.SYSTEM.write(file) { writeUtf8(value.toString()) } }
            .logOnFailure("QA day shift not persisted")
    }

    private fun readShift(): Int =
        Catching { FileSystem.SYSTEM.read(file) { readUtf8() }.trim().toInt() }.getOrNull() ?: 0
}

/**
 * The same wall-clock time, [days] calendar days later in [zone].
 *
 * Calendar days, not multiples of 24 hours. A spring-forward day is 23 hours
 * long, so `+ 1.days` on the evening before one lands on the day *after* next —
 * a QA clock that skips a day while claiming to move one is worse than no QA
 * clock. `DailyCalendar.untilNextDay` avoids the same trap for the same reason.
 *
 * Keeping the time of day is what makes the countdown honest: shift forward two
 * days at 23:58 and there are still two minutes left before the next rollover.
 */
internal fun Instant.shiftedByDays(days: Int, zone: TimeZone): Instant {
    if (days == 0) return this
    val local = toLocalDateTime(zone)
    return LocalDateTime(local.date.plus(DatePeriod(days = days)), local.time).toInstant(zone)
}

private const val FileName = "qa_day_shift"
