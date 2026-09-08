package com.sodogku.libraries.billing.impl

import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PaywallRequest
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.PaywallOfflineBlockEnabled
import com.sodogku.libraries.config.values.PaywallSessionCap
import com.sodogku.libraries.config.values.PaywallTriggers
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RealPaywallCoordinator] — the three refusals (`paywall.triggers`,
 * `paywall.sessionCap`, already Pro) and the one thing that is deliberately not
 * capped.
 *
 * Every refusal is paired with the allowed case, because a coordinator that
 * simply returned `false` would pass any single-sided assertion about capping.
 *
 * NOT covered here: what the paywall screen looks like or what happens after
 * the player taps buy — that is `PaywallViewModel` in `:features:paywall:impl`.
 */
class RealPaywallCoordinatorTest : CoroutineTest() {

    private val sessions = FakeSessionTracker()

    @Test
    fun anOfferIsCappedPerSessionAndTheCapResetsWithTheSession() = runUnitTest {
        val coordinator = coordinator(paywall = mapOf("sessionCap" to 2))

        assertTrue(coordinator.requestOffer(PaywallTrigger.ContinueLevel))
        assertTrue(coordinator.requestOffer(PaywallTrigger.SkipLevel))
        assertFalse(
            coordinator.requestOffer(PaywallTrigger.ContinueLevel),
            "A player who declined twice has answered",
        )

        sessions.roll()

        assertTrue(coordinator.requestOffer(PaywallTrigger.ContinueLevel))
    }

    @Test
    fun aTriggerMissingFromConfigIsRefusedWhileTheOthersStillWork() = runUnitTest {
        val coordinator = coordinator(
            paywall = mapOf("triggers" to listOf("continue_level")),
        )

        assertTrue(coordinator.requestOffer(PaywallTrigger.ContinueLevel))
        assertFalse(coordinator.requestOffer(PaywallTrigger.SkipLevel))
    }

    @Test
    fun aProPlayerIsNeverSoldToAgain() = runUnitTest {
        val coordinator = coordinator(isPro = true)

        assertFalse(coordinator.requestOffer(PaywallTrigger.ContinueLevel))
        assertFalse(coordinator.requestOffer(PaywallTrigger.Direct))
        assertFalse(coordinator.requestOfflineBlock(), "Pro is unlimited offline play")
    }

    @Test
    fun theShopIsNeverClosedToSomeoneWhoWalkedIntoIt() = runUnitTest {
        // `Direct` is Settings' "Go Pro". Gating it on a live-ops trigger list
        // or a session cap would make that button silently do nothing.
        val coordinator = coordinator(
            paywall = mapOf("triggers" to emptyList<String>(), "sessionCap" to 1),
        )

        repeat(3) { assertTrue(coordinator.requestOffer(PaywallTrigger.Direct)) }
        assertFalse(coordinator.requestOffer(PaywallTrigger.ContinueLevel))
    }

    @Test
    fun theOfflineBlockIsNotAnOfferAndIsNotCapped() = runUnitTest {
        val coordinator = coordinator(paywall = mapOf("sessionCap" to 1))

        assertTrue(coordinator.requestOffer(PaywallTrigger.ContinueLevel))
        repeat(3) {
            assertTrue(
                coordinator.requestOfflineBlock(),
                "The block is the state the player is in, not a nag to ration",
            )
        }
    }

    @Test
    fun theOfflineBlockCanBeSwitchedOffEntirely() = runUnitTest {
        val coordinator = coordinator(paywall = mapOf("offlineBlockEnabled" to false))

        assertFalse(coordinator.requestOfflineBlock())
        assertTrue(coordinator.requestOffer(PaywallTrigger.ContinueLevel), "and nothing else changes")
    }

    @Test
    fun acceptedRequestsReachTheBus() = runUnitTest {
        val coordinator = coordinator()
        val seen = mutableListOf<PaywallRequest>()
        val collector = launch { coordinator.requests.toList(seen) }

        coordinator.requestOffer(PaywallTrigger.SkipLevel)
        coordinator.requestOfflineBlock()

        assertEquals(
            listOf(PaywallRequest.Offer(PaywallTrigger.SkipLevel), PaywallRequest.OfflineBlock),
            seen,
        )
        collector.cancel()
    }

    private fun coordinator(
        paywall: Map<String, Any> = emptyMap(),
        isPro: Boolean = false,
    ): RealPaywallCoordinator {
        val map: AppConfigMap = TestConfigMap(mapOf("paywall" to paywall))
        return RealPaywallCoordinator(
            entitlements = StaticEntitlements(isPro),
            sessionTracker = sessions,
            triggers = PaywallTriggers(map),
            sessionCap = PaywallSessionCap(map),
            offlineBlockEnabled = PaywallOfflineBlockEnabled(map),
        )
    }

    private class StaticEntitlements(isPro: Boolean) : Entitlements {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(isPro)
        override suspend fun purchasePro(): PurchaseOutcome = PurchaseOutcome.Unavailable
        override suspend fun restore(): RestoreOutcome = RestoreOutcome.NothingToRestore
    }
}
