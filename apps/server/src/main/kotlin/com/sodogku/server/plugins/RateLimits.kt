package com.sodogku.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.header
import io.ktor.server.request.path
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting. A global per-IP bucket guards every route; named buckets are
 * registered for routes that want a tighter cap, opted into with
 * `rateLimit(RateLimitName(PROFILE_WRITE_LIMIT)) { … }` around the route.
 *
 * Keying is per-IP. There is no auth plugin and no per-user identity to key on,
 * so per-IP is the only option as well as the simplest. `/_health` is excluded
 * so health probes don't drain the bucket. Limits are deliberately loose: they
 * catch hot loops and trivial abuse, not concerted DoS (your edge/CDN owns
 * that).
 *
 * [PROFILE_WRITE_LIMIT] is currently registered but unclaimed. The route it was
 * written for went away with accounts in C0; it is kept as the worked example
 * of a named bucket.
 */
const val PROFILE_WRITE_LIMIT = "profile-write"

fun Application.installRateLimits() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 600, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
            requestWeight { call, _ -> if (call.request.path().startsWith("/_health")) 0 else 1 }
        }

        register(RateLimitName(PROFILE_WRITE_LIMIT)) {
            // A tighter cap for a write with a server-side uniqueness
            // constraint, so squatting bots are expensive while a real caller
            // still has plenty of retries.
            rateLimiter(limit = 30, refillPeriod = 1.hours)
            requestKey { call -> call.clientIp() }
        }
    }
}

/**
 * Best-effort client IP. Trusts the standard reverse-proxy headers (Fly's
 * `Fly-Client-IP` first, then `X-Forwarded-For`, then the socket). Order
 * matters: the wrong choice makes everyone share the edge IP and the limiter
 * degenerates to one global bucket.
 */
internal fun io.ktor.server.application.ApplicationCall.clientIp(): String {
    request.header("Fly-Client-IP")?.takeIf { it.isNotBlank() }?.let { return it }
    request.header("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim()
        ?.takeIf { it.isNotBlank() }?.let { return it }
    return request.local.remoteHost
}
