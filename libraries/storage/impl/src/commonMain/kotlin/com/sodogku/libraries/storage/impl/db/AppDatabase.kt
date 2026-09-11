package com.sodogku.libraries.storage.impl.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.sodogku.libraries.achievements.db.AchievementDao
import com.sodogku.libraries.achievements.db.AchievementFactEntity
import com.sodogku.libraries.achievements.db.AchievementUnlockEntity
import com.sodogku.libraries.achievements.db.AnnouncedAtBackfill
import com.sodogku.libraries.progress.db.DailyResultDao
import com.sodogku.libraries.progress.db.DailyResultEntity
import com.sodogku.libraries.progress.db.LevelProgressDao
import com.sodogku.libraries.progress.db.PlayDayDao
import com.sodogku.libraries.progress.db.PlayDayEntity
import com.sodogku.libraries.progress.db.LevelProgressEntity
import com.sodogku.libraries.progress.db.ScoreEventDao
import com.sodogku.libraries.progress.db.ScoreEventEntity
import com.sodogku.libraries.sodogku.storage.db.ExampleUserDataDao
import com.sodogku.libraries.sodogku.storage.db.ExampleUserDataEntity

@Database(
    entities = [
        ExampleUserDataEntity::class,
        LevelProgressEntity::class,
        DailyResultEntity::class,
        ScoreEventEntity::class,
        AchievementFactEntity::class,
        AchievementUnlockEntity::class,
        PlayDayEntity::class,
    ],
    version = 11,
    /**
     * Every bump from [FIRST_PLAYER_DATA_VERSION] on has to be listed here.
     *
     * There is no account and no server copy, so a player's campaign records,
     * daily streak and achievements exist in exactly one place: this file on
     * their phone. A destructive fallback is a silent, unrecoverable wipe on
     * the next release that happens to add a column.
     *
     * Most additions are a new table or a defaulted column, which Room can
     * migrate on its own. A change it cannot — a renamed or retyped column —
     * will fail the build here rather than at runtime, which is the point. A
     * spec is for the cases where the *default* is wrong: 10 → 11 adds
     * `achievement_unlock.announcedAt`, whose only harmless starting value is
     * the row's own `unlockedAt`, and no static default can say that.
     */
    autoMigrations = [
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11, spec = AnnouncedAtBackfill::class),
    ],
    exportSchema = true
)
@TypeConverters(CoreTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exampleUserDataDao(): ExampleUserDataDao
    abstract fun levelProgressDao(): LevelProgressDao
    abstract fun dailyResultDao(): DailyResultDao
    abstract fun scoreEventDao(): ScoreEventDao
    abstract fun achievementDao(): AchievementDao
    abstract fun playDayDao(): PlayDayDao

    companion object {
        /**
         * The first schema that held anything a player would miss. Versions below
         * it are template history from before the game existed; no install has
         * ever run them, so they are the only ones the builder is allowed to drop.
         */
        const val FIRST_PLAYER_DATA_VERSION = 6
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
