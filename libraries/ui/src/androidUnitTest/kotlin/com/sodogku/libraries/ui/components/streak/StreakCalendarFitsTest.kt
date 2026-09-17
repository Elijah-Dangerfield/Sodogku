package com.sodogku.libraries.ui.components.streak

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppThemeProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * A date still fits its square when the player has asked for bigger text.
 *
 * SD-128 moved the grid off a 6sp caption, and the reason nobody had moved it
 * before is in the item: the square is sized by the screen and never by the type
 * in it, so nothing about the change is visible from the source. A cell is
 * `weight(1f).aspectRatio(1f)` inside a clip — it does not grow when the text
 * does, it shaves the text instead — and the date is the one thing on the streak
 * page that has to be readable.
 *
 * ### What is measured
 *
 * The height the date wants against the height the square gives it, in one
 * composition at one font scale. The square comes from a real `StreakCalendar`
 * laid out at the narrowest width this app supports. The date's own height comes
 * from a second copy of the same text, drawn over the grid with nothing capping
 * it.
 *
 * A second copy rather than the one in the cell, because the cell clears its own
 * semantics: the whole square is a single node reading "Monday 28 September" and
 * the text inside it is not in the tree to be found. That is also why
 * [StreakCalendarTypography] exists — the bond between the two halves is a
 * token, so a size changed in the grid is the size this test measures. Written
 * twice, the grid could grow while the test went on measuring the old number.
 *
 * `@GraphicsMode(NATIVE)` is load-bearing. Under Robolectric's legacy graphics
 * every string measures to the same fixed box whatever size it is set in, and
 * the first draft of this file reported a date as 35dp tall at 8sp and at 32sp
 * alike — so the overflow case below passed as a fit.
 *
 * ### What is deliberately not covered
 *
 * Width: a date is at most two digits and the square is wider than the line is
 * tall, so height is the binding constraint. The weekday headings: they sit in a
 * `Row` with no fixed height, and a row that grows has nothing to prove.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreakCalendarFitsTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The theme loads its faces through Compose Multiplatform's resource reader,
     * which takes its `Context` from a `ContentProvider` the manifest merger
     * installs — and a unit test starts no providers. Same reflection, and the
     * same reason, as `CountUpNumberTest`.
     */
    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun aDateFitsItsSquareAtTheDefaultFontSize() {
        showGrid(NoScaling)

        assertTrue(dateHeight() < squareHeight(), fitMessage(NoScaling))
    }

    /**
     * 2.0 is Android's largest system font size, and it is above the 1.8 that
     * Compose Multiplatform maps iOS's largest accessibility text size to, so
     * the one number covers both platforms.
     */
    @Test
    fun aDateFitsItsSquareAtTheLargestSystemFontSize() {
        showGrid(LargestSystemFontScale)

        assertTrue(dateHeight() < squareHeight(), fitMessage(LargestSystemFontScale))
    }

    /**
     * The guard against the guard, and it has to exist: both assertions above
     * are "this number came back smaller than that one", which is also what a
     * pair of measurements that had stopped following the font scale would
     * report — and that is exactly what this file did on its first run. At four
     * times the system default a date genuinely does not fit.
     */
    @Test
    fun aDateAtAnAbsurdFontSizeOverflowsItsSquareAndTheMeasurementSaysSo() {
        showGrid(AbsurdFontScale)

        assertTrue(
            dateHeight() > squareHeight(),
            "a date set at four times the system default measured ${dateHeight().value}dp " +
                "against a ${squareHeight().value}dp square and was called a fit, so nothing " +
                "here is reading the composition it claims to",
        )
    }

    private fun fitMessage(scale: Float) =
        "at font scale $scale a date needs ${dateHeight().value}dp and its square gives it " +
            "${squareHeight().value}dp, so on the narrowest phone this app supports the streak " +
            "grid is drawing dates with their tops and bottoms shaved off"

    /** One square in the real grid. */
    private fun squareHeight(): Dp = compose
        .onNodeWithContentDescription(FirstDayDescription)
        .getUnclippedBoundsInRoot().height

    /** The same date with nothing capping it. */
    private fun dateHeight(): Dp = compose
        .onNodeWithTag(UncappedDateTag)
        .getUnclippedBoundsInRoot().height

    private var fontScale by mutableFloatStateOf(NoScaling)

    private fun showGrid(scale: Float) {
        fontScale = scale
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
                // The one square that can animate is driven by an `Animatable`,
                // and a measurement taken mid-fill is a measurement of a frame.
                // The grid holds still under inspection by design.
                LocalInspectionMode provides true,
            ) {
                AppThemeProvider {
                    // A `Box` and not a `Column`: the probe below is measured
                    // beside the grid rather than under it, so a grid that grows
                    // can never be what starves the probe of room.
                    Box {
                        StreakCalendar(
                            days = fiveWeeks(),
                            weekdayLabels = WeekdayLabels,
                            modifier = Modifier.width(NarrowestContentWidth),
                        )
                        Text(
                            text = WidestDateLabel,
                            typography = StreakCalendarTypography,
                            modifier = Modifier.testTag(UncappedDateTag),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun fiveWeeks(): List<StreakCell> = List(DaysShown) { index ->
        StreakCell(
            label = if (index == 0) WidestDateLabel else ((index % 28) + 1).toString(),
            description = if (index == 0) FirstDayDescription else "Day $index",
            stateLabel = "Played",
            state = StreakCellState.Done,
            isToday = false,
        )
    }

    private companion object {
        /**
         * 360dp is the narrowest Android width this app assumes — the figure the
         * 2026-09-08 touch-target entry measures against — and the screen takes
         * 12dp off each side. The iOS floor is wider, 375dp on an iPhone SE.
         */
        val NarrowestContentWidth = 336.dp

        const val NoScaling = 1f
        const val LargestSystemFontScale = 2f
        const val AbsurdFontScale = 4f

        const val DaysShown = 35

        const val WidestDateLabel = "28"
        const val FirstDayDescription = "Monday 28 September"
        const val UncappedDateTag = "uncapped-date"

        val WeekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    }
}
