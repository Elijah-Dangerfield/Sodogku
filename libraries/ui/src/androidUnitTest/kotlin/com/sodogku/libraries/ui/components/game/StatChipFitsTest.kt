package com.sodogku.libraries.ui.components.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.sodogku.libraries.ui.components.text.Text
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
import kotlin.test.assertTrue

/**
 * Nothing on a stat chip is cut short, however big the player's text is.
 *
 * The 2026-09 handoff puts three chips across a phone with a one-word label
 * on each, and "MISTAKES" at the largest system text size is wider than a
 * third of the narrowest phone this app supports. A chip that ellipsized it
 * would be showing "MISTAK…" on the one screen a mistake count matters, and a
 * chip that clipped it would be worse. So [StatChipRow] stacks its chips when
 * a third of the row is short of what the widest chip needs, and the claim
 * here is that the label and the value are always drawn whole.
 *
 * ### What is measured
 *
 * The width the text is laid out at inside the chip against the width the same
 * string wants with nothing capping it: a second copy, in the same typography,
 * measured beside the row. Equal means whole. That is also why
 * [StatChipLabelTypography] and [StatChipValueTypography] are named: the bond
 * between the two halves is a token, so a size changed in the chip is the size
 * this test measures.
 *
 * [aChipForcedTooNarrowIsCutAndTheMeasurementSaysSo] is the guard against the
 * guard. Every other assertion is "these two widths agree", which is also what
 * a pair of measurements that had stopped reading the composition would
 * report; a chip squeezed below its text genuinely does come up short.
 *
 * `@GraphicsMode(NATIVE)` is load-bearing. Under Robolectric's legacy graphics
 * every string measures to the same fixed box whatever size it is set in, and
 * the overflow case would pass as a fit.
 *
 * ### What is deliberately not covered
 *
 * Whether the chips are side by side or stacked at a given size. That is the
 * row choosing the layout that keeps the text whole, and which one it chose is
 * an outcome of the font, not a contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatChipFitsTest {

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
    fun theLabelAndValueAreWholeAtTheDefaultFontSize() {
        showRow(NoScaling)

        assertWhole(NoScaling)
    }

    /**
     * 2.0 is Android's largest system font size, and it is above the 1.8 that
     * Compose Multiplatform maps iOS's largest accessibility text size to, so
     * the one number covers both platforms.
     */
    @Test
    fun theLabelAndValueAreWholeAtTheLargestSystemFontSize() {
        showRow(LargestSystemFontScale)

        assertWhole(LargestSystemFontScale)
    }

    /**
     * And the row really is measuring against something. At the default size
     * three chips fit side by side, so the label sits in a chip a third of the
     * row wide; if the widest label were somehow already wider than that, every
     * "whole" above would be a chip that had stacked for free.
     */
    @Test
    fun theChipsSitSideBySideAtTheDefaultFontSize() {
        showRow(NoScaling)

        val first = compose.onNodeWithContentDescription(SpokenScore).getUnclippedBoundsInRoot()
        val last = compose.onNodeWithContentDescription(SpokenMistakes).getUnclippedBoundsInRoot()

        assertEquals(first.top, last.top, "at the default text size the chips have stacked, so nothing here is squeezing a label into a third of the row")
        assertTrue(first.width < NarrowestContentWidth / 2, "a side-by-side chip measured ${first.width.value}dp wide, which is not a third of a ${NarrowestContentWidth.value}dp row")
    }

    @Test
    fun aChipForcedTooNarrowIsCutAndTheMeasurementSaysSo() {
        showOneChip(width = TooNarrow)

        assertTrue(
            drawnLabelWidth() < wantedLabelWidth(),
            "a chip squeezed to ${TooNarrow.value}dp drew its label at ${drawnLabelWidth().value}dp " +
                "against the ${wantedLabelWidth().value}dp it wants and was called whole, so " +
                "nothing here is reading the composition it claims to",
        )
    }

    private fun assertWhole(scale: Float) {
        assertEquals(
            wantedLabelWidth(),
            drawnLabelWidth(),
            "at font scale $scale the label wants ${wantedLabelWidth().value}dp and is drawn at " +
                "${drawnLabelWidth().value}dp, so the chip is cutting its own label short",
        )
        assertEquals(
            wantedValueWidth(),
            drawnValueWidth(),
            "at font scale $scale the value wants ${wantedValueWidth().value}dp and is drawn at " +
                "${drawnValueWidth().value}dp, so the chip is cutting its own value short",
        )
    }

    /** The label as the chip lays it out. Uppercased, because that is what the chip draws. */
    private fun drawnLabelWidth(): Dp = drawnWidthOf(WidestLabel.uppercase())

    private fun drawnValueWidth(): Dp = drawnWidthOf(WidestValue)

    /**
     * The chip's own copy of [text], which is the one without a tag: the probe
     * beside it is the same string and is the only tagged node.
     */
    private fun drawnWidthOf(text: String): Dp = compose
        .onAllNodesWithText(text, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .first { node -> SemanticsProperties.TestTag !in node.config }
        .let { node -> with(compose.density) { node.boundsInRoot.width.toDp() } }

    /** The same strings with nothing capping them. */
    private fun wantedLabelWidth(): Dp =
        compose.onNodeWithTag(UncappedLabelTag).getUnclippedBoundsInRoot().width

    private fun wantedValueWidth(): Dp =
        compose.onNodeWithTag(UncappedValueTag).getUnclippedBoundsInRoot().width

    private var fontScale by mutableFloatStateOf(NoScaling)

    private fun showRow(scale: Float) {
        fontScale = scale
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                AppThemeProvider {
                    // A `Box` and not a `Column`: the probes are measured beside
                    // the row rather than under it, so a row that grows can
                    // never be what starves a probe of room.
                    Box {
                        StatChipRow(chips = chips(), modifier = Modifier.width(NarrowestContentWidth))
                        Probes()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showOneChip(width: Dp) {
        fontScale = NoScaling
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                AppThemeProvider {
                    Box {
                        StatChip(spec = chips().last(), modifier = Modifier.width(width))
                        Probes()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Probes() {
        Text(
            text = WidestLabel,
            typography = StatChipLabelTypography,
            allCaps = true,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(UncappedLabelTag),
        )
        Text(
            text = WidestValue,
            typography = StatChipValueTypography,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(UncappedValueTag),
        )
    }

    @Composable
    private fun chips(): List<StatChipSpec> = listOf(
        StatChipSpec(
            label = "Score",
            value = "791",
            tint = AppTheme.colors.accentBrand,
            labelInk = AppTheme.colors.onAccentBrand,
            valueInk = AppTheme.colors.accentBrandInk,
            spoken = SpokenScore,
            glyph = "⚡",
        ),
        StatChipSpec(
            label = "Time",
            value = WidestValue,
            tint = AppTheme.colors.accentSecondary,
            labelInk = AppTheme.colors.onAccentSecondary,
            valueInk = AppTheme.colors.accentSecondary,
            spoken = "2 minutes 3 seconds",
            glyph = "⏱",
        ),
        StatChipSpec(
            label = WidestLabel,
            value = "1",
            tint = AppTheme.colors.danger,
            labelInk = AppTheme.colors.onAccentPrimary,
            valueInk = AppTheme.colors.danger,
            spoken = SpokenMistakes,
            glyph = "❌",
        ),
    )

    private companion object {
        /**
         * 360dp is the narrowest Android width this app assumes, and the screen
         * takes 12dp off each side. The iOS floor is wider, 375dp on an iPhone SE.
         */
        val NarrowestContentWidth = 336.dp

        /** Narrower than "MISTAKES" at the default size, so the label has to be cut. */
        val TooNarrow = 40.dp

        const val NoScaling = 1f
        const val LargestSystemFontScale = 2f

        const val WidestLabel = "Mistakes"
        const val WidestValue = "2:03"
        const val SpokenScore = "791 points"
        const val SpokenMistakes = "1 mistake"
        const val UncappedLabelTag = "uncapped-label"
        const val UncappedValueTag = "uncapped-value"
    }
}
