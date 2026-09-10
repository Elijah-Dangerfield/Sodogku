package com.sodogku

import androidx.navigation.NavDeepLink
import com.sodogku.features.game.GameRoute
import com.sodogku.features.home.FeedbackRoute
import com.sodogku.libraries.navigation.AppShortcuts
import com.sodogku.libraries.navigation.routeDeepLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The URLs the home-screen long-press entries carry, held against the deep-link
 * patterns the routes actually generate.
 *
 * The two halves are written in different files at different times — the URL is
 * a constant a plist and a manifest quote, the pattern is derived by androidx
 * from a route's serialized properties — and nothing at compile time connects
 * them. Give `GameRoute` an argument without a default and its pattern grows a
 * **required path segment**; the shortcut URL no longer has the right shape,
 * matches nothing, and the only symptom is a menu row that opens the app on
 * whatever it was already showing.
 *
 * The pattern is read from the real `NavDeepLink` rather than written down
 * here, so an argument added to a route reaches this test on its own. What is
 * written down here is the *shape rule* androidx applies — a placeholder in the
 * path must be filled, a placeholder in the query need not be — because
 * `NavDeepLink`'s own matcher goes through `android.net.Uri`, which is a stub
 * on the host JVM these tests run on. [theShapeRuleWouldNoticeAWrongUrl] is
 * what stops that rule quietly accepting everything.
 *
 * Not covered here: whether the plist and the Android manifest carry these URLs
 * and types at all, which is `ShortcutEntriesAgreeTest` in `:apps:integration`.
 */
class AppShortcutDeepLinksTest {

    private val gameLink = routeDeepLink<GameRoute>(basePath = AppShortcuts.GameBasePath)
    private val feedbackLink = routeDeepLink<FeedbackRoute>(basePath = AppShortcuts.FeedbackBasePath)

    @Test
    fun theDailyEntryFitsTheGameRoute() {
        assertTrue(
            gameLink.fits(AppShortcuts.DailyChallengeUrl),
            "${AppShortcuts.DailyChallengeUrl} does not fit ${gameLink.uriPattern}",
        )
    }

    @Test
    fun theDailyEntryAsksForTheDailyPack() {
        assertEquals(
            mapOf("daily" to "true"),
            query(AppShortcuts.DailyChallengeUrl),
            "the daily entry opens the campaign pack",
        )
    }

    @Test
    fun theDailyEntryNamesNoLevel() {
        // A level id written into a plist is fixed for the life of the install
        // and the daily board changes at local midnight. `loadDaily` resolves
        // the board from `DailyRepository.status()` and ignores whatever the
        // route carried; the link carries nothing, so there is nothing to ignore.
        assertFalse(
            "levelId" in query(AppShortcuts.DailyChallengeUrl),
            "the daily entry pins a level id, which is yesterday's board tomorrow",
        )
    }

    @Test
    fun theReportBugEntryFitsTheFeedbackRoute() {
        assertTrue(
            feedbackLink.fits(AppShortcuts.ReportBugUrl),
            "${AppShortcuts.ReportBugUrl} does not fit ${feedbackLink.uriPattern}",
        )
    }

    @Test
    fun theReportBugEntryCannotDropItsTrailingSegment() {
        // The segment is `TrackableRoute.trackingKey`, which has no default and
        // is therefore a required path argument. This asserts the awkward URL is
        // load bearing, so nobody tidies it away and finds out on a phone.
        assertFalse(
            feedbackLink.fits(AppShortcuts.FeedbackBasePath),
            "the trailing segment is optional after all, so the URL should lose it",
        )
    }

    @Test
    fun neitherEntryFitsTheOtherRoute() {
        assertFalse(gameLink.fits(AppShortcuts.ReportBugUrl), "report a bug opens a puzzle")
        assertFalse(feedbackLink.fits(AppShortcuts.DailyChallengeUrl), "the daily opens feedback")
    }

    @Test
    fun theShapeRuleWouldNoticeAWrongUrl() {
        // The guard against the guard. Everything above is an assertion about a
        // rule written in this file, so the rule has to be shown to say no.
        assertFalse(gameLink.fits("sodogku://not-a-thing"), "a different host fits")
        assertFalse(gameLink.fits("https://sodogku.com/game"), "a different scheme fits")
        assertFalse(gameLink.fits("sodogku://game/7"), "a spare path segment fits")
        assertFalse(gameLink.fits("sodogku://game?nonsense=1"), "an argument the route has no idea about fits")
        assertFalse(feedbackLink.fits("sodogku://feedback/a/b"), "two segments fit a one-segment pattern")

        // And that the pattern being read is the real one rather than an empty
        // string, which every rule above would accept.
        assertTrue(
            "{daily}" in (gameLink.uriPattern ?: ""),
            "the game pattern parsed as '${gameLink.uriPattern}', which names no arguments",
        )
    }

    /**
     * Whether [url] has the shape [NavDeepLink] requires: the same path, with
     * every `{placeholder}` segment filled by something, and no query argument
     * the pattern does not declare. Query placeholders are all optional here,
     * because androidx only puts an argument in the query when it has a default.
     */
    private fun NavDeepLink.fits(url: String): Boolean {
        val pattern = uriPattern ?: return false
        if (path(pattern).size != path(url).size) return false

        val pathMatches = path(pattern).zip(path(url)).all { (expected, actual) ->
            if (expected.startsWith("{")) actual.isNotEmpty() else expected == actual
        }
        if (!pathMatches) return false

        return query(url).keys.all { it in query(pattern).keys }
    }

    /** Scheme, host and path segments, with the query dropped. */
    private fun path(url: String): List<String> =
        url.substringBefore('?')
            .split("://", "/")
            .filter { it.isNotEmpty() }

    private fun query(url: String): Map<String, String> =
        url.substringAfter('?', "")
            .split("&")
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
}
