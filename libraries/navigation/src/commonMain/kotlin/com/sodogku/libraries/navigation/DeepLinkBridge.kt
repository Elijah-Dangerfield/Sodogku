@file:OptIn(ExperimentalObjCName::class)

package com.sodogku.libraries.navigation

import kotlinx.coroutines.flow.Flow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Pipe for incoming deep-link URLs from the host platform into Compose
 * Navigation. Each platform pushes URLs in via [emit]; the app collects
 * [urls] and routes them with `NavController.handleDeepLink`.
 *
 * Why a bridge instead of direct calls: the platform entry points (iOS
 * `.onOpenURL` and the quick-action delegates, Android `onNewIntent`) fire
 * outside Compose, and the earliest of them fire before there is a nav
 * controller at all. The bridge decouples them from the NavController, which
 * only exists for the lifetime of the App composable.
 *
 * Android note: Compose NavHost reads `Activity.intent.data` automatically
 * when the Activity has the right intent filter, so a **cold** launch on
 * Android needs no emit. A warm one does — `MainActivity.onNewIntent` forwards
 * here. iOS has no automatic path at all.
 *
 * Per-route registration uses the `deepLinks` parameter on `screen<Route>`:
 *
 * ```
 * screen<ProfileRoute>(
 *     deepLinks = listOf(routeDeepLink<ProfileRoute>(basePath = "https://example.com/profile"))
 * ) { ... }
 * ```
 *
 * [urls] hands each URL to exactly one collector, and holds anything that
 * arrives while nobody is listening. Both halves matter: a quick action tapped
 * on a cold start reaches [emit] before Compose has composed, and a replaying
 * stream would re-open the last link every time the collector restarted.
 */
@ObjCName("DeepLinkBridge", exact = true)
interface DeepLinkBridge {
    val urls: Flow<String>
    fun emit(url: String)
}
