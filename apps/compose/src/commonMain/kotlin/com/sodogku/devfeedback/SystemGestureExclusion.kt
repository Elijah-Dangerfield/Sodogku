package com.sodogku.devfeedback

import androidx.compose.ui.Modifier

/**
 * Asks the OS not to claim touches inside this element for its own edge
 * gestures.
 *
 * Needed because Android's gesture navigation owns both screen edges for system
 * back, and it owns them for *taps* too, not only drags: an `input tap` 41px
 * from the right edge backed the app out to the launcher rather than reaching
 * the handle underneath. Without this, the only affordance on that edge is one
 * the system eats first.
 *
 * A no-op on iOS, where the right edge is already free (the interactive pop
 * gesture lives on the left).
 */
expect fun Modifier.excludeFromSystemGestures(): Modifier
