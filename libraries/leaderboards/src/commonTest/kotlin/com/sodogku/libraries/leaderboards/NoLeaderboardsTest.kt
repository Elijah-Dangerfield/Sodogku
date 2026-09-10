package com.sodogku.libraries.leaderboards

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * [NoLeaderboards] is the binding in the graph until the impl module is wired,
 * so the property worth pinning is the one the UI reads: it must never claim
 * there is somewhere to go. A call site that draws a leaderboard button on
 * `isOfferable` would otherwise ship a button that does nothing.
 */
class NoLeaderboardsTest {

    @Test
    fun itNeverOffersAnEntryPoint() {
        assertFalse(NoLeaderboards().isOfferable.value)
    }

    @Test
    fun aWindowedScoreIsNeverEvenAskedFor() = runTest {
        // The lambda reads the score ledger off disk. With no platform to file
        // the answer with, running it is work done for a submission that cannot
        // happen — and the call site was told it might never run.
        var asked = false

        NoLeaderboards().submitWindowed(Leaderboard.WeeklyScore) {
            asked = true
            9_000L
        }

        assertFalse(asked)
    }
}
