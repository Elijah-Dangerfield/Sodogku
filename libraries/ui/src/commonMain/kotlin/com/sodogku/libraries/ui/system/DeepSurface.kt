package com.sodogku.libraries.ui.system

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sodogku.system.Radii
import com.sodogku.system.Radius
import com.sodogku.system.clip

/**
 * A coloured face sitting on a darker lip, which the face drops onto when
 * pressed.
 *
 * The same treatment `BasicButton` gives every filled button, pulled out so the
 * board's own controls can share it. They previously used a gradient-and-sheen
 * of their own, and the result read as a shadow blob under a pill rather than as
 * a thing with thickness — two ways of drawing "pressable" in one app, and the
 * bespoke one lost.
 *
 * The illusion is the lip and nothing else: a band of a darker shade, the same
 * shape, showing below the face. The face keeps a constant bottom reserve so the
 * control's height never changes, and the press moves the face down into the
 * lip rather than scaling anything.
 */
@Composable
fun DeepSurface(
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Radius = Radii.Round,
    depth: Dp = DefaultDepth,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    // State rather than `by`: `.offset { }` already defers to the layout phase,
    // and reading the value in composition would recompose the content on every
    // frame of the press spring.
    val drop = animateDpAsState(
        targetValue = if (pressed && enabled) depth else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "DeepSurface",
    )

    Box(
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        ),
    ) {
        Box(Modifier.matchParentSize().clip(shape).background(color.deepen()))
        Box(
            propagateMinConstraints = true,
            modifier = Modifier
                .padding(bottom = depth)
                .offset { IntOffset(x = 0, y = drop.value.roundToPx()) }
                .clip(shape)
                .background(color),
        ) { content() }
    }
}

/**
 * The same face-on-a-lip, for something that is not pressable.
 *
 * [DeepSurface] is a control: it takes a click, collects presses, and drops the
 * face into the lip when you hold it. A reward chip on a level row is not any of
 * that — it is a label — and wrapping one in a `clickable` to borrow the look
 * would hand a screen reader a button that does nothing.
 *
 * So the lip is available on its own. Two rules of the illusion are worth
 * knowing before using it: the darker band has to be the same hue rather than a
 * grey, and the face has to reserve the same [depth] whether or not anything is
 * pressing it, or the thing changes height when it changes state.
 */
fun Modifier.deepFace(
    color: Color,
    shape: Radius = Radii.Round,
    depth: Dp = DefaultDepth,
): Modifier = this
    .background(color.deepen(), shape.shape)
    .padding(bottom = depth)
    .background(color, shape.shape)

/**
 * The lip's colour, derived rather than passed.
 *
 * A caller choosing both would eventually choose a pair that does not look like
 * one object in two lights, and there is only one right answer: the same hue,
 * darker.
 */
private fun Color.deepen(): Color =
    Color(red * (1f - Deepening), green * (1f - Deepening), blue * (1f - Deepening), alpha)

private const val Deepening = 0.28f

/** Deep enough to read as an edge, shallow enough not to look like two stacked pills. */
private val DefaultDepth: Dp = 4.dp
