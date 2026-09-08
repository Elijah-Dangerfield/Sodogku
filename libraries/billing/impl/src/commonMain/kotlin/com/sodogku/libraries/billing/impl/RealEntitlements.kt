package com.sodogku.libraries.billing.impl

import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.FreeEntitlements
import com.sodogku.libraries.billing.ProductIds
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.billing.StoreBilling
import com.sodogku.libraries.billing.StoreOwnership
import com.sodogku.libraries.billing.StorePurchaseResult
import com.sodogku.libraries.core.AutoInit
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.sodogku.AppEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The device's Pro entitlement, cached **true until proven false**.
 *
 * There is exactly one interesting rule here and the whole class is built
 * around it: [StoreOwnership.Unknown] changes nothing. A store that is
 * unreachable, a Play account mid-signout, a StoreKit call that timed out —
 * all of those mean *we could not ask*, and the difference between "we could
 * not ask" and "no" is a paying customer watching an ad on a train.
 *
 * Only [StoreOwnership.NotOwned] clears the flag, and it does so because the
 * opposite failure — a refunded purchase that stays Pro forever — is real too,
 * just much rarer and much less annoying.
 *
 * ## No server, no receipt store, no user id
 *
 * `docs/decisions.md`: the app has no accounts. The entitlement is this cache
 * plus the store's own restore, which is why [restore] exists as a visible
 * control in Settings (Apple requires it) and why a reinstall gets Pro back
 * through Play/StoreKit rather than through us.
 *
 * [AutoInit] because the hydrate-from-disk in `init` is the thing that stops
 * the first frame of the game from rendering as a free player for one dispatch.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = Entitlements::class,
    replaces = [FreeEntitlements::class],
)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class RealEntitlements(
    private val store: StoreBilling,
    private val appCache: AppCache,
    private val appScope: AppCoroutineScope,
    appEventsProvider: () -> AppEvents,
) : Entitlements, AutoInit {

    private val logger = KLog.withTag("Entitlements")
    private val proState = MutableStateFlow(false)

    override val isPro: StateFlow<Boolean> = proState.asStateFlow()

    init {
        appScope.launch {
            proState.value = Catching { appCache.get().isProEntitled }
                .logOnFailure { "Could not read the cached entitlement" }
                .getOrElse { false }
            refreshFromStore()
        }

        // A purchase made on another device, a refund, or a Play account
        // switch all land while the app is backgrounded. Re-asking on every
        // foreground is cheap (both stores answer from a local cache) and it is
        // the only signal we get without a server.
        appScope.launch {
            appEventsProvider()
                .live()
                .filterIsInstance<AppEvent.OnForeground>()
                .collect { refreshFromStore() }
        }
    }

    override suspend fun purchasePro(): PurchaseOutcome {
        val outcome = Catching { store.purchase(ProductIds.pro) }
            .logOnFailure { "Purchase threw" }
            .getOrNull()

        val result = when (outcome?.result) {
            StorePurchaseResult.Purchased -> {
                setPro(true)
                PurchaseOutcome.Success
            }

            StorePurchaseResult.AlreadyOwned -> {
                setPro(true)
                PurchaseOutcome.AlreadyOwned
            }

            StorePurchaseResult.Cancelled -> PurchaseOutcome.Cancelled
            StorePurchaseResult.Unavailable -> PurchaseOutcome.Unavailable
            StorePurchaseResult.Failed -> PurchaseOutcome.Failed(outcome.errorKind ?: "store")
            null -> PurchaseOutcome.Failed("threw")
        }

        logger.logEvent(
            "iap.purchase_result",
            "outcome" to result::class.simpleName,
            "error_kind" to (result as? PurchaseOutcome.Failed)?.kind,
        )
        return result
    }

    override suspend fun restore(): RestoreOutcome {
        val ownership = Catching { store.restore(ProductIds.pro) }
            .logOnFailure { "Restore threw" }
            .getOrElse { StoreOwnership.Unknown }

        val result = when (ownership) {
            StoreOwnership.Owned -> {
                setPro(true)
                RestoreOutcome.Restored
            }

            // A deliberate restore is the one moment a "no" is trustworthy: the
            // player asked, the store answered, and leaving a stale `true`
            // behind would make the button lie.
            StoreOwnership.NotOwned -> {
                setPro(false)
                RestoreOutcome.NothingToRestore
            }

            StoreOwnership.Unknown -> RestoreOutcome.Failed("store_unreachable")
        }

        logger.logEvent("iap.restore_result", "outcome" to result::class.simpleName)
        return result
    }

    private suspend fun refreshFromStore() {
        when (
            Catching { store.ownership(ProductIds.pro) }
                .logOnFailure { "Ownership check threw" }
                .getOrElse { StoreOwnership.Unknown }
        ) {
            StoreOwnership.Owned -> setPro(true)
            StoreOwnership.NotOwned -> setPro(false)
            StoreOwnership.Unknown -> logger.d { "Store unreachable; keeping the cached entitlement" }
        }
    }

    private suspend fun setPro(entitled: Boolean) {
        if (proState.value == entitled) return
        proState.value = entitled
        Catching { appCache.update { it.copy(isProEntitled = entitled) } }
            .logOnFailure { "Could not persist the entitlement" }
    }
}
