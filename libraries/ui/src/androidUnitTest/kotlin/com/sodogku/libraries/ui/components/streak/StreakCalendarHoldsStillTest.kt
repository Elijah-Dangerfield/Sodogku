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
 * The square the streak page is celebrating fills in, and holds still when the
 * player has asked for fewer animations, whatever the caller passed.
 *
 * Until 2026-09-20 the cell only read inspection mode, and `StreakViewModel`
 * withheld `fillingIndex` on the setting's behalf. That worked for the one
 * caller and would have silently stopped working for the second, which is the
 * shape of every reduce-animations bug this app has had. The cell reads the
 * setting itself now, and this is what stops the view model's courtesy from
 * becoming the only thing honouring it.
 *
 * ### What "holds still" is measured as
 *
 * Snapshot writes while the clock runs, the same measure `PawRatingHoldsStillTest`
 * uses and for the same reason: Robolectric runs no draw pass, so counting
 * draws reads zero for a filling square and a still one alike.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreakCalendarHoldsStillTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    /** The guard against the guard: a grid that had lost its fill would also report nothing. */
    @Test
    fun aFillingSquareIsStillFillingWhenNothingIsWatching() {
        val changes = changesWhileTheSquareFills(inspecting = false, reduceAnimations = false)

        assertTrue(
            changes > MinMovingChanges,
            "a filling square produced $changes state writes, so either the fill is not " +
                "running or this is measuring the wrong thing",
        )
    }

    @Test
    fun aFillingSquareHoldsStillUnderInspection() {
        assertEquals(
            0,
            changesWhileTheSquareFills(inspecting = true, reduceAnimations = false),
            "the day the streak page celebrates was still filling in a preview",
        )
    }

    @Test
    fun aFillingSquareHoldsStillWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            changesWhileTheSquareFills(inspecting = false, reduceAnimations = true),
            "the reduce-animations setting does not reach the calendar cell on its own, so " +
                "a caller that forgets to withhold fillingIndex animates anyway",
        )
    }

    private fun changesWhileTheSquareFills(inspecting: Boolean, reduceAnimations: Boolean): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides inspecting,
                LocalReduceAnimations provides reduceAnimations,
            ) {
                AppThemeProvider {
                    StreakCalendar(
                        days = week(),
                        weekdayLabels = WeekdayLabels,
                        fillingIndex = FillingDay,
                    )
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

    private fun week(): List<StreakCell> = List(DaysShown) { index ->
        StreakCell(
            label = (index + 1).toString(),
            description = "Day ${index + 1}",
            stateLabel = "Played",
            state = StreakCellState.Done,
            isToday = index == FillingDay,
        )
    }

    private companion object {
        const val DaysShown = 7
        const val FillingDay = 3
        const val WatchMillis = 2_000L
        const val MinMovingChanges = 20
        val WeekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    }
}
