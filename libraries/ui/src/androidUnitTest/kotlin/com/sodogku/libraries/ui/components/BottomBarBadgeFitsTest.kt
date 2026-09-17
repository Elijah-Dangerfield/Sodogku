package com.sodogku.libraries.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import com.sodogku.system.AppThemeProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nothing shaves the count on a tab, however big the player's text is.
 *
 * SD-128 moved the badge off a 6sp caption, and the badge is the site where
 * that could not be judged by eye. Material hangs a badge *above* its anchor,
 * at `-badgeHeight + 14dp`. The badge grows with the system font size and the
 * bar does not, so past some text size the badge reaches over the bar's top
 * edge — and `AppBottomBar` was drawn on a `Surface` that clips, which turned
 * "reaches over" into "is cut off". It was already happening at 6sp: a selected
 * tab is magnified 1.2x about its own centre, which throws the badge higher
 * still, and its count left the bar from around a 1.5x text setting on.
 *
 * The bar has no corners to protect, so it stopped clipping rather than
 * reserving headroom it would have had to charge every player for.
 *
 * ### What is measured
 *
 * The count's clipped bounds against its unclipped ones. Compose computes
 * `boundsInRoot` through every ancestor's clip and `unclippedBoundsInRoot`
 * without them, so the two disagreeing at the top edge *is* the bar taking a
 * bite out of the badge — and it is the only way to see a clip from here, since
 * Robolectric runs no draw pass.
 *
 * [theCountReallyDoesReachOverTheBar] is the guard, and this file is worth
 * nothing without it: "nothing was clipped" is also what a badge that never
 * came near an edge would report, which is exactly what this looked like before
 * anybody measured it.
 *
 * `@GraphicsMode(NATIVE)` is load-bearing. Under Robolectric's legacy graphics
 * every string measures to the same fixed box whatever size it is set in — an
 * earlier draft of this file had the badge 35dp tall at 8sp and at 32sp alike,
 * and passed every font scale it was given.
 *
 * ### What is deliberately not covered
 *
 * Where the badge sits horizontally, and what it looks like. Material's own end
 * offset has not changed, and a digit is narrower than it is tall.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomBarBadgeFitsTest {

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
    fun theCountIsWholeAtTheDefaultFontSize() {
        showBar(NoScaling)

        assertNothingShavesTheCount(NoScaling)
    }

    /**
     * 2.0 is Android's largest system font size, and it is above the 1.8 that
     * Compose Multiplatform maps iOS's largest accessibility text size to, so
     * the one number covers both platforms.
     */
    @Test
    fun theCountIsWholeAtTheLargestSystemFontSize() {
        showBar(LargestSystemFontScale)

        assertNothingShavesTheCount(LargestSystemFontScale)
    }

    /**
     * Four times the default is past anything a player can ask for. It is here
     * because the two sizes above are ones a reserved gap could plausibly be
     * tuned to survive, and this one no gap survives.
     */
    @Test
    fun theCountIsWholeEvenAtAnAbsurdFontSize() {
        showBar(AbsurdFontScale)

        assertNothingShavesTheCount(AbsurdFontScale)
    }

    /**
     * The guard against the guard. Every assertion above is "nothing was cut
     * off", which is also what a badge sitting comfortably inside the bar would
     * report, and what a bar that had stopped drawing a badge at all would
     * report. At the largest text size a player can choose the count genuinely
     * does hang over the top edge, so there is something for a clip to take.
     */
    @Test
    fun theCountReallyDoesReachOverTheBar() {
        showBar(LargestSystemFontScale)

        val bar = compose.onNodeWithTag(BarTag).getUnclippedBoundsInRoot()
        val count = wholeCount()

        assertTrue(
            count.top < bar.top,
            "at the largest system font the count sits ${(count.top - bar.top).value}dp inside " +
                "the bar, so it never reaches the edge and every 'nothing was clipped' in this " +
                "file passes for free",
        )
    }

    /**
     * And the badge is not a fixed box, which is the question SD-128 was really
     * asked. A count drawn at one height whatever the player's text setting says
     * is a count that ignores the setting.
     */
    @Test
    fun theCountGrowsWithTheSystemFontSize() {
        showBar(NoScaling)
        val small = wholeCount().height

        fontScale = LargestSystemFontScale
        compose.waitForIdle()
        val large = wholeCount().height

        assertTrue(
            large > small * GrewBy,
            "the count measured ${small.value}dp at the default text size and ${large.value}dp " +
                "at twice it, so the badge is not scaling with the system font setting",
        )
    }

    private fun assertNothingShavesTheCount(scale: Float) {
        val whole = wholeCount()
        val drawn = compose.onNodeWithText(Count.toString(), useUnmergedTree = true)
            .getBoundsInRoot()

        assertEquals(
            whole.top.value,
            drawn.top.value,
            "at font scale $scale the count is laid out with its top at ${whole.top.value}dp " +
                "but only drawn from ${drawn.top.value}dp down, so the bar is cutting the top " +
                "off its own badge",
        )
    }

    private fun wholeCount(): DpRect =
        compose.onNodeWithText(Count.toString(), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

    private var fontScale by mutableFloatStateOf(NoScaling)

    /**
     * The badged tab is the selected one on purpose: that is the tab the bar
     * magnifies, and the magnified one is where the badge reaches furthest.
     */
    private fun showBar(scale: Float) {
        fontScale = scale
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                AppThemeProvider {
                    AppBottomBar(
                        modifier = Modifier.testTag(BarTag),
                        items = listOf(
                            BottomBarItem.Home(isSelected = true, badgeAmount = Count),
                            BottomBarItem.Activity(isSelected = false),
                            BottomBarItem.Profile(isSelected = false),
                        ),
                        onItemClick = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val NoScaling = 1f
        const val LargestSystemFontScale = 2f
        const val AbsurdFontScale = 4f

        /** One digit, which is the badge at its narrowest. */
        const val Count = 9

        /**
         * Doubling the text setting should not quite double a line box, which
         * carries rounding of its own. Anything under this is a badge that is
         * not listening.
         */
        const val GrewBy = 1.5f

        const val BarTag = "bottom-bar"
    }
}
