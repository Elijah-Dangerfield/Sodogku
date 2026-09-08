package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.leaderboards.GameServices
import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.SubmitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one double the leaderboard tests need, per `docs/practices/testing.md`.
 *
 * What it is built to record is *whether the platform was touched at all*.
 * Most of what `RealLeaderboards` does is decline to send things, so almost
 * every assertion is about the contents of [submissions] rather than about a
 * return value, and an implementation that submitted everything unconditionally
 * has to fail them.
 */
class FakeGameServices(
    initial: GameServicesStatus = GameServicesStatus.Authenticated,
) : GameServices {

    private val state = MutableStateFlow(initial)

    override val status: StateFlow<GameServicesStatus> = state

    /** Every (leaderboardId, value) pair that reached the platform, in order. */
    val submissions = mutableListOf<Pair<String, Long>>()

    /** Every dashboard request, including the nulls that mean "no focused board". */
    val dashboards = mutableListOf<String?>()

    var startCalls = 0
        private set

    /** What [submit] answers. Flip it to model a rejection. */
    var result: SubmitResult = SubmitResult.Submitted

    /** Models a platform that throws rather than returning, which must not escape. */
    var throwOnSubmit: Boolean = false

    fun becomes(next: GameServicesStatus) {
        state.value = next
    }

    override fun startAuthentication() {
        startCalls++
    }

    override suspend fun submit(leaderboardId: String, value: Long): SubmitResult {
        submissions += leaderboardId to value
        if (throwOnSubmit) error("Game Center exploded")
        return result
    }

    override suspend fun presentDashboard(leaderboardId: String?) {
        dashboards += leaderboardId
    }
}
