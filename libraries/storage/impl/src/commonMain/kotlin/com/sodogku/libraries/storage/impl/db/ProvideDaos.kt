package com.sodogku.libraries.storage.impl.db

import com.sodogku.libraries.achievements.db.AchievementDao
import com.sodogku.libraries.progress.db.DailyResultDao
import com.sodogku.libraries.progress.db.LevelProgressDao
import com.sodogku.libraries.progress.db.PlayDayDao
import com.sodogku.libraries.progress.db.ScoreEventDao
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

/**
 * Not clearable either, and for a stronger reason than the campaign: this table
 * *is* the streak. Wiping it does not reset a number, it deletes the evidence the
 * number is derived from, and nothing can reconstruct it afterwards.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DailyResultDao::class)
class ProvideDailyResultDao @Inject constructor(
    provider: AppDatabaseProvider
) : DailyResultDao by provider.database.dailyResultDao()

/**
 * The one table here that could be cleared without costing anybody anything,
 * and it still is not in the [ClearableDao] set — that set is about a user
 * changing, and there is no user. It prunes itself instead
 * (`ScoreLedger.RETENTION_MILLIS`), which is the honest way to bound a table
 * nothing else is allowed to truncate.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ScoreEventDao::class)
class ProvideScoreEventDao @Inject constructor(
    provider: AppDatabaseProvider
) : ScoreEventDao by provider.database.scoreEventDao()

/**
 * Same reasoning again: the fact log *is* the achievement progress, since every
 * counter is folded from it rather than stored. Clearing it would silently take
 * badges off a player who had earned them.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AchievementDao::class)
class ProvideAchievementDao @Inject constructor(
    provider: AppDatabaseProvider
) : AchievementDao by provider.database.achievementDao()

/**
 * And again, most sharply of all. `play_day` is the streak: not a cached count
 * but the list of days it is folded from. There is no server copy and no
 * account, so deleting a row here removes a day the player actually turned up,
 * permanently, with nothing able to prove it happened.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = PlayDayDao::class)
class ProvidePlayDayDao @Inject constructor(
    provider: AppDatabaseProvider
) : PlayDayDao by provider.database.playDayDao()
