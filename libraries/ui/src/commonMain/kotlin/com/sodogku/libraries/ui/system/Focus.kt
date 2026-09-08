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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
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

    /**
     * Whether a tap on a lit target is reported back through `onTargetTap`
     * instead of dismissing the spotlight.
     *
     * Off by default, and the default is the one to keep for a *warning*: the
     * last-bone spotlight lights the bones, and a tap on them should close the
     * warning rather than do anything to the bones.
     *
     * On, the lit things are the only interactive part of the screen — which is
     * the whole of "only the correct cell is tappable" in a guided lesson. Pair
     * it with `dismissOnOutsideTap = false` so a stray tap elsewhere does
     * nothing rather than clearing a step the player has not completed.
     */
    val targetsAreLive: Boolean = false,
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
 *
 * [content] is handed the **union** of the lit rectangles, not one of them. A
 * caller hanging a card off the spotlight wants the box around everything that
 * is lit; anchoring on an arbitrary member of the set puts the card on top of
 * the others.
 */
@Composable
fun BoxScope.FocusScrim(
    spotlight: Spotlight?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = DefaultScrim,
    cornerRadius: Dp = DefaultCornerRadius,
    padding: Dp = DefaultPadding,
    onTargetTap: (FocusTargetKey) -> Unit = {},
    content: @Composable (Rect) -> Unit = {},
) {
    val registry = LocalFocusRegistry.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(spotlight) {
        progress.animateTo(if (spotlight == null) 0f else 1f, Motion.Pop)
    }

    if (progress.value <= 0f) return

    val lit = spotlight?.targets
        ?.mapNotNull { key -> registry.bounds[key]?.let { key to it } }
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
                lit.forEach { (_, rect) ->
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
            // The scrim swallows the whole screen and *reports* the taps that
            // land in a hole, rather than letting them fall through to what is
            // underneath. Falling through is not on offer: a Compose overlay
            // does not share pointer input with the siblings it covers, so a
            // lit composable never sees the touch however carefully the scrim
            // declines to consume it. Reporting the key back is the honest
            // version, and it keeps double-tap timing in the caller's hands.
            .pointerInput(spotlight, lit) {
                val holes = lit.map { (key, rect) -> key to rect.inflate(padding.toPx()) }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val hit = holes.firstOrNull { (_, hole) -> hole.contains(down.position) }
                    waitForUpOrCancellation()?.let { up ->
                        up.consume()
                        when {
                            hit != null && spotlight?.targetsAreLive == true ->
                                onTargetTap(hit.first)
                            spotlight?.dismissOnOutsideTap == true -> onDismiss()
                        }
                    }
                }
            },
    ) {
        content(lit.map { it.second }.union())
    }
}

/**
 * Takes this subtree out of the accessibility tree while something covers it.
 *
 * A scrim is a drawing, and drawings are invisible to semantics: everything
 * under [FocusScrim], under a sheet, under the level pane stays focusable to a
 * screen reader and stays *activatable*, even though the scrim swallows every
 * touch before it can reach the thing underneath. Without this, a screen-reader
 * player can place a dog through a coach mark that a sighted player cannot even
 * tap — the overlay is not blocking them, it is only hiding the board.
 *
 * Applied to what is being covered, not to the cover. Compose has no way for a
 * sibling to reach back over the ones it was drawn on top of, so the screen that
 * knows an overlay is up is the one that has to say so.
 */
fun Modifier.coveredByOverlay(covered: Boolean): Modifier =
    if (covered) semantics { hideFromAccessibility() } else this

/** The single box around every lit rectangle, or [Rect.Zero] when nothing is lit. */
private fun List<Rect>.union(): Rect = fold(null as Rect?) { box, rect ->
    if (box == null) {
        rect
    } else {
        Rect(
            left = minOf(box.left, rect.left),
            top = minOf(box.top, rect.top),
            right = maxOf(box.right, rect.right),
            bottom = maxOf(box.bottom, rect.bottom),
        )
    }
} ?: Rect.Zero

private val DefaultScrim = Color(0xC4101018)
private val DefaultCornerRadius = 14.dp
private val DefaultPadding = 6.dp
