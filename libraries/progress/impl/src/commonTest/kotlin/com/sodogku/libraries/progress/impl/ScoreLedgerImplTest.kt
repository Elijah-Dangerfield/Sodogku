package com.sodogku.libraries.progress.impl

import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.ScoreLedger
import com.sodogku.libraries.progress.db.ScoreEventDao
import com.sodogku.libraries.progress.db.ScoreEventEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The disk half of the ledger. The arithmetic is `LifetimeScoreTest`; what is
 * left here is what gets written, what does not, and what is thrown away.
 *
 * Runs against an in-memory [ScoreEventDao] for the reason
 * `ProgressRepositoryImplTest` gives: a KMP Room database needs a native driver
 * the host JVM test source set does not have.
 */
@OptIn(ExperimentalTime::class)
class ScoreLedgerImplTest : CoroutineTest() {

    private val dao = FakeScoreEventDao()
    private val clock = SettableClock(atMs = 1_000_000L)

    @Test
    fun pointsAreStampedWithTheMomentTheyWereBanked() = runUnitTest {
        val ledger = ledger()

        ledger.bank(400)
        clock.set(1_500_000L)
        ledger.bank(600)

        assertEquals(
            listOf(1_000_000L to 400, 1_500_000L to 600),
            dao.rows.map { it.atMillis to it.points },
        )
    }

    @Test
    fun aWindowThatOpenedBetweenTwoClearsSeesOnlyTheSecond() = runUnitTest {
        val ledger = ledger()
        ledger.bank(400)
        clock.set(1_500_000L)
        ledger.bank(600)

        assertEquals(600, ledger.bankedSince(1_200_000L))
    }

    @Test
    fun aReplayThatBeatNothingWritesNothing() = runUnitTest {
        val ledger = ledger()

        ledger.bank(0)
        ledger.bank(-780)

        assertTrue(dao.rows.isEmpty(), "an entry worth nothing is still an entry to read back")
    }

    @Test
    fun twoClearsInTheSameMillisecondAreTwoEntries() = runUnitTest {
        // The reason the table has a surrogate key. Keyed on the timestamp, the
        // second of these would silently replace the first.
        val ledger = ledger()

        ledger.bank(400)
        ledger.bank(600)

        assertEquals(1_000, ledger.bankedSince(0L))
    }

    @Test
    fun entriesOlderThanTheRetentionWindowArePrunedOnTheNextWrite() = runUnitTest {
        val ledger = ledger()
        ledger.bank(400)

        clock.set(1_000_000L + ScoreLedger.RETENTION_MILLIS + 1)
        ledger.bank(600)

        assertEquals(listOf(600), dao.rows.map { it.points })
    }

    @Test
    fun pruningNeverTakesAnEntryAWindowCouldStillWant() = runUnitTest {
        // Game Center will not accept a recurrence longer than 30 days, so the
        // margin here is the thing that makes the pruning above safe rather than
        // a judgement call. Asserted rather than commented, because shrinking
        // the retention is exactly the change that would look harmless.
        val longestWindowGameCenterAllows = 30L * 24 * 60 * 60 * 1000

        assertTrue(
            ScoreLedger.RETENTION_MILLIS > longestWindowGameCenterAllows,
            "retention of ${ScoreLedger.RETENTION_MILLIS}ms would prune inside a live window",
        )
    }

    private fun ledger() = ScoreLedgerImpl(dao, clock)
}

@OptIn(ExperimentalTime::class)
private class SettableClock(atMs: Long) : Clock {
    private var current = Instant.fromEpochMilliseconds(atMs)
    fun set(atMs: Long) {
        current = Instant.fromEpochMilliseconds(atMs)
    }

    override fun now(): Instant = current
}

private class FakeScoreEventDao : ScoreEventDao {

    val rows = mutableListOf<ScoreEventEntity>()
    private var nextId = 1L

    override suspend fun insert(row: ScoreEventEntity) {
        rows += row.copy(id = nextId++)
    }

    override suspend fun all(): List<ScoreEventEntity> = rows.sortedBy { it.atMillis }

    override suspend fun deleteBefore(beforeMillis: Long) {
        rows.removeAll { it.atMillis < beforeMillis }
    }
}
