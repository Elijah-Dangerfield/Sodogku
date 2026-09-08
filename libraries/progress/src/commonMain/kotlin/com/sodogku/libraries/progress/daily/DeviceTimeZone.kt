package com.sodogku.libraries.progress.daily

import kotlinx.datetime.TimeZone

/**
 * The zone the player's device is currently in.
 *
 * A seam rather than a `TimeZone` in the graph because the zone *changes* while
 * the app is running — a player who flies east is in a different day, and a
 * value resolved once at graph construction would keep serving them yesterday's
 * board until they force-quit.
 *
 * It is also the only reason the daily's date arithmetic is testable at all:
 * everything else takes the zone as an argument.
 *
 * Lives here rather than in `:libraries:core` because the daily is the only
 * thing that needs it. Move it up if a second caller appears.
 */
fun interface DeviceTimeZone {
    fun current(): TimeZone
}
