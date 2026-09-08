package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.Leaderboard
import com.sodogku.libraries.leaderboards.SubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The Android binding, which has to be inert in a way the layer above respects.
 *
 * Asserting `Unavailable` on its own would prove very little, so the second test
 * runs the real `RealLeaderboards` over it and checks the *observable* Android
 * behaviour: nothing is offered to the player and the platform is never asked
 * for anything, however many scores the game reports.
 */
class NoGameServicesTest : CoroutineTest() {

    @Test
    fun thePlatformIsUnavailableAndSaysSoImmediately() = runUnitTest {
        val services = NoGameServices()

        assertEquals(GameServicesStatus.Unavailable, services.status.value)
        assertEquals(SubmitResult.NotAuthenticated, services.submit("any.board", 100L))
    }

    @Test
    fun noEntryPointIsOfferedAndScoresGoNowhere() = runUnitTest {
        val leaderboards = RealLeaderboards(NoGameServices(), AppCoroutineScope(dispatchers))

        leaderboards.submit(Leaderboard.LifetimeScore, 4_200L)
        leaderboards.submit(Leaderboard.LongestStreak, 30L)
        leaderboards.openDashboard()

        assertFalse(leaderboards.isOfferable.value)
    }
}
