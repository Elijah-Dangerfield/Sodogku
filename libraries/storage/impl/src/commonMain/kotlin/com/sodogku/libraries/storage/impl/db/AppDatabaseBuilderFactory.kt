package com.sodogku.libraries.storage.impl.db

import androidx.room.RoomDatabase

interface AppDatabaseBuilderFactory {
    fun create(): RoomDatabase.Builder<AppDatabase>
}
