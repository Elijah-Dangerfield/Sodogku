package com.sodogku.libraries.ui.components.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two settings that can overrule a request to beat.
 *
 * `BoardControl` asks for attention on behalf of a stuck player, and the caller
 * decides whether the player is stuck. Neither of them knows about the accessi-
 * bility setting or about being rendered into a screenshot, and both of those
 * beat the caller.
 *
 * The reason this is a function and not three lines inside the composable is
 * that there is no Compose UI test harness in this repo, so a
 * `CompositionLocal` read is a branch no test can reach — and "the reduce-motion
 * setting is quietly ignored" is exactly the kind of regression that ships
 * because nobody who could see it had the setting on.
 */
class BoardControlBeatTest {

    @Test
    fun aControlNobodyAskedToBeatStaysStill() {
        assertFalse(beatsForAttention(attention = false, reduceAnimations = false, inspecting = false))
    }

    @Test
    fun aControlAskedToBeatOnAnOrdinaryScreenBeats() {
        assertTrue(beatsForAttention(attention = true, reduceAnimations = false, inspecting = false))
    }

    /**
     * The whole point of the setting. This animation repeats until the player
     * moves, which makes it the loudest thing in the app for somebody who asked
     * the app to stop moving.
     */
    @Test
    fun reduceAnimationsOutranksBeingAskedToBeat() {
        assertFalse(beatsForAttention(attention = true, reduceAnimations = true, inspecting = false))
    }

    /**
     * A loop that never ends is an idle state a screenshot test waits on until
     * it gives up.
     */
    @Test
    fun aPreviewHoldsStill() {
        assertFalse(beatsForAttention(attention = true, reduceAnimations = false, inspecting = true))
    }

    @Test
    fun eitherOneOnItsOwnIsEnoughToStopIt() {
        assertFalse(beatsForAttention(attention = true, reduceAnimations = true, inspecting = true))
    }
}
