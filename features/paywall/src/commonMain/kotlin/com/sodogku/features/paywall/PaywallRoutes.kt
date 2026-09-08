package com.sodogku.features.paywall

import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The Pro offer.
 *
 * [trigger] is the `paywall.triggers` id that opened it, carried on the route
 * rather than looked up so the screen and `iap.paywall_shown` agree about which
 * moment sold the purchase. Slides up and back down: it is an interruption of
 * whatever the player was doing, and it should read as one that goes away.
 *
 * A `class`, never a `data object` — an arg-less object route SIGSEGVs the iOS
 * navigator at navigate time.
 */
@Serializable
class PaywallRoute(
    val trigger: String = "direct",
    /**
     * Seconds the sheet's own close controls stay locked, for the one case where
     * this screen is standing in for a rewarded ad that could not be served.
     * Zero, meaning no lock at all, everywhere else, which is every other
     * caller.
     */
    val dwellSeconds: Int = 0,
    /**
     * Why the stand-in fired, as `placement · reason`. Rendered only in a debug
     * build, and empty in every release one. It is diagnostic, not copy: without
     * it, an ad that silently failed to appear and an ad that was never
     * requested look identical from the outside.
     */
    val standInNote: String = "",
) : Route(
    enter = AnimationType.SlideUp,
    exit = AnimationType.SlideDown,
    popExit = AnimationType.SlideDown,
    // Settings stays exactly where it was while this slides up over it.
    coversParent = true,
)

/**
 * The offline block: the one screen in this app that stops a player.
 *
 * It only appears when the OS reports no network at all *and* the offline grace
 * is spent *and* `paywall.offlineBlockEnabled` is on *and* the player is not
 * Pro. Fades rather than slides, because it is a state the app is in rather
 * than a place the player navigated to.
 */
@Serializable
class OfflineBlockRoute : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
)
