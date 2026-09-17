package com.sodogku.libraries.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A Route that automatically has its visits tracked when navigated to.
 *
 * The four presentation arguments are forwarded to [Route] rather than fixed,
 * because a trackable route is not automatically a route that pushes. They are
 * `@Transient` on purpose: [Route] already serializes `enter`, `exit`,
 * `popExit` and `coversParent`, so re-declaring them here as arguments would
 * add four nav arguments to every trackable destination and to the deep links
 * built from them, to carry values the navigator already has.
 *
 * @param trackingKey Unique key used to identify this route for tracking purposes.
 *                    This should match a field name in AppData.
 *                    Example: "settingsVisits", "aboutMeScreenOpens"
 */
@Serializable
open class TrackableRoute(
    val trackingKey: String,
    @Transient val entrance: AnimationType = AnimationType.SlideInFromRight,
    @Transient val departure: AnimationType = AnimationType.SlideOutToLeft,
    @Transient val popDeparture: AnimationType = AnimationType.SlideOutToRight,
    @Transient val covering: Boolean = false,
) : Route(entrance, departure, popDeparture, covering)
