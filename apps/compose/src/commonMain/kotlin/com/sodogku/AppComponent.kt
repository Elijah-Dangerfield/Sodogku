package com.sodogku

import com.sodogku.libraries.config.values.FeatureSharing
import com.sodogku.libraries.core.AppState
import com.sodogku.libraries.core.AutoInit
import com.sodogku.libraries.navigation.DeepLinkBridge
import com.sodogku.libraries.navigation.impl.DelegatingRouter
import com.sodogku.libraries.sharing.ShareLauncher
import com.sodogku.libraries.telemetry.impl.JankMonitor
import com.sodogku.libraries.telemetry.impl.StartupReporter
import com.sodogku.libraries.sodogku.Telemetry
import com.sodogku.libraries.navigation.FeatureEntryPoint
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.TimeSource

@ContributesTo(AppScope::class)
@SingleIn(AppScope::class)
interface AppComponent {
    val featureEntryPoints: Set<FeatureEntryPoint>
    val appViewModel: AppViewModel  // Singleton, shared between MainActivity and App
    val delegatingRouter: DelegatingRouter
    val telemetry: Telemetry

    /**
     * Real-user frame timing, reported per screen. Android binds JankStats; iOS
     * binds a no-op (see [JankMonitor]).
     */
    val jankMonitor: JankMonitor

    /**
     * Cold-start duration, reported once per process when the first usable
     * frame is on screen. Android measures from OS process creation; iOS
     * reports nothing on purpose (see [ProcessStartTimeProvider]).
     */
    val startupReporter: StartupReporter
    val shakeHandler: ShakeHandler
    val deepLinkBridge: DeepLinkBridge

    /**
     * The platform share sheet, provided into the composition as
     * [com.sodogku.libraries.ui.system.LocalShareSheet]. It is pulled out here
     * rather than injected into a ViewModel because a share's title and streak
     * line are string resources, so only a composable can build one.
     */
    val shareLauncher: ShareLauncher

    /**
     * `features.sharing`, provided into the composition as
     * [com.sodogku.libraries.ui.system.LocalSharingEnabled]. Pulled out
     * alongside [shareLauncher] and for the same reason — the only thing that
     * can build a share is a composable — and handed over as the value object
     * rather than as a resolved boolean, so the button asks the config map when
     * it draws instead of inheriting an answer from app start.
     */
    val sharingEnabled: FeatureSharing

    /**
     * Production app-wide state (offline banner etc.). Backed by
     * AppStateImpl — platform connectivity combined with witnessed
     * request reachability.
     */
    val appState: AppState

    /**
     * Singletons that need to construct at app boot rather than lazily
     * on first injection. Anvil populates this set via the
     * `@ContributesBinding(... AutoInit::class, multibinding = true)`
     * annotation on each implementer — see [AutoInit] for the
     * contract and when to opt in.
     *
     * The set is resolved once on first composition in `App.kt` (and in
     * `Application.onCreate` on Android); the act of resolving forces
     * every contributor to construct, which runs each implementer's
     * `init {}` block. That's how
     * [com.sodogku.libraries.sodogku.impl.AppEventDispatcher]
     * registers its lifecycle listener at boot.
     */
    val autoInits: Set<AutoInit>

    @Provides
    fun provideClock(): Clock = Clock.System

    /**
     * The monotonic clock, for anything measuring *elapsed* time rather than
     * wall-clock time: a level's duration, the speed bonus, gesture windows.
     * Injected so those are testable against a clock a test can advance.
     */
    @Provides
    fun provideTimeSource(): TimeSource.WithComparableMarks = TimeSource.Monotonic

}
