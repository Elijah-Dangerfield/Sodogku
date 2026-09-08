package com.sodogku

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sodogku.features.gate.impl.LaunchGateHost
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Platform
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.navigation.AccessDeniedRoute
import com.sodogku.libraries.navigation.AnimationType
import com.sodogku.libraries.navigation.FeatureEntryPoint
import com.sodogku.libraries.navigation.NavigationOptions
import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.navigation.RouteTransitions
import com.sodogku.libraries.navigation.floatingwindow.FloatingWindowHost
import com.sodogku.libraries.navigation.floatingwindow.FloatingWindowNavigator
import com.sodogku.libraries.navigation.impl.DelegatingRouter
import com.sodogku.libraries.navigation.serializableType
import com.sodogku.libraries.navigation.toEnterTransition
import com.sodogku.libraries.navigation.toExitTransition
import com.sodogku.libraries.navigation.toRouteOrNull
import com.sodogku.libraries.sodogku.Telemetry
import com.sodogku.libraries.telemetry.impl.JankMonitor
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.SnackbarDuration
import com.sodogku.libraries.ui.components.dialog.DialogHost
import com.sodogku.libraries.ui.components.dialog.LocalDialogHostState
import com.sodogku.libraries.ui.components.dialog.rememberDialogHostState
import com.sodogku.libraries.ui.debug.RecompositionCounter
import com.sodogku.libraries.ui.snackbar.PresenterSnackbarHost
import com.sodogku.libraries.ui.snackbar.showDebugSnackBar
import com.sodogku.libraries.ui.system.LocalAppState
import com.sodogku.libraries.ui.system.LocalBuildInfo
import com.sodogku.libraries.ui.system.LocalClock
import com.sodogku.libraries.ui.system.LocalReduceAnimations
import com.sodogku.libraries.ui.system.LocalShareSheet
import com.sodogku.libraries.ui.system.LocalSharingEnabled
import com.sodogku.system.AppThemeProvider
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.seconds

@Composable
fun App(appComponent: AppComponent) {
    val appViewModel = appComponent.appViewModel
    val floatingWindowNavigator = remember { FloatingWindowNavigator() }
    val navController = rememberNavController(floatingWindowNavigator)
    val appRecomposeLogger = remember { KLog.withTag("AppRecompose") }
    val router = remember { appComponent.delegatingRouter }
    val dialogHostState = rememberDialogHostState()

    val shakeHandler = remember { appComponent.shakeHandler }
    val deepLinkBridge = remember { appComponent.deepLinkBridge }
    val launchGateViewModel = remember { appComponent.launchGateViewModel }

    // Boot-warm every @AutoInit singleton. Resolving the Set forces each
    // contributor to construct, running their `init {}` blocks —
    // AppEventDispatcher attaches its lifecycle observer, connectivity
    // watchers arm, etc. Wrapped in `remember` so this resolves exactly once
    // per composition lifetime, even though App() recomposes. See [AutoInit]
    // for the contract. (Android also resolves this in Application.onCreate,
    // before the first Activity; resolving twice is a no-op.)
    remember { appComponent.autoInits }

    DisposableEffect(shakeHandler) {
        shakeHandler.start()
        onDispose {
            shakeHandler.stop()
        }
    }

    LaunchedEffect(navController, deepLinkBridge, launchGateViewModel) {
        deepLinkBridge.urls.collect { url ->
            // Dropped rather than queued while a launch gate is blocking. The
            // nav host is not composed behind a blocking gate, so nothing would
            // render — but a queued deep link would fire the moment the block
            // lifted, which is a link arriving minutes late at a screen the
            // player is no longer expecting. Read off the ViewModel rather than
            // from composition so this collector never recomposes App.
            if (launchGateViewModel.state.blocking != null) {
                KLog.w { "Dropping deep link behind a blocking launch gate: $url" }
                return@collect
            }
            Catching {
                val request = NavDeepLinkRequest.Builder.fromUri(NavUri(url)).build()
                navController.handleDeepLink(request)
            }.logOnFailure { "Failed to handle deep link: $url" }
        }
    }

    RecompositionCounter(
        tag = "App",
        logEvery = 1,
        rapidRecompositionThreshold = 6,
        rapidRecompositionWindow = 60.seconds,
        onRecompose = { count ->
            val message = if (count == 1L) {
                "App recomposed (this should be rare)"
            } else {
                "App recomposed $count times"
            }
            appRecomposeLogger.w { message }
        },
        onRapidRecomposition = { info ->
            appRecomposeLogger.e {
                "Rapid recompositions: ${info.countInWindow} in ${info.windowMillis}ms (total=${info.totalCount})"
            }
            showDebugSnackBar(
                title = "Performance hiccup",
                message = "App recomposed ${info.countInWindow}× in ${info.windowMillis}ms.",
                duration = SnackbarDuration.Long,
                withDismissAction = true,
            )
        }
    )

    val appState = remember { appComponent.appState }
    val reduceAnimations by appViewModel.reduceAnimations.collectAsState()

    CompositionLocalProvider(
        LocalAppState provides appState,
        LocalClock provides appComponent.provideClock(),
        LocalBuildInfo provides BuildInfo,
        LocalDialogHostState provides dialogHostState,
        // One provider for the whole tree: a component that moves on its own
        // reads the setting rather than being handed it, so a new screen honours
        // it without its author knowing it exists.
        LocalReduceAnimations provides reduceAnimations,
        // Provided once at the root: every screen that can build a share
        // string reads this rather than being handed a launcher.
        LocalShareSheet provides appComponent.shareLauncher,
        // The value object, not `sharingEnabled()`. Resolving it here would
        // answer once for the life of the composition — App is built to
        // recompose almost never — and a kill switch that waits for a process
        // restart is not one.
        LocalSharingEnabled provides appComponent.sharingEnabled::invoke,
    ) {
        AppThemeProvider {
            Box(modifier = Modifier.fillMaxSize()) {
                // Stage 1: null until the async AppData read resolves — the
                // platform splash (keyed on appViewModel.isReady) covers the
                // gap. Stage 2: the Compose boot gate holds a loading screen
                // until app-config resolves, so the first real frame renders
                // authoritative config values.
                val bootComplete by appViewModel.isBootComplete.collectAsState()
                val startDestination by appViewModel.startDestination.collectAsState()
                val route = startDestination
                if (bootComplete && route != null) {
                    // The launch gates wrap the whole app rather than sitting on
                    // it as a route: a blocking gate is rendered *instead of* the
                    // nav host, so there is no back stack entry to pop and no
                    // deep link that can land behind it. The gate state is read
                    // inside the host, never here — a state read in App
                    // recomposes the root and rebuilds the nav graph.
                    LaunchGateHost(
                        viewModel = launchGateViewModel,
                        onOpenLink = router::openWebLink,
                    ) {
                        AppNavigation(
                            navController = navController,
                            floatingWindowNavigator = floatingWindowNavigator,
                            featureEntryPoints = appComponent.featureEntryPoints,
                            startDestination = route,
                            router = router,
                            telemetry = appComponent.telemetry,
                            jankMonitor = appComponent.jankMonitor,
                        )
                    }
                } else {
                    BootLoadingScreen()
                }

                SplashGate()

                // Server returned the locked `403` access-denied envelope: push
                // the blocking AccessDenied screen. launchSingleTop so a burst
                // of denied calls collapses to one screen on top. The screen
                // keys title/body off `reason` and surfaces the optional lift
                // date + appeal link.
                LaunchedEffect(Unit) {
                    appViewModel.accessDenied.collect { denial ->
                        router.navigate(
                            AccessDeniedRoute(
                                reason = denial.reason,
                                until = denial.until,
                                appealUrl = denial.appealUrl,
                            ),
                            NavigationOptions(launchSingleTop = true),
                        )
                    }
                }

                DialogHost(
                    modifier = Modifier.matchParentSize(),
                    hostState = dialogHostState
                )
            }
        }
    }
}

/**
 * The simple class name of a destination's route, for telemetry tagging.
 *
 * Type-safe nav stores the route as its serializer name — the fully-qualified
 * class name followed by argument placeholders, e.g.
 * `com.sodogku.features.home.HomeRoute?tab={tab}`. We strip the args and
 * the package to get just `HomeRoute`, keeping the tag low-cardinality and
 * readable. Returns null for unnamed/graph destinations.
 */
private fun NavDestination.routeClassNameOrNull(): String? =
    route
        ?.substringBefore('/')
        ?.substringBefore('?')
        ?.substringAfterLast('.')
        ?.takeIf { it.isNotBlank() }

@Composable
private fun AppNavigation(
    navController: NavHostController,
    floatingWindowNavigator: FloatingWindowNavigator,
    featureEntryPoints: Set<FeatureEntryPoint>,
    startDestination: Route,
    router: DelegatingRouter,
    telemetry: Telemetry,
    jankMonitor: JankMonitor,
) {
    // Tag every crash/error with the route the user is currently on. Sheets
    // and dialogs are real destinations on this same back stack (see
    // `bottomSheet`/`dialog` nav builders), so an open sheet wins over the
    // screen beneath it — exactly the granularity we want for triage. Pushed
    // from a LaunchedEffect keyed on the name so we only touch the Sentry
    // scope when the route actually changes, not on every recomposition.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = currentBackStackEntry?.destination?.routeClassNameOrNull()
    LaunchedEffect(currentRouteName) {
        currentRouteName?.let {
            telemetry.setCurrentRoute(it)
            // Same hook, so jank attribution and crash attribution always name
            // the same screen. This closes out the previous screen's frame tally
            // and emits it as one app.jank event — never one per frame.
            jankMonitor.onRouteChanged(it)
        }
    }

    // Remember the graph-builder lambda so recompositions of AppNavigation
    // hand NavHost the SAME builder instance. A fresh lambda each pass makes
    // NavHost treat the graph as changed and re-push the start destination
    // onto the back stack.
    val graph: NavGraphBuilder.() -> Unit = remember(featureEntryPoints, router) {
        {
            featureEntryPoints.forEach { entryPoint ->
                with(entryPoint) {
                    buildNavGraph(router)
                }
            }
        }
    }

    Screen(
        snackbarHost = {
            PresenterSnackbarHost()
        },
        content = {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                //To make this more readable consider Screens A and B
                // The rules live in RouteTransitions so they can be tested.
                // These lambdas are handed a NavBackStackEntry inside an
                // AnimatedContentTransitionScope, neither of which a unit test
                // can build, so while the rules were inline the only way to
                // check them was to open the app and watch.
                enterTransition = {
                    RouteTransitions.enter(
                        target = targetState.toRouteOrNull<Route>(),
                    ).toEnterTransition()
                },
                popEnterTransition = {
                    RouteTransitions.popEnter(
                        initial = initialState.toRouteOrNull<Route>(),
                        target = targetState.toRouteOrNull<Route>(),
                    ).toEnterTransition()
                },
                exitTransition = {
                    RouteTransitions.exit(
                        initial = initialState.toRouteOrNull<Route>(),
                        target = targetState.toRouteOrNull<Route>(),
                    ).toExitTransition()
                },
                popExitTransition = {
                    RouteTransitions.popExit(
                        initial = initialState.toRouteOrNull<Route>(),
                    ).toExitTransition()
                },
                typeMap = mapOf(
                    typeOf<AnimationType>() to serializableType<AnimationType>()
                ),
                builder = graph
            )

            FloatingWindowHost(floatingWindowNavigator)

            router.Bind(navController)
        },
    )
}

/**
 * Isolates the splash-overlay's `hasShownSplash` state read into its own composable
 * so that flipping it cannot recompose `App` and, in particular, cannot cause
 * `AppNavigation` / `NavHost` to rebuild its graph — which was previously pushing
 * a second copy of the start destination onto the back stack.
 *
 * Only renders on iOS. Android uses the native splash API instead.
 */
@Composable
private fun SplashGate() {
    if (BuildInfo.platform != Platform.iOS) return
    var hasShownSplash by rememberSaveable { mutableStateOf(false) }
    if (!hasShownSplash) {
        SplashOverlay(onComplete = { hasShownSplash = true })
    }
}
