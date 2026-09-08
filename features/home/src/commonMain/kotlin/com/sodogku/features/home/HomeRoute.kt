package com.sodogku.features.home

import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

@Serializable
class HomeRoute : Route()

/**
 * The feedback page. Declared here rather than in `:features:settings`, which
 * owns the screen, only because `:features:game:impl` navigates to it by this
 * name and was locked for editing while C11 landed. Move it (and fix that one
 * import) the next time game code is open. See `docs/decisions.md`.
 */
@Serializable
class FeedbackRoute : TrackableRoute("feedbackScreenOpens")
