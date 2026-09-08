package com.sodogku.features.achievements

import com.sodogku.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

/**
 * The badge grid. Reached from Settings.
 *
 * A `class`, never a `data object`: an arg-less object route SIGSEGVs the iOS
 * navigator at navigate time.
 */
@Serializable
class AchievementsRoute : TrackableRoute("achievementsVisits")
