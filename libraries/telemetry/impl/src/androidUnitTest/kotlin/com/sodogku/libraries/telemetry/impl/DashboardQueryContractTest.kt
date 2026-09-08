package com.sodogku.libraries.telemetry.impl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The dashboards in `ops/grafana/` are held against the `logEvent` calls that
 * feed them.
 *
 * A Grafana panel that filters on `strikes_used` against an app that emits
 * `strikes` is not an error anywhere. Loki accepts the query, the panel renders,
 * and it renders **empty** — indistinguishable from "nobody has played yet".
 * Nothing in a build, a lint pass or a runtime test would have caught it, and
 * the person who finds it is whoever eventually asks the dashboard a question
 * and believes the blank answer.
 *
 * So both ends are read from the real artifacts. Every `event_name` and every
 * attribute referenced by a committed dashboard query has to appear in a real
 * `logEvent(...)` call somewhere in the source tree, and renaming either end
 * fails this test.
 *
 * **What it proves, precisely.** That the attribute key is *spelled* the same in
 * the query and at the emit site. It does not prove the value is meaningful, or
 * that the code path ever runs — a `logEvent` in dead code still counts as
 * emitted. That is a much smaller hole than the one it closes: the mistake
 * people make is a rename, and a rename is exactly what this catches.
 *
 * **Why a source scan and not a runtime assertion.** The emitters live in
 * `:features:game:impl`, `:libraries:ads:impl` and `:libraries:billing:impl`.
 * Only `:apps:*` may depend on an impl module, so no unit test anywhere can
 * construct a `GameViewModel` and watch what it emits. Text is what is left, and
 * the dashboards are text too, so both sides are read the same way.
 *
 * The LogQL reader is deliberately a small strict parser rather than a set of
 * loose regexes: it understands the stream selector, label filters and `unwrap`,
 * and it **throws** on anything else. A panel that reaches for `| json` or
 * `line_format` fails this test rather than slipping through with zero
 * extracted references, which is the failure mode that would make the whole
 * check vacuous.
 */
class DashboardQueryContractTest {

    private val dashboards: List<Dashboard> = readDashboards()
    private val emitted: Map<String, Map<String, List<String>>> = scanLogEventCalls()

    @Test
    fun everyQueriedEventIsActuallyEmitted() {
        val missing = dashboards.flatMap { dashboard ->
            dashboard.queries.flatMap { query ->
                query.events.filter { it !in emitted }.map { "${dashboard.file}: $it" }
            }
        }.distinct().sorted()

        assertTrue(
            missing.isEmpty(),
            "These dashboards query an event no logEvent call emits, so the panels are permanently " +
                "empty:\n" + missing.joinToString("\n") { "  $it" } +
                "\n\nEither the event is not wired up yet — say so in docs/BUILD-PLAN.md and drop " +
                "the panel — or the name drifted.",
        )
    }

    @Test
    fun everyQueriedAttributeIsEmittedOnTheEventItIsQueriedAgainst() {
        val missing = dashboards.flatMap { dashboard ->
            dashboard.queries.flatMap { query ->
                query.events.flatMap { event ->
                    val attributes = emitted[event].orEmpty()
                    query.attributes
                        .filter { it !in attributes }
                        .map { "${dashboard.file} · $event has no `$it`  (emits: ${attributes.keys.sorted()})" }
                }
            }
        }.distinct().sorted()

        assertTrue(
            missing.isEmpty(),
            "These dashboard queries filter, group or unwrap on an attribute the event does not " +
                "carry. Loki accepts the query and the panel renders empty:\n" +
                missing.joinToString("\n") { "  $it" } +
                "\n\nThe emitting code wins. Fix the query, or add the attribute at the emit site " +
                "and to docs/practices/app-events.md.",
        )
    }

    /**
     * `unwrap` needs a number. An attribute emitted as `difficulty.name` instead
     * of `difficulty` still passes the spelling check above and still produces an
     * empty panel, because Loki cannot unwrap `"Tricky"` into a sample.
     *
     * The check is a heuristic over the emit-site expression and only rejects the
     * unambiguous cases — a string literal, `.name`, `.toString()`, `simpleName`,
     * `.lowercase()`. A method returning a `String` from a name that does not say
     * so gets through.
     */
    @Test
    fun everyUnwrappedAttributeIsEmittedAsANumber() {
        val unwrapped = dashboards.flatMap { dashboard ->
            dashboard.queries.flatMap { query -> query.events.flatMap { event -> query.unwrapped.map { event to it } } }
        }.distinct()

        val stringy = unwrapped.flatMap { (event, attribute) ->
            emitted[event].orEmpty()[attribute].orEmpty()
                .filter { it.looksLikeAString() }
                .map { "$event · $attribute is emitted as `$it`" }
        }.distinct().sorted()

        assertTrue(
            stringy.isEmpty(),
            "A dashboard unwraps these into a numeric sample, but they are emitted as strings. " +
                "Loki cannot unwrap a string, so the panel renders empty:\n" +
                stringy.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun everyDashboardTargetsTheRealLokiDatasourceAndService() {
        val problems = dashboards.flatMap { dashboard ->
            buildList {
                dashboard.datasourceUids.filterNot { it in ALLOWED_DATASOURCE_UIDS }
                    .forEach { add("${dashboard.file}: datasource uid \"$it\"") }
                dashboard.queries.filterNot { it.expr.contains("service_name=\"${GrafanaLogTree.SERVICE_NAME}\"") }
                    .forEach { add("${dashboard.file}: query does not select service_name=\"${GrafanaLogTree.SERVICE_NAME}\": ${it.expr}") }
            }
        }.distinct().sorted()

        assertTrue(
            problems.isEmpty(),
            "Dashboards must point at the provisioned Grafana Cloud Loki datasource and at the " +
                "service name GrafanaLogTree actually exports under:\n" +
                problems.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun allSixSpecDashboardsAreCommitted() {
        assertEquals(
            EXPECTED_DASHBOARDS,
            dashboards.map { it.file }.toSet(),
            "SPEC section 14 names six dashboards and ops/grafana/ should hold one file each",
        )
        assertEquals(
            EXPECTED_DASHBOARDS.size,
            dashboards.map { it.uid }.toSet().size,
            "two dashboards share a uid, so importing both would overwrite one",
        )
    }

    /**
     * The guard against the guard.
     *
     * Three tests in this repo once passed against a stub that returned an empty
     * list. Every assertion above is "no violations found", which is exactly what
     * a reader that reads nothing reports — a wrong repo root, a moved source
     * layout or a LogQL construct the parser silently skips would all turn this
     * file green while proving nothing. Both readers are pinned here against
     * fixtures whose answers are written out, and against floors on the real
     * scan.
     */
    @Test
    fun bothReadersCanActuallyFail() {
        assertTrue(
            emitted.size >= MINIMUM_EMITTED_EVENTS,
            "only found ${emitted.size} emitted events in the source tree, so the scan is broken",
        )
        assertTrue(
            "game.level_completed" in emitted && "strikes_used" in emitted.getValue("game.level_completed"),
            "the scan did not find the attribute the difficulty board is built on",
        )
        assertTrue(
            "game.never_emitted_anywhere" !in emitted,
            "the scan reports an event that does not exist, so it matches too much",
        )

        val checked = dashboards.sumOf { d -> d.queries.sumOf { it.events.size * it.attributes.size } }
        assertTrue(
            checked >= MINIMUM_CHECKED_PAIRS,
            "only $checked (event, attribute) pairs were checked, so the dashboards parsed as empty",
        )

        val fixture = """
            logger.logEvent(
                "game.level_completed",
                "level_id" to level.id,
                "difficulty" to level.difficulty,
                "mode" to modeName,
            )
        """.trimIndent()
        val scanned = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        scanned.absorbLogEventCalls(fixture)
        assertEquals(setOf("game.level_completed"), scanned.keys)
        assertEquals(
            setOf("level_id", "difficulty", "mode"),
            scanned.getValue("game.level_completed").keys,
        )
        assertEquals(
            listOf("level.difficulty"),
            scanned.getValue("game.level_completed").getValue("difficulty"),
        )

        val empty = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        empty.absorbLogEventCalls("fun main() { println(\"logEventually\") }")
        assertTrue(empty.isEmpty(), "the scan invented an event out of a file with no logEvent call")

        val parsed = parseQuery(
            "quantile_over_time(0.5, {service_name=\"sodogku-client\"} | event_name=\"game.level_completed\" " +
                "| mode=\"campaign\" | unwrap duration_ms [1d]) by (difficulty)",
            legendFormat = "Tier {{difficulty}}",
        )
        assertEquals(setOf("game.level_completed"), parsed.events)
        assertEquals(setOf("mode", "duration_ms", "difficulty"), parsed.attributes)
        assertEquals(setOf("duration_ms"), parsed.unwrapped)

        val rejected = runCatching {
            parseQuery("sum(count_over_time({service_name=\"sodogku-client\"} | json | level=\"x\" [1d]))", null)
        }
        assertTrue(
            rejected.isFailure,
            "the LogQL reader accepted a pipeline stage it cannot understand, so it would extract " +
                "nothing from it and report no violations",
        )
    }
}

private const val REPO_ROOT_PROPERTY = "sodogku.repoRoot"
private const val DASHBOARD_DIR_PROPERTY = "sodogku.grafanaDashboards"

/** Floors, not real counts — see [DashboardQueryContractTest.bothReadersCanActuallyFail]. */
private const val MINIMUM_EMITTED_EVENTS = 25
private const val MINIMUM_CHECKED_PAIRS = 40
private const val MINIMUM_SOURCE_FILES = 200

private val EXPECTED_DASHBOARDS = setOf(
    "level-drop-off.json",
    "difficulty-calibration.json",
    "ad-funnel.json",
    "paywall-conversion.json",
    "daily-retention.json",
    "tutorial-funnel.json",
)

/**
 * `-- Mixed --` is the panel-level datasource for a stat built from two Loki
 * targets and an `__expr__` math node; the query targets underneath it still
 * name the Loki uid, and those are what the contract checks.
 */
private val ALLOWED_DATASOURCE_UIDS = setOf("grafanacloud-logs", "__expr__", "-- Mixed --", "-- Grafana --")

/**
 * Keys every record carries regardless of the event: the two stream labels, the
 * correlation ids and connectivity flag `GrafanaLogTree` stamps per record, and
 * the resource attributes Grafana Cloud lands in structured metadata. Querying
 * one of these is never a claim about a particular event.
 */
private val AMBIENT_KEYS = setOf(
    "service_name",
    "deployment_environment",
    "session_id",
    "install_id",
    "is_offline",
    "platform",
    "service_version",
    "build_number",
    "commit_sha",
    "release_channel",
    "detected_level",
    "event_name",
)

private data class Dashboard(
    val file: String,
    val uid: String,
    val queries: List<Query>,
    val datasourceUids: Set<String>,
)

private data class Query(
    val expr: String,
    val events: Set<String>,
    val attributes: Set<String>,
    val unwrapped: Set<String>,
)

private fun readDashboards(): List<Dashboard> {
    val dir = File(
        System.getProperty(DASHBOARD_DIR_PROPERTY)
            ?: error("$DASHBOARD_DIR_PROPERTY is unset — libraries/telemetry/impl/build.gradle.kts should supply it"),
    )
    val files = dir.listFiles { f: File -> f.isFile && f.extension == "json" }?.sortedBy { it.name }
    if (files.isNullOrEmpty()) error("no dashboards found in ${dir.absolutePath}")

    return files.map { file ->
        val root = Json.parseToJsonElement(file.readText()).jsonObjectOrFail(file.name)
        val panels = (root["panels"] as? JsonArray).orEmpty()
        val uids = mutableSetOf<String>()
        val queries = mutableListOf<Query>()

        root.collectDatasourceUids(uids)
        panels.forEach { panel ->
            val obj = panel.jsonObjectOrFail(file.name)
            obj.collectDatasourceUids(uids)
            (obj["targets"] as? JsonArray).orEmpty().forEach { rawTarget ->
                val target = rawTarget.jsonObjectOrFail(file.name)
                val expr = target["expr"]?.stringOrNull() ?: return@forEach
                queries += parseQuery(expr, target["legendFormat"]?.stringOrNull())
            }
        }
        Dashboard(file.name, root["uid"]?.stringOrNull().orEmpty(), queries, uids)
    }
}

private fun JsonElement.jsonObjectOrFail(file: String): JsonObject =
    this as? JsonObject ?: fail("$file: expected a JSON object, got $this")

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement.collectDatasourceUids(into: MutableSet<String>) {
    when (this) {
        is JsonObject -> {
            (this["datasource"] as? JsonObject)?.get("uid")?.stringOrNull()?.let { into += it }
            values.forEach { it.collectDatasourceUids(into) }
        }

        is JsonArray -> forEach { it.collectDatasourceUids(into) }
        else -> Unit
    }
}

private val IDENTIFIER = Regex("[a-z_][a-z0-9_]*")
private val GROUP_BY = Regex("""by\s*\(([^)]*)\)""")
private val LEGEND_FIELD = Regex("""\{\{\s*([a-z_][a-z0-9_]*)\s*}}""")

/** Longest first, so `=~` is never read as `=` followed by a stray `~`. */
private val FILTER_OPERATORS = listOf("=~", "!~", "!=", ">=", "<=", "=", ">", "<")

/**
 * Reads one panel query.
 *
 * Understands exactly the LogQL this repo's dashboards are allowed to use — a
 * stream selector, label filters on structured metadata, and `unwrap` — and
 * throws on anything else. That strictness is the point: a stage the reader
 * quietly skipped would contribute no references, and a query contributing no
 * references passes every assertion in this file.
 */
private fun parseQuery(expr: String, legendFormat: String?): Query {
    val events = mutableSetOf<String>()
    val attributes = mutableSetOf<String>()
    val unwrapped = mutableSetOf<String>()

    var i = 0
    while (i < expr.length) {
        if (expr[i] != '{') {
            i++
            continue
        }
        val selectorEnd = expr.indexOf('}', i)
        require(selectorEnd > 0) { "unterminated stream selector in: $expr" }
        i = selectorEnd + 1

        while (true) {
            while (i < expr.length && expr[i] == ' ') i++
            if (i >= expr.length || expr[i] != '|') break
            i++
            while (i < expr.length && expr[i] == ' ') i++

            if (expr.startsWith(UNWRAP, i)) {
                i += UNWRAP.length
                val name = IDENTIFIER.matchAt(expr, i) ?: error("unwrap without a name in: $expr")
                unwrapped += name.value
                attributes += name.value
                i = name.range.last + 1
                continue
            }

            val key = IDENTIFIER.matchAt(expr, i)
                ?: error("unsupported LogQL stage at offset $i in: $expr")
            i = key.range.last + 1
            while (i < expr.length && expr[i] == ' ') i++

            val operator = FILTER_OPERATORS.firstOrNull { expr.startsWith(it, i) }
                ?: error("`${key.value}` is not followed by a comparison in: $expr")
            i += operator.length
            while (i < expr.length && expr[i] == ' ') i++

            i = if (i < expr.length && expr[i] == '"') {
                val end = expr.indexOf('"', i + 1)
                require(end > 0) { "unterminated string in: $expr" }
                val value = expr.substring(i + 1, end)
                if (key.value == "event_name") events += value.split("|") else attributes += key.value
                end + 1
            } else {
                val number = NUMBER.matchAt(expr, i) ?: error("`${key.value}` compared to something unreadable in: $expr")
                attributes += key.value
                number.range.last + 1
            }
        }
    }

    GROUP_BY.findAll(expr).forEach { match ->
        match.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { attributes += it }
    }
    legendFormat?.let { format -> LEGEND_FIELD.findAll(format).forEach { attributes += it.groupValues[1] } }

    return Query(
        expr = expr,
        events = events,
        attributes = attributes - AMBIENT_KEYS,
        unwrapped = unwrapped - AMBIENT_KEYS,
    )
}

private const val UNWRAP = "unwrap "
private val NUMBER = Regex("""-?[0-9]+(\.[0-9]+)?""")

/**
 * Comment lines inside an argument list, which are common here and were poison.
 *
 * Emit sites in this repo are heavily commented, and a comment is prose, and
 * prose has commas in it. Since arguments are separated by splitting on
 * top-level commas, a comment reading "clear rate, time, score, retries" became
 * four arguments, and the genuine `"key" to value` after it was left fused to
 * the last fragment where [ATTRIBUTE_PAIR] could not match it.
 *
 * So every attribute introduced by a comment was invisible to the scan, and the
 * event's attribute list was quietly short. It fails closed — a dashboard
 * querying such an attribute is reported as querying something the event does
 * not carry — so nothing shipped broken. What it did was point the failure
 * message at the dashboard when the bug was here, listing "emits: [...]" without
 * the very attribute the emit site plainly has.
 */
private val COMMENT_LINE = Regex("""(?m)^\s*//[^\n]*\n""")

private fun String.withoutComments(): String = COMMENT_LINE.replace(this, "")

private val LOG_EVENT_CALL = Regex("""logEvent\s*\(""")
private val ATTRIBUTE_PAIR = Regex("""^\s*"([a-z0-9_]+)"\s+to\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)
private val STRINGY_VALUE = Regex("""^"|\.name\b|\.toString\(\)|simpleName|\.lowercase\(\)""")

private fun String.looksLikeAString(): Boolean = STRINGY_VALUE.containsMatchIn(this)

/** Event name → attribute key → every emit-site value expression seen for it. */
private fun scanLogEventCalls(): Map<String, Map<String, List<String>>> {
    val root = System.getProperty(REPO_ROOT_PROPERTY)
        ?: error("$REPO_ROOT_PROPERTY is unset — libraries/telemetry/impl/build.gradle.kts should supply it")

    val sources = listOf("features", "libraries", "apps")
        .map { File(root, it) }
        .flatMap { dir -> dir.walkTopDown().filter { it.isProductionKotlin() }.toList() }
    assertTrue(sources.size > MINIMUM_SOURCE_FILES, "only found ${sources.size} source files to scan")

    val found = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
    sources.forEach { found.absorbLogEventCalls(it.readText()) }
    return found
}

/**
 * Test sources are excluded because they emit invented events —
 * `example.completed` and friends — and an invented event is not evidence that a
 * dashboard has something to query.
 */
private fun File.isProductionKotlin(): Boolean {
    if (!isFile || extension != "kt") return false
    val parts = path.split(File.separator)
    return "build" !in parts && parts.none { it.endsWith("Test") || it == "test" }
}

private fun MutableMap<String, MutableMap<String, MutableList<String>>>.absorbLogEventCalls(source: String) {
    LOG_EVENT_CALL.findAll(source).forEach { call ->
        val open = call.range.last
        val arguments = balancedArguments(source, open)
            ?: error("unbalanced logEvent( call at offset ${call.range.first}")
        // Comments are stripped *before* splitting, not after, and that
        // ordering is the whole fix. `splitTopLevel` separates arguments on
        // commas, and prose contains commas: a comment reading "clear rate,
        // time, score, retries" was cut into four fake arguments, and the real
        // `"auto_mark" to autoMark` that followed it ended up glued to the tail
        // of one of them and matched nothing.
        val parts = splitTopLevel(arguments.withoutComments())
        val name = parts.firstOrNull()?.trim()?.removeSurrounding("\"") ?: return@forEach
        if (!name.matches(EVENT_NAME)) return@forEach

        val attributes = getOrPut(name) { mutableMapOf() }
        parts.drop(1).forEach { part ->
            val pair = ATTRIBUTE_PAIR.matchEntire(part) ?: return@forEach
            attributes.getOrPut(pair.groupValues[1]) { mutableListOf() } += pair.groupValues[2].trim().trimEnd(',')
        }
    }
}

private val EVENT_NAME = Regex("""[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*""")

/** The text between the call's parentheses, tracking nesting and string literals. */
private fun balancedArguments(source: String, openParen: Int): String? {
    var depth = 0
    var index = openParen
    var inString = false
    var escaped = false
    while (index < source.length) {
        val c = source[index]
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '(' -> depth++
            c == ')' -> {
                depth--
                if (depth == 0) return source.substring(openParen + 1, index)
            }
        }
        index++
    }
    return null
}

private fun splitTopLevel(arguments: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    var inString = false
    var escaped = false
    arguments.forEach { c ->
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '(' || c == '[' -> depth++
            c == ')' || c == ']' -> depth--
            c == ',' && depth == 0 -> {
                parts += current.toString()
                current.clear()
                return@forEach
            }
        }
        current.append(c)
    }
    if (current.isNotBlank()) parts += current.toString()
    return parts
}
