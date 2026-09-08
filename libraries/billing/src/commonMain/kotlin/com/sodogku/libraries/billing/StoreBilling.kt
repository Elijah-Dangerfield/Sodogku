@file:OptIn(ExperimentalObjCName::class)

package com.sodogku.libraries.billing

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The store's answer to "does this device own the product".
 *
 * [Unknown] is the whole reason this is three-valued. A network failure, a
 * signed-out Play account or a StoreKit hiccup all mean *we could not ask*, and
 * a two-valued answer would collapse that into "not owned" — which is how a
 * paying customer starts seeing ads on a train. Only [NotOwned] is allowed to
 * clear the cached entitlement.
 */
@ObjCName("StoreOwnership", exact = true)
enum class StoreOwnership {
    Owned,
    NotOwned,
    Unknown,
}

/** How a purchase attempt ended, flattened for the Swift side. */
@ObjCName("StorePurchaseResult", exact = true)
enum class StorePurchaseResult {
    Purchased,
    Cancelled,
    AlreadyOwned,

    /** Billing is unavailable on this device: no Play Services, parental controls, sandbox off. */
    Unavailable,
    Failed,
}

/** A result plus a machine-readable failure kind for `iap.purchase_result`. */
@ObjCName("StorePurchaseOutcome", exact = true)
class StorePurchaseOutcome(
    val result: StorePurchaseResult,
    val errorKind: String? = null,
)

/**
 * The thin platform seam under [Entitlements]: talk to Play or StoreKit, say
 * what the store said. No caching, no policy, no state.
 *
 * **Android binding** is `PlayStoreBilling` in `:libraries:billing:impl/androidMain`
 * (Play Billing). **iOS binding** is `IOSStoreBilling` (Swift, StoreKit 2) handed
 * to the graph through `IosAppComponent`.
 *
 * Implementations must not throw — a store that is down has to arrive here as
 * [StoreOwnership.Unknown] or [StorePurchaseResult.Failed], because the layer
 * above treats a thrown exception and a "no" identically and only one of those
 * is safe.
 */
@ObjCName("StoreBilling", exact = true)
interface StoreBilling {

    /**
     * Ask the store whether [productId] is owned right now.
     *
     * On Android this is `queryPurchasesAsync`, which reads Play's local cache
     * and refreshes it — it works offline for a device that has seen the
     * purchase before. On iOS it is `Transaction.currentEntitlements`, which is
     * likewise locally verified.
     */
    suspend fun ownership(productId: String): StoreOwnership

    /** Runs the purchase flow. Must be safe to call when the product is already owned. */
    suspend fun purchase(productId: String): StorePurchaseOutcome

    /**
     * The explicit restore Apple requires a visible control for. On Android
     * there is nothing to restore — `queryPurchasesAsync` already is the
     * restore — so the Play implementation forwards to [ownership].
     */
    suspend fun restore(productId: String): StoreOwnership

    /**
     * Localised price as the store formats it ("$4.99", "4,99 €"), or null when
     * the store could not be reached. Never hardcode a price: it is set per
     * storefront and SPEC 4.3 keeps it out of config for the same reason.
     */
    suspend fun priceLabel(productId: String): String?
}

/**
 * The store product. One non-consumable, SPEC 5.1.
 *
 * Same string on both stores by design — Play calls it a managed product, Apple
 * a non-consumable, and keeping the ids identical means one constant rather than
 * a platform branch on every call site. It is **not** in remote config: SPEC 4.4
 * lists the product id as a store operation.
 */
@ObjCName("ProductIds", exact = true)
object ProductIds {
    const val pro: String = "sodogku_pro"
}
