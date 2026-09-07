package com.sodogku.server.routes

import com.sodogku.server.domain.AppConfigSource
import com.sodogku.server.http.clientContext
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /v1/app-config` — returns the resolved override tree keyed by ConfiguredValue.path.
 *
 * The source resolves the tree against the calling client: [clientContext] (platform,
 * app version, country, locale, install id). That's what powers per-flag targeting +
 * staged rollouts server-side — the client just merges whatever tree it gets over its
 * defaults. Sodogku has no accounts, so the install id is the rollout bucketing key.
 *
 * Empty object is a legitimate response — it means "use client defaults". The client
 * always has safe defaults declared in its ConfiguredValue classes, so an empty server config
 * still produces a fully functional app.
 */
fun Route.appConfigRoutes(source: AppConfigSource) {
    get("/v1/app-config") {
        call.respond(source.read(call.clientContext()))
    }
}
