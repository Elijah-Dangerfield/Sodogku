package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.config.values.AdsRewardedPlacements
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `AdPlacement.configId` and the keys of `ads.rewardedPlacements` are one
 * vocabulary, and this is the only place that can say so: the enum is in
 * `:libraries:ads` and the config default is in `:libraries:config`, and neither
 * module sees the other.
 *
 * Drift here is silent in both directions. A placement whose id stops matching
 * its config key is simply never gated. `isEnabled` treats an unknown id as
 * enabled, on purpose, so the kill switch for that placement stops working and
 * nothing anywhere complains.
 */
class AdPlacementIdsTest {

    @Test
    fun everyPlacementIsAKeyTheRewardedConfigGatesOn() {
        assertEquals(
            AdPlacement.entries.map { it.configId }.toSet(),
            AdsRewardedPlacements.AllPlacementsOn.keys,
            "a placement id and its config key have drifted apart",
        )
    }
}
