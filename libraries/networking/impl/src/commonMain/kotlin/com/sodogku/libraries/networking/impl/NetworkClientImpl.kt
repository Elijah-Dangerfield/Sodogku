package com.sodogku.libraries.networking.impl

import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.networking.AccessDeniedBus
import com.sodogku.libraries.networking.ClientHeaders
import com.sodogku.libraries.networking.InternalNetworkingApi
import com.sodogku.libraries.networking.ClientHeadersProvider
import com.sodogku.libraries.networking.NetworkClient
import com.sodogku.libraries.networking.NetworkConfig
import com.sodogku.libraries.networking.NetworkJson
import com.sodogku.libraries.networking.NetworkReachability
import com.sodogku.libraries.networking.platformHttpEngineFactory
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
@OptIn(InternalNetworkingApi::class)
class NetworkClientImpl(
    private val config: NetworkConfig,
    private val headersProvider: ClientHeadersProvider,
    private val reachability: NetworkReachability,
    private val accessDeniedBus: AccessDeniedBus,
) : NetworkClient {

    override val client: HttpClient by lazy {
        HttpClient(platformHttpEngineFactory) {
            applyCommonConfig(config, headersProvider, reachability, accessDeniedBus)
            // WebSocket plugin so callers can open sockets through the same
            // client. The plugin is additive — existing HTTP calls don't
            // notice it. Keepalive is per-engine; see
            // installWebSocketKeepalive for the OkHttp trap.
            installWebSocketKeepalive()
            if (BuildInfo.isDebug) {
                // Wiretap's WS capture, so every sent/received frame plus
                // connect/close lands in the same inspector as HTTP. Installed
                // AFTER WebSockets so it can wrap the raw session before
                // WebSockets transforms it. Debug-only + noop in release.
                installWebSocketInspector()
            }
        }
    }
}

private fun HttpClientConfig<*>.applyCommonConfig(
    config: NetworkConfig,
    headersProvider: ClientHeadersProvider,
    reachability: NetworkReachability,
    accessDeniedBus: AccessDeniedBus,
) {
    install(ContentNegotiation) {
        json(NetworkJson)
    }
    // Witnessed reachability: a response (any status, even 4xx/5xx) means the
    // round-trip worked; a failure *without* a response (timeout / IO / DNS /
    // captive portal) means it didn't. This is what lets the offline banner
    // reflect "actually online" rather than just the OS's "there's a path."
    HttpResponseValidator {
        validateResponse { reachability.reportReachable() }
        handleResponseExceptionWithRequest { cause, _ ->
            // A ResponseException means the server answered (a 4xx/5xx) — the
            // network is fine. Anything else never reached the server.
            if (cause !is ResponseException) {
                reachability.reportUnreachable()
                return@handleResponseExceptionWithRequest
            }
            if (cause.response.status == HttpStatusCode.Forbidden) {
                signalAccessDeniedIfEnveloped(cause.response, accessDeniedBus)
            }
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMillis
        connectTimeoutMillis = config.requestTimeoutMillis
        socketTimeoutMillis = config.requestTimeoutMillis
    }
    install(DefaultRequest) {
        if (config.baseUrl.isNotBlank()) url(config.baseUrl)
        headers.append(HttpHeaders.Accept, "application/json")
        headers.append(HttpHeaders.ContentType, "application/json")
        // Per-request: re-read from the provider on every call so locale
        // changes flow through immediately. The provider caches its
        // build-info bits, so this is cheap.
        val h = headersProvider.current()
        headers.append(HttpHeaders.AcceptLanguage, h.acceptLanguage)
        headers.append(ClientHeaders.HEADER_PLATFORM, h.platform)
        headers.append(ClientHeaders.HEADER_APP_VERSION, h.appVersion)
        headers.append(ClientHeaders.HEADER_BUILD_NUMBER, h.buildNumber)
        h.countryCode?.let { headers.append(ClientHeaders.HEADER_COUNTRY_CODE, it) }
        h.installId?.let { headers.append(ClientHeaders.HEADER_INSTALL_ID, it) }
        headers.append(ClientHeaders.HEADER_SESSION_ID, h.sessionId)
    }
    if (BuildInfo.isDebug) {
        // WiretapKMP — captures every request/response through this client
        // into the on-device inspector (shake → "Network inspector"). Debug
        // builds link the real plugin; release builds link the noop (and
        // never enter this branch anyway). Applied to both the plain and
        // authenticated clients since they share this config. Platform-gated
        // (see installNetworkInspector) so host-JVM unit tests, where
        // Wiretap's DI isn't bootstrapped, don't install + crash on it.
        installNetworkInspector()
    }
    expectSuccess = true
}

/**
 * Server's `403 AccessDeniedResponse` wire shape — duplicated here (not shared)
 * because the server class lives in `:apps:server` which the client must not
 * depend on. Locked machine-readable data only, no copy: the client localizes
 * off [reason]. `internal` so the impl module's test can construct an
 * envelope-shaped response without re-deriving the shape; the public surface
 * outside the module is [AccessDeniedBus.Denial].
 */
@Serializable
internal data class AccessDeniedWire(
    val reason: String,
    val until: String? = null,
    val appealUrl: String? = null,
)

/**
 * On a `403`, attempt to read the response body as the locked [AccessDeniedWire]
 * envelope and signal the access-denied bus. We don't throw or swallow — the
 * caller's existing failure flow gets the same `ClientRequestException` it
 * always did. A 403 *without* our envelope (a route-level deny, an upstream
 * proxy 403) doesn't decode and is skipped silently — the [Catching] wrap is
 * the safety net there.
 *
 * Run from `handleResponseExceptionWithRequest` rather than `validateResponse`
 * because the `ResponseException` Ktor throws after validation holds a *saved*
 * copy of the response — reading the body here doesn't race the caller's own
 * body decoding (callers like `RoomRepositoryImpl` map 403 → `NotHost` off the
 * status alone and never re-read the body anyway).
 */
internal suspend fun signalAccessDeniedIfEnveloped(
    response: HttpResponse,
    accessDeniedBus: AccessDeniedBus,
) {
    Catching { response.body<AccessDeniedWire>() }
        .onSuccess { wire ->
            accessDeniedBus.signalDenied(
                AccessDeniedBus.Denial(
                    reason = wire.reason,
                    until = wire.until,
                    appealUrl = wire.appealUrl,
                ),
            )
        }
}
