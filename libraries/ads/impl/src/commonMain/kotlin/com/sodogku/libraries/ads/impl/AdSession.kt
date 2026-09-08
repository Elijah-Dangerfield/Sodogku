package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.sodogku.SessionTracker
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The per-session half of the interstitial triple gate (`ads.interstitialsPerSessionMax`).
 *
 * In memory, and that is the point. A ceiling that survived a process restart
 * would mean a player who force-quits twice on a bad day never sees another
 * interstitial, and a player who leaves the app open for a week never stops
 * seeing them. [SessionTracker] already owns the definition of a session — cold
 * boot, or a foreground after fifteen minutes away — so this reads its id
 * rather than inventing a second answer.
 *
 * The id is compared lazily instead of collected: there is no work to do at a
 * session boundary except forget a number, so a collector would be a coroutine
 * that exists to set a field to zero.
 */
@SingleIn(AppScope::class)
@Inject
class AdSession(
    private val sessionTracker: SessionTracker,
) {
    private var observedSessionId: Long = 0L
    private var interstitials: Int = 0

    /** How many interstitials have been shown in the session in progress. */
    fun interstitialsShown(): Int {
        rollIfNeeded()
        return interstitials
    }


    private fun rollIfNeeded() {
        val current = sessionTracker.current.id
        if (current != observedSessionId) {
            observedSessionId = current
            interstitials = 0
        }
    }
}
