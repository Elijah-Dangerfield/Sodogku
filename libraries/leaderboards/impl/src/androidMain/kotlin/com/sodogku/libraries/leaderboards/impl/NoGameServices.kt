package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.leaderboards.GameServices
import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.SubmitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Android has no leaderboards. Google Play Games is the equivalent and is out of
 * scope, so this reports [GameServicesStatus.Unavailable] from construction and
 * never changes its mind.
 *
 * That is enough for the whole feature to be silent here: `RealLeaderboards`
 * reads the status before it sends anything, `isOfferable` stays false so no
 * entry point is ever drawn, and held values are collected and never flushed.
 *
 * When Play Games does arrive it replaces this file and nothing else. The seam
 * above is already the right shape for it: `GamesSignInClient` answers
 * [startAuthentication], `LeaderboardsClient.submitScore(id, value)` answers
 * [submit], and `getLeaderboardIntent` answers [presentDashboard]. What it does
 * need is a second id per board, because Play mints its own, and `Leaderboard`
 * says where that goes.
 *
 * [currentWindowStart] is the one that will not port straight across. Play has
 * no recurring boards; it buckets a single board into daily, weekly and all-time
 * spans by submission time, so the weekly value there is the same number the
 * lifetime board already gets and the window question does not arise. Answering
 * `null` and letting the layer above drop the windowed submission is closer to
 * right than inventing a start date would be.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoGameServices : GameServices {

    private val state = MutableStateFlow(GameServicesStatus.Unavailable)

    override val status: StateFlow<GameServicesStatus> = state.asStateFlow()

    override fun startAuthentication() = Unit

    override suspend fun submit(leaderboardId: String, value: Long): SubmitResult =
        SubmitResult.NotAuthenticated

    /**
     * No platform, so no window, so nothing above ever asks a windowed board for
     * a value. That is what keeps the score ledger unread on Android rather than
     * read and thrown away.
     */
    override suspend fun currentWindowStart(leaderboardId: String): Long? = null

    override suspend fun presentDashboard(leaderboardId: String?) = Unit
}
