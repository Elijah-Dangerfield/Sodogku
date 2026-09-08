@file:OptIn(ExperimentalObjCName::class)

package com.sodogku.libraries.ads

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The one format the app serves.
 *
 * An enum with a single entry looks like something that wants to be deleted, and
 * it is kept deliberately: the SDK request shape is per-format, the Swift side
 * names it, and a later decision to serve a second format should be an entry
 * here rather than a new parameter threaded through the network.
 *
 * It had four. Interstitial, AppOpen and Banner were all reachable from the
 * network and unreachable from the game: nothing mapped a placement onto them,
 * so between them they had produced no impressions. The policy is that every ad
 * is asked for, and those three are the ones that cannot be.
 */
@ObjCName("AdFormat", exact = true)
enum class AdFormat {
    Rewarded,
}

/**
 * What the SDK did, flattened to something an ObjC-facing enum can carry.
 *
 * Deliberately *not* [RewardOutcome]: the Swift side has to be able to build a
 * return value, and a sealed hierarchy of Kotlin data objects is awkward to
 * construct from Swift. [RealAdGate][com.sodogku.libraries.ads.impl.RealAdGate]
 * widens this back into the richer sealed types, which is also the one place
 * the fail-open mapping lives.
 */
@ObjCName("AdShowResult", exact = true)
enum class AdShowResult {
    /** Watched to the reward threshold. */
    Rewarded,

    /** Closed early, by the player, on purpose. The only result that withholds. */
    Dismissed,

    /** The network had nothing to serve. */
    NoFill,

    /** The SDK reported no route to the network. */
    Offline,

    /** Nothing was attempted — the SDK is not initialised, or has no ad loaded. */
    NotShown,

    /** Anything else. [AdShowOutcome.errorKind] carries the detail, for telemetry only. */
    Failed,
}

/**
 * A result plus an optional machine-readable failure kind. Never a message for
 * the player — an ad failure is invisible to them by design.
 */
@ObjCName("AdShowOutcome", exact = true)
class AdShowOutcome(
    val result: AdShowResult,
    val errorKind: String? = null,
)

/**
 * The thin platform seam under [AdGate]: load an ad, show it, say what happened.
 *
 * Everything policy-shaped — the frequency gates, the new-user grace, the
 * offline grace, whether a placement is enabled, and the rule that a failure
 * still pays the player — lives above this in
 * [RealAdGate][com.sodogku.libraries.ads.impl.RealAdGate], in common code, so it
 * is the same on both platforms and testable without an ad network.
 *
 * **Android binding** is `AdMobAdNetwork` in `:libraries:ads:impl/androidMain`.
 * **iOS binding** is `IOSAdNetwork` (Swift, wrapping GoogleMobileAds) handed to
 * the graph through `IosAppComponent`, the same route `ReviewLauncher` takes.
 *
 * Implementations must not throw. An ad SDK that blows up has to look like
 * [AdShowResult.Failed] from here, because the layer above turns that into a
 * granted reward.
 */
@ObjCName("AdNetwork", exact = true)
interface AdNetwork {

    /**
     * Consent first, then SDK init. On iOS that is UMP **then** ATT **then**
     * `MobileAds.start`; on Android UMP then `MobileAds.initialize`. Both orders
     * are a store requirement rather than a preference — an ad request that
     * beats the consent form is a policy violation, so every show path awaits
     * this before it touches the SDK.
     *
     * Idempotent, and cheap after the first call.
     */
    suspend fun prepare()

    /**
     * Shows an already-loaded ad, or loads one first. Suspends until it closes.
     * The implementation resolves the ad unit itself from [AdUnits], so there is
     * exactly one file to edit when the real ids arrive.
     */
    suspend fun show(format: AdFormat): AdShowOutcome

    /** Warms a format. Fire and forget; failures are the SDK's problem, not the caller's. */
    fun preload(format: AdFormat)
}
