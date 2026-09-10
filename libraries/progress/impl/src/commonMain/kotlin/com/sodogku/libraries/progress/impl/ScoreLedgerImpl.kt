package com.sodogku.libraries.progress.impl

import com.sodogku.libraries.progress.LifetimeScore
import com.sodogku.libraries.progress.ScoreEvent
import com.sodogku.libraries.progress.ScoreLedger
import com.sodogku.libraries.progress.db.ScoreEventDao
import com.sodogku.libraries.progress.db.ScoreEventEntity
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Room-backed [ScoreLedger]. Everything interesting about it is in
 * [LifetimeScore.bankedSince]; this is the disk around that.
 *
 * No mutex, unlike its neighbours. Every write is an insert of a new row rather
 * than a read-modify-write of an existing one, so two concurrent banks produce
 * two rows and no race — the shape that made the mutex necessary in
 * `ProgressRepositoryImpl` is the shape this table does not have.
 *
 * Pruning rides along with the write instead of running on a timer. A ledger
 * nobody is adding to is not growing either, so there is nothing for a timer to
 * catch that this misses.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class ScoreLedgerImpl(
    private val dao: ScoreEventDao,
    private val clock: Clock,
) : ScoreLedger {

    override suspend fun bank(points: Int) {
        if (points <= 0) return
        val now = clock.now().toEpochMilliseconds()
        dao.insert(ScoreEventEntity(atMillis = now, points = points))
        dao.deleteBefore(now - ScoreLedger.RETENTION_MILLIS)
    }

    override suspend fun bankedSince(startMillis: Long): Int =
        LifetimeScore.bankedSince(dao.all().map { ScoreEvent(it.atMillis, it.points) }, startMillis)
}
