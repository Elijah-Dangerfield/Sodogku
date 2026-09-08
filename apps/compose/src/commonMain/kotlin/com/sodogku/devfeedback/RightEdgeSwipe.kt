package com.sodogku.devfeedback

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.sodogku.system.Dimension
import kotlin.math.abs

/**
 * Fires [onTriggered] when a drag starts inside the right-edge strip and
 * travels far enough leftward to be unambiguous.
 *
 * Two details are load-bearing, and both were bugs first in the app this was
 * ported from:
 *
 * 1. **The `Initial` pass.** On the default `Main` pass a nested vertical
 *    scroller wins the deltas before this detector sees them, so the gesture
 *    works on a static screen and mysteriously stops working inside a list.
 *    `Initial` runs parent-first, so the strip observes every move.
 * 2. **It never consumes.** Nothing here calls `consume()`, and
 *    `awaitFirstDown` passes `requireUnconsumed = false`. The detector only
 *    watches, so a vertical drag that happens to start in the strip still
 *    scrolls the content underneath exactly as it would have.
 *
 * [enabled] is checked before the modifier is attached, so a player's build
 * carries no pointer handler at all rather than one that returns early.
 */
fun Modifier.rightEdgeSwipe(
    enabled: Boolean,
    onTriggered: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(onTriggered) {
    val edgeZonePx = EDGE_ZONE.toPx()
    val triggerPx = TRIGGER_DISTANCE.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Arm only for gestures that begin against the edge. A touch anywhere
        // else is ordinary content interaction and is not watched at all.
        if (down.position.x < size.width - edgeZonePx) return@awaitEachGesture

        var totalX = 0f
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) break

            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y

            // Leftward past the threshold, and more horizontal than vertical.
            // The dominance check is what stops a diagonal scroll from opening
            // the panel by accident.
            if (totalX <= -triggerPx && abs(totalX) > abs(totalY)) {
                onTriggered()
                break
            }
        }
    }
}

/** How close to the right edge a drag must start to arm the detector. */
private val EDGE_ZONE = Dimension.D900

/** How far it must then travel leftward before the panel opens. */
private val TRIGGER_DISTANCE = Dimension.D1500
