package com.sodogku.libraries.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Indication
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.bounceClick(
    enabled: Boolean = true,
    mutableInteractionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    scaleDown: Float = 0.90f,
    onClick: () -> Unit = {}
) = composed {

    val interactionSource = mutableInteractionSource ?: remember {  MutableInteractionSource() }

    val animatable = remember { Animatable(1f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> animatable.animateTo(scaleDown)
                is PressInteraction.Release -> animatable.animateTo(1f)
                is PressInteraction.Cancel -> animatable.animateTo(1f)
            }
        }
    }

    this
        .graphicsLayer {
            val scale = animatable.value
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = indication,
            onClick = onClick
        )
}
/**
 * [bounceClick] with a long press.
 *
 * Separate from [bounceClick] rather than a nullable parameter on it, because
 * `combinedClickable` changes tap handling even when the long-press callback is
 * null: it has to wait out the long-press timeout before it can be sure a tap
 * was a tap. On the board that delay is the difference between a game that feels
 * instant and one that feels laggy, so only cells that genuinely need both
 * gestures pay for it.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceCombinedClick(
    enabled: Boolean = true,
    mutableInteractionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    scaleDown: Float = 0.90f,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) = composed {

    val interactionSource = mutableInteractionSource ?: remember { MutableInteractionSource() }

    val animatable = remember { Animatable(1f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> animatable.animateTo(scaleDown)
                is PressInteraction.Release -> animatable.animateTo(1f)
                is PressInteraction.Cancel -> animatable.animateTo(1f)
            }
        }
    }

    this
        .graphicsLayer {
            val scale = animatable.value
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = indication,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}
