package com.sodogku.features.game

import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * One puzzle.
 *
 * A `data class` with defaults, never a `data object`: an arg-less object route
 * SIGSEGVs at navigate time on iOS inside androidx.navigation's serialization.
 */
@Serializable
data class GameRoute(
    /**
     * The level's id **within the pack [daily] names**. The two packs share a
     * number line — daily level 7 is not campaign level 7 — so an id on its own
     * does not identify a board.
     */
    val levelId: Int = 1,
    /**
     * Which pack [levelId] belongs to.
     *
     * A `Boolean` rather than a `PackKind` enum on purpose. A non-primitive route
     * arg has to be `@Serializable` *and* registered in a typeMap at the
     * registration site, and the failure is a graph-build crash on iOS that no
     * JVM test can see. Two packs do not justify that risk.
     */
    val daily: Boolean = false,
) : Route(
    enter = AnimationType.SlideInFromRight,
    exit = AnimationType.SlideOutToLeft,
    popExit = AnimationType.SlideOutToRight,
)
