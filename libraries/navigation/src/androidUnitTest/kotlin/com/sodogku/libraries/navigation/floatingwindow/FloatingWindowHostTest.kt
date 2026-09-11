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
import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.navigation.baseRouteTypeMap
import com.sodogku.libraries.navigation.screen
import com.sodogku.libraries.navigation.serializableType
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
internal class HostTestHomeRoute : Route()

@Serializable
internal class HostTestSheetRoute : Route()

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
}

@Composable
private fun FloatingWindowTestGraph(navController: NavHostController) {
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
                BasicText("sheet", Modifier.testTag(SHEET_TAG))
            }
        )
    }
}
