package com.sodogku.features.streak.impl

import androidx.compose.runtime.Composable
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.ui.components.streak.StreakCell
import com.sodogku.libraries.ui.components.streak.StreakCellState
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import sodogku.features.streak.impl.generated.resources.Res
import sodogku.features.streak.impl.generated.resources.streak_day_bridged
import sodogku.features.streak.impl.generated.resources.streak_day_completed
import sodogku.features.streak.impl.generated.resources.streak_day_failed
import sodogku.features.streak.impl.generated.resources.streak_day_future
import sodogku.features.streak.impl.generated.resources.streak_day_missed
import sodogku.features.streak.impl.generated.resources.streak_day_today
import sodogku.features.streak.impl.generated.resources.streak_weekday_fri
import sodogku.features.streak.impl.generated.resources.streak_weekday_mon
import sodogku.features.streak.impl.generated.resources.streak_weekday_sat
import sodogku.features.streak.impl.generated.resources.streak_weekday_sun
import sodogku.features.streak.impl.generated.resources.streak_weekday_thu
import sodogku.features.streak.impl.generated.resources.streak_weekday_tue
import sodogku.features.streak.impl.generated.resources.streak_weekday_wed
import sodogku.libraries.resources.generated.resources.Res as SharedRes
import sodogku.libraries.resources.generated.resources.daily_date
import sodogku.libraries.resources.generated.resources.month_short_1
import sodogku.libraries.resources.generated.resources.month_short_10
import sodogku.libraries.resources.generated.resources.month_short_11
import sodogku.libraries.resources.generated.resources.month_short_12
import sodogku.libraries.resources.generated.resources.month_short_2
import sodogku.libraries.resources.generated.resources.month_short_3
import sodogku.libraries.resources.generated.resources.month_short_4
import sodogku.libraries.resources.generated.resources.month_short_5
import sodogku.libraries.resources.generated.resources.month_short_6
import sodogku.libraries.resources.generated.resources.month_short_7
import sodogku.libraries.resources.generated.resources.month_short_8
import sodogku.libraries.resources.generated.resources.month_short_9

/**
 * Turning the repository's days into the design system's squares.
 *
 * The DS knows nothing about `:libraries:progress` and should not: a calendar
 * that could only draw a daily-challenge streak is a screen, not a component.
 * The mapping is one function so there is one place where a new outcome has to
 * be given a colour and a sentence.
 *
 * The month names come from `:libraries:resources` because twelve of them
 * already live there. Everything the streak alone says lives in this module's
 * own `strings.xml`.
 */
@Composable
internal fun StreakDay.toCell(): StreakCell {
    val date = stringResource(
        SharedRes.string.daily_date,
        stringResource(MonthNames[date.month.number - 1]),
        date.day,
    )
    val spoken = stringResource(state.spoken())
    return StreakCell(
        label = this.date.day.toString(),
        // Today is named rather than left to the ring around it, which is a
        // visual cue a reader cannot see and a colourblind player may not
        // separate from the surrounding cells either.
        description = if (isToday) stringResource(Res.string.streak_day_today, date) else date,
        stateLabel = spoken,
        state = this.state.toCellState(),
        isToday = isToday,
    )
}

private fun StreakDayState.toCellState(): StreakCellState = when (this) {
    StreakDayState.Completed -> StreakCellState.Done
    StreakDayState.Bridged -> StreakCellState.Bridged
    StreakDayState.Failed -> StreakCellState.Failed
    StreakDayState.Missed -> StreakCellState.Missed
    StreakDayState.Future -> StreakCellState.Future
}

private fun StreakDayState.spoken(): StringResource = when (this) {
    StreakDayState.Completed -> Res.string.streak_day_completed
    StreakDayState.Bridged -> Res.string.streak_day_bridged
    StreakDayState.Failed -> Res.string.streak_day_failed
    StreakDayState.Missed -> Res.string.streak_day_missed
    StreakDayState.Future -> Res.string.streak_day_future
}

/**
 * Monday first, matching the grid the repository builds.
 *
 * One letter each, and Tuesday and Thursday share a "T" the way a printed
 * calendar does. The headings are hidden from screen readers, because each
 * cell speaks its own full date, so the ambiguity costs a reader nothing.
 */
internal val WeekdayInitials = listOf(
    Res.string.streak_weekday_mon,
    Res.string.streak_weekday_tue,
    Res.string.streak_weekday_wed,
    Res.string.streak_weekday_thu,
    Res.string.streak_weekday_fri,
    Res.string.streak_weekday_sat,
    Res.string.streak_weekday_sun,
)

private val MonthNames = listOf(
    SharedRes.string.month_short_1,
    SharedRes.string.month_short_2,
    SharedRes.string.month_short_3,
    SharedRes.string.month_short_4,
    SharedRes.string.month_short_5,
    SharedRes.string.month_short_6,
    SharedRes.string.month_short_7,
    SharedRes.string.month_short_8,
    SharedRes.string.month_short_9,
    SharedRes.string.month_short_10,
    SharedRes.string.month_short_11,
    SharedRes.string.month_short_12,
)
