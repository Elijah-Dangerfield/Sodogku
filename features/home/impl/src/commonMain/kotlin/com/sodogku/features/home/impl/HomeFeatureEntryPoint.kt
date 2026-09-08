package com.sodogku.features.home.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.sodogku.features.home.FeedbackRoute
import com.sodogku.features.game.GameRoute
import com.sodogku.features.home.HomeRoute
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

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class HomeFeatureEntryPoint(
    private val homeViewModelFactory: () -> HomeViewModel,
    private val bugReportViewModelFactory: (logId: String?, errorCode: Int?, contextMessage: String?) -> BugReportViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<HomeRoute> {
            val viewModel: HomeViewModel = viewModel { homeViewModelFactory() }
            HomeScreen(
                viewModel = viewModel,
                onNavigateToFeedback = { router.navigate(FeedbackRoute()) },
                onNavigateToBugReport = { router.navigate(BugReportRoute()) },
                onPlay = { levelId -> router.navigate(GameRoute(levelId)) },
            )
        }

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
