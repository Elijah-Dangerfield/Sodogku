package com.sodogku.libraries.flowroutines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import com.sodogku.libraries.core.logging.KLog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sodogku.libraries.flowroutines.SEAViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Observe a flow with the lifecycle of the current composable
 * @param flow the flow to observe
 * @param onItem the action to take when an item is emitted from the flow
 *
 * Observes on main immediate which ensures no emissions are missed
 */
@Composable
fun <T> ObserveWithLifecycle(flow: Flow<T>, tag: String = "flow", onItem: suspend (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner.lifecycle, flow) {
        flow.collectLogging(
            tag = tag,
            lifecycle = lifecycleOwner.lifecycle,
            state = Lifecycle.State.STARTED,
            onItem = onItem,
        )
    }
}

@Composable
fun <T : Any> SEAViewModel<*, T, *>.ObserveEvents(onItem: suspend (T) -> Unit) {
    ObserveWithLifecycle(eventFlow, tag = "${this::class.simpleName} events", onItem = onItem)
}

/**
 * Observe a flow with the provided lifecycle and scope
 * @param onItem the action to take when an item is emitted from the flow
 *
 * starts collection when the lifecycle reaches the started state,
 * stops collection when the lifecycle falls below the started state,
 *
 * Observes on main immediate which ensures no emissions are missed
 */
 fun <T> Flow<T>.observeWithLifecycleIn(
    lifecycleOwner: Lifecycle,
    scope: CoroutineScope,
    tag: String = "flow",
    onItem: suspend (T) -> Unit
) {
    scope.launch {
        collectLogging(
            tag = tag,
            lifecycle = lifecycleOwner,
            state = Lifecycle.State.STARTED,
            onItem = onItem,
        )
    }
}

/**
 * Observe a flow with the provided lifecycle
 * @param onItem the action to take when an item is emitted from the flow
 *
 * starts collection when the lifecycle reaches the started state,
 * stops collection when the lifecycle falls below the started state,
 *
 * Observes on main immediate which ensures no emissions are missed
 */
/**
 * Says out loud when lifecycle-gated collection starts and stops.
 *
 * Every one of these is a place the app goes quiet without going wrong: below
 * STARTED the collector detaches, the producer keeps producing, and nothing in
 * the logs says so. Chasing a stall on 2026-09-09 the only way to tell whether
 * the lifecycle had dropped was to infer it from what had *stopped* appearing,
 * which is a slow way to learn something the framework already knows.
 *
 * [tag] names the caller so two collectors on one screen are tellable apart.
 */
private suspend fun <T> Flow<T>.collectLogging(
    tag: String,
    lifecycle: Lifecycle,
    state: Lifecycle.State,
    onItem: suspend (T) -> Unit,
) {
    lifecycle.repeatOnLifecycle(state) {
        KLog.i("Collection started for $tag (lifecycle reached $state)")
        try {
            withContext(Dispatchers.Main.immediate) {
                collect(onItem)
            }
        } finally {
            KLog.i("Collection stopped for $tag (lifecycle fell below $state)")
        }
    }
}

suspend fun <T> Flow<T>.observeWithLifecycle(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    lifecycle: Lifecycle,
    tag: String = "flow",
    onItem: suspend (T) -> Unit
) {
    collectLogging(tag = tag, lifecycle = lifecycle, state = state, onItem = onItem)
}