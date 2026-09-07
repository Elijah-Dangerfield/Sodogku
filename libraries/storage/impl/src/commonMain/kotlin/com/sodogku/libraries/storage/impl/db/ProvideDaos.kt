package com.sodogku.libraries.storage.impl.db

import com.sodogku.libraries.progress.db.LevelProgressDao
import com.sodogku.libraries.sodogku.storage.db.ClearableDao
import com.sodogku.libraries.sodogku.storage.db.ExampleUserDataDao
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The pattern for exposing a DAO to the DI graph: delegate to the database
 * provider AND contribute the DAO into the [ClearableDao] multibinding set so
 * `UserScopedDaoCleaner` wipes it on user change. Copy this pair of
 * annotations for every user-scoped DAO you add.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ExampleUserDataDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideExampleUserDataDao @Inject constructor(
    provider: AppDatabaseProvider
) : ExampleUserDataDao by provider.database.exampleUserDataDao()

/**
 * Level progress is deliberately **not** in the [ClearableDao] set. That set is
 * wiped when the user changes, and this table is the device's campaign — the
 * one thing that must survive everything short of an uninstall. Sodogku has no
 * accounts anyway; `ProgressRepository.reset()` is the only intended eraser.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = LevelProgressDao::class)
class ProvideLevelProgressDao @Inject constructor(
    provider: AppDatabaseProvider
) : LevelProgressDao by provider.database.levelProgressDao()
