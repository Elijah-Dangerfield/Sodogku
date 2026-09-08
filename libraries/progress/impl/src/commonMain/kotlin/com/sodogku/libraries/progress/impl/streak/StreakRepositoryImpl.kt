package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.config.values.DailyEnabled
import com.sodogku.libraries.config.values.FeatureDailyChallenge
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.db.DailyResultDao
import com.sodogku.libraries.progress.impl.daily.dayOf
import com.sodogku.libraries.progress.impl.daily.streakOn
import com.sodogku.libraries.progress.impl.daily.toOutcomes
import com.sodogku.libraries.progress.impl.daily.untilNextDay
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.progress.streak.StreakSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The streak page's view of the same `daily_result` rows the card reads.
 *
 * It goes to the dao rather than to `DailyRepository` on purpose. The clock and
 * the zone are the two things every date bug in this app has come from, and
 * borrowing today's date from another object's snapshot would put the calendar
 * one indirection away from the seam that makes it testable. Both take the zone
 * as an argument here, exactly as the daily's own arithmetic does.
 *
 * The current streak is `streakOn`, the daily's fold, called rather than
 * reimplemented. Two answers to "how long is the streak" is one more than the
 * app can afford.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class StreakRepositoryImpl(
    private val dao: DailyResultDao,
    private val progress: ProgressRepository,
    private val prompts: StreakPromptCache,
    private val clock: Clock,
    private val timeZone: DeviceTimeZone,
    private val dailyEnabled: DailyEnabled,
    private val featureEnabled: FeatureDailyChallenge,
) : StreakRepository {

    override fun observe(): Flow<StreakSummary> = dayChanges()
        .flatMapLatest { day -> dao.observeAll().map { rows -> summaryOn(day, rows.toOutcomes()) } }
        .distinctUntilChanged()

    override suspend fun summary(): StreakSummary = summaryOn(today(), dao.all().toOutcomes())

    override suspend fun pendingPrompt(): StreakPrompt {
        val outcomes = dao.all().toOutcomes()
        return promptFor(
            streak = streakOn(today(), outcomes),
            campaignClears = progress.all().count { it.state == LevelState.Completed },
            dailyPlayedEver = outcomes.values.any { it.wasPlayed() },
            dailyEnabled = enabled(),
            state = prompts.get(),
        )
    }

    override suspend fun onPromptShown(prompt: StreakPrompt) {
        when (prompt) {
            StreakPrompt.None -> Unit
            StreakPrompt.Intention -> prompts.update { it.copy(intentionShown = true) }
            is StreakPrompt.Celebrate -> prompts.update { it.copy(celebratedStreak = prompt.streak) }
        }
    }

    override suspend fun reset() {
        prompts.clear()
    }

    private fun summaryOn(day: LocalDate, outcomes: Map<LocalDate, DailyOutcome>) = StreakSummary(
        current = streakOn(day, outcomes),
        longest = longestStreakOn(day, outcomes),
        today = day,
        days = calendarOn(day, outcomes, WeeksShown),
        enabled = enabled(),
    )

    /** Mirrors `DailyRepositoryImpl.dayChanges`: the zone is re-read every pass. */
    private fun dayChanges(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            val zone = timeZone.current()
            emit(dayOf(now, zone))
            delay(untilNextDay(now, zone))
        }
    }

    private fun today(): LocalDate = dayOf(clock.now(), timeZone.current())

    private fun enabled(): Boolean = dailyEnabled() && featureEnabled()
}

/**
 * Whether the player actually sat down to this day, as opposed to buying it.
 *
 * A loss counts. Somebody who opened the daily and ran out of bones has started
 * their streak in every sense the intention moment cares about, and telling them
 * to start one would be telling them their attempt did not happen.
 */
private fun DailyOutcome.wasPlayed(): Boolean =
    this == DailyOutcome.Completed || this == DailyOutcome.Failed

/**
 * Five weeks of calendar: the current week and the four behind it.
 *
 * Enough to hold a run past the length anybody reads off a grid rather than off
 * the headline number, and short enough that thirty-five cells still fit across
 * the narrowest phone at a tappable size.
 */
private const val WeeksShown = 5
