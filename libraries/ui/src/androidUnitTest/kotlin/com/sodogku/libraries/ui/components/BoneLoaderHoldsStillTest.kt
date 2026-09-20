package com.sodogku.libraries.ui.components

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
 * The loading bone sweeps forever, and stops when the tool or the player
 * watching it cannot wait.
 *
 * It is on screen for the whole of launch and it loops, which is exactly the
 * landmine AGENTS.md lists: a preview or a screenshot waits for an idle
 * composition, and a sweep that restarts as it lands is never idle. The guard
 * lives inside [BoneLoader], and this holds it there.
 *
 * ### What "holds still" is measured as
 *
 * Snapshot writes while the clock runs, the same measure `DogHoldsStillTest`
 * uses and for the same reason: Robolectric runs no draw pass, so the fill's
 * clip is invisible here, but the `Animatable` driving it writes state on
 * every frame and a snapped one writes nothing once its effect has settled.
 *
 * ### Not here
 *
 * That the bone has no outline, and what colour the track is. Those are pixels,
 * and the nearest thing to a check is the design-system catalog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BoneLoaderHoldsStillTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The loader reads its colours from the theme, and the theme loads its
     * faces through Compose Multiplatform's resource reader, which takes its
     * `Context` from a `ContentProvider` a unit test never starts. Same
     * reflection, and the same reason, as `DogHoldsStillTest`.
     */
    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    /** The guard against the guard: a loader that never started would also report nothing. */
    @Test
    fun theBoneSweepsWhenNothingIsWatching() {
        val changes = changesOverThreeSeconds(inspecting = false, reduceAnimations = false)

        assertTrue(
            changes > MinMovingChanges,
            "the loading bone wrote state $changes times in three seconds, so either the sweep " +
                "is not running or this is measuring the wrong thing",
        )
    }

    @Test
    fun theBoneHoldsStillUnderInspection() {
        assertEquals(
            0,
            changesOverThreeSeconds(inspecting = true, reduceAnimations = false),
            "the loading bone kept sweeping in a preview, which a screenshot test waits on forever",
        )
    }

    @Test
    fun theBoneHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            changesOverThreeSeconds(inspecting = false, reduceAnimations = true),
            "the reduce-animations setting no longer reaches the loading bone",
        )
    }

    private fun changesOverThreeSeconds(inspecting: Boolean, reduceAnimations: Boolean): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides inspecting,
                LocalReduceAnimations provides reduceAnimations,
            ) {
                AppThemeProvider {
                    BoneLoader()
                }
            }
        }
        // Let the effect settle: a still bone snaps once, on its first frame,
        // and that one write is not the sweep.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()

        var changes = 0
        val handle = Snapshot.registerApplyObserver { changed, _ -> changes += changed.size }
        return try {
            compose.mainClock.advanceTimeBy(ThreeSecondsMillis)
            compose.waitForIdle()
            changes
        } finally {
            handle.dispose()
        }
    }

    private companion object {
        const val ThreeSecondsMillis = 3_000L
        const val MinMovingChanges = 10
    }
}
