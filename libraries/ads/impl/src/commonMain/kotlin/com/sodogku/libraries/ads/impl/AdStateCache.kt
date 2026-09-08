package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.storage.Cache
import com.sodogku.libraries.storage.CacheFactory
import com.sodogku.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The ad-frequency bookkeeping that has to survive a force-quit.
 *
 * A separate cache from `AppData` on purpose. `AppData` is settings and
 * counters a person could recognise — haptics, held boosters, onboarding — and
 * this is machinery: five numbers nobody would ever want to read, three of
 * which are meaningless without the config values they are compared against.
 * Keeping them apart also means the ad layer can be reasoned about (and
 * force-reset in QA) without touching the file the whole app writes to.
 *
 * The **entitlement** does live in `AppData`, per SPEC 5.2 — that one is a fact
 * about the player, not machinery.
 *
 * Per-*session* counters are deliberately not here: an interstitial ceiling
 * that survived a restart would let a player who force-quit twice never see one
 * again. Those live in [AdSession], in memory.
 */
@Serializable
data class AdState(
    /**
     * Epoch-ms of the first launch we ever saw. The wall-clock leg of the
     * new-user grace counts from here rather than from the current session's
     * start, so closing the app during the first five minutes does not hand
     * the player another five.
     */
    val firstSeenAtMs: Long = 0L,

    /** Epoch-ms of the last interstitial actually shown. 0 = never. */

    /** Levels finished since the last interstitial, for `ads.interstitialEveryNLevels`. */

    /**
     * Epoch-ms of the **first ad gate that could not be served offline**, which
     * is where SPEC 6 counts the grace from — not from going offline. 0 = the
     * grace has not started.
     */
    val offlineGraceStartedAtMs: Long = 0L,

    /** Levels finished on the offline grace so far. */
    val offlineGraceLevelsSpent: Int = 0,
) {
    /** Both legs cleared. Called after a successful ad view, never on reconnect. */
    fun withOfflineGraceReset(): AdState = copy(
        offlineGraceStartedAtMs = 0L,
        offlineGraceLevelsSpent = 0,
    )
}

interface AdStateCache : Cache<AdState>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AdStateCache::class)
@Inject
class AdStateCacheImpl(
    cacheFactory: CacheFactory,
) : AdStateCache, Cache<AdState> by cacheFactory.persistent(
    name = "ad_state",
    serializer = versionedJsonSerializer(
        defaultValue = { AdState() },
    ),
)
