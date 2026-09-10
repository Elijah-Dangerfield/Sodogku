package com.sodogku.devfeedback

import com.sodogku.libraries.storage.Cache
import com.sodogku.libraries.storage.CacheFactory
import com.sodogku.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Where the feedback button sits, and whether it is there at all.
 *
 * Its own cache rather than two more fields on `AppData`, on the argument
 * `StreakPromptState` already makes: `AppData` is the player's blob, and this is
 * developer machinery that only exists in a tester build. A field there would
 * ride along in every player's install for a button they can never see.
 *
 * It has to be on disk at all because the button is draggable, and a position
 * that resets on every launch puts it back over the board the owner moved it off.
 */
interface DevFeedbackFabCache : Cache<DevFeedbackFabState>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DevFeedbackFabCache::class)
@Inject
class DevFeedbackFabCacheImpl(
    cacheFactory: CacheFactory,
) : DevFeedbackFabCache, Cache<DevFeedbackFabState> by cacheFactory.persistent(
    name = "dev_feedback_fab",
    serializer = versionedJsonSerializer(
        defaultValue = { DevFeedbackFabState() },
    ),
)

/**
 * [x] and [y] are fractions of the *travel*: the container minus the button, so
 * `0f` is flush left/top and `1f` is flush right/bottom.
 *
 * Fractions rather than pixels because the same install rotates, splits and runs
 * on a tablet. A stored pixel offset that was against the right edge in portrait
 * is somewhere in the middle in landscape, and one stored on a phone would be
 * lost off the edge of a smaller window.
 */
@Serializable
data class DevFeedbackFabState(
    val hidden: Boolean = false,
    val x: Float = DefaultFabPlacement.x,
    val y: Float = DefaultFabPlacement.y,
) {
    /**
     * The stored position, made safe to place.
     *
     * Coerced on the way out rather than trusted, because these two floats are
     * the only thing standing between a bad write and a button parked off screen
     * with no way to reach the toggle that would hide it. `NaN` gets the default
     * rather than a clamp, since `NaN.coerceIn` is still `NaN`.
     */
    val placement: FabPlacement
        get() = FabPlacement(
            x = if (x.isFinite()) x.coerceIn(0f, 1f) else DefaultFabPlacement.x,
            y = if (y.isFinite()) y.coerceIn(0f, 1f) else DefaultFabPlacement.y,
        )

    fun withPlacement(placement: FabPlacement): DevFeedbackFabState =
        copy(x = placement.x, y = placement.y)
}

/**
 * Right edge, low enough to clear a top bar and high enough to clear a bottom one.
 *
 * Only ever the starting guess. The point of the button is that it moves, so this
 * needs to be somewhere reachable rather than somewhere permanently correct.
 */
val DefaultFabPlacement: FabPlacement = FabPlacement(x = 1f, y = 0.62f)
