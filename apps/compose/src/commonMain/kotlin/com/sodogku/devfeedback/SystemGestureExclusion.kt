package com.sodogku.devfeedback

import androidx.compose.ui.Modifier

/**
 * Asks the OS not to claim touches inside this element for its own edge
 * gestures.
 *
 * Needed because Android's gesture navigation owns both screen edges for system
 * back, and it owns them for *taps* too, not only drags: an `input tap` 41px
 * from the right edge backed the app out to the launcher rather than reaching
 * the control underneath. The feedback button can be dragged flush against
 * either edge, so without this it becomes untappable exactly where a tester is
 * most likely to park it, to keep it off the board.
 *
 * A no-op on iOS, where the right edge is already free (the interactive pop
 * gesture lives on the left).
 */
expect fun Modifier.excludeFromSystemGestures(): Modifier
