package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdFormat
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.billing.PaywallTrigger

/** Which SDK format a placement asks the network for. `features.md#ads`. */
internal val AdPlacement.format: AdFormat
    get() = when (this) {
        AdPlacement.ContinueLevel,
        AdPlacement.BoosterGrant,
        AdPlacement.SkipLevel,
        AdPlacement.StreakFreeze,
        -> AdFormat.Rewarded

        AdPlacement.LevelComplete -> AdFormat.Interstitial
    }

/**
 * The paywall moment that sits alongside this placement, if any.
 *
 * Only two of the four (`features.md#pro`): a third strike and a skip are the
 * moments where the
 * player is already weighing "watch an ad or not", so Pro reads as the other
 * answer to a question they are already being asked. A booster grant and a
 * streak freeze are small and frequent, and offering to sell at every one of
 * them is the nag `paywall.sessionCap` exists to prevent.
 */
internal val AdPlacement.paywallTrigger: PaywallTrigger?
    get() = when (this) {
        AdPlacement.ContinueLevel -> PaywallTrigger.ContinueLevel
        AdPlacement.SkipLevel -> PaywallTrigger.SkipLevel
        // The interstitial is the one ad nobody asked for, and selling off the
        // back of it is the nag. The Pro answer to it is the button on the
        // same screen (SD-147).
        AdPlacement.BoosterGrant,
        AdPlacement.StreakFreeze,
        AdPlacement.LevelComplete,
        -> null
    }
