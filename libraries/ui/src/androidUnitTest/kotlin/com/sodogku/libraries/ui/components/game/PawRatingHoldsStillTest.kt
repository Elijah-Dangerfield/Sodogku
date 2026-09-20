package com.sodogku.libraries.ui.components.game

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
 * The paws a clear awards arrive one at a time, and stop arriving when the tool
 * or the player watching them has asked them not to.
 *
 * SD-123 gave the rating a stagger so a win celebration awards three paws rather
 * than revealing a rating. A stagger is a worse thing to leave running than the
 * pop it replaced: a paw that has not been reached yet is drawn at scale zero,
 * so a preview or a screenshot that captures the composition before the sequence
 * finishes captures a rating with paws *missing*, not one mid-bounce. That is
 * the landmine AGENTS.md lists, and this holds the guard against it inside the
 * component rather than at each call site.
 *
 * ### What "holds still" is measured as
 *
 * Snapshot writes while the clock runs, the same measure `DogHoldsStillTest`
 * uses and for the same reason: Robolectric runs no draw pass, so counting draws
 * reads zero for a moving rating and a still one alike. An `Animatable` driving a
 * scale writes state on every frame; a rating that has snapped to its landed
 * value writes nothing once its effect has settled.
 *
 * ### What this deliberately does not cover
 *
 * That the paws land in left-to-right order, or how big the overshoot is. Those
 * are claims about pixels and about time, and the nearest thing to a check is a
 * person looking at the design-system catalog.
 *
 * Which beats a win celebration schedules at all is a decision over plain values
 * and lives in `WinBeatsTest` in `:features:game:impl`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PawRatingHoldsStillTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The rating reads its gold from the theme (an earned paw is `accentBrand`
     * since the 2026-09 palette retune), and the theme loads its faces through
     * Compose Multiplatform's resource reader, which takes its `Context` from a
     * `ContentProvider` the manifest merger installs. A unit test starts no
     * providers. Same reflection, and the same reason, as `CountUpNumberTest`.
     */
    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    /**
     * The guard against the guard, and it has to come first: every assertion
     * below is "nothing happened", which is also what a rating that had stopped
     * animating entirely would report.
     */
    @Test
    fun aStaggeredRatingIsStillArrivingWhenNothingIsWatching() {
        val changes = stateChangesWhileTheRatingArrives(
            inspecting = false,
            reduceAnimations = false,
        )

        assertTrue(
            changes > MIN_MOVING_CHANGES,
            "three staggered paws produced $changes state writes, so either the stagger is not " +
                "running or this is measuring the wrong thing, and every stillness assertion " +
                "in this file passes for free",
        )
    }

    @Test
    fun aStaggeredRatingHoldsStillUnderInspection() {
        assertEquals(
            0,
            stateChangesWhileTheRatingArrives(
                // Animations explicitly allowed, so this is inspection mode on
                // its own: a preview and a screenshot test do not get to depend
                // on the player's setting agreeing with them.
                inspecting = true,
                reduceAnimations = false,
            ),
            "a win celebration's paws were still arriving in a preview, so a capture taken " +
                "before the last one lands is a rating with paws missing",
        )
    }

    @Test
    fun aStaggeredRatingHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            stateChangesWhileTheRatingArrives(
                inspecting = false,
                reduceAnimations = true,
            ),
            "the reduce-animations setting no longer reaches the paw rating, so the one " +
                "celebration in the app that cannot be skipped also cannot be turned down",
        )
    }

    @Test
    fun aRecalledRatingNeverMovesInTheFirstPlace() {
        // `animated = false` is the level list and the daily recap: the rating
        // is being remembered rather than awarded, and a thousand recycling rows
        // that each pop would make the list appear to twitch.
        assertEquals(
            0,
            stateChangesWhileTheRatingArrives(
                inspecting = false,
                reduceAnimations = false,
                animated = false,
            ),
            "a rating asked for no animation at all found some anyway",
        )
    }

    /**
     * How many pieces of state the composition writes once the clock starts
     * moving, which is the window the whole entrance happens in.
     *
     * The count begins after the first idle rather than before it. Landing on
     * its resting value is itself one write per paw — `Animatable.snapTo` is a
     * state write like any other — and a still rating makes three of those
     * before a single frame has been drawn. Counting them would put the floor
     * for "moving" and the ceiling for "still" on the same side of each other.
     */
    private fun stateChangesWhileTheRatingArrives(
        inspecting: Boolean,
        reduceAnimations: Boolean,
        animated: Boolean = true,
    ): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides inspecting,
                LocalReduceAnimations provides reduceAnimations,
            ) {
                AppThemeProvider {
                    PawRating(
                        paws = EVERY_PAW,
                        animated = animated,
                        staggerMillis = STAGGER_MILLIS,
                    )
                }
            }
        }
        compose.waitForIdle()

        var changes = 0
        val handle = Snapshot.registerApplyObserver { changed, _ -> changes += changed.size }
        return try {
            compose.mainClock.advanceTimeBy(WATCH_MILLIS)
            compose.waitForIdle()
            changes
        } finally {
            handle.dispose()
        }
    }

    private companion object {
        const val EVERY_PAW = 3

        /** The win celebration's own stagger, so this watches what ships. */
        const val STAGGER_MILLIS = 110

        /** Past the last paw's delay and its spring, with room to spare. */
        const val WATCH_MILLIS = 2_000L

        /**
         * A floor, not a target. Three paws each spring twice at 60fps, so a
         * rating that is genuinely arriving writes state dozens of times;
         * anything near zero is it not running.
         */
        const val MIN_MOVING_CHANGES = 20
    }
}
