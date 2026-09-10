package com.sodogku.server.domain

import com.sodogku.server.http.ClientContext
import kotlinx.serialization.json.JsonObject

/**
 * Where the app-config tree comes from. Today a Postgres-backed source
 * (`PostgresAppConfigSource`); routes depend on this interface, not the
 * concrete implementation, so swapping the source is a one-line DI change.
 *
 * Reads are scoped to the requesting client via [ClientContext] alone. That's
 * how per-flag targeting and staged rollouts resolve server-side without the
 * client model ever changing: the endpoint returns a *resolved* override tree
 * keyed by `ConfiguredValue.path` and the client merges it over its defaults.
 * The axes are platform, app version, country, locale, and install-id rollout
 * bucketing. There is no user axis, because there are no users.
 */
fun interface AppConfigSource {
    /** Returns the resolved config tree for this caller, keyed by ConfiguredValue.path. */
    suspend fun read(context: ClientContext): JsonObject
}
