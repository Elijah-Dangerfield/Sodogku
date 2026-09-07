package com.sodogku.libraries.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.system.Motion

/**
 * A named thing on screen that a spotlight can point at.
 *
 * Keys are strings rather than an enum so a feature can mint one per board cell
 * (`"cell-37"`) without the design system knowing what a cell is.
 */
@Immutable
data class FocusTargetKey(val value: String)

/**
 * What the spotlight is currently showing.
 *
 * [targets] is the set of things that stay lit. Everything else dims. An empty
 * spotlight means nothing is focused, which is the resting state.
 */
@Immutable
data class Spotlight(
    val targets: Set<FocusTargetKey>,
    val message: String? = null,
    val dismissOnOutsideTap: Boolean = true,
)

/**
 * Registry of where each focusable thing physically is.
 *
 * Positions are reported by [focusTarget] as the layout settles and read by
 * [FocusScrim] when it draws, so the design system can cut holes in a scrim over
 * arbitrary composables without those composables knowing anything about
 * spotlights.
 */
@Stable
class FocusRegistry {
    internal val bounds = mutableStateMapOf<FocusTargetKey, Rect>()

    internal fun report(key: FocusTargetKey, rect: Rect) {
        bounds[key] = rect
    }

    internal fun forget(key: FocusTargetKey) {
        bounds.remove(key)
    }
}

/**
 * Defaults to an empty registry rather than erroring, so previews and unit tests
 * get a working no-op without providing anything.
 */
val LocalFocusRegistry = staticCompositionLocalOf { FocusRegistry() }

/**
 * Marks this composable as spotlight-able under [key].
 *
 * Costs one `onGloballyPositioned` per marked element, so mark the things a
 * tutorial or a warning will actually point at, not every cell on the board by
 * reflex.
 */
fun Modifier.focusTarget(key: FocusTargetKey): Modifier = composed {
    val registry = LocalFocusRegistry.current
    DisposableEffect(key) { onDispose { registry.forget(key) } }
    onGloballyPositioned { coordinates ->
        registry.report(
            key,
            Rect(coordinates.positionInRoot(), coordinates.size.toSize()),
        )
    }
}

/**
 * Dims everything except [spotlight]'s targets, and blocks taps outside them.
 *
 * The hole is punched with [BlendMode.Clear] into an offscreen layer rather than
 * drawn as four rectangles around the target, which is what lets a spotlight
 * cover several scattered cells at once — the "these three rows are ruled out"
 * case that a hint needs.
 *
 * Place it as the last child of a full-screen `Box` so it covers the content it
 * is dimming.
 */
@Composable
fun BoxScope.FocusScrim(
    spotlight: Spotlight?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = DefaultScrim,
    cornerRadius: Dp = DefaultCornerRadius,
    padding: Dp = DefaultPadding,
    content: @Composable (Rect) -> Unit = {},
) {
    val registry = LocalFocusRegistry.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(spotlight) {
        progress.animateTo(if (spotlight == null) 0f else 1f, Motion.Pop)
    }

    if (progress.value <= 0f) return

    val rects = spotlight?.targets
        ?.mapNotNull { registry.bounds[it] }
        .orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress.value
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawRect(scrimColor)
                val grow = progress.value
                rects.forEach { rect ->
                    val pad = padding.toPx()
                    val hole = RoundRect(
                        rect = Rect(
                            offset = Offset(rect.left - pad, rect.top - pad),
                            size = Size(rect.width + pad * 2, rect.height + pad * 2),
                        ),
                        radiusX = cornerRadius.toPx(),
                        radiusY = cornerRadius.toPx(),
                    )
                    drawPath(
                        Path().apply { addRoundRect(hole) },
                        Color.Transparent,
                        alpha = grow,
                        blendMode = BlendMode.Clear,
                    )
                }
                drawContent()
            }
            .pointerInput(spotlight) {
                detectTapGestures {
                    if (spotlight?.dismissOnOutsideTap == true) onDismiss()
                }
            },
    ) {
        val anchor = rects.firstOrNull() ?: Rect.Zero
        content(anchor)
    }
}

private val DefaultScrim = Color(0xC4101018)
private val DefaultCornerRadius = 14.dp
private val DefaultPadding = 6.dp
