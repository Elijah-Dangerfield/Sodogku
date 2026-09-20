package com.sodogku.libraries.ui.components.achievements

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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

/**
 * A tile shows exactly the line its state earns under the name: a bar for a
 * badge being earned, the earned pill for one that is, and nothing at all for
 * a mystery.
 *
 * The mystery case is the one worth a composition test. Its numbers are zeroed
 * upstream, so a tile that drew a bar for it would draw an honest-looking
 * "0 / 1" and tell the player exactly what to go and try, which is the half of
 * the surprise worth keeping. Nothing below composition can see whether a
 * `when` branch composed a bar.
 *
 * The bar is found by its progress semantics, which [ProgressBar] declares, and
 * the pill by its text. Both are read from the unmerged tree because the tile
 * names itself as one sentence and clears everything under that name for a
 * screen reader; the nodes are still there, they are just not spoken.
 *
 * ### Not here
 *
 * Whether a name fits its tile is `BadgeTileGridFitsTest`. Which badge on a
 * shelf is the next rung is `AchievementsViewModelTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BadgeTileSemanticsTest {

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
    fun aLockedTileShowsABarAndNoEarnedPill() {
        show(BadgeTileState.Locked(BadgeProgress(0.4f, Fraction), upNext = true))

        progressBars().assertCountEquals(1)
        compose.onAllNodesWithText(Fraction, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText(EarnedLabel.uppercase(), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun anEarnedTileShowsThePillAndNoBar() {
        show(BadgeTileState.Earned)

        compose.onAllNodesWithText(EarnedLabel.uppercase(), useUnmergedTree = true).assertCountEquals(1)
        progressBars().assertCountEquals(0)
    }

    @Test
    fun aMysteryTileShowsNeither() {
        show(BadgeTileState.Mystery)

        progressBars().assertCountEquals(0)
        compose.onAllNodesWithText(EarnedLabel.uppercase(), useUnmergedTree = true).assertCountEquals(0)
    }

    /** And the tile is still one thing a player can press, whatever is under the name. */
    @Test
    fun aTileReadsAsOneSentenceAndIsClickable() {
        show(BadgeTileState.Locked(BadgeProgress(0.4f, Fraction), upNext = false))

        compose.onNodeWithContentDescription(Spoken).assertHasClickAction()
        compose.onAllNodesWithText(Fraction).assertCountEquals(0)
    }

    private fun progressBars() = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        useUnmergedTree = true,
    )

    private fun show(state: BadgeTileState) {
        compose.setContent {
            AppThemeProvider {
                BadgeTile(
                    spec = BadgeTileSpec(
                        face = Face,
                        name = Name,
                        state = state,
                        set = AppTheme.colors.badgeSets[AchievementGroup.Campaign],
                        spoken = Spoken,
                    ),
                    earnedLabel = EarnedLabel,
                    onClick = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val Face = "🌳"
        const val Name = "Off the Leash"
        const val Fraction = "4 / 10"
        const val EarnedLabel = "Earned"
        const val Spoken = "Off the Leash. Locked, 4 of 10."
    }
}
