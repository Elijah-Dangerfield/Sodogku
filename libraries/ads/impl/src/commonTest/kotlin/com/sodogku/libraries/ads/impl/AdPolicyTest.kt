package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdFormat
import com.sodogku.libraries.ads.AdPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every ad in this app is one the player asked for.
 *
 * That is the whole monetization policy, and it is worth a test rather than a
 * paragraph because the failure mode is additive: somebody adds an interstitial
 * placement, it looks like the others in the enum, and nothing anywhere objects.
 *
 * This replaces two config tests that asserted the intrusive formats defaulted
 * *off* and that interstitials were rationed by three gates. Those guarded a
 * weaker property — the formats existed and were one remote config flip from
 * appearing. Now they do not exist, and this says so directly.
 */
class AdPolicyTest {

    @Test
    fun theOnlyFormatIsTheOneThePlayerOptsInTo() {
        assertEquals(
            listOf(AdFormat.Rewarded),
            AdFormat.entries.toList(),
            "a format that is not rewarded is an ad nobody asked for",
        )
    }

    @Test
    fun everyPlacementIsRewarded() {
        // The mapping is what turns a placement into a request, so a placement
        // that resolved to anything else would be the way an interruption got
        // back in.
        assertTrue(AdPlacement.entries.isNotEmpty(), "there are no placements, so this proves nothing")
        assertTrue(
            AdPlacement.entries.all { it.format == AdFormat.Rewarded },
            "these placements are not rewarded: " +
                AdPlacement.entries.filterNot { it.format == AdFormat.Rewarded },
        )
    }

    @Test
    fun everyPlacementIsSomethingThePlayerTapped() {
        // Named individually rather than counted. Each of these is a control the
        // player pressed knowing an ad was coming: a third strike, a booster
        // refill, a skip offer, and covering a missed daily. Adding one that is
        // not should have to be argued for here.
        assertEquals(
            setOf(
                AdPlacement.ContinueLevel,
                AdPlacement.BoosterGrant,
                AdPlacement.SkipLevel,
                AdPlacement.StreakFreeze,
            ),
            AdPlacement.entries.toSet(),
        )
    }
}
