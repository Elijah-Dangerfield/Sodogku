package com.sodogku.libraries.leaderboards

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The app's whole view of leaderboards.
 *
 * Note what the signatures refuse to offer. Nothing suspends and nothing
 * returns a result, so there is no way to write a call site that waits on a
 * leaderboard or branches on one. That is the fail-open rule made structural:
 * Game Center is absent when the player is signed out, restricted by Screen
 * Time, offline, or in a region without it, and in every one of those cases the
 * only correct behaviour is that the game does not notice. An API that handed
 * back a `Boolean` would eventually get an `if` written around it.
 *
 * The one thing callers may read is [isOfferable], and only to decide whether
 * to draw an entry point.
 *
 * Values are submitted, not accumulated: send the player's current total and
 * the platform keeps the best it has seen. Sending the same number twice is
 * harmless, which is what makes "call this after every board" safe.
 */
interface Leaderboards {

    /**
     * Whether showing the player a way into the leaderboards would lead
     * anywhere. False on Android, false while authentication is unresolved, and
     * false when the platform has said no.
     *
     * True includes [GameServicesStatus.SignInRequired], because opening the
     * dashboard in that state presents the sign-in screen we held back at
     * launch, and that is a useful thing for a tap to do.
     */
    val isOfferable: StateFlow<Boolean>

    /**
     * Records that the player's total for [board] is now [value]. Returns
     * immediately, does the work elsewhere, and cannot fail in any way the
     * caller can observe.
     *
     * Safe to call on every board completion. A value that is not an
     * improvement never reaches the network.
     */
    fun submit(board: Leaderboard, value: Long)

    /**
     * Opens the platform's leaderboard UI, focused on [board] when one is given.
     * Only ever in response to a player action: this is the one call that can
     * put something on screen.
     */
    fun openDashboard(board: Leaderboard? = null)
}

/**
 * The binding when nothing better is in the graph, and the reason a call site
 * can be written before `:libraries:leaderboards:impl` is wired into the app.
 *
 * It lives in this api module rather than an impl for the same reason
 * `AlwaysRewardingAdGate` does: `RealLeaderboards` replaces it by name without
 * anything crossing the impl module boundary.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoLeaderboards : Leaderboards {

    private val offerable = MutableStateFlow(false)

    override val isOfferable: StateFlow<Boolean> = offerable.asStateFlow()

    override fun submit(board: Leaderboard, value: Long) = Unit

    override fun openDashboard(board: Leaderboard?) = Unit
}
