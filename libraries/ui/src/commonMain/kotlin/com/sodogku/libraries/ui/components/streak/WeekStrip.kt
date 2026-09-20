package com.sodogku.libraries.ui.components.streak

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.Feel
import com.sodogku.libraries.ui.system.LocalHaptics
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.ui.tooling.preview.Preview

/** How one day on the strip is drawn. */
enum class WeekDayState {
    /** Played and cleared: amber, with a tick. */
    Done,

    /** In the past and never played: rose, with a cross. Only the lost screen draws one. */
    Missed,

    /** Not yet. A grey disc. */
    Empty,
}

@Immutable
data class WeekStripDay(
    /** The weekday's initial, as the strip prints it. */
    val letter: String,
    val state: WeekDayState,
    /** What a screen reader says for the day. The initial alone is ambiguous. */
    val spoken: String,
)

/**
 * Monday to Sunday under the streak number.
 *
 * Seven days rather than the five weeks `StreakCalendar` draws, because the
 * screens this sits on are about the run now: the strip gives the number a
 * shape, it is not a record to audit. This is the 2026-09 handoff's strip, a
 * disc per day with a tick or a cross on it, and it is the one the streak
 * screens should draw; a strip built from filled and empty dots is the older
 * idea.
 *
 * [justLanded] is the one day that pops. Everything else is drawn at its final
 * state on the first frame. The screen decides when it pops, because the
 * handoff has it land after the number has finished counting, and the count
 * is the screen's; the cell only knows how to arrive.
 */
@Composable
fun WeekStrip(
    days: List<WeekStripDay>,
    modifier: Modifier = Modifier,
    justLanded: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.Chip)
            .background(AppTheme.colors.surfaceSecondary.color)
            .padding(vertical = StripVerticalPadding, horizontal = StripHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEachIndexed { index, day ->
            WeekStripCell(day = day, landed = index == justLanded)
        }
    }
}

/**
 * One day: the initial over a disc.
 *
 * The pop is an [Animatable] read inside [graphicsLayer], never in
 * composition; the strip shares a screen with a counting number and a
 * button, and none of that should recompose on a spring's frames.
 *
 * Still under [LocalReduceAnimations] and [LocalInspectionMode], so a preview
 * shows the landed day rather than a disc at scale zero, and a player who has
 * asked for less motion gets the finished strip.
 */
@Composable
private fun WeekStripCell(day: WeekStripDay, landed: Boolean) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    val haptics = LocalHaptics.current

    val done = AppTheme.colors.accentBrand.color
    val tick = TickInk.color
    val missed = AppTheme.colors.missFill.color
    val cross = AppTheme.colors.missMark.color
    val empty = AppTheme.colors.surfaceMuted.color

    val pop = remember { Animatable(if (landed && !still) 0f else 1f) }
    LaunchedEffect(landed, still) {
        if (!landed || still) {
            pop.snapTo(1f)
            return@LaunchedEffect
        }
        pop.snapTo(0f)
        haptics.play(Feel.Win)
        pop.animateTo(Motion.PopOvershoot, Motion.Pop)
        pop.animateTo(1f, Motion.Tap)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LetterToDisc),
        modifier = Modifier.semantics { contentDescription = day.spoken },
    ) {
        Text(
            text = day.letter,
            typography = AppTheme.typography.Body.B500.Bold,
            color = AppTheme.colors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(DiscSize)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .drawBehind {
                    when (day.state) {
                        WeekDayState.Done -> {
                            drawCircle(done)
                            drawTick(tick)
                        }
                        WeekDayState.Missed -> {
                            drawCircle(missed)
                            drawCross(cross)
                        }
                        WeekDayState.Empty -> drawCircle(empty)
                    }
                },
        )
    }
}

/**
 * The handoff's tick, `M5 13l4.5 4.5L19 7` on a 24 box, drawn 17 wide inside
 * the 34 disc. The mark is a fraction of the disc rather than a fixed size so
 * a strip drawn at another scale keeps the same tick.
 */
private fun DrawScope.drawTick(color: Color) {
    val unit = size.minDimension * TickExtent / MarkBox
    val origin = (size.minDimension - MarkBox * unit) / 2f
    val path = Path().apply {
        moveTo(origin + 5f * unit, origin + 13f * unit)
        lineTo(origin + 9.5f * unit, origin + 17.5f * unit)
        lineTo(origin + 19f * unit, origin + 7f * unit)
    }
    drawPath(path, color, style = Stroke(width = TickStroke * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** `M6 6l12 12M18 6L6 18` on the same 24 box, drawn 15 wide. */
private fun DrawScope.drawCross(color: Color) {
    val unit = size.minDimension * CrossExtent / MarkBox
    val origin = (size.minDimension - MarkBox * unit) / 2f
    val near = origin + 6f * unit
    val far = origin + 18f * unit
    val width = CrossStroke * unit
    drawLine(color, Offset(near, near), Offset(far, far), strokeWidth = width, cap = StrokeCap.Round)
    drawLine(color, Offset(far, near), Offset(near, far), strokeWidth = width, cap = StrokeCap.Round)
}

/**
 * White, not the cream the rest of the app reads on amber with. The tick is a
 * mark rather than text, and the handoff draws it pure.
 */
@Suppress("DEPRECATION")
private val TickInk = ColorResource.White

/** The marks' own coordinate box, from the handoff's SVG. */
private const val MarkBox = 24f

/** Mark width as a fraction of the disc: 17 of 34 for the tick, 15 of 34 for the cross. */
private const val TickExtent = 0.5f
private const val CrossExtent = 15f / 34f
private const val TickStroke = 3.6f
private const val CrossStroke = 3.4f

private val DiscSize = Dimension.D1100
private val LetterToDisc = Dimension.D400
private val StripVerticalPadding = Dimension.D600
private val StripHorizontalPadding = Dimension.D400

@Preview
@Composable
private fun WeekStripPreview() {
    PreviewContent {
        WeekStrip(days = previewWeek(), justLanded = 3)
    }
}

@Suppress("MagicNumber")
internal fun previewWeek(): List<WeekStripDay> = listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { index, letter ->
    WeekStripDay(
        letter = letter,
        state = when {
            index < 4 -> WeekDayState.Done
            index == 4 -> WeekDayState.Missed
            else -> WeekDayState.Empty
        },
        spoken = "Day ${index + 1}",
    )
}
