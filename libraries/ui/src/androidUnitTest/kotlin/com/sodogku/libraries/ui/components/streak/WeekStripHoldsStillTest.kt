package com.sodogku.libraries.ui.components.streak

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
 * The day that just landed on the week strip pops in, and stops popping when
 * the tool or the player watching it has asked it not to.
 *
 * A pop is the worst kind of entrance to leave running: the cell starts at
 * scale zero, so a preview or a screenshot taken before the spring settles is
 * a strip with a day *missing*, on the one screen whose whole point is that
 * day. That is the landmine `AGENTS.md` lists, and this holds the guard inside
 * the cell rather than at each screen that draws a strip.
 *
 * ### What "holds still" is measured as
 *
 * Snapshot writes while the clock runs, the same measure `PawRatingHoldsStillTest`
 * uses and for the same reason: Robolectric runs no draw pass, so counting
 * draws reads zero for a popping cell and a still one alike.
 *
 * ### What this deliberately does not cover
 *
 * When the day pops. The handoff has it land after the number has finished
 * counting, and that is the screen's script, not the strip's: the strip takes
 * an index and the screen decides when to hand it one. What a tick or a cross
 * looks like is a claim about pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeekStripHoldsStillTest {

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
     * The guard against the guard, and it has to come first: every assertion
     * below is "nothing happened", which is also what a cell that had lost its
     * pop entirely would report.
     */
    @Test
    fun aLandedDayIsStillPoppingWhenNothingIsWatching() {
        val changes = changesWhileTheDayLands(inspecting = false, reduceAnimations = false)

        assertTrue(
            changes > MinMovingChanges,
            "a landing day produced $changes state writes, so either the pop is not running " +
                "or this is measuring the wrong thing, and every stillness assertion in this " +
                "file passes for free",
        )
    }

    @Test
    fun aLandedDayHoldsStillUnderInspection() {
        assertEquals(
            0,
            // Animations explicitly allowed, so this is inspection mode on its
            // own: a preview does not get to depend on the player's setting
            // agreeing with it.
            changesWhileTheDayLands(inspecting = true, reduceAnimations = false),
            "the day the streak screen celebrates was still popping in a preview, so a " +
                "capture taken before it lands is a strip with that day missing",
        )
    }

    @Test
    fun aLandedDayHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            changesWhileTheDayLands(inspecting = false, reduceAnimations = true),
            "the reduce-animations setting no longer reaches the week strip",
        )
    }

    @Test
    fun aStripWithNoLandingDayNeverMovesInTheFirstPlace() {
        assertEquals(
            0,
            changesWhileTheDayLands(inspecting = false, reduceAnimations = false, justLanded = null),
            "a strip that was handed no day to celebrate found something to animate anyway",
        )
    }

    /**
     * How many pieces of state the composition writes once the clock starts
     * moving. The count begins after the first idle rather than before it:
     * landing on a resting scale is itself a state write, and a still cell
     * makes one before a single frame is drawn.
     */
    private fun changesWhileTheDayLands(
        inspecting: Boolean,
        reduceAnimations: Boolean,
        justLanded: Int? = LandingDay,
    ): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides inspecting,
                LocalReduceAnimations provides reduceAnimations,
            ) {
                AppThemeProvider {
                    WeekStrip(days = previewWeek(), justLanded = justLanded)
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
        /** A day the preview week draws as done, so there is a tick to pop. */
        const val LandingDay = 3

        /** Past both springs of the pop, with room to spare. */
        const val WatchMillis = 2_000L

        /**
         * A floor, not a target. Two springs at 60fps write state dozens of
         * times; anything near zero is it not running.
         */
        const val MinMovingChanges = 20
    }
}
