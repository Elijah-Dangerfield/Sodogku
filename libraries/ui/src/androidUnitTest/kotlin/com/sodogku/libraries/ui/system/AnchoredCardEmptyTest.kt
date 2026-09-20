package com.sodogku.libraries.ui.system

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sodogku.libraries.ui.components.feedback.UnlockToastItem
import com.sodogku.libraries.ui.components.feedback.UnlockToasts
import com.sodogku.system.AppThemeProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An anchored card with nothing in it measures rather than throws.
 *
 * On 2026-09-20 a tap on the First Steps toast took the app down on iOS. The
 * toast hangs off the lives pill through [AnchoredCard], the caller leaves the
 * badge list alone when the queue is done, and a toast that has left composes
 * nothing: so the card's Layout measured with no children and `first()` threw
 * inside the measure pass. Waiting out the dwell reached the same line.
 *
 * Driven through the real toast rather than an empty lambda so the test is
 * about the state the game gets into and not about a contract nobody calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnchoredCardEmptyTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun aToastThatHasLeftLeavesTheCardEmptyAndStanding() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReduceAnimations provides true) {
                AppThemeProvider {
                    AnchoredCard(anchor = Rect.Zero) {
                        UnlockToasts(items = listOf(Badge), onDismiss = {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(Badge.title).assertExists()

        compose.onNodeWithText(Badge.title).performClick()
        compose.mainClock.advanceTimeBy(PastTheExitMillis)
        compose.waitForIdle()

        // The measure pass with no child is what used to throw; getting here
        // is the assertion, and the toast being gone shows it was reached.
        compose.onNodeWithText(Badge.title).assertDoesNotExist()
    }

    private companion object {
        val Badge = UnlockToastItem(glyph = "🐾", label = "Badge unlocked", title = "First Steps")

        /** Past the exit however it is tuned, and well short of the dwell. */
        const val PastTheExitMillis = 800L
    }
}
