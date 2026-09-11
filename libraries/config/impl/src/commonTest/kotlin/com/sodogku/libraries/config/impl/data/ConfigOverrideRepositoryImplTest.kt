package com.sodogku.libraries.config.impl.data

import app.cash.turbine.test
import com.sodogku.libraries.config.ConfigOverride
import com.sodogku.libraries.config.impl.serialization.ConfigJsonConverter
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The QA menu's local overrides, and the disk they share with real config.
 *
 * Overrides and the fetched config live in the same cached blob, so the
 * assertion with the most teeth is that writing one leaves the other untouched.
 * Serialising the whole object back from a partial model would wipe the fetched
 * values on the next override, and the symptom is a tester reporting that
 * remote config stopped arriving on a build where it is working fine.
 *
 * The writes are otherwise an upsert with the edges stated separately: setting
 * the same path twice replaces rather than appends, and unrelated paths
 * survive. Clearing resets the cache as well as the in-memory copy, since an
 * override that comes back after a relaunch is worse than one that never left.
 *
 * Hydration covers three starting states: a populated cache, an empty one, and
 * a value on disk that no longer parses. The last falls back to no overrides,
 * because this is the path the app boots through and a refusal here bricks a
 * build over a debug feature.
 *
 * The flow emits the current snapshot before any update, which is what lets a
 * screen bind to it without a separate initial read.
 *
 * ### Not here
 *
 * How an override wins over a fetched value is
 * `OfflineFirstAppConfigRepositoryTest`. What the keys mean, and whether every
 * declared one is read, is `ConfigValuesAreReadTest` in `:apps:integration`.
 */
class ConfigOverrideRepositoryImplTest : CoroutineTest() {

    @Test
    fun init_hydratesFromCachedOverridesJson() = runUnitTest {
        val cache = FakeConfigCache().apply {
            seed(overridesJson = """[{"path":"feature.flag","value":true}]""")
        }
        val repo = newRepo(cache = cache)

        val overrides = repo.getOverrides()
        assertEquals(1, overrides.size)
        assertEquals("feature.flag", overrides.first().path)
        assertEquals(true, overrides.first().value)
    }

    @Test
    fun init_withEmptyCache_returnsNoOverrides() = runUnitTest {
        val repo = newRepo(cache = FakeConfigCache())
        assertTrue(repo.getOverrides().isEmpty())
    }

    @Test
    fun init_withMalformedJson_fallsBackToEmpty() = runUnitTest {
        val cache = FakeConfigCache().apply {
            seed(overridesJson = "not valid json")
        }
        val repo = newRepo(cache = cache)

        assertTrue(repo.getOverrides().isEmpty(), "malformed JSON must not crash; fall back to empty")
    }

    @Test
    fun addOverride_persistsToCache() = runUnitTest {
        val cache = FakeConfigCache()
        val repo = newRepo(cache = cache)

        repo.addOverride(ConfigOverride("ui.theme", "dark" as Any))

        val stored = cache.get().overridesJson
        assertTrue(stored != null && "ui.theme" in stored, "addOverride writes the new path to cache")
        assertEquals("dark", repo.getOverrides().single().value)
    }

    @Test
    fun addOverride_replacesExistingValueForSamePath() = runUnitTest {
        val cache = FakeConfigCache()
        val repo = newRepo(cache = cache)

        repo.addOverride(ConfigOverride("toggle", false as Any))
        repo.addOverride(ConfigOverride("toggle", true as Any))

        val all = repo.getOverrides()
        assertEquals(1, all.size, "same-path adds must dedup, not stack")
        assertEquals(true, all.single().value)
    }

    @Test
    fun addOverride_keepsUnrelatedPaths() = runUnitTest {
        val cache = FakeConfigCache()
        val repo = newRepo(cache = cache)

        repo.addOverride(ConfigOverride("a.b", 1 as Any))
        repo.addOverride(ConfigOverride("c.d", 2 as Any))

        val paths = repo.getOverrides().map { it.path }.toSet()
        assertEquals(setOf("a.b", "c.d"), paths)
    }

    @Test
    fun clearAll_resetsOverridesAndCache() = runUnitTest {
        val cache = FakeConfigCache().apply {
            seed(overridesJson = """[{"path":"x","value":1}]""")
        }
        val repo = newRepo(cache = cache)
        assertEquals(1, repo.getOverrides().size)

        repo.clearAll()

        assertTrue(repo.getOverrides().isEmpty())
        assertNull(cache.get().overridesJson, "clearAll nulls the persisted JSON, not just an empty array")
    }

    @Test
    fun getOverridesFlow_emitsCurrentSnapshotAndCacheUpdates() = runUnitTest {
        val cache = FakeConfigCache()
        val repo = newRepo(cache = cache)

        repo.getOverridesFlow().test {
            assertEquals(emptyList<ConfigOverride<Any>>(), awaitItem())

            repo.addOverride(ConfigOverride("flag", true as Any))
            val next = awaitItem()
            assertEquals(1, next.size)
            assertEquals("flag", next.single().path)

            repo.clearAll()
            assertEquals(emptyList<ConfigOverride<Any>>(), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addOverride_preservesConfigJson_andOnlyTouchesOverridesField() = runUnitTest {
        val cache = FakeConfigCache().apply {
            seed(configJson = """{"existing":"value"}""")
        }
        val repo = newRepo(cache = cache)

        repo.addOverride(ConfigOverride("k", "v" as Any))

        assertEquals(
            """{"existing":"value"}""",
            cache.get().configJson,
            "addOverride must not clobber the unrelated configJson field on the snapshot",
        )
    }

    // ---------- Test scaffolding ----------

    private fun newRepo(
        cache: ConfigCache = FakeConfigCache(),
        converter: ConfigJsonConverter = ConfigJsonConverter(Json { ignoreUnknownKeys = true }),
    ): ConfigOverrideRepositoryImpl = ConfigOverrideRepositoryImpl(
        configCache = cache,
        converter = converter,
        appScope = AppCoroutineScope(dispatchers),
    )

    private class FakeConfigCache : ConfigCache {
        private val state = MutableStateFlow(ConfigCacheSnapshot())
        override val updates: Flow<ConfigCacheSnapshot> = state
        override suspend fun get(): ConfigCacheSnapshot = state.value
        override suspend fun set(value: ConfigCacheSnapshot) { state.value = value }
        override suspend fun clear() { state.value = ConfigCacheSnapshot() }
        fun seed(configJson: String? = null, overridesJson: String? = null) {
            state.value = ConfigCacheSnapshot(configJson = configJson, overridesJson = overridesJson)
        }
    }
}
