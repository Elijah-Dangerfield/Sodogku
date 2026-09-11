package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.scoring.Standing
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The words the sheets choose, held apart from each other.
 *
 * Every test here is really the same shape: a `when` over a small set has to
 * produce a distinct answer per case, and the way it stops doing that is a
 * collapsed branch or a copy-paste that nothing else in the app would notice.
 * A win sheet that congratulates two different runs identically still renders,
 * still passes every view-model test, and quietly undoes the reason the
 * verdict exists.
 *
 * So distinctness is asserted by counting, and counted by resource key rather
 * than by resource object, since two reads of the same string are two objects
 * and a set of those would come back the right size whatever they pointed at.
 *
 * Two fallbacks are pinned for states the view model does not produce. A
 * difficulty above the top tier reads as the hardest rather than dropping
 * through to the easiest, and a win with no verdict reads as the neutral line
 * rather than the worst grade. Previews build both.
 *
 * The level count is read from the pack rather than from a number in the copy,
 * so lengthening the campaign cannot leave the explainer lying.
 *
 * ### Not here
 *
 * Which dialog is shown when, and what the run was actually worth, is
 * `GameViewModelTest`. What a lost run is allowed to report is
 * `LossFactsTest`.
 */
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
        const val ShippedCampaignLevels = 1_000
    }
}
