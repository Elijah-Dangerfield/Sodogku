package com.sodogku.libraries.progress.impl.daily

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The date arithmetic behind the daily, as free functions over an instant and a
 * zone.
 *
 * Neither argument is ever read from the system in here. That is the whole point:
 * midnight, a flight east and a device clock set to 1970 are the same function
 * call with different arguments, so all three are testable and none of them
 * needs a device.
 */
internal fun dayOf(now: Instant, zone: TimeZone): LocalDate = now.toLocalDateTime(zone).date

/**
 * How long until the local date changes.
 *
 * Goes through `atStartOfDayIn` rather than adding 24 hours, because a day is not
 * always 24 hours long — a DST spring-forward day is 23, and in a few zones
 * midnight itself does not exist on the transition day.
 *
 * Floored at a second so a clock or zone that somehow puts the next day in the
 * past cannot turn the rollover flow into a busy loop. Being a second late is
 * survivable; spinning a CPU on a phone is not.
 */
internal fun untilNextDay(now: Instant, zone: TimeZone): Duration =
    (dayOf(now, zone).nextDay().atStartOfDayIn(zone) - now).coerceAtLeast(1.seconds)

internal fun LocalDate.nextDay(): LocalDate = plus(1, DateTimeUnit.DAY)

internal fun LocalDate.previousDay(): LocalDate = minus(1, DateTimeUnit.DAY)

/** Same calendar month of the same year — the window `daily.freezesPerMonth` caps. */
internal fun LocalDate.inSameMonthAs(other: LocalDate): Boolean =
    year == other.year && month == other.month
