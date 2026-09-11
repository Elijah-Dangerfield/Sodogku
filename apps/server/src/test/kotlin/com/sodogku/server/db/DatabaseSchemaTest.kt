package com.sodogku.server.db

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.vendors.currentDialect
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Schema drift test: proves Flyway created each table *and* that the Exposed
 * projection in `Tables.kt` declares exactly the columns the migrated schema
 * has. The column names come from JDBC metadata, so a rename or a drop on
 * either side is a set mismatch naming the offending column.
 *
 * Reading every column of every row on top of that proves the declared Kotlin
 * types can decode what is actually stored, which comparing names alone cannot
 * see.
 *
 * Says nothing about row counts on purpose. `V4` seeds the kill-switch trio and
 * the other three tables migrate in empty, so a count assertion here would only
 * encode today's seed and go stale the next time someone edits it.
 *
 * Add a line to [PROJECTIONS] per new table.
 */
class DatabaseSchemaTest : DatabaseTest() {

    @Test
    fun projectionsMatchMigratedColumns() = runTest {
        database.transaction {
            PROJECTIONS.forEach { table ->
                assertEquals(
                    table.columns.map { it.name }.toSet(),
                    migratedColumnNames(table),
                    "${table.tableName}: Exposed projection and migrated schema disagree",
                )
                table.selectAll().forEach { row -> table.columns.forEach { row[it] } }
            }
        }
    }

    private fun migratedColumnNames(table: Table): Set<String> =
        currentDialect.tableColumns(table)[table]
            .orEmpty()
            .map { it.name }
            .toSet()

    private companion object {
        val PROJECTIONS = listOf(
            AppConfigValuesTable,
            AppConfigRulesTable,
            AppConfigAuditTable,
            AppConfigManifestTable,
        )
    }
}
