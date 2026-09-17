package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.streak.StreakPrompt
import kotlinx.datetime.LocalDate
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
 *
 * The four cases are not independent, and the pairs are written as pairs on
 * purpose. `Lost` and `Celebrate(1)` are due on exactly the same day, so "a
 * broken run is mourned" passes on its own against an implementation that
 * mourns every run of one, and "a restart is celebrated" passes against one that
 * never mourns anything. Neither is true of both tests at once.
 *
 * Deliberately not covered here: where the broken run's length comes from, which
 * is `PlayStreakTest`, and what the page does with it, which is
 * `StreakViewModelTest` in `:features:streak:impl`.
 */
class StreakPromptsTest {

    @Test
    fun theFirstBoardIsPracticeAndIsNotInterrupted() {
        // The player is still working out what the game is. A full-screen page
        // asking them to come back every day reads as an ad.
        assertEquals(StreakPrompt.None, promptOn(Wednesday, streak = 1, boardsCleared = 1, state = Fresh))
    }

    @Test
    fun theSecondBoardEarnsTheIntention() {
        // The first board they chose.
        assertEquals(StreakPrompt.Intention, promptOn(Wednesday, streak = 1, boardsCleared = 2, state = Fresh))
    }

    @Test
    fun theIntentionIsShownOnceEver() {
        val shown = StreakPromptState(intentionShown = true)

        for (boards in 2..50) {
            assertTrue(
                promptOn(Wednesday, streak = 1, boardsCleared = boards, state = shown) !is StreakPrompt.Intention,
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
                promptOn(Wednesday, streak = streak, boardsCleared = 20, state = seen),
                "no page for a run of $streak",
            )
        }
    }

    @Test
    fun aStreakOfOneIsTheIntentionsJobOnTheDayTheIntentionCoveredIt() {
        // Two pages about the same day is one too many. The intention spends the
        // day it showed on, so the boards after it that day are quiet.
        val seen = StreakPromptState(intentionShown = true, celebratedOn = Wednesday)

        assertEquals(StreakPrompt.None, promptOn(Wednesday, streak = 1, boardsCleared = 3, state = seen))
    }

    @Test
    fun aRunThatRestartedAtOneIsCelebrated() {
        // SD-121. The player played Monday, missed Tuesday, played Wednesday,
        // and got nothing at all, because a run of one was read as "brand new
        // player, the intention has this" for everybody. They had the intention
        // months ago; today is a run they started again.
        val returning = StreakPromptState(intentionShown = true, celebratedOn = Monday)

        assertEquals(
            StreakPrompt.Celebrate(1),
            promptOn(Wednesday, streak = 1, boardsCleared = 20, state = returning),
        )
    }

    @Test
    fun aPlayerWhoOnlyEverManagesOneDayIsCelebratedEveryTime() {
        // The half of SD-121 that a number-scoped rule cannot hold. Somebody who
        // turns up once a week restarts at one on every visit, so "celebrate
        // when the run's length changes" congratulates them once and is silent
        // for the rest of the install. It is the same player from the report,
        // one week later.
        val lastWeek = StreakPromptState(intentionShown = true, celebratedOn = Monday)

        assertEquals(
            StreakPrompt.Celebrate(1),
            promptOn(Wednesday, streak = 1, boardsCleared = 21, state = lastWeek),
        )
    }

    @Test
    fun aBrandNewPlayerGetsTheIntentionRatherThanACelebrationOfOne() {
        // The other half of the same rule, and the reason it is gated on the
        // intention rather than on the number: before that moment has been
        // spent, a run of one is the intention's to talk about.
        assertEquals(StreakPrompt.None, promptOn(Wednesday, streak = 1, boardsCleared = 1, state = Fresh))
        assertEquals(StreakPrompt.Intention, promptOn(Wednesday, streak = 1, boardsCleared = 2, state = Fresh))
    }

    @Test
    fun theSameDayIsNotCelebratedTwice() {
        // Reopening the app on a day already celebrated must be quiet, which is
        // what `celebratedOn` is for.
        val state = StreakPromptState(intentionShown = true, celebratedOn = Wednesday)

        assertEquals(StreakPrompt.None, promptOn(Wednesday, streak = 6, boardsCleared = 20, state = state))
        assertEquals(
            StreakPrompt.Celebrate(7),
            promptOn(Thursday, streak = 7, boardsCleared = 20, state = state),
            "the next day still gets its page",
        )
    }

    @Test
    fun aRebuiltRunIsCelebratedAgain() {
        // Broke at 30, climbed back to 7. That is a different run and the player
        // did the work twice, so it is not retired.
        val state = StreakPromptState(intentionShown = true, celebratedOn = Monday)

        assertEquals(StreakPrompt.Celebrate(7), promptOn(Wednesday, streak = 7, boardsCleared = 60, state = state))
    }

    @Test
    fun theIntentionOutranksACelebration() {
        // They cannot both be due in practice, since the intention fires on the
        // second board ever. The order is stated rather than left to whichever
        // branch happens to be written first.
        assertEquals(StreakPrompt.Intention, promptOn(Wednesday, streak = 9, boardsCleared = 2, state = Fresh))
    }

    @Test
    fun aRunThatBrokeIsNamedRatherThanQuietlyReplacedByAOne() {
        // SD-127. The player had twelve days, missed one, came back, and the
        // page said "1" with nothing to explain it.
        val returning = StreakPromptState(intentionShown = true, celebratedOn = Monday)

        assertEquals(
            StreakPrompt.Lost(12),
            promptOn(Wednesday, streak = 1, brokenStreak = 12, boardsCleared = 40, state = returning),
        )
    }

    @Test
    fun theLossOutranksTheCelebrationOfTheRunThatReplacedIt() {
        // The collision SD-121 created, and the reason the order is written
        // down. Both are due on exactly this day: the run is one, which is what
        // `Celebrate(1)` keys on, and it is one because the run before it ended.
        // The loss wins because it is the same page saying strictly more.
        val returning = StreakPromptState(intentionShown = true, celebratedOn = Monday)

        val prompt = promptOn(Wednesday, streak = 1, brokenStreak = 9, boardsCleared = 40, state = returning)

        assertTrue(
            prompt !is StreakPrompt.Celebrate,
            "a player who just lost nine days was congratulated on having one: $prompt",
        )
    }

    @Test
    fun aRunOfOneWithNothingBehindItIsStillCelebrated() {
        // SD-121's rule has to survive SD-127's. A player whose only previous
        // run was a single day has lost nothing worth a page, and the moment
        // they get is the one they got before this item existed.
        val returning = StreakPromptState(intentionShown = true, celebratedOn = Monday)

        assertEquals(
            StreakPrompt.Celebrate(1),
            promptOn(Wednesday, streak = 1, brokenStreak = 0, boardsCleared = 20, state = returning),
            "a first run was mourned, so every new player is told they lost something",
        )
        assertEquals(
            StreakPrompt.Celebrate(1),
            promptOn(Wednesday, streak = 1, brokenStreak = 1, boardsCleared = 20, state = returning),
            "somebody who plays every other day is told they lost something on every visit",
        )
    }

    @Test
    fun aBreakIsOnlyNewsOnTheFirstDayBack() {
        // The second day back is a run of two and a break that is old news. The
        // day-scoped guard alone cannot hold this: a new day clears it, and the
        // broken run behind them does not go anywhere.
        val returning = StreakPromptState(intentionShown = true, celebratedOn = Monday)

        for (streak in 2..30) {
            assertEquals(
                StreakPrompt.Celebrate(streak),
                promptOn(Wednesday, streak = streak, brokenStreak = 12, boardsCleared = 40, state = returning),
                "day $streak of the run back was still talking about the run before it",
            )
        }
    }

    @Test
    fun aBreakIsNamedOnceADay() {
        // The second board of the same day has nothing to add, and a page that
        // came back after every clear would be worse than the silence it
        // replaces.
        val told = StreakPromptState(intentionShown = true, celebratedOn = Wednesday)

        assertEquals(
            StreakPrompt.None,
            promptOn(Wednesday, streak = 1, brokenStreak = 12, boardsCleared = 40, state = told),
        )
    }

    @Test
    fun aPlayerWhoOnlyEverPlayedDailiesIsStillToldTheirRunBroke() {
        // `boardsCleared` counts campaign clears, so a daily-only player never
        // spends the intention and `intentionShown` stays false for the life of
        // the install. A broken run of twelve is its own proof they are not new,
        // which is why the loss does not borrow the celebration's gate.
        assertEquals(
            StreakPrompt.Lost(12),
            promptOn(Wednesday, streak = 1, brokenStreak = 12, boardsCleared = 0, state = Fresh),
        )
    }

    @Test
    fun theIntentionOutranksALostRunToo() {
        // Unreachable in practice, for the same reason the celebration's case
        // is: nobody holds a run of two days without having cleared two boards.
        // Stated so the order is a decision rather than a branch ordering.
        assertEquals(
            StreakPrompt.Intention,
            promptOn(Wednesday, streak = 1, brokenStreak = 12, boardsCleared = 2, state = Fresh),
        )
    }

    private fun promptOn(
        today: LocalDate,
        streak: Int,
        boardsCleared: Int,
        state: StreakPromptState,
        brokenStreak: Int = 0,
    ) = promptFor(
        today = today,
        streak = streak,
        brokenStreak = brokenStreak,
        boardsCleared = boardsCleared,
        state = state,
    )

    private companion object {
        val Fresh = StreakPromptState()
        val Monday = LocalDate(2026, 9, 14)
        val Wednesday = LocalDate(2026, 9, 16)
        val Thursday = LocalDate(2026, 9, 17)
    }
}
