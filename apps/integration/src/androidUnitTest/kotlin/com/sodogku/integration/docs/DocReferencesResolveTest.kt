package com.sodogku.integration.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A doc reference in a KDoc or in another doc has to point at something that exists.
 *
 * This is the guard the deleted spec doc never had. Around 200 `SPEC <n>`
 * citations were scattered through KDoc, tests, string resources and other docs,
 * and every one of them was a link nothing could check. When the sections behind
 * them stopped being true nothing failed, and when the file was finally deleted
 * they would all have gone on reading as though it were still there.
 *
 * Two rules, and the second is the one that matters.
 *
 * 1. A cited markdown file exists.
 * 2. An anchor on it names a heading in it.
 *
 * A dangling *file* gets noticed eventually. A dangling *section* is the expensive
 * one: the doc is still there, the link still opens it, and the reader concludes
 * the code is describing something the doc no longer says.
 *
 * ### What counts as a citation
 *
 * A markdown filename, optionally with an anchor, in a Kotlin, Gradle, markdown,
 * Swift, XML or JSON file. JSON is in the set for one reason: the committed
 * Grafana dashboards carry long markdown descriptions, thirteen citations
 * between them, and nothing else would ever look at them.
 *
 * The path is resolved three ways in order: against the citing
 * file's own directory, then the repo root, then by filename if exactly one doc
 * in the repo has that name.
 *
 * The citing directory goes first because that is what a markdown link means, and
 * getting it backwards is not theoretical: the server's deploy guide links the
 * environment-variables section of the README beside it, root-first resolved that
 * against the repo's own README instead, and a link that works read as dangling.
 *
 * The last one is what lets a KDoc write `features.md#skip` instead of
 * `docs/reference/features.md#skip`. Around eighty of these ended up in comment
 * prose, and the full path pushed every one of those lines twenty characters past
 * where the file wraps. A bare name only resolves while it is unambiguous, so
 * adding a second `features.md` anywhere turns every one of them into a failure
 * rather than a coin flip.
 *
 * A path that resolves to nothing is only an error when it starts with a directory
 * this repo keeps docs in. Otherwise it is a URL, a generated path, or prose naming
 * a file nobody has written yet, and failing on those would make the test a
 * nuisance rather than a guard.
 *
 * ### The Gradle half
 *
 * This test reads files at *runtime*, which Gradle cannot see, so
 * `apps/integration/build.gradle.kts` declares them with `inputs.files(...)`.
 * Without that the task stays UP-TO-DATE when a heading is renamed, and the drift
 * ships green. That trap has caught this module twice, and both times the test
 * looked like it was working.
 */
class DocReferencesResolveTest {

    @Test
    fun everyCitedDocExists() {
        val missing = citations()
            .filter { it.target == null && (it.looksRepoRelative || it.anchor != null) }
            .filter { it.path !in CROSS_REPO }
            .map { "  ${it.source}: ${it.raw}" }
            .distinct()

        assertTrue(
            missing.isEmpty(),
            "These references name a doc that does not exist:\n" + missing.joinToString("\n"),
        )
    }

    @Test
    fun everyCitedSectionExists() {
        val headings = mutableMapOf<File, Set<String>>()

        val dangling = citations()
            .filter { it.anchor != null && it.target != null }
            .filter { it.anchor !in headings.getOrPut(it.target!!) { slugsIn(it.target) } }
            .map { "  ${it.source}: ${it.raw}" }
            .distinct()

        assertTrue(
            dangling.isEmpty(),
            "These references name a section that no longer exists. Repoint each one at a " +
                "heading that does, or drop the anchor:\n" + dangling.joinToString("\n"),
        )
    }

    @Test
    fun theScanFindsCitationsAndReadsHeadings() {
        // The guard against the guard. Both tests above are written as "find the
        // offenders", so a scan matching nothing would report success in exactly
        // the same words as a scan that found nothing wrong.
        val found = citations()

        assertTrue(
            found.size >= MIN_CITATIONS,
            "only ${found.size} doc references matched, so the scan is broken rather than clean",
        )
        assertTrue(
            found.count { it.anchor != null } >= MIN_ANCHORED,
            "only ${found.count { it.anchor != null }} anchored references matched, so the " +
                "section rule is checking almost nothing",
        )

        val features = File(repoRoot(), "docs/reference/features.md")
        assertTrue(features.isFile, "no features doc at ${features.absolutePath}")
        assertTrue(
            slugsIn(features).size >= MIN_FEATURE_SECTIONS,
            "only ${slugsIn(features).size} headings read out of features.md, so the heading " +
                "parser is broken and every anchor into it would pass or fail together",
        )
    }

    private class Citation(
        val source: String,
        val raw: String,
        val path: String,
        val anchor: String?,
        val target: File?,
    ) {
        val looksRepoRelative: Boolean get() = DOC_ROOTS.any { path.startsWith("$it/") }
    }

    private fun citations(): List<Citation> {
        val root = File(repoRoot())
        val files = scannedFiles()
        val byName = files
            .filter { it.extension == "md" }
            .groupBy { it.name }
            .filterValues { it.size == 1 }
            .mapValues { (_, only) -> only.single() }

        return files.flatMap { file ->
            val source = file.toRelativeString(root)
            CITATION.findAll(file.readText()).map { match ->
                val path = match.groupValues[1]
                val resolved = sequenceOf(File(file.parentFile, path), File(root, path))
                    .firstOrNull { it.isFile }
                    ?: byName[path].takeIf { '/' !in path }
                Citation(
                    source = source,
                    raw = match.value,
                    path = path,
                    anchor = match.groupValues[2].ifEmpty { null },
                    target = resolved?.canonicalFile,
                )
            }
        }
    }

    /**
     * GitHub's heading slug: lowercase, punctuation dropped, spaces to hyphens.
     *
     * Fenced code is stripped first. A shell or Kotlin comment inside a fence starts
     * with `#` and would otherwise read as a heading, which is worse than missing
     * one: it makes an anchor pass against a line nobody can actually link to.
     */
    private fun slugsIn(doc: File): Set<String> = doc.readText()
        .replace(FENCE, "")
        .lineSequence()
        .mapNotNull { HEADING.matchEntire(it.trim())?.groupValues?.get(1) }
        .map { heading ->
            heading.lowercase()
                .replace(NOT_SLUGGABLE, "")
                .trim()
                .replace(SPACES, "-")
        }
        .toSet()

    private fun scannedFiles(): List<File> {
        val root = File(repoRoot())
        // The repo's own top-level markdown, non-recursively. README.md and
        // AGENTS.md are where a newcomer is pointed at every other doc, so they
        // are the last place a stale pointer should be allowed to sit.
        val topLevel = root.listFiles().orEmpty().filter { it.isFile && it.extension == "md" }
        return topLevel + SCANNED_ROOTS
            .map { File(root, it) }
            .filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.isScannable() }.toList() }
    }

    private fun File.isScannable(): Boolean {
        if (!isFile || extension !in SCANNED_EXTENSIONS) return false
        // Relative to the repo root, not the absolute path. An agent worktree is
        // itself a full checkout living under `.claude/worktrees/`, so matching
        // on the absolute path excluded the entire tree and left the scan reading
        // five files. It still passed the "did it find anything" floor, which is
        // why that floor now counts anchored references separately.
        val relative = toRelativeString(File(repoRoot()))
        if (relative in NOT_SCANNED) return false
        return relative.split(File.separatorChar).none { it == "build" || it == ".claude" }
    }

    private fun repoRoot(): String =
        System.getProperty("sodogku.repoRoot")
            ?: error("sodogku.repoRoot is unset; apps/integration/build.gradle.kts supplies it")

    private companion object {
        val CITATION = Regex("""([\w.-]+(?:/[\w.-]+)*\.md)(?:#([\w-]+))?""")

        val HEADING = Regex("""#{1,6}\s+(.+?)\s*#*""")
        val FENCE = Regex("""(?ms)^```.*?^```""")
        val NOT_SLUGGABLE = Regex("""[^\w\- ]""")
        val SPACES = Regex("""\s+""")

        val DOC_ROOTS = listOf("docs", "apps", "pages", "ops", "libraries", "features", "tools")

        /**
         * The one path that is meant to be unresolvable here.
         *
         * `docs/PORT-CANDIDATES.md` lives in the template this app was generated
         * from, not in this repo, and `AGENTS.md` says so in as many words: it is
         * a queue you write to across repos. Deliberately a set of one, and a
         * second entry should be argued for rather than appended, because the
         * whole value of this test is that it has no quiet exceptions.
         */
        val CROSS_REPO = setOf("docs/PORT-CANDIDATES.md")
        val SCANNED_ROOTS =
            listOf("docs", "libraries", "features", "apps", "ops", "tools", "gradle")

        /**
         * Every file type in this repo that has been caught holding a doc
         * reference, which is a longer list than it sounds.
         *
         * The last two found in the spec sweep were in a version catalogue and
         * an `Info.plist`, both cited a section, and both were missed by a
         * search that only read the obvious extensions. Adding a type here is
         * cheap; the hole it closes is not.
         */
        val SCANNED_EXTENSIONS =
            setOf("kt", "kts", "md", "swift", "xml", "json", "toml", "plist")

        /**
         * The two queues, and the only files here allowed to name a doc that
         * does not exist.
         *
         * A work item's whole job is to describe work not done yet, so it names
         * the file it is asking somebody to write. Scanning them would mean a
         * ticket could not say what it wants until after it was finished.
         */
        val NOT_SCANNED = setOf("docs/todos.md", "docs/backlog.md")

        /**
         * Floors, not counts. They exist so a scan that silently matches nothing
         * fails loudly rather than passing, and they sit well under the real
         * numbers so ordinary churn never touches them.
         */
        const val MIN_CITATIONS = 40
        const val MIN_ANCHORED = 10
        const val MIN_FEATURE_SECTIONS = 15
    }
}
