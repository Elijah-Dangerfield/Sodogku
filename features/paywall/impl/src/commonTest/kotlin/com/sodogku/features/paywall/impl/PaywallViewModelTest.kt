package com.sodogku.features.paywall.impl

import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PaywallRequest
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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
        val vm = paywall(store = FakeStore(price = "4,99 €"))

        assertEquals("4,99 €", vm.state.priceLabel)
    }

    @Test
    fun anUnreachableStoreLeavesThePriceBlankRatherThanGuessing() = runUnitTest {
        val vm = paywall(store = FakeStore(price = null))

        assertNull(
            vm.state.priceLabel,
            "A placeholder price in the wrong currency is worse than no price",
        )
    }

    @Test
    fun aSuccessfulPurchaseFlipsToProAndClosesTheSheet() = runUnitTest {
        val vm = paywall(entitlements = FakeEntitlements(purchaseResult = PurchaseOutcome.Success))

        vm.takeAction(PaywallAction.Buy)

        assertTrue(vm.state.isPro)
        assertFalse(vm.state.isWorking)
        assertNull(vm.state.message, "Nothing to say; the game behind the sheet is already Pro")
        assertEquals(PaywallEvent.Purchased, vm.eventFlow.first())
    }

    @Test
    fun aCancelledPurchaseSaysNothing() = runUnitTest {
        val vm = paywall(entitlements = FakeEntitlements(purchaseResult = PurchaseOutcome.Cancelled))

        vm.takeAction(PaywallAction.Buy)

        assertNull(vm.state.message, "They pressed back. Telling them so is noise.")
        assertFalse(vm.state.isWorking)
    }

    @Test
    fun aFailedPurchaseSaysSoAndUnlocksTheButton() = runUnitTest {
        val vm = paywall(entitlements = FakeEntitlements(purchaseResult = PurchaseOutcome.Failed("network")))

        vm.takeAction(PaywallAction.Buy)

        assertEquals(PaywallMessage.PurchaseFailed, vm.state.message)
        assertFalse(vm.state.isWorking, "A stuck spinner is how a failed purchase becomes a stuck screen")
    }

    @Test
    fun restoreOutcomesMapToDistinctMessages() = runUnitTest {
        val restored = paywall(entitlements = FakeEntitlements(restoreResult = RestoreOutcome.Restored))
            .also { it.takeAction(PaywallAction.Restore) }

        val nothing = paywall(entitlements = FakeEntitlements(restoreResult = RestoreOutcome.NothingToRestore))
            .also { it.takeAction(PaywallAction.Restore) }

        val failed = paywall(entitlements = FakeEntitlements(restoreResult = RestoreOutcome.Failed("x")))
            .also { it.takeAction(PaywallAction.Restore) }

        assertNull(restored.state.message)
        assertTrue(restored.state.isPro)
        assertEquals(PaywallMessage.NothingToRestore, nothing.state.message)
        assertEquals(PaywallMessage.StoreUnreachable, failed.state.message)
        assertFalse(failed.state.isPro)
    }

    // ------------------------------------------------------------------
    // Standing in for an ad that could not be served
    // ------------------------------------------------------------------

    @Test
    fun theDwellCountsItselfDownAndThenLetsGo() = runUnitTest {
        val vm = paywall(dwellSeconds = 3)

        assertEquals(3, vm.state.secondsUntilDismissible, "the sheet opens already counting")

        // Past the first tick rather than exactly on it: `advanceTimeBy` runs
        // what is scheduled strictly before the new time, so landing on the
        // boundary would assert against a tick that has not fired yet.
        advanceTimeBy(1500.milliseconds)
        assertEquals(2, vm.state.secondsUntilDismissible)

        advanceTimeBy(5.seconds)
        assertEquals(0, vm.state.secondsUntilDismissible, "and it stops at zero rather than going negative")
    }

    @Test
    fun theCloseControlIsHeldForTheDwellAndWorksAfterIt() = runUnitTest {
        val vm = paywall(dwellSeconds = 3)
        val seen = mutableListOf<PaywallEvent>()
        val collector = launch { vm.eventFlow.toList(seen) }

        vm.takeAction(PaywallAction.Dismiss)
        advanceTimeBy(1500.milliseconds)

        assertEquals(
            emptyList<PaywallEvent>(),
            seen,
            "one second in, the sheet is still standing in for the ad",
        )

        advanceTimeBy(5.seconds)
        vm.takeAction(PaywallAction.Dismiss)
        advanceUntilIdle()

        assertEquals(listOf<PaywallEvent>(PaywallEvent.Dismiss), seen)
        collector.cancel()
    }

    @Test
    fun everySheetThatIsNotStandingInForAnAdClosesImmediately() = runUnitTest {
        // Settings' "Go Pro", a third strike, the offline block's Pro button:
        // none of them asked for a dwell, and a lock they never requested would
        // be a paywall you cannot leave.
        val vm = paywall(dwellSeconds = 0)

        vm.takeAction(PaywallAction.Dismiss)

        assertEquals(0, vm.state.secondsUntilDismissible)
        assertEquals(PaywallEvent.Dismiss, vm.eventFlow.first())
    }

    @Test
    fun buyingIsNeverHeldBehindTheDwell() = runUnitTest {
        // The countdown runs in its own coroutine precisely so it cannot sit in
        // front of the one action this screen exists to accept.
        val vm = paywall(
            dwellSeconds = 5,
            entitlements = FakeEntitlements(purchaseResult = PurchaseOutcome.Success),
        )

        vm.takeAction(PaywallAction.Buy)

        assertTrue(vm.state.isPro)
        assertTrue(vm.state.secondsUntilDismissible > 0, "and the dwell was still running at the time")
    }

    @Test
    fun theDiagnosticLineIsForDebugBuildsOnly() = runUnitTest {
        val request = PaywallRequest.AdStandIn(placementId = "booster_grant", reason = "no_fill")

        val debug = request.standInNote(isDebug = true)

        assertTrue("booster_grant" in debug, "a stand-in with no placement in it diagnoses nothing")
        assertTrue("no_fill" in debug)
        assertEquals("", request.standInNote(isDebug = false), "it is an error kind, not copy")
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

    private fun paywall(
        dwellSeconds: Int = 0,
        entitlements: Entitlements = FakeEntitlements(),
        store: StoreBilling = FakeStore(),
    ) = PaywallViewModel(
        trigger = TestTrigger,
        dwellSeconds = dwellSeconds,
        entitlements = entitlements,
        store = store,
    )

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

        override suspend fun purchasePro(trigger: String?): PurchaseOutcome = purchaseResult.also {
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

    private companion object {
        /** Any id from `paywall.triggers`; the tests only care that it reaches the event. */
        const val TestTrigger = "offline_block"
    }
}
