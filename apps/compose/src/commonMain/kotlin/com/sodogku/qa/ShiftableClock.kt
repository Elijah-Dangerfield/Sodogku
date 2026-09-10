package com.sodogku.qa

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The app's clock with the date it resolves moved by a whole number of days.
 *
 * Nothing in `commonMain` implements this. The implementation lives in
 * `apps/compose/src/qaClock/kotlin`, a source directory that is only compiled
 * into the Android **debug** variant and into an iOS build that has not been
 * told to leave it out — see the `qaClock` block in the module's
 * `build.gradle.kts`. A release build therefore has no implementation of this
 * interface in the binary at all, and `Clock` resolves to
 * [com.sodogku.SystemClock].
 *
 * That is why [QaToolsViewModel] reaches it with a cast off the injected
 * `Clock` rather than injecting it: in a release build there is nothing to
 * inject, and asking the graph for it would stop the graph compiling.
 *
 * **Whole days, and shifted in the device zone.** Every date in the app is
 * `clock.now()` read through `DeviceTimeZone`, so moving the day moves
 * midnight, the countdown and the calendar together. Adding 24 hours would
 * have been simpler and would have been wrong twice a year: on a
 * spring-forward day 24 hours lands on the day after next.
 */
@OptIn(ExperimentalTime::class)
interface ShiftableClock : Clock {

    /** Days the resolved date is ahead of (or, negative, behind) real time. */
    val shiftedDays: Int

    fun shiftBy(days: Int)

    fun clearShift()
}
