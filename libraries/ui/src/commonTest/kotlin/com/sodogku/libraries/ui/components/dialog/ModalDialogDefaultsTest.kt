package com.sodogku.libraries.ui.components.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
