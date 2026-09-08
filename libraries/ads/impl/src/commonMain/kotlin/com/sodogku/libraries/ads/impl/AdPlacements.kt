package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdFormat
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.config.values.AdsRewardedPlacements

/** Which SDK format a placement asks the network for. SPEC 5.3. */
internal val AdPlacement.format: AdFormat
    get() = when (this) {
        AdPlacement.ContinueLevel,
        AdPlacement.BoosterGrant,
        AdPlacement.SkipLevel,
        AdPlacement.StreakFreeze,
        -> AdFormat.Rewarded
    }

/**
 * The `ads.rewardedPlacements` key, and the `placement` attribute on every
 * `ads.*` event. `level_complete` has no entry in that map — it is an
 * interstitial, gated by frequency rather than by an enable switch — but it
 * still needs a stable name for telemetry.
 */
internal val AdPlacement.configId: String
    get() = when (this) {
        AdPlacement.ContinueLevel -> AdsRewardedPlacements.CONTINUE_LEVEL
        AdPlacement.BoosterGrant -> AdsRewardedPlacements.BOOSTER_GRANT
        AdPlacement.SkipLevel -> AdsRewardedPlacements.SKIP_LEVEL
        AdPlacement.StreakFreeze -> AdsRewardedPlacements.STREAK_FREEZE
    }

/**
 * The paywall moment that sits alongside this placement, if any.
 *
 * Only the two SPEC names: a third strike and a skip are the moments where the
 * player is already weighing "watch an ad or not", so Pro reads as the other
 * answer to a question they are already being asked. A booster grant and a
 * streak freeze are small and frequent, and offering to sell at every one of
 * them is the nag `paywall.sessionCap` exists to prevent.
 */
internal val AdPlacement.paywallTrigger: PaywallTrigger?
    get() = when (this) {
        AdPlacement.ContinueLevel -> PaywallTrigger.ContinueLevel
        AdPlacement.SkipLevel -> PaywallTrigger.SkipLevel
        AdPlacement.BoosterGrant,
        AdPlacement.StreakFreeze,
        -> null
    }
