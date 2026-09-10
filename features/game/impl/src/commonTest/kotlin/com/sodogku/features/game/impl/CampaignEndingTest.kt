package com.sodogku.features.game.impl

import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.progress.daily.DailyStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate

/**
 * Where the campaign ends, as arithmetic rather than as a playthrough.
 *
 * `GameViewModelTest` plays the last level and the one before it, which is the
 * behaviour. This is the boundary on its own, because two of the ways to get it
 * wrong are invisible from inside a playthrough: an ending that fires on the
 * daily needs a daily whose id happens to reach the end of the campaign, and
 * whether such a daily exists depends on how long the two packs are this month.
 * A predicate can be asked directly.
 */
class CampaignEndingTest {

    @Test
    fun theLastCampaignLevelEndsIt() {
        assertTrue(endsTheCampaign(LevelPacks.lastCampaignLevelId, isDaily = false))
    }

    @Test
    fun theLevelBeforeItDoesNot() {
        assertFalse(endsTheCampaign(LevelPacks.lastCampaignLevelId - 1, isDaily = false))
    }

    @Test
    fun anIdPastTheEndStillEndsIt() {
        // Only reachable by shipping a shorter pack over a longer one. The
        // player is past the end either way and the ending is the one screen
        // that can say so.
        assertTrue(endsTheCampaign(LevelPacks.lastCampaignLevelId + 1, isDaily = false))
    }

    @Test
    fun noDailyEverEndsTheCampaign() {
        // The two packs share a number line, so a daily id says nothing about
        // campaign progress. Every id in the daily pool, plus the campaign's own
        // last id, asked as a daily.
        (LevelPacks.daily.levels.map { it.id } + LevelPacks.lastCampaignLevelId).forEach { id ->
            assertFalse(
                endsTheCampaign(id, isDaily = true),
                "daily level $id ended the campaign for somebody still playing it",
            )
        }
    }

    @Test
    fun theEndingShowsTheDailyOnlyWhenThereIsOneToShow() {
        // The sheet's primary button is `PlayDaily`, which refuses on a killed
        // daily. On any other sheet that is a dead tap; on this one it is the
        // only way off the screen, so the control has to be absent rather than
        // inert. This is the condition the sheet branches on.
        assertFalse(GameState().dailyOffered, "nothing loaded yet is not an offer")
        assertFalse(GameState(daily = dailyStatus(enabled = false)).dailyOffered)
        assertTrue(GameState(daily = dailyStatus(enabled = true)).dailyOffered)
    }

    private fun dailyStatus(enabled: Boolean) = DailyStatus(
        date = LocalDate(2026, 9, 10),
        packIndex = 0,
        levelId = 1,
        result = null,
        streak = 0,
        freezeOffer = null,
        restoreOffer = null,
        resetsIn = 6.hours,
        enabled = enabled,
    )
}
