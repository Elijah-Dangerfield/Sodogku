package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.db.PlayDayDao
import com.sodogku.libraries.progress.db.PlayDayEntity
import com.sodogku.libraries.progress.impl.daily.dayOf
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
 * The streak, folded from `play_day`.
 *
 * **Not from `daily_result` any more, and that is the whole change.** The streak
 * used to be "days you played the daily", which quietly made two unrelated ideas
 * into one: a player who cleared six campaign boards on a Tuesday had done
 * nothing for their streak. The daily is only a way to know you are solving the
 * same board as everybody else. The streak is about turning up, so any finished
 * board keeps it.
 *
 * It goes to the dao rather than to another repository on purpose. The clock and
 * the zone are the two things every date bug in this app has come from, and
 * borrowing today's date from another object's snapshot would put the calendar
 * one indirection away from the seam that makes it testable.
 *
 * It also no longer reads the daily's config. A streak that switched itself off
 * because the daily was disabled made sense when the daily was the only way to
 * feed it, and is now just a way to lose a run to a remote flag.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class StreakRepositoryImpl(
    private val dao: PlayDayDao,
    private val progress: ProgressRepository,
    private val prompts: StreakPromptCache,
    private val clock: Clock,
    private val timeZone: DeviceTimeZone,
) : StreakRepository {

    override fun observe(): Flow<StreakSummary> = dayChanges()
        .flatMapLatest { day -> dao.observeAll().map { rows -> summaryOn(day, rows.toDates()) } }
        .distinctUntilChanged()

    override suspend fun summary(): StreakSummary = summaryOn(today(), dao.all().toDates())

    override suspend fun onBoardCompleted() {
        dao.insertIfAbsent(PlayDayEntity(date = today().toString()))
    }

    override suspend fun pendingPrompt(): StreakPrompt {
        val played = dao.all().toDates()
        val day = today()
        return promptFor(
            today = day,
            streak = playStreakOn(day, played),
            boardsCleared = progress.all().count { it.state == LevelState.Completed },
            state = prompts.get(),
        )
    }

    /**
     * Both pages spend the day, and the intention spends it too.
     *
     * It prints the run the player already has, so it is that run's celebration
     * and the next board of the same day has nothing left to say.
     */
    override suspend fun onPromptShown(prompt: StreakPrompt) {
        when (prompt) {
            StreakPrompt.None -> Unit
            StreakPrompt.Intention -> prompts.update {
                it.copy(intentionShown = true, celebratedOn = today())
            }

            is StreakPrompt.Celebrate -> prompts.update { it.copy(celebratedOn = today()) }
        }
    }

    override suspend fun reset() {
        prompts.clear()
    }

    private fun summaryOn(day: LocalDate, played: Set<LocalDate>) = StreakSummary(
        current = playStreakOn(day, played),
        longest = longestPlayStreak(played),
        today = day,
        days = playCalendarOn(day, played, WeeksShown),
        playedToday = day in played,
        untilTomorrow = untilNextDay(clock.now(), timeZone.current()),
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
}

private fun List<PlayDayEntity>.toDates(): Set<LocalDate> =
    mapTo(mutableSetOf()) { LocalDate.parse(it.date) }

/**
 * Five weeks of calendar: the current week and the four behind it.
 *
 * Enough to hold a run past the length anybody reads off a grid rather than off
 * the headline number, and short enough that thirty-five cells still fit across
 * the narrowest phone at a tappable size.
 */
private const val WeeksShown = 5
