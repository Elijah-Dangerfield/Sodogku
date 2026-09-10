package com.sodogku.libraries.networking

/**
 * Marks low-level network surfaces (raw [HttpClient][io.ktor.client.HttpClient]
 * accessors on [NetworkClient]) that callers should NOT reach for under normal
 * use. The blessed paths are [NetworkClient.networkCall] and
 * [NetworkClient.webSocketCall] — they handle retry and structured logging so
 * every request flows through one code path.
 *
 * Direct HttpClient access bypasses those guarantees. The annotation exists
 * so the compiler tells you about that bypass at the call site. Test
 * fakes opt in via `@OptIn(InternalNetworkingApi::class)`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Direct HttpClient access skips networkCall's contract (retry policy, " +
        "logging). Use NetworkClient.networkCall / webSocketCall instead. " +
        "Test fakes can opt in.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FUNCTION)
annotation class InternalNetworkingApi
