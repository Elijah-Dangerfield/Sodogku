package com.sodogku.libraries.sodogku

import kotlinx.coroutines.flow.Flow

sealed class AppEvent {
    data object ColdBoot : AppEvent()
    data object WarmBoot : AppEvent()
    data class OnForeground(val isColdBoot: Boolean) : AppEvent()
    data object OnBackground : AppEvent()

    /**
     * The active user identity changed.
     *
     * **Nothing dispatches this.** The auth layer that fired it, and the
     * `UserScopedClearer` dump it announced, both went with accounts in C0.
     * There is one player per install, that identity never moves, and no local
     * data is ever wiped short of uninstall. The event and
     * [AppEventListener.onUserChanged] survive as the shape a future identity
     * feature would reuse; until then, a listener written for it will never
     * run.
     *
     * If it is ever revived: listeners must run synchronously, must not trigger
     * network work, and are the *announcement* rather than the clear. Anything
     * holding per-identity storage has to be wiped before the event, not by a
     * listener reacting to it.
     *
     * @param previous the user id that was active, or null if none.
     * @param current the user id now active, or null if none.
     */
    data class UserChanged(val previous: String?, val current: String?) : AppEvent() {
        /** True when this transition ended with no identity at all. */
        val isSignedOut: Boolean get() = current == null
    }

    /**
     * Connectivity was just regained — an offline→online edge. Dispatched by
     * [com.sodogku.libraries.sodogku.impl.ConnectivityEdgeDispatcher],
     * which watches `AppState.isOffline`, drops the optimistic initial value,
     * debounces flapping, and only fires on the true→false transition (so an
     * online cold boot never emits one).
     *
     * This is a *data-freshness* trigger: identity self-heal, authed-call
     * retriggers, and offline outboxes hang their reconnect flush off it. Like
     * the other events, listeners must run synchronously and hand heavy work off
     * to their own scope.
     */
    data object ConnectivityRegained : AppEvent()
}

interface AppEventListener {
    fun onColdBoot(event: AppEvent.ColdBoot) {}
    fun onWarmBoot(event: AppEvent.WarmBoot) {}
    fun onForeground(event: AppEvent.OnForeground) {}
    fun onBackground(event: AppEvent.OnBackground) {}
    fun onUserChanged(event: AppEvent.UserChanged) {}
    fun onConnectivityRegained(event: AppEvent.ConnectivityRegained) {}
}

/**
 * Public dispatch surface for [AppEvent]. The concrete
 * [com.sodogku.libraries.sodogku.impl.AppEventDispatcher] handles
 * boot/foreground/background lifecycle implicitly; explicit events
 * (currently just [AppEvent.UserChanged]) are dispatched by callers that
 * hold this bus.
 *
 * Lives in the api module so feature impls + libraries that can't see
 * the dispatcher's impl module can still publish events.
 */
interface AppEventBus {
    fun dispatch(event: AppEvent)

    /**
     * The same events [dispatch] fans out to listeners, as a hot stream — for
     * consumers that prefer to react reactively (filter, combine, carry state
     * across events) instead of implementing [AppEventListener]. Inject [AppEvents]
     * rather than depending on the bus directly.
     */
    fun eventStream(): Flow<AppEvent>

    /**
     * Like [eventStream] but without the one-event replay: a subscriber sees
     * only events dispatched *after* it attached. This is the stream for
     * **edge** semantics (re-fire triggers) — a replayed edge is by definition
     * stale and re-acting to it double-fires. Consumers that need the no-miss
     * guarantee for state activation should key off a level (e.g.
     * `AuthRepository.observe`) instead, not off replayed edges.
     */
    fun liveEventStream(): Flow<AppEvent>
}

interface AppLifecycleObserver {
    fun onEnterForeground()
    fun onEnterBackground()
}

interface AppLifecycle {
    fun addObserver(observer: AppLifecycleObserver)
    fun removeObserver(observer: AppLifecycleObserver)
}


