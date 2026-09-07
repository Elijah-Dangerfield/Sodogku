package com.sodogku.libraries.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer


fun Modifier.pulsate(scale: Float = 1.2f) = composed {

    val infiniteTransition = rememberInfiniteTransition(label = "")

    // Kept as State rather than unwrapped with `by`. This animation never ends,
    // so `by` here would recompose everything this modifier is applied to at
    // 60fps forever — and `Modifier.scale` reads its argument during
    // composition, which would keep it that way. graphicsLayer takes a lambda
    // that runs in the draw phase, so reading `.value` inside it means the
    // animation never invalidates composition at all.
    val scaleAnim = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = scale,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse animation"
    )

    this.graphicsLayer {
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
    }
}

