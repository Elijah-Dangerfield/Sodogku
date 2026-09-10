package com.sodogku.libraries.navigation

import kotlinx.serialization.Serializable

/**
 * The on-device QA menu. Debug builds only.
 *
 * Lives in `:libraries:navigation` beside `ShakeDialogRoute` rather than in a
 * feature, because the two things that open it are the shake dialog and
 * Settings, and neither of those is where it belongs either. The screen itself
 * is in `:apps:compose`, which is the only module that can reach every
 * repository it needs to poke.
 *
 * Nothing gates the *route*. What gates it is that nothing navigates here in a
 * release build: both entry points are behind `BuildInfo.isDebug`, exactly as
 * the network inspector's is. A route that exists but is unreachable costs a
 * serializer; a route guarded in three places would eventually be guarded in
 * two.
 *
 * A `class` and not a `data object` for the reason `ShakeDialogRoute` gives:
 * an arg-less `data object` route SIGSEGVs the iOS navigator at navigate time.
 */
@Serializable
class QaToolsRoute : Route(
    enter = AnimationType.SlideUp,
    exit = AnimationType.SlideDown,
    popExit = AnimationType.SlideDown,
)
