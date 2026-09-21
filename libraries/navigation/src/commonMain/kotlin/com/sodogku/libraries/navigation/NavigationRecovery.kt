package com.sodogku.libraries.navigation

/**
 * The way out of a host lifecycle that is lying about being on screen.
 *
 * Navigation is queued and drained under the Compose host's lifecycle, which is
 * right: a command applied to a controller whose host is genuinely gone is a
 * crash or a lost back stack. The whole mechanism rests on the host telling the
 * truth about whether its view is on screen.
 *
 * `SODOGKU-R` is what it looks like when it does not. A rewarded ad went up, the
 * host dropped to CREATED as it should, the ad came down, and the host stayed at
 * CREATED. Five commands sat in the queue for four seconds while the player
 * tapped a board that had stopped responding. Nothing was broken except one
 * boolean's worth of belief.
 *
 * Separate from [Router] on purpose. [Router] is for features, and no feature
 * should ever call this: asking to bypass a lifecycle gate is not navigation, it
 * is repair, and the only caller is the watchdog that can prove the gate is
 * wrong.
 */
interface NavigationRecovery {

    /**
     * Applies everything waiting in the navigation queue, now, without waiting
     * for the host lifecycle to say it is allowed.
     *
     * **Only call this with proof that the host is wrong.** The proof this was
     * built for is a touch: a press cannot reach a view that is genuinely
     * covered, so a press landing on a host that claims to be off screen is a
     * contradiction, and the host is the half that is wrong.
     *
     * A no-op when the queue is empty or no controller is attached, so calling
     * it on a hunch costs nothing but achieves nothing either.
     */
    fun drainQueueNow()
}
