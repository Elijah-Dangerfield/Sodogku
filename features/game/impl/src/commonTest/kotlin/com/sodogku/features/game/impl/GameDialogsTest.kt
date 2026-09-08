package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelPacks
import kotlin.test.Test
import kotlin.test.assertEquals

class GameDialogsTest {

    /**
     * The solver rates a board 1 to 5 and the explainer turns that into a word.
     * Every tier needs its own, or the line stops carrying information — which is
     * exactly what a collapsed `when` or a stray copy-paste produces, and what
     * nothing else in the app would notice.
     */
    @Test
    fun everyDifficultyTierGetsItsOwnWord() {
        val labels = (MinTier..MaxTier).map(::difficultyLabel)

        assertEquals(MaxTier, labels.toSet().size)
    }

    /**
     * `difficultyLabel` ends in an `else`, so a tier the solver does not emit
     * today still reads as the hardest rather than falling through to the
     * easiest.
     */
    @Test
    fun aTierAboveTheTopReadsAsTheHardest() {
        assertEquals(difficultyLabel(MaxTier), difficultyLabel(MaxTier + 1))
    }

    /**
     * The Level explainer says "number N of M" with M read from the pack rather
     * than the 500 the spec names, so a content update that lengthens the
     * campaign cannot leave the copy lying.
     */
    @Test
    fun theCampaignPackIsTheSourceOfTheLevelCount() {
        assertEquals(ShippedCampaignLevels, LevelPacks.campaign.size)
    }

    private companion object {
        const val MinTier = 1
        const val MaxTier = 5
        const val ShippedCampaignLevels = 500
    }
}
