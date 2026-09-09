package com.sodogku.features.paywall

import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The Pro offer.
 *
 * [trigger] is the `paywall.triggers` id that opened it, carried on the route
 * rather than looked up so the screen and `iap.paywall_shown` agree about which
 * moment sold the purchase.
 *
 * ## Why there are no animations on it
 *
 * It is registered with `bottomSheet<>`, so it is a floating window rather than
 * an entry in the `NavHost`'s own back stack — the enter/exit/popExit an
 * ordinary [Route] carries are never read for it, and the motion belongs to the
 * sheet state instead.
 *
 * They *were* set here, to slide up and back down, and that is where the bug
 * lived: `AnimationType.SlideDown` as an **exit** is `slideOutVertically { -it }`
 * — a slide out through the *top* of the display. So the sheet came up from the
 * bottom and left through the ceiling. Naming animations by where a thing goes
 * rather than by which direction it travels is a trap worth not standing next
 * to; a sheet that owns its own motion cannot fall into it.
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
) : Route()

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
