package com.sodogku.libraries.navigation

/**
 * Which animation each screen plays during a navigation, given the two routes.
 *
 * Pulled out of the `NavHost` lambdas in `App.kt` so it can be tested. The
 * lambdas are handed a `NavBackStackEntry` and run inside an
 * `AnimatedContentTransitionScope`, neither of which a unit test can build, so
 * for as long as the rules lived there the only way to check them was to open
 * the app and watch. The paywall bug survived exactly that long.
 *
 * Each function takes the two routes and returns an [AnimationType]. Whether a
 * route is present at all is part of the question: a destination that is not one
 * of ours has no metadata and should not move.
 */
object RouteTransitions {

    /**
     * The **arriving** screen, pushing forward. Always its own entrance.
     */
    fun enter(target: Route?): AnimationType = target?.enter ?: AnimationType.None

    /**
     * The **leaving** screen, pushing forward.
     *
     * Normally its own exit: the two screens travel together and only one is on
     * screen at the end. But a route that [Route.coversParent] arrives *over*
     * this one, so this one holds still and the overlay lands on a page that has
     * not moved.
     */
    fun exit(initial: Route?, target: Route?): AnimationType = when {
        target?.coversParent == true -> AnimationType.None
        else -> initial?.exit ?: AnimationType.None
    }

    /**
     * The **returning** screen, popping back.
     *
     * Mirrors whatever the screen on top is doing as it leaves, so the two move
     * as one. Unless that screen was covering this one, in which case this one
     * never went anywhere and must not come back: mirroring a slide-down here is
     * what slid Settings up from the bottom while the paywall slid down, making
     * the still page look like the moving one.
     */
    fun popEnter(initial: Route?, target: Route?): AnimationType = when {
        initial?.coversParent == true -> AnimationType.None
        initial != null -> initial.popExit.opposite()
        else -> target?.enter ?: AnimationType.None
    }

    /**
     * The **leaving** screen, popping back. Always its own pop exit.
     */
    fun popExit(initial: Route?): AnimationType = initial?.popExit ?: AnimationType.None
}
