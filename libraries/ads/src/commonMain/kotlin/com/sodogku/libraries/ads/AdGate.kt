package com.sodogku.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Every place the app can show an ad. One entry per slot, so config can gate
 * them individually.
 *
 * Four are **rewarded** and asked for: the player taps a thing that says an ad
 * is coming and gets something for it. One is not. [LevelComplete] is the floor
 * (SD-148, 2026-09-21): a free player who has cleared
 * `ads.interstitialEveryLevels` levels since the last ad of any kind sees one on
 * the next Next level, and a player who watched a continue two boards ago sees
 * nothing extra. It pays nothing, it is never on a board in play, never on the
 * daily, and never inside the new-user grace. The ceiling is the same counter:
 * any ad shown, of either kind, resets it.
 *
 * An earlier `LevelComplete` was deleted on 2026-09-14 for having no caller.
 * This one has exactly one, `GameViewModel.nextLevel`, and `AdPolicyTest` names
 * it as the one placement that is not rewarded so a second cannot arrive
 * unargued.
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

    /**
     * Between two campaign levels, on the Next level tap, when the player has
     * cleared enough of them without an ad. The one placement that pays
     * nothing; see the class KDoc.
     */
    LevelComplete("level_complete"),
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

    /**
     * The between-levels ad, if one is due. Returns whether an ad was put on
     * screen, which no caller needs for anything but a log line: there is no
     * reward to grant and nothing to withhold, and the next board opens either
     * way. Every gate the rewarded path has (Pro, the kill switches, the
     * new-user grace) applies, plus the floor: `ads.interstitialEveryLevels`
     * boards cleared since the last ad of any kind. Offline shows nothing and
     * spends no grace, because the offline grace is about rewards the player
     * was owed. Defaults to none shown, which is what a gate with no
     * interstitial means.
     */
    suspend fun showInterstitial(placement: AdPlacement): Boolean = false

    /**
     * A board was finished. Moves the floor's counter along; nothing else
     * happens. Called for the daily too, because the counter is "levels since
     * an ad" and a daily is a level, even though the ad itself never lands on
     * the daily's Next.
     */
    suspend fun levelCleared() = Unit

    /**
     * Whether Settings should offer a way back to the consent form (SD-149).
     *
     * Passed through from [AdNetwork.privacyOptionsRequired] rather than
     * decided here, because only UMP knows. Deliberately **not** gated on Pro:
     * a player who saw the form before buying still has a choice on record, and
     * Google's policy is about the choice rather than about whether ads are
     * currently being served. Defaults to false, which hides the row.
     */
    suspend fun privacyOptionsRequired(): Boolean = false

    /** Opens UMP's privacy options form. Only call it when the above is true. */
    suspend fun showPrivacyOptions() = Unit
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
