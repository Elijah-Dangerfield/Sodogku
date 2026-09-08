package com.sodogku.libraries.progress.impl

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.storage.Cache
import com.sodogku.libraries.storage.CacheFactory
import com.sodogku.libraries.storage.versionedJsonSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Today's skip allowance, on disk.
 *
 * Its own cache rather than two more fields in `AppData`, for the reason the ad
 * bookkeeping has its own: these two numbers are machinery, and neither means
 * anything without `progression.skipsPerDay` beside it.
 *
 * It has to survive a force-quit — the whole point of a daily cap is that
 * closing the app is not a way around it — so it is persistent rather than
 * per-session.
 */
@Serializable
data class SkipState(
    /**
     * The local date the allowance was last rolled to, ISO-8601. Empty until
     * the player's first skip.
     */
    val day: String = "",

    /** Skips taken on [day]. */
    val used: Int = 0,
) {
    /**
     * The allowance as it stands on [today], and the whole of what a moved clock
     * can and cannot do to it.
     *
     * **The recorded day only ever moves forward.** A date earlier than the one
     * on record does not roll the counter over, so a clock wound back cannot
     * restore a skip that has been spent — which is the manipulation worth
     * defending against, because it is the free one.
     *
     * Winding the clock *forward* does hand out a fresh allowance, and it is
     * allowed to, because refusing a future date means trusting the same clock
     * we already do not trust — see the daily's entry in `decisions.md`. What it
     * costs is the point: the recorded day jumps with it, so the player then
     * gets nothing until the real calendar catches up. One day's skips bought
     * with every day until then.
     *
     * The daily challenge deliberately keeps no such high-water mark, and the
     * asymmetry is deliberate too. There, a device whose clock shipped wrong and
     * later corrected itself would be locked out of the feature entirely; here
     * the same device loses free skips for a while and can still play every
     * level in the game.
     *
     * Flying west repeats a date and correctly grants nothing new. Flying east
     * skips one and correctly rolls over.
     */
    fun on(today: LocalDate): SkipState {
        val recorded = Catching { LocalDate.parse(day) }.getOrNull()
        return if (recorded == null || today > recorded) SkipState(today.toString(), used = 0) else this
    }

    fun spent(today: LocalDate): SkipState = on(today).let { it.copy(used = it.used + 1) }
}

interface SkipStateCache : Cache<SkipState>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = SkipStateCache::class)
@Inject
class SkipStateCacheImpl(
    cacheFactory: CacheFactory,
) : SkipStateCache, Cache<SkipState> by cacheFactory.persistent(
    name = "skip_state",
    serializer = versionedJsonSerializer(
        defaultValue = { SkipState() },
    ),
)
