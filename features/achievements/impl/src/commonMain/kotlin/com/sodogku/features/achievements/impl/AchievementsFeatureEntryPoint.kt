package com.sodogku.features.achievements.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.sodogku.features.achievements.AchievementsRoute
import com.sodogku.libraries.flowroutines.ObserveEvents
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
class AchievementsFeatureEntryPoint(
    private val achievementsViewModelFactory: () -> AchievementsViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<AchievementsRoute> {
            val viewModel: AchievementsViewModel = viewModel { achievementsViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    AchievementsEvent.NavigateBack -> router.goBack()
                }
            }

            AchievementsScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
