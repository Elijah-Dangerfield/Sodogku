package com.sodogku.features.streak.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.components.HeroBand
import com.sodogku.libraries.ui.components.HeroBandHeroSize
import com.sodogku.libraries.ui.components.HeroBandKickerTop
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.streak.WeekStrip
import com.sodogku.libraries.ui.components.streak.WeekStripDay
import com.sodogku.libraries.ui.components.text.CountUpNumber
import com.sodogku.libraries.ui.components.text.HeroNumeralStrokeWidth
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import kotlinx.coroutines.delay

/**
 * Which of the three ceremonies is on screen. The layout is the same for all
 * of them; what changes is the band's colour, what the dog is doing, and
 * what colour the number is.
 */
internal enum class StreakMoment {
    /** A finished board pushed the run up. Blue. */
    Update,

    /** The first board after a break. Brown, and the number is the run that ended. */
    Lost,

    /** The first streak day ever, shown once. Amber. */
    Intention,
}

/**
 * The one layout the three streak ceremonies share, from the 2026-09 handoff.
 *
 * Top to bottom: the band with the kicker in it and the dog hanging off its
 * edge, the number, the line under the number, a paragraph, the week, and the
 * actions pushed to the bottom. Every one of those is a slot or a string, and
 * the only thing a moment gets to decide is which [StreakMoment] it is; the
 * colours follow from that. Three screens that each laid this out by eye is
 * how the second one ends up a shade off the first.
 *
 * The band bleeds under the status bar, so the scaffold's top inset goes into
 * the kicker's padding rather than above the band; the bottom inset goes under
 * the buttons.
 *
 * **The script.** The number waits for the page to finish arriving and then
 * climbs ([numberStartMillis]); the week strip's new day pops once the number
 * has landed ([stripPopMillis]). The strip is handed [justLanded] only when
 * its turn comes, because the cell only knows how to arrive and the screen
 * owns when. Under reduce-animations or inspection the day is handed over at
 * once: the cell draws it at rest either way, and a preview should show the
 * finished strip rather than one with a day missing.
 */
@Composable
internal fun StreakCeremonyLayout(
    moment: StreakMoment,
    kicker: String,
    number: Int,
    countUpFrom: Int?,
    dayLabel: String,
    body: String,
    week: List<WeekStripDay>,
    justLanded: Int?,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    var landed by remember { mutableStateOf(if (still) justLanded else null) }
    LaunchedEffect(justLanded, countUpFrom, number, still) {
        if (justLanded != null && !still) delay(stripPopMillis(countUpFrom, number).toLong())
        landed = justLanded
    }

    Screen(modifier = modifier.fillMaxSize()) { insets ->
        val scrollState = rememberScrollState()
        // The buttons sit at the bottom of the screen when the content fits,
        // and below the content when it does not. A weighted spacer inside a
        // scroll only has a bottom to push against when the column's minimum
        // height is the viewport, which is what the `heightIn` is for.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .heightIn(min = viewport),
            ) {
                HeroBand(
                    color = moment.band(),
                    watermark = moment.watermark(),
                    watermarkAlpha = moment.watermarkAlpha,
                    kickerTopPadding = HeroBandKickerTop + insets.calculateTopPadding(),
                    kicker = {
                        Text(
                            text = kicker,
                            typography = AppTheme.typography.Label.Kicker,
                            color = moment.onBand(),
                            allCaps = true,
                            textAlign = TextAlign.Center,
                        )
                    },
                ) {
                    when (moment) {
                        StreakMoment.Update, StreakMoment.Intention -> Dog(pose = DogPose.Solved, size = DogSize)
                        StreakMoment.Lost -> LostDog(size = DogSize)
                    }
                }

                Spacer(Modifier.height(DogToNumber))

                CountUpNumber(
                    value = number,
                    countUpFrom = countUpFrom,
                    typography = AppTheme.typography.Display.D1600,
                    color = moment.numberColor(),
                    strokeColor = AppTheme.colors.textOutline,
                    strokeWidth = HeroNumeralStrokeWidth,
                    startDelayMillis = numberStartMillis(),
                )

                Text(
                    text = dayLabel,
                    typography = AppTheme.typography.Label.L750.Bold,
                    color = AppTheme.colors.accentBrandInk,
                    allCaps = true,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Dimension.D100),
                )

                Text(
                    text = body,
                    typography = AppTheme.typography.Body.B700,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(BodyPadding),
                )

                WeekStrip(
                    days = week,
                    justLanded = landed,
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )

                Spacer(Modifier.weight(1f))

                Column(
                    verticalArrangement = Arrangement.spacedBy(BetweenActions),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = ActionsTop,
                            start = ScreenGutter,
                            end = ScreenGutter,
                            bottom = insets.calculateBottomPadding().coerceAtLeast(ActionsBottom),
                        ),
                    content = actions,
                )
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun StreakMoment.band(): ColorResource = when (this) {
    StreakMoment.Update -> AppTheme.colors.accentPrimary
    StreakMoment.Lost -> AppTheme.colors.bandLoss
    StreakMoment.Intention -> AppTheme.colors.accentBrand
}

@Composable
@ReadOnlyComposable
private fun StreakMoment.watermark(): ColorResource = when (this) {
    StreakMoment.Update, StreakMoment.Lost -> AppTheme.colors.onAccentPrimary
    StreakMoment.Intention -> AppTheme.colors.watermarkOnBrand
}

/** The handoff's three washes: white at 0.14 on blue, 0.10 on brown, dark amber at 0.18 on amber. */
private val StreakMoment.watermarkAlpha: Float
    get() = when (this) {
        StreakMoment.Update -> 0.14f
        StreakMoment.Lost -> 0.1f
        StreakMoment.Intention -> 0.18f
    }

/** The amber band is the one that reads dark; see `onAccentBrand`. */
@Composable
@ReadOnlyComposable
private fun StreakMoment.onBand(): ColorResource = when (this) {
    StreakMoment.Update, StreakMoment.Lost -> AppTheme.colors.onBand
    StreakMoment.Intention -> AppTheme.colors.onAccentBrand
}

@Composable
@ReadOnlyComposable
private fun StreakMoment.numberColor(): ColorResource = when (this) {
    StreakMoment.Update, StreakMoment.Intention -> AppTheme.colors.accentBrand
    StreakMoment.Lost -> AppTheme.colors.textEnded
}

/**
 * The dog on the lost screen: the puzzled pose with a drop of sweat drawn
 * over it.
 *
 * No pose in [DogPose] is worried, so this is the handoff's fallback, the
 * plain dog plus a teardrop overlay, placed where the board puts it: a fifth
 * of the glyph wide, at 14% in from the left and 2% down from the top of the
 * glyph's box. The handoff also desaturates the dog by 12%; that is not done,
 * because `Dog` has no colour-filter seam and a saturation matrix through a
 * layer is not cheap enough to add for a change nobody can see.
 */
@Composable
private fun LostDog(size: Dp) {
    val drop = AppTheme.colors.sweatDrop.color
    val shine = AppTheme.colors.textOutline.color
    Dog(
        pose = DogPose.Thinking,
        size = size,
        modifier = Modifier.drawWithContent {
            drawContent()
            drawSweatDrop(drop, shine)
        },
    )
}

/**
 * The handoff's teardrop, `M10 0c4.2 5.6 8 10 8 14.6A8 8 0 012 14.6C2 10 5.8
 * 5.6 10 0z` on a 20 by 26 box, with its highlight arc.
 */
@Suppress("MagicNumber")
private fun ContentDrawScope.drawSweatDrop(fill: Color, shine: Color) {
    val unit = size.width * DropWidthFraction / DropBoxWidth
    withTransform({
        translate(size.width * DropLeftFraction, size.height * DropTopFraction)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        val drop = Path().apply {
            moveTo(10f, 0f)
            cubicTo(14.2f, 5.6f, 18f, 10f, 18f, 14.6f)
            arcTo(Rect(2f, 6.6f, 18f, 22.6f), startAngleDegrees = 0f, sweepAngleDegrees = 180f, forceMoveTo = false)
            cubicTo(2f, 10f, 5.8f, 5.6f, 10f, 0f)
            close()
        }
        drawPath(drop, fill)
        drawArc(
            color = shine,
            startAngle = 183f,
            sweepAngle = -71f,
            useCenter = false,
            topLeft = Offset(6.6f, 11.2f),
            size = Size(6.8f, 6.8f),
            style = Stroke(width = 1.6f, cap = StrokeCap.Round),
            alpha = 0.85f,
        )
    }
}

/** The drop's own box, from the handoff's SVG viewBox. */
private const val DropBoxWidth = 20f

/** 25 wide on a 124 glyph, at `left: 14%; top: 2%`. */
private const val DropWidthFraction = 25f / 124f
private const val DropLeftFraction = 0.14f
private const val DropTopFraction = 0.02f

private val DogSize = HeroBandHeroSize

/**
 * The board starts the number 72 below the band's edge and the dog overhangs
 * it by 56, so the digits sit this far under the dog's feet.
 */
private val DogToNumber = Dimension.D700

/**
 * `padding: 30px 42px 34px`, straight off the board. Two of the three are
 * off the scale by two, and written as the nearest rung plus the difference
 * rather than snapped, because the copy block's height is what puts the week
 * strip where the board has it.
 */
private val BodyPadding = PaddingValues(
    start = Dimension.D1200 + Dimension.D50,
    top = Dimension.D1000 + Dimension.D50,
    end = Dimension.D1200 + Dimension.D50,
    bottom = Dimension.D1100,
)

private val ScreenGutter = Dimension.D700
private val ActionsTop = Dimension.D1200
private val ActionsBottom = Dimension.D1100
private val BetweenActions = Dimension.D600
