package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.Leaderboard
import com.sodogku.libraries.leaderboards.SubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RealLeaderboards], which owns two behaviours worth separating.
 *
 * **Holding a score earned before sign-in.** Authentication is slow and a board
 * is quick, so the first score of a session routinely happens while Game Center
 * is still deciding who the player is. `heldValueIsSentWhenAuthenticationLands`
 * and `theBestHeldValueWins` are the ones that matter; they are also the two a
 * naive "submit and forget" implementation passes none of.
 *
 * **Not sending.** A lifetime total is resubmitted after every board and only
 * moves on a personal best, so most calls should reach nothing at all. Those
 * assertions are on the *contents* of `services.submissions` rather than on a
 * count alone, because a stub that dropped everything would satisfy a count.
 * `anImprovedValueIsSent` is the companion that kills that stub.
 *
 * NOT covered here: GameKit itself, which no common test can reach (see
 * `GameCenterServices` in `iosMain`, which is compiled and not unit tested), and
 * the ids themselves, which are `LeaderboardTest`.
 */
class RealLeaderboardsTest : CoroutineTest() {

    private val services = FakeGameServices()

    private fun leaderboards() = RealLeaderboards(
        services = services,
        appScope = AppCoroutineScope(dispatchers),
    )

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    @Test
    fun aScoreReachesThePlatformUnderTheBoardsOwnId() = runUnitTest {
        leaderboards().submit(Leaderboard.LifetimeScore, 4_200L)

        assertEquals(1, services.submissions.size)
        assertEquals(Leaderboard.LifetimeScore.id to 4_200L, services.submissions.single())
    }

    @Test
    fun twoBoardsAreSubmittedIndependently() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.submit(Leaderboard.LifetimeScore, 4_200L)
        leaderboards.submit(Leaderboard.LongestStreak, 9L)

        assertEquals(
            listOf(
                Leaderboard.LifetimeScore.id to 4_200L,
                Leaderboard.LongestStreak.id to 9L,
            ),
            services.submissions,
        )
    }

    @Test
    fun anImprovedValueIsSent() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.submit(Leaderboard.LifetimeScore, 500L)
        leaderboards.submit(Leaderboard.LifetimeScore, 900L)

        assertEquals(
            listOf(
                Leaderboard.LifetimeScore.id to 500L,
                Leaderboard.LifetimeScore.id to 900L,
            ),
            services.submissions,
        )
    }

    // ------------------------------------------------------------------
    // Not sending
    // ------------------------------------------------------------------

    @Test
    fun aValueAlreadyAcceptedIsNotSentAgain() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.submit(Leaderboard.LifetimeScore, 500L)
        leaderboards.submit(Leaderboard.LifetimeScore, 500L)
        leaderboards.submit(Leaderboard.LifetimeScore, 400L)

        assertEquals(1, services.submissions.size)
        assertEquals(Leaderboard.LifetimeScore.id to 500L, services.submissions.single())
    }

    @Test
    fun zeroIsNeverSent() = runUnitTest {
        leaderboards().submit(Leaderboard.LifetimeScore, 0L)

        assertTrue(services.submissions.isEmpty())
    }

    @Test
    fun aNegativeValueIsNeverSent() = runUnitTest {
        leaderboards().submit(Leaderboard.LongestStreak, -1L)

        assertTrue(services.submissions.isEmpty())
    }

    // ------------------------------------------------------------------
    // Holding, and the flush
    // ------------------------------------------------------------------

    @Test
    fun nothingIsSentBeforeAuthenticationResolves() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        RealLeaderboards(services, AppCoroutineScope(dispatchers))
            .submit(Leaderboard.LifetimeScore, 4_200L)

        assertTrue(services.submissions.isEmpty())
    }

    @Test
    fun heldValueIsSentWhenAuthenticationLands() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        RealLeaderboards(services, AppCoroutineScope(dispatchers))
            .submit(Leaderboard.LifetimeScore, 4_200L)
        assertTrue(services.submissions.isEmpty())

        services.becomes(GameServicesStatus.Authenticated)

        assertEquals(1, services.submissions.size)
        assertEquals(Leaderboard.LifetimeScore.id to 4_200L, services.submissions.single())
    }

    @Test
    fun theBestHeldValueWins() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.SignInRequired)
        val leaderboards = RealLeaderboards(services, AppCoroutineScope(dispatchers))
        leaderboards.submit(Leaderboard.LifetimeScore, 100L)
        leaderboards.submit(Leaderboard.LifetimeScore, 900L)
        leaderboards.submit(Leaderboard.LifetimeScore, 300L)
        assertTrue(services.submissions.isEmpty())

        services.becomes(GameServicesStatus.Authenticated)

        assertEquals(1, services.submissions.size)
        assertEquals(Leaderboard.LifetimeScore.id to 900L, services.submissions.single())
    }

    @Test
    fun aRejectedValueIsRetriedOnTheNextSubmit() = runUnitTest {
        // The same value twice, on purpose. An improved value would be sent
        // again whether or not the rejection was noticed, so it would pass
        // against an implementation that treated a refusal as an acceptance.
        val leaderboards = leaderboards()
        services.result = SubmitResult.Failed
        leaderboards.submit(Leaderboard.LifetimeScore, 500L)
        assertEquals(1, services.submissions.size)

        services.result = SubmitResult.Submitted
        leaderboards.submit(Leaderboard.LifetimeScore, 500L)

        assertEquals(
            listOf(
                Leaderboard.LifetimeScore.id to 500L,
                Leaderboard.LifetimeScore.id to 500L,
            ),
            services.submissions,
        )
    }

    // ------------------------------------------------------------------
    // Fail open
    // ------------------------------------------------------------------

    @Test
    fun aPlatformThatThrowsIsSurvivedAndTheNextScoreStillLands() = runUnitTest {
        val leaderboards = leaderboards()
        services.throwOnSubmit = true
        leaderboards.submit(Leaderboard.LifetimeScore, 500L)
        assertEquals(1, services.submissions.size)

        services.throwOnSubmit = false
        leaderboards.submit(Leaderboard.LifetimeScore, 500L)

        assertEquals(
            listOf(
                Leaderboard.LifetimeScore.id to 500L,
                Leaderboard.LifetimeScore.id to 500L,
            ),
            services.submissions,
        )
    }

    // ------------------------------------------------------------------
    // The UI signals
    // ------------------------------------------------------------------

    @Test
    fun authenticationIsStartedOnceAtConstruction() = runUnitTest {
        leaderboards()

        assertEquals(1, services.startCalls)
    }

    @Test
    fun anEntryPointIsOfferedOnlyWhenThereIsSomewhereToGo() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        val leaderboards = RealLeaderboards(services, AppCoroutineScope(dispatchers))
        assertFalse(leaderboards.isOfferable.value)

        services.becomes(GameServicesStatus.Unavailable)
        assertFalse(leaderboards.isOfferable.value)

        services.becomes(GameServicesStatus.SignInRequired)
        assertTrue(leaderboards.isOfferable.value)

        services.becomes(GameServicesStatus.Authenticated)
        assertTrue(leaderboards.isOfferable.value)
    }

    @Test
    fun openingTheDashboardForwardsTheBoard() = runUnitTest {
        leaderboards().openDashboard(Leaderboard.LongestStreak)

        assertEquals(listOf<String?>(Leaderboard.LongestStreak.id), services.dashboards)
    }

    @Test
    fun openingTheDashboardWithNoBoardAsksForNoFocus() = runUnitTest {
        leaderboards().openDashboard()

        assertEquals(listOf<String?>(null), services.dashboards)
    }

    @Test
    fun theDashboardIsStillOpenedWhenNobodyIsSignedIn() = runUnitTest {
        // The platform holds a sign-in screen in this state, and presenting it
        // is the only way a signed-out player ever gets onto a board.
        val services = FakeGameServices(initial = GameServicesStatus.SignInRequired)
        RealLeaderboards(services, AppCoroutineScope(dispatchers)).openDashboard()

        assertEquals(listOf<String?>(null), services.dashboards)
    }
}
