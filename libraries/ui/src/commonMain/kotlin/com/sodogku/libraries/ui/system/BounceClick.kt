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

/**
 * A press that scales.
 *
 * **This does not name the control, and it cannot.** `Modifier.clickable`
 * contributes a click action and a role and nothing else, so anything built from
 * this comes out of a `uiautomator` dump as a focusable node with no accessible
 * name and a separate unfocusable child holding the text. Every caller that takes
 * a tap has to put a `contentDescription` on its own modifier, *before* calling
 * this — see `RuleChip` or `BoosterButton`.
 *
 * Two tidier fixes were tried on a device and neither reached the tree:
 * `Modifier.semantics(mergeDescendants = true) { }` inside this chain, both
 * before and after the `clickable` (the labelled child stayed a separate node
 * both times), and a `label` parameter on this function setting
 * `contentDescription` here. The same `contentDescription`, set by the caller one
 * link earlier in the chain, works. The difference is `composed { }`, which is
 * deprecated for reasons of about this shape; rewriting this onto `Modifier.Node`
 * is the real fix and would let the label move back in here where it belongs.
 */
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
