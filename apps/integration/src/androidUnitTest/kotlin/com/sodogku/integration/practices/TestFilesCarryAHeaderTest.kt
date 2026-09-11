package com.sodogku.integration.practices

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every test file says, at the top, what it holds and why.
 *
 * `docs/practices/testing.md#conventions` has asked for this since it was
 * written, and a quarter of the suite had skipped it, including the largest file
 * in the repo. The rule earns the guard because the header is where a test's
 * claim lives, and a claim is the only thing a reader can hold the assertions
 * against. Both of this week's dead tests were found that way rather than by
 * reading the code: a refill test named for a reduction it never provoked, and a
 * sampling test that passed with sampling switched off. Neither looks wrong
 * beside its own assertions. Both look wrong beside a sentence saying what they
 * are for.
 *
 * ### What counts as a test file
 *
 * A Kotlin file under a test source set that declares at least one `@Test`.
 * Fakes, test doubles and harness helpers are out: they are read beside the
 * test that uses them, they make no claim of their own, and a rule that demanded
 * a purpose statement from `FakeDaily` would teach people to write one that says
 * nothing.
 *
 * ### What counts as a header
 *
 * A KDoc attached to the class the file is named after, or written above
 * `package`. Both positions are in the repo, and fixtures declared above the
 * class do not displace it. Two floors on top of that, both there for the same
 * reason: a header that restates the class name satisfies the rule and defeats
 * the point.
 *
 * 1. Twelve words. The shortest real header in the repo is fifteen, and the
 *    median is eighty, so this sits under ordinary practice rather than
 *    legislating a length.
 * 2. Five distinct words that are not already in the file's own path and are not
 *    on a list of words every test doc contains anyway (`tests`, `covers`,
 *    `verifies`, `behaviour`, articles, prepositions). "Unit tests for the
 *    GameViewModel class" clears nothing here, because after the path and the
 *    filler there is one word left.
 *
 * ### What this cannot catch
 *
 * That the header is true, and that the file does what it says. A sentence
 * listing the class's fields in prose order passes both floors and carries no
 * more claim than the name did. Word counting cannot tell the difference between
 * a purpose and an inventory, and every stricter rule this was tried with
 * started rejecting headers in the repo that are plainly good. So: the floors
 * make a naive header expensive enough that writing the real one is easier,
 * and the real check is still a person reading it. Nor does it hold the second
 * half of what the doc asks for, which is that the header say what is
 * deliberately *not* covered and where that lives instead. Nothing mechanical
 * can see an absence.
 *
 * ### The Gradle half
 *
 * The scan happens at test runtime, which Gradle cannot see, so
 * `apps/integration/build.gradle.kts` declares the Kotlin tree with
 * `inputs.files(...)`. Without it, adding a headerless test file leaves this
 * task UP-TO-DATE and the guard reports green on a repo it never read. That has
 * blinded four separate tests in this module, so the declaration is deliberately
 * wider than the source sets below: a test file appearing in a source set nobody
 * anticipated is exactly the change this exists to catch.
 */
class TestFilesCarryAHeaderTest {

    @Test
    fun everyTestFileCarriesAHeader() {
        val bare = testFiles()
            .filter { headerOf(it).isEmpty() }
            .map { "  ${it.toRelativeString(File(repoRoot()))}" }
            .sorted()

        assertTrue(
            bare.isEmpty(),
            "These test files open straight onto the class, so nothing says what they hold " +
                "or why. Write the top-level KDoc docs/practices/testing.md#conventions asks " +
                "for:\n" + bare.joinToString("\n"),
        )
    }

    @Test
    fun everyHeaderMakesAClaimTheAssertionsCanBeHeldAgainst() {
        val root = File(repoRoot())
        val thin = testFiles()
            .mapNotNull { file ->
                val path = file.toRelativeString(root)
                val header = headerOf(file)
                if (header.isEmpty()) return@mapNotNull null
                reasonItSaysNothing(path, header)?.let { "  $path: $it" }
            }
            .sorted()

        assertTrue(
            thin.isEmpty(),
            "These headers restate the file name rather than making a claim, which is the " +
                "shape that passes a header rule and helps nobody. Say what the file holds " +
                "and what it deliberately leaves to somewhere else:\n" + thin.joinToString("\n"),
        )
    }

    @Test
    fun theScanReadsTheRepoAndTheThinRuleCanFail() {
        // The guard against the guard. Both tests above are written as "find the
        // offenders", so a scan that matched nothing, or a parser that returned
        // an empty header for every file, would report success in the same words
        // as a clean repo.
        val found = testFiles()
        assertTrue(
            found.size >= MIN_TEST_FILES,
            "only ${found.size} test files matched, so the scan is broken rather than the " +
                "repo being small",
        )

        val path = "$INTEGRATION_TESTS/docs/DocReferencesResolveTest.kt"
        val neighbour = File(repoRoot(), path)
        assertTrue(neighbour.isFile, "no guard to parse at ${neighbour.absolutePath}")

        val header = headerOf(neighbour)
        assertTrue(
            wordsIn(header).size >= MIN_NEIGHBOUR_WORDS,
            "the parser read almost nothing off a file with a long header, so it would " +
                "report every header in the repo as thin or every one as fine",
        )
        assertEquals(
            null,
            reasonItSaysNothing(path, header),
            "a real header was rejected, so the thin rule is too strict to be trusted",
        )
        assertTrue(
            reasonItSaysNothing(path, listOf("/** Unit tests for the doc reference guard. */")) != null,
            "a header that only restates the class name passed, so the rule is the naive " +
                "one it exists not to be",
        )
    }

    /**
     * The KDoc attached to the file's test class, wherever it sits.
     *
     * Not simply "the first comment in the file", because fixtures get declared
     * above the class. `FloatingWindowHostTest` opens with three route classes
     * and a tag constant, and a parser that stopped at the first declaration
     * reported the best header in the module as missing.
     *
     * So: keep the run of KDoc blocks since the last line of code, and hand it
     * back on reaching the declaration named after the file. Any other
     * declaration clears the run, which is what stops a well documented helper
     * standing in for the header the class does not have. Annotations do not
     * clear it, since they belong to whatever follows them, and neither do
     * `package` or `import`, which is how a KDoc written above `package` still
     * counts.
     *
     * A file whose class is named something else falls back to the run before
     * its first declaration.
     */
    private fun headerOf(file: File): List<String> {
        val named = Regex("""\b(class|object|interface)\s+${Regex.escape(file.nameWithoutExtension)}\b""")
        val pending = mutableListOf<String>()
        var firstDeclaration: List<String>? = null
        var open: StringBuilder? = null

        for (raw in file.readText().lineSequence()) {
            val line = raw.trim()
            val current = open
            if (current != null) {
                current.append('\n').append(line)
                if (line.contains("*/")) {
                    pending += current.toString()
                    open = null
                }
                continue
            }
            when {
                line.startsWith("/**") && line.drop(3).contains("*/") -> pending += line
                line.startsWith("/**") -> open = StringBuilder(line)
                line.isEmpty() -> Unit
                line.startsWith("//") -> Unit
                line.startsWith("@file:") -> Unit
                line.startsWith("@") -> Unit
                line.startsWith("package ") -> Unit
                line.startsWith("import ") -> Unit
                named.containsMatchIn(line) -> return pending.toList()
                else -> {
                    if (firstDeclaration == null) firstDeclaration = pending.toList()
                    pending.clear()
                }
            }
        }
        return firstDeclaration ?: pending.toList()
    }

    /** Null when the header makes a claim, otherwise why it does not. */
    private fun reasonItSaysNothing(path: String, header: List<String>): String? {
        val words = wordsIn(header)
        if (words.size < MIN_WORDS) return "${words.size} words, which is not a sentence about anything"

        val fromTheName = nameWordsIn(path)
        val own = words
            .filter { it.length > SHORT_WORD }
            .filterNot { it in BOILERPLATE }
            .filterNot { it in fromTheName }
            .toSet()
        if (own.size < MIN_OWN_WORDS) {
            return "${own.size} words that are not already in the file's own name"
        }
        return null
    }

    private fun wordsIn(header: List<String>): List<String> {
        val prose = header.joinToString(" ")
            .replace(KDOC_EDGES, " ")
            .replace(LEADING_STAR, " ")
        return WORD.findAll(prose).map { it.value.lowercase() }.toList()
    }

    /**
     * Every word the file's own path already spends, camel-cased names split.
     *
     * The path and not only the class name, because `PlatformHttpEngineTest`
     * sits under `libraries/networking` and a header saying "networking engine
     * tests" is the same non-claim spelled from one directory up.
     */
    private fun nameWordsIn(path: String): Set<String> =
        path.split(NOT_NAME)
            .flatMap { token -> CAMEL.findAll(token).map { it.value.lowercase() } }
            .toSet()

    private fun testFiles(): List<File> {
        val root = File(repoRoot())
        return root.walkTopDown()
            .onEnter { it.name !in NOT_WALKED }
            .filter { it.isFile && it.extension == "kt" }
            .filter { TEST_SOURCE_SET.containsMatchIn("/" + it.toRelativeString(root)) }
            .filter { it.readText().contains("@Test") }
            .toList()
    }

    private fun repoRoot(): String =
        System.getProperty("sodogku.repoRoot")
            ?: error("sodogku.repoRoot is unset; apps/integration/build.gradle.kts supplies it")

    private companion object {
        /**
         * A Kotlin source set whose name contains "test": `commonTest`,
         * `androidUnitTest`, `iosTest`, `jsTest`, the server's plain `test`, and
         * whatever the next module invents.
         */
        val TEST_SOURCE_SET = Regex("""/src/[^/]*[Tt]est[^/]*/""")

        /**
         * Directories the walk never descends into. `.claude` holds agent
         * worktrees, which are full checkouts: without it the scan reads the
         * repo several times over and fails on somebody else's half-written
         * file.
         */
        val NOT_WALKED = setOf("build", ".claude", ".git", ".gradle", "node_modules")

        val WORD = Regex("""[A-Za-z][A-Za-z'-]*""")
        val CAMEL = Regex("""[A-Z]+(?![a-z])|[A-Z][a-z0-9]*|[a-z0-9]+""")
        val KDOC_EDGES = Regex("""/\*\*|\*/""")
        val LEADING_STAR = Regex("""(?m)^\s*\*""")
        val NOT_NAME = Regex("""[^A-Za-z0-9]+""")

        const val INTEGRATION_TESTS =
            "apps/integration/src/androidUnitTest/kotlin/com/sodogku/integration"

        /**
         * Words a header can contain without having said anything, so they do
         * not count toward the floor below.
         *
         * Kept deliberately short. Every addition makes the rule stricter in a
         * way nobody will notice until it rejects a header that was fine, and
         * the floor is already doing most of the work.
         */
        val BOILERPLATE = setOf(
            "the", "this", "that", "these", "those", "and", "but", "for", "its",
            "are", "was", "were", "here", "there", "what", "which", "when",
            "where", "how", "why", "all", "every", "each", "some",
            "test", "tests", "testing", "unit", "units", "case", "cases",
            "suite", "file", "class", "object", "function", "fun",
            "cover", "covers", "covered", "coverage",
            "verify", "verifies", "verified", "check", "checks", "checked",
            "assert", "asserts", "assertion", "assertions",
            "behaviour", "behavior", "behaviours", "behaviors",
        )

        /**
         * Floors, not targets. They sit under what the repo already writes, so
         * ordinary prose never meets them: the shortest header here is fifteen
         * words and the median is eighty.
         */
        const val MIN_WORDS = 12
        const val MIN_OWN_WORDS = 5
        const val SHORT_WORD = 2

        /**
         * A floor on the scan itself, well under the real count so that churn
         * never touches it and a scan reading nothing still fails loudly.
         */
        const val MIN_TEST_FILES = 150
        const val MIN_NEIGHBOUR_WORDS = 100
    }
}
