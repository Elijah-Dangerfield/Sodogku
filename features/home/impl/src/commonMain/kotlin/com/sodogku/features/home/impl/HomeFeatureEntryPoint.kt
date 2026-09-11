package com.sodogku.features.home.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.sodogku.features.home.impl.bugreport.BugReportScreen
import com.sodogku.features.home.impl.bugreport.BugReportViewModel
import com.sodogku.features.profile.BugReportRoute
import com.sodogku.libraries.navigation.FeatureEntryPoint
import com.sodogku.libraries.navigation.Router
import com.sodogku.libraries.navigation.screen
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Bug reporting, and nothing else.
 *
 * The name is the last of a home screen that no longer exists. `HomeScreen` was
 * a three-button launcher standing in for the level map until C5, and once the
 * board became the start destination nothing navigated to it: the level drawer
 * on the board is the level list, and `sodogku://game?levelId=N` reaches any
 * level directly. The module is still here because `BugReportRoute`'s screen is,
 * and because `FeedbackRoute` is declared beside it for the reason its own KDoc
 * gives.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class HomeFeatureEntryPoint(
    private val bugReportViewModelFactory: (logId: String?, errorCode: Int?, contextMessage: String?) -> BugReportViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<BugReportRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BugReportRoute>()
            val viewModel: BugReportViewModel = viewModel {
                bugReportViewModelFactory(route.logId, route.errorCode, route.contextMessage)
            }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            BugReportScreen(
                state = state,
                onAction = viewModel::takeAction,
            )
        }
    }
}
