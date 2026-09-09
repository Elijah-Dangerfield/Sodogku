package com.sodogku.libraries.navigation

import kotlinx.serialization.Serializable

/**
 * Shake to report a problem.
 *
 * Carries nothing. It used to pass a `headline` and `subtext` chosen at random
 * from a few hundred generated quips, so the route existed to ferry copy from a
 * message provider into a dialog. The dialog owns its own words now, which is
 * where a screen's copy belongs.
 *
 * A `class` and not a `data object` even though it has no arguments: an
 * arg-less `data object` route SIGSEGVs the iOS navigator at navigate time.
 * This was the last one in the repo.
 */
@Serializable
class ShakeDialogRoute : Route(
    enter = AnimationType.SlideUp,
    exit = AnimationType.SlideDown,
    popExit = AnimationType.SlideDown,
)
