@file:OptIn(ExperimentalObjCName::class)

package com.sodogku.libraries.ads

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Every AdMob identifier the app knows, in one file, deliberately.
 *
 * The ids below are **Google's published test units**. They are safe to ship in
 * a debug build, they always fill, and they are the only correct thing to
 * develop against — requesting a *live* unit from a development build is what
 * gets an AdMob account suspended for invalid traffic.
 *
 * Nothing here is in remote config. `features.md#remote-config`: an ad unit id
 * is a store operation, not a live-ops number, and a config outage that emptied
 * it would take the ads down with it.
 *
 * ## Going live
 *
 * Flip [useTestUnits] to `false` and fill in the `Live` blocks. Both are one
 * edit in one file, on purpose — the failure this guards against is a
 * half-migrated app with three real units and one test unit still in it. The
 * app id itself is *not* here: it goes in `AndroidManifest.xml`
 * (`com.google.android.gms.ads.APPLICATION_ID`) and `Info.plist`
 * (`GADApplicationIdentifier`), because both SDKs read it before any Kotlin
 * runs. `docs/OWNER-TODO.md` lists what has to be created and where each value
 * lands.
 */
@ObjCName("AdUnits", exact = true)
object AdUnits {

    /**
     * The single switch. The live units exist (2026-09-21) and are filled in
     * below; this stays `true` until the store builds go out, because a
     * development build requesting a live unit is invalid traffic and gets the
     * account suspended. Flip it in the release that ships. When it flips,
     * `Live` must be complete on **both** platforms or the placement falls back
     * to its test unit rather than silently requesting an empty string.
     */
    const val useTestUnits: Boolean = true

    /** https://developers.google.com/admob/android/test-ads — reserved sample units. */
    object AndroidTest {
        const val rewarded = "ca-app-pub-3940256099942544/5224354917"
        const val interstitial = "ca-app-pub-3940256099942544/1033173712"

        /** For the manifest, not for a request. */
        const val applicationId = "ca-app-pub-3940256099942544~3347511713"
    }

    /** https://developers.google.com/admob/ios/test-ads — reserved sample units. */
    object IosTest {
        const val rewarded = "ca-app-pub-3940256099942544/1712485313"
        const val interstitial = "ca-app-pub-3940256099942544/4411468910"

        /** For `Info.plist`'s `GADApplicationIdentifier`, not for a request. */
        const val applicationId = "ca-app-pub-3940256099942544~1458002511"
    }

    /**
     * Real Android units, created 2026-09-21 in AdMob app `~4172266958`
     * ("Sodogku (Android)"). Only requested once [useTestUnits] is off.
     */
    object AndroidLive {
        const val rewarded = "ca-app-pub-7008637445039253/5984786931"
        const val interstitial = "ca-app-pub-7008637445039253/7928423992"

        /** In `AndroidManifest.xml` already; here so the four values read together. */
        const val applicationId = "ca-app-pub-7008637445039253~4172266958"
    }

    /**
     * Real iOS units, created 2026-09-21 in AdMob app `~9668136217`
     * ("Sodogku (iOS)"). Only requested once [useTestUnits] is off.
     */
    object IosLive {
        const val rewarded = "ca-app-pub-7008637445039253/6862754337"
        const val interstitial = "ca-app-pub-7008637445039253/9485048246"

        /** In `Info.plist` already; here so the four values read together. */
        const val applicationId = "ca-app-pub-7008637445039253~9668136217"
    }

    /**
     * The unit to request on Android for [format]. Falls back to the test unit
     * when a live id is missing, because a blank unit id is an SDK error and an
     * SDK error is one more way for an ad to fail — and this app pays the player
     * when ads fail, so a typo would quietly hand out free rewards.
     */
    fun android(format: AdFormat): String = pick(
        test = when (format) {
            AdFormat.Rewarded -> AndroidTest.rewarded
            AdFormat.Interstitial -> AndroidTest.interstitial
        },
        live = when (format) {
            AdFormat.Rewarded -> AndroidLive.rewarded
            AdFormat.Interstitial -> AndroidLive.interstitial
        },
    )

    /** The unit to request on iOS for [format]. See [android]. */
    fun ios(format: AdFormat): String = pick(
        test = when (format) {
            AdFormat.Rewarded -> IosTest.rewarded
            AdFormat.Interstitial -> IosTest.interstitial
        },
        live = when (format) {
            AdFormat.Rewarded -> IosLive.rewarded
            AdFormat.Interstitial -> IosLive.interstitial
        },
    )

    private fun pick(test: String, live: String): String =
        if (useTestUnits || live.isBlank()) test else live
}
