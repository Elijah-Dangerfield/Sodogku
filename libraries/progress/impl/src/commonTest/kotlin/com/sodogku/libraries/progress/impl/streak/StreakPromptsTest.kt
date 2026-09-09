package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.streak.StreakPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * When the streak is allowed to take the screen.
 *
 * Rewritten when the streak stopped being the daily's. The old rules asked
 * whether the player had ever played a daily and whether the daily feature was
 * switched on, neither of which means anything now, and celebrated on
 * milestones rather than on every day the run grew.
 */
class StreakPromptsTest {

    @Test
    fun theFirstBoardIsPracticeAndIsNotInterrupted() {
        // The player is still working out what the game is. A full-screen page
        // asking them to come back every day reads as an ad.
        assertEquals(StreakPrompt.None, promptFor(streak = 1, boardsCleared = 1, state = Fresh))
    }

    @Test
    fun theSecondBoardEarnsTheIntention() {
        // The first board they chose.
        assertEquals(StreakPrompt.Intention, promptFor(streak = 1, boardsCleared = 2, state = Fresh))
    }

    @Test
    fun theIntentionIsShownOnceEver() {
        val shown = StreakPromptState(intentionShown = true)

        for (boards in 2..50) {
            assertTrue(
                promptFor(streak = 1, boardsCleared = boards, state = shown) !is StreakPrompt.Intention,
                "the intention came back at $boards boards",
            )
        }
    }

    @Test
    fun everyDayTheRunGrowsGetsAPage() {
        // Deliberately not milestones. A run acknowledged four times a month is
        // a run nobody keeps for its own sake.
        val seen = StreakPromptState(intentionShown = true)

        for (streak in 2..40) {
            assertEquals(
                StreakPrompt.Celebrate(streak),
                promptFor(streak = streak, boardsCleared = 20, state = seen),
                "no page for a run of $streak",
            )
        }
    }

    @Test
    fun aStreakOfOneIsTheIntentionsJobAndNotCelebratedSeparately() {
        // Two pages about the same day is one too many.
        val seen = StreakPromptState(intentionShown = true)

        assertEquals(StreakPrompt.None, promptFor(streak = 1, boardsCleared = 20, state = seen))
    }

    @Test
    fun theSameDayIsNotCelebratedTwice() {
        // Reopening the app on a day already celebrated must be quiet, which is
        // what `celebratedStreak` is for.
        val state = StreakPromptState(intentionShown = true, celebratedStreak = 6)

        assertEquals(StreakPrompt.None, promptFor(streak = 6, boardsCleared = 20, state = state))
        assertEquals(
            StreakPrompt.Celebrate(7),
            promptFor(streak = 7, boardsCleared = 20, state = state),
            "the next day still gets its page",
        )
    }

    @Test
    fun aRebuiltRunIsCelebratedAgain() {
        // Broke at 30, climbed back to 7. That is a different run and the player
        // did the work twice, so it is not retired.
        val state = StreakPromptState(intentionShown = true, celebratedStreak = 30)

        assertEquals(StreakPrompt.Celebrate(7), promptFor(streak = 7, boardsCleared = 60, state = state))
    }

    @Test
    fun theIntentionOutranksACelebration() {
        // They cannot both be due in practice, since the intention fires on the
        // second board ever. The order is stated rather than left to whichever
        // branch happens to be written first.
        assertEquals(StreakPrompt.Intention, promptFor(streak = 9, boardsCleared = 2, state = Fresh))
    }

    private companion object {
        val Fresh = StreakPromptState()
    }
}
