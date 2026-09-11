package com.sodogku.libraries.ui.system

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.sodogku.system.Motion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one scrim decision that can be made outside a composition, so it is.
 *
 * `FocusScrim` dims the board and cuts holes in itself, and it used to spring in
 * and out whatever the player had asked for. Reduced motion is a plain fade
 * rather than a shortened spring, which is the same call
 * `ModalDialogDefaults.animationSpecFor` makes and the same reasoning: the
 * setting is for players who find movement unpleasant.
 *
 * Stated as "it does not move past where it is going" rather than by naming a
 * spec type, because that is the part a player notices, and it fails for a
 * bounce that was merely made quicker.
 *
 * ### Not here
 *
 * That the scrim leaves composition once rather than three times is
 * `AnimatedStateReadInComposition`'s job now, enforced on every build. How the
 * spring behaves at the ends is `MotionTest`.
 */
class FocusScrimSpecTest {

    private fun travel(spec: AnimationSpec<Float>, from: Float, to: Float): List<Float> {
        val animation = TargetBasedAnimation(
            animationSpec = spec,
            typeConverter = Float.VectorConverter,
            initialValue = from,
            targetValue = to,
        )
        val step = animation.durationNanos / SampleCount
        return (0..SampleCount).map { animation.getValueFromNanos(it * step) }
    }

    @Test
    fun reducedMotionNeverMovesPastWhereItIsGoing() {
        val out = travel(focusScrimSpecFor(reduceAnimations = true), from = 1f, to = 0f)
        val back = travel(focusScrimSpecFor(reduceAnimations = true), from = 0f, to = 1f)

        assertTrue(out.min() >= 0f, "The fade-out dipped to ${out.min()}")
        assertTrue(back.max() <= 1f, "The fade-in reached ${back.max()}")
    }

    /**
     * The companion assertion. Without it, "reduced motion does not overshoot"
     * passes for an implementation that reduced the scrim for everybody.
     */
    @Test
    fun theDefaultIsStillTheBouncySpringTheRestOfTheAppUses() {
        assertEquals(Motion.Pop, focusScrimSpecFor(reduceAnimations = false))
    }

    private companion object {
        const val SampleCount = 600L
    }
}
