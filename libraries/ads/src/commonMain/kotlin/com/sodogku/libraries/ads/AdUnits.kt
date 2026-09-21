@file:OptIn(ExperimentalObjCName::class)

package com.sodogku.libraries.ads

import com.sodogku.libraries.core.BuildInfo
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
 * There is nothing to flip. [useTestUnits] is derived from the release channel,
 * so the live units below are requested by the binaries that go to the public
 * tracks and by nothing else. Fill in the `Live` blocks and that is the whole
 * migration. The app id itself is *not* here: it goes in `AndroidManifest.xml`
 * (`com.google.android.gms.ads.APPLICATION_ID`) and `Info.plist`
 * (`GADApplicationIdentifier`), because both SDKs read it before any Kotlin
 * runs. `docs/OWNER-TODO.md` lists what has to be created and where each value
 * lands.
 */
@ObjCName("AdUnits", exact = true)
object AdUnits {

    /**
     * The release channel that is allowed to request live units.
     *
     * Set by `RELEASE_CHANNEL_OVERRIDE` in `.github/workflows/release.yml` and
     * read back through `BuildInfo.releaseChannel`. The other two values it
     * takes are `dev`, the local default in `versions.properties`, and `beta`,
     * which `beta.yml` uses for TestFlight and Play internal.
     */
    private const val STORE_CHANNEL = "store"

    /**
     * True for every build except the ones going to the public store tracks.
     *
     * Derived rather than hand-flipped, because the thing this guards against
     * is someone forgetting. A build that is not headed for the store must not
     * request a live unit: those impressions are invalid traffic and they get
     * AdMob accounts suspended.
     *
     * **`BuildInfo.isDebug` is the wrong question**, which is worth stating
     * because it is the obvious one. TestFlight and Play internal ship *release*
     * binaries, so `isDebug` is false in exactly the case that matters most —
     * a tester watching a rewarded ad is the likeliest source of bad
     * impressions this app has. The release channel separates them; the build
     * type does not.
     *
     * One case this does not cover, named rather than solved: the first Play
     * upload is a `store` build routed to the internal track, because Play will
     * not take a production release until an approved one exists. That single
     * binary carries live units in front of internal testers. See
     * `docs/release-checklist.md`.
     *
     * `Live` must be complete on **both** platforms before a store build goes
     * out, or the placement falls back to its test unit rather than silently
     * requesting an empty string. [pick] does that on purpose.
     */
    val useTestUnits: Boolean get() = BuildInfo.releaseChannel != STORE_CHANNEL

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
