package com.sodogku.libraries.ui.components.feedback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

/**
 * Several unlocks show one at a time, and a toast over a board leaves the board
 * tappable.
 *
 * Both are claims about what is in the tree, which is why they are here rather
 * than beside [UnlockQueue]'s own tests: the queue says which item is the head,
 * and only a composition can say that the head is the only one drawn, that a
 * dismiss composes the next, and that a touch beside the card lands on what is
 * under it.
 *
 * ### Not here
 *
 * Which item is next, and that a dismiss takes exactly one, is `UnlockQueueTest`.
 * That the toast leaves on its own, and holds still when asked, is
 * `UnlockToastsHoldStillTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnlockToastsQueueTest {

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
    fun onlyTheHeadIsOnScreenWithItsReason() {
        show(listOf(First, Second))

        compose.onNodeWithText(First.title).assertExists()
        compose.onNodeWithText(FirstReason).assertExists()
        compose.onNodeWithText(Second.title).assertDoesNotExist()
    }

    @Test
    fun dismissingTheHeadShowsTheNextAndNotTheOneAfter() {
        var done = 0
        show(listOf(First, Second, Third), onDismiss = { done++ })

        compose.onNodeWithText(First.title).performClick()
        compose.mainClock.advanceTimeBy(PastTheExitMillis)
        compose.waitForIdle()

        compose.onNodeWithText(First.title).assertDoesNotExist()
        compose.onNodeWithText(Second.title).assertExists()
        compose.onNodeWithText(Third.title).assertDoesNotExist()
        assertEquals(0, done, "the caller was told the queue was done with two still to show")
    }

    @Test
    fun theCallerHearsBackOnceWhenTheLastOneLeaves() {
        var done = 0
        show(listOf(First, Second), onDismiss = { done++ })

        compose.onNodeWithText(First.title).performClick()
        compose.mainClock.advanceTimeBy(PastTheExitMillis)
        compose.waitForIdle()
        compose.onNodeWithText(Second.title).performClick()
        compose.mainClock.advanceTimeBy(PastTheExitMillis)
        compose.waitForIdle()

        compose.onNodeWithText(Second.title).assertDoesNotExist()
        assertEquals(1, done, "the caller heard back $done times for one queue")
    }

    /**
     * The toast is the width of the screen less a gutter. A touch in the
     * gutter, at the toast's own height, must reach the board underneath, and
     * a touch on the card must not: the second half is the guard against a
     * board that was simply on top of everything.
     */
    @Test
    fun aTapBesideTheToastReachesTheBoardAndATapOnItDoesNot() {
        var boardTaps = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReduceAnimations provides true) {
                AppThemeProvider {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().clickable { boardTaps++ })
                        UnlockToasts(
                            items = listOf(First),
                            onDismiss = {},
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        // The card merges its labels into one node, so the text is on the
        // clickable itself rather than under it.
        val card = compose.onNode(hasClickAction() and hasText(First.title))
            .fetchSemanticsNode()
            .boundsInRoot

        compose.onRoot().performTouchInput { click(Offset(x = 1f, y = card.center.y)) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, boardTaps, "a tap in the gutter beside the toast never reached the board")

        compose.onRoot().performTouchInput { click(card.center) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, boardTaps, "a tap on the toast fell through to the board")
    }

    private fun show(items: List<UnlockToastItem>, onDismiss: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // Still, so a dismiss is a state flip and a frame rather than a
            // slide to wait out; the slides are `UnlockToastsHoldStillTest`.
            CompositionLocalProvider(LocalReduceAnimations provides true) {
                AppThemeProvider {
                    UnlockToasts(items = items, onDismiss = onDismiss)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private companion object {
        const val FirstReason = "Clear your first board."
        val First = UnlockToastItem(glyph = "🐾", label = "Badge unlocked", title = "First Steps", body = FirstReason)
        val Second = UnlockToastItem(glyph = "✨", label = "Badge unlocked", title = "Perfect Form")
        val Third = UnlockToastItem(glyph = "⚡", label = "Badge unlocked", title = "Speed Demon")

        /** Past the exit however it is tuned, and well short of the dwell. */
        const val PastTheExitMillis = 800L
    }
}
