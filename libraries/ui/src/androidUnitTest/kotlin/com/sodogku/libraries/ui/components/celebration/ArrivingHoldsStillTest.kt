package com.sodogku.libraries.ui.components.celebration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The beat two celebrations are now built from arrives, and stops arriving when
 * the tool or the player watching it has asked it not to.
 *
 * [Arriving] was private to the win celebration until SD-124 gave the streak
 * ceremony the same staggered entrance. Shared, it is worth a guard of its own:
 * a beat that has not been reached yet is drawn at alpha zero, so a preview or a
 * screenshot that captures the composition before the sequence finishes captures
 * a page with pieces *missing*, not one mid-flight. That is the landmine
 * `AGENTS.md` lists, and holding it inside the component is what stops each new
 * call site having to remember it.
 *
 * ### What "holds still" is measured as
 *
 * Snapshot writes while the clock runs, the same measure `PawRatingHoldsStillTest`
 * uses and for the same reason: Robolectric runs no draw pass, so counting draws
 * reads zero for a moving beat and a still one alike.
 *
 * ### What this deliberately does not cover
 *
 * That a later beat lands after an earlier one. [beatDelayMillis] is arithmetic
 * over an `Int` and `WinBeatsTest` in `:features:game:impl` already pins it,
 * which is the cheaper layer. How far a beat overshoots is a claim about pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArrivingHoldsStillTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The guard against the guard, and it has to come first: both assertions
     * below are "nothing happened", which is also what a beat that had lost its
     * entrance entirely would report.
     */
    @Test
    fun aBeatIsStillArrivingWhenNothingIsWatching() {
        val changes = changesWhileTheBeatArrives(inspecting = false, reduceAnimations = false)

        assertTrue(
            changes > MinMovingChanges,
            "a beat produced $changes state writes, so either the entrance is not running " +
                "or this is measuring the wrong thing, and both stillness assertions here " +
                "pass for free",
        )
    }

    @Test
    fun aBeatHoldsStillUnderInspection() {
        assertEquals(
            0,
            // Animations explicitly allowed, so this is inspection mode on its
            // own: a preview does not get to depend on the player's setting
            // agreeing with it.
            changesWhileTheBeatArrives(inspecting = true, reduceAnimations = false),
            "a celebration's beats were still arriving in a preview, so a capture taken " +
                "before the last one lands is a page with pieces missing",
        )
    }

    @Test
    fun aBeatHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            changesWhileTheBeatArrives(inspecting = false, reduceAnimations = true),
            "the reduce-animations setting no longer reaches the staggered entrance shared " +
                "by the win celebration and the streak ceremony",
        )
    }

    /**
     * How many pieces of state the composition writes once the clock starts
     * moving, which is the window the whole entrance happens in.
     *
     * The count begins after the first idle rather than before it: landing on a
     * resting value is itself a state write, and a still beat makes one before a
     * single frame is drawn.
     */
    private fun changesWhileTheBeatArrives(inspecting: Boolean, reduceAnimations: Boolean): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides inspecting,
                LocalReduceAnimations provides reduceAnimations,
            ) {
                Arriving(order = LastBeat) {
                    Box(Modifier.size(BeatSize))
                }
            }
        }
        compose.waitForIdle()

        var changes = 0
        val handle = Snapshot.registerApplyObserver { changed, _ -> changes += changed.size }
        return try {
            compose.mainClock.advanceTimeBy(WatchMillis)
            compose.waitForIdle()
            changes
        } finally {
            handle.dispose()
        }
    }

    private companion object {
        /** Late in a script, so the wait as well as the spring is inside the window. */
        const val LastBeat = 4

        val BeatSize = 24.dp

        /** Past the last beat's delay and its spring, with room to spare. */
        const val WatchMillis = 3_000L

        /**
         * A floor, not a target. A spring at 60fps writes state dozens of times;
         * anything near zero is it not running.
         */
        const val MinMovingChanges = 20
    }
}
