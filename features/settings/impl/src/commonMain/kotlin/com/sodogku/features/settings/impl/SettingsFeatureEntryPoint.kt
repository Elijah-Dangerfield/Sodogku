package com.sodogku.features.settings.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.sodogku.features.achievements.AchievementsRoute
import com.sodogku.features.game.GameRoute
import com.sodogku.features.home.FeedbackRoute
import com.sodogku.features.settings.SettingsRoute
import com.sodogku.features.settings.impl.feedback.FeedbackEvent
import com.sodogku.features.settings.impl.feedback.FeedbackScreen
import com.sodogku.features.settings.impl.feedback.FeedbackViewModel
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
class SettingsFeatureEntryPoint(
    private val settingsViewModelFactory: () -> SettingsViewModel,
    private val feedbackViewModelFactory: () -> FeedbackViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<SettingsRoute> {
            val viewModel: SettingsViewModel = viewModel { settingsViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    SettingsEvent.NavigateBack -> router.goBack()
                    SettingsEvent.OpenFeedback -> router.navigate(FeedbackRoute())
                    SettingsEvent.OpenAchievements -> router.navigate(AchievementsRoute())
                    // The back stack goes with it. Coming back from a replayed
                    // tutorial should land on the board, not on the settings
                    // page the player left three levels ago.
                    SettingsEvent.RerunTutorial -> router.navigate(
                        GameRoute(levelId = FirstGuidedLevel),
                        NavigationOptions(launchSingleTop = true, clearBackStack = true),
                    )
                    // The legal pages are hosted (GitHub Pages, from `pages/`),
                    // so they open in a browser rather than as in-app screens.
                    is SettingsEvent.OpenLink -> router.openWebLink(event.url)
                }
            }

            SettingsScreen(state = state, onAction = viewModel::takeAction)
        }

        // `FeedbackRoute` is declared in :features:home — see the note on the
        // route itself. The screen and its view model live here.
        screen<FeedbackRoute> {
            val viewModel: FeedbackViewModel = viewModel { feedbackViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    FeedbackEvent.NavigateBack -> router.goBack()
                }
            }

            FeedbackScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}

/** Where a replayed tutorial starts. The guided run is levels 1 to 3. */
private const val FirstGuidedLevel = 1
