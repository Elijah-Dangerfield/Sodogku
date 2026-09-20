package com.sodogku.libraries.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.sodogku.system.AppTheme
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
 * The hero hangs off the band by exactly the overhang, and the kicker sits
 * inside it.
 *
 * The overhang is the whole reason [HeroBand] is a `Layout` rather than a
 * `Column` with a background, and it is the one thing about it that cannot be
 * judged from the source: the band's height is arithmetic on the two slots'
 * measurements, and a wrong sign or a dropped term in it is a dog with its
 * feet on the band's edge, or floating below it. Nothing below this tier can
 * measure a composition, so this is where it is pinned.
 *
 * Density is fixed at 1 so a dp is a pixel and the rounding in the layout
 * cannot make an exact assertion flaky. The slots are plain boxes rather than
 * text, so nothing here depends on how a glyph measures under Robolectric.
 *
 * ### What is deliberately not covered
 *
 * The watermark and the corner radius, which are pixels, and the status-bar
 * inset, which is the screen's to add and is tested by nothing until a screen
 * gets a composition test of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeroBandLayoutTest {

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
    fun theHeroHangsOffTheBandByExactlyTheOverhang() {
        showBand()

        assertEquals(
            Overhang.value,
            (hero().bottom - band().bottom).value,
            "the hero's bottom is ${(hero().bottom - band().bottom).value}dp below the band's " +
                "edge, not the ${Overhang.value}dp it was asked to overhang by",
        )
    }

    @Test
    fun theKickerSitsInsideTheBand() {
        showBand()

        assertTrue(
            kicker().top >= band().top && kicker().bottom <= band().bottom,
            "the kicker spans ${kicker().top.value} to ${kicker().bottom.value}dp and the band " +
                "${band().top.value} to ${band().bottom.value}dp, so the label is off its band",
        )
        assertEquals(KickerTop.value, kicker().top.value, "and it starts where the padding says")
    }

    /**
     * The guard against the guard. Both assertions above are arithmetic on
     * three rectangles, and all three would agree with each other if the band
     * were the whole layout and the hero drawn on top of it. The band has to
     * end above the hero for "overhang" to mean anything.
     */
    @Test
    fun theBandReallyDoesEndAboveTheHero() {
        showBand()

        assertTrue(
            band().bottom < hero().bottom && band().bottom > hero().top,
            "the band ends at ${band().bottom.value}dp against a hero from ${hero().top.value} " +
                "to ${hero().bottom.value}dp, so the hero is not hanging off anything",
        )
    }

    private fun band(): DpRect = compose.onNodeWithTag(HeroBandTestTag).getUnclippedBoundsInRoot()
    private fun kicker(): DpRect = compose.onNodeWithTag(KickerTag).getUnclippedBoundsInRoot()
    private fun hero(): DpRect = compose.onNodeWithTag(HeroTag).getUnclippedBoundsInRoot()

    private fun showBand() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                AppThemeProvider {
                    HeroBand(
                        color = AppTheme.colors.accentPrimary,
                        overhang = Overhang,
                        kickerTopPadding = KickerTop,
                        kicker = { Box(Modifier.testTag(KickerTag).size(KickerSize)) },
                        hero = { Box(Modifier.testTag(HeroTag).size(HeroSize)) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        val Overhang = 56.dp
        val KickerTop = 30.dp
        val KickerSize = 20.dp
        val HeroSize = 124.dp

        const val KickerTag = "kicker"
        const val HeroTag = "hero"
    }
}
