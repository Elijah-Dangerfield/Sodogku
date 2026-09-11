package com.sodogku.integration.copy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * House style for the words a player actually reads.
 *
 * Two style rules to begin with, both of which had already been broken by the
 * time this was written, and both of which are the kind of thing that is
 * invisible in review because each individual instance looks fine. It is only
 * across a whole screen that seven em dashes read as one voice and "colour" next
 * to "color" reads as two.
 *
 * **No em dashes.** They are the default connector when prose is drafted quickly
 * and they pile up: `rules_body`, both tutorial cards, the sniff explainer, both
 * score explainers and the dog-counter dialog all had one. A comma or a full
 * stop says the same thing, and a full stop is usually better in a dialog a
 * player is skimming.
 *
 * **One spelling of colour.** The app is American throughout ("1 dog per color"
 * on the rule chip the player sees on every board), and exactly one string had
 * drifted British. A player who sees both in one session notices, even if they
 * could not say what they noticed.
 *
 * The style rules deliberately check only `<string>` bodies. The comments in that
 * file are for whoever edits it next and are not held to either.
 *
 * The rest of the file is about what the batch **costs**, which is a per-string,
 * per-language price paid by a person: one file to send rather than several, and
 * nothing in it that no player will ever read.
 */
class UserFacingCopyStyleTest {

    @Test
    fun noStringUsesAnEmDash() {
        val offenders = strings().filter { (_, body) -> EM_DASH in body }

        assertTrue(
            offenders.isEmpty(),
            "These strings use an em dash. Use a comma or a full stop:\n" +
                offenders.joinToString("\n") { (name, body) -> "  $name: $body" },
        )
    }

    @Test
    fun colourIsSpelledTheAmericanWay() {
        // Word-boundary matched, so "colourblind" would be caught too but
        // "col" or a longer word containing the letters would not.
        val offenders = strings().filter { (_, body) -> Regex("""\bcolour""", RegexOption.IGNORE_CASE) in body }

        assertTrue(
            offenders.isEmpty(),
            "The app says \"color\" everywhere else, including the rule chip on every board:\n" +
                offenders.joinToString("\n") { (name, body) -> "  $name: $body" },
        )
    }

    @Test
    fun theScanFindsTheStringsAndCouldReportAViolation() {
        // The guard against the guard. A regex that stopped matching would make
        // both tests above pass over an empty list, which is exactly what they
        // would look like if they were working.
        val all = strings()

        assertTrue(all.size > MIN_STRINGS, "only found ${all.size} strings, so the scan is not reading the file")
        assertTrue(
            all.any { (name, _) -> name == "dogs_body" },
            "a known string is missing from the scan, so the parse is dropping entries",
        )
        assertTrue(
            all.any { (_, body) -> body.length > SENTENCE },
            "no string is longer than a fragment, so the scan is matching names but not bodies",
        )
    }

    @Test
    fun noResourceCommentContainsADoubleHyphen() {
        // Not style. `--` is illegal inside an XML comment, and the build
        // failure it produces is `convertXmlValueResourcesForCommonMain task was
        // failed` with no file, no line and no mention of hyphens. Written after
        // hitting it twice in one day while using `--` as a dash in a comment
        // explaining why not to use em dashes.
        val offenders = resourceFiles().flatMap { file ->
            COMMENT.findAll(file.readText())
                .filter { "--" in it.value.removePrefix("<!--").removeSuffix("-->") }
                .map { "  ${file.name}: ${it.value.take(70).replace('\n', ' ')}" }
        }

        assertTrue(
            offenders.isEmpty(),
            "`--` cannot appear inside an XML comment. Use a semicolon or a full stop:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun noStringEscapesAnApostrophe() {
        // `\'` is an Android resource convention the Compose Multiplatform
        // parser does not share: at best the backslash renders on screen, at
        // worst the file stops parsing.
        // Every resource file, not only the shared one. Scoping this to
        // `strings()` missed the per-feature files entirely, which is where the
        // escape was actually written.
        val offenders = resourceFiles().flatMap { file ->
            STRING.findAll(file.readText())
                .filter { "\\'" in it.groupValues[2] }
                .map { "  ${file.name}: ${it.groupValues[1]}: ${it.groupValues[2]}" }
        }

        assertTrue(
            offenders.isEmpty(),
            "Apostrophes are bare in Compose resources:\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun onlyTheResourcesModuleDeclaresStrings() {
        // What a translator is sent is a file, and what comes back is a file.
        // A second one that a feature happens to own has to be found, sent,
        // reconciled and merged separately, and that cost is paid again on
        // every language rather than once.
        //
        // `features/streak/impl` had its own, deliberately: nothing outside the
        // feature rendered that copy and a shared file is a shared merge
        // conflict. That is a fair argument and it lost, so this is here to
        // stop it being re-won quietly by whoever adds the next feature.
        //
        // Scoped to `composeResources`. `apps/compose/src/androidMain/res` also
        // has a `values/strings.xml` and has to: the manifest and the launcher
        // shortcuts read it before any Kotlin runs.
        val owner = File(repoRoot(), OWNER)
        val strays = composeResourceStrings().filterNot { it.startsWith(owner) }

        assertTrue(
            strays.isEmpty(),
            "Player-facing copy lives in $OWNER so that one file is the whole " +
                "translation batch. Move these into it:\n" +
                strays.joinToString("\n") { "  ${it.relativeTo(File(repoRoot()))}" },
        )
    }

    @Test
    fun everyStringIsRenderedBySomething() {
        // Same cost as the rule above, from the other end: a key nothing draws
        // is still a row in the batch, still priced per language, and still a
        // decision a translator has to make. Twelve were in the file when this
        // was written, among them copy for a `StreakDayState` that does not
        // exist and a second spelling of the dogs counter.
        val used = referencedKeys()
        val orphans = keys().filterNot { it in used || it in KEPT }

        assertTrue(
            orphans.isEmpty(),
            "Nothing renders these, so they would be paid for in every language and read in none. " +
                "Delete them, or add them to KEPT with the reason:\n" +
                orphans.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun theReferenceScanTellsAUseFromAnImport() {
        // The guard against that guard, and the half that is easy to get wrong.
        // `Res.string.foo` is an extension property, so it needs `foo` imported
        // by name: an import is where a dead key hides rather than evidence
        // anything draws it. `StreakScreen` imported five of the twelve and read
        // none of them, and detekt cannot say so because the default rule sets
        // are turned off.
        val used = referencedKeys()

        assertTrue("streak_title" in used, "a string the streak page draws is missing from the scan")
        assertTrue(
            GENERATED_IMPORT.matches("import sodogku.libraries.resources.generated.resources.streak_title"),
            "the import filter has stopped recognising an import, so every import counts as a use",
        )
        assertFalse(
            GENERATED_IMPORT.matches("text = stringResource(Res.string.streak_title),"),
            "the import filter is eating uses, so every string would read as dead",
        )
    }

    /** Every key a translator is handed: `<plurals>` is a row like any other. */
    private fun keys(): List<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"""")
            .findAll(sharedStrings().readText())
            .map { it.groupValues[1] }
            .toList()

    /**
     * Every word the app's Kotlin says, minus the resource imports.
     *
     * Word-matched rather than parsed, because both spellings of a reference
     * (`Res.string.foo` and a bare `foo` under a wildcard import) contain the
     * key and nothing else in the tree is named like one. The cost is that a
     * key named in a comment reads as used, which is a false negative and the
     * safe direction: this test deletes copy.
     *
     * `apps/integration` is left out on purpose. This file names keys in its own
     * assertions, and a scan that counted its own prose would vouch for whatever
     * it happened to mention.
     */
    private fun referencedKeys(): Set<String> {
        val root = File(repoRoot())
        val files = listOf("libraries", "features", "apps")
            .map { File(root, it) }
            .flatMap { tree ->
                tree.walkTopDown()
                    .onEnter { it.name !in setOf("build", ".git", ".claude") }
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }
            .filterNot { it.startsWith(File(root, HARNESS)) }

        assertTrue(files.size >= MIN_KOTLIN_FILES, "only found ${files.size} Kotlin files, so the walk is broken")

        return files.flatMapTo(mutableSetOf()) { file ->
            file.readLines()
                .filterNot { GENERATED_IMPORT.matches(it.trim()) }
                .flatMap { line -> WORD.findAll(line).map { it.value } }
        }
    }

    private fun sharedStrings(): File {
        val file = File(repoRoot(), SHARED_STRINGS)
        assertTrue(file.isFile, "no strings at ${file.absolutePath}")
        return file
    }

    /**
     * Every `strings.xml` under a `composeResources` folder, in any module.
     *
     * The locale folder is matched on a `values` prefix rather than the exact
     * name, so `values-es` is covered the day the first translation lands,
     * which is the whole reason this rule exists.
     */
    private fun composeResourceStrings(): List<File> {
        val files = File(repoRoot()).walkTopDown()
            .onEnter { it.name !in setOf("build", ".git", ".claude") }
            .filter { it.isFile && it.name == "strings.xml" }
            .filter { it.parentFile.name.startsWith("values") }
            .filter { it.parentFile.parentFile?.name == "composeResources" }
            .toList()

        // A floor, so a walk that stopped matching reports it instead of
        // passing over an empty list, which is what a working run looks like.
        assertTrue(files.isNotEmpty(), "found no composeResources strings.xml at all, so the walk is broken")
        return files
    }

    /** Every `values/strings.xml` in the repo, not only the shared one. */
    private fun resourceFiles(): List<File> {
        val root = File(repoRoot())
        val files = root.walkTopDown()
            // `.claude` holds agent worktrees, which are full checkouts of this
            // repo. Without excluding it the walk scans every worktree's copy of
            // every strings.xml, so a rule fails against a file that is not in
            // the tree and may be several commits stale.
            .onEnter { it.name !in setOf("build", ".git", ".claude") }
            .filter { it.isFile && it.name == "strings.xml" && it.parentFile.name == "values" }
            .toList()

        assertTrue(files.size >= MIN_RESOURCE_FILES, "only found ${files.size} strings.xml files")
        return files
    }

    /** Every `<string name="...">body</string>` in the shared resources. */
    private fun strings(): List<Pair<String, String>> =
        STRING.findAll(sharedStrings().readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    private fun repoRoot(): String =
        System.getProperty("sodogku.repoRoot")
            ?: error("sodogku.repoRoot is unset — apps/integration/build.gradle.kts supplies it")

    private operator fun Regex.contains(text: String): Boolean = containsMatchIn(text)

    private companion object {
        const val EM_DASH = "—"

        /** A floor, so a broken parse reports "found nothing" rather than passing. */
        const val MIN_STRINGS = 200

        /** Long enough that a body was captured rather than just an attribute. */
        const val SENTENCE = 60

        /** The one module that owns player-facing copy. */
        const val OWNER = "libraries/resources/src/commonMain/composeResources"

        const val SHARED_STRINGS = "$OWNER/values/strings.xml"

        /** This module: the scan must not be able to vouch for itself. */
        const val HARNESS = "apps/integration"

        /**
         * Keys kept without a caller, and why.
         *
         * Empty, and the burden is on whoever adds the first entry: a string
         * here is one a translator is paid for and no player ever sees, so the
         * reason has to be better than "we might need it". Copy for a feature
         * that is not built yet belongs in the branch that builds it.
         */
        val KEPT = emptySet<String>()

        /**
         * A floor under the Kotlin walk, so a walk that found nothing reports
         * it rather than declaring every string dead and deleting the lot.
         */
        const val MIN_KOTLIN_FILES = 500

        val GENERATED_IMPORT = Regex("""import\s+[\w.]+\.generated\.resources\.\w+""")

        val WORD = Regex("""\w+""")

        /**
         * The shared file plus the Android app's own `res/values/strings.xml`,
         * which stays where it is because the manifest reads it. A floor, so a
         * broken walk reports it rather than passing over nothing.
         */
        const val MIN_RESOURCE_FILES = 2

        val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

        val STRING = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    }
}
