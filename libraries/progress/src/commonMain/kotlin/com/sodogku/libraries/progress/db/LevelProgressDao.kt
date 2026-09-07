package com.sodogku.libraries.progress.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One row per level the player has touched; levels never started have no row.
 *
 * The table lives in the api module rather than next to `ProgressRepositoryImpl`
 * because the shared `AppDatabase` in `:libraries:storage:impl` has to list the
 * entity, and one impl module may not depend on another. Same reason
 * `ExampleUserDataEntity` sits outside `:libraries:storage:impl` — see the
 * module boundary rules in AGENTS.md.
 *
 * [state] is the [com.sodogku.libraries.progress.LevelState] name rather than an
 * ordinal, so reordering the enum can't silently reinterpret everyone's saved
 * campaign.
 *
 * Deliberately **not** a `ClearableDao`: that set is wiped on user change, and
 * progress here is the device's, permanently. There is no user to change.
 */
@Entity(tableName = "level_progress")
data class LevelProgressEntity(
    @PrimaryKey val levelId: Int,
    val state: String,
    val bestScore: Int,
    val bestPaws: Int,
    val bestTimeMs: Long,
    val attempts: Int,
    val firstCompletedAt: Long,
    val lastPlayedAt: Long,
)

@Dao
interface LevelProgressDao {

    @Upsert
    suspend fun upsert(row: LevelProgressEntity)

    @Query("SELECT * FROM level_progress WHERE levelId = :levelId")
    fun observe(levelId: Int): Flow<LevelProgressEntity?>

    @Query("SELECT * FROM level_progress WHERE levelId = :levelId")
    suspend fun find(levelId: Int): LevelProgressEntity?

    @Query("SELECT * FROM level_progress ORDER BY levelId")
    suspend fun all(): List<LevelProgressEntity>

    @Query("DELETE FROM level_progress")
    suspend fun deleteAll()
}
