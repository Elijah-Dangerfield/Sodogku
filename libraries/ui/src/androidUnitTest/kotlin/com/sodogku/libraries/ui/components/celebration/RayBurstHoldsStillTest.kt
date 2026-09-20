package com.sodogku.libraries.ui.components.celebration

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.system.AppThemeProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rays behind a cleared board turn, and stop turning when the tool or the
 * player watching them has asked them not to.
 *
 * [RayBurst] is the first thing in this library that moves for as long as its
 * page is up rather than for a beat: an infinite rotation. That is a worse thing
 * to leave running than any entrance, because a screenshot test waiting for an
 * idle composition never gets one, and it is the landmine `AGENTS.md` lists by
 * name. The entrance is the other half: a burst caught before it has scaled in
 * is a burst at scale zero, which is a picture of nothing.
 *
 * ### What "holds still" is measured as
 *
 * Snapshot writes while the clock runs, the same measure `ArrivingHoldsStillTest`
 * and `PawRatingHoldsStillTest` use and for the same reason: Robolectric runs no
 * draw pass, so counting draws reads zero for a turning burst and a still one
 * alike. An infinite transition writes state on every frame; a burst that has
 * snapped to its resting scale and skipped the transition writes nothing.
 *
 * ### What this deliberately does not cover
 *
 * How fast it turns, and what twelve bars look like. Those are claims about
 * pixels and about time, and the nearest thing to a check is a person looking
 * at the design-system catalog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RayBurstHoldsStillTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The theme loads its faces through Compose Multiplatform's resource reader,
     * which takes its `Context` from a `ContentProvider` the manifest merger
     * installs, and a unit test starts no providers. Same reflection, and the
     * same reason, as `CountUpNumberTest`.
     */
    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    /**
     * The guard against the guard, and it has to come first: both assertions
     * below are "nothing happened", which is also what a burst that had lost
     * its rotation entirely would report.
     */
    @Test
    fun aBurstIsTurningWhenNothingIsWatching() {
        val changes = changesWhileTheBurstTurns(inspecting = false, reduceAnimations = false)

        assertTrue(
            changes > MinMovingChanges,
            "a burst produced $changes state writes over ${WatchMillis}ms, so either it is not " +
                "turning or this is measuring the wrong thing, and both stillness assertions " +
                "here pass for free",
        )
    }

    @Test
    fun aBurstHoldsStillUnderInspection() {
        assertEquals(
            0,
            // Animations explicitly allowed, so this is inspection mode on its
            // own: a preview does not get to depend on the player's setting
            // agreeing with it.
            changesWhileTheBurstTurns(inspecting = true, reduceAnimations = false),
            "the rays were still turning in a preview, so a screenshot test of the cleared " +
                "board waits for an idle that never comes",
        )
    }

    @Test
    fun aBurstHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            changesWhileTheBurstTurns(inspecting = false, reduceAnimations = true),
            "the reduce-animations setting no longer reaches the rays, which are the one " +
                "thing on the cleared board that would otherwise move for as long as it is up",
        )
    }

    /**
     * How many pieces of state the composition writes once the clock starts
     * moving. The count begins after the first idle rather than before it:
     * landing on a resting scale is itself a state write, and a still burst
     * makes one before a single frame is drawn.
     */
    private fun changesWhileTheBurstTurns(inspecting: Boolean, reduceAnimations: Boolean): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides inspecting,
                LocalReduceAnimations provides reduceAnimations,
            ) {
                AppThemeProvider {
                    RayBurst()
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
        /** Well past the entrance spring, so the window is mostly the rotation. */
        const val WatchMillis = 2_000L

        /**
         * A floor, not a target. An infinite transition at 60fps writes state
         * every frame; anything near zero is it not running.
         */
        const val MinMovingChanges = 20
    }
}
