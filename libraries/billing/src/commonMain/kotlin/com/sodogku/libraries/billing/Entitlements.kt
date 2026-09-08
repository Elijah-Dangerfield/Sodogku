package com.sodogku.libraries.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** What buying Pro can do. Sealed per operation rather than thrown, like the rest of the app. */
sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data object AlreadyOwned : PurchaseOutcome
    data object Unavailable : PurchaseOutcome
    data class Failed(val kind: String) : PurchaseOutcome
}

sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome
    data object NothingToRestore : RestoreOutcome
    data class Failed(val kind: String) : RestoreOutcome
}

/**
 * Whether this device has Sodogku Pro.
 *
 * [isPro] is a `StateFlow` rather than a suspend check because a dozen screens
 * read it and none of them should wait on the store to render. The real
 * implementation caches it as **true until proven false**: if the store is
 * unreachable at launch, a paying customer must not see ads.
 */
interface Entitlements {
    val isPro: StateFlow<Boolean>

    /**
     * [trigger] is the `paywall.triggers` id that opened the sheet, carried into
     * the purchase event.
     *
     * Optional because most callers are test doubles and the QA path, and a
     * required parameter would have made every one of them assert something they
     * do not care about. It is the question SPEC section 14 actually asks of the
     * paywall board — which moment converts — and without it the board can only
     * report that *someone* bought.
     */
    suspend fun purchasePro(trigger: String? = null): PurchaseOutcome

    suspend fun restore(): RestoreOutcome
}

/**
 * The binding until real billing lands (C8): nobody is Pro, and nothing can be
 * bought. Replaced the same way [com.sodogku.libraries.ads.AlwaysRewardingAdGate] is.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class FreeEntitlements : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun purchasePro(trigger: String?): PurchaseOutcome = PurchaseOutcome.Unavailable
    override suspend fun restore(): RestoreOutcome = RestoreOutcome.NothingToRestore
}
