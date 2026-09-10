package com.sodogku

import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavGraphBuilder
import com.sodogku.features.profile.BugReportRoute
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.navigation.FeatureEntryPoint
import com.sodogku.libraries.navigation.Router
import com.sodogku.libraries.navigation.QaToolsRoute
import com.sodogku.libraries.navigation.ShakeDialogRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sodogku.libraries.flowroutines.ObserveEvents
import com.sodogku.libraries.navigation.dialog
import com.sodogku.libraries.navigation.screen
import com.sodogku.qa.QaToolsEvent
import com.sodogku.qa.QaToolsScreen
import com.sodogku.qa.QaToolsViewModel
import com.sodogku.libraries.networking.NetworkInspector
import com.sodogku.libraries.ui.components.dialog.ShakeDialog
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class ShakeDialogEntryPoint(
    private val networkInspector: NetworkInspector,
    private val shakeHandler: ShakeHandler,
    private val qaToolsViewModelFactory: () -> QaToolsViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        // Registered unconditionally; nothing navigates here outside a debug
        // build. See `QaToolsRoute` for why the guard lives at the two entry
        // points rather than here as well.
        screen<QaToolsRoute> {
            val viewModel: QaToolsViewModel = viewModel { qaToolsViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    QaToolsEvent.Back -> router.goBack()
                }
            }

            QaToolsScreen(state = state, onAction = viewModel::takeAction)
        }

        dialog<ShakeDialogRoute> { _, dialogState ->
            // The handler suppresses further shakes while this is up, and this
            // is the only thing that tells it either way. Tied to the
            // destination leaving composition rather than to a button, so back
            // presses, scrim taps and navigating away all clear it — and so
            // does a navigation that never arrived, because then this never ran.
            DisposableEffect(Unit) {
                shakeHandler.onDialogShown()
                onDispose { shakeHandler.onDialogDismissed() }
            }

            ShakeDialog(
                state = dialogState,
                onDismiss = { router.goBack() },
                onReportBug = {
                    router.goBack()
                    router.navigate(
                        BugReportRoute(contextMessage = "Triggered via shake")
                    )
                },
                // Debug-only: reuse the shake gesture to also open the
                // WiretapKMP network inspector. Hidden in release (and the
                // inspector itself is the noop there).
                onOpenQaTools = if (BuildInfo.isDebug) {
                    {
                        router.goBack()
                        router.navigate(QaToolsRoute())
                    }
                } else {
                    null
                },
                onOpenNetworkInspector = if (BuildInfo.isDebug) {
                    {
                        router.goBack()
                        networkInspector.open()
                    }
                } else {
                    null
                },
            )
        }
    }
}
