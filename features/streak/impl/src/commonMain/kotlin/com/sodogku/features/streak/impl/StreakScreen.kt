package com.sodogku.features.streak.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.progress.streak.StreakDay
import com.sodogku.libraries.progress.streak.StreakDayState
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.FullScreenLoader
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.celebration.Arrived
import com.sodogku.libraries.ui.components.celebration.Arriving
import com.sodogku.libraries.ui.components.celebration.beatDelayMillis
import com.sodogku.libraries.ui.components.header.TopBar
import com.sodogku.libraries.ui.components.streak.StreakCalendar
import com.sodogku.libraries.ui.components.streak.StreakHero
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.screenContentPadding
import com.sodogku.system.AppTheme
import com.sodogku.system.VerticalSpacerD300
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.streak_calendar_title
import sodogku.libraries.resources.generated.resources.streak_celebrate_body
import sodogku.libraries.resources.generated.resources.streak_empty_body
import sodogku.libraries.resources.generated.resources.streak_day_label
import sodogku.libraries.resources.generated.resources.streak_keep_it_today
import sodogku.libraries.resources.generated.resources.streak_longest
import sodogku.libraries.resources.generated.resources.streak_longest_none
import sodogku.libraries.resources.generated.resources.streak_lost_body
import sodogku.libraries.resources.generated.resources.streak_lost_title
import sodogku.libraries.resources.generated.resources.streak_title

/**
 * The run, the record, and the last five weeks.
 *
 * **Nothing on this page moves when it is opened by tap.** Every moving part is
 * behind the same question, [StreakState.ceremony]: the beats arrive only on a
 * ceremony ([Beat]), the number only counts when [StreakState.countUpFrom] says
 * there is something to count ([StreakViewModel]), and the one filling day cell
 * is the one named by [StreakState.fillingIndex], which is null on a plain
 * visit. That is the difference the owner asked for, and each half of it is
 * decided outside this file so something can test it.
 *
 * A run that broke is the same page with one more sentence at the top and a
 * different sign-off at the bottom. It is deliberately not a separate screen:
 * what a player wants at that moment is the calendar with the gap in it and the
 * record that survived, which is this page, and a dedicated one would have had
 * to redraw both to say anything at all.
 *
 * The slide up is not here either. A page cannot rise over a board the navigator
 * has already taken away, so the entrance belongs to `StreakRoute`, which asks
 * for a slide up and for the board underneath to hold still while it travels.
 */
@Composable
fun StreakScreen(
    state: StreakState,
    onAction: (StreakAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.streak_title),
                onNavigateBack = { onAction(StreakAction.Back) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        when {
            state.loading -> FullScreenLoader()
            else -> StreakBody(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .screenContentPadding(padding),
            )
        }
    }
}

@Composable
private fun StreakBody(state: StreakState, modifier: Modifier = Modifier) {
    val ceremony = state.ceremony
    val mourning = state.lost > 0

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        VerticalSpacerD500()

        // The same picture the commitment moment shows, and on a celebration the
        // same performance: the number flips to the run the player has just
        // earned and lands with a thump.
        //
        // The count waits for the hero to finish arriving. Started with it, the
        // digits change while the whole beat is still scaling and travelling,
        // and two springs on one number read as a wobble rather than as a
        // landing.
        //
        // A lost run puts its headline inside this beat rather than taking one
        // of its own, so that the sentence and the 1 it is explaining arrive
        // together. Given its own beat it would have had to be first, which
        // renumbers the whole script and retunes a celebration that was tuned
        // three commits ago for a page that is not this one.
        Beat(ceremony, HeroBeat) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (mourning) {
                    Text(
                        text = pluralStringResource(
                            Res.plurals.streak_lost_title,
                            state.lost,
                            state.lost,
                        ),
                        typography = AppTheme.typography.Heading.H700,
                        textAlign = TextAlign.Center,
                    )
                    VerticalSpacerD500()
                }

                StreakHero(
                    streak = state.current,
                    week = state.days.toWeekStrip(),
                    dayLabel = pluralStringResource(
                        Res.plurals.streak_day_label,
                        state.current,
                        state.current,
                    ),
                    countUpFrom = state.countUpFrom,
                    countUpDelayMillis = if (ceremony) {
                        beatDelayMillis(HeroBeat) + HeroSettleMillis
                    } else {
                        0
                    },
                )
            }
        }

        VerticalSpacerD500()

        // The record is quieter than the run. It is the thing to beat, not the
        // thing the page is about, and a run of 3 under a record of 90 read at
        // the same weight is a page that opens by saying you used to be better.
        Beat(ceremony, RecordBeat) {
            Text(
                text = if (state.longest > 0) {
                    pluralStringResource(Res.plurals.streak_longest, state.longest, state.longest)
                } else {
                    stringResource(Res.string.streak_longest_none)
                },
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }

        if (ceremony) {
            // **The seam for an offer, and the whole of it.** The owner's
            // instinct on a lost run was a freeze or a store; what a freeze even
            // covers is undecided and is SD-28 in `docs/backlog.md`, so nothing
            // is offered here yet and the moment is an acknowledgement on its
            // own, which is what "an experience either way" asked for. When
            // SD-28 is answered, the offer is another beat under this sentence,
            // gated on the same `mourning`, with the run it is priced against
            // already on state as `lost`.
            VerticalSpacerD500()
            Beat(ceremony, SignOffBeat) {
                Text(
                    text = if (mourning) {
                        stringResource(Res.string.streak_lost_body)
                    } else {
                        stringResource(Res.string.streak_celebrate_body)
                    },
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (!state.playedToday && state.current > 0) {
            // The one thing a status page can say that the calendar cannot: how
            // long is left. Only when there is a run to lose and it has not been
            // kept yet, because on any other day it is a countdown to nothing.
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.streak_keep_it_today, state.hoursLeftLabel),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        VerticalSpacerD800()

        // The heading and the grid are one beat. They are one thing, and a
        // heading that arrives ahead of the squares it names spends its own
        // turn on screen labelling nothing.
        Beat(ceremony, CalendarBeat, Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = stringResource(Res.string.streak_calendar_title),
                    typography = AppTheme.typography.Heading.H600,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                VerticalSpacerD300()

                StreakCalendar(
                    days = state.days.map { it.toCell() },
                    weekdayLabels = WeekdayInitials.map { stringResource(it) },
                    fillingIndex = state.fillingIndex,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Only on a genuinely empty page. Explaining where the squares come from
        // to somebody who has already filled some in is noise.
        if (state.longest == 0) {
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.streak_empty_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        VerticalSpacerD800()
    }
}

/**
 * A piece of the page, arriving on a ceremony and simply present otherwise.
 *
 * Both branches lay the content out the same way, so the page a player opened by
 * tap is the page a ceremony finishes as. Omitting the wrapper on a plain visit
 * would have changed the measurement as well as the motion.
 */
@Composable
private fun Beat(
    ceremony: Boolean,
    order: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (ceremony) {
        Arriving(order = order, modifier = modifier, content = content)
    } else {
        Arrived(modifier = modifier, content = content)
    }
}

/**
 * The script, in the order it is read. The number first, because it is what the
 * page was pushed to say.
 */
private const val HeroBeat = 0
private const val RecordBeat = 1
private const val SignOffBeat = 2
private const val CalendarBeat = 3

/**
 * How long the number waits after its own beat has started before it climbs.
 *
 * Past the arrival spring rather than inside it. `Motion.Pop` is still settling
 * for a while after the beat is legible, and the whole point of the flip is that
 * it is watched.
 */
private const val HeroSettleMillis = 260

@Preview
@Composable
private fun StreakScreenPreview() {
    PreviewContent {
        StreakScreen(state = previewState(), onAction = {})
    }
}

/**
 * The ceremony. Under `LocalInspectionMode` every beat has already landed and
 * the number is already at its new value, which is both the point of that rule
 * and what makes this worth looking at.
 */
@Preview
@Composable
private fun StreakScreenCelebratingPreview() {
    PreviewContent {
        StreakScreen(state = previewState().copy(celebrating = 6), onAction = {})
    }
}

/**
 * The run that broke. A 1 under a sentence naming the 12 it replaced, with the
 * gap sitting in the calendar underneath and the record still on the page.
 */
@Preview
@Composable
private fun StreakScreenLostPreview() {
    PreviewContent {
        StreakScreen(
            state = previewState().copy(current = 1, lost = 12),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun StreakScreenEmptyPreview() {
    PreviewContent {
        StreakScreen(
            state = previewState().copy(
                current = 0,
                longest = 0,
                days = previewState().days.map { it.copy(state = StreakDayState.Missed) },
            ),
            onAction = {},
        )
    }
}

@Suppress("MagicNumber")
private fun previewState(): StreakState {
    val start = LocalDate(2026, 8, 10)
    val today = LocalDate(2026, 9, 7)
    return StreakState(
        loading = false,
        current = 6,
        longest = 21,
        today = today,
        days = List(35) { index ->
            val date = LocalDate.fromEpochDays(start.toEpochDays() + index)
            StreakDay(
                date = date,
                state = when {
                    date > today -> StreakDayState.Future
                    index >= 23 -> StreakDayState.Completed
                    index == 22 -> StreakDayState.Bridged
                    index >= 12 -> StreakDayState.Completed
                    else -> StreakDayState.Missed
                },
                isToday = date == today,
            )
        },
    )
}
