package com.sodogku.libraries.ui.components.achievements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.achievements.AchievementGroup
import com.sodogku.system.AppTheme
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * No badge name in the grid is cut short, however big the player's text is.
 *
 * The 2026-09 handoff puts the tiles three across with the name allowed two
 * lines, and at the largest system text size a third of the narrowest phone
 * this app supports cannot set "Regular as Clockwork" in two. A tile that
 * ellipsized it would read "Regular as…" on the one screen that exists to name
 * badges. So [BadgeTileGrid] measures every name at the width three columns
 * would give it and drops to two when any needs a third line, and the claim
 * here is that whichever it chose, every name is whole.
 *
 * ### What is measured
 *
 * Each name's own `TextLayoutResult`, asked for through its semantics: a name
 * that needed more lines than it was allowed reports visual overflow, and a
 * name that fit does not. That is the text node's own verdict rather than a
 * second measurement that might disagree with it.
 *
 * [aTileForcedToAThirdOfThePhoneCutsTheLongestNameAndTheMeasurementSaysSo] is
 * the guard against the guard. Every other assertion is "nothing overflowed",
 * which is also what a layout result that had stopped reporting overflow would
 * say; a tile squeezed to the width the grid refused genuinely does come up
 * short.
 *
 * `@GraphicsMode(NATIVE)` is load-bearing. Under Robolectric's legacy graphics
 * every string measures to the same fixed box whatever size it is set in, and
 * the overflow case would pass as a fit.
 *
 * ### What is deliberately not covered
 *
 * That the grid is exactly two across at the largest size. It is, today, with
 * this catalog; it is an outcome of the longest name and the font, not a
 * contract, and [theTilesSitThreeAcrossAtTheDefaultFontSize] is only here to
 * prove the default case is measuring a third of the row rather than a tile
 * that had stacked for free.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric's default screen is 320 by 470dp, narrower than the phone this
// measures against and short enough that a fourth row of tiles at the largest
// text size is laid out with no height at all, which reads as an overflow.
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BadgeTileGridFitsTest {

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

    @Test
    fun everyNameIsWholeAtTheDefaultFontSize() {
        showGrid(NoScaling)

        assertEveryNameIsWhole(NoScaling)
    }

    /**
     * 2.0 is Android's largest system font size, and it is above the 1.8 that
     * Compose Multiplatform maps iOS's largest accessibility text size to, so
     * the one number covers both platforms.
     */
    @Test
    fun everyNameIsWholeAtTheLargestSystemFontSize() {
        showGrid(LargestSystemFontScale)

        assertEveryNameIsWhole(LargestSystemFontScale)
    }

    @Test
    fun theTilesSitThreeAcrossAtTheDefaultFontSize() {
        showGrid(NoScaling)

        val tops = Names.take(PreferredColumns).map { name ->
            compose.onNodeWithContentDescription(spoken(name)).getUnclippedBoundsInRoot().top
        }

        assertEquals(
            tops.first(),
            tops.last(),
            "at the default text size the third tile is under the first rather than beside it, so " +
                "nothing here is squeezing a name into a third of the row",
        )
    }

    @Test
    fun aTileForcedToAThirdOfThePhoneCutsTheLongestNameAndTheMeasurementSaysSo() {
        showOneTile(name = LongestName, width = AThird, scale = LargestSystemFontScale)

        assertTrue(
            layoutOf(LongestName).hasVisualOverflow,
            "\"$LongestName\" at font scale $LargestSystemFontScale fit a ${AThird.value}dp tile " +
                "in $MaxNameLines lines, so either the grid never needed to drop a column or " +
                "nothing here is reading the layout it claims to",
        )
    }

    private fun assertEveryNameIsWhole(scale: Float) {
        Names.forEach { name ->
            val laid = layoutOf(name)
            assertFalse(
                laid.hasVisualOverflow,
                "at font scale $scale \"$name\" was cut to ${laid.lineCount} lines in the grid",
            )
        }
    }

    /** The text node's own layout, which is the only place the overflow verdict lives. */
    private fun layoutOf(name: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().single()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun showGrid(scale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = scale)) {
                AppThemeProvider {
                    // Scrolling, as the page is, so the grid is measured with
                    // the height it needs rather than the height of the screen.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BadgeTileGrid(
                            tiles = Names.map { tile(it) },
                            earnedLabel = EarnedLabel,
                            onSelect = {},
                            modifier = Modifier.width(NarrowestContentWidth),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showOneTile(name: String, width: Dp, scale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = scale)) {
                AppThemeProvider {
                    Box(Modifier.width(width)) {
                        BadgeTile(spec = tile(name), earnedLabel = EarnedLabel, onClick = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun tile(name: String) = BadgeTileSpec(
        face = Face,
        name = name,
        state = BadgeTileState.Locked(BadgeProgress(0.4f, Fraction), upNext = false),
        set = AppTheme.colors.badgeSets[AchievementGroup.Daily],
        spoken = spoken(name),
    )

    private fun spoken(name: String) = "$name. Locked, 4 of 10."

    private companion object {
        /**
         * 360dp is the narrowest Android width this app assumes, and the page
         * takes 16dp off each side. The iOS floor is wider, 375dp on an iPhone SE.
         */
        val NarrowestContentWidth = 328.dp

        /** What three across hands one tile: a third of the row, less two gaps. */
        val AThird = (NarrowestContentWidth - GridGap * 2) / 3

        const val NoScaling = 1f
        const val LargestSystemFontScale = 2f

        /** The catalog's longest names, and a short one so a row has a mix. */
        const val LongestName = "Blink and You Miss It"
        val Names = listOf(LongestName, "An Hour with the Dogs", "Regular as Clockwork", "Top Dog")

        const val PreferredColumns = 3
        const val Face = "🦉"
        const val Fraction = "4 / 10"
        const val EarnedLabel = "Earned"
    }
}
