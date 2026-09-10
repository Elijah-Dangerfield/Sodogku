package com.sodogku.util

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

private const val EXPECTED_HOOKS_PATH = ".githooks"

fun Project.verifyGitHooksInstalled() {
    if (System.getenv("CI") != null) return
    if (System.getProperty("sodogku.skipGitHooksCheck") == "true") return

    val gitDir = resolveGitDir(rootProject.projectDir) ?: return
    val configFile = File(gitDir, "config").takeIf { it.exists() } ?: return
    val configured = readHooksPath(configFile)

    if (pointsAtOurHooks(configured, rootProject.projectDir)) return

    throw GradleException(
        """

        Git hooks are not installed. Conventional Commits enforcement is missing.

          Run: ./scripts/install_hooks.sh

        This wires $EXPECTED_HOOKS_PATH/commit-msg as a local hook so release-please can
        derive version bumps from commit history. See docs/release-automation.md.

        To bypass (e.g. non-interactive build outside CI), pass
        -Dsodogku.skipGitHooksCheck=true or set the CI env var.

        """.trimIndent()
    )
}

/**
 * Whether [configured] names this repo's hooks directory, however it is spelled.
 *
 * String equality against `.githooks` was the whole check, and it is wrong the
 * moment anything writes an absolute path. `git config core.hooksPath .githooks`
 * run from a *worktree* records the absolute path in the shared config, so
 * installing hooks from a worktree broke every Gradle task in the main checkout
 * with "Git hooks are not installed" while the hooks were fine. That happened
 * twice in one day once agents started working in worktrees.
 *
 * Resolving both sides to a canonical file answers the question actually being
 * asked, which is "will git find our hooks", not "is this string the one we
 * expected".
 */
private fun pointsAtOurHooks(configured: String?, projectDir: File): Boolean {
    if (configured.isNullOrBlank()) return false
    val expected = File(projectDir, EXPECTED_HOOKS_PATH)
    val actual = File(configured).let { if (it.isAbsolute) it else File(projectDir, configured) }
    return runCatching { actual.canonicalFile == expected.canonicalFile }.getOrDefault(false)
}

private fun resolveGitDir(projectDir: File): File? {
    val gitPath = File(projectDir, ".git")
    if (!gitPath.exists()) return null
    if (gitPath.isDirectory) return gitPath
    // Worktree / submodule: .git is a file containing `gitdir: <path>`.
    val pointer = gitPath.readText().trim().removePrefix("gitdir:").trim()
    val resolved = if (File(pointer).isAbsolute) File(pointer) else File(projectDir, pointer)
    return resolved.takeIf { it.exists() }
}

private fun readHooksPath(config: File): String? {
    var inCore = false
    for (raw in config.readLines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
        if (line.startsWith("[")) {
            inCore = line.substringBefore(']').trim('[').trim().equals("core", ignoreCase = true)
            continue
        }
        if (!inCore) continue
        val match = Regex("""(?i)^hooksPath\s*=\s*(.+)$""").find(line) ?: continue
        return match.groupValues[1].trim().trim('"')
    }
    return null
}
