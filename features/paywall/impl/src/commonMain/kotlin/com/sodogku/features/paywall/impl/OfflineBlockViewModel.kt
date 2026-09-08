package com.sodogku.features.paywall.impl

import androidx.lifecycle.viewModelScope
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.core.AppState
import com.sodogku.libraries.flowroutines.SEAViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * The offline block.
 *
 * It watches the two things that end it — the OS reporting a network again, and
 * the player becoming Pro — and dismisses itself. Making the player press
 * something after they have already reconnected would turn a friendly wall into
 * a stuck screen, which is exactly what the retry button is for on the platforms
 * where connectivity resolves slowly.
 */
@Inject
class OfflineBlockViewModel(
    private val appState: AppState,
    private val entitlements: Entitlements,
) : SEAViewModel<OfflineBlockState, OfflineBlockEvent, OfflineBlockAction>(
    initialStateArg = OfflineBlockState(),
) {

    init {
        viewModelScope.launch {
            combine(appState.isDeviceOffline, entitlements.isPro) { offline, pro -> offline to pro }
                .collect { (offline, pro) ->
                    if (!offline || pro) sendEvent(OfflineBlockEvent.Dismiss)
                }
        }
    }

    override suspend fun handleAction(action: OfflineBlockAction) {
        when (action) {
            // The flow above already dismisses on the edge; this covers the
            // case where connectivity came back before the screen was even
            // shown, so there is no edge left to observe.
            is OfflineBlockAction.Retry -> if (!appState.isDeviceOffline.value) {
                sendEvent(OfflineBlockEvent.Dismiss)
            } else {
                action.updateState { it.copy(retriedWhileOffline = true) }
            }

            is OfflineBlockAction.GoPro -> sendEvent(OfflineBlockEvent.OpenPaywall)
        }
    }
}

data class OfflineBlockState(
    /** True once a retry has found the device still offline. Softens the copy. */
    val retriedWhileOffline: Boolean = false,
)

sealed interface OfflineBlockEvent {
    data object Dismiss : OfflineBlockEvent
    data object OpenPaywall : OfflineBlockEvent
}

sealed interface OfflineBlockAction {
    data object Retry : OfflineBlockAction
    data object GoPro : OfflineBlockAction
}
