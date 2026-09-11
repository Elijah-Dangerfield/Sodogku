package com.sodogku.libraries.ui.components.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The two dialog decisions that can be made outside a composition, so they are.
 *
 * Nothing here builds a composition. `animationSpecFor` returns a value, and a
 * value can be compared, which is what lets the reduced-motion promise be
 * checked at all. The trick is comparing the card's animation to the scrim's:
 * the scrim only ever fades, so "the card does exactly what the scrim does" is
 * the accessibility claim stated without reaching into Compose internals, and
 * it fails for a spec that kept a scale or a slide while shortening it.
 *
 * That assertion is paired on purpose, because on its own it passes for an
 * implementation that reduced every dialog in the app whether the setting was
 * on or not.
 *
 * The width fraction is the other one. The card is narrower than the page and
 * the strip either side is the tap target that dismisses it, so a full-width
 * card is not a cosmetic change: it silently removes a gesture, and no visual
 * review would flag it.
 *
 * ### Not here
 *
 * How the dialog is hosted and torn down is `:libraries:navigation`, where
 * `FloatingWindowHostTest` drives a real composition. Anything about how these
 * specs look in motion is not testable and is not attempted.
 */
class ModalDialogDefaultsTest {

    /**
     * Reduced motion has to strip the card's spring and rise, not just shorten
     * them. The scrim only ever fades, so "the card animates exactly like the
     * scrim" is the shape of that claim without reaching into Compose's
     * internals — and it fails for a spec that keeps the scale or the slide.
     */
    @Test
    fun reducedMotionLeavesTheCardDoingNothingTheScrimDoesNot() {
        val reduced = ModalDialogDefaults.animationSpecFor(reduceAnimations = true)

        assertEquals(reduced.scrimEnter, reduced.contentEnter)
        assertEquals(reduced.scrimExit, reduced.contentExit)
    }

    /**
     * The companion assertion, without which the one above passes for an
     * implementation that reduced every dialog in the app.
     */
    @Test
    fun fullMotionSpringsTheCardRatherThanFadingItLikeTheScrim() {
        val full = ModalDialogDefaults.animationSpecFor(reduceAnimations = false)

        assertNotEquals(full.scrimEnter, full.contentEnter)
    }

    @Test
    fun theSettingChangesTheSpec() {
        assertNotEquals(
            ModalDialogDefaults.animationSpecFor(reduceAnimations = true),
            ModalDialogDefaults.animationSpecFor(reduceAnimations = false),
        )
    }

    /**
     * The card is deliberately narrower than the page. The strip of scrim either
     * side is the tap target that dismisses it, so a full-width card is not a
     * cosmetic change — it removes the gesture.
     */
    @Test
    fun theCardLeavesScrimEitherSideToTap() {
        assertEquals(true, ModalDialogDefaults.WidthFraction < 1f)
    }
}
