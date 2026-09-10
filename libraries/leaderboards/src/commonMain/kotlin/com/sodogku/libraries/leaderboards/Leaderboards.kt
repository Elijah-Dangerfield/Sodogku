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
 * the platform is absent when the player is signed out, restricted by Screen
 * Time, offline, in a region without it, or on an Android device with no Play
 * services and no Games profile, and in every one of those cases the only
 * correct behaviour is that the game does not notice. An API that handed back a
 * `Boolean` would eventually get an `if` written around it.
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
     * anywhere. False while authentication is unresolved, and false when the
     * platform has said no.
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
     * The same thing for a board whose window the platform owns.
     *
     * A recurring board keeps the best submission it saw during the occurrence
     * that was open when it arrived, so the value has to be scoped to that
     * occurrence and the caller has no way to know when it began. Hence the
     * lambda: this asks the platform for the window, hands the start to
     * [points], and sends what comes back.
     *
     * [points] may be called late, more than once, or never — never being the
     * normal case for a signed-out player and the *only* case on Android, where
     * Play Games has no recurring board to have a window. A window that cannot
     * be established is a submission that does not happen. Write it as a fresh
     * read rather than a captured number; if it is called a second time, a
     * second answer is the right answer.
     */
    fun submitWindowed(board: Leaderboard, points: WindowedScore)

    /**
     * Opens the platform's leaderboard UI, focused on [board] when one is given.
     * Only ever in response to a player action: this is the one call that can
     * put something on screen.
     */
    fun openDashboard(board: Leaderboard? = null)
}

/**
 * How many points the player has banked since [windowStartMillis].
 *
 * A function rather than a number because the number cannot be worked out until
 * the platform has said when the window opened, and that answer arrives over the
 * network some time after the board was finished.
 */
fun interface WindowedScore {
    suspend fun bankedSince(windowStartMillis: Long): Long
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

    /** [points] is never called: there is no window and so no value to want. */
    override fun submitWindowed(board: Leaderboard, points: WindowedScore) = Unit

    override fun openDashboard(board: Leaderboard?) = Unit
}
