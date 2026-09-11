package com.sodogku.libraries.flowroutines

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Work that should run while a key is present, and must stop the moment it is
 * not.
 *
 * The primitive looks small and has a lot of edges, which is why the file is
 * long. Each test names one transition: a key already present when somebody
 * subscribes, a key arriving later, a key changing to a different one, and a
 * key going away. The last is the one with consequences, and it is asserted
 * twice, once against work that is mid-flight and once against a retry sitting
 * in its backoff. A cancellation that reaches the running body and not the
 * pending delay leaves work that wakes up after the key it belonged to is gone.
 *
 * Repeat requests while a slow run is in progress coalesce into one trailing
 * run rather than queueing, because the caller is asking for fresh results and
 * not for a backlog.
 *
 * Retries stop on success, stop on exhaustion, and re-arm a fresh counter on
 * the next request, so an early streak of failures does not poison the rest of
 * the session. A body that throws is a failed attempt rather than the death of
 * the surrounding scope, which is the difference between one retry and a silent
 * end to everything the scope was running.
 *
 * The external cancellation test is about a promise, not a mechanism. Whatever
 * cancels the job seen from inside the body has to be cancelling the *whole*
 * cycle, pending backoff and repeat edges included, and the loop still has to
 * be alive for the next key.
 *
 * Timing is virtual throughout, so nothing here sleeps.
 *
 * ### Not here
 *
 * The delay arithmetic under a retrying network call is
 * `:libraries:networking`. Anything about view-model scopes is `SEAViewModel`
 * and its own tests.
 */
class RunWhenTest {

    @Test
    fun keyAlreadyNonNullAtSubscribe_firesExactlyOnce() = runTest {
        val key = MutableStateFlow<String?>("u1")
        val runs = mutableListOf<String>()

        backgroundScope.runWhen(key = key) { k ->
            runs += k
            Result.success(Unit)
        }
        runCurrent()

        assertEquals(listOf("u1"), runs, "the boot race: a level true before subscribe still fires")
    }

    @Test
    fun nullToNonNull_firesOnce_staysNullNeverFires() = runTest {
        val key = MutableStateFlow<String?>(null)
        var runs = 0

        backgroundScope.runWhen(key = key) {
            runs++
            Result.success(Unit)
        }
        runCurrent()
        assertEquals(0, runs)

        key.value = "u1"
        runCurrent()
        assertEquals(1, runs)
    }

    @Test
    fun keyChange_cancelsInFlightWork_andFiresFresh() = runTest {
        val key = MutableStateFlow<String?>("a")
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val gateForA = CompletableDeferred<Unit>()

        backgroundScope.runWhen(key = key) { k ->
            started += k
            if (k == "a") gateForA.await()
            completed += k
            Result.success(Unit)
        }
        runCurrent()
        assertEquals(listOf("a"), started)

        key.value = "b"
        runCurrent()

        assertEquals(listOf("a", "b"), started)
        assertEquals(listOf("b"), completed, "a's in-flight run was cancelled, not completed")
    }

    @Test
    fun keyToNull_cancelsMidRun() = runTest {
        val key = MutableStateFlow<String?>("a")
        var completed = 0

        backgroundScope.runWhen(key = key) {
            CompletableDeferred<Unit>().await()
            completed++
            Result.success(Unit)
        }
        runCurrent()

        key.value = null
        runCurrent()
        advanceTimeBy(10.seconds)
        assertEquals(0, completed)
    }

    @Test
    fun keyToNull_cancelsPendingRetryBackoff() = runTest {
        val key = MutableStateFlow<String?>("a")
        var attempts = 0

        backgroundScope.runWhen(
            key = key,
            retry = RunWhenRetry.exponential(initial = 5.seconds, retries = 5),
        ) {
            attempts++
            Result.failure(RuntimeException("nope"))
        }
        runCurrent()
        assertEquals(1, attempts)

        key.value = null
        runCurrent()
        advanceTimeBy(60.seconds)
        assertEquals(1, attempts, "backoff was cancelled; no further attempts after key went null")
    }

    @Test
    fun refire_whileNonNull_runsAgain_whileNullDoesNothing() = runTest {
        val key = MutableStateFlow<String?>(null)
        val refire = MutableSharedFlow<Unit>()
        var runs = 0

        backgroundScope.runWhen(key = key, refireOn = refire) {
            runs++
            Result.success(Unit)
        }
        runCurrent()

        refire.emit(Unit)
        runCurrent()
        assertEquals(0, runs, "an edge while the key is null is ignored")

        key.value = "u1"
        runCurrent()
        assertEquals(1, runs)

        refire.emit(Unit)
        runCurrent()
        assertEquals(2, runs)
    }

    @Test
    fun refiresDuringSlowRun_coalesceToOneTrailingRun() = runTest {
        val key = MutableStateFlow<String?>("u1")
        val refire = MutableSharedFlow<Unit>()
        val gate = CompletableDeferred<Unit>()
        var runs = 0

        backgroundScope.runWhen(key = key, refireOn = refire) {
            runs++
            if (runs == 1) gate.await()
            Result.success(Unit)
        }
        runCurrent()
        assertEquals(1, runs)

        repeat(3) { refire.emit(Unit) }
        runCurrent()
        assertEquals(1, runs, "still inside the slow first run")

        gate.complete(Unit)
        runCurrent()
        assertEquals(2, runs, "three edges during a run coalesce into one trailing run")
    }

    @Test
    fun failure_retriesOnSchedule_successStops() = runTest {
        val key = MutableStateFlow<String?>("u1")
        val attemptTimes = mutableListOf<Long>()

        backgroundScope.runWhen(
            key = key,
            retry = RunWhenRetry.exponential(initial = 5.seconds, factor = 2.0, retries = 5),
        ) {
            attemptTimes += testScheduler.currentTime
            if (attemptTimes.size < 3) Result.failure(RuntimeException("nope")) else Result.success(Unit)
        }
        runCurrent()
        advanceTimeBy(60.seconds)

        assertEquals(listOf(0L, 5_000L, 15_000L), attemptTimes, "t=0, +5s, then +10s; success stops the cycle")
    }

    @Test
    fun exhaustion_stops_andNextRefireReArmsFreshCounter() = runTest {
        val key = MutableStateFlow<String?>("u1")
        val refire = MutableSharedFlow<Unit>()
        var attempts = 0

        backgroundScope.runWhen(
            key = key,
            refireOn = refire,
            retry = RunWhenRetry.exponential(initial = 5.seconds, retries = 2),
        ) {
            attempts++
            Result.failure(RuntimeException("nope"))
        }
        runCurrent()
        advanceTimeBy(120.seconds)
        assertEquals(3, attempts, "1 initial + 2 retries, then exhaustion stops")

        refire.emit(Unit)
        runCurrent()
        advanceTimeBy(120.seconds)
        assertEquals(6, attempts, "the next edge re-arms a fresh retry counter")
    }

    @Test
    fun booleanOverload_trueAtSubscribeFires_repeatedTrueDoesNotRefire() = runTest {
        val condition = MutableSharedFlow<Boolean>(replay = 1)
        condition.emit(true)
        var runs = 0

        backgroundScope.runWhen(condition = condition) {
            runs++
            Result.success(Unit)
        }
        runCurrent()
        assertEquals(1, runs)

        condition.emit(true)
        runCurrent()
        assertEquals(1, runs, "a repeated true is not an edge")

        condition.emit(false)
        condition.emit(true)
        runCurrent()
        assertEquals(2, runs, "a genuine false→true transition fires again")
    }

    @Test
    fun cancellingTheCycleJobFromOutside_killsRetriesAndRefires_butTheNextKeyStillFires() = runTest {
        // External quiescing (UserScopedWorkRegistry) registers the job seen
        // inside work and cancels it on user switch. That only closes the
        // clear window if the job covers the WHOLE cycle — pending backoff and
        // refire edges included — and the loop survives for the next key.
        val key = MutableStateFlow<String?>("u1")
        val refire = MutableSharedFlow<Unit>()
        val runs = mutableListOf<String>()
        var cycleJob: Job? = null

        backgroundScope.runWhen(
            key = key,
            refireOn = refire,
            retry = RunWhenRetry.exponential(initial = 5.seconds, retries = 5),
        ) { k ->
            cycleJob = currentCoroutineContext()[Job]
            runs += k
            Result.failure(RuntimeException("nope"))
        }
        runCurrent()
        assertEquals(listOf("u1"), runs)

        cycleJob!!.cancel()
        cycleJob!!.join()
        advanceTimeBy(120.seconds)
        assertEquals(listOf("u1"), runs, "cancelling the cycle job killed the pending retry")

        refire.emit(Unit)
        runCurrent()
        assertEquals(listOf("u1"), runs, "refire edges died with the cycle")

        key.value = "u2"
        runCurrent()
        assertEquals(listOf("u1", "u2"), runs, "the loop stayed alive for the next key")
    }

    @Test
    fun workThrowing_isAFailedAttempt_notScopeDeath() = runTest {
        val key = MutableStateFlow<String?>("u1")
        val refire = MutableSharedFlow<Unit>()
        var runs = 0

        backgroundScope.runWhen(key = key, refireOn = refire) {
            runs++
            throw RuntimeException("boom")
        }
        runCurrent()
        assertEquals(1, runs)

        refire.emit(Unit)
        runCurrent()
        assertEquals(2, runs, "the loop survived the throw and kept listening")
    }
}
