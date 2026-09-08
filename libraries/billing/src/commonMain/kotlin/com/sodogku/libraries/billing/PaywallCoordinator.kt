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

    /**
     * A rewarded ad the player asked for could not be served, so Pro stood in
     * for it. Deliberately not part of `paywall.triggers`; see
     * [PaywallCoordinator.requestAdStandIn]. The id exists so a sale made off
     * the back of a failed ad is distinguishable in `iap.paywall_shown` from one
     * we chose to make.
     */
    AdUnavailable("ad_unavailable"),
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

    /**
     * Pro standing in for a rewarded ad that could not be served.
     *
     * [placementId] and [reason] are the `ads.*` placement id and the SDK's
     * verdict (`no_fill`, `offline`, `not_shown`, or an error kind). Neither is
     * copy: the screen only surfaces them in a debug build, where "why did an ad
     * not appear here" is otherwise invisible.
     */
    data class AdStandIn(
        val placementId: String,
        val reason: String,
        val dwellSeconds: Int = DEFAULT_DWELL_SECONDS,
    ) : PaywallRequest {
        companion object {
            /**
             * How long the sheet's own close controls stay locked.
             *
             * Five seconds because that is the skip delay every rewarded ad
             * format already trains players to expect, so the wait reads as
             * familiar rather than as the app hanging. The number that matters
             * is not five, it is *less than the ad it replaces*: a rewarded
             * video runs fifteen to thirty seconds, so an inventory outage is
             * always cheaper for the player than a full house. A fallback that
             * cost more than the ad would be a reason to hope our fill rate
             * stays bad.
             */
            const val DEFAULT_DWELL_SECONDS: Int = 5
        }
    }
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

    /**
     * Put Pro up in place of a rewarded ad that could not be served.
     *
     * **This can never decide a reward.** By the time it is called the ad gate
     * has already resolved the outcome, and every outcome that reaches here is
     * one that grants. The player has their bones before this returns, whatever
     * it returns.
     *
     * Not gated on `paywall.triggers`, and that is the one thing about it worth
     * arguing over. That list names the moments we *chose* to sell at, and this
     * is not one. It is the replacement for content the player was promised and
     * we failed to deliver, closer in kind to [requestOfflineBlock] than to
     * [requestOffer]. It **is** counted against `paywall.sessionCap`, though, so
     * a thin-inventory afternoon cannot turn every booster into a sales pitch.
     */
    fun requestAdStandIn(placementId: String, reason: String): Boolean
}
