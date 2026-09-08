package com.sodogku.features.paywall.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.sodogku.features.paywall.OfflineBlockRoute
import com.sodogku.features.paywall.PaywallRoute
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.flowroutines.ObserveEvents
import com.sodogku.libraries.navigation.FeatureEntryPoint
import com.sodogku.libraries.navigation.NavigationOptions
import com.sodogku.libraries.navigation.Router
import com.sodogku.libraries.navigation.screen
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class PaywallFeatureEntryPoint(
    private val paywallViewModelFactory: (trigger: String) -> PaywallViewModel,
    private val offlineBlockViewModelFactory: () -> OfflineBlockViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<PaywallRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PaywallRoute>()
            val viewModel: PaywallViewModel = viewModel { paywallViewModelFactory(route.trigger) }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    PaywallEvent.Dismiss -> router.goBack()
                    // Nothing to celebrate on this screen: the board behind it
                    // is already Pro by the time the sheet closes, and a
                    // "thanks!" interstitial after a purchase is one more thing
                    // between the player and the game they just paid for.
                    PaywallEvent.Purchased -> router.goBack()
                }
            }

            PaywallScreen(state = state, onAction = viewModel::takeAction)
        }

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
