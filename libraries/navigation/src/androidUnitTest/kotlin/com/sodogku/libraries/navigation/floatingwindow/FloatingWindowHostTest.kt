package com.sodogku.libraries.navigation.floatingwindow

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.get
import com.sodogku.libraries.flowroutines.ObserveWithLifecycle
import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.navigation.baseRouteTypeMap
import com.sodogku.libraries.navigation.screen
import com.sodogku.libraries.navigation.serializableType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
internal class HostTestHomeRoute : Route()

@Serializable
internal class HostTestSheetRoute : Route()

@Serializable
internal class HostTestUpperSheetRoute : Route()

private const val SHEET_TAG = "floating-window-test-sheet"

/**
 * [FloatingWindowHost] completes a navigator transition from `onDispose`, so an
 * entry popped before it ever composes has nothing to dispose and stays in the
 * navigator's `transitionsInProgress`. `NavController` will not move an entry it
 * still believes is transitioning past CREATED, and will not clear its
 * `ViewModelStore`, so that entry is pinned for the life of the process. That is
 * a claim about recomposition, which is why no view-model test could make it.
 *
 * Covered: that a popped entry is released whether or not it ever composed, and
 * that a sheet's content reaches the screen and leaves again.
 *
 * Also covered, for SD-68: what `FloatingWindowNavigator.navigate`'s
 * `pushWithTransition` costs, which is the state a live window settles at and
 * nothing else. See the comment on that method for the full reading.
 *
 * Not covered, and known: the window between a pop and the dispose that follows
 * it, which is what the `awaitingDispose` guard in the host exists for. Removing
 * that guard leaves both tests green. Nor the `ON_START` branch of
 * `PopulateVisibleList`, which is redundant with `rememberVisibleList`'s own
 * filter on this path: breaking either one alone still shows the sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingWindowHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anEntryPoppedBeforeItComposesIsReleased() {
        val navigator = FloatingWindowNavigator()
        lateinit var navController: NavHostController

        compose.setContent {
            navController = rememberNavController(navigator)
            FloatingWindowTestGraph(navController)
            FloatingWindowHost(navigator)
        }
        compose.waitForIdle()

        var popped: NavBackStackEntry? = null
        // Both calls in one block, so no recomposition can run between them.
        // That is the whole condition: the sheet is gone again before the host
        // ever sees it, so there is no DisposableEffect to complete it.
        compose.runOnIdle {
            navController.navigate(HostTestSheetRoute())
            popped = navController.currentBackStackEntry
            navController.popBackStack()
        }
        compose.waitForIdle()

        val entry = assertNotNull(popped, "navigate() pushed no entry")
        assertEquals(
            Lifecycle.State.DESTROYED,
            entry.lifecycle.currentState,
            "the popped entry is still marked in transition, so it and its ViewModelStore leak",
        )
    }

    @Test
    fun aSheetThatComposesIsShownAndReleasedOnPop() {
        val navigator = FloatingWindowNavigator()
        lateinit var navController: NavHostController

        compose.setContent {
            navController = rememberNavController(navigator)
            FloatingWindowTestGraph(navController)
            FloatingWindowHost(navigator)
        }
        compose.waitForIdle()

        compose.runOnIdle { navController.navigate(HostTestSheetRoute()) }
        compose.waitForIdle()

        compose.onNodeWithTag(SHEET_TAG).assertIsDisplayed()
        val entry = assertNotNull(navController.currentBackStackEntry)

        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        compose.onNodeWithTag(SHEET_TAG).assertDoesNotExist()
        assertEquals(
            Lifecycle.State.DESTROYED,
            entry.lifecycle.currentState,
            "a sheet that did compose must still be released when it is popped",
        )
    }

    @Test
    fun aSheetOnScreenIsHeldAtStarted() {
        val navigator = FloatingWindowNavigator()
        lateinit var navController: NavHostController

        compose.setContent {
            navController = rememberNavController(navigator)
            FloatingWindowTestGraph(navController)
            FloatingWindowHost(navigator)
        }
        compose.waitForIdle()

        compose.runOnIdle { navController.navigate(HostTestSheetRoute()) }
        compose.waitForIdle()

        compose.onNodeWithTag(SHEET_TAG).assertIsDisplayed()
        val entry = assertNotNull(navController.currentBackStackEntry)
        assertEquals(
            Lifecycle.State.STARTED,
            entry.lifecycle.currentState,
            "the sheet is drawn and interactive, and `navigate` holds it below RESUMED",
        )
    }

    @Test
    fun aSheetHeldAtStartedStillCollectsWithLifecycle() {
        val navigator = FloatingWindowNavigator()
        lateinit var navController: NavHostController
        val ticks = MutableStateFlow(0)
        var seen = -1

        compose.setContent {
            navController = rememberNavController(navigator)
            FloatingWindowTestGraph(navController) {
                ObserveWithLifecycle(ticks) { seen = it }
                BasicText("sheet", Modifier.testTag(SHEET_TAG))
            }
            FloatingWindowHost(navigator)
        }
        compose.waitForIdle()

        compose.runOnIdle { navController.navigate(HostTestSheetRoute()) }
        compose.waitUntil { seen == 0 }

        compose.runOnIdle { ticks.value = 1 }
        compose.waitUntil { seen == 1 }
    }

    /**
     * The second-order effect of pushing with a transition, and the only one
     * that outlives the window it happened to.
     *
     * `popBackStack` completes every entry positioned after `popUpTo` in
     * `transitionsInProgress`, which upstream is exactly the incoming entry that
     * `popWithTransition` just added. Here the lower sheet is already in the set
     * from its own push, ahead of `popUpTo`, so the loop skips it and it is
     * never completed. It stays capped at STARTED with the screen to itself.
     */
    @Test
    fun poppingTheUpperOfTwoSheetsLeavesTheLowerInTransition() {
        val navigator = FloatingWindowNavigator()
        lateinit var navController: NavHostController

        compose.setContent {
            navController = rememberNavController(navigator)
            FloatingWindowTestGraph(navController)
            FloatingWindowHost(navigator)
        }
        compose.waitForIdle()

        compose.runOnIdle { navController.navigate(HostTestSheetRoute()) }
        compose.waitForIdle()
        val lower = assertNotNull(navController.currentBackStackEntry)

        compose.runOnIdle { navController.navigate(HostTestUpperSheetRoute()) }
        compose.waitForIdle()
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        assertTrue(
            navigator.transitionsInProgress.value.contains(lower),
            "the lower sheet is the only window left and nothing will complete it",
        )
        assertEquals(
            Lifecycle.State.STARTED,
            lower.lifecycle.currentState,
            "so it stays below RESUMED even though it is now the top of the stack",
        )
    }
}

@Composable
private fun FloatingWindowTestGraph(
    navController: NavHostController,
    sheetContent: @Composable () -> Unit = { BasicText("sheet", Modifier.testTag(SHEET_TAG)) },
) {
    NavHost(
        navController = navController,
        startDestination = HostTestHomeRoute(),
        typeMap = mapOf(typeOf<AnimationType>() to serializableType<AnimationType>()),
    ) {
        screen<HostTestHomeRoute> { }
        // The real `bottomSheet` / `dialog` builders wrap the content in a
        // design system sheet. Registering the destination directly keeps the
        // subject the host's transition bookkeeping, not the sheet's chrome.
        destination(
            FloatingWindowNavDestinationBuilder(
                provider[FloatingWindowNavigator::class],
                HostTestSheetRoute::class,
                baseRouteTypeMap,
            ) {
                sheetContent()
            }
        )
        destination(
            FloatingWindowNavDestinationBuilder(
                provider[FloatingWindowNavigator::class],
                HostTestUpperSheetRoute::class,
                baseRouteTypeMap,
            ) {
                BasicText("upper sheet")
            }
        )
    }
}
