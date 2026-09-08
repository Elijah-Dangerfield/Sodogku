package com.sodogku.libraries.ui.components.board

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp

/**
 * Where each square of a grid sits, in pixels.
 *
 * Squares are numbered row-major, which is the numbering the rest of this
 * package already uses — see [PlacementPulse.roleOf], which reads a cell's row
 * as `index / size`.
 *
 * A value type with the arithmetic on it, rather than the arithmetic inlined in
 * the gesture, because the gesture cannot be unit tested and this can: every
 * interesting answer here is an edge — the gutter, the last row, a finger that
 * has left the board — and those are the answers that decide whether a stroke
 * paints the square under the thumb or the one next to it.
 */
@Immutable
data class BoardGeometry(
    val size: Int,
    val cellPx: Float,
    val gapPx: Float,
) {
    private val pitch: Float get() = cellPx + gapPx

    /** The square at [x], [y], or null for the gutter and for off the board. */
    fun cellAt(x: Float, y: Float): Int? {
        val row = trackAt(y) ?: return null
        val column = trackAt(x) ?: return null
        return row * size + column
    }

    /**
     * Which row or column a coordinate falls in, or null for neither.
     *
     * The gutter belongs to no square rather than to the nearer one. Rounding it
     * would let a stroke travelling down a grid line paint a column it never
     * actually touched, and the gutter is only a couple of dp wide — a finger
     * that meant a square is already on one.
     */
    private fun trackAt(position: Float): Int? {
        if (position < 0f) return null
        val index = (position / pitch).toInt()
        if (index >= size) return null
        return index.takeIf { position - it * pitch <= cellPx }
    }
}

/**
 * A drag across the grid, reported square by square.
 *
 * The gesture lives on the container rather than on each square, because a
 * pointer that has left a square stops being that square's business — the run of
 * squares a stroke crosses is only knowable from above.
 *
 * ### Why it waits for a second square
 *
 * Nothing is reported, and nothing is consumed, until the stroke reaches a
 * *different* square from the one it started on. Until then the children own the
 * gesture exactly as they did before this existed: a tap is a tap, and the
 * second tap of a double tap is the second tap of a double tap, however much the
 * thumb rolls while making it. That is the whole design constraint here. A drag
 * detector that claimed the gesture at touch slop would turn every slightly
 * sloppy double tap into a one-square drag, and the double tap is the gesture
 * this game charges a life for — it must not be the one that gets eaten.
 *
 * The cost is that a one-square drag does nothing. That is the right trade: a
 * one-square drag is a tap, and a tap already marks a square.
 *
 * Once the stroke does claim the gesture it consumes every change, which cancels
 * the tap the starting square was still holding — so the square under the thumb
 * when the finger went down is painted by [onDragStart] and never also tapped.
 *
 * @param cellSize the side of one square, as laid out.
 * @param gap the gutter between two squares.
 * @param onDragStart the square the stroke began on, reported once the stroke
 *   has committed to being a drag.
 * @param onDragEnter every further square the stroke crosses, in order, once
 *   each per entry.
 */
fun Modifier.dragAcrossCells(
    size: Int,
    cellSize: Dp,
    gap: Dp,
    enabled: Boolean = true,
    onDragStart: (cell: Int) -> Unit,
    onDragEnter: (cell: Int) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(size, cellSize, gap, enabled) {
    if (!enabled) return@pointerInput
    val grid = BoardGeometry(size, cellSize.toPx(), gap.toPx())
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        // `requireUnconsumed = false`: the square under the thumb has already
        // consumed this down for its own tap detector, and it is meant to keep
        // it. Watching the gesture is not the same as claiming it.
        val down = awaitFirstDown(requireUnconsumed = false)
        var last = grid.cellAt(down.position.x, down.position.y)
        var travelled = 0f
        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed()) break
            // Somebody else took the gesture before it became a drag. Nothing
            // here has been reported yet, so there is nothing to undo.
            if (!dragging && change.isConsumed) break

            travelled += change.positionChange().getDistance()
            val cell = grid.cellAt(change.position.x, change.position.y)
            // Touch slop as well as a change of square, for a thumb that goes
            // down a pixel from a boundary: without it the smallest wobble on
            // such a square would be a drag rather than the tap it was.
            if (cell != null && cell != last && travelled >= slop) {
                if (dragging) {
                    onDragEnter(cell)
                } else {
                    dragging = true
                    // Null when the thumb went down in the gutter, where there
                    // is no square to have started on. The first square the
                    // stroke reaches is the start instead.
                    onDragStart(last ?: cell)
                    if (last != null) onDragEnter(cell)
                }
                last = cell
            }
            if (dragging) change.consume()
        }
        if (dragging) onDragEnd()
    }
}
