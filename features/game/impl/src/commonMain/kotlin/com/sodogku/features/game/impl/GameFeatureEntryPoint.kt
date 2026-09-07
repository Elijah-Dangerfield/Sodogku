package com.sodogku.features.game.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.sodogku.features.game.GameRoute
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
class GameFeatureEntryPoint(
    private val gameViewModelFactory: (levelId: Int) -> GameViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<GameRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GameRoute>()
            val viewModel: GameViewModel = viewModel { gameViewModelFactory(route.levelId) }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    GameEvent.NavigateBack -> router.goBack()
                    // Sound and haptics land here in C12; the cell animates itself.
                    is GameEvent.PlacedDog, is GameEvent.Struck -> Unit
                }
            }

            GameScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
