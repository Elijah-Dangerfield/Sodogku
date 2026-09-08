package com.sodogku.libraries.achievements.impl

import com.sodogku.libraries.achievements.Achievement
import com.sodogku.libraries.achievements.AchievementEngine
import com.sodogku.libraries.achievements.AchievementId
import com.sodogku.libraries.achievements.AchievementState
import com.sodogku.libraries.achievements.Achievements
import com.sodogku.libraries.achievements.AchievementsRepository
import com.sodogku.libraries.achievements.LevelResult
import com.sodogku.libraries.achievements.PlayMode
import com.sodogku.libraries.achievements.db.AchievementDao
import com.sodogku.libraries.achievements.db.AchievementFactEntity
import com.sodogku.libraries.achievements.db.AchievementUnlockEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Room-backed [AchievementsRepository].
 *
 * There is no in-memory tally. Every read folds the stored fact log from
 * scratch, which sounds wasteful and is not: a completionist's log is 500
 * campaign rows plus one a day, and the fold is arithmetic over a list. What it
 * buys is that there is exactly one representation of a player's progress — the
 * facts — so no cached counter can disagree with the history it came from, and
 * an achievement added in a later release is simply picked up by the next fold.
 * If the log ever gets long enough to matter, materializing counters behind this
 * interface is a change nobody outside the file can see.
 *
 * The mutex serializes `record`, which is a read-modify-write over two tables:
 * the daily and a campaign level finishing at the same moment would otherwise
 * both compute "already announced" from the same snapshot and race.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AchievementsRepositoryImpl(
    private val dao: AchievementDao,
) : AchievementsRepository {

    private val writes = Mutex()

    override fun observe(): Flow<AchievementState> =
        combine(dao.observeFacts(), dao.observeUnlocks()) { facts, unlocks -> stateOf(facts, unlocks) }
            .distinctUntilChanged()

    override suspend fun state(): AchievementState = stateOf(dao.facts(), dao.unlocks())

    /**
     * The fact goes in *first*, then the whole log is re-folded. Doing it in
     * that order is what makes a duplicate record harmless: the unique index
     * drops the second copy, so the counters the fold sees are the counters the
     * player actually earned, rather than the ones an at-least-once caller
     * happened to report.
     *
     * What gets announced is measured against the stored unlocks, not against
     * the state before this attempt. So an achievement that a *previous* release
     * did not have, but that this player's history already earns, is granted and
     * celebrated here — with the date of the attempt that really earned it.
     */
    override suspend fun record(result: LevelResult): List<Achievement> = writes.withLock {
        val announced = dao.unlocks().mapTo(mutableSetOf()) { it.achievementId }
        dao.insertFact(result.toEntity())

        val earned = AchievementEngine.replay(dao.facts().map { it.toResult() }).unlocked
        val newly = earned.filterKeys { it.name !in announced }
        if (newly.isNotEmpty()) {
            dao.insertUnlocks(newly.map { (id, at) -> AchievementUnlockEntity(id.name, at) })
        }
        Achievements.catalog.filter { it.id in newly }
    }

    override suspend fun reset() {
        writes.withLock {
            dao.deleteAllFacts()
            dao.deleteAllUnlocks()
        }
    }

    private fun stateOf(
        facts: List<AchievementFactEntity>,
        unlocks: List<AchievementUnlockEntity>,
    ): AchievementState = AchievementState(
        counters = AchievementEngine.replay(facts.map { it.toResult() }).counters,
        unlocked = unlocks.mapNotNull { row ->
            row.achievementId.toAchievementId()?.let { it to row.unlockedAt }
        }.toMap(),
    )
}

/**
 * An unreadable id means the row was written by a build whose catalog we no
 * longer have — a downgrade, or a badge that was removed. Dropping it is the
 * honest reading: nothing in the catalog can render it, and the fact log still
 * holds everything needed to grant it again if it comes back.
 */
private fun String.toAchievementId(): AchievementId? =
    AchievementId.entries.firstOrNull { it.name == this }

/**
 * An unreadable mode falls back to campaign, which is the conservative answer:
 * campaign clears are gated on [LevelResult.isFirstClear], so a misread row can
 * inflate nothing.
 */
private fun String.toPlayMode(): PlayMode =
    PlayMode.entries.firstOrNull { it.name == this } ?: PlayMode.Campaign

private fun AchievementFactEntity.toResult(): LevelResult = LevelResult(
    levelId = levelId,
    mode = mode.toPlayMode(),
    size = size,
    completed = completed,
    score = score,
    paws = paws,
    timeMs = timeMs,
    strikes = strikes,
    bestCombo = bestCombo,
    sniffsUsed = sniffsUsed,
    treatsUsed = treatsUsed,
    isFirstClear = firstClear,
    previousBestPaws = previousBestPaws,
    dailyStreakDays = dailyStreakDays,
    localHour = localHour,
    finishedAt = finishedAt,
)

private fun LevelResult.toEntity(): AchievementFactEntity = AchievementFactEntity(
    key = key,
    levelId = levelId,
    mode = mode.name,
    size = size,
    completed = completed,
    score = score,
    paws = paws,
    timeMs = timeMs,
    strikes = strikes,
    bestCombo = bestCombo,
    sniffsUsed = sniffsUsed,
    treatsUsed = treatsUsed,
    firstClear = isFirstClear,
    previousBestPaws = previousBestPaws,
    dailyStreakDays = dailyStreakDays,
    localHour = localHour,
    finishedAt = finishedAt,
)
