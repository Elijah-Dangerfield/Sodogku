package com.sodogku.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Every place the app can show an ad. One entry per slot, so config can gate
 * them individually.
 *
 * All of them are **rewarded**, and all of them are asked for. That is the whole
 * policy: the player taps a thing that says an ad is coming and gets something
 * for it. There is no format here that interrupts.
 *
 * There used to be `LevelComplete`, an interstitial fired automatically after a
 * level behind a frequency gate. It was built, configured, given a triple gate
 * and three remote keys, and never called by anything — so it produced no
 * impressions and cost nothing to delete, and leaving it would have meant the
 * one ad nobody asked for was a single call site away from shipping by accident.
 */
enum class AdPlacement {
    /** Third strike: restore a life and keep the board. */
    ContinueLevel,

    /** Earn a Sniff or a Treat. */
    BoosterGrant,

    /** Skip the level, after two failed attempts. */
    SkipLevel,

    /** Cover a missed daily and keep the streak. */
    StreakFreeze,
}

/** How a rewarded ad ended. */
sealed interface RewardOutcome {
    /** Watched to the end. Grant the reward. */
    data object Rewarded : RewardOutcome

    /** The player closed it early. The only outcome that withholds the reward. */
    data object Dismissed : RewardOutcome

    /** No ad was available to serve. */
    data object NoFill : RewardOutcome

    /** No route to the network. */
    data object Offline : RewardOutcome

    /** The SDK failed. [kind] is for telemetry, never for the player. */
    data class Failed(val kind: String) : RewardOutcome
}

/**
 * The app's whole view of advertising.
 *
 * Note what [RewardOutcome] forces on callers: `NoFill`, `Offline` and `Failed`
 * are distinct from `Dismissed` on purpose, because **only a deliberate dismissal
 * may withhold the reward**. An ad network outage must never be able to block a
 * player from continuing a level they are in the middle of, and modelling
 * "didn't watch" as one boolean is how that bug gets written.
 */
interface AdGate {
    suspend fun showRewarded(placement: AdPlacement): RewardOutcome

    /** Warms a placement so it is ready when the moment arrives. Fire and forget. */
    fun preload(placement: AdPlacement)
}

/**
 * The binding until the real AdMob implementation lands (C8): every rewarded ad
 * succeeds instantly.
 *
 * It lives in this api module rather than an `impl` for the same reason
 * `NoOpAuthTokenProvider` does — the real implementation will replace it with
 * `@ContributesBinding(AppScope::class, replaces = [AlwaysRewardingAdGate::class])`
 * without crossing the impl-module boundary.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AlwaysRewardingAdGate : AdGate {
    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome = RewardOutcome.Rewarded
    override fun preload(placement: AdPlacement) = Unit
}
