package com.sodogku.libraries.leaderboards

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
}
