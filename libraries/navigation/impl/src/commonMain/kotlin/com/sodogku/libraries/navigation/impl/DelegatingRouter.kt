package com.sodogku.libraries.navigation.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.shouldNotBeCaught
import com.sodogku.libraries.core.throwIfDebug
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.observeWithLifecycle
import com.sodogku.libraries.navigation.BlockingErrorRoute
import com.sodogku.libraries.navigation.NavigationOptions
import com.sodogku.libraries.navigation.NavigationRecovery
import com.sodogku.libraries.navigation.NavigationTracker
import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.navigation.Router
import com.sodogku.libraries.navigation.WebLinkLauncher
import com.sodogku.libraries.navigation.NavigableWhileBlocked
import com.sodogku.libraries.navigation.TrackableRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Duration.Companion.seconds

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = Router::class)
@ContributesBinding(AppScope::class, boundType = NavigationRecovery::class)
@Inject
class DelegatingRouter(
    private val appScope: AppCoroutineScope,
    private val webLinkLauncher: WebLinkLauncher,
    private val navigationTracker: NavigationTracker,
) : Router, NavigationRecovery {

    private val logger = KLog.withTag("DelegatingRouter")
    private val navigationRequests = Channel<NavHostController.() -> Unit>(Channel.UNLIMITED)

    private var navController: NavHostController? = null
    private var processingJob: Job? = null

    private val watchdog = NavigationQueueWatchdog()

    /**
     * The lifecycle the drain is gated on, kept only so a stall report can say
     * what state it was in. That is the fact SD-26 needs and cannot get from a
     * log line after the fact.
     */
    private var gatingLifecycle: Lifecycle? = null

    /**
     * Set while something is waiting, so the watchdog poll is asleep on a flow
     * rather than waking every second for the life of the app.
     *
     * This lives on the router and not inside [NavigationQueueWatchdog] because
     * the watchdog is deliberately free of coroutines: everything it decides is
     * a function of counts and elapsed time, which is what makes its rules
     * assertions instead of scenarios.
     */
    private val queueHasWork = MutableStateFlow(false)

    init {
        // On the app scope, not the view scope, and that is the whole point. A
        // watchdog gated on the same lifecycle as the thing it watches would go
        // quiet in exactly the situation it exists to report.
        appScope.launch {
            while (true) {
                queueHasWork.first { it }
                delay(StallPollInterval)
                watchdog.stall()?.let { stall ->
                    // On main because the report reads the back stack, and a
                    // NavController read from another thread is undefined.
                    withContext(Dispatchers.Main.immediate) { reportStall(stall) }
                }
                queueHasWork.value = watchdog.isWaiting
            }
        }
    }

    fun clearNavController(controller: NavHostController? = null) {
        if (navController === controller || controller == null) {
            logger.d { "Clearing nav controller" }
            processingJob?.cancel()
            processingJob = null
            navController = null
            gatingLifecycle = null
        }
    }

    fun setNavController(
        controller: NavHostController,
        lifecycle: Lifecycle,
    ) {
        logger.d { "Setting nav controller" }
        navController = controller
        gatingLifecycle = lifecycle
        processingJob?.cancel()
        processingJob = appScope.launch {
            controller.awaitGraphAttachment()
            navigationRequests
                .receiveAsFlow()
                .observeWithLifecycle(lifecycle = lifecycle, tag = "navigation queue") { command ->
                    command(controller)
                    watchdog.drained()?.let { stuckFor ->
                        logger.i { "Navigation queue is draining again after $stuckFor stuck" }
                    }
                    queueHasWork.value = watchdog.isWaiting
                }
        }
    }

    /**
     * Says the queue has stopped moving, at error level so it reaches Sentry.
     *
     * The two lifecycle readings are the whole reason this is worth logging, and
     * they answer different questions.
     *
     * The host's state says whether the drain here is gated shut. But a screen
     * can go dead with the host at RESUMED, because a feature's screen state and
     * its events are collected against its **`NavBackStackEntry`** lifecycle
     * (`collectAsStateWithLifecycle` and `ObserveEvents` both read
     * `LocalLifecycleOwner`, which inside a destination is the entry). An entry
     * pinned below STARTED by a transition that never completed stops the board
     * redrawing and stops its events being delivered, while touches, logging and
     * the shake detector all carry on, because those hang off the host. That is
     * the shape SD-26 was reported in and there has never been a reading of it.
     *
     * So the back stack goes in the report entry by entry. Which one is stuck,
     * and at what state, is the difference between looking at the host and
     * looking at `FloatingWindowHost`.
     */
    private fun reportStall(stall: NavigationQueueWatchdog.Stall) {
        val host = gatingLifecycle?.currentState
        val entries = navController
            ?.currentBackStack
            ?.value
            ?.joinToString { entry ->
                "${entry.destination.route?.substringAfterLast('.') ?: "?"}=${entry.lifecycle.currentState}"
            }
            ?: "controller absent"
        logger.e {
            "Navigation queue has not moved for ${stall.idle} with ${stall.depth} command(s) waiting. " +
                "Host lifecycle is $host. Back stack: $entries"
        }
    }

    override fun navigate(route: Route, options: NavigationOptions) {
        // Track the navigation if the route is trackable
        if (route is TrackableRoute) {
            appScope.launch { navigationTracker.onNavigate(route) }
        }
        
        enqueueNavigation(
            description = "navigate to ${route.nameForLogs()}",
            route = route,
        ) {
            navigate(route) {

                if (options.clearBackStack) {
                    popUpTo(0) { inclusive = true }
                }
                if (options.launchSingleTop) {
                    launchSingleTop = true
                }
                if (options.restoreState) {
                    restoreState = true
                }
            }
        }
    }

    override fun goBack() {
        enqueueNavigation("go back") {
            popBackStack()
        }
    }

    override fun popBackTo(route: Route, inclusive: Boolean) {
        enqueueNavigation("popBackTo ${route.nameForLogs()}") {
            popBackStack(route, inclusive)
        }
    }

    /**
     * See [NavigationRecovery.drainQueueNow]. Takes the same channel the gated
     * drain takes from, so the two cannot both run a command: `tryReceive` hands
     * each one to exactly one of them.
     *
     * On the main dispatcher because a `NavHostController` may only be touched
     * there, and the caller is a pointer handler that has no business blocking.
     *
     * Failures are caught per command rather than per batch. A command that
     * throws is one command; letting it take the other four with it would turn a
     * recovery into a second fault.
     */
    override fun drainQueueNow() {
        appScope.launch {
            withContext(Dispatchers.Main) {
                val controller = navController ?: run {
                    logger.i { "Recovery drain asked for with no controller attached; nothing to do" }
                    return@withContext
                }
                val applied = navigationRequests.drainInto(controller)
                if (applied > 0) {
                    logger.w {
                        "Recovery drained $applied queued navigation(s) past a host lifecycle of " +
                            "${gatingLifecycle?.currentState}. The host said the view was not on " +
                            "screen and a touch proved otherwise."
                    }
                    watchdog.drained()
                    queueHasWork.value = watchdog.isWaiting
                }
            }
        }
    }

    override fun openWebLink(url: String) {
        webLinkLauncher
            .open(url)
            .logOnFailure("Failed to open web link: $url")
            .throwIfDebug()
    }
    @Composable
    fun Bind(navController: NavHostController) {
        logger.i { "Binding nav controller" }
        val lifecycleOwner = LocalLifecycleOwner.current
        val controllerKey = remember(navController) { navController }

        DisposableEffect(controllerKey, lifecycleOwner) {
            setNavController(controllerKey, lifecycleOwner.lifecycle)
            onDispose { clearNavController(controllerKey) }
        }
    }

    private fun enqueueNavigation(
        description: String,
        route: Route? = null,
        block: NavHostController.() -> Unit,
    ) {
        logger.d { "Enqueuing navigation: $description" }
        // Counted before the send rather than after, because an unlimited
        // channel's `trySend` cannot fail and a command counted after a send
        // could be drained before it was ever counted, which would push the
        // depth negative and retire the watchdog.
        watchdog.enqueued()
        queueHasWork.value = true
        navigationRequests.trySend {
            if (route != null && shouldBlockNavigation(route)) {
                logger.w { "Blocked navigation '$description' because a blocking error is active" }
                return@trySend
            }
            Catching { block() }
                .logOnFailure("Navigation failure: $description")
                .throwIfDebug()
        }
    }

    private fun NavHostController.shouldBlockNavigation(route: Route): Boolean {
        if (!isBlockingErrorActive()) return false
        if (route is NavigableWhileBlocked) return false
        KLog.i { "Blocking Navigation for route $route" }
        return true
    }

    private fun NavHostController.isBlockingErrorActive(): Boolean {
        val destination = currentBackStackEntry?.destination ?: return false
        return destination.hasRoute<BlockingErrorRoute>()
    }

    private fun Route.nameForLogs(): String =
        this::class.simpleName ?: this::class.qualifiedName ?: toString()

    private suspend fun NavHostController.awaitGraphAttachment() {
        withContext(Dispatchers.Main.immediate) {
            currentBackStackEntryFlow.first()
            delay(100)
        }
    }
}

/**
 * How often the watchdog looks, while there is anything to look at.
 *
 * A poll rather than a scheduled deadline because a stall has no event to hang
 * one off: the whole shape of the fault is that nothing happens. One wakeup a
 * second, only while commands are outstanding, and none at all on an app that
 * is navigating normally.
 */
private val StallPollInterval = 1.seconds

/**
 * Takes every command currently queued and applies it to [target], returning
 * how many ran.
 *
 * Its own function so the loop can be tested without a `NavHostController`,
 * which needs a Compose host and an Android runtime and would turn three
 * assertions into an instrumentation test.
 *
 * `tryReceive` rather than `receive` so this never waits: the queue as it
 * stands at the moment of the call is the whole job, and a command enqueued
 * while this runs belongs to whoever drains next.
 *
 * One command's failure is caught and the rest still run. A recovery that
 * stopped on the first bad command would leave the queue half drained, which is
 * the state it was called to get out of.
 */
internal fun <T> Channel<T.() -> Unit>.drainInto(target: T): Int {
    var applied = 0
    while (true) {
        val command = tryReceive().getOrNull() ?: break
        Catching { command(target) }
            .logOnFailure { "A queued navigation failed during recovery drain" }
        applied++
    }
    return applied
}
