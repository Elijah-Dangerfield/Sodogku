package com.sodogku.libraries.achievements.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

/**
 * One row per finished attempt, append-only.
 *
 * The facts are stored rather than the counters they add up to. It costs a
 * couple of hundred bytes per level and it buys two things worth far more: an
 * achievement shipped in a later release back-fills from history instead of
 * starting everyone at zero, and there is no second copy of the truth to drift
 * from the fold.
 *
 * [key] is the attempt's identity ([com.sodogku.libraries.achievements.LevelResult.key])
 * and carries the unique index, so an insert that arrives twice is ignored
 * rather than counted twice. The surrogate [id] exists because ordering is
 * load-bearing — streaks are defined by the order attempts happened — and
 * `ORDER BY id` is that order even when two attempts share a timestamp.
 *
 * Like `level_progress`, this is deliberately **not** a `ClearableDao`: that set
 * is wiped on user change, and there is no user. `AchievementsRepository.reset()`
 * is the only intended eraser.
 */
@Entity(
    tableName = "achievement_fact",
    indices = [Index(value = ["key"], unique = true)],
)
data class AchievementFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val levelId: Int,
    /** [com.sodogku.libraries.achievements.PlayMode] name, never its ordinal. */
    val mode: String,
    val size: Int,
    val completed: Boolean,
    val score: Int,
    val paws: Int,
    val timeMs: Long,
    val strikes: Int,
    val bestCombo: Int,
    val sniffsUsed: Int,
    val treatsUsed: Int,
    val firstClear: Boolean,
    val previousBestPaws: Int,
    val dailyStreakDays: Int,
    val localHour: Int,
    val finishedAt: Long,
)

/**
 * What the player has been told they earned, and when.
 *
 * Derivable from the facts, and stored anyway: this is the record of what has
 * already been *announced*, so a catalog change cannot re-toast a badge someone
 * earned two months ago.
 *
 * Two timestamps, because a back-filled badge has two honest answers.
 * [unlockedAt] is the historical one, taken from the attempt that crossed the
 * threshold. [announcedAt] is the attempt during which the badge was actually
 * handed over. They differ only for a badge the catalog gained after the play
 * that earned it, and keeping them apart is what lets such a badge read as news
 * on a page that decides news by comparing against a seen-watermark.
 */
@Entity(tableName = "achievement_unlock")
data class AchievementUnlockEntity(
    /** [com.sodogku.libraries.achievements.AchievementId] name. */
    @PrimaryKey val achievementId: String,
    val unlockedAt: Long,
    /**
     * Zero only in the window between the column arriving and
     * [AnnouncedAtBackfill] filling it, which is inside the migration's own
     * transaction.
     */
    @ColumnInfo(defaultValue = "0") val announcedAt: Long,
)

/**
 * Rows written before `announcedAt` existed claim they were announced when they
 * were earned.
 *
 * That is the only reading that changes nothing. Before the column, "is this
 * news" was `unlockedAt > seenAt`; copying `unlockedAt` across makes the new
 * comparison give the identical answer for every badge already on disk, so
 * nobody upgrading is told their two-month-old collection is new, and nobody
 * loses a badge they earned yesterday and have not looked at yet. Defaulting to
 * zero would do the second of those, and defaulting to the migration's own
 * clock would do the first to everybody at once.
 */
class AnnouncedAtBackfill : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE achievement_unlock SET announcedAt = unlockedAt")
    }
}

@Dao
interface AchievementDao {

    /** Ignores a duplicate [AchievementFactEntity.key] rather than replacing it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFact(row: AchievementFactEntity)

    @Query("SELECT * FROM achievement_fact ORDER BY id")
    suspend fun facts(): List<AchievementFactEntity>

    @Query("SELECT * FROM achievement_fact ORDER BY id")
    fun observeFacts(): Flow<List<AchievementFactEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnlocks(rows: List<AchievementUnlockEntity>)

    @Query("SELECT * FROM achievement_unlock")
    suspend fun unlocks(): List<AchievementUnlockEntity>

    @Query("SELECT * FROM achievement_unlock")
    fun observeUnlocks(): Flow<List<AchievementUnlockEntity>>

    @Query("DELETE FROM achievement_fact")
    suspend fun deleteAllFacts()

    @Query("DELETE FROM achievement_unlock")
    suspend fun deleteAllUnlocks()
}
