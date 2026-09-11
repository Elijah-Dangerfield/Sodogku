package com.sodogku.integration.privacy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Two sentences in `pages/privacy.html`, held to the source that has to keep
 * them true: the app never gives Sentry a name, an email address or a user id,
 * and the feedback form has no email field.
 *
 * Both were one line away from false. `Telemetry.setUser(email, name, id)` was
 * declared and implemented with no caller anywhere, and `captureUserFeedback`
 * took an `email` that `FeedbackRepositoryImpl` never passed. Either would
 * compile, both are the natural thing to reach for the day someone adds a
 * contact field, and nothing would have failed.
 *
 * `docs/store/data-safety.md` lists "anything that starts calling
 * `Telemetry.setUser`" among the four things that invalidate the store forms.
 * That was a note asking a human to remember. This is the same note as a test.
 */
class NoIdentitySeamsTest {

    @Test
    fun nothingHandsAnIdentityToTheCrashReporter() {
        val offenders = productionKotlin()
            .filter { IDENTITY_CALL.containsMatchIn(it.readText().withoutComments()) }
            .map { "  ${it.relativePath()}" }

        assertTrue(
            offenders.isEmpty(),
            "Sodogku has no accounts, and the privacy policy says Sentry is never given a name, an " +
                "email or a user id. These files set one:\n" + offenders.joinToString("\n") +
                "\n\nIf contact details are ever wanted they go in the feedback message body, which " +
                "is what the policy already describes.",
        )
    }

    @Test
    fun theFeedbackPathCarriesNoEmail() {
        val offenders = feedbackPath()
            .filter { EMAIL_FIELD.containsMatchIn(it.readText().withoutComments()) }
            .map { "  ${it.relativePath()}" }

        assertTrue(
            offenders.isEmpty(),
            "The feedback form has no email field and the policy says so. These name one on the " +
                "path a report takes to Sentry:\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun theScanReadsTheFilesItClaimsTo() {
        // The guard against the guard. Both assertions above are "no offenders
        // found", which is exactly what a scan of nothing reports.
        val files = productionKotlin()
        assertTrue(files.size > MIN_SOURCE_FILES, "only found ${files.size} source files, so the scan is broken")

        val found = feedbackPath().map { it.relativePath() }.toSet()
        assertTrue(
            found.size == FEEDBACK_PATH.size,
            "the feedback path has moved or been renamed: found $found, expected $FEEDBACK_PATH",
        )
        assertTrue(
            IDENTITY_CALL.containsMatchIn("Sentry.setUser(User(id = id))"),
            "the identity pattern no longer matches the call it exists to catch",
        )
        assertTrue(
            EMAIL_FIELD.containsMatchIn("""        email: String? = null,"""),
            "the email pattern no longer matches the parameter it exists to catch",
        )
    }

    /** The two files a feedback report passes through on its way out. */
    private fun feedbackPath(): List<File> = FEEDBACK_PATH
        .map { File(repoRoot(), it) }
        .onEach { assertTrue(it.isFile, "the feedback path has moved: no file at ${it.absolutePath}") }

    private fun productionKotlin(): List<File> = File(repoRoot())
        .walkTopDown()
        // `.claude` holds agent worktrees, which are full checkouts of this
        // repo: without this the scan reports a file that is not in the tree.
        .onEnter { it.name != "build" && it.name != ".claude" }
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { file -> file.path.split(File.separator).any { it.endsWith("Test") || it == "test" } }
        .toList()

    private fun File.relativePath(): String = path.removePrefix(repoRoot()).trimStart(File.separatorChar)

    /**
     * Comment lines go first, so a file explaining why the seam is absent does
     * not read as the seam. The KDoc on `Telemetry` says the words out loud on
     * purpose, and it tripped this test the first time it ran.
     */
    private fun String.withoutComments(): String = COMMENT_LINE.replace(this, "")

    private fun repoRoot(): String =
        System.getProperty("sodogku.repoRoot")
            ?: error("sodogku.repoRoot is unset, apps/integration/build.gradle.kts supplies it")

    private companion object {
        /** The declaration, an override, or a call. Any of the three. */
        val IDENTITY_CALL = Regex("""\bsetUser\s*\(""")

        /** A `//` line or a `*` continuation inside a block comment. */
        val COMMENT_LINE = Regex("""(?m)^\s*(//|\*|/\*).*$""")

        /** An `email` parameter or property assignment, not the word in prose. */
        val EMAIL_FIELD = Regex("""(?m)^\s*(\w+\.)?email\s*[:=]""")

        val FEEDBACK_PATH = listOf(
            "libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/Telemetry.kt",
            "libraries/sodogku/impl/src/commonMain/kotlin/com/sodogku/libraries/sodogku/impl/AppTelemetry.kt",
        )

        const val MIN_SOURCE_FILES = 200
    }
}
