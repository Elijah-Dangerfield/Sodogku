package com.sodogku.libraries.sodogku.impl

import com.sodogku.libraries.core.AppState
import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.sodogku.AppEvents
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The app-state edges that background refresh work hangs off, derived once so
 * every consumer agrees on the semantics.
 *
 * Sodogku has no accounts and no user-scoped server state, so there is no
 * "active account" level here. What remains is the pair of edges that anything
 * network-touching (remote config refresh, ad preloading) wants:
 *
 * - [warmForeground] — edge; app resumed. Reads the replay-free stream so a
 *   stale pre-subscribe foreground can't double-fire at activation. Cold-boot
 *   foregrounds are excluded: boot work belongs to the caller's own init.
 * - [cameOnline] — edge; connectivity regained, derived from the
 *   [AppState.isOffline] level (not the replayed [AppEvent.ConnectivityRegained]
 *   edge, which could spuriously refire at boot from the bus's replay slot).
 *   Initial value dropped, flaps debounced.
 */
@SingleIn(AppScope::class)
@Inject
class SyncTriggers(
    appEventsProvider: () -> AppEvents,
    private val appState: AppState,
) {

    // Deferred: SyncTriggers is constructed by AppEventListeners inside the
    // event dispatcher's own construction. Nothing here is touched until a
    // trigger flow is collected, which only happens from post-construction
    // coroutines.
    private val appEvents by lazy { appEventsProvider() }

    val warmForeground: Flow<Unit> = flow {
        emitAll(
            appEvents.live()
                .filterIsInstance<AppEvent.OnForeground>()
                .filter { !it.isColdBoot }
                .map { },
        )
    }

    /**
     * Level twin of [cameOnline] — read before starting network work that
     * should defer while the device has no route. A cycle that parks on this
     * level is re-armed by the [cameOnline] edge, so deferring is safe.
     */
    val isOffline: StateFlow<Boolean> get() = appState.isOffline

    @OptIn(FlowPreview::class)
    val cameOnline: Flow<Unit> = appState.isOffline
        .drop(1)
        .debounce(ONLINE_DEBOUNCE_MS)
        .filter { offline -> !offline }
        .map { }

    private companion object {
        const val ONLINE_DEBOUNCE_MS: Long = 750L
    }
}
