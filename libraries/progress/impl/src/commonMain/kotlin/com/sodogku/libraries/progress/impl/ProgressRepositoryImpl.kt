package com.sodogku.libraries.progress.impl

import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.ScoreLedger
import com.sodogku.libraries.progress.db.LevelProgressDao
import com.sodogku.libraries.progress.db.LevelProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Room-backed [ProgressRepository].
 *
 * Every write is read-modify-write, so a mutex serializes them: the win sheet
 * writing a clear while the board writes the next attempt would otherwise be a
 * last-writer-wins race over a record that is supposed to only improve.
 *
 * Rows are written lazily — a level nobody has opened has no row, and
 * [LevelRecord.unplayed] stands in for it. That keeps the table proportional to
 * what the player has played rather than to the size of the pack, and it means
 * shipping more levels doesn't need a backfill.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class ProgressRepositoryImpl(
    private val dao: LevelProgressDao,
    private val clock: Clock,
    private val ledger: ScoreLedger,
) : ProgressRepository {

    private val writes = Mutex()

    override fun observe(levelId: Int): Flow<LevelRecord> = dao.observe(levelId)
        .map { it?.toRecord() ?: LevelRecord.unplayed(levelId) }
        .distinctUntilChanged()

    override suspend fun record(levelId: Int): LevelRecord =
        dao.find(levelId)?.toRecord() ?: LevelRecord.unplayed(levelId)

    override suspend fun all(): List<LevelRecord> = dao.all().map { it.toRecord() }

    override suspend fun unlockedThrough(): Int = dao.all()
        .filter { it.toRecord().state != LevelState.Locked }
        .maxOfOrNull { it.levelId }
        ?.coerceAtLeast(LevelRecord.FIRST_LEVEL_ID)
        ?: LevelRecord.FIRST_LEVEL_ID

    override suspend fun onAttemptStarted(levelId: Int) {
        val now = clock.now().toEpochMilliseconds()
        update(levelId) { current ->
            current.copy(
                state = current.state.orBetter(LevelState.Unlocked),
                attempts = current.attempts + 1,
                lastPlayedAt = now,
            )
        }
    }

    /**
     * Every metric here is judged on its own, and a run that wins one of them
     * does not take the others with it.
     *
     * Stated because the time-to-beat feature makes it a live question: a replay
     * chased purely for the clock will usually bank *less*, since rushing costs
     * strikes and leans on boosters. Letting the faster run own the whole row
     * would mean a player who beat their time silently lost their three-paw
     * clear and watched their lifetime total go down — `lifetimeScore` prices
     * this board at [LevelRecord.bestScore], so overwriting it is money off the
     * headline number. The record keeps the fast time and the high score, from
     * different runs, and neither is a lie: they are both bests, which is what
     * the type says they are.
     */
    override suspend fun onCompleted(levelId: Int, score: Int, paws: Int, timeMs: Long) {
        val now = clock.now().toEpochMilliseconds()
        // Captured out of the transform rather than read back afterwards,
        // because the number wanted is the difference the write made and only
        // the transform sees both sides of it. A replay that failed to beat the
        // old best gives a negative, which the ledger declines to record.
        var gained = 0
        update(levelId) { current ->
            gained = score - current.bestScore
            current.copy(
                state = current.state.orBetter(LevelState.Completed),
                bestScore = maxOf(current.bestScore, score),
                bestPaws = maxOf(current.bestPaws, paws),
                bestTimeMs = bestTimeOf(current.bestTimeMs, timeMs),
                firstCompletedAt = if (current.firstCompletedAt == 0L) now else current.firstCompletedAt,
                lastPlayedAt = now,
            )
        }
        ledger.bank(gained)
        unlock(levelId + 1)
    }

    override suspend fun onSkipped(levelId: Int) {
        val now = clock.now().toEpochMilliseconds()
        update(levelId) { current ->
            current.copy(
                state = current.state.orBetter(LevelState.Skipped),
                lastPlayedAt = now,
            )
        }
        unlock(levelId + 1)
    }

    override suspend fun reset() {
        writes.withLock { dao.deleteAll() }
    }

    /**
     * The next level opens on a skip as well as on a clear — a skip the player
     * can't move past is just a dead end.
     */
    private suspend fun unlock(levelId: Int) {
        update(levelId) { it.copy(state = it.state.orBetter(LevelState.Unlocked)) }
    }

    private suspend fun update(levelId: Int, change: (LevelRecord) -> LevelRecord) {
        writes.withLock {
            val current = dao.find(levelId)?.toRecord() ?: LevelRecord.unplayed(levelId)
            dao.upsert(change(current).toEntity())
        }
    }
}

/**
 * Ranks the states by how far through a level they are, which is *not* the
 * declaration order: a skipped level is further along than a merely unlocked
 * one, and a cleared one furthest of all. Starting an attempt on a level that
 * is already cleared must not demote it back to unlocked.
 */
private fun LevelState.orBetter(other: LevelState): LevelState =
    if (other.progressRank > progressRank) other else this

private val LevelState.progressRank: Int
    get() = when (this) {
        LevelState.Locked -> 0
        LevelState.Unlocked -> 1
        LevelState.Skipped -> 2
        LevelState.Completed -> 3
    }

/**
 * Best time is the *lowest* non-zero one, because `0` on the record means
 * "never cleared" rather than "cleared instantly". A run reporting no duration
 * leaves the existing best alone for the same reason.
 */
private fun bestTimeOf(current: Long, candidate: Long): Long = when {
    candidate <= 0L -> current
    current == 0L -> candidate
    else -> minOf(current, candidate)
}

/**
 * An unreadable state name means the row was written by a build whose enum we
 * no longer have. The row existing at all proves the player reached the level,
 * so unlocked is the honest floor — locking them out of a level they have
 * played would be the worse failure.
 */
private fun String.toLevelState(): LevelState =
    LevelState.entries.firstOrNull { it.name == this } ?: LevelState.Unlocked

private fun LevelProgressEntity.toRecord(): LevelRecord = LevelRecord(
    levelId = levelId,
    state = state.toLevelState(),
    bestScore = bestScore,
    bestPaws = bestPaws,
    bestTimeMs = bestTimeMs,
    attempts = attempts,
    firstCompletedAt = firstCompletedAt,
    lastPlayedAt = lastPlayedAt,
)

private fun LevelRecord.toEntity(): LevelProgressEntity = LevelProgressEntity(
    levelId = levelId,
    state = state.name,
    bestScore = bestScore,
    bestPaws = bestPaws,
    bestTimeMs = bestTimeMs,
    attempts = attempts,
    firstCompletedAt = firstCompletedAt,
    lastPlayedAt = lastPlayedAt,
)
