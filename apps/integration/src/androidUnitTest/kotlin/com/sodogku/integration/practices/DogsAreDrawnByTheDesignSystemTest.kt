package com.sodogku.integration.practices

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The design system draws every dog, so only it has to know when one may move.
 *
 * SD-103 came out of three composables that each drew a dog and each decided for
 * themselves whether to animate it. Two consulted `LocalInspectionMode` and one
 * did not, so a preview holding a board looped until the tool gave up. The
 * refactor put the decision in `Dog`; this is what keeps it there, because a
 * refactor is a state and a guard is a rule.
 *
 * The rule: no Kotlin file outside the design system's `components/dog` package
 * names a dog drawable. A screen that wants a dog asks `Dog` for one and gets
 * the stillness rule with it. A screen that reaches for `Res.drawable.dog_*`
 * has quietly opened a fourth place where that rule has to be remembered, and
 * the symptom will be a preview that hangs six months from now.
 *
 * The drawable names are read off disk rather than listed here, so a sprite
 * sheet added tomorrow is covered by a rule written today. That is also why the
 * scan has a floor: a glob that stopped matching would report a clean repo in
 * the same words as a clean repo.
 *
 * ### The second assertion
 *
 * Inside the package, the inspection check itself lives in one file. Splitting
 * it across the poses and the sprites would satisfy the rule above and put the
 * repo back where it started, one package smaller.
 *
 * ### What this cannot catch
 *
 * A dog drawn from art that is not called `dog_something`, and a caller that
 * gets a dog through `Dog` and then wraps it in its own endless animation.
 * Neither is what went wrong, and a rule broad enough to catch the second would
 * have to understand what an animation is.
 *
 * ### The Gradle half
 *
 * The scan happens at test runtime, which Gradle cannot see, so
 * `apps/integration/build.gradle.kts` declares both the Kotlin tree and the
 * drawable directory with `inputs.files(...)`. Without the second one, adding a
 * sprite sheet and a call site that misuses it in the same commit can leave this
 * task UP-TO-DATE. That hole has blinded four separate tests in this module.
 */
class DogsAreDrawnByTheDesignSystemTest {

    @Test
    fun onlyTheDesignSystemNamesADogDrawable() {
        val root = File(repoRoot())
        val names = dogDrawableNames()
        val word = Regex("""\b(${names.joinToString("|") { Regex.escape(it) }})\b""")

        val trespassers = kotlinFiles()
            .filterNot { it.invariantPath(root).contains(DOG_PACKAGE) }
            .mapNotNull { file ->
                val hit = word.find(file.readText()) ?: return@mapNotNull null
                "  ${file.toRelativeString(root)}: ${hit.value}"
            }
            .sorted()

        assertTrue(
            trespassers.isEmpty(),
            "These files name a dog drawable directly, which puts the decision about whether " +
                "a dog may animate back at the call site — the shape SD-103 removed. Draw it " +
                "with Dog(pose = …, motion = …) from $DOG_PACKAGE instead:\n" +
                trespassers.joinToString("\n"),
        )
    }

    @Test
    fun oneFileInThatPackageDecidesWhetherADogMoves() {
        val root = File(repoRoot())
        val deciders = kotlinFiles()
            .filter { it.invariantPath(root).contains(DOG_PACKAGE) }
            .filter { it.readText().contains(INSPECTION_LOCAL) }
            .map { it.name }
            .sorted()

        assertEquals(
            listOf(DECIDING_FILE),
            deciders,
            "$INSPECTION_LOCAL is read in ${deciders.size} files inside the dog package. One " +
                "component owning the decision was the point; spreading it over the package " +
                "is the same bug with a shorter blast radius",
        )
    }

    @Test
    fun theScanReadsTheRepoAndTheDesignSystemItselfWouldFail() {
        // Both assertions above are "find the offenders", so a glob that matched
        // nothing reports success in the same words as a clean repo.
        val names = dogDrawableNames()
        assertTrue(
            names.size >= MIN_DRAWABLES,
            "only ${names.size} dog drawables matched, so the art glob is broken rather than " +
                "the set being small",
        )

        val scanned = kotlinFiles()
        assertTrue(
            scanned.size >= MIN_KOTLIN_FILES,
            "only ${scanned.size} Kotlin files matched, so the walk is broken and the rule " +
                "above passed because it read nothing",
        )

        val root = File(repoRoot())
        val owners = scanned.filter { it.invariantPath(root).contains(DOG_PACKAGE) }
        assertTrue(
            owners.isNotEmpty(),
            "nothing was found at $DOG_PACKAGE, so either the component moved and the rule " +
                "now exempts a directory that does not exist, or the path separator handling " +
                "is wrong and every file in the repo is exempt",
        )

        val word = Regex("""\b(${names.joinToString("|") { Regex.escape(it) }})\b""")
        assertTrue(
            owners.any { word.containsMatchIn(it.readText()) },
            "the design system's own dog package names no dog drawable, so the matcher does " +
                "not match what it is looking for and the rule can never fail",
        )
    }

    private fun dogDrawableNames(): List<String> {
        val art = File(repoRoot(), DRAWABLE_DIRECTORY)
        assertTrue(art.isDirectory, "no drawable directory at ${art.absolutePath}")
        val names = art.listFiles().orEmpty()
            .filter { it.isFile && it.nameWithoutExtension.startsWith(DOG_PREFIX) }
            .map { it.nameWithoutExtension }
            .sorted()
        // An empty alternation matches the empty string at every position, so
        // the rule above would report every file in the repo rather than none
        // of them. Loud either way, but the message would be a lie.
        assertTrue(names.isNotEmpty(), "no dog art at ${art.absolutePath}")
        return names
    }

    private fun kotlinFiles(): List<File> {
        val root = File(repoRoot())
        return root.walkTopDown()
            .onEnter { it.name !in NOT_WALKED }
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    private fun File.invariantPath(root: File): String =
        "/" + toRelativeString(root).replace(File.separatorChar, '/')

    private fun repoRoot(): String =
        System.getProperty("sodogku.repoRoot")
            ?: error("sodogku.repoRoot is unset; apps/integration/build.gradle.kts supplies it")

    private companion object {
        const val DRAWABLE_DIRECTORY = "libraries/resources/src/commonMain/composeResources/drawable"

        /** Matches the art naming convention, not any one file. */
        const val DOG_PREFIX = "dog"

        const val DOG_PACKAGE = "/libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/dog/"

        const val INSPECTION_LOCAL = "LocalInspectionMode"
        const val DECIDING_FILE = "Dog.kt"

        /**
         * Directories the walk never descends into. `.claude` holds agent
         * worktrees, which are full checkouts: without it the scan reads the
         * repo several times over and fails on somebody else's half-written
         * file.
         */
        val NOT_WALKED = setOf("build", ".claude", ".git", ".gradle", "node_modules")

        /**
         * Floors, well under the real counts, so ordinary churn never touches
         * them and a scan reading nothing still fails loudly.
         */
        const val MIN_DRAWABLES = 10
        const val MIN_KOTLIN_FILES = 400
    }
}
