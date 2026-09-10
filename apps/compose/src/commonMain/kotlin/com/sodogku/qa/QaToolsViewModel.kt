package com.sodogku.qa

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.db.PlayDayDao
import com.sodogku.libraries.progress.db.PlayDayEntity
import com.sodogku.libraries.progress.streak.StreakRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The on-device QA menu, debug builds only.
 *
 * It writes straight to `PlayDayDao` rather than going through
 * [StreakRepository], and that is deliberate: the repository's job is to record
 * that a board was finished *today*, and a tool for testing streaks has to write
 * days that are not today. Adding a "pretend it was Tuesday" method to the
 * production interface to serve a debug screen is how a debug affordance ends up
 * shipping.
 *
 * Everything here is destructive and none of it asks. That is the right trade
 * for a screen only reachable in a debug build, and a confirm on every row would
 * make the menu slower than editing the database by hand.
 */
@OptIn(ExperimentalTime::class)
@Inject
class QaToolsViewModel(
    private val playDays: PlayDayDao,
    private val streak: StreakRepository,
    private val clock: Clock,
    private val timeZone: DeviceTimeZone,
) : SEAViewModel<QaToolsState, QaToolsEvent, QaToolsAction>(
    initialStateArg = QaToolsState(),
) {

    init {
        takeAction(QaToolsAction.Refresh)
    }

    override suspend fun handleAction(action: QaToolsAction) {
        when (action) {
            QaToolsAction.Refresh -> action.refresh()
            QaToolsAction.MarkTodayPlayed -> action.write { playDays.insertIfAbsent(PlayDayEntity(today().toString())) }
            is QaToolsAction.SeedStreak -> action.write { seed(action.days) }
            QaToolsAction.ClearPlayDays -> action.write { playDays.deleteAll() }
            QaToolsAction.ResetPrompts -> action.write { streak.reset() }
            QaToolsAction.Back -> sendEvent(QaToolsEvent.Back)
        }
    }

    /**
     * Backdates [days] consecutive days ending yesterday.
     *
     * Ending *yesterday* rather than today on purpose: that leaves today
     * unplayed, so the very next board finished is the one that bumps the streak
     * and fires the celebration. Seeding through today would set the number and
     * leave nothing to watch, which is the whole reason to seed it.
     */
    private suspend fun seed(days: Int) {
        val yesterday = today().minus(DatePeriod(days = 1))
        repeat(days) { back ->
            playDays.insertIfAbsent(PlayDayEntity(yesterday.minus(DatePeriod(days = back)).toString()))
        }
    }

    private suspend fun QaToolsAction.write(block: suspend () -> Unit) {
        Catching { block() }.logOnFailure { "QA tool failed" }
        refresh()
    }

    private suspend fun QaToolsAction.refresh() {
        val summary = Catching { streak.summary() }.logOnFailure { "QA refresh failed" }.getOrNull()
        val days = Catching { playDays.all() }.getOrNull().orEmpty()
        updateState {
            it.copy(
                loaded = true,
                streak = summary?.current ?: 0,
                longest = summary?.longest ?: 0,
                playedToday = summary?.playedToday == true,
                daysRecorded = days.size,
                today = today().toString(),
            )
        }
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone.current()).date
}

data class QaToolsState(
    val loaded: Boolean = false,
    val streak: Int = 0,
    val longest: Int = 0,
    val playedToday: Boolean = false,
    val daysRecorded: Int = 0,
    val today: String = "",
)

sealed interface QaToolsEvent {
    data object Back : QaToolsEvent
}

sealed interface QaToolsAction {
    data object Refresh : QaToolsAction
    data object MarkTodayPlayed : QaToolsAction
    data class SeedStreak(val days: Int) : QaToolsAction
    data object ClearPlayDays : QaToolsAction
    data object ResetPrompts : QaToolsAction
    data object Back : QaToolsAction
}
