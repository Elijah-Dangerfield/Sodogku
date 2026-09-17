package com.sodogku.features.streak

import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.Route
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the streak page arrives, which is decided by the one number the route
 * carries.
 *
 * A ceremony rises over the board and a page the player asked for is pushed. The
 * two are written as a pair on purpose: "the ceremony slides up" alone passes
 * against a route that slides up whatever opened it, which is the thing the
 * `celebrating` argument exists to prevent.
 *
 * The entrance lives on the route rather than in `StreakScreen` because a page
 * cannot rise over a board the navigator has already removed. That makes it a
 * decision over a plain `Int`, which is why this file can exist at all.
 *
 * Deliberately not covered here: what the *page* does once it has arrived. The
 * staggered beats and the number flip are claims about a composition and about
 * view-model state, and they live in `:libraries:ui`'s composition tier and in
 * `StreakViewModelTest`.
 */
class StreakRouteTest {

    @Test
    fun aCeremonyRisesOverABoardThatHoldsStill() {
        val route = StreakRoute(celebrating = 2)

        assertEquals(AnimationType.SlideUp, route.enter, "the ceremony no longer slides up")
        assertEquals(
            AnimationType.SlideDown,
            route.popExit,
            "a ceremony that rose over the board should leave the way it came",
        )
        assertTrue(
            route.coversParent,
            "the board slides out from under the ceremony, so the screen that is holding " +
                "still is the one that appears to move — the paywall bug, again",
        )
    }

    @Test
    fun aPageThePlayerOpenedIsAnOrdinaryPush() {
        val route = StreakRoute()

        assertEquals(
            AnimationType.SlideInFromRight,
            route.enter,
            "opening the streak from the level pane performs an arrival nobody asked for",
        )
        assertEquals(AnimationType.SlideOutToRight, route.popExit)
        assertFalse(
            route.coversParent,
            "a plain push wants the screen underneath to travel with it",
        )
    }

    @Test
    fun theCeremonyStillReportsItsVisit() {
        // The presentation arguments are forwarded through `TrackableRoute`, and
        // dropping the tracking key on the way through would be invisible: the
        // page still opens and the counter in `AppData` simply stops moving.
        assertEquals("streakVisits", StreakRoute(celebrating = 2).trackingKey)
        assertEquals("streakVisits", StreakRoute().trackingKey)
    }

    @Test
    fun theNavigatorReadsTheSlideUpBackOffTheRoute() {
        // `App.kt` does not hold the object this test built. It decodes the base
        // [Route] out of the backstack entry's arguments, so an entrance that is
        // computed but not carried would be correct here and gone in the app.
        val decoded = json.decodeFromString<Route>(
            json.encodeToString(StreakRoute(celebrating = 2)),
        )

        assertEquals(AnimationType.SlideUp, decoded.enter)
        assertEquals(AnimationType.SlideDown, decoded.popExit)
        assertTrue(decoded.coversParent)
    }

    private val json = Json { ignoreUnknownKeys = true }
}
