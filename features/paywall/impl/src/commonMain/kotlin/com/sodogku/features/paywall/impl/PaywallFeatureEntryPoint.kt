package com.sodogku.features.paywall.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.sodogku.features.paywall.OfflineBlockRoute
import com.sodogku.features.paywall.PaywallRoute
import com.sodogku.features.paywall.ProCelebrationRoute
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.flowroutines.ObserveEvents
import com.sodogku.libraries.navigation.FeatureEntryPoint
import com.sodogku.libraries.navigation.NavigationOptions
import com.sodogku.libraries.navigation.Router
import com.sodogku.libraries.navigation.bottomSheet
import com.sodogku.libraries.navigation.screen
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class PaywallFeatureEntryPoint(
    private val paywallViewModelFactory: (trigger: String, dwellSeconds: Int) -> PaywallViewModel,
    private val offlineBlockViewModelFactory: () -> OfflineBlockViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        // A sheet, not a screen. It is an interruption of whatever the player
        // was doing and it should read as one that goes away — registered as a
        // `screen<>` it slid up on the way in and kept going up on the way out,
        // which is what the player saw and reported.
        //
        // Both events close through `sheetState.dismiss()` rather than
        // `router.goBack()`: the sheet animates down first and the pop happens
        // in `onDismissed`, once it has landed. Popping first would delete the
        // destination out from under a sheet that had not moved yet.
        bottomSheet<PaywallRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<PaywallRoute>()
            val viewModel: PaywallViewModel = viewModel {
                paywallViewModelFactory(route.trigger, route.dwellSeconds)
            }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            // Remembered rather than navigated to from the event handler,
            // because the sheet has to land before anything else moves: a
            // navigate issued while it is still sliding down deletes the
            // destination out from under it. So the event records what should
            // happen next and `onDismissed`, which runs once the sheet is
            // actually down, does it.
            var celebrateOnClose by remember { mutableStateOf(false) }

            viewModel.ObserveEvents { event ->
                when (event) {
                    PaywallEvent.Dismiss -> sheetState.dismiss()
                    // A purchase made here earns the celebration page. It used
                    // to close onto the board saying nothing, on the reasoning
                    // that an interstitial after a purchase is one more thing in
                    // the way. A payment with no visible response is the shape
                    // of a payment that failed, which is worse. A restore still
                    // closes quietly: nothing was bought.
                    is PaywallEvent.Purchased -> {
                        celebrateOnClose = event.celebrate
                        sheetState.dismiss()
                    }
                }
            }

            PaywallScreen(
                state = state,
                sheetState = sheetState,
                onDismissed = {
                    router.goBack()
                    if (celebrateOnClose) router.navigate(ProCelebrationRoute())
                },
                onAction = viewModel::takeAction,
                standInNote = route.standInNote,
                // The dwell is what only a stand-in has, so it is what
                // identifies one. Reading it from the route rather than from
                // state keeps the explanation up after the countdown ends.
                isStandIn = route.dwellSeconds > 0,
            )
        }

        screen<ProCelebrationRoute> {
            ProCelebrationScreen(onDone = { router.goBack() })
        }

        // Deliberately still a `screen<>`. The offline block is the one thing
        // in this app that stops a player (`features.md#offline`): it swallows
        // back, it has no dismiss control of its own, and it leaves only when
        // the network comes back or the player buys Pro. A bottom sheet is the
        // wrong shape for that in three separate ways — a scrim you can tap, a
        // drag you can swipe, and a page visible underneath that the block is
        // there to stop you reaching. Being a sheet would make it dismissible,
        // which is the one property it must not have.
        screen<OfflineBlockRoute> {
            val viewModel: OfflineBlockViewModel = viewModel { offlineBlockViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    OfflineBlockEvent.Dismiss -> router.goBack()
                    OfflineBlockEvent.OpenPaywall -> router.navigate(
                        PaywallRoute(trigger = PaywallTrigger.OfflineBlock.id),
                        NavigationOptions(launchSingleTop = true),
                    )
                }
            }

            OfflineBlockScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
