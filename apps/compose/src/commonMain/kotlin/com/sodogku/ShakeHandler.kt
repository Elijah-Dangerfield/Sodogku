package com.sodogku

import com.sodogku.libraries.core.ShakeDetector
import com.sodogku.libraries.navigation.Router
import com.sodogku.libraries.navigation.ShakeDialogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Turns a shake into the report dialog.
 *
 * [start] and [stop] are driven by the app's visibility, not by its process
 * lifetime — see the `LifecycleStartEffect` in `App.kt`. That matters more than
 * it looks: the router only executes queued navigation while the app is at least
 * STARTED, so a shake detected in a backgrounded app used to sit in that queue
 * and open the dialog the moment the player came back. Listening exactly while
 * the router can act means a shake is either handled now or not detected at all.
 *
 * [isDialogOnScreen] is set by the dialog destination itself
 * (see `ShakeDialogEntryPoint`), never by the act of asking to navigate. A flag
 * set at navigate time is a latch: a navigation that is dropped — the router
 * refuses to move while a blocking error screen is up — would leave it stuck on
 * with no dialog to turn it back off, and the gesture would be dead for the rest
 * of the process.
 */
@Inject
@SingleIn(AppScope::class)
class ShakeHandler(
    private val shakeDetector: ShakeDetector,
    private val router: Router,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collection: Job? = null
    private var isDialogOnScreen = false

    fun start() {
        if (collection?.isActive == true) return
        collection = scope.launch {
            shakeDetector.shakeEvents.collect { onShake() }
        }
        shakeDetector.start()
    }

    fun stop() {
        shakeDetector.stop()
        collection?.cancel()
        collection = null
    }

    fun onDialogShown() {
        isDialogOnScreen = true
    }

    fun onDialogDismissed() {
        isDialogOnScreen = false
    }

    private fun onShake() {
        if (isDialogOnScreen) return
        router.navigate(ShakeDialogRoute())
    }
}
