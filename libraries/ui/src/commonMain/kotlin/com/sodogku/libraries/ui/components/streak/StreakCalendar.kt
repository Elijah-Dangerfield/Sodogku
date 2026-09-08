package com.sodogku.libraries.ui.components.streak

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.Feel
import com.sodogku.libraries.ui.system.LocalHaptics
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.ui.tooling.preview.Preview

/** How one square on the streak calendar is drawn. */
enum class StreakCellState {
    /** Played and cleared. The only state that is a day of the streak. */
    Done,

    /** Covered by a freeze or a restore. Drawn as a held place, not as a win. */
    Bridged,

    /** Played and lost. */
    Failed,

    /** In the past and never played. */
    Missed,

    /** Later than today. Not a miss, and not the player's fault yet. */
    Future,
}

/**
 * One square, and everything it has to say.
 *
 * [description] and [stateLabel] are split the way `BoardCellLabels` splits
 * them: the date is the square's identity and never changes, what happened on it
 * is the state, and a reader announces the second on its own when it changes.
 */
@Immutable
data class StreakCell(
    /** The day of the month, as the grid prints it. */
    val label: String,

    /** The whole date, spoken. "Monday 7 September". */
    val description: String,
    val stateLabel: String,
    val state: StreakCellState,
    val isToday: Boolean,
)

/**
 * Five weeks of days, seven across.
 *
 * A plain `Column` of `Row`s rather than a `LazyVerticalGrid`: thirty-five
 * squares all fit on screen at once, so laziness buys nothing and costs the
 * whole grid its intrinsic height inside a scrolling page.
 *
 * [fillingIndex] is the one square that animates. Everything else is drawn at
 * its final state on the first frame, which is what makes a tap-to-open
 * genuinely still. There is no entrance here to suppress, because there was
 * never one to begin with.
 */
@Composable
fun StreakCalendar(
    days: List<StreakCell>,
    weekdayLabels: List<String>,
    modifier: Modifier = Modifier,
    /** Index into [days] of the day to animate in, or null for a still grid. */
    fillingIndex: Int? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D200)) {
            weekdayLabels.forEach { day ->
                Text(
                    text = day,
                    typography = AppTheme.typography.Caption.C200,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    // The headings repeat what every cell already says out loud,
                    // so a reader crossing the grid would hear the weekday twice.
                    modifier = Modifier.weight(ColumnWeight).clearAndSetSemantics { },
                )
            }
        }
        days.chunked(DaysPerWeek).forEachIndexed { week, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D200)) {
                row.forEachIndexed { column, day ->
                    StreakDayCell(
                        cell = day,
                        filling = fillingIndex == week * DaysPerWeek + column,
                        modifier = Modifier.weight(ColumnWeight),
                    )
                }
            }
        }
    }
}

/**
 * One square.
 *
 * The fill is an [Animatable] read inside `drawBehind`, never during
 * composition. Read with `by` it would recompose the whole grid on every frame
 * of the animation, which is the single most common Compose performance bug in
 * this codebase's list of them.
 */
@Composable
private fun StreakDayCell(cell: StreakCell, filling: Boolean, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val empty = AppTheme.colors.surfaceSecondary.color
    val done = AppTheme.colors.accentPrimary.color
    val bridged = AppTheme.colors.accentSecondary.color
    val failed = AppTheme.colors.danger.color
    val today = AppTheme.colors.border.color

    // Held at 1 in a preview or a screenshot test: an animation that starts at 0
    // and is never driven leaves the day the page is celebrating drawn empty.
    val still = LocalInspectionMode.current
    val fill = remember { Animatable(if (filling && !still) 0f else 1f) }

    LaunchedEffect(filling) {
        if (!filling || still) return@LaunchedEffect
        fill.snapTo(0f)
        haptics.play(Feel.Win)
        fill.animateTo(1f, Motion.Pop)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(Square)
            .clip(Radii.Cell)
            .drawBehind {
                drawRect(color = empty)
                val colour = when (cell.state) {
                    StreakCellState.Done -> done
                    StreakCellState.Bridged -> bridged
                    StreakCellState.Failed -> failed
                    StreakCellState.Missed, StreakCellState.Future -> Color.Transparent
                }
                // A rising fill rather than a fade. The day the celebration is
                // for should look like it is being filled in, which is what the
                // greyed-out-thing-that-fills asks for everywhere else too.
                val height = size.height * fill.value
                drawRect(
                    color = colour,
                    topLeft = Offset(0f, size.height - height),
                    size = Size(size.width, height),
                )
                if (cell.isToday) {
                    drawRect(
                        color = today,
                        style = Stroke(width = size.minDimension * TodayRing),
                    )
                }
            }
            .clearAndSetSemantics {
                contentDescription = cell.description
                stateDescription = cell.stateLabel
            },
    ) {
        Text(
            text = cell.label,
            typography = AppTheme.typography.Caption.C200,
            color = when (cell.state) {
                StreakCellState.Done, StreakCellState.Failed -> AppTheme.colors.onAccentPrimary
                StreakCellState.Future -> AppTheme.colors.textDisabled
                else -> AppTheme.colors.textSecondary
            },
        )
    }
}

private const val DaysPerWeek = 7

private const val ColumnWeight = 1f

private const val Square = 1f

/** Ring width as a fraction of the cell, so it scales with the grid. */
private const val TodayRing = 0.09f

@Preview
@Composable
private fun StreakCalendarPreview() {
    PreviewContent {
        StreakCalendar(
            days = previewDays(),
            weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S"),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Suppress("MagicNumber")
private fun previewDays(): List<StreakCell> = List(35) { index ->
    StreakCell(
        label = (index % 28 + 1).toString(),
        description = "Day $index",
        stateLabel = "Played",
        state = when {
            index > 22 -> StreakCellState.Future
            index == 22 -> StreakCellState.Done
            index in 16..21 -> StreakCellState.Done
            index == 15 -> StreakCellState.Bridged
            index == 9 -> StreakCellState.Failed
            index in 10..14 -> StreakCellState.Done
            else -> StreakCellState.Missed
        },
        isToday = index == 22,
    )
}
