package com.sodogku.libraries.storage.impl.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.sodogku.libraries.progress.db.LevelProgressDao
import com.sodogku.libraries.progress.db.LevelProgressEntity
import com.sodogku.libraries.sodogku.storage.db.ExampleUserDataDao
import com.sodogku.libraries.sodogku.storage.db.ExampleUserDataEntity

@Database(
    entities = [
        ExampleUserDataEntity::class,
        LevelProgressEntity::class,
    ],
    version = 6, // Bumped: added level_progress
    exportSchema = true
)
@TypeConverters(CoreTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exampleUserDataDao(): ExampleUserDataDao
    abstract fun levelProgressDao(): LevelProgressDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
