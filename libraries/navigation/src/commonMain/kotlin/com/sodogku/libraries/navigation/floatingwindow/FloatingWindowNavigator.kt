package com.sodogku.libraries.navigation.floatingwindow

import androidx.compose.runtime.Composable
import androidx.navigation.FloatingWindow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator

/**
 * Navigator used to allow the nav graph to show floating windows in response to a navigation.
 */
@Navigator.Name("floatingwindow")
class FloatingWindowNavigator : Navigator<FloatingWindowNavigator.Destination>() {

    /**
     * Get the back stack from the [state].
     */
    internal val backStack get() = state.backStack

    /**
     * Entries the navigator is still transitioning. An entry stays here until
     * something calls [onTransitionComplete] for it, and `NavController` will
     * not destroy an entry or clear its `ViewModelStore` while it is listed.
     * [FloatingWindowHost] watches this to catch entries that were popped
     * before they ever composed.
     */
    internal val transitionsInProgress get() = state.transitionsInProgress

    /**
     * Upstream's `DialogNavigator` pushes without a transition here. This one
     * does not, so `NavController` caps the entry at STARTED until something
     * calls [onTransitionComplete], and for a window that stays on screen that
     * means for as long as it is on screen. Nothing chose this: the file arrived
     * whole from the template.
     *
     * Checked on 2026-09-11 and left alone, because nothing in this app can tell
     * the two apart. Everything hung off a destination's lifecycle gates on
     * STARTED: `collectAsStateWithLifecycle` at its default, `ObserveEvents` via
     * `ObserveWithLifecycle`, the host's own visibility filter, the watchdog in
     * `:apps:compose`. `NavController` reads the transition set only to pick
     * that cap, so the entry, its `ViewModelStore` and its saved state behave
     * the same either way.
     *
     * The one thing that does outlive the window: on a pop, [popBackStack]
     * completes the entries positioned after `popUpTo`, which upstream is the
     * incoming one. Pushed with a transition, a sheet underneath is already in
     * the set ahead of `popUpTo`, so it is skipped and stays capped at STARTED
     * with the screen to itself. Still nothing reads it.
     *
     * All three readings are pinned in `FloatingWindowHostTest` rather than
     * argued here. Switch to `state.push` if a sheet or dialog ever needs
     * RESUMED: a `LifecycleResumeEffect`, a `repeatOnLifecycle(RESUMED)`, or a
     * `collectAsStateWithLifecycle(minActiveState = RESUMED)` would all sit dead
     * inside a window this pushes.
     */
    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ) {
        entries.forEach { entry ->
            state.pushWithTransition(entry)
        }
    }

    override fun createDestination(): Destination {
        return Destination(this) {  }
    }

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.popWithTransition(popUpTo, savedState)
        // When popping, the incoming dialog is marked transitioning to hold it in
        // STARTED. With pop complete, we can remove it from transition so it can move to RESUMED.
        val popIndex = state.transitionsInProgress.value.indexOf(popUpTo)
        // do not mark complete for entries up to and including popUpTo
        state.transitionsInProgress.value.forEachIndexed { index, entry ->
            if (index > popIndex) onTransitionComplete(entry)
        }
    }

    internal fun onTransitionComplete(entry: NavBackStackEntry) {
        state.markTransitionComplete(entry)
    }

    /**
     * NavDestination specific to [FloatingWindowNavigator]
     */
    @NavDestination.ClassType(Composable::class)
    class Destination(
        navigator: FloatingWindowNavigator,
        internal val content: @Composable (NavBackStackEntry) -> Unit
    ) : NavDestination(navigator), FloatingWindow
}
