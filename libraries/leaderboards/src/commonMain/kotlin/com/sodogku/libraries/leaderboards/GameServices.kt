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
     * The platform handed us a sign-in screen. Nobody is signed in, but there is
     * a way in, and it is worth offering a leaderboard entry point that presents
     * it. See [GameServices.presentDashboard] for why we hold that screen rather
     * than showing it.
     */
    SignInRequired,

    /** Signed in. Submissions will be accepted. */
    Authenticated,

    /**
     * There is no route to a leaderboard on this device or in this state:
     * restricted by Screen Time, blocked by parental controls, unavailable in
     * the region, or the platform simply reported an error. Terminal for the
     * launch as far as callers are concerned.
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
 * **Android binding** is `NoGameServices`, which is inert. Play Games is the
 * equivalent and is out of scope.
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
     */
    fun startAuthentication()

    /** Sends one value to one board. Suspends until the platform answers. */
    suspend fun submit(leaderboardId: String, value: Long): SubmitResult

    /**
     * Shows the platform's own leaderboard screen, focused on [leaderboardId]
     * when one is given.
     *
     * This is also the only place a sign-in screen is ever presented. When the
     * platform hands us one during authentication we keep it rather than showing
     * it, because a full screen sign-in that arrives on its own at launch is an
     * interruption the player did not ask for, and this game needs nothing from
     * Game Center to be played. Asking to see a leaderboard is the moment the
     * player has asked.
     */
    suspend fun presentDashboard(leaderboardId: String?)
}
