package com.sodogku.libraries.leaderboards

import kotlinx.coroutines.flow.StateFlow

/**
 * Where the platform thinks the player stands. Four values, because collapsing
 * them to a boolean loses the one distinction the UI needs.
 */
enum class GameServicesStatus {

    /** Not resolved yet. The state for the first second or so of every launch. */
    Unknown,

    /**
     * Nobody is signed in, but there is a way in, and it is worth offering a
     * leaderboard entry point that opens it. On iOS the platform has handed us a
     * sign-in screen and we are holding it; on Android it means Play Games
     * answered "not signed in", which is also what a player who has never made a
     * Games profile looks like. See [GameServices.presentDashboard] for why the
     * way in is only ever taken when the player asks.
     */
    SignInRequired,

    /** Signed in. Submissions will be accepted. */
    Authenticated,

    /**
     * There is no route to a leaderboard on this device or in this state:
     * restricted by Screen Time, blocked by parental controls, unavailable in
     * the region, no Play services on the device at all, or the platform simply
     * reported an error. Terminal for the launch as far as callers are
     * concerned, though Android re-asks on each foreground and may move off it.
     */
    Unavailable,
}

/** What one submission attempt did. Never an exception. */
enum class SubmitResult {

    /** The platform accepted the value. */
    Submitted,

    /** Nobody is signed in. Distinct from [Failed] because it is not a fault. */
    NotAuthenticated,

    /** Offline, rejected, timed out, or the SDK threw. All the same from here. */
    Failed,
}

/**
 * The thin platform seam under [Leaderboards]: sign in, send a number, show the
 * platform's own screen. No policy, no retries, no memory.
 *
 * Everything policy-shaped (what is worth sending, what happens to a score
 * earned before sign-in resolved, what a failure costs the player) lives above
 * this in `RealLeaderboards`, in common code, where it is the same on both
 * platforms and testable without a signed-in device.
 *
 * **iOS binding** is `GameCenterServices` in `:libraries:leaderboards:impl/iosMain`,
 * written in Kotlin against the GameKit platform bindings. It is not a Swift
 * object handed through `IosAppComponent` the way `AdNetwork` and `StoreBilling`
 * are, because those wrap SDKs Kotlin cannot reach: GoogleMobileAds is a third
 * party framework and StoreKit 2 is Swift-only. GameKit is an ordinary
 * Objective-C system framework, so Kotlin/Native already has full bindings for
 * it and a Swift shim would only add a file to keep in sync.
 *
 * **Android binding** is `PlayGamesServices` in the same impl module's
 * `androidMain`, over `play-services-games-v2`.
 *
 * Note the calls take a [Leaderboard] rather than an id string. Each store mints
 * its own ids ([Leaderboard.appleId], [Leaderboard.playId]) and picking between
 * them is the one thing an implementation of this interface always knows and a
 * caller above it never does.
 *
 * Implementations must not throw. The layer above treats a thrown exception and
 * a refusal identically, and it swallows both, but a seam that throws makes
 * every call site a place a crash can come from.
 */
interface GameServices {

    /** Re-emits whenever the platform changes its mind, which it can, mid-session. */
    val status: StateFlow<GameServicesStatus>

    /**
     * Begins authentication. Returns immediately, idempotent, safe from any
     * thread. The answer arrives on [status].
     *
     * Called once at app start, which is what Apple asks for. It must be
     * invisible: nothing may be presented, and no dialog may appear, on the
     * strength of a call the player did not make.
     *
     * "Once" is about the call, not about the answer. GameKit keeps a handler
     * and re-fires it; Play Games has no such callback, so the Android binding
     * re-asks on every foreground instead. Either way [status] can move at any
     * point in the session and nothing here needs calling again.
     */
    fun startAuthentication()

    /** Sends one value to one board. Suspends until the platform answers. */
    suspend fun submit(board: Leaderboard, value: Long): SubmitResult

    /**
     * When the window [board] is currently accepting scores into opened, as
     * epoch millis, or `null` when the platform will not say.
     *
     * This is the whole of the recurring-board design. A weekly board needs a
     * value scoped to the week, the week is defined in App Store Connect, and
     * this is the app asking rather than guessing. Nothing is cached: the answer
     * is only used to build one submission, and a stale one would attribute a
     * score to a window that has already closed. That is a network round trip
     * per submission, alongside the submission's own.
     *
     * `null` is the answer for a classic board, for a signed-out player, for
     * every board on Android, and for any failure. Every one of them ends the
     * same way — the value is not computed and nothing is sent — which keeps
     * this on the right side of the fail-open rule despite being the one call
     * here that returns something.
     *
     * Android is `null` for a structural reason rather than a missing feature.
     * Play Games has no recurring board and no occurrence to have a start: it
     * derives daily and weekly views of a single board from the times scores
     * were submitted. There is nothing to ask, so this asks nothing.
     */
    suspend fun currentWindowStart(board: Leaderboard): Long?

    /**
     * Shows the platform's own leaderboard screen, focused on [board] when one
     * is given.
     *
     * This is also the only place a sign-in screen is ever presented. When the
     * platform hands us one during authentication we keep it rather than showing
     * it, because a full screen sign-in that arrives on its own at launch is an
     * interruption the player did not ask for, and this game needs nothing from
     * Game Center to be played. Asking to see a leaderboard is the moment the
     * player has asked. Play Games works the same way: its automatic sign-in is
     * silent and `signIn()`, the call that puts a sheet on screen, is only ever
     * made from here.
     */
    suspend fun presentDashboard(board: Leaderboard?)
}
