package com.sodogku.integration.shortcuts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The home-screen long-press entries say the same thing on both platforms, and
 * both point at links the app knows.
 *
 * Three files declare this feature and none of them can see the others. iOS
 * reads `Info.plist` and Android reads `res/xml/shortcuts.xml` before any of our
 * code runs, so neither can be generated from Kotlin — and the Kotlin side is
 * where the plist's opaque item types are turned into URLs. A constant renamed
 * on one side is a compile error nowhere and a menu row that does nothing on a
 * phone, which is a bug nobody finds without a phone.
 *
 * So all three are read as text and compared. The same trade as
 * `LaunchScreenMatchesTheAppTest` next door, and for the same reason: the
 * literal is what ships.
 *
 * Not covered here: whether a URL matches the route it is supposed to, which is
 * `AppShortcutDeepLinksTest` in `:apps:compose` against the generated patterns.
 */
class ShortcutEntriesAgreeTest {

    @Test
    fun iosNamesTheTypesKotlinKnows() {
        assertEquals(
            listOf(constants().getValue("DailyChallengeType"), constants().getValue("ReportBugType")),
            plistTypes(),
            "Info.plist and AppShortcuts.kt name different quick-action types, so tapping " +
                "an entry falls through urlFor and opens nothing",
        )
    }

    @Test
    fun everyTypeIsOneUrlForAnswers() {
        // The constants can exist and still not be wired: `urlFor` is a `when`
        // over them, and a branch is easy to forget when adding an entry.
        val source = appShortcuts().readText()
        listOf("DailyChallengeType", "ReportBugType").forEach { name ->
            assertTrue(
                Regex("""$name\s*->""").containsMatchIn(source),
                "urlFor has no branch for $name",
            )
        }
    }

    @Test
    fun androidCarriesTheSameLinks() {
        assertEquals(
            listOf(constants().getValue("DailyChallengeUrl"), constants().getValue("ReportBugUrl")),
            androidShortcuts().map { it.data },
            "shortcuts.xml and AppShortcuts.kt disagree about where an entry goes",
        )
    }

    @Test
    fun bothPlatformsOfferTheSameEntriesInTheSameOrder() {
        assertEquals(
            listOf("daily-challenge", "report-bug"),
            androidShortcuts().map { it.id },
            "the Android entries are not the two iOS offers, or are the other way round",
        )
    }

    @Test
    fun bothPlatformsUseTheSameWords() {
        val labels = androidStrings()
        assertEquals(
            plistTitles(),
            androidShortcuts().map { labels.getValue(it.longLabel) },
            "a player sees one wording on iOS and another on Android",
        )
    }

    @Test
    fun androidStillDeclaresTheShortcutsAtAll() {
        // res/xml/shortcuts.xml is inert until the manifest points an activity
        // at it. Without this, deleting one line leaves a resource nothing
        // reads, a launcher menu with no entries, and every test above green.
        val manifest = File(repoRoot(), "apps/compose/src/androidMain/AndroidManifest.xml").readText()

        assertTrue(
            Regex("""android:name="android\.app\.shortcuts"""").containsMatchIn(manifest),
            "no activity declares android.app.shortcuts",
        )
        assertTrue(
            """android:resource="@xml/shortcuts"""" in manifest,
            "the shortcuts meta-data points at something other than @xml/shortcuts",
        )
    }

    @Test
    fun theRoutesAreRegisteredAgainstTheseBasePaths() {
        // The URLs above are checked against the *patterns* the routes generate,
        // in `AppShortcutDeepLinksTest`, which builds those patterns from these
        // same constants. Nothing there notices a registration site that quietly
        // used a literal instead, so a base path could be changed in one place
        // and everything would still pass.
        assertRegistersBasePath(
            source = "features/game/impl/src/commonMain/kotlin/com/sodogku/features/game/impl/" +
                "GameFeatureEntryPoint.kt",
            route = "GameRoute",
            constant = "GameBasePath",
        )
        assertRegistersBasePath(
            source = "features/settings/impl/src/commonMain/kotlin/com/sodogku/features/settings/impl/" +
                "SettingsFeatureEntryPoint.kt",
            route = "FeedbackRoute",
            constant = "FeedbackBasePath",
        )
    }

    @Test
    fun theSwiftSideStillAsksKotlinWhereToGo() {
        // The plist carries a type and no URL on purpose. If Swift stops asking
        // Kotlin, the type strings above are checked against a mapping nothing
        // reads, and every test here keeps passing.
        val swift = File(repoRoot(), "apps/ios/iosApp/iOSApp.swift")
        assertTrue(swift.isFile, "no iOS entry point at ${swift.absolutePath}")
        assertTrue(
            "deepLinkForShortcut" in swift.readText(),
            "iOSApp.swift no longer resolves a tapped entry through AppShortcuts",
        )
    }

    @Test
    fun everyParseFoundSomething() {
        // The guard against the guard. Each comparison above is between two
        // parses, and two parses that both returned nothing agree perfectly.
        assertEquals(2, plistTypes().size, "the plist parse found ${plistTypes().size} entries")
        assertEquals(2, plistTitles().size, "the plist title parse found ${plistTitles().size}")
        assertEquals(2, androidShortcuts().size, "the shortcuts.xml parse found nothing")
        assertTrue(
            constants().keys.containsAll(ExpectedConstants),
            "AppShortcuts.kt parsed as ${constants().keys}, missing $ExpectedConstants",
        )
        assertTrue(
            androidShortcuts().all { it.data.isNotEmpty() && it.longLabel.isNotEmpty() },
            "a shortcut parsed with an empty field, so the comparisons above are hollow",
        )
        assertTrue(
            androidStrings().keys.containsAll(androidShortcuts().map { it.longLabel }),
            "a shortcut label names a string resource that does not exist",
        )
    }

    private fun assertRegistersBasePath(source: String, route: String, constant: String) {
        val file = File(repoRoot(), source)
        assertTrue(file.isFile, "no entry point at ${file.absolutePath}")
        assertTrue(
            Regex("""routeDeepLink<$route>\(\s*basePath\s*=\s*AppShortcuts\.$constant""")
                .containsMatchIn(file.readText()),
            "$source does not register $route against AppShortcuts.$constant",
        )
    }

    private data class AndroidShortcut(val id: String, val data: String, val longLabel: String)

    /**
     * The `const val NAME: String = "value"` declarations in `AppShortcuts.kt`.
     *
     * Interpolated values are deliberately not matched: `"$Base/x"` read as text
     * is not the string that ships, so a constant that grows an interpolation
     * drops out of this map and [everyParseFoundSomething] says so.
     */
    private fun constants(): Map<String, String> =
        Regex("""const val (\w+): String = "([^"$]*)"""")
            .findAll(appShortcuts().readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun plistTypes(): List<String> = plistValues("UIApplicationShortcutItemType")

    private fun plistTitles(): List<String> = plistValues("UIApplicationShortcutItemTitle")

    private fun plistValues(key: String): List<String> =
        Regex("""<key>$key</key>\s*<string>([^<]*)</string>""")
            .findAll(shortcutItemsArray())
            .map { it.groupValues[1] }
            .toList()

    /** Just the `UIApplicationShortcutItems` array, so no other key can match. */
    private fun shortcutItemsArray(): String {
        val plist = File(repoRoot(), "apps/ios/iosApp/Info.plist")
        assertTrue(plist.isFile, "no plist at ${plist.absolutePath}")

        val text = plist.readText()
        val start = text.indexOf(ShortcutItemsKey)
        assertTrue(start >= 0, "Info.plist declares no $ShortcutItemsKey")
        val end = text.indexOf("</array>", start)
        assertTrue(end > start, "the $ShortcutItemsKey array is never closed")
        return text.substring(start, end)
    }

    private fun androidShortcuts(): List<AndroidShortcut> {
        val xml = File(repoRoot(), "apps/compose/src/androidMain/res/xml/shortcuts.xml")
        assertTrue(xml.isFile, "no shortcuts at ${xml.absolutePath}")

        // From the root tag, so the comment above it cannot be read as content.
        val body = xml.readText().substringAfter("<shortcuts")
        return Regex("""<shortcut\b(.*?)</shortcut>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body)
            .map { match ->
                val block = match.groupValues[1]
                AndroidShortcut(
                    id = block.attribute("shortcutId"),
                    data = block.attribute("data"),
                    longLabel = block.attribute("shortcutLongLabel").removePrefix("@string/"),
                )
            }
            .toList()
    }

    private fun String.attribute(name: String): String =
        Regex("""android:$name="([^"]*)"""").find(this)?.groupValues?.get(1).orEmpty()

    private fun androidStrings(): Map<String, String> {
        val strings = File(repoRoot(), "apps/compose/src/androidMain/res/values/strings.xml")
        assertTrue(strings.isFile, "no strings at ${strings.absolutePath}")

        return Regex("""<string name="([^"]+)">([^<]*)</string>""")
            .findAll(strings.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun appShortcuts(): File {
        val source = File(
            repoRoot(),
            "libraries/navigation/src/commonMain/kotlin/com/sodogku/libraries/navigation/AppShortcuts.kt",
        )
        assertTrue(source.isFile, "no shortcut constants at ${source.absolutePath}")
        return source
    }

    private fun repoRoot(): String =
        System.getProperty(REPO_ROOT_PROPERTY)
            ?: error("$REPO_ROOT_PROPERTY is unset — apps/integration/build.gradle.kts supplies it")

    private companion object {
        const val REPO_ROOT_PROPERTY = "sodogku.repoRoot"
        const val ShortcutItemsKey = "<key>UIApplicationShortcutItems</key>"
        val ExpectedConstants = setOf(
            "DailyChallengeType",
            "ReportBugType",
            "DailyChallengeUrl",
            "ReportBugUrl",
            "GameBasePath",
            "FeedbackBasePath",
        )
    }
}
