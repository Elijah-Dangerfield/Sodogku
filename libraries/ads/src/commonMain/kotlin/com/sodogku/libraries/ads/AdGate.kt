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
enum class AdPlacement(
    /**
     * The one name this placement answers to outside Kotlin: the key
     * `ads.rewardedPlacements` is gated on, and the `placement` attribute on
     * **every** event that names a placement: the ad events, and the
     * `game.bones_refilled` the ad paid for.
     *
     * It lives on the enum rather than on an extension in `:libraries:ads:impl`
     * because the game emits a placement too and cannot see an impl module. It
     * reached for `.name` instead, so the refill said `ContinueLevel` where the
     * ad said `continue_level` and one query could not have both.
     */
    val configId: String,
) {
    /** Third strike: restore a life and keep the board. */
    ContinueLevel("continue_level"),

    /** Earn a Sniff or a Treat. */
    BoosterGrant("booster_grant"),

    /** Skip the level, after two failed attempts. */
    SkipLevel("skip_level"),

    /** Cover a missed daily and keep the streak. */
    StreakFreeze("streak_freeze"),
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

    /**
     * The reward was given without an ad being shown at all: the player is Pro,
     * ads are switched off in config, this placement is disabled, or they are
     * inside the new-user grace. [reason] matches `ads.result`'s own `reason`.
     *
     * Distinct from [Rewarded], which it used to be folded into, because the
     * caller is the only thing that can tell the player anything and it could
     * not tell these two apart. Granting an ad's worth of bones with no ad is
     * the game being generous, and a screen that wants to say so needs to know
     * it happened. **It grants exactly as [Rewarded] does** — callers ask
     * `!= Dismissed`, and this is not `Dismissed`.
     */
    data class GrantedWithoutAd(val reason: String) : RewardOutcome
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

    /**
     * Whether the player is still inside `features.md#ads`'s new-user grace:
     * no ads before level 5 or the first five minutes, both legs.
     *
     * Exposed because the grace is not only about ads. The standing Go Pro
     * button (SD-147) hides behind the same window, for the same reason the
     * gate's own Pro offer sits below every free path: "no more ads" is not
     * something to sell to a player who has not been shown one. Defaults to
     * false, which is what a gate with no grace to keep means.
     */
    suspend fun inNewUserGrace(): Boolean = false
}

/**
 * The binding until the real AdMob implementation lands (C8): every rewarded ad
 * succeeds instantly.
 *
 * It lives in this api module rather than an `impl` so the real implementation
 * can replace it with
 * `@ContributesBinding(AppScope::class, replaces = [AlwaysRewardingAdGate::class])`
 * without crossing the impl-module boundary.
 *
 * **[RewardOutcome.GrantedWithoutAd], not [RewardOutcome.Rewarded]**, because no
 * ad plays here and that is the whole distinction between the two. Returning
 * `Rewarded` would have a build on this binding hand over an ad's worth of bones
 * while telling the player an ad paid for them, which is the one thing the
 * `GrantedWithoutAd` reason exists to let a caller say out loud. Grants exactly
 * as `Rewarded` does; callers ask `!= Dismissed`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AlwaysRewardingAdGate : AdGate {
    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome =
        RewardOutcome.GrantedWithoutAd(NoAdNetworkReason)

    override fun preload(placement: AdPlacement) = Unit

    private companion object {
        /** Named like `RealAdGate`'s reasons, which are the values a funnel joins on. */
        const val NoAdNetworkReason = "no_ad_network"
    }
}
