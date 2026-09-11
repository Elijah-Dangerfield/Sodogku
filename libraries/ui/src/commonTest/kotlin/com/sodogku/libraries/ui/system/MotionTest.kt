package com.sodogku.libraries.ui.system

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.sodogku.system.Motion
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the motion tokens do outside the range they are animated between.
 *
 * `Pop` is a bouncy spring, which is the whole point of it, and a bouncy spring
 * goes past its target before it settles. That is invisible on a scale or a
 * translation and load-bearing on a *progress* value: anything that treats
 * `0f` as "gone" and springs to zero is gone, back, and gone again.
 *
 * Sampled from a [TargetBasedAnimation] rather than run through an `Animatable`,
 * because the curve is the claim and a frame clock would only be scaffolding
 * around it.
 */
class MotionTest {

    private fun samples(from: Float, to: Float): List<Float> {
        val animation = TargetBasedAnimation(
            animationSpec = Motion.Pop,
            typeConverter = Float.VectorConverter,
            initialValue = from,
            targetValue = to,
        )
        val step = animation.durationNanos / SampleCount
        return (0..SampleCount).map { animation.getValueFromNanos(it * step) }
    }

    @Test
    fun popUndershootsZeroOnTheWayOut() {
        val lowest = samples(from = 1f, to = 0f).min()

        assertTrue(
            lowest < 0f,
            "A gate on `progress <= 0f` leaves and re-enters composition mid-dismissal; " +
                "Pop bottoms out at $lowest",
        )
    }

    @Test
    fun popOvershootsOneOnTheWayIn() {
        val highest = samples(from = 0f, to = 1f).max()

        assertTrue(
            highest > 1f,
            "Pop tops out at $highest, so a progress value it drives is not a fraction",
        )
    }

    private companion object {
        const val SampleCount = 600L
    }
}
