package com.sodogku.features.streak.impl

import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop

/**
 * `AppData` in memory, shared by both streak ViewModel tests.
 *
 * Hand-rolled rather than mocked, per `docs/practices/testing.md`, and shared
 * rather than declared twice because two files in one package cannot both have
 * a private class of the same name.
 */
internal class InMemoryAppCache(initial: AppData = AppData()) : AppCache {
    private val data = MutableStateFlow(initial)

    /**
     * A change feed, not a state holder, and the `drop(1)` is the whole reason
     * this fake exists.
     *
     * The real `Cache.updates` does not replay what is already on disk. See
     * `AchievementsViewModel`, which reads *and* observes for exactly this
     * reason. A fake that replays hides a ViewModel that only observes, and
     * that ViewModel would show defaults on every cold start.
     */
    override val updates: Flow<AppData> = data.drop(1)
    override suspend fun get(): AppData = data.value
    override suspend fun set(value: AppData) {
        data.value = value
    }
    override suspend fun clear() {
        data.value = AppData()
    }
}
