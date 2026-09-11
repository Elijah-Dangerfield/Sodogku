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
    private val registry: Set<String> = readRegistryEvents()

    /**
     * The registry calls itself the source of truth and says the code wins when
     * the two disagree. Both halves of that had rotted: three emitted events had
     * no row (`game.drag`, `game.hint_applied`, `ads.stand_in`) and two rows
     * named events nothing emits (`purchase.failed`, `conn.regained`).
     *
     * Neither shape is visible from anywhere else. The dashboards are written
     * from this page, as it asks them to be, so an event with no row is an event
     * no panel will ever be written against, and a row with no event is a panel
     * written against nothing, which the dashboard check above only catches
     * once somebody has already built the panel.
     */
    @Test
    fun everyEmittedEventHasARowInTheRegistry() {
        val missing = (emitted.keys - registry).sorted()

        assertTrue(
            missing.isEmpty(),
            "These events are emitted and the registry does not list them, so the next panel will " +
                "be written as though they do not exist:\n" + missing.joinToString("\n") { "  $it" } +
                "\n\nAdd a row to docs/practices/app-events.md in the same change as the emit site.",
        )
    }

    @Test
    fun everyRegistryRowNamesAnEventSomethingEmits() {
        val orphaned = (registry - emitted.keys).sorted()

        assertTrue(
            orphaned.isEmpty(),
            "The registry has a table row for these and no `logEvent` call emits them, so a panel " +
                "written from the page would render empty forever:\n" +
                orphaned.joinToString("\n") { "  $it" } +
                "\n\nAn event that is deliberately not coming belongs in prose on that page, next " +
                "to the argument for why, not in a table of what fires.",
        )
    }

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

    /**
     * The same failure as the two tests above, one layer down: the attribute is
     * spelled right at the emit site and the *value* is what changes.
     *
     * Android release builds are minified. `::class.simpleName` reads the class
     * name out of the dex at runtime, so R8 renaming the class silently rewrites
     * the value — `PurchaseOutcome$Success -> ta.l` in a real `mapping.txt`, which
     * made `iap.purchase_result` report `outcome=l` while the paywall board
     * filtered on `Success`. Purchases were happening; the panel read zero; nothing
     * errored. The spelling check above cannot see it, because the spelling is
     * fine, and iOS cannot see it either, because iOS is not obfuscated.
     *
     * So no attribute value may be written as a class name. The alternatives that
     * survive R8 are an enum's `.name` (the constant fields get renamed, the
     * string in `<clinit>` does not) and a declared `val name` holding a literal.
     *
     * **What it proves, precisely.** That no `logEvent` argument list *contains*
     * the text. A class name laundered through a helper function called on the
     * argument is still invisible here — `NetworkCall.classifyForLog()` is exactly
     * that, and it is deliberate, because it returns a Throwable name and
     * `proguard-rules.pro` keeps those. Pinning the rule at the emit site is what
     * keeps the reliance on that keep rule down to the one place that documents it.
     */
    @Test
    fun noAttributeIsSpelledWithAClassNameR8CanRename() {
        val offenders = emitted.flatMap { (event, attributes) ->
            attributes.flatMap { (key, values) ->
                values.filter { CLASS_NAME_VALUE.containsMatchIn(it) }
                    .map { "$event · $key is emitted as `$it`" }
            }
        }.distinct().sorted()

        assertTrue(
            offenders.isEmpty(),
            "These attributes are fed from a class name, and R8 renames classes in the Play " +
                "build — the dashboards filtering on them go quietly empty on Android while iOS " +
                "looks fine:\n" + offenders.joinToString("\n") { "  $it" } +
                "\n\nGive the type a `val name` holding a literal, or use an enum's `.name`. " +
                "See docs/practices/app-events.md.",
        )
    }

    /**
     * The other half of the same contract: the `outcome` values the paywall board
     * filters on have to be values the app can actually produce.
     *
     * The test above stops the emit site reaching for a name R8 can rewrite. This
     * one stops the literal that replaced it drifting away from the query — which
     * is the same empty panel arriving by a different route, and the reason the
     * names live on the sealed types rather than in a keep rule.
     *
     * Only the `iap.*` events, because they are the only ones whose vocabulary is
     * declared in one readable place. `ads.result` mixes an enum with a synthetic
     * `granted_without_ad` and is not extractable this way.
     */
    @Test
    fun everyIapOutcomeTheDashboardsFilterOnIsOneTheAppCanEmit() {
        val declared = declaredOutcomeNames()
        val unknown = dashboards.flatMap { dashboard ->
            dashboard.queries.flatMap { query ->
                query.events.mapNotNull { event -> IAP_OUTCOME_EVENTS[event]?.let { event to it } }
                    .flatMap { (event, type) ->
                        val names = declared[type].orEmpty()
                        query.equalities["outcome"].orEmpty()
                            .filter { it !in names }
                            .map { "${dashboard.file} · $event: outcome=\"$it\" (emits $names)" }
                    }
            }
        }.distinct().sorted()

        assertTrue(
            unknown.isEmpty(),
            "These panels filter on an `outcome` the event's sealed type does not declare, so " +
                "they render empty:\n" + unknown.joinToString("\n") { "  $it" } +
                "\n\nEither the query drifted or a `name` in libraries/billing/.../Entitlements.kt " +
                "was renamed without its dashboard.",
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

        // The class-name detector, against the emit site as it was actually
        // written before SD-39. A regex that matches nothing reports no
        // offenders, which is indistinguishable from a clean tree.
        val renameable = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        renameable.absorbLogEventCalls(
            """logger.logEvent("iap.restore_result", "outcome" to result::class.simpleName)""",
        )
        assertEquals(
            listOf("result::class.simpleName"),
            renameable.getValue("iap.restore_result").getValue("outcome"),
        )
        assertTrue(
            renameable.getValue("iap.restore_result").getValue("outcome")
                .all { CLASS_NAME_VALUE.containsMatchIn(it) },
            "the class-name detector does not match the expression that caused SD-39",
        )
        assertTrue(
            listOf("result.name", "outcome.result.name", "\"granted_without_ad\"", "step.name")
                .none { CLASS_NAME_VALUE.containsMatchIn(it) },
            "the class-name detector rejects values that survive minification",
        )

        assertTrue(
            registry.size >= MINIMUM_REGISTERED_EVENTS,
            "only found ${registry.size} events in the registry, so the markdown parse is broken",
        )
        assertEquals(
            setOf("game.level_started"),
            parseRegistryEvents(
                """
                | Event | Attributes | Fires |
                |---|---|---|
                | `game.level_started` | `level_id` | Every attempt |

                Prose about `app.launched`, which is not a row.
                """.trimIndent(),
            ),
            "the registry parse reads prose as rows, or misses rows",
        )

        val parsed = parseQuery(
            "quantile_over_time(0.5, {service_name=\"sodogku-client\"} | event_name=\"game.level_completed\" " +
                "| mode=\"campaign\" | unwrap duration_ms [1d]) by (difficulty)",
            legendFormat = "Tier {{difficulty}}",
        )
        assertEquals(setOf("game.level_completed"), parsed.events)
        assertEquals(setOf("mode", "duration_ms", "difficulty"), parsed.attributes)
        assertEquals(setOf("duration_ms"), parsed.unwrapped)
        assertEquals(mapOf("mode" to setOf("campaign")), parsed.equalities)

        // A negation is not a claim that anything emits the value, so it must not
        // reach the vocabulary check — recorded, it would fail a correct query.
        val negated = parseQuery(
            "sum(count_over_time({service_name=\"sodogku-client\"} | event_name=\"ads.result\" " +
                "| error_kind!=\"\" | outcome=~\"NoFill|Offline\" [1d]))",
            legendFormat = null,
        )
        assertEquals(mapOf("outcome" to setOf("NoFill", "Offline")), negated.equalities)

        val outcomes = declaredOutcomeNames()
        assertEquals(
            setOf("Success", "Cancelled", "AlreadyOwned", "Unavailable", "Failed"),
            outcomes["PurchaseOutcome"],
        )
        assertEquals(setOf("Restored", "NothingToRestore", "Failed"), outcomes["RestoreOutcome"])

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
private const val REGISTRY_PROPERTY = "sodogku.appEventsRegistry"

/** Floors, not real counts — see [DashboardQueryContractTest.bothReadersCanActuallyFail]. */
private const val MINIMUM_EMITTED_EVENTS = 25
private const val MINIMUM_CHECKED_PAIRS = 40
private const val MINIMUM_SOURCE_FILES = 200
private const val MINIMUM_REGISTERED_EVENTS = 25

/**
 * Every table on the registry page that lists events has this header, and
 * nothing else does. Matching on it rather than on "a table" keeps the prose
 * tables, the ones arguing about what is deliberately *not* emitted, out of
 * the parse.
 */
private val REGISTRY_TABLE_HEADER = Regex("""\|\s*Event\s*\|\s*Attributes\s*\|\s*Fires\s*\|""")

/** The event named in the first column of every row of those tables. */
private fun readRegistryEvents(): Set<String> {
    val file = File(
        System.getProperty(REGISTRY_PROPERTY)
            ?: error("$REGISTRY_PROPERTY is unset, libraries/telemetry/impl/build.gradle.kts should supply it"),
    )
    if (!file.isFile) error("no event registry at ${file.absolutePath}")
    return parseRegistryEvents(file.readText())
}

private fun parseRegistryEvents(markdown: String): Set<String> {
    val events = mutableSetOf<String>()
    var inTable = false
    markdown.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            REGISTRY_TABLE_HEADER.matches(trimmed) -> inTable = true
            !trimmed.startsWith("|") -> inTable = false
            inTable -> {
                val name = trimmed.trim('|').substringBefore('|').trim().trim('`')
                if (name.matches(EVENT_NAME)) events += name
            }
        }
    }
    return events
}

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
    /**
     * Attribute → the values it is matched *positively* against (`=` and `=~`).
     *
     * `!=` and `!~` are left out on purpose: excluding a value is not a claim
     * that anything ever emits it, so holding a negation against the emitted
     * vocabulary would fail on a query that is perfectly correct.
     */
    val equalities: Map<String, Set<String>>,
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
    val equalities = mutableMapOf<String, MutableSet<String>>()

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
                if (key.value == "event_name") {
                    events += value.split("|")
                } else {
                    attributes += key.value
                    if (operator == "=" || operator == "=~") {
                        equalities.getOrPut(key.value) { mutableSetOf() } += value.split("|")
                    }
                }
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
        equalities = equalities - AMBIENT_KEYS,
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

/**
 * Every way of reading a class name that R8 is free to rewrite.
 *
 * `qualifiedName` and the `::class.java` forms are here even though nothing uses
 * them, because they are the obvious next reach for someone who finds
 * `simpleName` rejected and they fail in exactly the same way.
 */
private val CLASS_NAME_VALUE = Regex("""::class(\.java)?\.(simpleName|qualifiedName|name\b)""")

/** The event each sealed type reports its `name` on. */
private val IAP_OUTCOME_EVENTS = mapOf(
    "iap.purchase_result" to "PurchaseOutcome",
    "iap.restore_result" to "RestoreOutcome",
)

private const val ENTITLEMENTS_PATH =
    "libraries/billing/src/commonMain/kotlin/com/sodogku/libraries/billing/Entitlements.kt"

private val DECLARED_NAME = Regex("""override val name = "([A-Za-z_]+)"""")
private val SEALED_TYPE = Regex("""(?m)^sealed interface (\w+)""")

/**
 * The `outcome` vocabulary, per sealed type, read from the file that declares it.
 *
 * Read as text rather than by reflection because `:libraries:billing` is not on
 * this module's classpath — the same reason [scanLogEventCalls] is a source scan.
 *
 * **Per type, not pooled.** Both types have a `Failed`, and a single flat set of
 * every name in the file hid that: renaming only `PurchaseOutcome.Failed` left
 * `Failed` in the set via `RestoreOutcome`, so the mutation survived and the
 * "Purchase failures by store code" panel would have gone empty unnoticed.
 */
private fun declaredOutcomeNames(): Map<String, Set<String>> {
    val root = System.getProperty(REPO_ROOT_PROPERTY)
        ?: error("$REPO_ROOT_PROPERTY is unset — libraries/telemetry/impl/build.gradle.kts should supply it")
    val source = File(root, ENTITLEMENTS_PATH)
    require(source.isFile) { "$ENTITLEMENTS_PATH has moved; this test reads the outcome names out of it" }
    val text = source.readText()

    val starts = SEALED_TYPE.findAll(text).toList()
    val byType = starts.mapIndexed { index, match ->
        val end = starts.getOrNull(index + 1)?.range?.first ?: text.length
        match.groupValues[1] to DECLARED_NAME.findAll(text.substring(match.range.first, end))
            .map { it.groupValues[1] }
            .toSet()
    }.toMap()

    IAP_OUTCOME_EVENTS.values.forEach { type ->
        val names = byType[type].orEmpty()
        require(names.size >= MINIMUM_NAMES_PER_TYPE) {
            "only found $names for $type in $ENTITLEMENTS_PATH, so the reader is broken and " +
                "every query would pass"
        }
    }
    return byType
}

/** `RestoreOutcome` is the smaller of the two, with three. */
private const val MINIMUM_NAMES_PER_TYPE = 3

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
