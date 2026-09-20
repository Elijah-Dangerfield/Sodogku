package com.sodogku.features.streak.impl

import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.ui.components.celebration.beatDelayMillis
import com.sodogku.libraries.ui.components.streak.WeekDayState
import com.sodogku.libraries.ui.components.text.countUpMillis

/**
 * The week under the number, decided over plain values so a test can read it.
 *
 * The repository's calendar is whole weeks, Monday first, ending in the week
 * today falls in (`playCalendarOn`, and `PlayStreakTest` pins that the last
 * seven cells are that week). So the current week is the tail of the grid,
 * and slicing it rather than asking for a second window is what keeps the
 * strip and the five-week calendar from ever disagreeing about which day is
 * which.
 */
internal fun List<StreakDay>.currentWeek(): List<StreakDay> = takeLast(DaysInWeek)

/**
 * How each day of the current week is drawn.
 *
 * A covered day counts as done: the run stood through it. Today unplayed is
 * empty rather than missed, because the day is not over, which is the same
 * rule `playStreakOn` applies to the number.
 */
internal fun List<StreakDay>.weekStripStates(): List<WeekDayState> = currentWeek().map { day ->
    when (day.state) {
        StreakDayState.Completed, StreakDayState.Bridged -> WeekDayState.Done
        StreakDayState.Missed -> if (day.isToday) WeekDayState.Empty else WeekDayState.Missed
        StreakDayState.Future -> WeekDayState.Empty
    }
}

/**
 * The index within the current week of the day that just landed, or null
 * when nothing did: a ceremony that arrives with today not completed (a
 * wiped table, a config change mid-flight) pops nothing rather than reaching
 * for a day that is not there.
 */
internal fun List<StreakDay>.justLandedIndex(): Int? = currentWeek()
    .indexOfFirst { it.isToday && it.state == StreakDayState.Completed }
    .takeIf { it >= 0 }

/**
 * When the number starts climbing, counted from the moment the ceremony
 * begins to arrive.
 *
 * Past the page's own entrance rather than inside it. The route slides up
 * over the board, and digits changing while the whole page is still
 * travelling read as a wobble rather than as a landing.
 */
internal fun numberStartMillis(): Int = beatDelayMillis(NumberBeat) + NumberSettleMillis

/**
 * When the week strip's new day pops, counted from the same moment.
 *
 * After the number has finished counting and landed, per the handoff: the
 * strip's day is the second thing that happens, and it waits for the first.
 * A run that does not count (a restart, a lost run) still waits for the
 * number's thump, so the two never land on the same frame.
 */
internal fun stripPopMillis(countUpFrom: Int?, value: Int): Int =
    numberStartMillis() + countUpMillis(countUpFrom, value) + NumberSettleMillis

private const val NumberBeat = 0

/**
 * How long the number waits after its cue before it climbs, and how long the
 * strip waits after the climb. Past the arrival spring rather than inside it:
 * `Motion.Pop` is still settling for a while after the page is legible, and
 * the whole point of the flip is that it is watched.
 */
private const val NumberSettleMillis = 260

private const val DaysInWeek = 7
