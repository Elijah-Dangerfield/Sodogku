package com.sodogku.libraries.ui.system

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import com.sodogku.system.Dimension

/**
 * A card hung off a rectangle somewhere else on screen: a coach mark under a
 * spotlight, a speech bubble under a lit control.
 *
 * A [Layout] rather than an alignment and some padding, and the difference is
 * not tidiness. Where the card goes depends on how tall the card is, and in the
 * composition phase that is not known yet. Everything built that way has to
 * guess a height, place the card, then correct itself once the real height
 * arrives, which means a sentinel for "not measured yet", a hidden first frame,
 * and a measure-to-state-to-layout loop. A Layout measures first and places
 * second, in one pass, so none of that exists: there is no frame where the
 * height is unknown.
 *
 * Placement is [anchoredCardTop], which is a pure function and tested as one.
 *
 * **This does not animate.** Compose the caller with [animatePlacement] if the
 * card should travel when the anchor moves. The two are separate because
 * animating needs a coroutine and a layout pass cannot start one; fusing them is
 * what makes this kind of component ugly.
 */
@Composable
fun AnchoredCard(
    anchor: Rect,
    modifier: Modifier = Modifier,
    gap: Dp = DefaultGap,
    minTop: Dp = DefaultMinTop,
    minBottom: Dp = DefaultMinBottom,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        // No card is a legal state, not a crash. A toast that has finished
        // leaving composes nothing while its host is still on screen, and a
        // Layout with no children still has to measure.
        val measurable = measurables.firstOrNull()
            ?: return@Layout layout(width, height) {}

        // Loose constraints: the card is as big as it wants to be within the
        // scrim, not stretched to fill it.
        val card = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

        val top = anchoredCardTop(
            anchorTop = anchor.top,
            anchorBottom = anchor.bottom,
            cardHeight = card.height.toFloat(),
            availableHeight = height.toFloat(),
            gap = gap.toPx(),
            minTop = minTop.toPx(),
            minBottom = minBottom.toPx(),
        )

        layout(width, height) {
            card.place(x = (width - card.width) / 2, y = top.toInt())
        }
    }
}

/**
 * Where the top of the card goes, in the same pixel space as the anchor.
 *
 * Pure, and separated out so the rules can be tested without a screen. All four
 * of them came from something going wrong:
 *
 * - **Below the anchor by default**, so the card and the thing it describes read
 *   as one object.
 * - **Flipped above when there is no room below.** Hanging under a target near
 *   the bottom of the screen, the booster row say, pushes the card off the
 *   display, and a lesson nobody can read is worse than no lesson.
 * - **Never above [minTop].** A spotlight with nothing to hang off reports an
 *   anchor at the origin, and an unclamped card lands under the status bar.
 * - **Never past [minBottom].** The flipped case has its own edge to fall off.
 *
 * The clamp is applied last and deliberately: it is a correction to whichever
 * side was chosen, not an input to choosing. A card that is clamped is already
 * in a bad spot, and the useful behaviour there is "as close as allowed" rather
 * than "flip to the other side and be wrong differently".
 *
 * When the card is taller than the space it has, `coerceIn` would be handed a
 * reversed range, so the floor wins: better pinned to the top and cut off at the
 * bottom than the reverse, because the title and the buttons are at opposite
 * ends and the title is the part that has to survive.
 */
internal fun anchoredCardTop(
    anchorTop: Float,
    anchorBottom: Float,
    cardHeight: Float,
    availableHeight: Float,
    gap: Float,
    minTop: Float,
    minBottom: Float,
): Float {
    val fitsBelow = anchorBottom + gap + cardHeight <= availableHeight - minBottom
    val preferred = if (fitsBelow) anchorBottom + gap else anchorTop - gap - cardHeight

    val floor = minTop
    val ceiling = availableHeight - minBottom - cardHeight
    return if (ceiling < floor) floor else preferred.coerceIn(floor, ceiling)
}

/** Breathing room between the lit thing and the card describing it. */
private val DefaultGap: Dp = Dimension.D600

/** Clears the status bar and the header when there is no anchor to hang from. */
private val DefaultMinTop: Dp = Dimension.D1900

/** Keeps a flipped card clear of the gesture bar. */
private val DefaultMinBottom: Dp = Dimension.D1200
