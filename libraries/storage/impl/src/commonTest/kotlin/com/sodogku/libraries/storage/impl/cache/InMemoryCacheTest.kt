package com.sodogku.libraries.storage.impl.cache

import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The regression test for a bug that shipped through a green suite.
 *
 * `Cache.update`'s interface default is `set(transform(get()))`, which two
 * overlapping writers each apply to a snapshot the other has already replaced,
 * so the later write silently reverts the earlier one. On a cold start several
 * writers touch `AppData` at once — the install-id minter, the review
 * coordinator, the navigation tracker — and the symptom was a setting the player
 * had just flipped being back off on the next launch.
 *
 * Nothing caught it because every ViewModel test uses a single-writer fake,
 * where the interleaving cannot happen. Those tests are correct and stayed green
 * the whole time. So the guard belongs here, against the real implementation.
 *
 * It deliberately does **not** run on the test dispatcher. That dispatcher is
 * single-threaded, and neither `get` nor `set` actually suspends, so a
 * read-then-write would run atomically anyway and this test would pass against
 * the very bug it exists for. Real threads are the point.
 */
class InMemoryCacheTest : CoroutineTest() {

    @Test
    fun concurrentUpdatesDoNotLoseEachOther() = runUnitTest {
        val cache = InMemoryCache { Counters() }

        // Two fields, two writers. With one field a lost update looks like a
        // count that is merely low; with two, the failure being modelled is
        // visible as one writer's field reverting under the other's stale
        // snapshot.
        withContext(Dispatchers.Default) {
            val bumpA = List(WRITES) { async { cache.update { it.copy(a = it.a + 1) } } }
            val bumpB = List(WRITES) { async { cache.update { it.copy(b = it.b + 1) } } }
            (bumpA + bumpB).awaitAll()
        }

        assertEquals(Counters(WRITES, WRITES), cache.get())
    }

    @Test
    fun updateReturnsWhatItsOwnTransformProduced() = runUnitTest {
        val cache = InMemoryCache { Counters() }

        val returned = cache.update { it.copy(a = 7) }

        // Not "whatever is in the cache now". An update-then-read implementation
        // hands back a later writer's value, which the caller never asked for.
        assertEquals(7, returned.a)
        assertEquals(cache.get(), returned)
    }

    @Test
    fun clearGoesBackToTheInitialValueNotAnEmptyOne() = runUnitTest {
        val cache = InMemoryCache { Counters(a = 5) }
        cache.update { it.copy(a = 9, b = 9) }

        cache.clear()

        assertEquals(Counters(a = 5), cache.get())
    }

    private data class Counters(val a: Int = 0, val b: Int = 0)

    private companion object {
        /** High enough that a lost update is near-certain rather than flaky-rare. */
        const val WRITES = 500
    }
}
