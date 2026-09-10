package com.sodogku.libraries.networking

import io.ktor.client.HttpClient

/**
 * The app's one pre-configured [HttpClient]. Repos and data sources should NOT
 * touch [client] directly — it is annotated [InternalNetworkingApi] and exists
 * for the `:libraries:networking` helpers themselves. Use one of:
 *
 *  - [networkCall] — request/response
 *  - [webSocketCall] — WebSocket upgrade
 *
 * Both converge on the same code path: structured failure logging keyed by
 * `description`, plus an optional [retry.RetryPolicy] on the HTTP side.
 *
 * There is one client rather than a plain/authenticated pair because there are
 * no accounts and so no bearer token to attach. See "No accounts" in AGENTS.md.
 *
 * Wrap call-site decoding in `Catching { }` if you need to handle
 * deserialization separately from transport — Ktor throws on non-2xx +
 * network errors and the helpers above already wrap in [Catching].
 */
interface NetworkClient {
    @InternalNetworkingApi
    val client: HttpClient
}
