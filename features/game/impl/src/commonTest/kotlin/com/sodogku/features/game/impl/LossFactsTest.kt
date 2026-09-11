package com.sodogku.features.game.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the lose sheet is allowed to say about a run that ran out of bones.
 *
 * The sheet is the win sheet's sibling and reports the attempt as facts, so the
 * interesting part is the two facts it refuses. Both refusals are the difference
 * between a report and a telling-off, and neither is visible from the code that
 * draws the row — which is the whole reason the choice is a function rather than
 * a `buildList` inside a composable.
 */
class LossFactsTest {

    @Test
    fun aLossNeverReportsItsScoreOrItsMistakes() {
        // Mistakes is the full set of bones on every loss ever played, so the
        // pill would say the same thing every time and spend its space
        // reminding somebody how they lost. Score is whatever the strike left,
        // and `GameState.lifetimeScore` banks none of it — a number no record
        // will hold is the sheet inventing a result.
        val facts = lossFacts(dogsPlaced = 4, elapsedMs = 90_000)

        assertFalse(LossFact.Mistakes in facts)
        assertFalse(LossFact.Score in facts)
    }

    @Test
    fun aRunThatGotSomewhereReportsHowFarAndHowLong() {
        assertEquals(listOf(LossFact.Dogs, LossFact.Time), lossFacts(dogsPlaced = 4, elapsedMs = 90_000))
    }

    @Test
    fun aBoardWithNoDogsOnItSaysNothingAboutDogs() {
        // "Dogs 0/16" is the one case where the honest number really is rubbing
        // it in, and it matches the rest of the app: the recap hides a nought
        // paw rating and `elapsedLabel` answers null rather than "0:00".
        assertFalse(LossFact.Dogs in lossFacts(dogsPlaced = 0, elapsedMs = 90_000))
    }

    @Test
    fun aRunThatEndedBeforeTheClockMovedSaysNothingAboutTime() {
        assertFalse(LossFact.Time in lossFacts(dogsPlaced = 4, elapsedMs = 0))
    }

    @Test
    fun aLossWithNothingToReportDrawsNoRowAtAll() {
        // Three wrong guesses inside the first second. The sheet drops the pills
        // rather than drawing an empty strip of them.
        assertTrue(lossFacts(dogsPlaced = 0, elapsedMs = 0).isEmpty())
    }
}
