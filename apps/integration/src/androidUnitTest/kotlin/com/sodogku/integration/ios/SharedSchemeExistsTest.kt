package com.sodogku.integration.ios

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The scheme fastlane builds is one a fresh clone actually has.
 *
 * `Fastfile` passes `scheme: "iosApp"` to `build_app`, and Xcode writes schemes
 * to `xcuserdata/` by default — which `.gitignore` excludes, because it is full
 * of per-developer state. So the scheme existed on exactly one machine, and on
 * CI or any fresh clone `xcodebuild -list` reported no schemes at all and the
 * beta and release lanes had nothing to build.
 *
 * That is a failure nobody sees until a release is being cut, which is the worst
 * moment to find it, and it cannot be caught by building locally because locally
 * the ignored file is right there.
 *
 * Sharing a scheme is just moving it to `xcshareddata/`, so the guard is that
 * the shared copy exists and is tracked.
 */
class SharedSchemeExistsTest {

    @Test
    fun theSchemeFastlaneBuildsIsShared() {
        val name = schemeNameFromFastfile()
        val shared = File(repoRoot(), "$PROJECT/xcshareddata/xcschemes/$name.xcscheme")

        assertTrue(
            shared.isFile,
            "Fastfile builds scheme '$name' but $shared does not exist. Xcode keeps schemes in " +
                "xcuserdata/, which .gitignore excludes, so a fresh clone would have no scheme " +
                "to build. In Xcode: Product > Scheme > Manage Schemes, tick Shared.",
        )
    }

    @Test
    fun theSharedSchemeCarriesNoMachineSpecificPaths() {
        // A scheme copied out of xcuserdata can carry absolute paths or a
        // developer's name, which works on one machine and confuses the next.
        val name = schemeNameFromFastfile()
        val text = File(repoRoot(), "$PROJECT/xcshareddata/xcschemes/$name.xcscheme").readText()

        assertTrue("/Users/" !in text, "the shared scheme contains an absolute home path")
        assertTrue(
            "container:" in text,
            "the scheme references no container, so it does not point at the project",
        )
    }

    /** The scheme name as `Fastfile` spells it, so the two cannot drift apart. */
    private fun schemeNameFromFastfile(): String {
        val fastfile = File(repoRoot(), "apps/ios/fastlane/Fastfile")
        assertTrue(fastfile.isFile, "no Fastfile at ${fastfile.absolutePath}")
        return Regex("""SCHEME\s*=\s*"([^"]+)"""")
            .find(fastfile.readText())
            ?.groupValues
            ?.get(1)
            ?: error("could not find SCHEME in ${fastfile.absolutePath}")
    }

    private fun repoRoot(): String =
        System.getProperty("sodogku.repoRoot")
            ?: error("sodogku.repoRoot is unset — apps/integration/build.gradle.kts supplies it")

    private companion object {
        const val PROJECT = "apps/ios/iosApp.xcodeproj"
    }
}
