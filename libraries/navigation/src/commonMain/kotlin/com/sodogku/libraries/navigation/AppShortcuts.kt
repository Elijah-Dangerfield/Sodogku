@file:OptIn(ExperimentalObjCName::class)

package com.sodogku.libraries.navigation

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The rows we own in the home-screen long-press menu, on both platforms.
 *
 * Delete App, Share App, Edit Home Screen and Require Face ID are the system's
 * and cannot be removed, reordered or renamed, and nothing can sit in front of
 * a delete. What is available is a couple of entries above them, so the list is
 * kept to two: the menu is somewhere people go to delete an app, and a longer
 * list there reads as a pitch from something they are already annoyed with.
 *
 * *Report a bug* sits second on purpose. It is the row directly above Delete
 * App, which is where a finger heading for the delete passes on its way.
 *
 * Each platform declares the entries itself, because both are read before any
 * of our code runs — `UIApplicationShortcutItems` in `apps/ios/iosApp/Info.plist`
 * and `android.app.shortcuts` in `apps/compose/src/androidMain/res/xml/shortcuts.xml`.
 * What they share is what is here: iOS carries [DailyChallengeType] /
 * [ReportBugType] and asks [urlFor] for the link, Android carries the link
 * directly. `ShortcutEntriesAgreeTest` in `:apps:integration` reads all three
 * files and fails when they drift, which is the only thing standing between a
 * renamed constant and a menu row that silently does nothing.
 *
 * The URLs below spell their base path out rather than interpolating it,
 * because that test reads this file as text and an interpolated constant would
 * arrive as the literal `$GameBasePath?daily=true`. `AppShortcutDeepLinksTest`
 * is what holds each URL against the pattern its base path generates.
 */
@ObjCName("AppShortcuts", exact = true)
object AppShortcuts {

    /** The `UIApplicationShortcutItemType` of the daily entry. */
    const val DailyChallengeType: String = "com.sodogku.shortcut.daily-challenge"

    /** The `UIApplicationShortcutItemType` of the report-a-bug entry. */
    const val ReportBugType: String = "com.sodogku.shortcut.report-bug"

    /** What `routeDeepLink<GameRoute>` is registered against. */
    const val GameBasePath: String = "sodogku://game"

    /** What `routeDeepLink<FeedbackRoute>` is registered against. */
    const val FeedbackBasePath: String = "sodogku://feedback"

    /**
     * Today's daily.
     *
     * No level id, and that is not an omission. `GameViewModel.loadDaily` reads
     * the board out of `DailyRepository.status()` and ignores the id the route
     * carried, precisely so a screen opened either side of local midnight
     * cannot record yesterday's board against today. A shortcut is the extreme
     * case of a stale id — the plist is written months before it is tapped — so
     * it passes none, and the default `levelId` on the route goes unread.
     */
    const val DailyChallengeUrl: String = "sodogku://game?daily=true"

    /**
     * The feedback panel.
     *
     * The trailing segment is `TrackableRoute.trackingKey`, which
     * `FeedbackRoute` fixes at `feedbackScreenOpens`. It is in the URL because
     * androidx builds a route's deep-link pattern from its serialized
     * properties and makes every one without a default a **required path
     * segment** — `trackingKey` has no default, so the pattern is
     * `sodogku://feedback/{trackingKey}` and a link that leaves it out matches
     * nothing. `AppShortcutDeepLinksTest` holds this string against the pattern
     * the route actually generates.
     */
    const val ReportBugUrl: String = "sodogku://feedback/feedbackScreenOpens"

    /**
     * The deep link a tapped entry stands for, or null for a type we do not
     * know — an entry left in the plist after its route was removed, or a
     * constant renamed on one side only.
     */
    fun urlFor(type: String): String? = when (type) {
        DailyChallengeType -> DailyChallengeUrl
        ReportBugType -> ReportBugUrl
        else -> null
    }
}
