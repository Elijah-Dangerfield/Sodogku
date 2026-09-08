package com.sodogku.libraries.config.impl.repository

import app.cash.turbine.test
import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.sodogku.AppEventBus
import com.sodogku.libraries.sodogku.AppEvents
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfigOverride
import com.sodogku.libraries.config.ConfigOverrideRepository
import com.sodogku.libraries.config.impl.data.ConfigCache
import com.sodogku.libraries.config.impl.data.ConfigCacheSnapshot
import com.sodogku.libraries.config.impl.data.RemoteConfigDataSource
import com.sodogku.libraries.config.impl.model.FallbackConfigMap
import com.sodogku.libraries.config.impl.serialization.ConfigJsonConverter
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class OfflineFirstAppConfigRepositoryTest : CoroutineTest() {

    private val throttleMs = 5 * 60 * 1000L

    @Test
    fun coldBootForeground_withEmptyCache_triggersRefreshAndEmits() = runUnitTest {
        val source = FakeRemoteDataSource(response = MapAppConfig(mapOf("feature.x" to true)))
        val events = FakeAppEvents()
        val repo = newRepo(source = source, events = events)
        events.foreground(isColdBoot = true)
        runCurrent()

        assertEquals(1, source.callCount, "cold-boot foreground should trigger one refresh")
        assertEquals(true, repo.configStream().first().map["feature.x"])
    }

    @Test
    fun refresh_failureWithNoCachedConfig_persistsFallback() = runUnitTest {
        val fallback = TestFallbackConfigMap(map = mapOf("fallback" to "yes"))
        val source = FakeRemoteDataSource().apply { failNext = RuntimeException("server down") }
        val events = FakeAppEvents()
        val repo = newRepo(source = source, fallback = fallback, events = events)
        events.foreground(isColdBoot = true)
        runCurrent()

        val emitted = repo.configStream().first()
        assertEquals("yes", emitted.map["fallback"], "fallback config persisted when no cache present")
    }

    @Test
    fun refresh_failureWithCachedConfig_keepsCachedSnapshot() = runUnitTest {
        val cache = FakeConfigCache()
        cache.seed(configJson = """{"existing":"value"}""")
        val source = FakeRemoteDataSource().apply { failNext = RuntimeException("server down") }
        val fallback = TestFallbackConfigMap(map = mapOf("fallback" to "yes"))
        val events = FakeAppEvents()
        val repo = newRepo(source = source, cache = cache, fallback = fallback, events = events)
        events.foreground(isColdBoot = true)
        runCurrent()

        val emitted = repo.configStream().first()
        assertEquals("value", emitted.map["existing"])
        assertNull(emitted.map["fallback"], "fallback should NOT clobber a prior cached snapshot")
    }

    @Test
    fun config_beforeAnyEmission_returnsFallbackAndDoesNotBlock() = runUnitTest {
        // Regression: a ConfiguredValue read can land on the main thread (e.g.
        // progressionConfig.levelCurve() during composition). On a cold/fresh
        // start the stream hasn't emitted yet, and the old code did
        // `runBlocking { configStream.first() }` here — which deadlocked the app
        // at launch (scene-create watchdog → SIGKILL). It must fall back to the
        // shipped defaults synchronously instead of blocking. (No foreground
        // event is fired, so configStream never emits — the old code would hang
        // this test.)
        val fallback = TestFallbackConfigMap(map = mapOf("fallback" to "yes"))
        val repo = newRepo(fallback = fallback)

        assertEquals(
            "yes",
            repo.config().map["fallback"],
            "config() must return the fallback before the stream emits, never block",
        )
    }

    @Test
    fun init_withCachedConfig_hydratesBeforeRefreshLands() = runUnitTest {
        val cache = FakeConfigCache()
        cache.seed(configJson = """{"hydrated":"from-disk"}""")
        val source = FakeRemoteDataSource(response = MapAppConfig(mapOf("hydrated" to "from-server")))
        val events = FakeAppEvents()
        val repo = newRepo(source = source, cache = cache, events = events)

        repo.configStream().test {
            val initial = awaitItem()
            assertTrue(initial.map["hydrated"] in listOf("from-disk", "from-server"))
            events.foreground(isColdBoot = true)
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun foregroundAfterThrottle_refetches() = runUnitTest {
        val source = FakeRemoteDataSource(response = MapAppConfig(mapOf("k" to "v")))
        val events = FakeAppEvents()
        val clock = FakeClock(nowMs = 0L)
        newRepo(source = source, events = events, clock = clock)

        events.foreground(isColdBoot = true)
        runCurrent()
        assertEquals(1, source.callCount, "cold boot fetches")

        clock.nowMs += throttleMs + 1
        events.foreground(isColdBoot = false)
        runCurrent()
        assertEquals(2, source.callCount, "foreground past the throttle window should re-fetch")
    }

    @Test
    fun foregroundWithinThrottle_doesNotRefetch() = runUnitTest {
        val source = FakeRemoteDataSource(response = MapAppConfig(mapOf("k" to "v")))
        val events = FakeAppEvents()
        val clock = FakeClock(nowMs = 0L)
        newRepo(source = source, events = events, clock = clock)

        events.foreground(isColdBoot = true)
        runCurrent()
        assertEquals(1, source.callCount)

        clock.nowMs += throttleMs - 1 // still inside the window
        events.foreground(isColdBoot = false)
        runCurrent()
        assertEquals(1, source.callCount, "foreground within the throttle window must not re-fetch")
    }

    @Test
    fun foregroundAfterFailure_nextForegroundPastThrottleRetries() = runUnitTest {
        val source = FakeRemoteDataSource(response = MapAppConfig(mapOf("k" to "v")))
        val events = FakeAppEvents()
        val clock = FakeClock(nowMs = 0L)
        newRepo(source = source, events = events, clock = clock)

        source.failNext = RuntimeException("cold boot fetch failed")
        events.foreground(isColdBoot = true)
        runCurrent()
        assertEquals(1, source.callCount)

        // Within the window: no retry even though the last fetch failed.
        clock.nowMs += throttleMs - 1
        events.foreground(isColdBoot = false)
        runCurrent()
        assertEquals(1, source.callCount, "a failed fetch still respects the throttle")

        // Past the window: retry.
        clock.nowMs += 2
        events.foreground(isColdBoot = false)
        runCurrent()
        assertEquals(2, source.callCount, "foreground past the throttle retries after a failure")
    }

    @Test
    fun zeroThrottleFromConfig_refetchesOnEveryForeground() = runUnitTest {
        // The throttle is itself a config value; 0 means "refetch every foreground".
        val zeroConfig = MapAppConfig(mapOf("config" to mapOf("refreshThrottleMs" to 0)))
        val cache = FakeConfigCache().apply { seed(configJson = """{"config":{"refreshThrottleMs":0}}""") }
        val source = FakeRemoteDataSource(response = zeroConfig)
        val events = FakeAppEvents()
        val clock = FakeClock(nowMs = 0L)
        newRepo(source = source, cache = cache, events = events, clock = clock)

        events.foreground(isColdBoot = true)
        runCurrent()
        assertEquals(1, source.callCount)

        clock.nowMs += 10 // far inside the default 5-min window, but throttle is 0
        events.foreground(isColdBoot = false)
        runCurrent()
        assertEquals(2, source.callCount, "a 0 throttle should refetch on every foreground")
    }

    @Test
    fun overrides_apply_onTopOfCachedConfig() = runUnitTest {
        val source = FakeRemoteDataSource(response = MapAppConfig(mapOf("foo" to "bar")))
        val overrides = FakeConfigOverrideRepository()
        val events = FakeAppEvents()
        val repo = newRepo(source = source, overrides = overrides, events = events)
        events.foreground(isColdBoot = true)
        runCurrent()

        assertEquals("bar", repo.configStream().first().map["foo"])

        overrides.set(listOf(ConfigOverride("foo", "overridden" as Any)))
        runCurrent()
        assertEquals("overridden", repo.configStream().first().map["foo"])
    }

    @Test
    fun freshInstall_configStreamEmitsWithoutTheNetworkAnsweringAtAll() = runUnitTest {
        // The boot gate awaits `configStream().first()`. This used to emit
        // nothing on a device with no cached snapshot, so the first frame waited
        // out the whole retry chain — measured at 8 seconds on an emulator with
        // the server unreachable, which is every launch until the deploy lands
        // and every offline launch after it.
        //
        // The data source is never allowed to answer, so a pass here can only
        // come from the bundled fallback.
        val cache = FakeConfigCache()
        val source = FakeRemoteDataSource(neverAnswers = true)
        val fallback = TestFallbackConfigMap()
        val repo = newRepo(source = source, cache = cache, fallback = fallback)
        runCurrent()

        val emitted = withTimeoutOrNull(1_000) { repo.configStream().first() }

        assertNotNull(emitted, "nothing emitted, so the first frame is still blocked on the network")
        assertEquals(fallback.map, emitted.map, "and what it emitted has to be the bundled fallback")
        assertEquals(0, cache.writes, "reading config must not require having written any")
    }

    @Test
    fun aCorruptCachedSnapshotIsAsGoodAReasonToUseTheFallbackAsAnAbsentOne() = runUnitTest {
        // Same failure, different cause: `decodeConfig` returns null on
        // unreadable JSON, which used to be filtered out and emit nothing.
        val cache = FakeConfigCache().apply { seed(configJson = "{ this is not json") }
        val fallback = TestFallbackConfigMap()
        val repo = newRepo(
            source = FakeRemoteDataSource(neverAnswers = true),
            cache = cache,
            fallback = fallback,
        )
        runCurrent()

        val emitted = withTimeoutOrNull(1_000) { repo.configStream().first() }

        assertNotNull(emitted, "a corrupt snapshot must not wedge the boot gate")
        assertEquals(fallback.map, emitted.map)
    }

    @Test
    fun aRealSnapshotStillWinsOverTheFallback() = runUnitTest {
        // The companion that stops "always return the fallback" passing the two
        // tests above. Starting from the fallback is only correct if a genuine
        // snapshot supersedes it.
        val cache = FakeConfigCache().apply { seed(configJson = """{"foo":"cached"}""") }
        val repo = newRepo(source = FakeRemoteDataSource(neverAnswers = true), cache = cache)
        runCurrent()

        assertEquals("cached", repo.configStream().first().map["foo"])
    }

    // ---------- Test scaffolding ----------

    private fun newRepo(
        source: FakeRemoteDataSource = FakeRemoteDataSource(),
        cache: FakeConfigCache = FakeConfigCache(),
        fallback: TestFallbackConfigMap = TestFallbackConfigMap(),
        overrides: FakeConfigOverrideRepository = FakeConfigOverrideRepository(),
        events: FakeAppEvents = FakeAppEvents(),
        clock: FakeClock = FakeClock(nowMs = 0L),
    ): OfflineFirstAppConfigRepository {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val converter = ConfigJsonConverter(json)
        return OfflineFirstAppConfigRepository(
            dispatcherProvider = dispatchers,
            remoteConfigDataSource = source,
            configCache = cache,
            converter = converter,
            fallbackConfig = fallback,
            configOverrideRepository = overrides,
            appEvents = events.appEvents,
            clock = clock,
            appScope = AppCoroutineScope(dispatchers),
        )
    }

    private class FakeClock(var nowMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    /** Emits [AppEvent.OnForeground] into the repo's [AppEvents] stream on demand. */
    private class FakeAppEvents {
        private val flow = MutableSharedFlow<AppEvent>(replay = 1, extraBufferCapacity = 16)
        val appEvents = AppEvents(bus = NoOpBus, backingFlow = flow)

        fun foreground(isColdBoot: Boolean) {
            flow.tryEmit(AppEvent.OnForeground(isColdBoot = isColdBoot))
        }

        private object NoOpBus : AppEventBus {
            override fun dispatch(event: AppEvent) {}
            override fun eventStream(): Flow<AppEvent> = MutableSharedFlow()
            override fun liveEventStream(): Flow<AppEvent> = MutableSharedFlow()
        }
    }

    private class FakeRemoteDataSource(
        var response: AppConfigMap = MapAppConfig(emptyMap<String, Any?>()),
        /** Suspends forever, standing in for a server that is simply not there. */
        private val neverAnswers: Boolean = false,
    ) : RemoteConfigDataSource {
        var failNext: Throwable? = null
        var callCount: Int = 0
            private set

        override suspend fun getConfig(): Catching<AppConfigMap> {
            callCount++
            if (neverAnswers) awaitCancellation()
            failNext?.let { failNext = null; return Catching.failure(it) }
            return Catching.success(response)
        }
    }

    private class FakeConfigCache : ConfigCache {
        private val state = MutableStateFlow(ConfigCacheSnapshot())

        /** Counts persists, so a test can prove reading needed no write. */
        var writes: Int = 0
            private set

        override val updates: Flow<ConfigCacheSnapshot> = state
        override suspend fun get(): ConfigCacheSnapshot = state.value
        override suspend fun set(value: ConfigCacheSnapshot) {
            writes++
            state.value = value
        }
        override suspend fun clear() { state.value = ConfigCacheSnapshot() }
        /** Puts a snapshot in place without counting as a persist. */
        fun seed(configJson: String? = null, overridesJson: String? = null) {
            state.value = ConfigCacheSnapshot(configJson = configJson, overridesJson = overridesJson)
        }
    }

    private class TestFallbackConfigMap(
        override val map: Map<String, *> = emptyMap<String, Any?>(),
    ) : FallbackConfigMap(converter = ConfigJsonConverter(Json.Default))

    private class FakeConfigOverrideRepository : ConfigOverrideRepository {
        private val flow = MutableStateFlow<List<ConfigOverride<Any>>>(emptyList())
        override fun getOverrides(): List<ConfigOverride<Any>> = flow.value
        override fun getOverridesFlow(): Flow<List<ConfigOverride<Any>>> = flow
        override suspend fun addOverride(override: ConfigOverride<Any>) {
            flow.value = flow.value.filter { it.path != override.path } + override
        }
        override suspend fun clearAll() { flow.value = emptyList() }
        fun set(overrides: List<ConfigOverride<Any>>) { flow.value = overrides }
    }

    private class MapAppConfig(override val map: Map<String, *>) : AppConfigMap()
}
