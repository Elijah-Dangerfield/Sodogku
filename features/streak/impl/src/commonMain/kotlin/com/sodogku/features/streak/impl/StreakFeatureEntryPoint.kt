package com.sodogku.features.streak.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.sodogku.features.game.GameRoute
import com.sodogku.features.streak.StreakIntentionRoute
import com.sodogku.features.streak.StreakRoute
import com.sodogku.libraries.flowroutines.ObserveEvents
import com.sodogku.libraries.navigation.FeatureEntryPoint
import com.sodogku.libraries.navigation.Router
import com.sodogku.libraries.navigation.screen
import com.sodogku.libraries.ui.system.LocalHaptics
import com.sodogku.libraries.ui.system.rememberHaptics
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Both streak destinations, contributed to the graph by multibinding.
 *
 * Nothing in `:apps:compose` names either route. The only thing the app module
 * has to do is depend on this module so the binding exists at all.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class StreakFeatureEntryPoint(
    private val streakViewModelFactory: (celebrating: Int) -> StreakViewModel,
    private val intentionViewModelFactory: () -> StreakIntentionViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<StreakRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<StreakRoute>()
            val viewModel: StreakViewModel = viewModel(key = StreakKey(route.celebrating)) {
                streakViewModelFactory(route.celebrating)
            }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    StreakEvent.NavigateBack -> router.goBack()
                }
            }

            // Provided rather than played from here, because the thing that
            // buzzes is a day cell deep inside the calendar and only it knows
            // when its fill lands. `LocalHaptics` defaults to silent, so a
            // screen that forgets this is quiet rather than wrong.
            CompositionLocalProvider(LocalHaptics provides rememberHaptics(state.haptics)) {
                StreakScreen(state = state, onAction = viewModel::takeAction)
            }
        }

        screen<StreakIntentionRoute> {
            val viewModel: StreakIntentionViewModel = viewModel { intentionViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    // Popped first, so the daily is pushed onto the screen the
                    // player came from. Leaving this on the stack would put a
                    // moment they have already had behind the back button.
                    is StreakIntentionEvent.OpenDaily -> {
                        router.goBack()
                        router.navigate(GameRoute(levelId = event.levelId, daily = true))
                    }
                    StreakIntentionEvent.Close -> router.goBack()
                }
            }

            CompositionLocalProvider(LocalHaptics provides rememberHaptics(state.haptics)) {
                StreakIntentionScreen(state = state, onAction = viewModel::takeAction)
            }
        }
    }
}

/**
 * Keyed on the argument the ViewModel is built with.
 *
 * `viewModel { }` caches per backstack entry and ignores the factory on a hit,
 * so a page opened by tap and then re-opened by a celebration would otherwise
 * get the first instance back and quietly refuse to animate.
 */
private fun StreakKey(celebrating: Int): String = "streak-$celebrating"
