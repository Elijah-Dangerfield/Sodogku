package com.sodogku.integration.config

import com.sodogku.libraries.config.impl.model.BasicMapAppConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The guard on the guards.
 *
 * Every config check the repo has starts from a hand-written enumeration —
 * `SodogkuConfigValues` for the keys `:libraries:config` can see, and
 * [declaredConfigValues] for the ones declared in impl modules it cannot. The
 * runtime source of truth is the graph's `Set<QaConfigValue>` multibinding, which
 * no unit test can resolve, so the enumerations are a transcription, and a
 * transcription with nothing holding it to its subject drifts.
 *
 * It had drifted. `config.refreshThrottleMs` is declared in
 * `:libraries:config:impl` next to the repository that reads it, and because it
 * was in neither enumeration it was in neither completeness test: no bundled
 * fallback, no admin registry entry, no failure anywhere. Adding the key fixes
 * the key. This fixes the reason a key could go missing, which is that the
 * enumerations were checked against each other and against the fallback map, and
 * never against the declarations themselves.
 *
 * So this reads the declarations out of the source tree instead. Textual, which
 * is the cost of not having the DI graph: it sees `override val path = "…"` and
 * nothing else, and [everyPathDeclaredInSourceIsWrittenAsALiteral] fails rather
 * than skipping a declaration it cannot parse.
 */
class ConfigDeclarationsAreEnumeratedTest {

    private val enumerated: Set<String> =
        declaredConfigValues(BasicMapAppConfig(emptyMap<String, Any>())).map { it.path }.toSet()

    @Test
    fun everyPathDeclaredInSourceIsEnumerated() {
        val unenumerated = declarationsInSource().map { it.path }.toSet() - enumerated

        assertTrue(
            unenumerated.isEmpty(),
            "These config keys are declared in a ConfiguredValue class that no enumeration " +
                "names:\n" + unenumerated.sorted().joinToString("\n") { "  $it" } +
                "\n\nA key nothing enumerates has no bundled fallback, no admin registry entry " +
                "and no test that would notice. Add the class to SodogkuConfigValues if it lives " +
                "in :libraries:config, or to declaredConfigValues() if it does not.",
        )
    }

    @Test
    fun everyEnumeratedPathIsDeclaredInSource() {
        // The half that stops the test above passing vacuously. A scan pointed at
        // the wrong root, or a regex that stopped matching, finds nothing, and
        // "nothing unenumerated" is exactly what a broken scan reports.
        val undeclared = enumerated - declarationsInSource().map { it.path }.toSet()

        assertTrue(
            undeclared.isEmpty(),
            "These paths are enumerated but the scan found no `override val path` declaring " +
                "them:\n" + undeclared.sorted().joinToString("\n") { "  $it" } +
                "\n\nEither a value class was deleted and its enumeration entry left behind, or " +
                "this scan is no longer reading the source it thinks it is.",
        )
    }

    @Test
    fun everyPathDeclaredInSourceIsWrittenAsALiteral() {
        // A `path` built from a constant or an interpolation reads as no
        // declaration at all to a text scan, so it would slip through
        // everyPathDeclaredInSourceIsEnumerated silently — the same failure this
        // whole test exists to stop. Fail on it instead.
        val unparsed = configSourceFiles().flatMap { file ->
            file.readLines()
                .filter { PathDeclaration.containsMatchIn(it) && PathLiteral.find(it) == null }
                .map { "${file.path}: ${it.trim()}" }
        }

        assertTrue(
            unparsed.isEmpty(),
            "These path declarations are not plain string literals, so this scan cannot read " +
                "them:\n" + unparsed.joinToString("\n") { "  $it" } +
                "\n\nWrite the path as a literal, or teach this scan to resolve it.",
        )
    }

    @Test
    fun theScanCanActuallyFail() {
        val found = declarationsInSource()

        assertTrue(
            found.size >= MINIMUM_DECLARATIONS,
            "the scan found ${found.size} declarations, which is fewer than the app has, " +
                "so it is reading the wrong tree",
        )
        assertTrue(
            found.any { it.path == "config.refreshThrottleMs" },
            "the scan missed the key declared outside :libraries:config, which is the one " +
                "case it exists for",
        )
        assertTrue(
            found.none { it.path == "progression.levelLadder" },
            "the scan counted a KDoc example as a declaration — that path is only ever written " +
                "inside the JsonConfigValue doc comment",
        )
        assertTrue(
            found.none { it.file.path.contains("Test.kt") },
            "the scan read a test's throwaway value class as a shipped declaration:\n" +
                found.filter { it.file.path.contains("Test.kt") }.joinToString("\n") { "  ${it.file}" },
        )
    }

    private fun declarationsInSource(): List<Declaration> = configSourceFiles().flatMap { file ->
        file.readLines().mapNotNull { line ->
            PathLiteral.find(line)?.let { Declaration(it.groupValues[1], file) }
        }
    }

    /**
     * Shipped source that could hold a `ConfiguredValue`. A value class has to
     * name the base it extends, so a file mentioning neither name cannot declare
     * one — which keeps an unrelated `override val path` (a route, a resource
     * handle) out of the scan.
     */
    private fun configSourceFiles(): List<File> {
        val root = System.getProperty(REPO_ROOT_PROPERTY)
            ?: error("$REPO_ROOT_PROPERTY is unset — apps/integration/build.gradle.kts should supply it")

        val files = listOf("libraries", "features", "apps")
            .map { File(root, it) }
            .flatMap { dir -> dir.walkTopDown().onEnter { it.name !in NOT_SOURCE }.toList() }
            .filter { it.isShippedKotlin() }

        assertTrue(files.size > MIN_SOURCE_FILES, "only found ${files.size} source files to scan")

        return files.filter { file ->
            val text = file.readText()
            ConfigBaseNames.any { it in text }
        }
    }

    /**
     * `commonMain` and the platform source sets, not tests. A test fixture is
     * free to declare a throwaway value class with a made-up path, and one of
     * them does.
     */
    private fun File.isShippedKotlin(): Boolean {
        if (!isFile || extension != "kt") return false
        val parts = path.split(File.separator)
        return "build" !in parts && "commonTest" !in parts &&
            "androidUnitTest" !in parts && "test" !in parts
    }

    private data class Declaration(val path: String, val file: File)

    private companion object {
        const val REPO_ROOT_PROPERTY = "sodogku.repoRoot"

        /** A floor, so a scan that reads almost nothing reports it rather than passing. */
        const val MINIMUM_DECLARATIONS = 40
        const val MIN_SOURCE_FILES = 200

        /** `.claude` holds agent worktrees, which are full checkouts of this repo. */
        val NOT_SOURCE = setOf("build", ".git", ".claude")

        val ConfigBaseNames = listOf("ConfigValue", "ConfiguredValue")

        /**
         * Anchored at the start of the line so a KDoc example, which is indented
         * behind its `*`, does not match.
         */
        val PathDeclaration = Regex("""^\s*override\s+val\s+path\b""")
        val PathLiteral = Regex("""^\s*override\s+val\s+path\s*(?::\s*String\s*)?=\s*"([^"]+)"\s*$""")
    }
}
