package com.sodogku.features.game.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.sodogku.features.achievements.AchievementsRoute
import com.sodogku.features.game.GameRoute
import com.sodogku.features.home.FeedbackRoute
import com.sodogku.features.settings.SettingsRoute
import com.sodogku.features.streak.StreakIntentionRoute
import com.sodogku.features.streak.StreakRoute
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
    private val gameViewModelFactory: (levelId: Int, isDaily: Boolean) -> GameViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<GameRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GameRoute>()
            val viewModel: GameViewModel = viewModel {
                gameViewModelFactory(route.levelId, route.daily)
            }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            val haptics = rememberHaptics(enabled = state.haptics)

            viewModel.ObserveEvents { event ->
                when (event) {
                    GameEvent.NavigateBack -> router.goBack()
                    // Both packs get a route of their own rather than a mode
                    // swapped into the current screen: the route is what a
                    // process death restores, and an id alone does not say
                    // which pack it came from.
                    is GameEvent.OpenDaily -> router.navigate(
                        GameRoute(levelId = event.levelId, daily = true),
                    )
                    is GameEvent.OpenLevel -> router.navigate(GameRoute(levelId = event.levelId))
                    is GameEvent.Marked -> haptics.play(Feel.Mark)
                    is GameEvent.PlacedDog -> haptics.play(Feel.Place)
                    is GameEvent.Struck -> haptics.play(Feel.Strike)
                    GameEvent.Won -> haptics.play(Feel.Win)
                    // Legal pages are hosted (GitHub Pages via `pages/`), so they
                    // open in a browser rather than as in-app screens. The URLs
                    // move to remote config with the rest of the legal gate in C11.
                    GameEvent.OpenPrivacy -> router.openWebLink(PrivacyUrl)
                    GameEvent.OpenTerms -> router.openWebLink(TermsUrl)
                    GameEvent.OpenFeedback -> router.navigate(FeedbackRoute())
                    GameEvent.OpenSettings -> router.navigate(SettingsRoute())
                    GameEvent.OpenAchievements -> router.navigate(AchievementsRoute())
                    GameEvent.OpenStreakIntention -> router.navigate(StreakIntentionRoute())
                    is GameEvent.OpenStreak -> router.navigate(StreakRoute(celebrating = event.streak))
                }
            }

            GameScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}

/** Placeholders until `legal.privacyUrl` / `legal.termsUrl` land in remote config (C11). */
private const val PrivacyUrl = "https://elijah-dangerfield.github.io/Sodogku/privacy.html"
private const val TermsUrl = "https://elijah-dangerfield.github.io/Sodogku/terms.html"
