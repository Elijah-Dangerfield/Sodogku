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
 * **The recurring board**, which is neither of those. Its value does not exist
 * until the platform says when the week opened, so the assertions there are
 * about *what it was priced against* as much as what was sent — a weekly board
 * fed the lifetime total submits a perfectly plausible number and ranks
 * everybody wrong.
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
    // The recurring board
    // ------------------------------------------------------------------

    @Test
    fun theWeeklyValueIsPricedAgainstTheWindowThePlatformReports() = runUnitTest {
        services.windowStart = MONDAY
        var askedAbout: Long? = null

        leaderboards().submitWindowed(Leaderboard.WeeklyScore) { start ->
            askedAbout = start
            900L
        }

        assertEquals(MONDAY, askedAbout, "the window has to come from the platform, not from here")
        assertEquals(Leaderboard.WeeklyScore.id to 900L, services.submissions.single())
    }

    @Test
    fun aPlatformWithNoWindowIsNeverEvenAskedForAValue() = runUnitTest {
        // Android and every signed-out player. The lambda reads the score ledger
        // off disk, so this is also the assertion that Android does no work for
        // a board it does not have.
        services.windowStart = null
        var asked = false

        leaderboards().submitWindowed(Leaderboard.WeeklyScore) {
            asked = true
            900L
        }

        assertFalse(asked)
        assertTrue(services.submissions.isEmpty())
    }

    @Test
    fun aWeeklyScoreEarnedBeforeSignInIsPricedWhenAuthenticationLands() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        services.windowStart = MONDAY
        RealLeaderboards(services, AppCoroutineScope(dispatchers))
            .submitWindowed(Leaderboard.WeeklyScore) { 900L }
        assertTrue(services.submissions.isEmpty())

        services.becomes(GameServicesStatus.Authenticated)

        assertEquals(Leaderboard.WeeklyScore.id to 900L, services.submissions.single())
    }

    @Test
    fun aHeldWeeklyScoreIsPricedAgainstTheWindowItIsFinallySentIn() = runUnitTest {
        // Why the hold is a lambda rather than a number. The value was earned
        // under one window and sent under another, and the one that counts is
        // the one Game Center will file it into.
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        services.windowStart = MONDAY
        val pricedAgainst = mutableListOf<Long>()
        RealLeaderboards(services, AppCoroutineScope(dispatchers))
            .submitWindowed(Leaderboard.WeeklyScore) { start ->
                pricedAgainst += start
                900L
            }

        services.windowStart = NEXT_MONDAY
        services.becomes(GameServicesStatus.Authenticated)

        assertEquals(listOf(NEXT_MONDAY), pricedAgainst)
    }

    @Test
    fun aNewWindowLetsASmallerScoreThroughAgain() = runUnitTest {
        // The reset, and the only part of it that happens on the device: the
        // memory of what was already accepted is scoped to a window. Without
        // this a big Sunday would hide the player from the whole next week,
        // because 400 is not an improvement on 9,000.
        val leaderboards = leaderboards()
        services.windowStart = MONDAY
        leaderboards.submitWindowed(Leaderboard.WeeklyScore) { 9_000L }

        services.windowStart = NEXT_MONDAY
        leaderboards.submitWindowed(Leaderboard.WeeklyScore) { 400L }

        assertEquals(
            listOf(
                Leaderboard.WeeklyScore.id to 9_000L,
                Leaderboard.WeeklyScore.id to 400L,
            ),
            services.submissions,
        )
    }

    @Test
    fun aScoreThatHasNotMovedInsideTheSameWindowIsNotSentTwice() = runUnitTest {
        val leaderboards = leaderboards()
        services.windowStart = MONDAY
        leaderboards.submitWindowed(Leaderboard.WeeklyScore) { 9_000L }
        leaderboards.submitWindowed(Leaderboard.WeeklyScore) { 9_000L }

        assertEquals(1, services.submissions.size)
    }

    @Test
    fun aRejectedWeeklyScoreIsRetriedOnTheNextBoard() = runUnitTest {
        // The same value twice on purpose, for the reason
        // `aRejectedValueIsRetriedOnTheNextSubmit` gives: a larger one would be
        // sent again whether or not the refusal was noticed.
        val leaderboards = leaderboards()
        services.windowStart = MONDAY
        services.result = SubmitResult.Failed
        leaderboards.submitWindowed(Leaderboard.WeeklyScore) { 900L }
        assertEquals(1, services.submissions.size)

        services.result = SubmitResult.Submitted
        leaderboards.submitWindowed(Leaderboard.WeeklyScore) { 900L }

        assertEquals(
            listOf(
                Leaderboard.WeeklyScore.id to 900L,
                Leaderboard.WeeklyScore.id to 900L,
            ),
            services.submissions,
        )
    }

    @Test
    fun aRefusedWeeklyScoreIsStillHeldForTheNextFlush() = runUnitTest {
        // The other half of a retry, and the one no call site can rescue: the
        // player finished one board and did not finish another. GameKit signs
        // people out and back in mid-session, so a second flush arrives without
        // a second board, and dropping the hold on a refusal means the week's
        // points are gone until the player plays again.
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        services.windowStart = MONDAY
        services.result = SubmitResult.Failed
        RealLeaderboards(services, AppCoroutineScope(dispatchers))
            .submitWindowed(Leaderboard.WeeklyScore) { 900L }

        services.becomes(GameServicesStatus.Authenticated)
        assertEquals(1, services.submissions.size, "the first attempt was refused")

        services.result = SubmitResult.Submitted
        services.becomes(GameServicesStatus.SignInRequired)
        services.becomes(GameServicesStatus.Authenticated)

        assertEquals(
            listOf(
                Leaderboard.WeeklyScore.id to 900L,
                Leaderboard.WeeklyScore.id to 900L,
            ),
            services.submissions,
        )
    }

    @Test
    fun aWeekWorthNothingIsNotSent() = runUnitTest {
        services.windowStart = MONDAY

        leaderboards().submitWindowed(Leaderboard.WeeklyScore) { 0L }

        assertTrue(services.submissions.isEmpty())
    }

    @Test
    fun aLedgerThatThrowsCostsTheSubmissionAndNothingElse() = runUnitTest {
        val leaderboards = leaderboards()
        services.windowStart = MONDAY
        leaderboards.submitWindowed(Leaderboard.WeeklyScore) { error("the disk is on fire") }

        leaderboards.submitWindowed(Leaderboard.WeeklyScore) { 900L }

        assertEquals(Leaderboard.WeeklyScore.id to 900L, services.submissions.single())
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

    private companion object {
        /** Two window starts a week apart, as Game Center would report them. */
        const val MONDAY = 1_757_376_000_000L
        const val NEXT_MONDAY = MONDAY + 7L * 24 * 60 * 60 * 1000
    }
}
