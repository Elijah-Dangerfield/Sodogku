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
import com.sodogku.libraries.ui.system.Feel
import com.sodogku.libraries.ui.system.rememberHaptics
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
            val haptics = rememberHaptics(enabled = state.haptics)

            viewModel.ObserveEvents { event ->
                when (event) {
                    GameEvent.NavigateBack -> router.goBack()
                    is GameEvent.Marked -> haptics.play(Feel.Mark)
                    is GameEvent.PlacedDog -> haptics.play(Feel.Place)
                    is GameEvent.Struck -> haptics.play(Feel.Strike)
                    GameEvent.Won -> haptics.play(Feel.Win)
                }
            }

            GameScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
