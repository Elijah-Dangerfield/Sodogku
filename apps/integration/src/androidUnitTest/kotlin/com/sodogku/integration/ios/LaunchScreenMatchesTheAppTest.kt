package com.sodogku.integration.ios

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The iOS launch screen is the same cream the app is.
 *
 * iOS paints the launch screen before any Kotlin runs, from `Info.plist` and an
 * asset catalog it reads on its own. Nothing connects that colour to the design
 * system, so the two can disagree, and when they do the failure is a white or
 * off-cream flash on every cold start — the exact thing the launch screen was
 * added to remove. It is also the kind of mismatch that never shows up in a
 * screenshot test, because by the time the app can screenshot itself the launch
 * screen is gone.
 *
 * So this reads both sides as data and compares them. Changing the palette
 * without changing the catalog fails here rather than on a device.
 *
 * Both sides are read as text, including the Kotlin one. `:apps:integration` has
 * no Compose on its classpath, and pulling `:libraries:ui` in for a single colour
 * would put the whole design system behind every test in this module. Reading the
 * literal is not weaker than reading the parsed value: the literal is what ships.
 */
class LaunchScreenMatchesTheAppTest {

    @Test
    fun theLaunchColorIsTheAppBackground() {
        assertEquals(
            paletteComponents(),
            launchColorComponents(),
            "LaunchBackground.colorset and ColorResource.Cream50 have drifted, so iOS " +
                "flashes the wrong colour before the app draws",
        )
    }

    @Test
    fun theLaunchScreenIsDeclared() {
        // The colorset is inert unless Info.plist names it. Without UILaunchScreen
        // iOS falls back to a blank system screen and the catalog entry above is
        // a file nothing reads.
        val plist = File(repoRoot(), "apps/ios/iosApp/Info.plist").readText()

        assertTrue("<key>UILaunchScreen</key>" in plist, "no launch screen is declared")
        assertTrue(
            "<string>LaunchBackground</string>" in plist,
            "the launch screen does not point at the LaunchBackground colorset",
        )
    }

    @Test
    fun bothParsesWouldNoticeAWrongColor() {
        // The guard against the guard. A parse that silently returned an empty
        // map, or that matched any hex anywhere in either file, would make the
        // comparison above pass against anything.
        val channels = setOf("red", "green", "blue")

        assertEquals(channels, launchColorComponents().keys)
        assertEquals(channels, paletteComponents().keys)
        assertTrue(
            launchColorComponents().values.any { it != 0 },
            "every channel parsed as zero, which means the catalog regex matched nothing",
        )
        // Cream is nearly white, so a parse that dropped a digit or read the
        // alpha byte as a channel lands well below this.
        assertTrue(
            paletteComponents().values.all { it > NEARLY_WHITE },
            "the palette parsed as ${paletteComponents()}, which is not a cream",
        )
    }

    /** The three channel bytes the colorset declares, as ints. */
    private fun launchColorComponents(): Map<String, Int> {
        val catalog = File(
            repoRoot(),
            "apps/ios/iosApp/Assets.xcassets/LaunchBackground.colorset/Contents.json",
        )
        assertTrue(catalog.isFile, "no colorset at ${catalog.absolutePath}")

        return Regex("\"(red|green|blue)\"\\s*:\\s*\"0x([0-9A-Fa-f]{2})\"")
            .findAll(catalog.readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt(radix = HEX) }
    }

    /** The same three bytes, from `ColorResource.Cream50`'s ARGB literal. */
    private fun paletteComponents(): Map<String, Int> {
        val palette = File(
            repoRoot(),
            "libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/system/color/ColorResource.kt",
        )
        assertTrue(palette.isFile, "no palette at ${palette.absolutePath}")

        // Anchored on the name, so it cannot drift onto a neighbouring colour.
        val rgb = Regex("Cream50\\s*:\\s*ColorResource\\(Color\\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\\)")
            .find(palette.readText())
            ?.groupValues
            ?.get(1)
            ?: error("could not find Cream50's literal in ${palette.absolutePath}")

        return mapOf(
            "red" to rgb.substring(0, 2).toInt(radix = HEX),
            "green" to rgb.substring(2, 4).toInt(radix = HEX),
            "blue" to rgb.substring(4, 6).toInt(radix = HEX),
        )
    }

    private fun repoRoot(): String =
        System.getProperty(REPO_ROOT_PROPERTY)
            ?: error("$REPO_ROOT_PROPERTY is unset — apps/integration/build.gradle.kts supplies it")

    private companion object {
        const val REPO_ROOT_PROPERTY = "sodogku.repoRoot"
        const val HEX = 16
        const val NEARLY_WHITE = 0xE0
    }
}
