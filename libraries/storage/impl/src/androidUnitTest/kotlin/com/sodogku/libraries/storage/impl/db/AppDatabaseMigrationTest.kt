package com.sodogku.libraries.storage.impl.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A player's history survives a schema bump.
 *
 * This is the test whose failure would mean shipping a release that deletes
 * people's saves. Sodogku has no accounts and no cloud copy — the campaign
 * records, the daily streak and every badge exist in exactly one place, a file
 * on the phone — so a bad migration is not recoverable by the player or by us.
 *
 * The database went 6 → 7 → 8 in a single day while it was still pre-release,
 * under `fallbackToDestructiveMigration(dropAllTables = true)`, which silently
 * dropped everything on each bump. Free then, unrecoverable after the first
 * store build.
 *
 * It builds a **genuine v6 database** — the tables exactly as `6.json` declares
 * them, with a row in `level_progress` — and runs the real generated migrations
 * over it. The DDL is written out here rather than read from `6.json`, so the
 * fixture and the migrations have independent origins: `MigrationTestHelper`
 * would read the same exported schema the migrations were generated *from*, and
 * a mistake in the export would be invisible to it.
 *
 * **What it does not cover.** Room's own version dispatch — deciding which
 * migrations to run for a given `user_version` — is exercised here only in the
 * sense that these are the migrations it would pick. That dispatch is Room's
 * code rather than ours. What *is* ours, and is the other thing that could wipe
 * a database, is which fallback the builder is configured with, and
 * [theBuilderDoesNotFallBackDestructivelyFromAShippedVersion] pins that.
 */
class AppDatabaseMigrationTest {

    private lateinit var dir: File
    private val dbPath: String get() = File(dir, "sodogku.db").absolutePath

    @BeforeTest
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "sodogku-migration-${System.nanoTime()}")
        dir.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun aVersionSixDatabaseKeepsItsLevelRecords() {
        writeVersionSixDatabase(
            "INSERT INTO level_progress VALUES " +
                "(40, 'Completed', 12345, 3, 61000, 2, 1700000000000, 1700000001000)",
        )

        migrateToCurrent()

        val row = query("SELECT state, bestScore, bestPaws, bestTimeMs, attempts " +
            "FROM level_progress WHERE levelId = 40") { stmt ->
            assertTrue(stmt.step(), "the row is gone, which means a release deleted someone's campaign")
            listOf(stmt.getText(0), stmt.getInt(1), stmt.getInt(2), stmt.getLong(3), stmt.getInt(4))
        }

        assertEquals(listOf<Any>("Completed", 12345, 3, 61000L, 2), row)
    }

    @Test
    fun everyTableAddedSinceVersionSixArrives() {
        // The other half. A migration that dropped and recreated everything
        // would pass the test above on an empty database, so this proves the new
        // tables *arrived* rather than that the old one merely survived.
        writeVersionSixDatabase()

        migrateToCurrent()

        val tables = query("SELECT name FROM sqlite_master WHERE type = 'table'") { stmt ->
            buildList { while (stmt.step()) add(stmt.getText(0)) }
        }
        listOf("level_progress", "daily_result", "achievement_fact", "achievement_unlock").forEach {
            assertTrue(it in tables, "$it is missing after the upgrade; found $tables")
        }
    }

    @Test
    fun theFixtureReallyIsAtVersionSix() {
        // The guard against the guard. A fixture written at the current version
        // would make both tests above pass while proving nothing about
        // migration, because there would be nothing to migrate.
        writeVersionSixDatabase()

        val version = query("PRAGMA user_version") { stmt ->
            stmt.step()
            stmt.getInt(0)
        }

        assertEquals(VERSION_SIX, version)
    }

    @Test
    fun theBuilderDoesNotFallBackDestructivelyFromAShippedVersion() {
        // Read as source rather than exercised, because the alternative needs an
        // Android `Context` this source set has no way to produce. Crude, and it
        // pins the one line whose absence would make every test above pointless:
        // migrations that work are no help if the builder throws the database
        // away before reaching them.
        val provider = File(
            "src/commonMain/kotlin/com/sodogku/libraries/storage/impl/db/RealAppDatabaseProvider.kt",
        ).readText()

        assertTrue(
            "fallbackToDestructiveMigrationFrom(" in provider,
            "the builder must name the versions it may drop",
        )
        assertTrue(
            Regex("""fallbackToDestructiveMigration\s*\(""").find(provider) == null,
            "the unrestricted destructive fallback drops every table on any bump",
        )
        // The versions it is allowed to drop are template history no install has
        // ever run. Anything from FIRST_PLAYER_DATA_VERSION up holds a save.
        listOf("6", "7", "8").forEach {
            assertTrue(
                !Regex("""PRE_GAME_SCHEMA_VERSIONS\s*=\s*intArrayOf\([^)]*\b$it\b""").containsMatchIn(provider),
                "version $it holds player data and must never be droppable",
            )
        }
    }

    /**
     * Writes the schema exactly as `6.json` declares it, plus whatever [rows] the
     * test wants in it.
     */
    private fun writeVersionSixDatabase(vararg rows: String) {
        BundledSQLiteDriver().open(dbPath).use { connection ->
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `example_user_data` " +
                    "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `level_progress` (" +
                    "`levelId` INTEGER NOT NULL, `state` TEXT NOT NULL, `bestScore` INTEGER NOT NULL, " +
                    "`bestPaws` INTEGER NOT NULL, `bestTimeMs` INTEGER NOT NULL, " +
                    "`attempts` INTEGER NOT NULL, `firstCompletedAt` INTEGER NOT NULL, " +
                    "`lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`levelId`))",
            )
            connection.execSQL("PRAGMA user_version = $VERSION_SIX")
            rows.forEach { connection.execSQL(it) }
        }
    }

    /**
     * Runs every migration `AppDatabase` declares, in order.
     *
     * Listed rather than discovered, so adding version 9 without a migration
     * fails this file rather than passing quietly — which is the whole failure
     * mode being guarded against.
     */
    private fun migrateToCurrent() {
        val migrations: List<Migration> = listOf(
            AppDatabase_AutoMigration_6_7_Impl(),
            AppDatabase_AutoMigration_7_8_Impl(),
        )
        // Checked against the *exported schemas*, which Room writes on every
        // build, rather than against a constant in this file. The first version
        // of this compared two numbers that both lived here, so bumping
        // `@Database(version = …)` without adding a migration left it green —
        // which is precisely the mistake it exists to catch.
        assertEquals(
            AppDatabase.FIRST_PLAYER_DATA_VERSION + migrations.size,
            currentSchemaVersion(),
            "AppDatabase's version moved without a migration being added to this test",
        )
        BundledSQLiteDriver().open(dbPath).use { connection ->
            migrations.forEach { it.migrate(connection) }
            connection.execSQL("PRAGMA user_version = ${currentSchemaVersion()}")
        }
    }

    /** The highest schema Room has exported, which is `@Database(version = …)`. */
    private fun currentSchemaVersion(): Int {
        val schemas = File("schemas/com.sodogku.libraries.storage.impl.db.AppDatabase")
        assertTrue(schemas.isDirectory, "no exported schemas at ${schemas.absolutePath}")
        return schemas.listFiles()
            .orEmpty()
            .mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
            .max()
    }

    private fun <T> query(sql: String, block: (SQLiteStatement) -> T): T =
        BundledSQLiteDriver().open(dbPath).use { connection -> connection.prepare(sql).use(block) }

    private inline fun <T> SQLiteConnection.use(block: (SQLiteConnection) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    private companion object {
        const val VERSION_SIX = 6
    }
}
