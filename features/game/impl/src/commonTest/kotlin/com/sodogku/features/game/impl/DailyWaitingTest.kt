package com.sodogku.features.game.impl

import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.progress.daily.DailyStatus
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate

/**
 * The dot on the drawer button.
 *
 * Small enough to look obviously right, and worth pinning anyway: the whole
 * value of the dot is that it is absent on the days there is nothing to say, so
 * a version that is always on is worse than no dot at all.
 */
class DailyWaitingTest {

    @Test
    fun anUnplayedDailyIsWaiting() {
        assertTrue(stateWith(status(result = null)).dailyWaiting)
    }

    @Test
    fun everyWayOfFinishingTheDayClearsIt() {
        // Listed rather than sampled, because the production code tests
        // `result == null` precisely so it does not have to enumerate these. If
        // an outcome is ever added that should leave the dot up, this fails and
        // makes somebody decide.
        DailyOutcome.entries.forEach { outcome ->
            val state = stateWith(status(result = result(outcome)))
            assertTrue(!state.dailyWaiting, "$outcome left the dot showing")
        }
    }

    @Test
    fun aDisabledDailyIsNeverWaiting() {
        // A dot pointing at a card that is not there is a support ticket.
        assertTrue(!stateWith(status(result = null, enabled = false)).dailyWaiting)
    }

    @Test
    fun noDailyLoadedYetIsNotWaiting() {
        // Null while the first read is in flight. Flashing a dot on every cold
        // boot and taking it away again is worse than showing it a moment late.
        assertTrue(!GameState().dailyWaiting)
    }

    private fun stateWith(status: DailyStatus) = GameState(daily = status)

    private fun status(result: DailyResult?, enabled: Boolean = true) = DailyStatus(
        date = Today,
        packIndex = 0,
        levelId = 1,
        result = result,
        streak = 0,
        freezeOffer = null,
        restoreOffer = null,
        resetsIn = 6.hours,
        enabled = enabled,
    )

    private fun result(outcome: DailyOutcome) = DailyResult(
        date = Today,
        levelIndex = 0,
        outcome = outcome,
        score = 0,
        paws = 0,
        timeMs = 0L,
    )

    private companion object {
        val Today = LocalDate(2026, 9, 10)
    }
}
