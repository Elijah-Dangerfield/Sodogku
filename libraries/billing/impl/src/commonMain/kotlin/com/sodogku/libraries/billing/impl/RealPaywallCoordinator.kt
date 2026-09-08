package com.sodogku.libraries.billing.impl

import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PaywallCoordinator
import com.sodogku.libraries.billing.PaywallRequest
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.config.values.PaywallAdStandInEnabled
import com.sodogku.libraries.config.values.PaywallOfflineBlockEnabled
import com.sodogku.libraries.config.values.PaywallSessionCap
import com.sodogku.libraries.config.values.PaywallTriggers
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.sodogku.SessionTracker
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Decides whether the paywall may be shown, then puts the request on a bus the
 * navigator in `:features:paywall:impl` collects.
 *
 * A bus rather than a direct `Router.navigate` because the callers are a
 * library and a repository — neither should know a route exists, and a
 * navigation call from an ad gate is how a library ends up depending on a
 * feature. The feature owns its own destinations; this owns the policy.
 *
 * **Refusals are silent and return `false`**, so a caller can fall back to
 * whatever it was going to do anyway. Nothing in this class can block a player:
 * the worst it does is decline to offer them something.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RealPaywallCoordinator(
    private val entitlements: Entitlements,
    private val sessionTracker: SessionTracker,
    private val triggers: PaywallTriggers,
    private val sessionCap: PaywallSessionCap,
    private val offlineBlockEnabled: PaywallOfflineBlockEnabled,
    private val adStandInEnabled: PaywallAdStandInEnabled,
) : PaywallCoordinator {

    private val logger = KLog.withTag("Paywall")

    // extraBufferCapacity + DROP_OLDEST so `requestOffer` never suspends and
    // never blocks a caller that is mid-ad-gate. A dropped offer is a
    // non-event; a suspended ad gate is a frozen board.
    private val bus = MutableSharedFlow<PaywallRequest>(
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var observedSessionId: Long = 0L
    private var shownThisSession: Int = 0

    override val requests: Flow<PaywallRequest> = bus.asSharedFlow()

    override fun requestOffer(trigger: PaywallTrigger): Boolean {
        if (entitlements.isPro.value) return false

        // The player opened it themselves. Gating the shop on a live-ops list
        // would make "Go Pro" in Settings do nothing, which reads as a bug.
        if (trigger != PaywallTrigger.Direct) {
            if (!triggers.isEnabled(trigger.id)) return false
            if (spentThisSession() >= sessionCap()) return false
            recordShown()
        }

        logger.logEvent("iap.paywall_shown", "trigger" to trigger.id)
        return bus.tryEmit(PaywallRequest.Offer(trigger))
    }

    override fun requestOfflineBlock(): Boolean {
        if (entitlements.isPro.value) return false
        if (!offlineBlockEnabled()) return false

        // Not counted against the session cap. The cap exists to stop an offer
        // becoming a nag, and this is not an offer — it is the state the player
        // is in until they reconnect. Capping it would mean the fourth time
        // they hit it the app simply stopped explaining itself.
        logger.logEvent("iap.paywall_shown", "trigger" to PaywallTrigger.OfflineBlock.id)
        return bus.tryEmit(PaywallRequest.OfflineBlock)
    }

    override fun requestAdStandIn(placementId: String, reason: String): Boolean {
        if (entitlements.isPro.value) return false
        // Its own switch rather than sharing the offline block's or the session
        // cap. This is the one paywall moment nobody here chose: it fires on an
        // ad network having no inventory, which makes it the one most likely to
        // need turning off from a distance and in a hurry.
        if (!adStandInEnabled()) return false

        // Capped like an offer, and unlike the offline block. The block is a
        // state the player is stuck in and has to keep being explained; this is
        // a pitch, and a pitch that arrives after every failed booster ad on a
        // bad fill day is the nag the cap exists for. `paywall.triggers` is
        // deliberately not consulted; see the interface KDoc.
        if (spentThisSession() >= sessionCap()) return false
        recordShown()

        logger.logEvent(
            "iap.paywall_shown",
            "trigger" to PaywallTrigger.AdUnavailable.id,
            "placement" to placementId,
            "reason" to reason,
        )
        return bus.tryEmit(PaywallRequest.AdStandIn(placementId = placementId, reason = reason))
    }

    private fun spentThisSession(): Int {
        rollIfNeeded()
        return shownThisSession
    }

    private fun recordShown() {
        rollIfNeeded()
        shownThisSession++
    }

    private fun rollIfNeeded() {
        val current = sessionTracker.current.id
        if (current != observedSessionId) {
            observedSessionId = current
            shownThisSession = 0
        }
    }

    private companion object {
        const val BUFFER = 4
    }
}
