package com.sodogku.libraries.flowroutines

import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.DebugException
import com.sodogku.libraries.core.logging.KLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.CoroutineContext

interface DispatcherProvider {
    val io: CoroutineDispatcher

    val main: CoroutineDispatcher

    /**
     * Main dispatcher in `immediate` mode — runs synchronously if the caller
     * is already on the main thread, otherwise posts. Use this when a hop
     * onto Main shouldn't burn a frame (lifecycle-aware flow collection,
     * nav-graph attachment, anything that needs to land before the next
     * frame fires).
     */
    val mainImmediate: CoroutineDispatcher

    val default: CoroutineDispatcher

    val unconfined: CoroutineDispatcher
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val io: CoroutineDispatcher
        get() = Dispatchers.IO

    override val main: CoroutineDispatcher
        get() = Dispatchers.Main

    override val mainImmediate: CoroutineDispatcher
        get() = Dispatchers.Main.immediate

    override val default: CoroutineDispatcher
        get() = Dispatchers.Default

    override val unconfined: CoroutineDispatcher
        get() = Dispatchers.Unconfined
}

/**
 * The scope background work outlives a screen in: entitlement refreshes,
 * leaderboard writes, ad prefetches, queued navigation.
 *
 * ## Why it catches
 *
 * A [SupervisorJob] keeps one failed child from cancelling its siblings, which
 * is what people usually reach for it for. It does nothing about an exception
 * nobody catches: that goes to the platform's default handler, which on Android
 * kills the process. Every `appScope.launch` in the app was therefore one
 * uncaught throw away from ending the session, wherever the player happened to
 * be. On 2026-09-25 one did (`SODOGKU-T`): a lifecycle registration on the
 * wrong thread turned a background sign-in into a crash at launch.
 *
 * So failures are logged rather than fatal, and the logging is the point.
 * `KLog.e` carries the throwable to Sentry through `SentryLogTree`, so the
 * report that made that bug findable still arrives; what changes is that the
 * player keeps their board. In a debug build it rethrows, because locally the
 * loud version is the useful one. That is the same shape as
 * `Catching {}.logOnFailure().throwIfDebug()`, which is how the rest of the app
 * already treats a failure it cannot act on.
 *
 * What this is not is a licence to skip handling errors where they happen. A
 * handler this far out knows nothing except that something failed, so it cannot
 * retry, fall back, or tell anyone. Anything that can do better should still do
 * it at the call site.
 *
 * Cancellation never reaches here. The machinery treats it as normal completion
 * rather than as a failure, so there is nothing to filter out.
 */
@SingleIn(AppScope::class)
class AppCoroutineScope @Inject constructor(
    dispatcherProvider: DispatcherProvider
) : CoroutineScope {

    private val job = SupervisorJob()

    private val failures = CoroutineExceptionHandler { _, throwable ->
        KLog.e(throwable) { "An app-scope coroutine failed" }
        if (BuildInfo.isDebug) throw DebugException(throwable)
    }

    override val coroutineContext: CoroutineContext = job + dispatcherProvider.default + failures
}
