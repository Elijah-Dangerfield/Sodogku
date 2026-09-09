package com.sodogku.libraries.progress.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One row per local day the player finished **any** board.
 *
 * This is the streak, and it is deliberately not `daily_result`.
 *
 * The streak used to be folded out of the daily's rows, which quietly tied two
 * unrelated ideas together: a player who cleared six campaign levels on a
 * Tuesday had done nothing for their streak, because the streak was really "days
 * you played the daily". The daily is only a way to know you are solving the
 * same board as everybody else. The streak is about turning up.
 *
 * So the rule is now the simple one a player would guess: finish a board, any
 * board, and today counts.
 *
 * Nothing but the date. There is no score here, no level, no outcome, because
 * the only question this table answers is "did they play". Anything richer would
 * be a second copy of records `level_progress` and `daily_result` already hold
 * properly, and a second copy is a second thing to keep true.
 *
 * [date] is the **local** ISO date (`2026-09-09`), matching
 * [DailyResultEntity.date] for the same reasons: ISO text sorts
 * chronologically, so `ORDER BY date` is free, and a support dump reads without
 * a converter.
 */
@Entity(tableName = "play_day")
data class PlayDayEntity(
    @PrimaryKey val date: String,
)

@Dao
interface PlayDayDao {

    /**
     * Marks today as played, or does nothing if it already is.
     *
     * `IGNORE` rather than an upsert because the row has no payload to update:
     * the second board of the day carries exactly the same information as the
     * first, and a write per completed board would be a write per board for the
     * life of the install.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: PlayDayEntity): Long

    @Query("SELECT * FROM play_day ORDER BY date")
    fun observeAll(): Flow<List<PlayDayEntity>>

    @Query("SELECT * FROM play_day ORDER BY date")
    suspend fun all(): List<PlayDayEntity>

    @Query("DELETE FROM play_day")
    suspend fun deleteAll()
}
