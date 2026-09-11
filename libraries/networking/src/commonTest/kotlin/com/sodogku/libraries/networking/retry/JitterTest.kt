package com.sodogku.libraries.networking.retry

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Spreading retries out so a fleet that failed together does not come back
 * together.
 *
 * The point of jitter is a distribution, and a distribution is what a single
 * assertion cannot see, so each strategy is walked two hundred times through a
 * seeded random and held to its range. Seeded rather than free, because a range
 * test on real randomness is a test that fails once a month on somebody else's
 * change.
 *
 * The zero case is the one with teeth. No configured backoff means no delay,
 * and jitter must not invent one out of nothing. It also guards a crash:
 * picking a random value in an empty range throws, so an unguarded jitter turns
 * a no-retry policy into an exception on the first failure.
 *
 * ### Not here
 *
 * The delays being jittered are `BackoffTest`. Whether a retry happens at all
 * is `RetryPolicyTest`.
 */
class JitterTest {

    @Test
    fun none_returnsDelayUnchanged() {
        assertEquals(800.milliseconds, Jitter.None.apply(800.milliseconds))
        assertEquals(Duration.ZERO, Jitter.None.apply(Duration.ZERO))
    }

    @Test
    fun equal_returnsValueInHalfToOneAndAHalfRange() {
        val delay = 1000.milliseconds
        // Walk a seeded random so the assertion isn't flaky.
        val random = Random(seed = 42)
        repeat(200) {
            val jittered = Jitter.Equal.apply(delay, random)
            assertTrue(
                jittered >= 500.milliseconds && jittered < 1500.milliseconds,
                "Equal jitter must land in [delay/2, delay*1.5); got $jittered",
            )
        }
    }

    @Test
    fun full_returnsValueInZeroToDelayRange() {
        val delay = 1000.milliseconds
        val random = Random(seed = 7)
        repeat(200) {
            val jittered = Jitter.Full.apply(delay, random)
            assertTrue(
                jittered >= Duration.ZERO && jittered < 1000.milliseconds,
                "Full jitter must land in [0, delay); got $jittered",
            )
        }
    }

    @Test
    fun equal_andFull_returnZero_whenDelayIsZero() {
        // Zero base delay means no backoff configured — jitter must not
        // synthesize delay out of nothing (and Random.nextLong(0, 0) would
        // throw if we didn't guard it).
        assertEquals(Duration.ZERO, Jitter.Equal.apply(Duration.ZERO))
        assertEquals(Duration.ZERO, Jitter.Full.apply(Duration.ZERO))
    }
}
