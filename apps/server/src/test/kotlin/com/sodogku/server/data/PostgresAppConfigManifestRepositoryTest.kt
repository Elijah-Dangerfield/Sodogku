package com.sodogku.server.data

import com.sodogku.server.db.AppConfigManifestTable
import com.sodogku.server.db.DatabaseTest
import com.sodogku.server.domain.ManifestEntry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Storing what a build declares it can be configured with, against real
 * Postgres.
 *
 * A manifest is uploaded per app version, and CI uploads the same version more
 * than once, so the upload has to replace that version's rows rather than
 * append to them. An appending write looks correct until the second deploy of a
 * release, at which point the console shows every key twice and the second copy
 * may be stale. That is the claim the single test is built around: upload,
 * upload again with a different key set, and read back the second one.
 *
 * Two smaller promises ride along because they share the fixture. Versions list
 * newest first, which is the order the console renders without sorting. And
 * asking for no particular version answers with the newest captured one, which
 * is what the admin tool does on first load.
 *
 * This runs on a Testcontainers Postgres through the real migrations, and it
 * cleans up its own rows in a `finally` because the container is shared for the
 * whole JVM.
 *
 * ### Not here
 *
 * What a value is allowed to be is `ConfigValidationTest`, with no database.
 * Whether the uploaded key set agrees with the client's is
 * `ConfigManifestRegistryDriftTest` in `:apps:integration`.
 */
@OptIn(ExperimentalTime::class)
class PostgresAppConfigManifestRepositoryTest : DatabaseTest() {

    private val repo = PostgresAppConfigManifestRepository(database, Clock.System)

    @Test
    fun upsertReplacesVersion_andListsNewestFirst() = runTest {
        val v1 = 900_001
        val v2 = 900_002
        try {
            repo.upsertManifest(v1, "9.0.1", listOf(entry("social.enabled", "boolean", false)))
            // Re-uploading a version replaces its rows rather than appending.
            repo.upsertManifest(
                v1,
                "9.0.1",
                listOf(
                    entry("social.enabled", "boolean", false),
                    entry("upgrade.maintenanceMode", "string", "off"),
                ),
            )
            repo.upsertManifest(v2, "9.0.2", listOf(entry("social.enabled", "boolean", true)))

            val v1Entries = repo.getManifest(v1)
            assertEquals(2, v1Entries.size)

            val versions = repo.listVersions()
            assertEquals(listOf(v2, v1), versions.map { it.versionCode })
            assertEquals(2, versions.first { it.versionCode == v1 }.flagCount)

            // getManifest(null) returns the latest captured version.
            assertEquals(JsonPrimitive(true), repo.getManifest(null).single { it.path == "social.enabled" }.default)
        } finally {
            cleanup(v1, v2)
        }
    }

    private fun entry(path: String, type: String, default: Any) = ManifestEntry(
        path = path,
        type = type,
        default = if (default is Boolean) JsonPrimitive(default) else JsonPrimitive(default.toString()),
        description = null,
        allowedValues = null,
    )

    private suspend fun cleanup(vararg versions: Int) = database.transaction {
        versions.forEach { v -> AppConfigManifestTable.deleteWhere { versionCode eq v } }
    }
}
