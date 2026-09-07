package com.sodogku.system

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * The motion vocabulary. Screens pick a named intent, not a damping ratio, so
 * two bouncy things in the app bounce the same way.
 *
 * Two rules the tokens encode. Everything springs rather than eases, because a
 * linear tween on a bubbly game reads as cheap. And everything lands inside
 * ~300ms, because a player clearing ten easy levels in a row will sit through
 * these hundreds of times and any one of them being "delightful" at 600ms
 * becomes the thing they uninstall over.
 */
object Motion {

    /** A dog landing on a correct cell. Overshoots, then settles. */
    val Pop: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Press feedback and small state flips. Quick, barely overshoots. */
    val Tap: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Colour and alpha transitions, where a spring would look like a glitch. */
    fun <T> fade(): AnimationSpec<T> = tween(durationMillis = FadeMillis)

    /** A wrong tap. Short, sharp, and over before the player can dwell on it. */
    const val ShakeMillis: Int = 260

    /** Cell fills, X marks appearing, anything cross-fading. */
    const val FadeMillis: Int = 180

    /**
     * How long a player's cross takes to draw itself, stroke by stroke.
     *
     * Short on purpose. A player marking up a 10x10 will do this dozens of
     * times, and anything slower than a flick of a pen becomes the thing
     * standing between them and their next thought.
     */
    const val MarkDrawMillis: Int = 170

    /** Per-cell stagger for the board's entrance wave, measured on the diagonal. */
    const val BoardWaveStepMillis: Int = 22

    /** How far a pressed element scales down. Matches `bounceClick`'s default. */
    const val PressScale: Float = 0.90f

    /** How far a popping-in dog overshoots before settling. */
    const val PopOvershoot: Float = 1.15f
}
