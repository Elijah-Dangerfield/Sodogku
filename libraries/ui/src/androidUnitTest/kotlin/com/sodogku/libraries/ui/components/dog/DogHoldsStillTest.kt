package com.sodogku.libraries.ui.components.dog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.system.LocalReduceAnimations
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
 * A dog that was asked to move stops when the tool watching it cannot wait.
 *
 * This is the claim SD-103 exists for. `AnimatedDog` looped forever under
 * [LocalInspectionMode], so a preview holding a board spun until Android Studio
 * gave up, while its two neighbours checked the flag and held a frame. Two right
 * out of three is what a rule every call site has to remember looks like, so the
 * rule moved inside [Dog] and this holds it there.
 *
 * ### What "holds still" is measured as
 *
 * Snapshot writes while the clock runs. A moving dog steps `frame` about twelve
 * times a second and [DogMotion.Alive] drives an `Animatable` on top of that; a
 * still one writes nothing at all once its effect has settled. So the assertion
 * is that advancing three seconds of virtual time produces zero state changes,
 * which is the same thing a preview renderer and a screenshot test are asking
 * when they wait for idle.
 *
 * Not the drawing. Robolectric never runs a draw pass, so a `drawWithContent`
 * counter reads zero for a moving dog and a still one alike — it looks like a
 * passing test and is measuring nothing. Snapshot writes are the layer below
 * that, and they are visible here.
 *
 * ### What this deliberately does not cover
 *
 * Which frame a still dog holds. That is `RestFrame` in `DogSprite.kt` and it is
 * a claim about pixels, which this tier cannot see either; the nearest thing to
 * a check is the design-system catalog page, read by a person.
 *
 * That no *new* dog escapes the component. That is a source-scanning question
 * and it lives in `DogsAreDrawnByTheDesignSystemTest` in `:apps:integration`,
 * because this module cannot see the features that call it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DogHoldsStillTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Compose Multiplatform's resource reader gets its `Context` from a
     * `ContentProvider` the manifest merger installs, and a unit test starts no
     * providers — so `imageResource` throws "Android context is not
     * initialized" before any of this gets as far as an assertion.
     *
     * Reflection because the provider is internal to the resources artifact.
     * The alternative is to stop the component loading its own sheets, which
     * would make the test a test of a stub.
     */
    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun aSettledDogMovesWhenNothingIsWatching() {
        // The guard against the guard, and it has to come first: every
        // assertion below is "nothing happened", which is also what a dog that
        // never started would report.
        val changes = stateChangesOverThreeSeconds(
            motion = DogMotion.Settled,
            inspecting = false,
            reduceAnimations = false,
        )
        assertTrue(
            changes > MIN_MOVING_CHANGES,
            "a placed dog stepped $changes times in three seconds, so either the loop is not " +
                "running or this is measuring the wrong thing, and every stillness assertion " +
                "in this file passes for free",
        )
    }

    @Test
    fun aSettledDogHoldsStillUnderInspection() {
        assertEquals(
            0,
            stateChangesOverThreeSeconds(
                motion = DogMotion.Settled,
                inspecting = true,
                // Animations explicitly allowed, so this is inspection mode on
                // its own: the preview and the composition test are not
                // negotiable and do not need the player's setting to agree.
                reduceAnimations = false,
            ),
            "a board dog kept stepping in a preview, which is the bug SD-103 was filed for",
        )
    }

    @Test
    fun aSettledDogHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            stateChangesOverThreeSeconds(
                motion = DogMotion.Settled,
                inspecting = false,
                reduceAnimations = true,
            ),
            "the reduce-animations setting no longer reaches the board dog",
        )
    }

    @Test
    fun anAliveDogMovesWhenNothingIsWatching() {
        val changes = stateChangesOverThreeSeconds(
            motion = DogMotion.Alive,
            inspecting = false,
            reduceAnimations = false,
        )
        assertTrue(
            changes > MIN_MOVING_CHANGES,
            "the hero dog changed $changes times in three seconds, so the two assertions " +
                "below are not testing anything",
        )
    }

    @Test
    fun anAliveDogHoldsStillUnderInspection() {
        assertEquals(
            0,
            stateChangesOverThreeSeconds(
                motion = DogMotion.Alive,
                inspecting = true,
                reduceAnimations = false,
            ),
            "the hero dog kept its clip schedule or its float running in a preview",
        )
    }

    @Test
    fun anAliveDogHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            stateChangesOverThreeSeconds(
                motion = DogMotion.Alive,
                inspecting = false,
                reduceAnimations = true,
            ),
            "the hero dog's float outlived the reduce-animations setting",
        )
    }

    @Test
    fun aPosedDogNeverMovesInTheFirstPlace() {
        assertEquals(
            0,
            stateChangesOverThreeSeconds(
                motion = DogMotion.None,
                inspecting = false,
                reduceAnimations = false,
            ),
            "a dog asked for no motion at all found some anyway",
        )
    }

    /**
     * How many pieces of state the composition writes over three seconds of
     * virtual time, after it has settled.
     *
     * The settle window matters: the sheet loads asynchronously and lands as a
     * state write of its own, which would otherwise count as movement.
     */
    private fun stateChangesOverThreeSeconds(
        motion: DogMotion,
        inspecting: Boolean,
        reduceAnimations: Boolean,
    ): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides inspecting,
                LocalReduceAnimations provides reduceAnimations,
            ) {
                Dog(size = DOG_SIZE.dp, motion = motion)
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
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
        const val DOG_SIZE = 120

        const val SETTLE_MILLIS = 1_000L
        const val WATCH_MILLIS = 3_000L

        /**
         * A floor, not a target. A settled dog steps once every 83ms, so three
         * seconds is about 36; anything near that is the loop running and
         * anything near zero is it not.
         */
        const val MIN_MOVING_CHANGES = 20
    }
}
