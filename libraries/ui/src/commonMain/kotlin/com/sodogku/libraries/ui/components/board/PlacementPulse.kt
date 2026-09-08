package com.sodogku.libraries.ui.components.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** What a square has to do about a placement that just happened. */
enum class PlacementRole {
    /** Nothing. Most of the board, most of the time. */
    None,

    /** On the row or column the placement resolved. Glows and settles. */
    Line,

    /** The square the dog landed on. Gets the starburst as well. */
    Origin,
}

/**
 * Which squares should react to the placement that just happened.
 *
 * A placement resolves exactly two lines — its own row and its own column —
 * because that is what the rules of the board say a dog does. Showing that is
 * the difference between a dog appearing and a *deduction landing*: the player
 * made an inference about a row, and the row is what should answer.
 *
 * [nonce] rather than a flag, and for the same reason a strike carries one: two
 * placements in the same row have to be two animations, and a boolean that is
 * already true cannot restart one.
 */
@Immutable
data class PlacementPulse(
    val nonce: Int,
    private val row: Int,
    private val column: Int,
    private val cell: Int,
    private val size: Int,
) {
    fun roleOf(index: Int): PlacementRole = when {
        nonce == 0 -> PlacementRole.None
        index == cell -> PlacementRole.Origin
        index / size == row || index % size == column -> PlacementRole.Line
        else -> PlacementRole.None
    }

    companion object {
        val None = PlacementPulse(nonce = 0, row = -1, column = -1, cell = -1, size = 1)

        /**
         * The pulse for going from [before] to [after], or [None] when nothing
         * was added.
         *
         * Exactly one added square counts. Zero is the common case — a mark, a
         * strike, a redraw — and *more* than one is a restore or an undo rather
         * than a move somebody made, which has no single line to celebrate.
         */
        fun between(before: Set<Int>, after: Set<Int>, size: Int, nonce: Int): PlacementPulse {
            val added = after - before
            val cell = added.singleOrNull() ?: return None
            return PlacementPulse(
                nonce = nonce,
                row = cell / size,
                column = cell % size,
                cell = cell,
                size = size,
            )
        }
    }
}

/**
 * Tracks [placed] and hands back the pulse for the square that most recently
 * joined it.
 *
 * A screen cannot forget to animate a placement, because it never asks for the
 * animation — it hands over the set of placed dogs it was already rendering and
 * gets back the answer for every square.
 */
@Composable
fun rememberPlacementPulse(placed: Set<Int>, size: Int): PlacementPulse {
    var previous by remember { mutableStateOf(placed) }
    // Counted apart from the pulse rather than read back off it. A pulse that
    // resolved to `None` — a restore, an undo — would otherwise take the nonce
    // back to zero, and the next placement would reuse a number a cell had
    // already animated on and stay silent.
    var counter by remember { mutableIntStateOf(0) }
    var pulse by remember { mutableStateOf(PlacementPulse.None) }

    LaunchedEffect(placed) {
        counter += 1
        pulse = PlacementPulse.between(previous, placed, size, counter)
        previous = placed
    }

    return pulse
}
