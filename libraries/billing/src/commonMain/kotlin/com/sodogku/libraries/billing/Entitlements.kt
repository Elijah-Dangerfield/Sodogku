package com.sodogku.libraries.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * What buying Pro can do. Sealed per operation rather than thrown, like the rest of the app.
 *
 * [name] is what `iap.purchase_result` reports as its `outcome`, and it is a
 * literal here rather than `::class.simpleName` because R8 renames these classes
 * in a Play build. A release `mapping.txt` has `PurchaseOutcome$Success -> ta.l`,
 * so `simpleName` was arriving in Loki as `l` while `paywall-conversion.json`
 * filtered on `Success`. The board read zero on Android with Play Console showing
 * sales, and nothing anywhere errored.
 *
 * Keep these spellings and the dashboard's in step. A `-keepnames` rule would
 * also work and is worse: the contract belongs where somebody renaming one of
 * these will see it, not in a ProGuard file nobody opens.
 */
sealed interface PurchaseOutcome {
    val name: String

    data object Success : PurchaseOutcome {
        override val name = "Success"
    }

    data object Cancelled : PurchaseOutcome {
        override val name = "Cancelled"
    }

    data object AlreadyOwned : PurchaseOutcome {
        override val name = "AlreadyOwned"
    }

    data object Unavailable : PurchaseOutcome {
        override val name = "Unavailable"
    }

    data class Failed(val kind: String) : PurchaseOutcome {
        override val name = "Failed"
    }
}

/** [name] is the `outcome` on `iap.restore_result`; see [PurchaseOutcome]. */
sealed interface RestoreOutcome {
    val name: String

    data object Restored : RestoreOutcome {
        override val name = "Restored"
    }

    data object NothingToRestore : RestoreOutcome {
        override val name = "NothingToRestore"
    }

    data class Failed(val kind: String) : RestoreOutcome {
        override val name = "Failed"
    }
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
