package com.sodogku.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.typography.TypographyResource
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The running score, counting up to each new value rather than snapping.
 *
 * The count-up is the point. A number that jumps from 3,840 to 4,896 reads as a
 * different number; one that rolls there reads as *earning* 1,056, which is the
 * feedback the whole scoring system exists to deliver.
 *
 * The roll takes longer for a bigger jump. It used to be a flat 420ms whatever
 * the delta, which is fine for the few hundred points a placement pays and far
 * too fast for the few thousand a completion pays: the digits blurred and the
 * number simply appeared to change, which is the thing the animation exists to
 * avoid. Scaling it means a placement still feels immediate while a level clear
 * is watchable.
 */
@Composable
fun ScoreCounter(
    score: Int,
    modifier: Modifier = Modifier,
    /**
     * What the counter reads before its first roll.
     *
     * Defaults to [score], because the header's counter is on screen for the
     * whole board and has nothing to earn: it starts holding the player's real
     * total and only rolls when that total moves. The win sheet's counter
     * *appears* already holding its number, so it passes zero and the number is
     * seen to be earned rather than found.
     */
    countFrom: Int = score,
    typography: TypographyResource = AppTheme.typography.Display.D900,
    color: ColorResource = AppTheme.colors.text,
    /**
     * Draw `130.5K` rather than `130450`.
     *
     * Opt-in, and only the board's header takes it. That counter holds the
     * lifetime total, which is already six figures by level 29 and will reach
     * seven — at display size beside a level number it stops being a number and
     * becomes a wall of digits, and it is glanced at rather than read. The
     * outcome sheet's counter shows one attempt's score, which is three or four
     * digits and is the number the sheet is *about*, so it stays exact.
     */
    abbreviated: Boolean = false,
) {
    // Read here rather than taken as a parameter, so a new screen honours the
    // setting without its author knowing the setting exists. A number that rolls
    // is an animation like any other, and a preview that captured one mid-roll
    // would screenshot a number the player never sees.
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    var displayed by remember { mutableIntStateOf(if (still) score else countFrom) }

    LaunchedEffect(score, still) {
        val start = displayed
        if (still) {
            displayed = score
            return@LaunchedEffect
        }
        if (start == score) return@LaunchedEffect
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(durationMillis = countUpMillis(score - start))) {
            displayed = (start + (score - start) * value).roundToInt()
        }
        displayed = score
    }

    Text(
        text = if (abbreviated) abbreviateScore(displayed) else displayed.toString(),
        typography = typography,
        color = color,
        modifier = modifier,
    )
}

/**
 * A score short enough to sit in a header: `845`, `1.2K`, `130.5K`, `1.2M`.
 *
 * One decimal, always, and dropped when it is zero — `12K` rather than `12.0K`.
 * The decimal is what stops the abbreviation eating the feedback: without it a
 * clear worth 400 points would leave a six-figure total reading `130K` before
 * and after, and the count-up roll above would animate between two identical
 * strings.
 *
 * Under a thousand the number is drawn exactly, because there is nothing to
 * save: `845` is shorter than `0.8K` and it is also true.
 *
 * The promotion at the top is the case worth stating. 999,999 rounds to
 * `1000.0K`, which is longer than the number it abbreviates and reads as a
 * mistake; it becomes `1M`. That is detected by counting digits rather than by
 * comparing against a hand-written 999,950, so the edge cannot drift away from
 * the rounding that produces it.
 */
internal fun abbreviateScore(score: Int): String {
    if (score < Thousand) return score.toString()
    val thousands = oneDecimal(score.toDouble() / Thousand)
    if (thousands.substringBefore('.').length <= AbbreviatedDigits) return thousands + "K"
    return oneDecimal(score.toDouble() / Million) + "M"
}

/** [value] to one decimal place, with a trailing `.0` dropped. */
private fun oneDecimal(value: Double): String {
    val tenths = (value * TENTHS).roundToInt()
    val whole = tenths / TENTHS
    val fraction = tenths % TENTHS
    return if (fraction == 0) whole.toString() else "$whole.$fraction"
}

/**
 * A handful of golden paws converging on the score that just went up.
 *
 * Drawn as an overlay on the number itself rather than measured between two
 * anchors: the paws launch from a spread of points above it, which is where the
 * rating they come from sits, and converge on its centre. The measured version
 * buys a few pixels of accuracy and costs an anchor registry threaded through
 * every layer between the two, which is machinery this does not need.
 *
 * Longer than the ~300ms [Motion] holds most things to, and for the same reason
 * `Motion.PlacementPulseMillis` is: this is a reward rather than feedback. It
 * fires once at the end of a board, it takes no input away while it runs, and
 * the button under it is live the whole time.
 */
@Composable
fun ScorePawBurst(modifier: Modifier = Modifier) {
    if (LocalReduceAnimations.current || LocalInspectionMode.current) return

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        repeat(PawsInBurst) { index ->
            val progress = remember(index) { Animatable(0f) }
            LaunchedEffect(index) {
                delay(index * PawStaggerMillis.toLong())
                progress.animateTo(
                    1f,
                    tween(durationMillis = PawFlightMillis, easing = FastOutSlowInEasing),
                )
            }
            // The spread runs left to right across the burst, so five paws leave
            // as a group rather than stacking on one point.
            val lane = index - (PawsInBurst - 1) / 2f
            Box(
                modifier = Modifier
                    .size(PawSize)
                    // Read here and never in composition. The sheet holds a
                    // share card and a paw rating, and subscribing that subtree
                    // to sixty frames a second is the one way this flourish
                    // could cost the player something.
                    .graphicsLayer {
                        val travelled = progress.value
                        val remaining = 1f - travelled
                        translationX = lane * PawSpread.toPx() * remaining
                        // Negative is up the screen, so they start above the
                        // number and fall into it. The rating they come from is
                        // the row directly above.
                        translationY = -PawDrop.toPx() * remaining
                        val scale = PawLandScale + (1f - PawLandScale) * remaining
                        scaleX = scale
                        scaleY = scale
                        alpha = when {
                            travelled < PawFadeIn -> travelled / PawFadeIn
                            travelled > PawFadeOut -> (1f - travelled) / (1f - PawFadeOut)
                            else -> 1f
                        }.coerceIn(0f, 1f)
                    }
                    .drawBehind { drawPaw(PawGold, filled = true) },
            )
        }
    }
}

/**
 * Points floating up from a placement, with the praise word above them.
 *
 * Keyed on [nonce] rather than on the value: two placements can be worth exactly
 * the same points, and a value-keyed animation would silently skip the second.
 */
@Composable
fun FloatingPoints(
    points: Int,
    praise: String?,
    nonce: Int,
    modifier: Modifier = Modifier,
    riseBy: Dp = Dimension.D1300,
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(nonce) {
        if (nonce == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = FloatMillis))
    }

    if (progress.value >= 1f) return

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer {
            translationY = -riseBy.toPx() * progress.value
            alpha = (1f - progress.value).coerceIn(0f, 1f)
            val scale = FloatStartScale + (1f - FloatStartScale) * (progress.value * FloatScaleRamp)
                .coerceAtMost(1f)
            scaleX = scale
            scaleY = scale
        },
    ) {
        Text(
            text = buildString {
                if (praise != null) append(praise).append('\n')
                append('+').append(points)
            },
            typography = AppTheme.typography.Heading.H500,
            color = AppTheme.colors.accentPrimary,
        )
    }
}

/**
 * How long to roll, given how far.
 *
 * Linear in the delta between a floor and a ceiling. The floor keeps a small
 * gain from feeling sluggish, and the ceiling keeps a huge one from holding the
 * screen: past a few thousand points nobody is reading the digits anyway, they
 * are watching it climb, and the extra time buys nothing.
 */
internal fun countUpMillis(delta: Int): Int =
    (MinCountUpMillis + abs(delta) * MillisPerPoint).roundToInt()
        .coerceAtMost(MaxCountUpMillis)

/** Quick enough that a single placement still reads as immediate. */
private const val MinCountUpMillis = 320

/** A level clear pays a few thousand, and lands near the ceiling. */
private const val MaxCountUpMillis = 1400

private const val MillisPerPoint = 0.35f

private const val Thousand = 1_000
private const val Million = 1_000_000
private const val TENTHS = 10

/** Above three digits before the point, the next suffix up is the shorter answer. */
private const val AbbreviatedDigits = 3
private const val FloatMillis = 900
private const val FloatStartScale = 0.7f
private const val FloatScaleRamp = 4f

/**
 * Enough to read as a handful, few enough to arrive as one gesture. Each one is
 * a `graphicsLayer` and a `drawBehind`, so this is also the whole cost.
 */
private const val PawsInBurst = 5

private const val PawFlightMillis = 360
private const val PawStaggerMillis = 50

/** How far apart the paws start, per lane either side of the number. */
private val PawSpread = Dimension.D800

/** How far above the number they start, which is about where the rating sits. */
private val PawDrop = Dimension.D1400

private val PawSize = Dimension.D800

/** They shrink as they land, so the number reads as absorbing them. */
private const val PawLandScale = 0.45f

private const val PawFadeIn = 0.15f
private const val PawFadeOut = 0.85f

/**
 * The gold of an earned paw. A second copy of `GameHud`'s `EarnedPaw`, which is
 * file-private there. The two are the same colour on purpose, and want to be one
 * token the next time that file is open.
 */
private val PawGold = Color(0xFFF5B93D)

@Preview
@Composable
private fun ScoreCounterPreview() {
    PreviewContent {
        Box(contentAlignment = Alignment.Center) {
            ScoreCounter(score = 4_896)
            FloatingPoints(points = 1_056, praise = "Excellent", nonce = 1)
        }
    }
}
