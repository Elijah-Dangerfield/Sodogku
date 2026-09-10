package com.sodogku.libraries.navigation.impl

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.navigation.DeepLinkBridge

/**
 * Drains [DeepLinkBridge.urls] into [open], one URL at a time, and does not
 * start on a URL until [awaitNavGraph] says the graph exists.
 *
 * That wait is the whole of this function. `NavController.handleDeepLink` reads
 * the graph on the way in and throws if `setGraph` has not run, and the App
 * composable holds the nav host back until the boot gate resolves — so on a cold
 * start the two are in a race the link loses. The failure is silent (the throw
 * is caught and logged) and it is the common case rather than the rare one,
 * because a home-screen quick action is delivered before Compose starts.
 *
 * The wait is per URL rather than once up front so [isBlocked] is still read at
 * the moment the URL arrives. A link that turns up behind a blocking launch gate
 * is dropped on purpose: there is no nav host behind a blocking gate, and a link
 * held until the gate lifted would open a screen minutes after the player asked
 * for it.
 *
 * Suspends forever, or until its caller is cancelled.
 *
 * @param isBlocked whether a blocking launch gate is currently up.
 * @param awaitNavGraph returns once the nav controller has a graph.
 * @param open hands the URL to the navigator; false means nothing matched.
 */
suspend fun DeepLinkBridge.routeDeepLinks(
    isBlocked: () -> Boolean,
    awaitNavGraph: suspend () -> Unit,
    open: (String) -> Boolean,
) {
    urls.collect { url ->
        if (isBlocked()) {
            KLog.w { "Dropping deep link behind a blocking launch gate: $url" }
            return@collect
        }
        awaitNavGraph()
        Catching { open(url) }
            .logOnFailure { "Failed to handle deep link: $url" }
            .onSuccess { matched ->
                if (!matched) KLog.w { "No destination matched the deep link: $url" }
            }
    }
}
