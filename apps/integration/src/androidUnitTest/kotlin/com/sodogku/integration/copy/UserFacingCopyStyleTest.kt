package com.sodogku.integration.copy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * House style for the words a player actually reads.
 *
 * Two rules, both of which had already been broken by the time this was written,
 * and both of which are the kind of thing that is invisible in review because
 * each individual instance looks fine. It is only across a whole screen that
 * seven em dashes read as one voice and "colour" next to "color" reads as two.
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
 * This deliberately checks only `<string>` bodies. The comments in that file are
 * for whoever edits it next and are not held to either rule.
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

    /** Every `values/strings.xml` in the repo, not only the shared one. */
    private fun resourceFiles(): List<File> {
        val root = File(repoRoot())
        val files = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" }
            .filter { it.isFile && it.name == "strings.xml" && it.parentFile.name == "values" }
            .toList()

        assertTrue(files.size >= MIN_RESOURCE_FILES, "only found ${files.size} strings.xml files")
        return files
    }

    /** Every `<string name="...">body</string>` in the shared resources. */
    private fun strings(): List<Pair<String, String>> {
        val file = File(
            repoRoot(),
            "libraries/resources/src/commonMain/composeResources/values/strings.xml",
        )
        assertTrue(file.isFile, "no strings at ${file.absolutePath}")

        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
    }

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

        /** Shared resources plus the per-feature ones. A floor, so a broken walk reports it. */
        const val MIN_RESOURCE_FILES = 2

        val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

        val STRING = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    }
}
