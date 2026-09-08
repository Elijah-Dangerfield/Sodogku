package com.sodogku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sodogku.features.game.GameRoute
import com.sodogku.features.onboarding.OnboardingRoute
import com.sodogku.libraries.config.EnsureAppConfigLoaded
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.networking.AccessDeniedBus
import com.sodogku.libraries.sodogku.AppCache
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Hard cap on how long the Compose boot gate waits for app-config to resolve.
 * The config repository already times its network refresh out and falls back to
 * bundled defaults, so this is just a backstop so a wedged fetch can never
 * strand the player on the loading screen.
 */
private const val BootConfigTimeoutMillis = 8_000L

/**
 * App-level ViewModel. Resolves the start destination by reading the persistent
 * `AppData` cache — returning players land straight on the puzzle they had
 * reached; first-launch players land on [OnboardingRoute].
 *
 * Scoped as singleton so Android's splash-screen API can read the same instance
 * used by the App composable.
 *
 * `startDestination` is a [StateFlow] that's `null` until the cache read
 * completes. The App composable blocks NavHost construction on a non-null
 * value, keeping the splash visible during the brief async read.
 *
 * Two readiness signals drive a two-stage boot:
 *  - [isReady] flips as soon as the start destination is resolved. It releases
 *    the platform splash so it hands off to the Compose boot gate.
 *  - [isBootComplete] flips once app-config has resolved (or timed out), so the
 *    first screen renders real config-driven values on frame one instead of the
 *    "server hasn't told us yet" sentinel.
 *
 * The iOS splash-overlay state lives inside the [App] composable itself, not
 * here — keeping it out of the reactive app state means flipping it cannot
 * trigger a root recomposition.
 */
@SingleIn(AppScope::class)
@Inject
class AppViewModel(
    private val appCache: AppCache,
    private val ensureAppConfigLoaded: EnsureAppConfigLoaded,
    private val accessDeniedBus: AccessDeniedBus,
    private val progress: ProgressRepository,
) : ViewModel() {

    private val logger = KLog.withTag("AppNav")

    private val _startDestination = MutableStateFlow<Route?>(null)
    val startDestination: StateFlow<Route?> = _startDestination.asStateFlow()

    /**
     * Fires when the server returned a `403` with the locked access-denied
     * envelope. The App composable collects this to push a blocking
     * access-denied screen. A one-shot **event** (navigation), so a `Channel` —
     * same convention as `SEAViewModel`: exactly-once delivery to the single
     * collector, no replay, buffered if it fires before the collector is ready.
     */
    private val _accessDenied = Channel<AccessDeniedBus.Denial>(Channel.UNLIMITED)
    val accessDenied: Flow<AccessDeniedBus.Denial> = _accessDenied.receiveAsFlow()

    private val _isReady = MutableStateFlow(false)

    /**
     * True once the start destination is resolved. Exposed to Android's splash
     * screen API for keepOnScreenCondition so the platform splash dismisses
     * promptly once we know where to navigate.
     */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isBootComplete = MutableStateFlow(false)

    /**
     * True once app-config has resolved (or timed out). The App composable
     * holds the cycling boot-loading screen until this flips, then renders the
     * nav graph.
     */
    val isBootComplete: StateFlow<Boolean> = _isBootComplete.asStateFlow()

    init {
        viewModelScope.launch {
            val onboarded = appCache.get().hasUserOnboarded
            // Progress is the single source of how far the player got. The old
            // `AppData.currentLevel` stopgap is gone, and reading it here after
            // nothing writes it would have opened everyone on level 1 forever.
            val level = Catching { progress.unlockedThrough() }
                .logOnFailure { "Failed to read progress for the start destination" }
                .getOrNull()
                ?.let(LevelPacks::clampToCampaign)
                ?: LevelRecord.FIRST_LEVEL_ID
            logger.d {
                "Resolving start destination: hasUserOnboarded=$onboarded → " +
                    if (onboarded) "Game(level $level)" else "Onboarding"
            }
            // The puzzle *is* the home screen. Sending a returning player to a
            // menu first puts a navigation between them and the thing they
            // opened the app to do; the level list is a drawer on the board.
            _startDestination.value = if (onboarded) {
                GameRoute(level)
            } else {
                OnboardingRoute()
            }
            // Start destination resolved — release the platform splash; the
            // Compose boot gate now covers the rest of the wait.
            _isReady.value = true

            withTimeoutOrNull(BootConfigTimeoutMillis) { ensureAppConfigLoaded() }
            _isBootComplete.value = true
        }

        viewModelScope.launch {
            accessDeniedBus.denials.collect { denial -> _accessDenied.trySend(denial) }
        }
    }
}
