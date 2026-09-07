package com.sodogku.features.home.impl

import com.sodogku.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject

@Inject
class HomeViewModel : SEAViewModel<HomeState, HomeEvent, HomeAction>(
    initialStateArg = HomeState(),
) {

    override suspend fun handleAction(action: HomeAction) {
        when (action) {
            is HomeAction.Load -> Unit
            is HomeAction.Refresh -> Unit
        }
    }
}

data class HomeState(
    val placeholder: Unit = Unit,
)

sealed interface HomeEvent

sealed interface HomeAction {
    data object Load : HomeAction
    data object Refresh : HomeAction
}
