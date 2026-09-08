package com.sodogku.features.paywall.impl

import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.billing.StoreBilling
import com.sodogku.libraries.billing.StoreOwnership
import com.sodogku.libraries.billing.StorePurchaseOutcome
import com.sodogku.libraries.billing.StorePurchaseResult
import com.sodogku.libraries.core.AppState
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PaywallViewModel] and [OfflineBlockViewModel].
 *
 * What is worth asserting here is the mapping from a store outcome to what the
 * player sees, and in particular the two cases that are easy to get backwards:
 * a cancelled purchase says nothing (the player pressed back, they know), while
 * an unreachable store says something (the player pressed buy and nothing
 * happened).
 *
 * NOT covered: the entitlement cache itself, which is `RealEntitlementsTest` in
 * `:libraries:billing:impl`, and the decision to *show* a paywall, which is
 * `RealPaywallCoordinatorTest`.
 */
class PaywallViewModelTest : CoroutineTest() {

    @Test
    fun thePriceComesFromTheStore() = runUnitTest {
        val vm = PaywallViewModel(FakeEntitlements(), FakeStore(price = "4,99 €"))

        assertEquals("4,99 €", vm.state.priceLabel)
    }

    @Test
    fun anUnreachableStoreLeavesThePriceBlankRatherThanGuessing() = runUnitTest {
        val vm = PaywallViewModel(FakeEntitlements(), FakeStore(price = null))

        assertNull(
            vm.state.priceLabel,
            "A placeholder price in the wrong currency is worse than no price",
        )
    }

    @Test
    fun aSuccessfulPurchaseFlipsToProAndClosesTheSheet() = runUnitTest {
        val entitlements = FakeEntitlements(purchaseResult = PurchaseOutcome.Success)
        val vm = PaywallViewModel(entitlements, FakeStore())

        vm.takeAction(PaywallAction.Buy)

        assertTrue(vm.state.isPro)
        assertFalse(vm.state.isWorking)
        assertNull(vm.state.message, "Nothing to say; the game behind the sheet is already Pro")
        assertEquals(PaywallEvent.Purchased, vm.eventFlow.first())
    }

    @Test
    fun aCancelledPurchaseSaysNothing() = runUnitTest {
        val vm = PaywallViewModel(
            FakeEntitlements(purchaseResult = PurchaseOutcome.Cancelled),
            FakeStore(),
        )

        vm.takeAction(PaywallAction.Buy)

        assertNull(vm.state.message, "They pressed back. Telling them so is noise.")
        assertFalse(vm.state.isWorking)
    }

    @Test
    fun aFailedPurchaseSaysSoAndUnlocksTheButton() = runUnitTest {
        val vm = PaywallViewModel(
            FakeEntitlements(purchaseResult = PurchaseOutcome.Failed("network")),
            FakeStore(),
        )

        vm.takeAction(PaywallAction.Buy)

        assertEquals(PaywallMessage.PurchaseFailed, vm.state.message)
        assertFalse(vm.state.isWorking, "A stuck spinner is how a failed purchase becomes a stuck screen")
    }

    @Test
    fun restoreOutcomesMapToDistinctMessages() = runUnitTest {
        val restored = PaywallViewModel(
            FakeEntitlements(restoreResult = RestoreOutcome.Restored),
            FakeStore(),
        ).also { it.takeAction(PaywallAction.Restore) }

        val nothing = PaywallViewModel(
            FakeEntitlements(restoreResult = RestoreOutcome.NothingToRestore),
            FakeStore(),
        ).also { it.takeAction(PaywallAction.Restore) }

        val failed = PaywallViewModel(
            FakeEntitlements(restoreResult = RestoreOutcome.Failed("x")),
            FakeStore(),
        ).also { it.takeAction(PaywallAction.Restore) }

        assertNull(restored.state.message)
        assertTrue(restored.state.isPro)
        assertEquals(PaywallMessage.NothingToRestore, nothing.state.message)
        assertEquals(PaywallMessage.StoreUnreachable, failed.state.message)
        assertFalse(failed.state.isPro)
    }

    @Test
    fun theOfflineBlockDismissesItselfWhenTheNetworkComesBack() = runUnitTest {
        val appState = MutableAppState(offline = true)
        val vm = OfflineBlockViewModel(appState, FakeEntitlements())

        appState.isOffline.value = false

        assertEquals(OfflineBlockEvent.Dismiss, vm.eventFlow.first())
    }

    @Test
    fun theOfflineBlockDismissesItselfWhenThePlayerBuysPro() = runUnitTest {
        val appState = MutableAppState(offline = true)
        val entitlements = FakeEntitlements()
        val vm = OfflineBlockViewModel(appState, entitlements)

        entitlements.setPro(true)

        assertEquals(OfflineBlockEvent.Dismiss, vm.eventFlow.first())
    }

    @Test
    fun retryingWhileStillOfflineSaysSoRatherThanDoingNothing() = runUnitTest {
        val vm = OfflineBlockViewModel(MutableAppState(offline = true), FakeEntitlements())

        vm.takeAction(OfflineBlockAction.Retry)

        assertTrue(vm.state.retriedWhileOffline)
    }

    private class MutableAppState(offline: Boolean) : AppState {
        override val isOffline = MutableStateFlow(offline)
        override val isBlockActive = MutableStateFlow(false)
    }

    private class FakeEntitlements(
        isProNow: Boolean = false,
        private val purchaseResult: PurchaseOutcome = PurchaseOutcome.Cancelled,
        private val restoreResult: RestoreOutcome = RestoreOutcome.NothingToRestore,
    ) : Entitlements {
        private val state = MutableStateFlow(isProNow)
        override val isPro: StateFlow<Boolean> = state

        fun setPro(value: Boolean) {
            state.value = value
        }

        override suspend fun purchasePro(): PurchaseOutcome = purchaseResult.also {
            if (it is PurchaseOutcome.Success || it is PurchaseOutcome.AlreadyOwned) state.value = true
        }

        override suspend fun restore(): RestoreOutcome = restoreResult.also {
            if (it is RestoreOutcome.Restored) state.value = true
        }
    }

    private class FakeStore(private val price: String? = "$4.99") : StoreBilling {
        override suspend fun ownership(productId: String): StoreOwnership = StoreOwnership.NotOwned
        override suspend fun purchase(productId: String): StorePurchaseOutcome =
            StorePurchaseOutcome(StorePurchaseResult.Cancelled)

        override suspend fun restore(productId: String): StoreOwnership = StoreOwnership.NotOwned
        override suspend fun priceLabel(productId: String): String? = price
    }
}
