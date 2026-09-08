package com.sodogku.libraries.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.toRoute
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


@Serializable
enum class AnimationType {
    None,
    FadeIn,
    FadeOut,

    SlideInFromRight,
    SlideInFromLeft,

    SlideOutToRight,
    SlideOutToLeft,

    SlideUp,
    SlideDown,
    ExpandIn,
    ShrinkOut;

    fun opposite(): AnimationType {
        return when (this) {
            None -> None
            FadeIn -> FadeOut
            FadeOut -> FadeIn
            SlideOutToRight -> SlideInFromLeft
            SlideOutToLeft -> SlideInFromRight
            SlideInFromRight -> SlideOutToLeft
            SlideInFromLeft -> SlideOutToRight
            SlideUp -> SlideDown
            SlideDown -> SlideUp
            ExpandIn -> ShrinkOut
            ShrinkOut -> ExpandIn
        }
    }
}

fun AnimationType.toEnterTransition(): EnterTransition = when (this) {
    AnimationType.None -> fadeIn(tween(0))
    AnimationType.FadeIn -> fadeIn()
    AnimationType.SlideInFromLeft -> slideInHorizontally { -it }
    AnimationType.SlideInFromRight -> slideInHorizontally { it }
    AnimationType.SlideOutToRight -> slideInHorizontally { it }
    AnimationType.SlideOutToLeft -> slideInHorizontally { -it }
    AnimationType.SlideUp -> slideInVertically { it }
    AnimationType.SlideDown -> slideInVertically { -it }
    AnimationType.ExpandIn -> expandIn { it }
    AnimationType.ShrinkOut -> fadeIn(tween(0))
    AnimationType.FadeOut -> fadeIn(tween(0))
}

fun AnimationType.toExitTransition(): ExitTransition = when (this) {
    AnimationType.None -> fadeOut(tween(0))
    AnimationType.FadeOut -> fadeOut()
    AnimationType.SlideInFromLeft -> slideOutHorizontally { -it }
    AnimationType.SlideInFromRight -> slideOutHorizontally { it }
    AnimationType.SlideOutToRight -> slideOutHorizontally { it }
    AnimationType.SlideOutToLeft -> slideOutHorizontally { -it }
    AnimationType.SlideUp -> slideOutVertically { it }
    AnimationType.SlideDown -> slideOutVertically { -it }
    AnimationType.ShrinkOut -> shrinkOut { it }
    AnimationType.ExpandIn -> fadeOut(tween(0))
    AnimationType.FadeIn -> fadeOut(tween(0))
}

@Serializable
open class Route(
    val enter: AnimationType = AnimationType.SlideInFromRight,
    val exit: AnimationType = AnimationType.SlideOutToLeft,
    val popExit: AnimationType = AnimationType.SlideOutToRight,
    /**
     * Whether this route *covers* the screen beneath it rather than replacing it.
     *
     * The three animations above only ever describe the route's own movement.
     * What the screen underneath does is derived: it plays its own `exit` on the
     * way in, and the opposite of this route's `popExit` on the way back. For a
     * push that reads correctly, because the two screens are travelling together
     * and only one of them should be on screen at the end.
     *
     * For something that slides up over what you were doing it reads wrong, and
     * the paywall was the proof. Opening Pro from Settings slid Pro up *and*
     * slid Settings out to the left, and closing it slid Pro down while sliding
     * Settings back up from the bottom, so the thing that appeared to move was
     * the screen that should have been sitting still the whole time.
     *
     * Set this and the screen beneath does nothing at all: the overlay arrives
     * over a still page and leaves the same page behind.
     */
    val coversParent: Boolean = false,
)  {
    fun getEnterTransition(): EnterTransition = enter.toEnterTransition()
    fun getExitTransition(): ExitTransition = exit.toExitTransition()

    fun getPopExitTransition(): ExitTransition = popExit.toExitTransition()

    companion object
}
inline fun <reified T> NavBackStackEntry.toRouteOrNull(): T? = Catching<T> { toRoute(T::class) }
    .logOnFailure()
    .getOrNull()


inline fun <reified T : Any> serializableType(
    isNullableAllowed: Boolean = false,
    json: Json = Json,
) = object : NavType<T>(isNullableAllowed = isNullableAllowed) {

    override fun put(bundle: SavedState, key: String, value: T) {
        bundle.write { putString(key, json.encodeToString(value)) }
    }

    override fun get(bundle: SavedState, key: String): T? {
        return json.decodeFromString<T?>(bundle.read { getString(key) })
    }

    override fun parseValue(value: String): T = json.decodeFromString(value)

    override fun serializeAsValue(value: T): String = json.encodeToString(value)
}