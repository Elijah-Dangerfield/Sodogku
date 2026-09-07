package com.sodogku.server

import com.sodogku.server.config.AdminConfig
import com.sodogku.server.config.ServerConfig
import com.sodogku.server.data.InMemoryExampleSource
import com.sodogku.server.data.WebhookConfigChangeNotifier
import com.sodogku.server.db.Database
import com.sodogku.server.di.ServerComponent
import com.sodogku.server.di.create
import com.sodogku.server.domain.ConfigChangeNotifier
import com.sodogku.server.plugins.installAdminWeb
import com.sodogku.server.plugins.installCors
import com.sodogku.server.plugins.installHttpServerTracing
import com.sodogku.server.plugins.installObservability
import com.sodogku.server.plugins.installOpenTelemetry
import com.sodogku.server.plugins.installRateLimits
import com.sodogku.server.plugins.installSentry
import com.sodogku.server.plugins.installSerialization
import com.sodogku.server.plugins.installStatusPages
import com.sodogku.server.plugins.installWebSockets
import com.sodogku.server.routes.appConfigRoutes
import com.sodogku.server.routes.configAdminRoutes
import com.sodogku.server.routes.exampleRoutes
import com.sodogku.server.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

/**
 * Single source of truth for how the app boots. Stays small on purpose — the
 * plugins/ and routes/ packages own their concerns and this wires them together
 * in the right order.
 *
 *  - [module] does production-only setup (observability, the DB connection),
 *    builds the DI graph, and picks the JWT verification strategy, then
 *    delegates to [installApp].
 *  - [installApp] installs the functional plugins + mounts every route. It is
 *    the seam reused by full-stack tests: a test builds a [ServerComponent]
 *    against a Testcontainers database and passes a [JwtVerification.Static]
 *    verifier to exercise the real plugins + routes + DB.
 *
 * Graceful degradation: with no `DATABASE_URL` the server runs in limited mode
 * (health + example); with no `SUPABASE_URL` the authenticated `/v1/me` route
 * isn't mounted. Either way it boots — so you can clone and run with zero config.
 *
 * Order matters: serialization before status pages (so error envelopes encode),
 * auth after serialization (the 401 challenge writes a JSON body), CORS early.
 */
fun Application.module(config: ServerConfig) {
    val logger = LoggerFactory.getLogger("Bootstrap")
    logger.info("Booting server on ${config.http.host}:${config.http.port}")

    // Production-only observability, kept out of [installApp] so tests don't pay
    // for it. Sentry first (so later boot failures are captured), then OTel + HTTP
    // tracing (so subsequent plugins' spans export), then request logging.
    installSentry(config.sentry)
    val openTelemetry = installOpenTelemetry(config.observability)
    installHttpServerTracing(openTelemetry)
    installObservability()

    val database = config.database?.let {
        Database.connect(it).also { logger.info("Database connected and migrations applied") }
    }
    if (database == null) {
        logger.warn("DATABASE_URL not set — limited mode (no DB-backed routes). See apps/server/README.md.")
    }

    val component = database?.let { ServerComponent::class.create(it, config.supabase) }
    installApp(
        component = component,
        adminConfig = config.admin,
        configChangeNotifier = component?.let {
            WebhookConfigChangeNotifier(
                webhookUrl = config.configChange.webhookUrl,
                environment = config.observability.environment,
                scope = it.provideServerCoroutineScope(),
            )
        } ?: ConfigChangeNotifier {},
    )

    // The hosted admin console (static bundle at /admin). Outside installApp so
    // integration tests don't need a bundle on disk.
    installAdminWeb(config.admin.webDir)
}

/**
 * Installs the functional plugins + every route. Shared by production [module]
 * and full-stack tests (which pass a real [component] + a [JwtVerification.Static]).
 *
 * [component] is null only in limited mode (no `DATABASE_URL`); [verification] is
 * null only when Supabase isn't configured. Health + the example resource are
 * always served.
 */
fun Application.installApp(
    component: ServerComponent?,
    adminConfig: AdminConfig = AdminConfig(apiToken = null),
    configChangeNotifier: ConfigChangeNotifier = ConfigChangeNotifier {},
) {
    installSerialization()
    installCors()
    installRateLimits()
    installStatusPages()
    installWebSockets()

    routing {
        healthRoutes()
        exampleRoutes(component?.exampleSource ?: InMemoryExampleSource())
        if (component != null) {
            appConfigRoutes(component.appConfigSource)
            // Admin API is inert without a token: requireAdmin 401s every call
            // when ADMIN_API_TOKEN is unset, so mounting is gated for clarity,
            // not security.
            if (!adminConfig.apiToken.isNullOrBlank()) {
                configAdminRoutes(
                    config = adminConfig,
                    repository = component.appConfigAdminRepository,
                    manifestRepository = component.appConfigManifestRepository,
                    notifier = configChangeNotifier,
                )
            }
        }
    }
}
