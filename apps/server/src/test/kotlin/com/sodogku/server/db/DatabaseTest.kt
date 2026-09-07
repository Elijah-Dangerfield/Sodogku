package com.sodogku.server.db

import com.sodogku.server.config.DatabaseConfig
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base class for tests that need a real Postgres. Spins up a single
 * Testcontainers Postgres for the whole test class (`@BeforeClass`), runs Flyway
 * migrations through the production [Database.connect] path, and exposes a
 * [Database] handle.
 *
 * Why class-level (not per-test) containers: starting Postgres costs ~3s cold;
 * per-test containers would make the suite unusable. Tests in a class don't
 * share state — clean tables in `@After` or use unique data per test.
 *
 * If Docker isn't reachable the suite is skipped (JUnit `Assume`) rather than
 * failing red, so contributors without Docker still get a green build.
 *
 * ```
 * class MyRepoTest : DatabaseTest() {
 *     @Test fun something() = runTest {
 *         database.transaction { … }
 *     }
 * }
 * ```
 */
abstract class DatabaseTest {

    protected val database: Database
        get() = sharedDatabase ?: error("Database not initialized; @BeforeClass must run")

    companion object {
        private const val POSTGRES_IMAGE = "postgres:16-alpine"

        private var container: PostgreSQLContainer<*>? = null
        private var sharedDatabase: Database? = null

        @JvmStatic
        @BeforeClass
        fun startPostgres() {
            Assume.assumeTrue(
                "Docker is not available; skipping Postgres integration tests",
                isDockerAvailable(),
            )
            val c = PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("template_test")
                .withUsername("template")
                .withPassword("template")
                .also { it.start() }
            container = c
            sharedDatabase = Database.connect(
                DatabaseConfig(
                    jdbcUrl = c.jdbcUrl,
                    username = c.username,
                    password = c.password,
                    poolMaxSize = 4,
                    poolMinIdle = 1,
                ),
            )
        }

        private fun isDockerAvailable(): Boolean = try {
            DockerClientFactory.instance().client().pingCmd().exec()
            true
        } catch (_: Throwable) {
            false
        }

        @JvmStatic
        @AfterClass
        fun stopPostgres() {
            sharedDatabase?.close()
            sharedDatabase = null
            container?.stop()
            container = null
        }
    }
}
