package com.sodogku.libraries.billing

import kotlinx.coroutines.flow.Flow

/**
 * The moments that may open the paywall, matching the ids in `paywall.triggers`.
 *
 * [id] is the config key *and* the `iap.paywall_shown` attribute, so "which
 * trigger converts" is one Loki query rather than a join. A trigger the server
 * enables that this build has no moment for is simply never requested; a moment
 * the server has disabled is refused here.
 */
enum class PaywallTrigger(val id: String) {
    /** Offline grace spent, play is blocked. The highest-intent moment in the app. */
    OfflineBlock("offline_block"),

    /** Third strike, next to the rewarded continue. */
    ContinueLevel("continue_level"),

    /** Skip offered after repeated failures, next to the rewarded skip. */
    SkipLevel("skip_level"),

    /**
     * The player opened it themselves from Settings. Never gated and never
     * capped — refusing to sell to someone who walked into the shop would be
     * absurd, and it is not an interruption to cap.
     */
    Direct("direct"),
}

/** What the app should put on screen. */
sealed interface PaywallRequest {
    /** The Pro sheet, as an offer. Dismissible. */
    data class Offer(val trigger: PaywallTrigger) : PaywallRequest

    /**
     * The offline block screen: reconnect or go Pro. Not an offer — it is the
     * one deliberate block in the app, and it only fires when the OS reports no
     * network at all and `ads.offlineGrace*` is spent.
     */
    data object OfflineBlock : PaywallRequest
}

/**
 * The one place that decides whether a paywall may be shown, and the bus the
 * navigator listens on.
 *
 * It exists because the moments that want the paywall (an ad gate, an offline
 * block) are nowhere near the navigation graph, and because "may we show it"
 * has three inputs — `paywall.triggers`, `paywall.sessionCap`, and whether the
 * player is already Pro — that should be answered once rather than at each call
 * site.
 */
interface PaywallCoordinator {

    /** What to show. Hot; the navigator is the only subscriber. */
    val requests: Flow<PaywallRequest>

    /**
     * Ask for the paywall at [trigger]. Returns whether it was accepted, so a
     * caller can fall back to its own UI (a rewarded ad, usually) when the
     * offer is capped or disabled. Refused for a Pro player, for a trigger
     * missing from `paywall.triggers`, and once `paywall.sessionCap` is spent.
     */
    fun requestOffer(trigger: PaywallTrigger): Boolean

    /**
     * Put the offline block on screen. Not subject to the session cap: it is
     * not an offer that can nag, it is the state the player is in. Refused when
     * `paywall.offlineBlockEnabled` is off or the player is Pro (Pro is
     * unlimited offline play, SPEC 5.1).
     */
    fun requestOfflineBlock(): Boolean
}
