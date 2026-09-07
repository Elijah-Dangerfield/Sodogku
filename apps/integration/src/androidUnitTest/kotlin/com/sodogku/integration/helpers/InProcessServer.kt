package com.sodogku.integration.helpers

import com.sodogku.server.config.DatabaseConfig
import com.sodogku.server.db.Database
import com.sodogku.server.di.ServerComponent
import com.sodogku.server.di.create
import com.sodogku.server.installApp
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

/**
 * A real Ktor server — the production plugins + routes via the same [installApp]
 * seam production boots through, a real [ServerComponent] over a real Postgres
 * (Testcontainers) — bound to an ephemeral port.
 *
 * Bound to a real port on purpose: the production client builds its own Ktor
 * engine and talks over real TCP, which Ktor's in-memory `testApplication`
 * engine can't serve.
 *
 * Sodogku has no accounts, so there is no auth plugin here and nothing to seed.
 * The one surface worth driving end to end is remote config, which is exactly
 * what the harness exists to prove.
 *
 * The Postgres container + migrated [Database] are shared across the whole JVM
 * (starting Postgres costs seconds; per-test containers would make the suite
 * unusable — same reasoning as the server's own `DatabaseTest`). Each
 * [InProcessServer] gets a fresh engine + a fresh [ServerComponent]; tests
 * isolate by using unique flag paths rather than by wiping tables.
 */
class InProcessServer : AutoCloseable {

    private val engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0) {
            installApp(component = ServerComponent::class.create(sharedDatabase(), null))
        }.start(wait = false)

    private val boundPort: Int = runBlocking {
        engine.engine.resolvedConnectors().first().port
    }

    /** The base URL a client's `NetworkConfig` should point at. */
    val baseUrl: String get() = "http://127.0.0.1:$boundPort"

    /**
     * Seed a remote-config base value the client should read back. Raw JDBC
     * because Exposed's DSL lives on the server's `implementation` classpath
     * and isn't visible here — one upsert doesn't justify re-declaring the
     * dependency.
     */
    fun seedConfigValue(path: String, valueJson: String) {
        val container = sharedContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
            .use { connection ->
                connection.prepareStatement(
                    "INSERT INTO app_config_values (path, value_jsonb, updated_at) " +
                        "VALUES (?, ?::jsonb, now()) " +
                        "ON CONFLICT (path) DO UPDATE SET value_jsonb = EXCLUDED.value_jsonb",
                ).use { statement ->
                    statement.setString(1, path)
                    statement.setString(2, valueJson)
                    statement.executeUpdate()
                }
            }
    }

    override fun close() {
        engine.stop(0, 0)
    }

    companion object {
        private const val POSTGRES_IMAGE = "postgres:16-alpine"

        private val shared by lazy {
            val container = PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("sodogku_integration")
                .withUsername("sodogku")
                .withPassword("sodogku")
                .also { it.start() }
            val database = Database.connect(
                DatabaseConfig(
                    jdbcUrl = container.jdbcUrl,
                    username = container.username,
                    password = container.password,
                    poolMaxSize = 4,
                    poolMinIdle = 1,
                ),
            )
            container to database
        }

        private fun sharedContainer(): PostgreSQLContainer<*> = shared.first
        private fun sharedDatabase(): Database = shared.second

        /**
         * Skip (JUnit `Assume`) rather than fail when Docker isn't reachable,
         * so contributors without Docker still get a green build — mirrors the
         * server's `DatabaseTest`.
         */
        fun assumeDockerAvailable() {
            Assume.assumeTrue(
                "Docker is not available; skipping integration tests",
                try {
                    DockerClientFactory.instance().client().pingCmd().exec()
                    true
                } catch (_: Throwable) {
                    false
                },
            )
        }
    }
}
