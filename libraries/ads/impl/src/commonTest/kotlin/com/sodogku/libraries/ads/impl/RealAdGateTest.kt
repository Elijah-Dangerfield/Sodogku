package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdFormat
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.AdShowOutcome
import com.sodogku.libraries.ads.AdShowResult
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.AdsEnabled
import com.sodogku.libraries.config.values.AdsInterstitialEveryLevels
import com.sodogku.libraries.config.values.AdsNewUserGraceLevels
import com.sodogku.libraries.config.values.AdsNewUserGraceMinutes
import com.sodogku.libraries.config.values.AdsOfflineGraceLevels
import com.sodogku.libraries.config.values.AdsOfflineGraceMinutes
import com.sodogku.libraries.config.values.AdsRewardedPlacements
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.seconds

/**
 * [RealAdGate], which owns two things worth testing separately.
 *
 * **Fail-open.** `features.md#ads` and `features.md#remote-config`: only a
 * deliberate dismissal withholds a
 * reward. Most of these tests assert on the *pair* — the outcome the caller
 * gets **and** whether the network was asked at all — because an implementation
 * that just returned `Rewarded` unconditionally would satisfy half of them.
 * `dismissedIsTheOnlyOutcomeThatWithholds` is the one that kills that stub.
 *
 * **The frequency gates.** Every one is asserted in both directions (blocked
 * before the threshold, allowed after), for the same reason: a gate that always
 * says no passes a one-sided test.
 *
 * NOT covered here: the AdMob and StoreKit SDKs themselves (no test can reach
 * them from `commonTest`, and the seam exists so nothing above needs to), and
 * the paywall's own session cap, which belongs to `RealPaywallCoordinatorTest`
 * in `:libraries:billing:impl`.
 */
@OptIn(ExperimentalTime::class)
class RealAdGateTest : CoroutineTest() {

    private val network = FakeAdNetwork()
    private val entitlements = FakeEntitlements()
    private val progress = FakeProgressRepository(unlocked = 99)
    private val paywall = FakePaywallCoordinator()
    private val appState = FakeAppState()
    private val cache = FakeAdStateCache()
    private val sessions = FakeSessionTracker()
    private val clock = MutableClock()
    private val elapsed = TestTimeSource()

    // ------------------------------------------------------------------
    // Fail open
    // ------------------------------------------------------------------

    @Test
    fun aProPlayerNeverSeesAnAdAndAlwaysGetsTheReward() = runUnitTest {
        entitlements.setPro(true)

        val rewarded = gate().showRewarded(AdPlacement.ContinueLevel)

        // `GrantedWithoutAd`, not `Rewarded`: both grant, and the difference is
        // that the game can tell a reward it gave away from one the player sat
        // through. Pro is the one free grant nothing thanks the player for.
        assertEquals(RewardOutcome.GrantedWithoutAd("pro"), rewarded)
        assertEquals(emptyList(), network.shown, "Pro was shown an ad")
    }

    @Test
    fun dismissedIsTheOnlyOutcomeThatWithholds() = runUnitTest {
        val withholding = mutableListOf<AdShowResult>()
        val granting = mutableListOf<AdShowResult>()

        AdShowResult.entries.forEach { result ->
            val gate = gate()
            network.outcome = AdShowOutcome(result, errorKind = "kind")
            val outcome = gate.showRewarded(AdPlacement.ContinueLevel)
            if (outcome == RewardOutcome.Dismissed) withholding += result else granting += result
        }

        assertEquals(
            listOf(AdShowResult.Dismissed),
            withholding,
            "Only a deliberate dismissal may withhold a reward. Everything else — " +
                "no fill, offline, an SDK failure, an ad that never showed — pays the player.",
        )
        assertTrue(granting.containsAll(listOf(AdShowResult.NoFill, AdShowResult.Failed, AdShowResult.NotShown)))
    }

    @Test
    fun aThrowingNetworkStillPaysThePlayer() = runUnitTest {
        val exploding = object : com.sodogku.libraries.ads.AdNetwork {
            override suspend fun prepare() = Unit
            override suspend fun show(format: AdFormat): AdShowOutcome = error("SDK exploded")
            override fun preload(format: AdFormat) = Unit
        }

        val outcome = gate(network = exploding).showRewarded(AdPlacement.BoosterGrant)

        assertNotEquals(RewardOutcome.Dismissed, outcome)
    }

    @Test
    fun failureModeLockCannotReachTheRewardPath() = runUnitTest {
        // `ads.failureMode = LOCK` is an A/B arm about what the lose sheet
        // offers. If it could change a reward outcome, an outage or a mistyped
        // config value would lock players out of levels — the exact thing
        // `features.md#remote-config` forbids.
        network.outcome = AdShowOutcome(AdShowResult.Rewarded)
        val withDefault = gate().showRewarded(AdPlacement.ContinueLevel)

        network.outcome = AdShowOutcome(AdShowResult.Rewarded)
        val withLock = gate(ads = mapOf("failureMode" to "LOCK"))
            .showRewarded(AdPlacement.ContinueLevel)

        assertEquals(withDefault, withLock)
        assertEquals(RewardOutcome.Rewarded, withLock)
    }

    @Test
    fun adsDisabledGrantsTheRewardWithoutAskingTheNetwork() = runUnitTest {
        val outcome = gate(ads = mapOf("enabled" to false))
            .showRewarded(AdPlacement.SkipLevel)

        assertEquals(RewardOutcome.GrantedWithoutAd("ads_disabled"), outcome)
        assertEquals(emptyList(), network.shown, "A kill switch must not still request an ad")
    }

    @Test
    fun aDisabledPlacementGrantsTheRewardWithoutAskingTheNetwork() = runUnitTest {
        val outcome = gate(
            ads = mapOf("rewardedPlacements" to mapOf("skip_level" to false)),
        ).showRewarded(AdPlacement.SkipLevel)

        assertEquals(RewardOutcome.GrantedWithoutAd("placement_disabled"), outcome)
        assertEquals(emptyList(), network.shown)
    }


    @Test
    fun consentIsAlwaysRequestedBeforeTheFirstShow() = runUnitTest {
        gate().showRewarded(AdPlacement.BoosterGrant)

        assertEquals(0, network.showCallsBeforePrepare, "An ad request must never beat prepare()")
        assertTrue(network.prepareCalls > 0)
    }

    // ------------------------------------------------------------------
    // New-user grace
    // ------------------------------------------------------------------

    @Test
    fun theNewUserGraceNeedsBothLegsPastBeforeAnAdShows() = runUnitTest {
        val config = mapOf("newUserGraceLevels" to 5, "newUserGraceMinutes" to 5)
        progress.unlocked = 2

        val early = gate(ads = config)
        assertEquals(
            RewardOutcome.GrantedWithoutAd("new_user_grace"),
            early.showRewarded(AdPlacement.BoosterGrant),
        )
        assertEquals(emptyList(), network.shown, "Level 2 is inside the level leg")

        // Past the level leg but not the clock leg.
        progress.unlocked = 6
        gate(ads = config).showRewarded(AdPlacement.BoosterGrant)
        assertEquals(emptyList(), network.shown, "Minute 0 is still inside the wall-clock leg")

        clock.advanceMinutes(6)
        gate(ads = config).showRewarded(AdPlacement.BoosterGrant)
        assertEquals(listOf(AdFormat.Rewarded), network.shown, "Both legs past: the ad should show")
    }

    @Test
    fun theGraceClockStartsAtFirstLaunchNotAtTheFirstAd() = runUnitTest {
        // A player who takes ten minutes to reach level 5 has already had their
        // ad-free five minutes. Starting the clock at the first ad request
        // would hand them another five, which is the grace measuring itself.
        val config = mapOf("newUserGraceLevels" to 1, "newUserGraceMinutes" to 5)
        progress.unlocked = 99

        gate(ads = config)
        clock.advanceMinutes(6)

        gate(ads = config).showRewarded(AdPlacement.BoosterGrant)

        assertEquals(listOf(AdFormat.Rewarded), network.shown)
    }

    // ------------------------------------------------------------------
    // The interstitial floor (SD-148)
    // ------------------------------------------------------------------

    @Test
    fun theInterstitialWaitsForTheFloorAndThenShows() = runUnitTest {
        val gate = gate(ads = mapOf("interstitialEveryLevels" to 3))
        network.outcome = AdShowOutcome(AdShowResult.Dismissed)

        gate.clearLevels(2)
        assertFalse(gate.showInterstitial(AdPlacement.LevelComplete), "two boards is under a floor of three")
        assertEquals(emptyList(), network.shown, "the network was asked before the floor was met")

        gate.clearLevels(1)
        assertTrue(gate.showInterstitial(AdPlacement.LevelComplete), "three boards meets a floor of three")
        assertEquals(listOf(AdFormat.Interstitial), network.shown)
    }

    @Test
    fun anAdOnScreenOfEitherKindResetsTheFloor() = runUnitTest {
        // The ceiling is the same counter as the floor: a player who just
        // watched a continue is not shown an interstitial two taps later.
        val gate = gate(ads = mapOf("interstitialEveryLevels" to 2))
        gate.clearLevels(2)

        network.outcome = AdShowOutcome(AdShowResult.Rewarded)
        gate.showRewarded(AdPlacement.ContinueLevel)

        network.outcome = AdShowOutcome(AdShowResult.Dismissed)
        assertFalse(gate.showInterstitial(AdPlacement.LevelComplete), "a rewarded ad did not reset the floor")
        assertEquals(listOf(AdFormat.Rewarded), network.shown)

        gate.clearLevels(2)
        assertTrue(gate.showInterstitial(AdPlacement.LevelComplete))
        gate.clearLevels(1)
        assertFalse(gate.showInterstitial(AdPlacement.LevelComplete), "an interstitial did not reset its own floor")
    }

    @Test
    fun aRewardedAdClosedEarlyStillCountsAsSeen() = runUnitTest {
        // Withholding the reward is about the reward. The player still sat
        // through part of an ad, and the floor is about ads seen.
        val gate = gate(ads = mapOf("interstitialEveryLevels" to 1))
        gate.clearLevels(1)

        network.outcome = AdShowOutcome(AdShowResult.Dismissed)
        gate.showRewarded(AdPlacement.BoosterGrant)

        assertFalse(gate.showInterstitial(AdPlacement.LevelComplete))
    }

    @Test
    fun anInterstitialThatCouldNotBeServedLeavesTheFloorMet() = runUnitTest {
        // No fill showed nothing, so the next Next level asks again.
        val gate = gate(ads = mapOf("interstitialEveryLevels" to 1))
        gate.clearLevels(1)

        network.outcome = AdShowOutcome(AdShowResult.NoFill)
        assertFalse(gate.showInterstitial(AdPlacement.LevelComplete))

        network.outcome = AdShowOutcome(AdShowResult.Dismissed)
        assertTrue(gate.showInterstitial(AdPlacement.LevelComplete), "a no-fill spent the floor")
    }

    @Test
    fun theInterstitialNeverPutsUpTheProSheet() = runUnitTest {
        val gate = gate(ads = mapOf("interstitialEveryLevels" to 1))
        gate.clearLevels(1)

        network.outcome = AdShowOutcome(AdShowResult.NoFill)
        gate.showInterstitial(AdPlacement.LevelComplete)
        network.outcome = AdShowOutcome(AdShowResult.Dismissed)
        gate.showInterstitial(AdPlacement.LevelComplete)

        assertEquals(emptyList(), paywall.offers, "the one ad nobody asked for tried to sell")
        assertEquals(emptyList(), paywall.standIns, "Pro stood in for an ad the player was never owed")
    }

    @Test
    fun theInterstitialShowsNothingOfflineAndSpendsNoGrace() = runUnitTest {
        appState.isDeviceOffline.value = true
        val gate = gate(ads = mapOf("interstitialEveryLevels" to 1, "offlineGraceLevels" to 0))
        gate.clearLevels(3)

        assertFalse(gate.showInterstitial(AdPlacement.LevelComplete))
        assertEquals(emptyList(), network.shown)
        assertEquals(0, paywall.offlineBlocks, "an unowed ad raised the offline block")
        assertEquals(0, cache.get().offlineGraceLevelsSpent, "an unowed ad spent the offline grace")
    }

    @Test
    fun everyReasonTheRewardedPathIsFreeAlsoSilencesTheInterstitial() = runUnitTest {
        network.outcome = AdShowOutcome(AdShowResult.Dismissed)
        val floorMet = mapOf<String, Any>("interstitialEveryLevels" to 1)

        entitlements.setPro(true)
        gate(ads = floorMet).also { it.clearLevels(1) }.showInterstitial(AdPlacement.LevelComplete)
        assertEquals(emptyList(), network.shown, "Pro was shown an interstitial")
        entitlements.setPro(false)

        gate(ads = floorMet + ("enabled" to false)).also { it.clearLevels(1) }
            .showInterstitial(AdPlacement.LevelComplete)
        assertEquals(emptyList(), network.shown, "the kill switch did not reach the interstitial")

        gate(ads = floorMet + ("rewardedPlacements" to mapOf("level_complete" to false)))
            .also { it.clearLevels(1) }.showInterstitial(AdPlacement.LevelComplete)
        assertEquals(emptyList(), network.shown, "the placement switch did not reach the interstitial")

        gate(ads = mapOf("interstitialEveryLevels" to 0)).also { it.clearLevels(9) }
            .showInterstitial(AdPlacement.LevelComplete)
        assertEquals(emptyList(), network.shown, "zero should turn the interstitial off")

        progress.unlocked = 2
        gate(ads = floorMet + mapOf("newUserGraceLevels" to 5, "newUserGraceMinutes" to 0))
            .also { it.clearLevels(1) }.showInterstitial(AdPlacement.LevelComplete)
        assertEquals(emptyList(), network.shown, "a day-zero player was shown an interstitial")
    }

    @Test
    fun theFloorSurvivesAForceQuit() = runUnitTest {
        val config = mapOf("interstitialEveryLevels" to 2)
        gate(ads = config).clearLevels(2)

        network.outcome = AdShowOutcome(AdShowResult.Dismissed)
        // Same cache, new gate: a process restart forgets everything but disk.
        assertTrue(gate(ads = config).showInterstitial(AdPlacement.LevelComplete))
    }

    @Test
    fun aThrowingNetworkShowsNothingAndOpensTheNextBoard() = runUnitTest {
        val exploding = object : com.sodogku.libraries.ads.AdNetwork {
            override suspend fun prepare() = Unit
            override suspend fun show(format: AdFormat): AdShowOutcome = error("SDK exploded")
            override fun preload(format: AdFormat) = Unit
        }
        val gate = gate(ads = mapOf("interstitialEveryLevels" to 1), network = exploding)
        gate.clearLevels(1)

        assertFalse(gate.showInterstitial(AdPlacement.LevelComplete))
    }

    // ------------------------------------------------------------------
    // Offline grace
    // ------------------------------------------------------------------

    @Test
    fun offlineGateGrantsTheRewardAndSpendsGraceWithoutBlockingUntilItIsGone() = runUnitTest {
        appState.isDeviceOffline.value = true
        val config = mapOf("offlineGraceLevels" to 3, "offlineGraceMinutes" to 20)
        val gate = gate(ads = config)

        repeat(3) { attempt ->
            val outcome = gate.showRewarded(AdPlacement.ContinueLevel)
            assertNotEquals(RewardOutcome.Dismissed, outcome, "Offline must not withhold on attempt $attempt")
            assertEquals(0, paywall.offlineBlocks, "The block must not fire inside the grace")
        }

        val past = gate.showRewarded(AdPlacement.ContinueLevel)

        assertNotEquals(RewardOutcome.Dismissed, past, "Even the blocking gate still pays the player")
        assertEquals(1, paywall.offlineBlocks)
        assertEquals(emptyList(), network.shown, "There is no point asking a network that is unreachable")
    }

    @Test
    fun theOfflineGraceAlsoExpiresOnWallClock() = runUnitTest {
        appState.isDeviceOffline.value = true
        val config = mapOf("offlineGraceLevels" to 99, "offlineGraceMinutes" to 20)
        val gate = gate(ads = config)

        gate.showRewarded(AdPlacement.ContinueLevel)
        assertEquals(0, paywall.offlineBlocks)

        clock.advanceMinutes(21)
        gate.showRewarded(AdPlacement.ContinueLevel)

        assertEquals(1, paywall.offlineBlocks, "Twenty minutes offline is the other half of the grace")
    }

    @Test
    fun offlineGraceCountersSurviveAForceQuit() = runUnitTest {
        appState.isDeviceOffline.value = true
        val config = mapOf("offlineGraceLevels" to 2, "offlineGraceMinutes" to 20)

        val before = gate(ads = config)
        before.showRewarded(AdPlacement.ContinueLevel)
        before.showRewarded(AdPlacement.ContinueLevel)
        assertEquals(0, paywall.offlineBlocks)

        // Same cache, new gate: a process restart forgets everything but disk.
        val after = gate(ads = config)
        after.showRewarded(AdPlacement.ContinueLevel)

        assertEquals(1, paywall.offlineBlocks, "A relaunch must not hand back a spent grace")
    }

    @Test
    fun theOfflineGraceResetsOnASuccessfulAdViewNotOnReconnect() = runUnitTest {
        appState.isDeviceOffline.value = true
        val config = mapOf("offlineGraceLevels" to 2, "offlineGraceMinutes" to 20)
        val gate = gate(ads = config)

        gate.showRewarded(AdPlacement.ContinueLevel)
        gate.showRewarded(AdPlacement.ContinueLevel)

        // Back online, but nothing watched yet: the debt is still owed.
        appState.isDeviceOffline.value = false
        assertEquals(2, cache.get().offlineGraceLevelsSpent, "Reconnecting is not a payment")

        network.outcome = AdShowOutcome(AdShowResult.Rewarded)
        gate.showRewarded(AdPlacement.ContinueLevel)

        assertEquals(0, cache.get().offlineGraceLevelsSpent)
        assertEquals(0L, cache.get().offlineGraceStartedAtMs)
    }

    @Test
    fun aFailedAdDoesNotResetTheOfflineGrace() = runUnitTest {
        appState.isDeviceOffline.value = true
        val config = mapOf("offlineGraceLevels" to 5, "offlineGraceMinutes" to 20)
        val gate = gate(ads = config)
        gate.showRewarded(AdPlacement.ContinueLevel)

        appState.isDeviceOffline.value = false
        network.outcome = AdShowOutcome(AdShowResult.NoFill)
        gate.showRewarded(AdPlacement.ContinueLevel)

        assertEquals(1, cache.get().offlineGraceLevelsSpent, "A no-fill is not an ad view")
    }

    @Test
    fun ourOwnBackendBeingDownDoesNotSpendTheOfflineGrace() = runUnitTest {
        // Found on a device, 2026-09-07: the offline block went up on full
        // wifi, because `AppState.isOffline` also means "our server is
        // unreachable" and the dev server is not deployed. AdMob does not care
        // whether our backend is up. `features.md#offline` says only the OS
        // signal counts.
        appState.isOffline.value = true
        appState.isDeviceOffline.value = false
        network.outcome = AdShowOutcome(AdShowResult.Rewarded)

        val outcome = gate(ads = mapOf("offlineGraceLevels" to 1)).showRewarded(AdPlacement.ContinueLevel)

        assertEquals(RewardOutcome.Rewarded, outcome)
        assertEquals(listOf(AdFormat.Rewarded), network.shown, "The ad network was reachable the whole time")
        assertEquals(0, cache.get().offlineGraceLevelsSpent)
        assertEquals(0, paywall.offlineBlocks)
    }

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // Paywall handoff
    // ------------------------------------------------------------------

    @Test
    fun onlyTheTwoSpecNamedPlacementsOfferPro() = runUnitTest {
        val gate = gate()
        AdPlacement.entries.forEach { gate.showRewarded(it) }

        assertEquals(
            listOf(PaywallTrigger.ContinueLevel, PaywallTrigger.SkipLevel),
            paywall.offers.distinct().sortedBy { it.id },
            "A booster grant and a streak freeze are too frequent to sell against",
        )
    }

    @Test
    fun noOneIsSoldProBeforeTheyHaveSeenASingleAd() = runUnitTest {
        // Day 0, inside the new-user grace: the reward is free and the player
        // has not been interrupted by anything yet. "Pay to remove the ads" is
        // an odd first impression when there have not been any.
        progress.unlocked = 1
        val gate = gate(ads = mapOf("newUserGraceLevels" to 5, "newUserGraceMinutes" to 5))

        gate.showRewarded(AdPlacement.ContinueLevel)
        gate.showRewarded(AdPlacement.SkipLevel)

        assertEquals(emptyList(), paywall.offers)
    }

    @Test
    fun theOfflineGateOffersTheBlockScreenRatherThanAnOffer() = runUnitTest {
        // A configured zero is the harsh live-ops setting the admin console
        // makes an operator confirm, so it has to actually bite: block on the
        // first unservable gate. And it puts up the block, not a Pro offer —
        // two paywalls stacked on one gate is a nag.
        appState.isDeviceOffline.value = true
        val gate = gate(ads = mapOf("offlineGraceLevels" to 0))

        val outcome = gate.showRewarded(AdPlacement.ContinueLevel)

        assertNotEquals(RewardOutcome.Dismissed, outcome, "Even a zero grace still pays this reward")
        assertEquals(emptyList(), paywall.offers)
        assertEquals(1, paywall.offlineBlocks)
    }

    // ------------------------------------------------------------------
    // The self-promo stand-in
    // ------------------------------------------------------------------

    @Test
    fun everyUnservedRewardedAdPutsProUpRatherThanNothing() = runUnitTest {
        val unserved = listOf(
            AdShowOutcome(AdShowResult.NoFill) to "no_fill",
            AdShowOutcome(AdShowResult.Offline) to "offline",
            AdShowOutcome(AdShowResult.NotShown) to "not_shown",
            AdShowOutcome(AdShowResult.Failed, "load_3") to "load_3",
        )

        unserved.forEach { (outcome, _) ->
            network.outcome = outcome
            gate().showRewarded(AdPlacement.BoosterGrant)
        }

        assertEquals(
            unserved.map { (_, reason) -> AdsRewardedPlacements.BOOSTER_GRANT to reason },
            paywall.standIns,
            "A rewarded slot is attention the player volunteered. Handing it back " +
                "unused on every no-fill throws away the only inventory we own.",
        )
    }

    @Test
    fun theStandInCannotChangeTheReward() = runUnitTest {
        // The promo is shown *after* the outcome is decided and can never reach
        // it. If watching it were what earned the bones, closing it early would
        // have to withhold them, and that is a second `Dismissed` path wearing a
        // feature's clothes.
        network.outcome = AdShowOutcome(AdShowResult.NoFill)

        val withStandIn = gate().showRewarded(AdPlacement.BoosterGrant)
        val withoutStandIn = gate().showRewarded(AdPlacement.ContinueLevel)

        assertTrue(paywall.standIns.isNotEmpty(), "the booster grant did take the stand-in path")
        assertEquals(RewardOutcome.NoFill, withStandIn)
        assertEquals(withStandIn, withoutStandIn, "Whether Pro appeared must not change the reward")
    }

    @Test
    fun aGateThatJustOfferedProDoesNotOfferItAgainOnTheWayOut() = runUnitTest {
        // `continue_level` puts the sheet up on the way *in*. Two Pro sheets
        // around one ad gate is the nag `paywall.sessionCap` exists to stop.
        network.outcome = AdShowOutcome(AdShowResult.NoFill)

        gate().showRewarded(AdPlacement.ContinueLevel)

        assertEquals(listOf(PaywallTrigger.ContinueLevel), paywall.offers)
        assertEquals(emptyList(), paywall.standIns)
    }

    @Test
    fun anOfferTheCoordinatorRefusedLeavesTheStandInToDoItsJob() = runUnitTest {
        // The pre-ad offer is capped or switched off, so nothing was shown on
        // the way in and the slot really is empty.
        paywall.acceptsOffers = false
        network.outcome = AdShowOutcome(AdShowResult.NoFill)

        gate().showRewarded(AdPlacement.ContinueLevel)

        assertEquals(emptyList(), paywall.offers)
        assertEquals(
            listOf(AdsRewardedPlacements.CONTINUE_LEVEL to "no_fill"),
            paywall.standIns,
        )
    }

    @Test
    fun neitherAWatchedAdNorADismissedOneIsStoodInFor() = runUnitTest {
        network.outcome = AdShowOutcome(AdShowResult.Rewarded)
        gate().showRewarded(AdPlacement.BoosterGrant)

        network.outcome = AdShowOutcome(AdShowResult.Dismissed)
        gate().showRewarded(AdPlacement.BoosterGrant)

        assertEquals(
            listOf(AdFormat.Rewarded, AdFormat.Rewarded),
            network.shown,
            "both gates really did reach the network",
        )
        assertEquals(
            emptyList(),
            paywall.standIns,
            "One slot was filled and the other the player closed on purpose. " +
                "Selling to someone who just said no is the nag, not the fallback.",
        )
    }

    @Test
    fun aFreeRewardIsNotAnUnservedOneAndNeverPitchesPro() = runUnitTest {
        // Ads off, placement off, still inside the new-user grace: the reward
        // was free because we chose not to show an ad, not because we failed to.
        val adsOff = gate(ads = mapOf("enabled" to false)).showRewarded(AdPlacement.BoosterGrant)
        val placementOff = gate(ads = mapOf("rewardedPlacements" to mapOf("booster_grant" to false)))
            .showRewarded(AdPlacement.BoosterGrant)

        assertEquals(
            listOf(
                RewardOutcome.GrantedWithoutAd("ads_disabled"),
                RewardOutcome.GrantedWithoutAd("placement_disabled"),
            ),
            listOf(adsOff, placementOff),
        )
        assertEquals(emptyList(), network.shown, "neither gate went near an ad")
        assertEquals(emptyList(), paywall.standIns)
    }

    @Test
    fun theAdClockDoesNotFollowThePhoneClock() = recordingEvents { events ->
        runUnitTest {
            // The phone syncs network time while the ad is on screen. That is
            // not a contrived moment: a sync commonly lands just after
            // connectivity returns, which is exactly when the first ad after an
            // offline stretch is requested. Measured on the wall clock this
            // reported an hour of negative latency, and the ad-funnel board
            // unwraps `latency_ms` into a p90 by placement, so one such record
            // in a day is enough to move the line.
            network.whileShowing = {
                elapsed += 12.seconds
                clock.stepBySeconds(-3_600)
            }

            gate().showRewarded(AdPlacement.BoosterGrant)

            assertEquals(12_000L, events.single("ads.result")["latency_ms"])
        }
    }

    // ------------------------------------------------------------------

    /**
     * [ads] is merged over a base that switches the **new-user grace off**.
     * Every gate in this class would otherwise sit inside the shipped five
     * minutes, because the grace clock starts when the gate is constructed —
     * which is correct in production and useless as a default in a test.
     * `theNewUserGraceNeedsBothLegsPastBeforeAnAdShows` turns it back on.
     */
    private fun gate(
        ads: Map<String, Any> = emptyMap(),
        network: com.sodogku.libraries.ads.AdNetwork = this.network,
    ): RealAdGate {
        val map: AppConfigMap = TestConfigMap(mapOf("ads" to (NO_NEW_USER_GRACE + ads)))
        return RealAdGate(
            network = network,
            entitlements = entitlements,
            progress = progress,
            paywall = paywall,
            appState = appState,
            adState = cache,
            session = AdSession(sessions),
            appScope = AppCoroutineScope(dispatchers),
            clock = clock,
            timeSource = elapsed,
            adsEnabled = AdsEnabled(map),
            newUserGraceLevels = AdsNewUserGraceLevels(map),
            newUserGraceMinutes = AdsNewUserGraceMinutes(map),
            rewardedPlacements = AdsRewardedPlacements(map),
            offlineGraceLevels = AdsOfflineGraceLevels(map),
            offlineGraceMinutes = AdsOfflineGraceMinutes(map),
            interstitialEveryLevels = AdsInterstitialEveryLevels(map),
        )
    }

    private suspend fun RealAdGate.clearLevels(n: Int) = repeat(n) { levelCleared() }

    private companion object {
        val NO_NEW_USER_GRACE: Map<String, Any> = mapOf(
            "newUserGraceLevels" to 0,
            "newUserGraceMinutes" to 0,
        )
    }
}
