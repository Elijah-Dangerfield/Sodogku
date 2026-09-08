package com.sodogku.features.paywall.impl

import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.ProductIds
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.billing.StoreBilling
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject

/**
 * The Pro sheet.
 *
 * The price is asked of the store rather than hardcoded or configured — it is
 * set per storefront, and SPEC 4.3 says so outright. Until the store answers,
 * the button reads "Get Pro" with no number in it, which is the honest state:
 * a placeholder price that turns out to be wrong in a currency we did not think
 * about is worse than no price.
 */
@Inject
class PaywallViewModel(
    private val entitlements: Entitlements,
    private val store: StoreBilling,
) : SEAViewModel<PaywallState, PaywallEvent, PaywallAction>(
    initialStateArg = PaywallState(),
) {

    init {
        takeAction(PaywallAction.Load)
    }

    override suspend fun handleAction(action: PaywallAction) {
        when (action) {
            is PaywallAction.Load -> action.load()
            is PaywallAction.Buy -> action.buy()
            is PaywallAction.Restore -> action.restore()
            is PaywallAction.MessageShown -> action.updateState { it.copy(message = null) }
            is PaywallAction.Dismiss -> sendEvent(PaywallEvent.Dismiss)
        }
    }

    private suspend fun PaywallAction.Load.load() {
        updateState { it.copy(isPro = entitlements.isPro.value) }
        val price = Catching { store.priceLabel(ProductIds.pro) }
            .logOnFailure { "Could not read the Pro price" }
            .getOrNull()
        updateState { it.copy(priceLabel = price) }
    }

    private suspend fun PaywallAction.Buy.buy() {
        updateState { it.copy(isWorking = true) }
        val outcome = entitlements.purchasePro()
        // `state` lags `updateState` by a dispatch, so the outcome travels as a
        // value rather than being read back off the state that was just written.
        updateState { it.copy(isWorking = false, message = outcome.toMessage()) }
        if (outcome is PurchaseOutcome.Success || outcome is PurchaseOutcome.AlreadyOwned) {
            updateState { it.copy(isPro = true) }
            sendEvent(PaywallEvent.Purchased)
        }
    }

    private suspend fun PaywallAction.Restore.restore() {
        updateState { it.copy(isWorking = true) }
        val outcome = entitlements.restore()
        updateState { it.copy(isWorking = false, message = outcome.toMessage()) }
        if (outcome is RestoreOutcome.Restored) {
            updateState { it.copy(isPro = true) }
            sendEvent(PaywallEvent.Purchased)
        }
    }
}

private fun PurchaseOutcome.toMessage(): PaywallMessage? = when (this) {
    PurchaseOutcome.Success -> null
    PurchaseOutcome.AlreadyOwned -> PaywallMessage.AlreadyPro
    // The player pressed back. Telling them what they already know is noise.
    PurchaseOutcome.Cancelled -> null
    PurchaseOutcome.Unavailable -> PaywallMessage.StoreUnavailable
    is PurchaseOutcome.Failed -> PaywallMessage.PurchaseFailed
}

private fun RestoreOutcome.toMessage(): PaywallMessage? = when (this) {
    RestoreOutcome.Restored -> null
    RestoreOutcome.NothingToRestore -> PaywallMessage.NothingToRestore
    is RestoreOutcome.Failed -> PaywallMessage.StoreUnreachable
}

/**
 * What to tell the player, as a case rather than a string — the screen owns the
 * copy, because copy is a string resource and a view model that held one would
 * need a Compose resource reader in it.
 */
enum class PaywallMessage {
    AlreadyPro,
    PurchaseFailed,
    StoreUnavailable,
    NothingToRestore,
    StoreUnreachable,
}

data class PaywallState(
    /** As the store formats it. Null until it answers, or forever if it cannot. */
    val priceLabel: String? = null,
    /** A purchase or restore is in flight; both buttons are held. */
    val isWorking: Boolean = false,
    val isPro: Boolean = false,
    val message: PaywallMessage? = null,
)

sealed interface PaywallEvent {
    data object Dismiss : PaywallEvent
    data object Purchased : PaywallEvent
}

sealed interface PaywallAction {
    data object Load : PaywallAction
    data object Buy : PaywallAction
    data object Restore : PaywallAction
    data object Dismiss : PaywallAction
    data object MessageShown : PaywallAction
}
