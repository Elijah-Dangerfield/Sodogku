package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.storage.Cache
import com.sodogku.libraries.storage.CacheFactory
import com.sodogku.libraries.storage.versionedJsonSerializer
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * What the streak has already said to this player, on disk.
 *
 * Persistent rather than per-session for the reason the skip allowance is: the
 * intention moment is non-skippable, and a force-quit must not be a way to be
 * shown it again. Its own cache next to `SkipStateCache`, not two more fields in
 * `AppData`, on the same argument: these are machinery, not settings.
 */
interface StreakPromptCache : Cache<StreakPromptState>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = StreakPromptCache::class)
@Inject
class StreakPromptCacheImpl(
    cacheFactory: CacheFactory,
) : StreakPromptCache, Cache<StreakPromptState> by cacheFactory.persistent(
    name = "streak_prompts",
    serializer = versionedJsonSerializer(
        defaultValue = { StreakPromptState() },
    ),
)
