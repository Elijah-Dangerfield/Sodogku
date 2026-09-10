package com.sodogku.libraries.progress.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One row per lot of points the player banked, with the wall clock time it
 * happened. Append-only: there is no update path and no upsert, because an
 * entry describes a moment that has already passed.
 *
 * In the api module for the same reason as [LevelProgressEntity]: the shared
 * `AppDatabase` in `:libraries:storage:impl` has to list the entity, and one
 * impl module may not depend on another.
 *
 * [atMillis] is an epoch stamp rather than the local date `daily_result` uses,
 * and that is the whole point of the table. The window this is read against
 * comes from Game Center as an instant, so anything the device would have to
 * convert through a time zone to compare would be reintroducing exactly the
 * local-midnight problem `Leaderboard` rules out for the daily board.
 *
 * The surrogate [id] rather than a natural key: two clears can land in the same
 * millisecond, and the second one is not a duplicate of the first.
 */
@Entity(tableName = "score_event")
data class ScoreEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val atMillis: Long,
    val points: Int,
)

@Dao
interface ScoreEventDao {

    @Insert
    suspend fun insert(row: ScoreEventEntity)

    @Query("SELECT * FROM score_event ORDER BY atMillis")
    suspend fun all(): List<ScoreEventEntity>

    /**
     * Drops everything older than [beforeMillis]. The cutoff is the caller's,
     * and `ScoreLedger.RETENTION_MILLIS` says why it is safe.
     */
    @Query("DELETE FROM score_event WHERE atMillis < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long)
}
