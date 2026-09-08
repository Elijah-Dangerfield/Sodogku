package com.sodogku.features.streak

import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

/**
 * The streak page: the run, the record, and the last five weeks.
 *
 * A `class`, never a `data object`: an arg-less object route SIGSEGVs the iOS
 * navigator at navigate time.
 */
@Serializable
class StreakRoute(
    /**
     * The streak being celebrated, or `0` for a page the player simply opened.
     *
     * An `Int` rather than a `Boolean` because the page needs the number for its
     * headline anyway, and an `Int` is a primitive route arg: no `@Serializable`
     * enum, no typeMap at the registration site, and no graph-build crash that
     * only appears on iOS.
     *
     * At `0` the page has **no** entrance animation of its own and nothing on it
     * moves. That is the whole difference between the two ways in, and it is
     * carried by one number rather than by two routes.
     */
    val celebrating: Int = 0,
) : TrackableRoute("streakVisits")

/**
 * The one-off "start your streak" moment. Full screen, and the only way out is
 * through it.
 *
 * `coversParent` and a fade rather than a push: this arrives over whatever the
 * player was doing and hands the same screen back, so nothing underneath should
 * appear to move. A slide would read as navigation, and navigation is the one
 * thing this is not, because there is no back.
 */
@Serializable
class StreakIntentionRoute : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
    coversParent = true,
)
