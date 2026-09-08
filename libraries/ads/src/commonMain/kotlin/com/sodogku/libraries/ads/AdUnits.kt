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
 * Nothing here is in remote config. SPEC 4.4: an ad unit id is a store
 * operation, not a live-ops number, and a config outage that emptied it would
 * take the ads down with it.
 *
 * ## Going live
 *
 * Flip [useTestUnits] to `false` and fill in the `Live` blocks. Both are
 * one edit in one file, on purpose — the failure this guards against is a
 * half-migrated app with three real units and one test unit still in it.
 * The app id itself is *not* here: it goes in `AndroidManifest.xml`
 * (`com.google.android.gms.ads.APPLICATION_ID`) and `Info.plist`
 * (`GADApplicationIdentifier`), because both SDKs read it before any Kotlin
 * runs. `docs/SPEC.md` §20 lists what has to be created and where each value
 * lands.
 */
@ObjCName("AdUnits", exact = true)
object AdUnits {

    /**
     * The single switch. Left `true` until real units exist; when it flips,
     * `Live` must be complete on **both** platforms or the placement falls back
     * to its test unit rather than silently requesting an empty string.
     */
    const val useTestUnits: Boolean = true

    /** https://developers.google.com/admob/android/test-ads — reserved sample units. */
    object AndroidTest {
        const val interstitial = "ca-app-pub-3940256099942544/1033173712"
        const val rewarded = "ca-app-pub-3940256099942544/5224354917"
        const val appOpen = "ca-app-pub-3940256099942544/9257395921"
        const val banner = "ca-app-pub-3940256099942544/6300978111"

        /** For the manifest, not for a request. */
        const val applicationId = "ca-app-pub-3940256099942544~3347511713"
    }

    /** https://developers.google.com/admob/ios/test-ads — reserved sample units. */
    object IosTest {
        const val interstitial = "ca-app-pub-3940256099942544/4411468910"
        const val rewarded = "ca-app-pub-3940256099942544/1712485313"
        const val appOpen = "ca-app-pub-3940256099942544/5575463023"
        const val banner = "ca-app-pub-3940256099942544/2934735716"

        /** For `Info.plist`'s `GADApplicationIdentifier`, not for a request. */
        const val applicationId = "ca-app-pub-3940256099942544~1458002511"
    }

    /** Real Android units. Empty until the AdMob app exists — see [useTestUnits]. */
    object AndroidLive {
        const val interstitial = ""
        const val rewarded = ""
        const val appOpen = ""
        const val banner = ""
    }

    /** Real iOS units. Empty until the AdMob app exists — see [useTestUnits]. */
    object IosLive {
        const val interstitial = ""
        const val rewarded = ""
        const val appOpen = ""
        const val banner = ""
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
            AdFormat.AppOpen -> AndroidTest.appOpen
            AdFormat.Banner -> AndroidTest.banner
        },
        live = when (format) {
            AdFormat.Rewarded -> AndroidLive.rewarded
            AdFormat.Interstitial -> AndroidLive.interstitial
            AdFormat.AppOpen -> AndroidLive.appOpen
            AdFormat.Banner -> AndroidLive.banner
        },
    )

    /** The unit to request on iOS for [format]. See [android]. */
    fun ios(format: AdFormat): String = pick(
        test = when (format) {
            AdFormat.Rewarded -> IosTest.rewarded
            AdFormat.Interstitial -> IosTest.interstitial
            AdFormat.AppOpen -> IosTest.appOpen
            AdFormat.Banner -> IosTest.banner
        },
        live = when (format) {
            AdFormat.Rewarded -> IosLive.rewarded
            AdFormat.Interstitial -> IosLive.interstitial
            AdFormat.AppOpen -> IosLive.appOpen
            AdFormat.Banner -> IosLive.banner
        },
    )

    private fun pick(test: String, live: String): String =
        if (useTestUnits || live.isBlank()) test else live
}
