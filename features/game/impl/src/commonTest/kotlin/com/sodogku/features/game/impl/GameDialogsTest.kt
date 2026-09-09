package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.scoring.Standing
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

    /**
     * The win sheet's headline is the verdict, so a collapsed `when` would leave
     * two different runs congratulated identically — which is what S16 asked
     * for, silently undone. [Standing.Solid] keeps the sheet's original line,
     * which is a fourth distinct one and not a shared one.
     */
    @Test
    fun everyVerdictGetsItsOwnLine() {
        // By key: two `Res.string.x` reads are two objects, so a set of the
        // resources themselves would count four no matter what they point at.
        val lines = Standing.entries.map { verdictTitle(it).key }

        assertEquals(Standing.entries.size, lines.toSet().size)
    }

    /**
     * A won sheet with no verdict is not a state the ViewModel produces, but a
     * preview builds one, and the fallback has to be the neutral line rather
     * than the worst grade.
     */
    @Test
    fun anUnjudgedWinFallsBackToTheNeutralLine() {
        assertEquals(verdictTitle(Standing.Solid).key, verdictTitle(null).key)
    }

    private companion object {
        const val MinTier = 1
        const val MaxTier = 5
        const val ShippedCampaignLevels = 500
    }
}
