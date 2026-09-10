package com.sodogku.devfeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.IconSize
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import kotlin.math.roundToInt

/**
 * The tester's way into the feedback panel: a small button that floats over the
 * app, goes where it is put, and opens the form when tapped.
 *
 * It replaced a handle pinned to the right edge, which was hard to grab and, on a
 * big grid, sat on cells the player needed. Being movable is the requirement:
 * the board is the whole screen, so *any* fixed position is over something.
 *
 * **It does not take touches it is not over.** The full-size [Box] below is a
 * bare layout node with no pointer handler, so it is invisible to hit testing;
 * only the 48dp button itself has a `pointerInput`, and a drag that begins
 * anywhere else never reaches this file. That is the difference from the edge
 * swipe this replaced, which watched every gesture on the screen from the
 * `Initial` pass to decide whether it was interested.
 *
 * [excludeFromSystemGestures] is still needed, and for the reason it always was:
 * dragged against the left or right edge, the button lands inside the strip
 * Android's gesture navigation claims for back, and the OS wins that touch
 * otherwise. It is scoped to the button's own rect, so the exclusion travels
 * with it rather than blanking an edge of the screen.
 */
@Composable
internal fun DevFeedbackFab(
    placement: FabPlacement,
    onSettled: (FabPlacement) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonPx = with(LocalDensity.current) { FabSize.roundToPx() }
    // Neither of these is read during composition — the offset lambda reads them
    // in the layout phase and the drag callback reads them off the pointer
    // coroutine. A drag therefore relayouts one node per frame and recomposes
    // nothing, which is the same reason an animated value belongs in
    // `graphicsLayer` rather than in a composable body.
    var travel by remember { mutableStateOf(IntSize.Zero) }
    val position = remember { mutableStateOf(placement) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Keeps the button out from under the status and navigation bars,
            // and makes the travel it is clamped to the area it can actually be
            // seen in.
            .safeDrawingPadding()
            .onSizeChanged { travel = travelWithin(it, buttonPx) },
    ) {
        Box(
            modifier = Modifier
                .offset { position.value.offsetIn(travel) }
                .size(FabSize)
                .excludeFromSystemGestures()
                .clip(CircleShape)
                .background(color = AppTheme.colors.accentPrimary.color, shape = CircleShape)
                // A ring in the background colour, because this thing is
                // deliberately parked over arbitrary UI and a bare accent circle
                // disappears into an accent-coloured cell.
                .border(
                    width = Dimension.D50,
                    color = AppTheme.colors.background.color,
                    shape = CircleShape,
                )
                .pointerInput(onSettled) {
                    detectDragGestures(
                        // Written once, when the finger lifts. Persisting every
                        // frame would put a disk write on the drag.
                        onDragEnd = { onSettled(position.value) },
                    ) { change, dragAmount ->
                        change.consume()
                        position.value = position.value.movedBy(dragAmount, travel)
                    }
                }
                // A tap opens the panel and a drag does not, which is not luck.
                // `clickable` consumes the *down*, but `detectDragGestures`
                // arms with `requireUnconsumed = false`, so both are live; then
                // the drag consumes each move past touch slop, and
                // `waitForUpOrCancellation` re-checks consumption on the `Final`
                // pass and gives the click up. Take `clickable` off and this
                // loses its semantics role and its ripple; take the ordering
                // for granted and a moved button also files a directive.
                .clickable(onClick = onClick)
                .semantics { contentDescription = FabDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon = Icons.Bug.decorative,
                size = IconSize.Small,
                color = AppTheme.colors.onAccentPrimary,
            )
        }
    }
}

/**
 * Where the button sits, as a fraction of how far it can travel.
 *
 * `0f` is flush against the left or top edge of the area it may occupy, `1f`
 * flush against the right or bottom. Fractions rather than pixels so the same
 * stored position survives a rotation, a split-screen window and a different
 * device — see [DevFeedbackFabState].
 */
data class FabPlacement(val x: Float, val y: Float)

/**
 * How far the button can move inside [container], which is the container less
 * the button itself.
 *
 * Floored at zero rather than allowed to go negative: a window narrower than the
 * button has no travel, and a negative one would flip the meaning of the
 * fractions and place the button *outside* the container it is clamped to.
 */
internal fun travelWithin(container: IntSize, buttonPx: Int): IntSize = IntSize(
    width = (container.width - buttonPx).coerceAtLeast(0),
    height = (container.height - buttonPx).coerceAtLeast(0),
)

internal fun FabPlacement.offsetIn(travel: IntSize): IntOffset = IntOffset(
    x = (x * travel.width).roundToInt(),
    y = (y * travel.height).roundToInt(),
)

/**
 * This placement moved by a drag delta in pixels.
 *
 * The clamp is what keeps the button reachable. Without it a drag off the top of
 * the screen parks a tester's only route to the feedback form somewhere they
 * cannot tap, and the only way back is to reinstall.
 */
internal fun FabPlacement.movedBy(dragAmount: Offset, travel: IntSize): FabPlacement = FabPlacement(
    x = nudge(x, dragAmount.x, travel.width),
    y = nudge(y, dragAmount.y, travel.height),
)

private fun nudge(fraction: Float, delta: Float, travel: Int): Float =
    if (travel <= 0) 0f else (fraction + delta / travel).coerceIn(0f, 1f)

/**
 * 48dp: the minimum comfortable touch target, and no bigger.
 *
 * The complaint that started this was that the old affordance was hard to grab,
 * so it has to clear that bar; it also spends its life on top of a puzzle, so
 * anything larger covers more of the thing being reported on.
 */
private val FabSize = Dimension.D1300

/** What `scripts/dev/drive.py tap "Leave feedback"` looks for. */
private const val FabDescription = "Leave feedback"
