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
 *
 * **One route, two presentations, chosen by the numbers it carries.** A ceremony
 * rises over whatever the player was doing and leaves the same way, which is the
 * "slide up" the owner asked for and is the navigator's job rather than the
 * screen's — a page cannot slide up over a board that the navigator has already
 * taken away. A page the player went looking for is pushed like any other page.
 *
 * A lost run arrives the same way a won day does, and that is deliberate rather
 * than lazy. It is the same page about the same run; what differs is which
 * sentence it opens with, and giving bad news its own slower, sadder entrance
 * would be the app performing disappointment at somebody who came back.
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
     * At `0` the page arrives as an ordinary push and nothing on it moves. That
     * is the whole difference between the two ways in, and it is carried by one
     * number rather than by two routes.
     */
    val celebrating: Int = 0,

    /**
     * The run that just broke, or `0` when nothing did.
     *
     * A second number rather than a second route, on the same argument
     * [celebrating] makes: the page needs it for its headline, it is a primitive
     * arg, and the destination is the same streak page either way. It defaults,
     * so a route serialized before SD-127 still decodes.
     *
     * Never set alongside [celebrating] by anything in the app: `promptFor`
     * answers with one prompt, and a lost run and a celebration are two of them.
     */
    val lost: Int = 0,
) : TrackableRoute(
    trackingKey = "streakVisits",
    entrance = if (isCeremony(celebrating, lost)) {
        AnimationType.SlideUp
    } else {
        AnimationType.SlideInFromRight
    },
    popDeparture = if (isCeremony(celebrating, lost)) {
        AnimationType.SlideDown
    } else {
        AnimationType.SlideOutToRight
    },
    // The board underneath holds still while the ceremony travels, for the
    // reason `RouteTransitions` gives about the paywall: a page that slides out
    // to the left under something arriving from the bottom makes the still
    // screen look like the moving one. A plain visit is a push and wants the
    // ordinary pairing.
    covering = isCeremony(celebrating, lost),
)

/**
 * Whether the page was pushed at the player or opened by them.
 *
 * A function rather than a property because all three arguments above are
 * evaluated in the call to the superclass constructor, where there is nowhere to
 * put a `val`, and three copies of the same expression is three places for the
 * next argument to be forgotten.
 */
private fun isCeremony(celebrating: Int, lost: Int): Boolean = celebrating > 0 || lost > 0

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
