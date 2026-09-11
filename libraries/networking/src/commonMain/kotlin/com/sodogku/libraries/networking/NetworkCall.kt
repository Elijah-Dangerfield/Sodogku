package com.sodogku.libraries.networking

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.networking.retry.RetryPolicy
import com.sodogku.libraries.networking.retry.withRetry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder

private val networkCallLogger = KLog.withTag("NetworkCall")

/**
 * HTTP call wrapper. Hands the configured [HttpClient] to [block], wraps each
 * attempt in [Catching], and emits a structured failure log keyed by
 * [description].
 *
 * [description] is a short, stable identifier for the call — used as the
 * log message prefix. Pick `"inventory.sync"` or `"wallet.fetch"`, not a
 * sentence; logs aggregate by description.
 *
 * [retry] defaults to [RetryPolicy.None] — opting in to retry is explicit
 * so non-idempotent POSTs can't silently inherit a retry that
 * double-spends. See [RetryPolicy] header for the idempotency tradeoffs.
 *
 * Cancellation is preserved via [Catching] — `CancellationException` is
 * re-thrown rather than swallowed, and the retry loop's `delay` is
 * cancellable.
 */
@OptIn(InternalNetworkingApi::class)
suspend fun <T> NetworkClient.networkCall(
    description: String,
    retry: RetryPolicy = RetryPolicy.None,
    block: suspend (HttpClient) -> T,
): Catching<T> = withRetry(retry) {
    Catching { block(client) }
}.logFailure(description)

/**
 * WebSocket upgrade. Opens a [DefaultClientWebSocketSession] via Ktor's
 * `webSocketSession` builder; failure surfaces in the returned [Catching] and
 * the caller owns the session lifecycle from there.
 *
 * Retry isn't a parameter here — the reconnect-on-drop loop lives at a
 * higher layer (e.g. `ReconnectingRoomSocket`), where it can coordinate
 * with the WebSocket's lifecycle (close vs. error vs. backoff).
 */
@OptIn(InternalNetworkingApi::class)
suspend fun NetworkClient.webSocketCall(
    description: String,
    builder: HttpRequestBuilder.() -> Unit,
): Catching<DefaultClientWebSocketSession> = Catching {
    client.webSocketSession(block = builder)
}.logFailure(description)

private fun <T> Catching<T>.logFailure(description: String): Catching<T> = onFailure { throwable ->
    when {
        // Expected while the device has no route — no backend_unreachable
        // event: AppState already records the offline edge once via
        // net.offline_banner, so per-call events would only add noise.
        throwable.isOfflineError() ->
            networkCallLogger.i { "$description failed: device offline (${throwable.classifyForLog()})" }
        else -> {
            networkCallLogger.w(throwable) { "$description failed (${throwable.classifyForLog()})" }
            // A ResponseException means the backend answered (an HTTP status IS
            // reachability); anything else — timeout, DNS, refused connection —
            // is the "client couldn't reach us at all" class the app-event
            // taxonomy exists to catch (`docs/practices/app-events.md`).
            if (throwable !is ResponseException) {
                networkCallLogger.logEvent(
                    "net.backend_unreachable",
                    "operation" to description,
                    "error_kind" to throwable.classifyForLog(),
                )
            }
        }
    }
}

private fun Throwable.classifyForLog(): String = when (this) {
    is HttpRequestTimeoutException -> "timeout"
    is ResponseException -> "http ${response.status.value}"
    else -> this::class.simpleName ?: "unknown"
}
