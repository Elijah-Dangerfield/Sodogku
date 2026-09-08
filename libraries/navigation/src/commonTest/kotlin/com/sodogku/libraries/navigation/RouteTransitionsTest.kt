package com.sodogku.libraries.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What each of the two screens does during a navigation.
 *
 * The rules used to be inline in `App.kt`'s `NavHost` lambdas, which take a
 * `NavBackStackEntry` and run inside an `AnimatedContentTransitionScope`. A unit
 * test can build neither, so the only way to check a transition was to open the
 * app and watch one, and the paywall bug lasted exactly as long as that was
 * true: opening Pro from Settings slid Pro up *and* slid Settings out to the
 * left, and closing it slid Settings back up from the bottom, so the screen that
 * should have been sitting still was the one that appeared to move.
 */
class RouteTransitionsTest {

    private val page = Route()

    private val overlay = Route(
        enter = AnimationType.SlideUp,
        exit = AnimationType.SlideDown,
        popExit = AnimationType.SlideDown,
        coversParent = true,
    )

    @Test
    fun anOverlayLeavesThePageBeneathAlone() {
        assertEquals(
            AnimationType.None,
            RouteTransitions.exit(initial = page, target = overlay),
            "the page slid away while something slid up over it",
        )
        assertEquals(
            AnimationType.None,
            RouteTransitions.popEnter(initial = overlay, target = page),
            "the page came back, which means it had left",
        )
    }

    @Test
    fun theOverlayItselfStillMoves() {
        // The whole point is that one of the two moves. A fix that stilled both
        // would satisfy the test above and leave the paywall appearing instantly.
        assertEquals(AnimationType.SlideUp, RouteTransitions.enter(target = overlay))
        assertEquals(AnimationType.SlideDown, RouteTransitions.popExit(initial = overlay))
    }

    @Test
    fun anOrdinaryPushStillMovesBothScreens() {
        // The other half. Stilling the outgoing screen for every navigation
        // would make every push look like a cut.
        assertEquals(
            AnimationType.SlideOutToLeft,
            RouteTransitions.exit(initial = page, target = page),
            "an ordinary push stopped animating the screen it replaces",
        )
        assertEquals(
            AnimationType.SlideInFromRight,
            RouteTransitions.enter(target = page),
        )
    }

    @Test
    fun anOrdinaryPopBringsTheScreenBeneathBack() {
        // Mirrored, so the two move together: the top slides out to the right
        // and the one underneath slides in from the left behind it.
        assertEquals(
            AnimationType.SlideOutToRight.opposite(),
            RouteTransitions.popEnter(initial = page, target = page),
        )
        assertEquals(AnimationType.SlideOutToRight, RouteTransitions.popExit(initial = page))
    }

    @Test
    fun aDestinationWithNoRouteMetadataDoesNotMove() {
        // Not every destination in the graph is one of ours. Guessing an
        // animation for something with no metadata is how a screen ends up
        // sliding in a direction nobody chose.
        assertEquals(AnimationType.None, RouteTransitions.enter(target = null))
        assertEquals(AnimationType.None, RouteTransitions.exit(initial = null, target = null))
        assertEquals(AnimationType.None, RouteTransitions.popExit(initial = null))
        assertEquals(AnimationType.None, RouteTransitions.popEnter(initial = null, target = null))
    }

    @Test
    fun popEnterFallsBackToTheArrivingRouteWhenThereIsNoOutgoingOne() {
        // Popping onto one of our routes from a destination that is not ours:
        // there is nothing to mirror, so use what the arriving screen would
        // normally do on the way in.
        assertEquals(
            AnimationType.SlideInFromRight,
            RouteTransitions.popEnter(initial = null, target = page),
        )
    }
}
