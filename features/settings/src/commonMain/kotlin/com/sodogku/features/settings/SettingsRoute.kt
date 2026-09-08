package com.sodogku.features.settings

import com.sodogku.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

/**
 * The app's settings page. Reachable from the board, and the only place that
 * owns the player's toggles, the legal links and the version string.
 *
 * A `class`, never a `data object`: an arg-less object route SIGSEGVs the iOS
 * navigator at navigate time.
 */
@Serializable
class SettingsRoute : TrackableRoute("settingsVisits")
