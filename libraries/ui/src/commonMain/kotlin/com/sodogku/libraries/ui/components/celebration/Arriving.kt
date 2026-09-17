package com.sodogku.libraries.ui.components.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import kotlinx.coroutines.delay

/**
 * One beat of a celebration, landing.
 *
 * Fades, rises and springs to its place. Every value is read inside
 * [graphicsLayer] and none of it in composition, which is what keeps a rolling
 * counter, a row of stats or a five-week calendar off the recomposition path of
 * its own entrance.
 *
 * Still under [LocalReduceAnimations] and under [LocalInspectionMode], so the
 * page a player asked to calm down is the finished page, and so a preview or a
 * screenshot captures it rather than whichever frame it was on.
 *
 * Lifted out of the win celebration when the streak ceremony wanted the same
 * arrival. Two celebrations landing at different speeds is how an app ends up
 * with two ideas of what a reward feels like, and the stagger is also the thing
 * a screenshot test is most likely to catch mid-flight — one guard is better
 * than two that drift.
 */
@Composable
fun Arriving(order: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val still = LocalReduceAnimations.current || LocalInspectionMode.current
    val landed = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(order, still) {
        if (still) {
            landed.snapTo(1f)
            return@LaunchedEffect
        }
        landed.snapTo(0f)
        delay(beatDelayMillis(order).toLong())
        landed.animateTo(1f, Motion.Pop)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer {
            val progress = landed.value
            // Coerced because [Motion.Pop] overshoots past one, which is what
            // the scale wants and what alpha cannot have.
            alpha = progress.coerceIn(0f, 1f)
            val scale = ArrivalStartScale + (1f - ArrivalStartScale) * progress
            scaleX = scale
            scaleY = scale
            translationY = (1f - progress) * ArrivalRise.toPx()
        },
    ) {
        content()
    }
}

/**
 * A beat's place in the script, laid out as a still box.
 *
 * The same layout [Arriving] produces with none of the movement, for a page that
 * shows the same content without performing it. A caller that simply omitted the
 * wrapper would change the measurement as well as the motion, and the two
 * versions of the page would drift apart by a few pixels for no reason anybody
 * could see in the diff.
 */
@Composable
fun Arrived(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        content()
    }
}

/**
 * When the beat at [order] lands, counted from the moment the celebration starts
 * arriving — the win panel beginning its rise, or the streak ceremony beginning
 * to slide up over the board.
 */
fun beatDelayMillis(order: Int): Int = LeadMillis + order * BeatStaggerMillis

/**
 * How long the first beat waits for the page carrying it.
 *
 * Short of the full arrival on purpose. The first beat starting while the page
 * is still travelling is what makes the two read as one movement rather than as
 * a page that lands and then remembers it has contents.
 */
private const val LeadMillis = 220

/** The gap between one beat landing and the next starting. */
private const val BeatStaggerMillis = 70

/** How small a beat starts. Small enough to read as arriving, not as a glitch. */
private const val ArrivalStartScale = 0.82f

/** How far below its place a beat starts. */
private val ArrivalRise = Dimension.D700
