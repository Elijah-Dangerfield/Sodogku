package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdFormat
import com.sodogku.libraries.ads.AdOutcome
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.AdShowOutcome
import com.sodogku.libraries.ads.AdShowResult
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.AdsEnabled
import com.sodogku.libraries.config.values.AdsInterstitialCooldownSec
import com.sodogku.libraries.config.values.AdsInterstitialEveryNLevels
import com.sodogku.libraries.config.values.AdsInterstitialsPerSessionMax
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * [RealAdGate], which owns two things worth testing separately.
 *
 * **Fail-open.** SPEC 5.3 and 4.2: only a deliberate dismissal withholds a
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

    // ------------------------------------------------------------------
    // Fail open
    // ------------------------------------------------------------------

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
        // SPEC 4.2 forbids.
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

        assertEquals(RewardOutcome.Rewarded, outcome)
        assertEquals(emptyList(), network.shown, "A kill switch must not still request an ad")
    }

    @Test
    fun aDisabledPlacementGrantsTheRewardWithoutAskingTheNetwork() = runUnitTest {
        val outcome = gate(
            ads = mapOf("rewardedPlacements" to mapOf("skip_level" to false)),
        ).showRewarded(AdPlacement.SkipLevel)

        assertEquals(RewardOutcome.Rewarded, outcome)
        assertEquals(emptyList(), network.shown)
    }

    @Test
    fun aProPlayerNeverSeesAnAdAndAlwaysGetsTheReward() = runUnitTest {
        entitlements.setPro(true)

        val rewarded = gate().showRewarded(AdPlacement.ContinueLevel)
        val interstitial = gate().showInterstitial(AdPlacement.LevelComplete)

        assertEquals(RewardOutcome.Rewarded, rewarded)
        assertEquals(AdOutcome.NotShown, interstitial)
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
        assertEquals(RewardOutcome.Rewarded, early.showRewarded(AdPlacement.BoosterGrant))
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
        // whether our backend is up. SPEC 6 says only the OS signal counts.
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
    // Interstitial triple gate
    // ------------------------------------------------------------------

    @Test
    fun theInterstitialWaitsForNLevels() = runUnitTest {
        val gate = gate(
            ads = mapOf(
                "interstitialEveryNLevels" to 3,
                "interstitialCooldownSec" to 0,
            ),
        )
        network.outcome = AdShowOutcome(AdShowResult.Completed)

        assertEquals(AdOutcome.NotShown, gate.showInterstitial(AdPlacement.LevelComplete))
        assertEquals(AdOutcome.NotShown, gate.showInterstitial(AdPlacement.LevelComplete))
        assertEquals(AdOutcome.Shown, gate.showInterstitial(AdPlacement.LevelComplete))
        assertEquals(AdOutcome.NotShown, gate.showInterstitial(AdPlacement.LevelComplete))
    }

    @Test
    fun theCooldownHoldsAnInterstitialTheLevelCounterWouldAllow() = runUnitTest {
        val gate = gate(
            ads = mapOf(
                "interstitialEveryNLevels" to 1,
                "interstitialCooldownSec" to 60,
            ),
        )
        network.outcome = AdShowOutcome(AdShowResult.Completed)

        assertEquals(AdOutcome.Shown, gate.showInterstitial(AdPlacement.LevelComplete))
        clock.advanceSeconds(30)
        assertEquals(AdOutcome.NotShown, gate.showInterstitial(AdPlacement.LevelComplete))
        clock.advanceSeconds(31)
        assertEquals(AdOutcome.Shown, gate.showInterstitial(AdPlacement.LevelComplete))
    }

    @Test
    fun theSessionCapHoldsInterstitialsTheOtherTwoGatesWouldAllow() = runUnitTest {
        val gate = gate(
            ads = mapOf(
                "interstitialEveryNLevels" to 1,
                "interstitialCooldownSec" to 0,
                "interstitialsPerSessionMax" to 2,
            ),
        )
        network.outcome = AdShowOutcome(AdShowResult.Completed)

        assertEquals(AdOutcome.Shown, gate.showInterstitial(AdPlacement.LevelComplete))
        assertEquals(AdOutcome.Shown, gate.showInterstitial(AdPlacement.LevelComplete))
        assertEquals(AdOutcome.NotShown, gate.showInterstitial(AdPlacement.LevelComplete))

        // A new session is a new ceiling — that is the whole reason the counter
        // is in memory rather than on disk.
        sessions.roll()
        assertEquals(AdOutcome.Shown, gate.showInterstitial(AdPlacement.LevelComplete))
    }

    @Test
    fun aNoFillDoesNotSpendTheLevelCounter() = runUnitTest {
        val gate = gate(
            ads = mapOf(
                "interstitialEveryNLevels" to 2,
                "interstitialCooldownSec" to 0,
            ),
        )
        network.outcome = AdShowOutcome(AdShowResult.NoFill)

        gate.showInterstitial(AdPlacement.LevelComplete)
        assertEquals(AdOutcome.NotShown, gate.showInterstitial(AdPlacement.LevelComplete))

        network.outcome = AdShowOutcome(AdShowResult.Completed)
        assertEquals(
            AdOutcome.Shown,
            gate.showInterstitial(AdPlacement.LevelComplete),
            "A no-fill must not push the next interstitial N levels further out",
        )
    }

    @Test
    fun interstitialsNeverFireOffline() = runUnitTest {
        appState.isDeviceOffline.value = true
        val gate = gate(
            ads = mapOf(
                "interstitialEveryNLevels" to 1,
                "interstitialCooldownSec" to 0,
            ),
        )

        assertEquals(AdOutcome.NotShown, gate.showInterstitial(AdPlacement.LevelComplete))
        assertEquals(emptyList(), network.shown)
        assertEquals(0, paywall.offlineBlocks, "An automatic ad the player never asked for owes no grace")
    }

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
            adsEnabled = AdsEnabled(map),
            newUserGraceLevels = AdsNewUserGraceLevels(map),
            newUserGraceMinutes = AdsNewUserGraceMinutes(map),
            interstitialEveryNLevels = AdsInterstitialEveryNLevels(map),
            interstitialCooldownSec = AdsInterstitialCooldownSec(map),
            interstitialsPerSessionMax = AdsInterstitialsPerSessionMax(map),
            rewardedPlacements = AdsRewardedPlacements(map),
            offlineGraceLevels = AdsOfflineGraceLevels(map),
            offlineGraceMinutes = AdsOfflineGraceMinutes(map),
        )
    }

    private companion object {
        val NO_NEW_USER_GRACE: Map<String, Any> = mapOf(
            "newUserGraceLevels" to 0,
            "newUserGraceMinutes" to 0,
        )
    }
}
