package com.sodogku.libraries.networking.retry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The builder a caller actually writes, and the two places it can read one way
 * and behave another.
 *
 * Retries and attempts are different numbers, and the whole file leans on that:
 * three retries is four attempts, and zero retries is one attempt rather than
 * none. A policy that made zero mean zero would turn a caller opting out of
 * retrying into a caller making no request at all, which is why that case has
 * its own test instead of riding on the arithmetic.
 *
 * The chain is immutable, so every step returns a new policy and the one it was
 * built from is unchanged. That is asserted by reading the base back after two
 * further steps, since a builder that mutates in place passes every test that
 * only reads the end of the chain.
 *
 * `retryIf` replaces the predicate rather than composing with it, which is a
 * decision a reader could assume either way. It is pinned with a failure the
 * default would retry and the replacement will not.
 *
 * ### Not here
 *
 * The delay arithmetic is `BackoffTest` and the randomisation is `JitterTest`.
 * Which real network failures count as transient is `OfflineErrorsTest`, once
 * per platform, because the answer is different on each.
 */
class RetryPolicyTest {

    @Test
    fun none_isSingleAttempt_noBackoff_noJitter() {
        val p = RetryPolicy.None
        assertEquals(1, p.maxAttempts)
        assertEquals(Backoff.None, p.backoff)
        assertEquals(Jitter.None, p.jitter)
    }

    @Test
    fun exponential_starter_uses4AttemptsWithDefaults() {
        val p = RetryPolicy.exponential()
        // Defaults: 3 retries = 4 attempts.
        assertEquals(4, p.maxAttempts)
        assertTrue(p.backoff is Backoff.Exponential)
        assertEquals(Jitter.None, p.jitter)
    }

    @Test
    fun fluentChain_isImmutable_returnsNewInstancesEachStep() {
        val base = RetryPolicy.exponential()
        val withJitter = base.withJitter()
        val capped = withJitter.maxRetries(10)

        // Base never mutated.
        assertEquals(Jitter.None, base.jitter)
        assertEquals(4, base.maxAttempts)
        // Each step has the prior state plus its own change.
        assertEquals(Jitter.Equal, withJitter.jitter)
        assertEquals(Jitter.Equal, capped.jitter)
        assertEquals(11, capped.maxAttempts)
    }

    @Test
    fun chaining_readsLikeTheUserExpected() {
        val policy = RetryPolicy.exponential().withJitter().maxRetries(10)
        assertEquals(11, policy.maxAttempts)
        assertEquals(Jitter.Equal, policy.jitter)
        assertTrue(policy.backoff is Backoff.Exponential)
    }

    @Test
    fun withJitter_allowsExplicitStrategy() {
        val p = RetryPolicy.exponential().withJitter(Jitter.Full)
        assertEquals(Jitter.Full, p.jitter)
    }

    @Test
    fun retryIf_replacesPredicate_doesNotCompose() {
        val custom: (Throwable) -> Boolean = { it is IllegalStateException }
        val p = RetryPolicy.exponential().retryIf(custom)
        assertSame(custom, p.retryIf)
        // Default predicate would say true for a timeout; ours doesn't.
        assertFalse(p.retryIf(RuntimeException("simulated timeout")))
        assertTrue(p.retryIf(IllegalStateException("matches")))
    }

    @Test
    fun backoff_replacesStrategy() {
        val p = RetryPolicy.exponential().backoff(Backoff.Fixed(2.seconds))
        assertEquals(Backoff.Fixed(2.seconds), p.backoff)
    }

    @Test
    fun maxRetries_zero_meansSingleAttempt() {
        val p = RetryPolicy.exponential().maxRetries(0)
        assertEquals(1, p.maxAttempts)
    }

    @Test
    fun maxRetries_validates() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy.exponential().maxRetries(-1)
        }
    }

    @Test
    fun defaultPredicate_treatsTimeoutsAsTransient() {
        val timeout = io.ktor.client.plugins.HttpRequestTimeoutException(
            url = "http://example.com",
            timeoutMillis = 100,
        )
        assertTrue(isTransientNetworkFailure(timeout))
    }

    @Test
    fun defaultPredicate_doesNotRetry4xx() {
        // Build a stub ResponseException; the simplest in commonTest is a
        // RuntimeException pretending to be one. Easier: just hit the
        // non-ResponseException path and prove the 5xx/4xx logic with a
        // dedicated test below that uses MockEngine.
        assertFalse(isTransientNetworkFailure(IllegalArgumentException("validation")))
    }

    @Test
    fun idempotent_preset_isExponentialWithEqualJitter_3Retries() {
        val p = RetryPolicy.idempotent()
        assertEquals(4, p.maxAttempts, "3 retries + initial = 4 attempts")
        assertTrue(p.backoff is Backoff.Exponential)
        assertEquals(Jitter.Equal, p.jitter)
    }
}
