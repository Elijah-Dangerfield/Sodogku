package com.sodogku.libraries.billing.impl

import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PaywallRequest
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.PaywallAdStandInEnabled
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
        assertFalse(
            coordinator.requestAdStandIn("booster_grant", "no_fill"),
            "Pro never sees an ad, so there is no empty slot to fill",
        )
    }

    @Test
    fun theAdStandInIsNotOnTheTriggerListAndDoesNotNeedToBe() = runUnitTest {
        // `paywall.triggers` names the moments we chose to sell at. This is not
        // one of them: it is the replacement for content the player was promised
        // and we could not deliver. Gating it on that list would mean the
        // fallback shipped switched off.
        val coordinator = coordinator(paywall = mapOf("triggers" to emptyList<String>()))

        assertFalse(coordinator.requestOffer(PaywallTrigger.ContinueLevel))
        assertTrue(coordinator.requestAdStandIn("booster_grant", "no_fill"))
    }

    @Test
    fun theAdStandInSpendsTheSameSessionCapAsAnOffer() = runUnitTest {
        // It is a pitch, so it can nag. A thin-inventory afternoon must not turn
        // every booster into a sales screen.
        val coordinator = coordinator(paywall = mapOf("sessionCap" to 2))

        assertTrue(coordinator.requestAdStandIn("booster_grant", "no_fill"))
        assertTrue(coordinator.requestOffer(PaywallTrigger.ContinueLevel))
        assertFalse(coordinator.requestAdStandIn("booster_grant", "no_fill"))
        assertFalse(coordinator.requestOffer(PaywallTrigger.SkipLevel), "one shared cap, not two")

        sessions.roll()

        assertTrue(coordinator.requestAdStandIn("skip_level", "offline"))
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
        coordinator.requestAdStandIn("booster_grant", "no_fill")

        assertEquals(
            listOf(
                PaywallRequest.Offer(PaywallTrigger.SkipLevel),
                PaywallRequest.OfflineBlock,
                PaywallRequest.AdStandIn("booster_grant", "no_fill", dwellSeconds = 5),
            ),
            seen,
        )
        collector.cancel()
    }

    @Test
    fun theStandInNeverCostsMoreThanTheAdItReplaces() = runUnitTest {
        // Five seconds is the skip delay every rewarded format already trains
        // players to expect, so it reads as familiar. But the number that has to
        // hold is the relation, not the value: if the fallback ever ran longer
        // than the video it stands in for, a bad fill rate would become
        // something to hope for rather than something to fix.
        val dwell = PaywallRequest.AdStandIn.DEFAULT_DWELL_SECONDS

        assertTrue(dwell > 0, "a dwell of zero is a sheet nobody reads")
        assertTrue(
            dwell < ShortestRewardedVideoSeconds,
            "$dwell seconds is not shorter than the ad it replaces",
        )
    }

    @Test
    fun theStandInCanBeSwitchedOffWithoutSilencingRealOffers() {
        // The point of a separate key. Before it existed the only way to stop
        // the stand-in was `paywall.sessionCap = 0`, which also stops every
        // offer we deliberately chose to make.
        val off = coordinator(paywall = mapOf("adStandInEnabled" to false))

        val stoodIn = off.requestAdStandIn(placementId = "booster_grant", reason = "no_fill")
        val offered = off.requestOffer(PaywallTrigger.Direct)

        assertFalse(stoodIn, "the stand-in fired with its switch off")
        assertTrue(offered, "turning the stand-in off also silenced a real offer")
    }

    @Test
    fun theStandInIsOnByDefault() {
        // A kill switch defaulting to off is a feature nobody ships.
        val on = coordinator()

        assertTrue(
            on.requestAdStandIn(placementId = "booster_grant", reason = "no_fill"),
            "the stand-in did not fire with no config set",
        )
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
            adStandInEnabled = PaywallAdStandInEnabled(map),
        )
    }

    private class StaticEntitlements(isPro: Boolean) : Entitlements {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(isPro)
        override suspend fun purchasePro(trigger: String?): PurchaseOutcome = PurchaseOutcome.Unavailable
        override suspend fun restore(): RestoreOutcome = RestoreOutcome.NothingToRestore
    }

    private companion object {
        /** The short end of a rewarded video. AdMob's own creatives run 15 to 30. */
        const val ShortestRewardedVideoSeconds = 15
    }
}
