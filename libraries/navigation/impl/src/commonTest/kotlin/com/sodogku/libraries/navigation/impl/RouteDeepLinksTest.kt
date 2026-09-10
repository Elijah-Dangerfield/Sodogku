package com.sodogku.libraries.navigation.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The cold-start half of deep linking: what happens to a URL that arrives
 * before the app can act on one.
 *
 * This is the case a home-screen quick action always hits. UIKit hands the
 * shortcut over while Compose is still starting, so the URL exists before the
 * nav controller has a graph, and `handleDeepLink` on a graphless controller
 * throws. A queue that buffers, paired with a consumer that waits, is what makes
 * that a handled case rather than a race.
 *
 * Covered here: holding, delivering once, ordering, the blocking-gate drop, and
 * surviving a failed open.
 *
 * Not covered here: whether a given URL matches a given route — that is
 * `AppShortcutDeepLinksTest` in `:apps:compose`, against the real generated
 * patterns — or whether the plist and manifest name the same links, which is
 * `ShortcutEntriesAgreeTest` in `:apps:integration`.
 */
class RouteDeepLinksTest {

    private val bridge = DeepLinkBridgeImpl()
    private val opened = mutableListOf<String>()

    @Test
    fun aLinkThatArrivesBeforeTheGraphExistsIsHeldRatherThanDropped() = runTest {
        bridge.emit(Daily)

        val graphExists = CompletableDeferred<Unit>()
        val routing = launch {
            bridge.routeDeepLinks(
                isBlocked = { false },
                awaitNavGraph = { graphExists.await() },
                open = ::record,
            )
        }
        runCurrent()

        assertEquals(
            emptyList(),
            opened,
            "the link was opened before there was a graph to open it on",
        )

        graphExists.complete(Unit)
        runCurrent()

        assertEquals(listOf(Daily), opened, "the held link never arrived")
        routing.cancel()
    }

    @Test
    fun aHeldLinkIsDeliveredOnceAndNotAgainToTheNextCollector() = runTest {
        bridge.emit(Daily)

        val first = launch { route() }
        runCurrent()
        assertEquals(listOf(Daily), opened)
        first.cancel()

        val second = launch { route() }
        runCurrent()

        assertEquals(
            listOf(Daily),
            opened,
            "the link was replayed to a second collector, so one tap opened it twice",
        )
        second.cancel()
    }

    @Test
    fun linksArriveInTheOrderTheyWereSent() = runTest {
        bridge.emit(Daily)
        bridge.emit(ReportBug)

        val routing = launch { route() }
        runCurrent()

        assertEquals(listOf(Daily, ReportBug), opened)
        routing.cancel()
    }

    @Test
    fun aLinkBehindABlockingGateIsDroppedRatherThanHeld() = runTest {
        bridge.emit(Daily)
        var blocking = true

        val routing = launch { route(isBlocked = { blocking }) }
        runCurrent()
        assertEquals(emptyList(), opened)

        blocking = false
        bridge.emit(ReportBug)
        runCurrent()

        assertEquals(
            listOf(ReportBug),
            opened,
            "the dropped link came back when the gate lifted, minutes after it was asked for",
        )
        routing.cancel()
    }

    @Test
    fun aLinkThatFailsToOpenDoesNotStopTheNextOne() = runTest {
        val routing = launch {
            route(open = { url ->
                record(url)
                error("the navigator threw on $url")
            })
        }
        runCurrent()

        bridge.emit(Daily)
        runCurrent()
        bridge.emit(ReportBug)
        runCurrent()

        assertEquals(listOf(Daily, ReportBug), opened)
        routing.cancel()
    }

    private suspend fun route(
        isBlocked: () -> Boolean = { false },
        open: (String) -> Boolean = ::record,
    ) = bridge.routeDeepLinks(
        isBlocked = isBlocked,
        awaitNavGraph = {},
        open = open,
    )

    private fun record(url: String): Boolean {
        opened += url
        return true
    }

    private companion object {
        const val Daily = "sodogku://game?daily=true"
        const val ReportBug = "sodogku://feedback/feedbackScreenOpens"
    }
}
