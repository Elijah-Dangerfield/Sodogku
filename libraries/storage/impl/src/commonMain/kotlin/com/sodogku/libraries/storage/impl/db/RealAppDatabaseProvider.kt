package com.sodogku.libraries.storage.impl.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sodogku.libraries.flowroutines.DispatcherProvider
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAppDatabaseProvider @Inject constructor(
    private val builderFactory: AppDatabaseBuilderFactory,
    private val dispatcherProvider: DispatcherProvider
) : AppDatabaseProvider {

    override val database: AppDatabase by lazy {
        builderFactory
            .create()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(dispatcherProvider.io)
            // Only the pre-game template schemas may be dropped. Everything from
            // AppDatabase.FIRST_PLAYER_DATA_VERSION up migrates, because there is
            // no account to restore a wiped campaign from. See AppDatabase.
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                *PRE_GAME_SCHEMA_VERSIONS,
            )
            .build()
    }
}

private val PRE_GAME_SCHEMA_VERSIONS = intArrayOf(1, 2, 3, 4, 5)
