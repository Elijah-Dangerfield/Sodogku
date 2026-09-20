package com.sodogku.libraries.ui.components.feedback

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
 * A badge toast slides away when its time is up, and vanishes in place instead
 * when the player has asked for fewer animations, without losing the timer
 * that takes it away.
 *
 * Until 2026-09-20 the toasts read inspection mode and nothing else, so a
 * player who had turned animations down still got a slide over the board.
 * The fix has two halves and the second is the one worth pinning: dropping
 * the travel is decoration, but a toast that had also dropped its dismiss
 * would sit over the board until tapped, which is the one thing a toast must
 * not do.
 *
 * ### Why the exit and not the entrance
 *
 * The toast's `AnimatedVisibility` starts visible, so its enter transition
 * never runs: the first draft of this file watched the entrance and counted
 * nothing with animations on. The slide that actually reaches the screen is
 * the one out, after the dwell, and that is the window measured here.
 *
 * ### What is measured
 *
 * Snapshot writes while the exit would be running, the measure every
 * holds-still test here uses. And then, separately, that the toast is gone
 * and has called back once its dwell has passed, with the setting on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnlockToastsHoldStillTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installResourceContext() {
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredField("ANDROID_CONTEXT")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    /** The guard against the guard: a toast that had lost its slide would also report nothing. */
    @Test
    fun aToastIsStillSlidingOutWhenNothingIsWatching() {
        val changes = changesWhileTheToastLeaves(reduceAnimations = false)

        assertTrue(
            changes > MinMovingChanges,
            "a toast produced $changes state writes during its exit, so either the slide " +
                "is not running or this is measuring the wrong thing",
        )
    }

    @Test
    fun aToastVanishesInPlaceWhenThePlayerAskedForFewerAnimations() {
        assertEquals(
            0,
            changesWhileTheToastLeaves(reduceAnimations = true),
            "the reduce-animations setting no longer reaches the badge toast, which still " +
                "slides off the board",
        )
    }

    @Test
    fun aToastStillLeavesOnItsOwnWithFewerAnimations() {
        var dismissed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReduceAnimations provides true) {
                AppThemeProvider {
                    UnlockToasts(items = listOf(Item), onDismiss = { dismissed++ })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(Item.title).assertExists()

        compose.mainClock.advanceTimeBy(PastTheDwellMillis)
        compose.waitForIdle()

        assertEquals(1, dismissed, "with animations reduced the toast never called back, so it sits over the board until tapped")
        compose.onNodeWithText(Item.title).assertDoesNotExist()
    }

    private fun changesWhileTheToastLeaves(reduceAnimations: Boolean): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReduceAnimations provides reduceAnimations) {
                AppThemeProvider {
                    UnlockToasts(items = listOf(Item), onDismiss = {})
                }
            }
        }
        compose.waitForIdle()

        // Into the exit: past the dwell and the write that hides the toast, so
        // the count is the slide and nothing else.
        compose.mainClock.advanceTimeBy(JustPastTheDwellMillis)
        compose.waitForIdle()

        var changes = 0
        val handle = Snapshot.registerApplyObserver { changed, _ -> changes += changed.size }
        return try {
            compose.mainClock.advanceTimeBy(ExitWindowMillis)
            compose.waitForIdle()
            changes
        } finally {
            handle.dispose()
        }
    }

    private companion object {
        val Item = UnlockToastItem(glyph = "🐾", label = "Badge unlocked", title = "First Steps")

        /** The toast's own dwell plus a frame or two. */
        const val JustPastTheDwellMillis = 2_700L

        /** Long enough for the slide out, however it is tuned. */
        const val ExitWindowMillis = 600L

        /** The dwell plus the exit, with room to spare. */
        const val PastTheDwellMillis = 4_000L

        const val MinMovingChanges = 10
    }
}
