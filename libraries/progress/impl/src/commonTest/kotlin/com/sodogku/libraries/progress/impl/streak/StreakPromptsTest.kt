package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.streak.StreakPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which ceremony fires, and mostly which one does not.
 *
 * Every one of these is an interruption, so the tests that matter are the
 * refusals. A rule phrased as "the intention shows after three clears" is
 * satisfied by a function that always returns `Intention`, which is why each
 * case below pins the *other* answer as well.
 */
class StreakPromptsTest {

    @Test
    fun nothingIsOfferedBeforeTheThirdClear() {
        assertEquals(StreakPrompt.None, prompt(campaignClears = 0))
        assertEquals(StreakPrompt.None, prompt(campaignClears = 2))
        assertEquals(
            StreakPrompt.Intention,
            prompt(campaignClears = 3),
            "the third clear is the moment; the first two are the game earning the right to ask",
        )
        assertEquals(
            StreakPrompt.Intention,
            prompt(campaignClears = 40),
            "and a player who got there without ever seeing it still gets it",
        )
    }

    @Test
    fun theIntentionIsNeverShownTwice() {
        val shown = StreakPromptState(intentionShown = true)

        assertEquals(StreakPrompt.None, prompt(campaignClears = 3, state = shown))
        assertEquals(StreakPrompt.None, prompt(campaignClears = 99, state = shown))
    }

    @Test
    fun aPlayerWhoAlreadyPlaysTheDailyIsNotToldToStart() {
        assertEquals(
            StreakPrompt.None,
            prompt(campaignClears = 3, dailyPlayedEver = true),
            "the daily card is in the pane from level one, so this is reachable and it would be a lie",
        )
    }

    @Test
    fun aDisabledDailyAsksForNothing() {
        assertEquals(StreakPrompt.None, prompt(campaignClears = 3, dailyEnabled = false))
        assertEquals(
            StreakPrompt.None,
            prompt(streak = 7, dailyPlayedEver = true, dailyEnabled = false, state = intentionDone()),
            "a milestone page for a feature that is switched off leads nowhere",
        )
    }

    @Test
    fun milestonesAreThreeSevenFourteenAndEveryThirtyAfterThirty() {
        val celebrated = (1..95).filter { isMilestone(it) }

        assertEquals(listOf(3, 7, 14, 30, 60, 90), celebrated)
    }

    @Test
    fun aMilestoneIsCelebratedOnceAndThenLeftAlone() {
        val started = intentionDone()

        assertEquals(StreakPrompt.Celebrate(7), prompt(streak = 7, state = started))
        assertEquals(
            StreakPrompt.None,
            prompt(streak = 7, state = started.copy(celebratedStreak = 7)),
            "opening the app again on the same day must not replay it",
        )
        assertEquals(
            StreakPrompt.None,
            prompt(streak = 8, state = started.copy(celebratedStreak = 7)),
            "and the day after a milestone is not itself one",
        )
    }

    @Test
    fun aRunThatBreaksAndClimbsBackEarnsItsMilestoneAgain() {
        val afterAThirtyDayRun = intentionDone().copy(celebratedStreak = 30)

        assertEquals(
            StreakPrompt.Celebrate(7),
            prompt(streak = 7, state = afterAThirtyDayRun),
            "they did the seven days a second time; a set of retired milestones would swallow it",
        )
    }

    @Test
    fun theIntentionOutranksACelebration() {
        // Not reachable through the repository, since a streak means a daily
        // was played, but the ordering is stated rather than left to argument.
        val both = prompt(streak = 7, campaignClears = 3, state = StreakPromptState())

        assertEquals(StreakPrompt.Intention, both)
    }

    @Test
    fun aStreakWithNoMilestoneSaysNothingAtAll() {
        val started = intentionDone()

        val quiet = (1..29).filter { prompt(streak = it, state = started) == StreakPrompt.None }

        assertEquals((1..29).toList() - listOf(3, 7, 14), quiet, "26 of the first 29 days say nothing")
        assertTrue(quiet.isNotEmpty())
    }

    private fun intentionDone() = StreakPromptState(intentionShown = true)

    private fun prompt(
        streak: Int = 0,
        campaignClears: Int = 0,
        dailyPlayedEver: Boolean = false,
        dailyEnabled: Boolean = true,
        state: StreakPromptState = StreakPromptState(),
    ): StreakPrompt = promptFor(
        streak = streak,
        campaignClears = campaignClears,
        dailyPlayedEver = dailyPlayedEver,
        dailyEnabled = dailyEnabled,
        state = state,
    )
}
