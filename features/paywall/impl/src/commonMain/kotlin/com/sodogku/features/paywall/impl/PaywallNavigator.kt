package com.sodogku.features.paywall.impl

import com.sodogku.features.paywall.OfflineBlockRoute
import com.sodogku.features.paywall.PaywallRoute
import com.sodogku.libraries.billing.PaywallCoordinator
import com.sodogku.libraries.billing.PaywallRequest
import com.sodogku.libraries.core.AutoInit
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.navigation.NavigationOptions
import com.sodogku.libraries.navigation.Router
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Turns a [PaywallRequest] into a navigation.
 *
 * This is the only place in the app that knows both that a paywall exists and
 * where it lives. `RealAdGate` asks for one from inside a library that must not
 * know about routes; this feature owns the route and does the asking-to-showing
 * translation.
 *
 * [AutoInit] is load-bearing rather than a performance choice: the coordinator's
 * bus has no replay, so a request made before this collector attaches is simply
 * lost. Boot-warming it means the subscription exists before the first ad gate
 * can fire.
 *
 * `launchSingleTop` on both because a burst — three ad gates in a row while
 * offline — must collapse into one screen rather than three copies of it on the
 * back stack.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class PaywallNavigator(
    coordinator: PaywallCoordinator,
    router: Router,
    appScope: AppCoroutineScope,
) : AutoInit {

    init {
        appScope.launch {
            coordinator.requests.collect { request ->
                when (request) {
                    is PaywallRequest.Offer -> router.navigate(
                        PaywallRoute(trigger = request.trigger.id),
                        NavigationOptions(launchSingleTop = true),
                    )

                    PaywallRequest.OfflineBlock -> router.navigate(
                        OfflineBlockRoute(),
                        NavigationOptions(launchSingleTop = true),
                    )
                }
            }
        }
    }
}
