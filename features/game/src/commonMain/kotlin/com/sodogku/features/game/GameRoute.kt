package com.sodogku.features.game

import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * One puzzle. [levelId] indexes into the campaign pack.
 *
 * A `data class` with a default, never a `data object`: an arg-less object route
 * SIGSEGVs at navigate time on iOS inside androidx.navigation's serialization.
 */
@Serializable
data class GameRoute(
    val levelId: Int = 1,
) : Route(
    enter = AnimationType.SlideInFromRight,
    exit = AnimationType.SlideOutToLeft,
    popExit = AnimationType.SlideOutToRight,
)
