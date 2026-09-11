package com.sodogku.features.streak.impl

import androidx.compose.runtime.Composable
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.ui.components.streak.StreakWeekDay
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.ui.components.streak.StreakCell
import com.sodogku.libraries.ui.components.streak.StreakCellState
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
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
import sodogku.libraries.resources.generated.resources.streak_day_bridged
import sodogku.libraries.resources.generated.resources.streak_day_completed
import sodogku.libraries.resources.generated.resources.streak_day_future
import sodogku.libraries.resources.generated.resources.streak_day_missed
import sodogku.libraries.resources.generated.resources.streak_day_today
import sodogku.libraries.resources.generated.resources.streak_weekday_fri
import sodogku.libraries.resources.generated.resources.streak_weekday_mon
import sodogku.libraries.resources.generated.resources.streak_weekday_sat
import sodogku.libraries.resources.generated.resources.streak_weekday_sun
import sodogku.libraries.resources.generated.resources.streak_weekday_thu
import sodogku.libraries.resources.generated.resources.streak_weekday_tue
import sodogku.libraries.resources.generated.resources.streak_weekday_wed

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
        Res.string.daily_date,
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
    StreakDayState.Missed -> StreakCellState.Missed
    StreakDayState.Future -> StreakCellState.Future
}

private fun StreakDayState.spoken(): StringResource = when (this) {
    StreakDayState.Completed -> Res.string.streak_day_completed
    StreakDayState.Bridged -> Res.string.streak_day_bridged
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
    Res.string.month_short_1,
    Res.string.month_short_2,
    Res.string.month_short_3,
    Res.string.month_short_4,
    Res.string.month_short_5,
    Res.string.month_short_6,
    Res.string.month_short_7,
    Res.string.month_short_8,
    Res.string.month_short_9,
    Res.string.month_short_10,
    Res.string.month_short_11,
    Res.string.month_short_12,
)

/**
 * The last seven days of the calendar, as the strip [StreakHero] draws.
 *
 * Takes the tail of the grid the repository already built rather than asking for
 * a second window. The repository's last row *is* the current week, Monday
 * first, so slicing it means the strip and the full calendar can never disagree
 * about which day is which.
 */
@Composable
internal fun List<StreakDay>.toWeekStrip(): List<StreakWeekDay> {
    val week = takeLast(DaysInWeek)
    return week.mapIndexed { index, day ->
        StreakWeekDay(
            initial = stringResource(WeekdayInitials[index]),
            filled = day.state == StreakDayState.Completed || day.state == StreakDayState.Bridged,
            isToday = day.isToday,
            spoken = day.toCell().description + ", " + stringResource(day.state.spoken()),
        )
    }
}

private const val DaysInWeek = 7
