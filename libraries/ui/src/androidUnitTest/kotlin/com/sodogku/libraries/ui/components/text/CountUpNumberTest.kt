package com.sodogku.libraries.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.sodogku.system.AppThemeProvider
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The biggest number on a celebration flips to the run that was just earned, and
 * holds still everywhere it should.
 *
 * SD-124's ask was "a smooth animation of a streak flipping from 1 to 2 with a
 * bouncy celebration", and all three parts of that are claims about a
 * composition over time: which digits are on screen when, whether anything is
 * still moving, and whether the movement stops when a tool or a player asks it
 * to. Nothing below this tier can see any of them — a pure function cannot say
 * what a `LaunchedEffect` did, and the thump is an `Animatable` read inside
 * `graphicsLayer`, which is exactly where no test can read it.
 *
 * ### The three shapes, and why the middle one exists
 *
 * `countUpFrom = null` is a status number and never moves. `countUpFrom = 1,
 * value = 2` is the flip. `countUpFrom = value` is the run that started over:
 * SD-121 made a ceremony fire on a streak of one, and there is no previous run
 * to climb from, so the digits stay put and only the thump lands. That middle
 * case is one line of production code and it would be invisible in review, since
 * the loop it disables is a `for` over an empty range.
 *
 * ### What "holds still" is measured as
 *
 * Snapshot writes while the clock runs, the same measure `PawRatingHoldsStillTest`
 * and `DogHoldsStillTest` use, and for the same reason: Robolectric runs no draw
 * pass, so counting draws reads zero for a moving number and a still one alike.
 *
 * ### What this deliberately does not cover
 *
 * How far the number overshoots, or what it looks like doing it. Those are
 * claims about pixels; the nearest thing to a check is a person looking at the
 * streak page. Which number a given ceremony is asked to count from is a
 * decision over plain values and lives in `StreakViewModelTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountUpNumberTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The theme loads its faces through Compose Multiplatform's resource reader,
     * which takes its `Context` from a `ContentProvider` the manifest merger
     * installs — and a unit test starts no providers. Same reflection, and the
     * same reason, as `DogHoldsStillTest`.
     */
    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun aRunThatGrewWaitsOnItsBeatAndThenFlipsToTheNewNumber() {
        compose.mainClock.autoAdvance = false
        setContent(value = NewRun, countUpFrom = OldRun, startDelayMillis = StartDelayMillis)
        compose.waitForIdle()

        // Part way into the wait, and not before the clock has moved at all. An
        // assertion taken at the first idle reads the old number whether the
        // wait exists or not, because nothing has run yet — which is a test that
        // cannot fail, and this one was exactly that until a mutation said so.
        compose.mainClock.advanceTimeBy(PartWayThroughTheWaitMillis)
        compose.waitForIdle()

        assertTrue(
            reads(OldRun),
            "the number climbed before its beat had arrived, so the flip happened behind an " +
                "alpha of zero and there was nothing to watch",
        )

        compose.mainClock.advanceTimeBy(WatchMillis)
        compose.waitForIdle()

        assertTrue(reads(NewRun), "the number never reached the run it was celebrating")
        assertFalse(reads(OldRun), "and it did not leave the old one on screen beside it")
    }

    @Test
    fun aRunThatStartedOverKeepsItsNumberAndStillLands() {
        compose.mainClock.autoAdvance = false
        setContent(value = RestartedRun, countUpFrom = RestartedRun)
        compose.waitForIdle()

        val changes = changesWhileTheClockRuns()

        assertTrue(reads(RestartedRun), "the restart lost the number it was celebrating")
        assertFalse(
            reads(Nothing),
            "a run that started over counted up from zero, which is the page opening by " +
                "saying in display type that the player had nothing",
        )
        assertTrue(
            changes > MinMovingChanges,
            "a restart produced $changes state writes, so the thump does not run and a " +
                "ceremony for a run of one is a still page",
        )
    }

    @Test
    fun aCelebrationMovesWhenNothingIsWatching() {
        // The guard against the guard, and it has to come first: every assertion
        // below is "nothing happened", which is also what a number that had lost
        // its animation entirely would report.
        val changes = changesWhileCounting(inspecting = false, reduceAnimations = false)

        assertTrue(
            changes > MinMovingChanges,
            "a flip produced $changes state writes, so either it is not running or this is " +
                "measuring the wrong thing, and every stillness assertion here passes free",
        )
    }

    @Test
    fun aStatusNumberNeverMovesInTheFirstPlace() {
        assertEquals(
            0,
            changesWhileCounting(inspecting = false, reduceAnimations = false, countUpFrom = null),
            "a page the player went looking for performed at them",
        )
    }

    @Test
    fun aCelebrationHoldsStillUnderInspection() {
        assertEquals(
            0,
            // Animations explicitly allowed, so this is inspection mode on its
            // own: a preview and a screenshot test do not get to depend on the
            // player's setting agreeing with them.
            changesWhileCounting(inspecting = true, reduceAnimations = false),
            "a celebration's number was still climbing in a preview, so a capture taken " +
                "before it lands is a streak page showing the wrong streak",
        )
    }

    @Test
    fun aCelebrationHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            changesWhileCounting(inspecting = false, reduceAnimations = true),
            "the reduce-animations setting no longer reaches the number the whole ceremony " +
                "is about",
        )
    }

    private fun changesWhileCounting(
        inspecting: Boolean,
        reduceAnimations: Boolean,
        countUpFrom: Int? = OldRun,
    ): Int {
        compose.mainClock.autoAdvance = false
        setContent(
            value = NewRun,
            countUpFrom = countUpFrom,
            inspecting = inspecting,
            reduceAnimations = reduceAnimations,
        )
        compose.waitForIdle()
        return changesWhileTheClockRuns()
    }

    /**
     * How many pieces of state the composition writes once the clock starts
     * moving.
     *
     * The count begins after the first idle rather than before it. Landing on a
     * resting value is itself a state write — `Animatable.snapTo` is one like any
     * other — and a still number makes those before a single frame is drawn.
     * Counting them would put the floor for "moving" and the ceiling for "still"
     * on the same side of each other.
     */
    private fun changesWhileTheClockRuns(): Int {
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

    private fun setContent(
        value: Int,
        countUpFrom: Int?,
        startDelayMillis: Int = 0,
        inspecting: Boolean = false,
        reduceAnimations: Boolean = false,
    ) {
        compose.setContent {
            Themed(inspecting = inspecting, reduceAnimations = reduceAnimations) {
                CountUpNumber(
                    value = value,
                    countUpFrom = countUpFrom,
                    startDelayMillis = startDelayMillis,
                )
            }
        }
    }

    /**
     * Whether the number is drawing [value]. Counted rather than matched
     * exactly, because [OutlinedText] draws the same string twice — once
     * stroked, once filled.
     */
    private fun reads(value: Int): Boolean =
        compose.onAllNodesWithText(value.toString()).fetchSemanticsNodes().isNotEmpty()

    @Composable
    private fun Themed(
        inspecting: Boolean,
        reduceAnimations: Boolean,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalInspectionMode provides inspecting,
            LocalReduceAnimations provides reduceAnimations,
        ) {
            AppThemeProvider(content)
        }
    }

    private companion object {
        const val Nothing = 0
        const val RestartedRun = 1
        const val OldRun = 1
        const val NewRun = 2

        /** The streak ceremony's own wait for its first beat, so this watches what ships. */
        const val StartDelayMillis = 480

        /**
         * Inside the wait, and well past the 90ms a digit takes. A number that
         * was not waiting has finished climbing by here.
         */
        const val PartWayThroughTheWaitMillis = 300L

        /** Past the wait, the climb and the spring, with room to spare. */
        const val WatchMillis = 3_000L

        /**
         * A floor, not a target. A spring at 60fps writes state dozens of times;
         * anything near zero is it not running.
         */
        const val MinMovingChanges = 20
    }
}
