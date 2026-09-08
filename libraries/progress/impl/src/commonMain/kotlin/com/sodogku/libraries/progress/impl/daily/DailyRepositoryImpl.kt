package com.sodogku.libraries.progress.impl.daily

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.config.values.DailyEnabled
import com.sodogku.libraries.config.values.DailyFreezesPerMonth
import com.sodogku.libraries.config.values.DailyPoolOffset
import com.sodogku.libraries.config.values.FeatureDailyChallenge
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyRepository
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.daily.FreezeResult
import com.sodogku.libraries.progress.db.DailyResultDao
import com.sodogku.libraries.progress.db.DailyResultEntity
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
 * Room-backed [DailyRepository].
 *
 * Reads the whole `daily_result` table for every answer. That is a few hundred
 * rows after a few years, and it is what makes the streak a fold rather than a
 * counter — a query that only fetched the recent tail would be a cache, and a
 * cache is the thing this design exists to avoid.
 *
 * Both writes go through `insertIfAbsent`, so the one-attempt rule is enforced by
 * the primary key rather than by a check the caller could skip. There is
 * deliberately no update path on this table at all.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DailyRepositoryImpl(
    private val dao: DailyResultDao,
    private val clock: Clock,
    private val timeZone: DeviceTimeZone,
    private val adGate: AdGate,
    private val entitlements: Entitlements,
    private val dailyEnabled: DailyEnabled,
    private val featureEnabled: FeatureDailyChallenge,
    private val poolOffset: DailyPoolOffset,
    private val freezesPerMonth: DailyFreezesPerMonth,
) : DailyRepository {

    override fun observe(): Flow<DailyStatus> = dayChanges()
        .flatMapLatest { day -> dao.observeAll().map { rows -> statusOn(day, rows.toResults()) } }
        .distinctUntilChanged()

    override suspend fun status(): DailyStatus = statusOn(today(), dao.all().toResults())

    override suspend fun history(): List<DailyResult> = dao.all().mapNotNull { it.toResult() }

    override suspend fun onCompleted(date: LocalDate, score: Int, paws: Int, timeMs: Long) {
        write(date, DailyOutcome.Completed, score = score, paws = paws, timeMs = timeMs)
    }

    override suspend fun onFailed(date: LocalDate, timeMs: Long) {
        write(date, DailyOutcome.Failed, score = 0, paws = 0, timeMs = timeMs)
    }

    override suspend fun useFreeze(): FreezeResult {
        val today = today()
        val results = dao.all().toResults()
        val offer = freezeOfferOn(today, results, freezesPerMonth()) ?: return FreezeResult.NothingToFreeze
        if (offer.freezesRemaining <= 0) return FreezeResult.NoneLeft

        val granted = entitlements.isPro.value ||
            adGate.showRewarded(AdPlacement.StreakFreeze) != RewardOutcome.Dismissed
        if (!granted) return FreezeResult.Declined

        write(offer.missedDate, DailyOutcome.Frozen, score = 0, paws = 0, timeMs = 0L)
        return FreezeResult.Applied(offer.missedDate, streakOn(today(), dao.all().toOutcomes()))
    }

    override suspend fun reset() {
        dao.deleteAll()
    }

    private suspend fun write(
        date: LocalDate,
        outcome: DailyOutcome,
        score: Int,
        paws: Int,
        timeMs: Long,
    ) {
        dao.insertIfAbsent(
            DailyResultEntity(
                date = date.toString(),
                levelIndex = packIndexFor(date),
                outcome = outcome.name,
                score = score,
                paws = paws,
                timeMs = timeMs,
            )
        )
    }

    private fun statusOn(day: LocalDate, results: Map<LocalDate, DailyResult>): DailyStatus {
        val index = packIndexFor(day)
        return DailyStatus(
            date = day,
            packIndex = index,
            levelId = LevelPacks.daily[index].id,
            result = results[day],
            streak = streakOn(day, results.mapValues { it.value.outcome }),
            freezeOffer = freezeOfferOn(day, results, freezesPerMonth())
                ?.takeIf { it.freezesRemaining > 0 },
            resetsIn = untilNextDay(clock.now(), timeZone.current()),
            enabled = dailyEnabled() && featureEnabled(),
        )
    }

    /**
     * Emits the current local date, then again each time it changes.
     *
     * The zone is re-read on every pass rather than captured once, so a player who
     * lands in a new zone gets the right board at their new midnight instead of
     * their old one.
     */
    private fun dayChanges(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            val zone = timeZone.current()
            emit(dayOf(now, zone))
            delay(untilNextDay(now, zone))
        }
    }

    private fun today(): LocalDate = dayOf(clock.now(), timeZone.current())

    private fun packIndexFor(date: LocalDate): Int =
        LevelPacks.dailyIndexFor(date.toEpochDays() + poolOffset())
}

/**
 * A row whose date or outcome no longer parses is dropped rather than guessed at.
 *
 * It can only come from a build we no longer have, and the two ways to be wrong
 * are not symmetric: a dropped row costs the player a day of streak they can see
 * and freeze, while a row invented at the wrong date silently shifts every
 * calculation that walks past it.
 */
private fun DailyResultEntity.toResult(): DailyResult? {
    val parsedDate = Catching { LocalDate.parse(date) }.getOrNull() ?: return null
    val parsedOutcome = DailyOutcome.entries.firstOrNull { it.name == outcome } ?: return null
    return DailyResult(
        date = parsedDate,
        levelIndex = levelIndex,
        outcome = parsedOutcome,
        score = score,
        paws = paws,
        timeMs = timeMs,
    )
}

private fun List<DailyResultEntity>.toResults(): Map<LocalDate, DailyResult> =
    mapNotNull { it.toResult() }.associateBy { it.date }

private fun List<DailyResultEntity>.toOutcomes(): Map<LocalDate, DailyOutcome> =
    toResults().mapValues { it.value.outcome }
