package com.sodogku.libraries.networking.retry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long to wait before trying again, for each of the four strategies.
 *
 * Mostly arithmetic, and pinned at several attempt numbers rather than one
 * because an off-by-one in the exponent is invisible at attempt one and doubles
 * every wait after it. The two tests that are not arithmetic are the ones worth
 * having.
 *
 * The cap has to hold for pathological attempt counts. Computed in nanoseconds
 * the exponential overflows a `Long` somewhere around attempt forty, which
 * turns a sixty second ceiling into a negative delay and then into no wait at
 * all, so a phone with a dead connection hammers the gateway. Attempt one
 * hundred is asked for explicitly.
 *
 * The two constructor refusals, a growth factor at or below one and an initial
 * delay above the ceiling, both describe a policy that silently does nothing
 * like backoff. Refusing at construction puts the failure where somebody wrote
 * it rather than in production traffic.
 *
 * ### Not here
 *
 * Randomisation on top of these numbers is `JitterTest`, and how many attempts
 * are made and which failures qualify is `RetryPolicyTest`.
 */
class BackoffTest {

    @Test
    fun none_alwaysReturnsZero() {
        repeat(10) { i ->
            assertEquals(Duration.ZERO, Backoff.None.delayFor(attempt = i + 1))
        }
    }

    @Test
    fun fixed_returnsSameDelay_regardlessOfAttempt() {
        val backoff = Backoff.Fixed(750.milliseconds)
        assertEquals(750.milliseconds, backoff.delayFor(1))
        assertEquals(750.milliseconds, backoff.delayFor(5))
        assertEquals(750.milliseconds, backoff.delayFor(100))
    }

    @Test
    fun linear_scalesByAttempt() {
        val backoff = Backoff.Linear(step = 500.milliseconds)
        assertEquals(500.milliseconds, backoff.delayFor(1))
        assertEquals(1000.milliseconds, backoff.delayFor(2))
        assertEquals(2500.milliseconds, backoff.delayFor(5))
    }

    @Test
    fun exponential_growsByFactor() {
        val backoff = Backoff.Exponential(initial = 100.milliseconds, factor = 2.0, max = 10.seconds)
        assertEquals(100.milliseconds, backoff.delayFor(1))
        assertEquals(200.milliseconds, backoff.delayFor(2))
        assertEquals(400.milliseconds, backoff.delayFor(3))
        assertEquals(800.milliseconds, backoff.delayFor(4))
    }

    @Test
    fun exponential_capsAtMax() {
        val backoff = Backoff.Exponential(initial = 500.milliseconds, factor = 2.0, max = 2.seconds)
        // 500ms → 1s → 2s (cap) → 2s (cap) → 2s (cap)
        assertEquals(500.milliseconds, backoff.delayFor(1))
        assertEquals(1000.milliseconds, backoff.delayFor(2))
        assertEquals(2000.milliseconds, backoff.delayFor(3))
        assertEquals(2000.milliseconds, backoff.delayFor(4))
        assertEquals(2000.milliseconds, backoff.delayFor(50))
    }

    @Test
    fun exponential_pathologicalAttemptCount_doesNotOverflow() {
        val backoff = Backoff.Exponential(initial = 1.seconds, factor = 2.0, max = 60.seconds)
        // Without the millis-Double computation this would overflow Long
        // nanos around attempt ~40. The cap should hold.
        assertEquals(60.seconds, backoff.delayFor(100))
    }

    @Test
    fun exponential_validatesFactor() {
        assertFailsWith<IllegalArgumentException> {
            Backoff.Exponential(initial = 1.seconds, factor = 0.5, max = 10.seconds)
        }
    }

    @Test
    fun exponential_validatesInitialNotGreaterThanMax() {
        assertFailsWith<IllegalArgumentException> {
            Backoff.Exponential(initial = 10.seconds, factor = 2.0, max = 5.seconds)
        }
    }
}
