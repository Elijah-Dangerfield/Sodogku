package com.sodogku.libraries.progress.impl.daily

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdOutcome
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.DailyEnabled
import com.sodogku.libraries.config.values.DailyFreezesPerMonth
import com.sodogku.libraries.config.values.DailyPoolOffset
import com.sodogku.libraries.config.values.FeatureDailyChallenge
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.daily.FreezeResult
import com.sodogku.libraries.progress.db.DailyResultDao
import com.sodogku.libraries.progress.db.DailyResultEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What the daily card is allowed to see and what the day is allowed to do:
 * selection from the local date, one attempt per day whatever the clock says,
 * and a freeze that is bought once and covers one day.
 *
 * The streak *rules* are pinned in `DailyStreakTest` against the pure fold, and
 * the date arithmetic in `DailyCalendarTest`. What is here is the wiring those
 * two can't see — that the repository walks history rather than a counter, that
 * the database refuses a second result, and that a clock the player has moved
 * cannot leave anything permanently wrong.
 *
 * Runs against an in-memory [DailyResultDao] for the reason
 * `ProgressRepositoryImplTest` gives: a KMP Room database needs a native driver
 * the host JVM test source set does not have. The one query with real behaviour
 * in it — `insertIfAbsent` ignoring a conflict — is reproduced exactly by the
 * fake, and it is the reason the fake is a map keyed on date.
 */
@OptIn(ExperimentalTime::class)
class DailyRepositoryImplTest : CoroutineTest() {

    private val dao = FakeDailyResultDao()
    private val adGate = FakeAdGate()
    private val clock = MovableClock(Instant.parse("2026-09-07T20:00:00Z"))
    private var zone: TimeZone = TimeZone.UTC

    @Test
    fun theBoardComesFromThePlayersLocalDate() = runUnitTest {
        val repo = repository()

        val inLondon = repo.status()

        zone = TimeZone.of("Europe/Berlin")
        clock.set(Instant.parse("2026-09-07T23:30:00Z"))
        val inBerlin = repository().status()

        assertEquals(LocalDate(2026, 9, 7), inLondon.date)
        assertEquals(LocalDate(2026, 9, 8), inBerlin.date, "an hour ahead is already on tomorrow")
        assertTrue(inLondon.packIndex != inBerlin.packIndex, "and tomorrow is a different board")
        assertEquals(LevelPacks.daily[inLondon.packIndex].id, inLondon.levelId)
    }

    @Test
    fun everyPlayerOnTheSameLocalDateGetsTheSameBoard() = runUnitTest {
        zone = TimeZone.of("Pacific/Auckland")
        clock.set(Instant.parse("2026-09-07T00:00:00Z"))
        val auckland = repository().status()

        zone = TimeZone.of("America/Los_Angeles")
        clock.set(Instant.parse("2026-09-07T18:00:00Z"))
        val losAngeles = repository().status()

        assertEquals(LocalDate(2026, 9, 7), auckland.date)
        assertEquals(losAngeles.date, auckland.date, "22 hours apart, same calendar day")
        assertEquals(
            losAngeles.packIndex,
            auckland.packIndex,
            "the daily is the same board for everyone, which is the whole reason it is shareable",
        )
    }

    @Test
    fun poolOffsetShiftsWhichBoardTheDayResolvesTo() = runUnitTest {
        val unshifted = repository().status()
        val shifted = repository(poolOffset = 3).status()

        assertEquals((unshifted.packIndex + 3) % LevelPacks.daily.size, shifted.packIndex)
        assertEquals(unshifted.date, shifted.date, "the offset moves the board, never the date")
    }

    @Test
    fun aCompletedDailyCannotBeReplayedForABetterScore() = runUnitTest {
        val repo = repository()
        val today = repo.status().date

        repo.onCompleted(today, score = 900, paws = 2, timeMs = 60_000)
        repo.onCompleted(today, score = 9_999, paws = 3, timeMs = 30_000)

        val status = repo.status()
        assertEquals(900, status.result?.score, "the first result is the day's result, better or worse")
        assertEquals(2, status.result?.paws)
        assertTrue(!status.playable)
        assertEquals(1, repo.history().size, "and there is only ever one row per day")
    }

    @Test
    fun aFailedDailySpendsTheDayToo() = runUnitTest {
        val repo = repository()
        val today = repo.status().date

        repo.onFailed(today, timeMs = 45_000)
        repo.onCompleted(today, score = 900, paws = 3, timeMs = 30_000)

        val status = repo.status()
        assertEquals(DailyOutcome.Failed, status.result?.outcome)
        assertEquals(0, status.result?.score, "losing then 'winning' the same day cannot rewrite it")
        assertTrue(!status.playable)
    }

    @Test
    fun theResultIsRecordedAgainstTheBoardThatWasPlayed_notTheClockAtTheEnd() = runUnitTest {
        val repo = repository()
        val started = repo.status()

        clock.advance(4.hours + 1.milliseconds)
        repo.onCompleted(started.date, score = 700, paws = 2, timeMs = 90_000)

        val afterMidnight = repo.status()
        assertEquals(started.date.nextDay(), afterMidnight.date)
        assertEquals(
            started.packIndex,
            repo.history().single().levelIndex,
            "an attempt that ran through midnight belongs to the board it started on",
        )
        assertTrue(afterMidnight.playable, "and the new day is genuinely unplayed")
        assertEquals(1, afterMidnight.streak)
    }

    @Test
    fun midnightOffersANewBoardAndReopensPlay() = runUnitTest {
        val repo = repository()
        val today = repo.status()
        repo.onCompleted(today.date, score = 500, paws = 2, timeMs = 60_000)

        assertTrue(!repo.status().playable)

        clock.advance(4.hours)

        val tomorrow = repo.status()
        assertEquals(today.date.nextDay(), tomorrow.date)
        assertEquals((today.packIndex + 1) % LevelPacks.daily.size, tomorrow.packIndex)
        assertTrue(tomorrow.playable)
        assertEquals(1, tomorrow.streak, "yesterday's clear still counts all of today")
    }

    @Test
    fun observe_reEmitsWhenTheDateRollsOver() = runUnitTest {
        clock.followVirtualTime { testScheduler.currentTime.milliseconds }
        val repo = repository()
        val seen = mutableListOf<Pair<LocalDate, Int>>()
        backgroundScope.launch { repo.observe().collect { seen += it.date to it.packIndex } }

        testScheduler.advanceTimeBy(4.hours + 1.milliseconds)

        assertEquals(2, seen.size, "one for today, one for the day that arrived while the card was open")
        assertEquals(LocalDate(2026, 9, 7), seen.first().first)
        assertEquals(LocalDate(2026, 9, 8), seen.last().first)
        assertTrue(seen.first().second != seen.last().second, "and the card is showing a new board")
    }

    @Test
    fun observe_reEmitsWhenTheDayIsPlayed() = runUnitTest {
        val repo = repository()
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { repo.observe().collect { seen += it.playable } }

        repo.onCompleted(repo.status().date, score = 500, paws = 2, timeMs = 60_000)

        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun theStreakIsRecomputed_soAClockSetBackCannotCorruptIt() = runUnitTest {
        val repo = repository()
        completeDays(repo, count = 5)
        assertEquals(5, repo.status().streak)

        clock.rewind(3.days)
        assertEquals(
            2,
            repo.status().streak,
            "three days back the player really had a two-day run; the number follows the date",
        )
        assertEquals(5, repo.history().size, "and nothing was thrown away to produce it")

        clock.advance(3.days)
        assertEquals(5, repo.status().streak, "putting the clock right puts the streak right")
    }

    @Test
    fun aDayAlreadyPlayedStaysLockedWhenTheClockGoesBack() = runUnitTest {
        val repo = repository()
        val today = repo.status().date
        repo.onCompleted(today, score = 400, paws = 1, timeMs = 80_000)

        clock.rewind(12.hours)

        val status = repo.status()
        assertEquals(today, status.date, "still the same local date, just earlier in it")
        assertTrue(!status.playable, "a clock set back is not a second attempt at a board")
        assertEquals(400, status.result?.score)
    }

    @Test
    fun aResultWrittenAheadOfTodayIsIgnoredUntilTheDateArrives() = runUnitTest {
        val repo = repository()
        completeDays(repo, count = 2)

        clock.advance(1.days)
        repo.onCompleted(repo.status().date, score = 800, paws = 3, timeMs = 50_000)
        clock.rewind(1.days)

        val backToNow = repo.status()
        assertEquals(2, backToNow.streak, "the future result is not part of a run that has not reached it")
        assertTrue(!backToNow.playable, "today was already played and stays played")
        assertEquals(3, repo.history().size, "the row is kept, not discarded")

        clock.advance(1.days)
        assertEquals(3, repo.status().streak, "and it counts once the date catches up")
    }

    @Test
    fun flyingEastSkipsADay_andTheFreezeOfferCoversIt() = runUnitTest {
        val repo = repository()
        completeDays(repo, count = 4)

        zone = TimeZone.of("Pacific/Auckland")
        clock.advance(20.hours)

        val afterTheFlight = repo.status()
        assertEquals(LocalDate(2026, 9, 9), afterTheFlight.date, "the local date skipped the 8th")
        assertEquals(0, afterTheFlight.streak, "a calendar day with nothing in it is a calendar day missed")

        val offer = assertNotNull(afterTheFlight.freezeOffer)
        assertEquals(LocalDate(2026, 9, 8), offer.missedDate)
        assertEquals(4, offer.streakIfUsed, "which is exactly what the freeze exists for")
    }

    @Test
    fun flyingWestGoesBackADay_withoutReopeningItOrLosingAnything() = runUnitTest {
        zone = TimeZone.of("Pacific/Auckland")
        clock.set(Instant.parse("2026-09-06T02:00:00Z"))
        val repo = repository()
        repo.onCompleted(repo.status().date, score = 600, paws = 2, timeMs = 70_000)
        clock.advance(1.days)
        repo.onCompleted(repo.status().date, score = 600, paws = 2, timeMs = 70_000)
        assertEquals(2, repo.status().streak)

        zone = TimeZone.of("America/Los_Angeles")

        val afterTheFlight = repo.status()
        assertEquals(LocalDate(2026, 9, 6), afterTheFlight.date, "nineteen hours back is yesterday again")
        assertTrue(!afterTheFlight.playable, "that board was already solved; it does not come round twice")
        assertEquals(
            1,
            afterTheFlight.streak,
            "the second clear is now in the future and waits there — the number follows the date",
        )
        assertEquals(2, repo.history().size, "nothing was rewritten to make that number")

        clock.advance(5.hours)
        assertEquals(LocalDate(2026, 9, 7), repo.status().date)
        assertEquals(2, repo.status().streak, "and it is all back the moment the date catches up")
    }

    @Test
    fun freeze_reconnectsTheStreakForAnAd() = runUnitTest {
        val repo = repository()
        seedHistory(completedDaysBack = (2..8).toList())

        val before = repo.status()
        assertEquals(0, before.streak, "yesterday is missing and today is not played, so nothing is running")

        val result = repo.useFreeze()

        assertEquals(FreezeResult.Applied(LocalDate(2026, 9, 6), streak = 7), result)
        assertEquals(1, adGate.rewardedShown.size)
        assertEquals(AdPlacement.StreakFreeze, adGate.rewardedShown.single())
        assertEquals(7, repo.status().streak)
    }

    @Test
    fun freeze_coversOneDayAndNotTheDayBehindIt() = runUnitTest {
        val repo = repository()
        seedHistory(completedDaysBack = (3..8).toList())

        assertEquals(FreezeResult.NothingToFreeze, repo.useFreeze())
        assertEquals(
            0,
            repo.status().streak,
            "two days are missing; one freeze cannot bridge both, so it is not sold",
        )
        assertTrue(adGate.rewardedShown.isEmpty(), "and no ad is burned finding that out")
    }

    @Test
    fun freeze_isWithheldWhenThePlayerClosesTheAd() = runUnitTest {
        adGate.outcome = RewardOutcome.Dismissed
        val repo = repository()
        seedHistory(completedDaysBack = (2..8).toList())

        assertEquals(FreezeResult.Declined, repo.useFreeze())
        assertEquals(0, repo.status().streak)
        assertEquals(7, repo.history().size, "nothing was written")
    }

    @Test
    fun freeze_isGrantedWhenTheAdNetworkFails() = runUnitTest {
        listOf(RewardOutcome.NoFill, RewardOutcome.Offline, RewardOutcome.Failed("sdk")).forEach { outcome ->
            dao.clear()
            adGate.outcome = outcome
            val repo = repository()
            seedHistory(completedDaysBack = (2..8).toList())

            assertEquals(
                7,
                (repo.useFreeze() as? FreezeResult.Applied)?.streak,
                "an ad that could not be served is not the player's fault: $outcome",
            )
        }
    }

    @Test
    fun freeze_skipsTheAdEntirelyForPro() = runUnitTest {
        val repo = repository(isPro = true)
        seedHistory(completedDaysBack = (2..8).toList())

        assertTrue(repo.useFreeze() is FreezeResult.Applied)
        assertTrue(adGate.rewardedShown.isEmpty(), "a paying player is never shown a rewarded ad")
    }

    @Test
    fun freeze_stopsAtTheMonthlyAllowance() = runUnitTest {
        val repo = repository(freezesPerMonth = 1)
        seedHistory(
            completedDaysBack = (2..8).toList(),
            frozenDates = listOf(LocalDate(2026, 9, 2)),
        )

        assertEquals(FreezeResult.NoneLeft, repo.useFreeze())
        assertNull(repo.status().freezeOffer, "an offer the cap would refuse is not shown")
        assertTrue(adGate.rewardedShown.isEmpty())
    }

    @Test
    fun killSwitchAndFeatureFlagBothCloseTheCard() = runUnitTest {
        assertTrue(repository().status().enabled, "on by default")
        assertTrue(!repository(dailyEnabled = false).status().enabled)
        assertTrue(!repository(featureEnabled = false).status().enabled)
        assertTrue(
            !repository(dailyEnabled = false).status().playable,
            "and a disabled daily is never playable, whatever the day looks like",
        )
    }

    @Test
    fun reset_wipesEveryDay() = runUnitTest {
        val repo = repository()
        completeDays(repo, count = 3)
        assertEquals(3, repo.status().streak)

        repo.reset()

        assertEquals(emptyList(), repo.history())
        assertEquals(0, repo.status().streak)
        assertTrue(repo.status().playable)
    }

    /** Plays [count] days ending today, oldest first, so the streak is a real run. */
    private suspend fun completeDays(repo: DailyRepositoryImpl, count: Int) {
        clock.rewind((count - 1).days)
        repeat(count) {
            repo.onCompleted(repo.status().date, score = 500, paws = 2, timeMs = 60_000)
            if (it < count - 1) clock.advance(1.days)
        }
    }

    /** Writes history straight to the dao, for shapes the repository would refuse to produce. */
    private fun seedHistory(completedDaysBack: List<Int>, frozenDates: List<LocalDate> = emptyList()) {
        val today = LocalDate(2026, 9, 7)
        completedDaysBack.forEach { back ->
            var date = today
            repeat(back) { date = date.previousDay() }
            dao.put(date, DailyOutcome.Completed)
        }
        frozenDates.forEach { dao.put(it, DailyOutcome.Frozen) }
    }

    private fun repository(
        isPro: Boolean = false,
        dailyEnabled: Boolean = true,
        featureEnabled: Boolean = true,
        poolOffset: Int = 0,
        freezesPerMonth: Int = 2,
    ): DailyRepositoryImpl {
        val config = configOf(
            "enabled" to dailyEnabled,
            "poolOffset" to poolOffset,
            "freezesPerMonth" to freezesPerMonth,
        )
        val features = object : AppConfigMap() {
            override val map = mapOf("features" to mapOf("dailyChallenge" to featureEnabled))
        }
        return DailyRepositoryImpl(
            dao = dao,
            clock = clock,
            timeZone = DeviceTimeZone { zone },
            adGate = adGate,
            entitlements = FakeEntitlements(isPro),
            dailyEnabled = DailyEnabled(config),
            featureEnabled = FeatureDailyChallenge(features),
            poolOffset = DailyPoolOffset(config),
            freezesPerMonth = DailyFreezesPerMonth(config),
        )
    }

    private fun configOf(vararg values: Pair<String, Any>) = object : AppConfigMap() {
        override val map = mapOf("daily" to values.toMap())
    }
}

@OptIn(ExperimentalTime::class)
private class MovableClock(private var current: Instant) : Clock {

    private var virtualTime: () -> Duration = { Duration.ZERO }

    override fun now(): Instant = current + virtualTime()

    fun set(instant: Instant) {
        current = instant
    }

    fun advance(by: Duration) {
        current += by
    }

    fun rewind(by: Duration) {
        current -= by
    }

    /**
     * Ties the clock to the test scheduler, so a `delay` inside the repository
     * moves the wall clock the way it would in life. Without it the rollover flow
     * would wake up at midnight and find it was still yesterday.
     */
    fun followVirtualTime(elapsed: () -> Duration) {
        virtualTime = elapsed
    }
}

private class FakeAdGate(var outcome: RewardOutcome = RewardOutcome.Rewarded) : AdGate {

    val rewardedShown = mutableListOf<AdPlacement>()

    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome {
        rewardedShown += placement
        return outcome
    }

    override suspend fun showInterstitial(placement: AdPlacement): AdOutcome = AdOutcome.NotShown

    override fun preload(placement: AdPlacement) = Unit
}

private class FakeEntitlements(isPro: Boolean) : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(isPro)
    override suspend fun purchasePro(trigger: String?): PurchaseOutcome = PurchaseOutcome.Unavailable
    override suspend fun restore(): RestoreOutcome = RestoreOutcome.NothingToRestore
}

private class FakeDailyResultDao : DailyResultDao {

    private val rows = MutableStateFlow<Map<String, DailyResultEntity>>(emptyMap())

    override suspend fun insertIfAbsent(row: DailyResultEntity): Long {
        if (rows.value.containsKey(row.date)) return CONFLICT_IGNORED
        rows.value = rows.value + (row.date to row)
        return 1L
    }

    override fun observeAll(): Flow<List<DailyResultEntity>> = rows.map { it.sorted() }

    override suspend fun all(): List<DailyResultEntity> = rows.value.sorted()

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }

    fun put(date: LocalDate, outcome: DailyOutcome) {
        val row = DailyResultEntity(
            date = date.toString(),
            levelIndex = 0,
            outcome = outcome.name,
            score = 0,
            paws = 0,
            timeMs = 0,
        )
        rows.value = rows.value + (row.date to row)
    }

    fun clear() {
        rows.value = emptyMap()
    }

    private fun Map<String, DailyResultEntity>.sorted() = values.sortedBy { it.date }

    private companion object {
        const val CONFLICT_IGNORED = -1L
    }
}
