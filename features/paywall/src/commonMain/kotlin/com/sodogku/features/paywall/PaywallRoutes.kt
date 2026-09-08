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
