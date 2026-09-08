package com.sodogku.libraries.billing.impl

import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.billing.StoreOwnership
import com.sodogku.libraries.billing.StorePurchaseOutcome
import com.sodogku.libraries.billing.StorePurchaseResult
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.sodogku.AppData
import com.sodogku.libraries.sodogku.AppEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RealEntitlements], whose entire job is the asymmetry between "no" and "we
 * could not ask".
 *
 * The tests below are written as pairs on purpose: for every case that *should*
 * clear the entitlement there is a neighbouring case that must not, so an
 * implementation that treated [StoreOwnership.Unknown] as `NotOwned` (or that
 * never cleared anything at all) fails one side or the other.
 *
 * NOT covered here: Play Billing and StoreKit themselves. Those live behind
 * `StoreBilling` precisely so this logic is reachable without a store, and the
 * only way to test them is a sandbox purchase on a device — written down in
 * `BUILD-PLAN.md` as an acceptance step rather than pretended at here.
 */
class RealEntitlementsTest : CoroutineTest() {

    private val store = FakeStoreBilling()
    private val bus = FakeAppEventBus()

    @Test
    fun anUnreachableStoreLeavesAPayingCustomerPro() = runUnitTest {
        val cache = FakeAppCache(AppData(isProEntitled = true))
        store.ownership = StoreOwnership.Unknown

        val entitlements = entitlements(cache)

        assertTrue(entitlements.isPro.value, "A store we could not reach is not a store that said no")
        assertTrue(cache.get().isProEntitled, "and the cache must not be rewritten either")
        assertTrue(store.ownershipCalls > 0, "we did actually ask")
    }

    @Test
    fun aStoreThatThrowsLeavesAPayingCustomerPro() = runUnitTest {
        val cache = FakeAppCache(AppData(isProEntitled = true))
        store.throwOnEverything = true

        assertTrue(entitlements(cache).isPro.value)
    }

    @Test
    fun anExplicitNotOwnedClearsTheEntitlement() = runUnitTest {
        val cache = FakeAppCache(AppData(isProEntitled = true))
        store.ownership = StoreOwnership.NotOwned

        val entitlements = entitlements(cache)

        assertFalse(entitlements.isPro.value, "A refund has to be able to land")
        assertFalse(cache.get().isProEntitled)
    }

    @Test
    fun theCachedEntitlementIsVisibleBeforeTheStoreAnswers() = runUnitTest {
        // The dozen screens that read `isPro` render on the first frame. If the
        // flow started false and only turned true after a round trip, a Pro
        // player would see one frame of the free build every launch.
        val cache = FakeAppCache(AppData(isProEntitled = true))
        store.ownership = StoreOwnership.Owned

        assertTrue(entitlements(cache).isPro.value)
    }

    @Test
    fun aSuccessfulPurchasePersistsAcrossARestart() = runUnitTest {
        val cache = FakeAppCache()
        store.ownership = StoreOwnership.NotOwned
        val entitlements = entitlements(cache)

        store.purchaseOutcome = StorePurchaseOutcome(StorePurchaseResult.Purchased)
        assertEquals(PurchaseOutcome.Success, entitlements.purchasePro())
        assertTrue(entitlements.isPro.value)

        // A relaunch with the store unreachable: disk is the only source left.
        store.ownership = StoreOwnership.Unknown
        assertTrue(entitlements(cache).isPro.value)
    }

    @Test
    fun alreadyOwnedIsAGrantNotAFailure() = runUnitTest {
        val cache = FakeAppCache()
        store.ownership = StoreOwnership.NotOwned
        val entitlements = entitlements(cache)

        store.purchaseOutcome = StorePurchaseOutcome(StorePurchaseResult.AlreadyOwned)

        assertEquals(PurchaseOutcome.AlreadyOwned, entitlements.purchasePro())
        assertTrue(entitlements.isPro.value, "The player owns it; the only bug would be not saying so")
    }

    @Test
    fun aCancelledPurchaseChangesNothing() = runUnitTest {
        val cache = FakeAppCache()
        store.ownership = StoreOwnership.NotOwned
        val entitlements = entitlements(cache)

        store.purchaseOutcome = StorePurchaseOutcome(StorePurchaseResult.Cancelled)

        assertEquals(PurchaseOutcome.Cancelled, entitlements.purchasePro())
        assertFalse(entitlements.isPro.value)
    }

    @Test
    fun restoreGrantsProAndSurvivesAReinstall() = runUnitTest {
        // A reinstall is an empty cache plus a store that remembers.
        val cache = FakeAppCache()
        store.ownership = StoreOwnership.NotOwned
        val entitlements = entitlements(cache)
        assertFalse(entitlements.isPro.value)

        store.restoreOwnership = StoreOwnership.Owned

        assertEquals(RestoreOutcome.Restored, entitlements.restore())
        assertTrue(entitlements.isPro.value)
        assertTrue(cache.get().isProEntitled)
        assertEquals(1, store.restoreCalls)
    }

    @Test
    fun aFailedRestoreDoesNotClearAnExistingEntitlement() = runUnitTest {
        val cache = FakeAppCache(AppData(isProEntitled = true))
        store.ownership = StoreOwnership.Unknown
        val entitlements = entitlements(cache)

        store.restoreOwnership = StoreOwnership.Unknown

        assertEquals(RestoreOutcome.Failed("store_unreachable"), entitlements.restore())
        assertTrue(entitlements.isPro.value, "A restore that could not reach the store is not a refund")
    }

    @Test
    fun aDeliberateRestoreThatFindsNothingDoesClearIt() = runUnitTest {
        // The one moment a "no" is trustworthy: the player asked, the store
        // answered. Leaving a stale `true` behind would make the button lie.
        val cache = FakeAppCache(AppData(isProEntitled = true))
        store.ownership = StoreOwnership.Unknown
        val entitlements = entitlements(cache)
        assertTrue(entitlements.isPro.value)

        store.restoreOwnership = StoreOwnership.NotOwned

        assertEquals(RestoreOutcome.NothingToRestore, entitlements.restore())
        assertFalse(entitlements.isPro.value)
    }

    @Test
    fun aForegroundReAsksTheStore() = runUnitTest {
        // A purchase made on another device, or a refund, only ever arrives
        // while the app is backgrounded. With no accounts and no server push,
        // this is the whole of the sync story.
        val cache = FakeAppCache()
        store.ownership = StoreOwnership.NotOwned
        val entitlements = entitlements(cache)
        val callsAtBoot = store.ownershipCalls

        store.ownership = StoreOwnership.Owned
        bus.dispatch(AppEvent.OnForeground(isColdBoot = false))

        assertTrue(store.ownershipCalls > callsAtBoot)
        assertTrue(entitlements.isPro.value)
    }

    private fun entitlements(cache: FakeAppCache) = RealEntitlements(
        store = store,
        appCache = cache,
        appScope = AppCoroutineScope(dispatchers),
        appEventsProvider = { fakeAppEvents(bus) },
    )
}
