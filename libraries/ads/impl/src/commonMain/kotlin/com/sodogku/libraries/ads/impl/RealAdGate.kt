package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdOutcome
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.AdNetwork
import com.sodogku.libraries.ads.AdShowResult
import com.sodogku.libraries.ads.AlwaysRewardingAdGate
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PaywallCoordinator
import com.sodogku.libraries.config.values.AdsEnabled
import com.sodogku.libraries.config.values.AdsInterstitialCooldownSec
import com.sodogku.libraries.config.values.AdsInterstitialEveryNLevels
import com.sodogku.libraries.config.values.AdsInterstitialsPerSessionMax
import com.sodogku.libraries.config.values.AdsNewUserGraceLevels
import com.sodogku.libraries.config.values.AdsNewUserGraceMinutes
import com.sodogku.libraries.config.values.AdsOfflineGraceLevels
import com.sodogku.libraries.config.values.AdsOfflineGraceMinutes
import com.sodogku.libraries.config.values.AdsRewardedPlacements
import com.sodogku.libraries.core.AppState
import com.sodogku.libraries.core.AutoInit
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.progress.ProgressRepository
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The real [AdGate]: every rule about *whether* an ad may be shown, and the one
 * rule about what happens when it cannot be.
 *
 * ## The property that matters most
 *
 * **A failed ad pays the player.** SPEC 5.3: only a deliberate
 * [RewardOutcome.Dismissed] withholds a reward. Every other path through
 * [showRewarded] — ads switched off, the placement disabled, no fill, an SDK
 * that threw, no network, a config server nobody can reach — ends in something
 * the call sites treat as a grant. That is not politeness; a rewarded continue
 * is the thing standing between a player and a lost board, and an ad network
 * having a bad afternoon must not be able to take it.
 *
 * Read the `when` in [rewarded] as a list of *reasons the reward is free*, and
 * note there is no branch that returns `Dismissed` except the one where the
 * player closed the ad. `ads.failureMode = LOCK` is deliberately not consulted
 * here at all — it is an A/B arm about what the *lose sheet* offers, and wiring
 * it into the reward path is the shape of bug SPEC 4.2 forbids.
 *
 * ## Everything is read at the point of use
 *
 * No config value is captured in a field. SPEC 4.2 asks for kill switches that
 * work within the hour rather than the session, and a cached `ads.enabled` is a
 * kill switch that does not.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = AdGate::class,
    replaces = [AlwaysRewardingAdGate::class],
)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class RealAdGate(
    private val network: AdNetwork,
    private val entitlements: Entitlements,
    private val progress: ProgressRepository,
    private val paywall: PaywallCoordinator,
    private val appState: AppState,
    private val adState: AdStateCache,
    private val session: AdSession,
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
    private val adsEnabled: AdsEnabled,
    private val newUserGraceLevels: AdsNewUserGraceLevels,
    private val newUserGraceMinutes: AdsNewUserGraceMinutes,
    private val interstitialEveryNLevels: AdsInterstitialEveryNLevels,
    private val interstitialCooldownSec: AdsInterstitialCooldownSec,
    private val interstitialsPerSessionMax: AdsInterstitialsPerSessionMax,
    private val rewardedPlacements: AdsRewardedPlacements,
    private val offlineGraceLevels: AdsOfflineGraceLevels,
    private val offlineGraceMinutes: AdsOfflineGraceMinutes,
) : AdGate, AutoInit {

    private val logger = KLog.withTag("AdGate")

    init {
        // The wall-clock leg of the new-user grace has to start when the app
        // first runs, not when the first ad is requested — otherwise a player
        // who takes ten minutes to reach level 5 still gets five ad-free
        // minutes from the moment the first ad would have shown, which is the
        // grace measuring itself.
        appScope.launch {
            adState.update { state ->
                if (state.firstSeenAtMs == 0L) state.copy(firstSeenAtMs = now()) else state
            }
        }
    }

    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome =
        Catching { rewarded(placement) }
            .logOnFailure { "Rewarded ad path threw for $placement; granting anyway" }
            .getOrElse { RewardOutcome.Failed(it::class.simpleName ?: "unknown") }

    override suspend fun showInterstitial(placement: AdPlacement): AdOutcome =
        Catching { interstitial(placement) }
            .logOnFailure { "Interstitial path threw for $placement" }
            .getOrElse { AdOutcome.Failed(it::class.simpleName ?: "unknown") }

    override fun preload(placement: AdPlacement) {
        if (entitlements.isPro.value) return
        if (!adsEnabled()) return
        appScope.launch {
            Catching {
                network.prepare()
                network.preload(placement.format)
            }.logOnFailure { "Preload failed for $placement" }
        }
    }

    private suspend fun rewarded(placement: AdPlacement): RewardOutcome {
        val offline = appState.isDeviceOffline.value
        logger.logEvent(
            "ads.gate_shown",
            "placement" to placement.configId,
            "is_offline" to offline,
        )

        // Each of these is a reason the reward is free. None of them is a
        // reason to withhold it.
        val freeReason = when {
            entitlements.isPro.value -> "pro"
            !adsEnabled() -> "ads_disabled"
            !rewardedPlacements.isEnabled(placement.configId) -> "placement_disabled"
            inNewUserGrace() -> "new_user_grace"
            else -> null
        }
        if (freeReason != null) return granted(placement, freeReason)

        if (offline) return offlineRewarded(placement)

        // Below every free path on purpose. Offering to sell "no more ads" to a
        // player who has not been shown one yet — day 0, inside the new-user
        // grace — is the friction SPEC 5.3 spends a whole section avoiding, and
        // the offline path has its own block screen to put up instead.
        placement.paywallTrigger?.let { paywall.requestOffer(it) }

        val started = now()
        network.prepare()
        val outcome = network.show(placement.format)
        logger.logEvent(
            "ads.result",
            "placement" to placement.configId,
            "outcome" to outcome.result.name,
            "latency_ms" to (now() - started),
            "error_kind" to outcome.errorKind,
        )

        return when (outcome.result) {
            AdShowResult.Rewarded, AdShowResult.Completed -> {
                // SPEC 6: the offline grace resets on a *successful ad view*,
                // not on reconnect. Coming back online without watching
                // anything means the debt is still owed.
                adState.update { it.withOfflineGraceReset() }
                RewardOutcome.Rewarded
            }

            AdShowResult.Dismissed -> RewardOutcome.Dismissed
            AdShowResult.NoFill -> RewardOutcome.NoFill
            AdShowResult.Offline -> RewardOutcome.Offline
            AdShowResult.NotShown -> RewardOutcome.NoFill
            AdShowResult.Failed -> RewardOutcome.Failed(outcome.errorKind ?: "sdk")
        }
    }

    /**
     * No route to the network, so there is no ad to serve and no chance of one.
     *
     * The grace is spent here rather than at level completion because this is
     * the moment SPEC 6 names: "counted from the first ad gate that could not
     * be served". The player is paid either way — what changes past the grace
     * is that the offline block goes up behind them.
     */
    private suspend fun offlineRewarded(placement: AdPlacement): RewardOutcome {
        val state = adState.update { current ->
            val startedAt = if (current.offlineGraceStartedAtMs == 0L) now() else current.offlineGraceStartedAtMs
            current.copy(
                offlineGraceStartedAtMs = startedAt,
                offlineGraceLevelsSpent = current.offlineGraceLevelsSpent + 1,
            )
        }

        if (offlineGraceIsSpent(state)) {
            logger.logEvent(
                "ads.offline_block",
                "placement" to placement.configId,
                "grace_levels_used" to state.offlineGraceLevelsSpent,
            )
            paywall.requestOfflineBlock()
        }

        logger.logEvent(
            "ads.result",
            "placement" to placement.configId,
            "outcome" to AdShowResult.Offline.name,
            "grace_levels_used" to state.offlineGraceLevelsSpent,
        )
        return RewardOutcome.Offline
    }

    private fun granted(placement: AdPlacement, reason: String): RewardOutcome {
        logger.logEvent(
            "ads.result",
            "placement" to placement.configId,
            "outcome" to "granted_without_ad",
            "reason" to reason,
        )
        return RewardOutcome.Rewarded
    }

    /**
     * The `level_complete` interstitial and its triple gate.
     *
     * Every call is one finished level, so the N-levels counter ticks here
     * whether or not an ad ends up showing — otherwise a player held back by
     * the cooldown would need N *more* levels afterwards.
     */
    private suspend fun interstitial(placement: AdPlacement): AdOutcome {
        val state = adState.update { it.copy(levelsSinceInterstitial = it.levelsSinceInterstitial + 1) }

        val skipReason = when {
            entitlements.isPro.value -> "pro"
            !adsEnabled() -> "ads_disabled"
            appState.isDeviceOffline.value -> "offline"
            inNewUserGrace() -> "new_user_grace"
            state.levelsSinceInterstitial < interstitialEveryNLevels() -> "every_n_levels"
            withinCooldown(state) -> "cooldown"
            session.interstitialsShown() >= interstitialsPerSessionMax() -> "session_cap"
            else -> null
        }
        if (skipReason != null) {
            logger.d { "Interstitial suppressed at ${placement.configId}: $skipReason" }
            return AdOutcome.NotShown
        }

        logger.logEvent("ads.gate_shown", "placement" to placement.configId, "is_offline" to false)

        val started = now()
        network.prepare()
        val outcome = network.show(placement.format)
        logger.logEvent(
            "ads.result",
            "placement" to placement.configId,
            "outcome" to outcome.result.name,
            "latency_ms" to (now() - started),
            "error_kind" to outcome.errorKind,
        )

        return when (outcome.result) {
            AdShowResult.Completed, AdShowResult.Dismissed, AdShowResult.Rewarded -> {
                session.recordInterstitial()
                adState.update {
                    it.copy(levelsSinceInterstitial = 0, lastInterstitialAtMs = now())
                }
                AdOutcome.Shown
            }

            // Nothing was shown, so nothing is spent: the counters stay where
            // they are and the next level tries again. A no-fill that reset the
            // N-levels counter would quietly halve the ad load every time
            // inventory got thin, which is the opposite of what it is for.
            AdShowResult.NoFill, AdShowResult.NotShown, AdShowResult.Offline -> AdOutcome.NotShown
            AdShowResult.Failed -> AdOutcome.Failed(outcome.errorKind ?: "sdk")
        }
    }

    /**
     * SPEC 5.3: no ads before level 5 or the first 5 minutes. Two legs because
     * a fast player and a slow player fail different halves of the same intent,
     * and **both** have to be past for an ad to show.
     */
    private suspend fun inNewUserGrace(): Boolean {
        val levels = newUserGraceLevels()
        val minutes = newUserGraceMinutes()
        if (levels <= 0 && minutes <= 0) return false

        val reachedLevel = Catching { progress.unlockedThrough() }
            .logOnFailure { "Could not read progress for the new-user grace; treating as new" }
            .getOrElse { 0 }
        if (reachedLevel < levels) return true

        val firstSeen = adState.get().firstSeenAtMs
        if (firstSeen == 0L) return true
        return now() - firstSeen < minutes * MILLIS_PER_MINUTE
    }

    private fun withinCooldown(state: AdState): Boolean {
        if (state.lastInterstitialAtMs == 0L) return false
        return now() - state.lastInterstitialAtMs < interstitialCooldownSec() * MILLIS_PER_SECOND
    }

    /**
     * Both legs of SPEC 6's "three levels or twenty minutes, whichever comes
     * first".
     *
     * A configured **zero blocks on the first unservable gate**, and that is
     * deliberate rather than an oversight: the admin console already lists a
     * zero offline grace among the writes it makes an operator confirm, so
     * treating it as "leg disabled" would have made the one control that
     * warning exists for do nothing. The protection against a typo is upstream
     * — an unparseable number resolves to null and the shipped default (3 / 20)
     * wins, so only a deliberate value can be harsh.
     */
    private fun offlineGraceIsSpent(state: AdState): Boolean {
        val levelsSpent = state.offlineGraceLevelsSpent > offlineGraceLevels()
        val timeSpent = state.offlineGraceStartedAtMs != 0L &&
            now() - state.offlineGraceStartedAtMs > offlineGraceMinutes() * MILLIS_PER_MINUTE
        return levelsSpent || timeSpent
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
