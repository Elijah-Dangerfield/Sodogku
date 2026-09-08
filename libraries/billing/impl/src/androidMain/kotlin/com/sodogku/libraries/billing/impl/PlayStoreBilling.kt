package com.sodogku.libraries.billing.impl

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.sodogku.libraries.billing.StoreBilling
import com.sodogku.libraries.billing.StoreOwnership
import com.sodogku.libraries.billing.StorePurchaseOutcome
import com.sodogku.libraries.billing.StorePurchaseResult
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.flowroutines.DispatcherProvider
import com.sodogku.libraries.sodogku.ActivityProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Play Billing.
 *
 * SPEC 5.2 says "Play Billing 7"; this is 8.x, because Play stopped accepting
 * new releases on 7 and the two API differences that matter
 * (`QueryProductDetailsResult` instead of a bare list, and the mandatory
 * `PendingPurchasesParams`) are both here.
 *
 * ## Ownership is answerable offline, and that is the point
 *
 * `queryPurchasesAsync` reads Play's own on-device cache. A device that has
 * seen the purchase before answers `Owned` with no network, which is most of
 * what makes the true-until-proven-false entitlement work — this class only has
 * to be honest about the case where it genuinely could not ask, and return
 * [StoreOwnership.Unknown] there.
 *
 * `SERVICE_UNAVAILABLE`, `SERVICE_DISCONNECTED`, `SERVICE_TIMEOUT` and
 * `NETWORK_ERROR` are all "could not ask". `BILLING_UNAVAILABLE` is *not*
 * mapped to `NotOwned` either: it means this device cannot do billing at all
 * (no Play Services, a managed profile), and a device that cannot ask is not a
 * device that answered no.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PlayStoreBilling(
    context: Context,
    private val activityProvider: ActivityProvider,
    private val dispatchers: DispatcherProvider,
) : StoreBilling {

    private val logger = KLog.withTag("PlayBilling")
    private val connectLock = Mutex()

    /**
     * Purchases arrive on a listener, not on the call that started the flow, so
     * the suspended [purchase] picks them up here. Buffered rather than
     * rendezvous because Play also delivers purchases we never asked for —
     * a pending card payment clearing, a purchase made on another device — and
     * dropping one on the floor would leave the player paid up and un-entitled
     * until the next foreground refresh.
     */
    private val purchaseUpdates = Channel<PurchaseUpdate>(capacity = Channel.BUFFERED)

    private val listener = PurchasesUpdatedListener { result, purchases ->
        purchaseUpdates.trySend(PurchaseUpdate(result.responseCode, purchases.orEmpty()))
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    override suspend fun ownership(productId: String): StoreOwnership {
        if (!connect()) return StoreOwnership.Unknown

        val response = withTimeoutOrNull(QUERY_TIMEOUT) {
            suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { cont ->
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ) { result, purchases ->
                    if (cont.isActive) cont.resume(result to purchases.orEmpty())
                }
            }
        } ?: return StoreOwnership.Unknown

        val (result, purchases) = response
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            logger.w { "queryPurchases failed: ${result.responseCode} ${result.debugMessage}" }
            return StoreOwnership.Unknown
        }

        val owned = purchases.firstOrNull {
            productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        } ?: return StoreOwnership.NotOwned

        acknowledgeIfNeeded(owned)
        return StoreOwnership.Owned
    }

    override suspend fun restore(productId: String): StoreOwnership = ownership(productId)

    override suspend fun purchase(productId: String): StorePurchaseOutcome {
        if (!connect()) return StorePurchaseOutcome(StorePurchaseResult.Unavailable, "no_billing_connection")

        val activity = activityProvider.currentActivity()
            ?: return StorePurchaseOutcome(StorePurchaseResult.Failed, "no_foreground_activity")

        val details = productDetails(productId)
            ?: return StorePurchaseOutcome(StorePurchaseResult.Unavailable, "product_not_found")

        // Anything the listener buffered before now belongs to an earlier
        // flow (or to a purchase that completed while we were backgrounded).
        // Draining first is what stops a stale OK being read as this flow's
        // answer.
        while (purchaseUpdates.tryReceive().isSuccess) Unit

        val launch = withContext(dispatchers.main) {
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .build(),
                        ),
                    )
                    .build(),
            )
        }
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            return launch.toPurchaseOutcome()
        }

        val update = withTimeoutOrNull(PURCHASE_TIMEOUT) { purchaseUpdates.receive() }
            ?: return StorePurchaseOutcome(StorePurchaseResult.Failed, "purchase_timeout")

        return when (update.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val bought = update.purchases.firstOrNull {
                    productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (bought == null) {
                    // The only normal way here is a PENDING purchase — a cash
                    // or slow-card payment Play will confirm later. Not owned
                    // yet, not failed either; the foreground refresh picks it
                    // up when it clears.
                    StorePurchaseOutcome(StorePurchaseResult.Failed, "purchase_pending")
                } else {
                    acknowledgeIfNeeded(bought)
                    StorePurchaseOutcome(StorePurchaseResult.Purchased)
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                StorePurchaseOutcome(StorePurchaseResult.Cancelled)

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                StorePurchaseOutcome(StorePurchaseResult.AlreadyOwned)

            else -> BillingResult.newBuilder().setResponseCode(update.responseCode).build().toPurchaseOutcome()
        }
    }

    override suspend fun priceLabel(productId: String): String? {
        val details = productDetails(productId) ?: return null
        return details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.formattedPrice
            ?: details.oneTimePurchaseOfferDetails?.formattedPrice
    }

    private suspend fun productDetails(productId: String): ProductDetails? {
        if (!connect()) return null

        val response = withTimeoutOrNull(QUERY_TIMEOUT) {
            suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { cont ->
                client.queryProductDetailsAsync(
                    QueryProductDetailsParams.newBuilder()
                        .setProductList(
                            listOf(
                                QueryProductDetailsParams.Product.newBuilder()
                                    .setProductId(productId)
                                    .setProductType(BillingClient.ProductType.INAPP)
                                    .build(),
                            ),
                        )
                        .build(),
                ) { result, details ->
                    if (cont.isActive) cont.resume(result to details.productDetailsList)
                }
            }
        } ?: return null

        val (result, list) = response
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            logger.w { "queryProductDetails failed: ${result.responseCode} ${result.debugMessage}" }
            return null
        }
        return list.firstOrNull { it.productId == productId }
    }

    /**
     * Play refunds an unacknowledged purchase after three days. Doing it on
     * every sighting rather than only after a purchase covers the case that
     * actually bites: the app was killed between the payment and the
     * acknowledgement.
     */
    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        Catching {
            withTimeoutOrNull(QUERY_TIMEOUT) {
                suspendCancellableCoroutine { cont ->
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build(),
                    ) { result -> if (cont.isActive) cont.resume(result) }
                }
            }
        }.logOnFailure { "Acknowledge failed; Play will refund this purchase in three days" }
    }

    private suspend fun connect(): Boolean = connectLock.withLock {
        if (client.isReady) return true
        val result = withTimeoutOrNull(CONNECT_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            if (cont.isActive) cont.resume(billingResult.responseCode)
                        }

                        override fun onBillingServiceDisconnected() {
                            // enableAutoServiceReconnection handles the retry;
                            // a disconnect mid-setup just means this attempt
                            // gets no answer and the timeout resolves it.
                        }
                    },
                )
            }
        }
        if (result != BillingClient.BillingResponseCode.OK) {
            logger.w { "Billing connect failed: $result" }
        }
        result == BillingClient.BillingResponseCode.OK
    }

    private data class PurchaseUpdate(val responseCode: Int, val purchases: List<Purchase>)

    private fun BillingResult.toPurchaseOutcome(): StorePurchaseOutcome = when (responseCode) {
        BillingClient.BillingResponseCode.USER_CANCELED ->
            StorePurchaseOutcome(StorePurchaseResult.Cancelled)

        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            StorePurchaseOutcome(StorePurchaseResult.AlreadyOwned)

        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        ->
            StorePurchaseOutcome(StorePurchaseResult.Unavailable, "billing_$responseCode")

        else -> StorePurchaseOutcome(StorePurchaseResult.Failed, "billing_$responseCode")
    }

    private companion object {
        val CONNECT_TIMEOUT = 15.seconds
        val QUERY_TIMEOUT = 20.seconds

        /**
         * A backstop, not a deadline: the flow always calls back, but a Play
         * dialog the user leaves open behind a locked screen would otherwise
         * hold a coroutine forever.
         */
        val PURCHASE_TIMEOUT = 10.minutes
    }
}
