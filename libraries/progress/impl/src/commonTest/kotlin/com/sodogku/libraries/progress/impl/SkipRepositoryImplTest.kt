package com.sodogku.libraries.progress.impl

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdOutcome
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.ProgressionSkipsPerDay
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.SkipResult
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SkipRepositoryImplTest : CoroutineTest() {

    private val clock = MovableClock(Noon)
    private val cache = FakeSkipStateCache()
    private val progress = RecordingProgress()

    @Test
    fun theAllowanceOpensAtTheConfiguredCap() = runUnitTest {
        assertEquals(DefaultSkipsPerDay, repository().remainingToday())
    }

    @Test
    fun aNonDefaultCapIsWhatIsActuallyEnforced() = runUnitTest {
        // The half that stops a hardcoded 3 passing: the config value has to
        // reach the decision, not just the default it declares.
        val repository = repository(config = configOf("skipsPerDay" to 1))

        assertEquals(1, repository.remainingToday())
        assertIs<SkipResult.Skipped>(repository.skip(levelId = 12))
        assertEquals(SkipResult.NoneLeft, repository.skip(levelId = 13))
    }

    @Test
    fun aSkipSpendsOneAndRecordsTheLevel() = runUnitTest {
        val result = repository().skip(levelId = 12)

        assertEquals(SkipResult.Skipped(remainingToday = DefaultSkipsPerDay - 1), result)
        assertEquals(listOf(12), progress.skipped)
    }

    @Test
    fun theAllowanceRunsOutAndTheLevelIsNotRecorded() = runUnitTest {
        val repository = repository()
        repeat(DefaultSkipsPerDay) { repository.skip(levelId = it) }
        progress.skipped.clear()

        assertEquals(SkipResult.NoneLeft, repository.skip(levelId = 99))
        assertTrue(progress.skipped.isEmpty(), "a refused skip must not advance the campaign")
    }

    @Test
    fun noAdIsPlayedForASkipThatWasNeverAvailable() = runUnitTest {
        val ads = CountingAdGate(RewardOutcome.Rewarded)
        val repository = repository(adGate = ads, config = configOf("skipsPerDay" to 0))

        assertEquals(SkipResult.NoneLeft, repository.skip(levelId = 12))
        assertEquals(0, ads.shown, "nobody watches an ad for a skip they cannot have")
    }

    @Test
    fun aNegativeCapGrantsNothingRatherThanEverything() = runUnitTest {
        val repository = repository(config = configOf("skipsPerDay" to -4))

        assertEquals(0, repository.remainingToday())
        assertEquals(SkipResult.NoneLeft, repository.skip(levelId = 12))
    }

    @Test
    fun anAdThatFailsStillGrantsTheSkip() = runUnitTest {
        // SPEC 4.2: an ad network outage may never be the reason a player is
        // stuck. Only a deliberate dismissal withholds.
        listOf(
            RewardOutcome.NoFill,
            RewardOutcome.Offline,
            RewardOutcome.Failed("sdk"),
        ).forEach { outcome ->
            val repository = repository(adGate = CountingAdGate(outcome), cache = FakeSkipStateCache())
            assertIs<SkipResult.Skipped>(repository.skip(levelId = 12), "$outcome must still skip")
        }
    }

    @Test
    fun closingTheAdEarlyWithholdsTheSkipAndSpendsNothing() = runUnitTest {
        val repository = repository(adGate = CountingAdGate(RewardOutcome.Dismissed))

        assertEquals(SkipResult.Declined, repository.skip(levelId = 12))
        assertEquals(DefaultSkipsPerDay, repository.remainingToday())
        assertTrue(progress.skipped.isEmpty())
    }

    @Test
    fun proSkipsWithoutAnAdAndIsStillCapped() = runUnitTest {
        val ads = CountingAdGate(RewardOutcome.Rewarded)
        val repository = repository(adGate = ads, entitlements = ProEntitlements())

        repeat(DefaultSkipsPerDay) { assertIs<SkipResult.Skipped>(repository.skip(levelId = it)) }

        assertEquals(0, ads.shown, "Pro pays for the skip already")
        assertEquals(SkipResult.NoneLeft, repository.skip(levelId = 99))
    }

    @Test
    fun theCounterSurvivesAColdStart() = runUnitTest {
        repository().skip(levelId = 12)

        // A second repository over the same persisted state is what a
        // force-quit and relaunch looks like from here.
        assertEquals(DefaultSkipsPerDay - 1, repository().remainingToday())
    }

    @Test
    fun tomorrowRefillsTheAllowance() = runUnitTest {
        val repository = repository()
        repeat(DefaultSkipsPerDay) { repository.skip(levelId = it) }

        clock.set(Noon + OneDay)

        assertEquals(DefaultSkipsPerDay, repository.remainingToday())
    }

    @Test
    fun windingTheClockBackDoesNotRestoreASpentSkip() = runUnitTest {
        val repository = repository()
        repeat(DefaultSkipsPerDay) { repository.skip(levelId = it) }

        clock.set(Noon - OneDay * 3)

        assertEquals(0, repository.remainingToday())
        assertEquals(SkipResult.NoneLeft, repository.skip(levelId = 99))
    }

    @Test
    fun windingTheClockForwardAndBackLeavesTheAllowanceSpentUntilTheDateCatchesUp() = runUnitTest {
        val repository = repository()
        repeat(DefaultSkipsPerDay) { repository.skip(levelId = it) }

        // The cheat: claim it is next week, take the fresh allowance, then put
        // the clock back. The recorded day only ever moves forward, so what
        // comes back is next week's spent allowance, not today's fresh one.
        clock.set(Noon + OneDay * 7)
        repeat(DefaultSkipsPerDay) { repository.skip(levelId = it) }
        clock.set(Noon)

        assertEquals(0, repository.remainingToday())

        // And it stays spent for every real day in between, which is what makes
        // the cheat cost more than it buys.
        clock.set(Noon + OneDay * 3)
        assertEquals(0, repository.remainingToday())

        clock.set(Noon + OneDay * 8)
        assertEquals(DefaultSkipsPerDay, repository.remainingToday())
    }

    @Test
    fun flyingWestRepeatsADateAndGrantsNothingNew() = runUnitTest {
        val zone = MovableZone(TimeZone.of("Pacific/Auckland"))
        val repository = repository(timeZone = zone)
        repeat(DefaultSkipsPerDay) { repository.skip(levelId = it) }

        // Same instant, a zone thirteen hours behind: the local date goes back
        // to the previous day.
        zone.zone = TimeZone.of("America/Los_Angeles")

        assertEquals(0, repository.remainingToday(), "a flight west is not a second allowance")
    }

    private fun repository(
        adGate: AdGate = CountingAdGate(RewardOutcome.Rewarded),
        entitlements: Entitlements = FreeEntitlements(),
        cache: SkipStateCache = this.cache,
        timeZone: DeviceTimeZone = DeviceTimeZone { TimeZone.UTC },
        config: AppConfigMap = configOf(),
    ) = SkipRepositoryImpl(
        progress = progress,
        cache = cache,
        adGate = adGate,
        entitlements = entitlements,
        clock = clock,
        timeZone = timeZone,
        skipsPerDay = ProgressionSkipsPerDay(config),
    )

    private fun configOf(vararg values: Pair<String, Any>) = object : AppConfigMap() {
        override val map = mapOf("progression" to values.toMap())
    }

    private companion object {
        /** Matches `ProgressionSkipsPerDay.default`, and the tests say when they mean it. */
        const val DefaultSkipsPerDay = 3

        /** Midday, so a zone shift of a few hours does not roll the date by itself. */
        val Noon: Instant = Instant.parse("2026-09-07T12:00:00Z")

        val OneDay = 1.days
    }
}

@OptIn(ExperimentalTime::class)
private class MovableClock(private var current: Instant) : Clock {
    override fun now(): Instant = current

    fun set(instant: Instant) {
        current = instant
    }
}

private class MovableZone(var zone: TimeZone) : DeviceTimeZone {
    override fun current(): TimeZone = zone
}

private class FakeSkipStateCache : SkipStateCache {
    private val state = MutableStateFlow(SkipState())
    override val updates: Flow<SkipState> = state
    override suspend fun get(): SkipState = state.value
    override suspend fun set(value: SkipState) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = SkipState()
    }
}

/** Only [onSkipped] matters here; the rest is enough to satisfy the interface. */
private class RecordingProgress : ProgressRepository {
    val skipped = mutableListOf<Int>()

    override fun observe(levelId: Int): Flow<LevelRecord> = flowOf(LevelRecord.unplayed(levelId))
    override suspend fun record(levelId: Int): LevelRecord = LevelRecord.unplayed(levelId)
    override suspend fun all(): List<LevelRecord> = emptyList()
    override suspend fun unlockedThrough(): Int = LevelRecord.FIRST_LEVEL_ID
    override suspend fun onAttemptStarted(levelId: Int) = Unit
    override suspend fun onCompleted(levelId: Int, score: Int, paws: Int, timeMs: Long) = Unit

    override suspend fun onSkipped(levelId: Int) {
        skipped += levelId
    }

    override suspend fun reset() {
        skipped.clear()
    }
}

private class CountingAdGate(private val outcome: RewardOutcome) : AdGate {
    var shown = 0
        private set

    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome {
        shown++
        return outcome
    }

    override suspend fun showInterstitial(placement: AdPlacement): AdOutcome = AdOutcome.NotShown
    override fun preload(placement: AdPlacement) = Unit
}

private class FreeEntitlements : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun purchasePro() = PurchaseOutcome.Unavailable
    override suspend fun restore() = RestoreOutcome.NothingToRestore
}

private class ProEntitlements : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun purchasePro() = PurchaseOutcome.AlreadyOwned
    override suspend fun restore() = RestoreOutcome.Restored
}
