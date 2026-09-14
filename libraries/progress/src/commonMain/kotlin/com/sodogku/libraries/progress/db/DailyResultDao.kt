package com.sodogku.libraries.progress.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One row per day the player either played the daily or spent a freeze on.
 * A day with no row was missed.
 *
 * In the api module for the same reason as [LevelProgressEntity]: the shared
 * `AppDatabase` in `:libraries:storage:impl` has to list the entity, and one
 * impl module may not depend on another.
 *
 * [date] is the **local** ISO date the board belonged to (`2026-09-07`), not an
 * epoch stamp. Text rather than a number because ISO dates sort lexicographically,
 * so `ORDER BY date` is the chronological order for free, and a support dump of
 * this table is readable without a converter.
 *
 * [outcome] is the [com.sodogku.libraries.progress.daily.DailyOutcome] name
 * rather than the `completed` / `froze` boolean pair the spec first sketched:
 * two booleans describe four states and one of them (completed *and* frozen) is
 * meaningless, so the enum is the shape that cannot be written wrong. Stored by
 * name, not ordinal, like `level_progress.state`.
 */
@Entity(tableName = "daily_result")
data class DailyResultEntity(
    @PrimaryKey val date: String,
    val levelIndex: Int,
    val outcome: String,
    val score: Int,
    val paws: Int,
    val timeMs: Long,
)

@Dao
interface DailyResultDao {

    /**
     * Writes a day's result **only if that day has none**, and reports whether it
     * landed.
     *
     * This is where one-attempt-per-day actually lives. An upsert would let a
     * second finish for the same date overwrite the first, which is the "replay
     * for a better score" bug; the conflict strategy makes the first result the
     * only one the database will ever hold, even if two writers race.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: DailyResultEntity): Long

    /**
     * Drops the row for [date] when it carries [outcome], and does nothing
     * otherwise.
     *
     * **Not an update path**, and the narrowness is the point: it can only take a
     * row away, and only one whose outcome the caller already named. It exists
     * for `Failed`, which nothing has written since SD-49 removed Give up on
     * today and which SD-111 stopped reading as a result at all. A row nobody
     * reads still owns the primary key for its date, so without this the clear
     * that the day is now open for would be refused by [insertIfAbsent] and bank
     * nothing.
     *
     * A `Completed` row is never passed here. One attempt per day is still the
     * table's rule, and it is still the primary key that keeps it.
     */
    @Query("DELETE FROM daily_result WHERE date = :date AND outcome = :outcome")
    suspend fun deleteWithOutcome(date: String, outcome: String)

    @Query("SELECT * FROM daily_result ORDER BY date")
    fun observeAll(): Flow<List<DailyResultEntity>>

    @Query("SELECT * FROM daily_result ORDER BY date")
    suspend fun all(): List<DailyResultEntity>

    @Query("DELETE FROM daily_result")
    suspend fun deleteAll()
}
