package com.sodogku.libraries.sodogku.impl

import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The only id this app has, and the three states it can be read in.
 *
 * Sodogku has no accounts, so an install id is the whole of what correlates one
 * device's telemetry. It is minted once on a cold install and then has to be
 * returned verbatim forever. The reuse test asserts both halves of that: the
 * value comes back unchanged, and reading it does not write a new one, since a
 * provider that re-minted on every read would still pass a test that only
 * compared the return value to itself.
 *
 * The third state is a cache that cannot be read. The provider answers null
 * rather than throwing, the header is simply omitted, and the server tolerates
 * its absence. A crash here would take down a launch over a value that is only
 * ever used for correlation.
 *
 * The eager hydrate in `init` is why the fixture works without awaiting
 * anything: `CoroutineTest`'s unconfined dispatcher runs it before `current()`
 * is called, which mirrors what the real `Main.immediate` does.
 *
 * ### Not here
 *
 * That the id reaches outbound requests is the networking headers provider, and
 * `:apps:integration` sees it over real HTTP. What the privacy page promises
 * about identity is `NoIdentitySeamsTest`.
 */
class CachedInstallIdProviderTest : CoroutineTest() {

    @Test
    fun mintsNewInstallId_andPersistsToAppCache_whenAppCacheValueIsNull() = runUnitTest {
        val cache = FakeAppCache(initial = AppData(installId = null))
        val appScope = AppCoroutineScope(dispatchers)

        val provider = CachedInstallIdProvider(cache, appScope)
        // CoroutineTest's UnconfinedTestDispatcher runs the init {}-launched
        // hydrate eagerly, so by the time current() is called the in-memory
        // ref has been populated and the cache has been updated.
        val minted = provider.current()

        assertNotNull(minted, "Cold-start install must produce an in-memory id")
        assertEquals(minted, cache.get().installId, "Minted id must be persisted to AppCache")
    }

    @Test
    fun reusesPersistedInstallId_whenAppCacheAlreadyHasOne() = runUnitTest {
        val existing = "11111111-1111-1111-1111-111111111111"
        val cache = FakeAppCache(initial = AppData(installId = existing))
        val appScope = AppCoroutineScope(dispatchers)

        val provider = CachedInstallIdProvider(cache, appScope)

        assertEquals(existing, provider.current(), "Hot path keeps the persisted id verbatim")
        assertEquals(existing, cache.get().installId, "Persisted id is not overwritten on read")
    }

    @Test
    fun current_returnsNull_whenAppCacheReadThrows() = runUnitTest {
        // A failing AppCache shouldn't crash the provider — the header
        // simply stays unset and the next request omits it. The server
        // tolerates absence, so transient cache failures don't break /me.
        val cache = ThrowingAppCache()
        val appScope = AppCoroutineScope(dispatchers)

        val provider = CachedInstallIdProvider(cache, appScope)

        assertNull(provider.current(), "Failed hydration leaves current() at null")
    }

    private class FakeAppCache(initial: AppData = AppData()) : AppCache {
        private val state = MutableStateFlow(initial)
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData) { state.value = value }
        override suspend fun clear() { state.value = AppData() }
    }

    private class ThrowingAppCache : AppCache {
        override val updates: Flow<AppData> = MutableStateFlow(AppData())
        override suspend fun get(): AppData = error("simulated AppCache failure")
        override suspend fun set(value: AppData) = error("simulated AppCache failure")
        override suspend fun clear() = error("simulated AppCache failure")
    }
}
