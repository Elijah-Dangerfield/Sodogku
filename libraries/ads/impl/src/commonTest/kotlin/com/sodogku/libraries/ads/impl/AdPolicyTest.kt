package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdFormat
import com.sodogku.libraries.ads.AdPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every ad in this app is one the player asked for, except one, and this test
 * names it.
 *
 * The monetization policy from 2026-09-14 was that no ad interrupts. On
 * 2026-09-21 the owner added a floor (SD-148): a free player who has cleared
 * several levels without seeing an ad gets an interstitial between two of
 * them. That is one placement, one format, and one call site, and the failure
 * mode is still additive: a second non-rewarded placement would look like this
 * one in the enum and nothing anywhere would object. So the exception is
 * spelled out here, and a new one has to be argued for in this file.
 *
 * AppOpen and Banner stay deleted. There is no format for an ad on a board.
 */
class AdPolicyTest {

    @Test
    fun theOnlyFormatsAreRewardedAndTheBetweenLevelsInterstitial() {
        assertEquals(
            setOf(AdFormat.Rewarded, AdFormat.Interstitial),
            AdFormat.entries.toSet(),
            "a format nobody argued for; banners and app-open ads were deleted on purpose",
        )
    }

    @Test
    fun exactlyOnePlacementIsNotRewardedAndItIsTheLevelCompleteFloor() {
        // Named rather than counted. Each rewarded placement is a control the
        // player pressed knowing an ad was coming; the one that is not is the
        // floor between two cleared campaign boards.
        assertEquals(
            listOf(AdPlacement.LevelComplete),
            AdPlacement.entries.filterNot { it.format == AdFormat.Rewarded },
            "a second placement that is not rewarded has to be argued for here",
        )
        assertEquals(
            setOf(
                AdPlacement.ContinueLevel,
                AdPlacement.BoosterGrant,
                AdPlacement.SkipLevel,
                AdPlacement.StreakFreeze,
            ),
            AdPlacement.entries.filter { it.format == AdFormat.Rewarded }.toSet(),
        )
    }

    @Test
    fun theInterstitialNeverSells() {
        // The rewarded gate puts Pro up beside a continue or a skip, because
        // the player is already weighing "watch an ad or not". Nobody is
        // weighing anything at the interstitial; selling there is the nag.
        assertNull(AdPlacement.LevelComplete.paywallTrigger)
    }
}
