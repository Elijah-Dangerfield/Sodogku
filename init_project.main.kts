#!/usr/bin/env kotlin

// No @file:DependsOn here on purpose: the script uses only the stdlib, and a
// dependency declaration makes every cold run resolve Maven coordinates over
// the network (observed hanging the script runner entirely on flaky daemons).

import java.io.File
import kotlin.system.exitProcess

/**
 * KMP Template Project Initialization Script
 *
 * This script helps you set up a new project from the KMP Template.
 * It will rename all template placeholders to your chosen project name.
 *
 * Interactive usage: ./init_project.main.kts
 *
 * The script will prompt you for:
 * - App name (e.g., "My Awesome App") - used for display
 * - Package name (e.g., "com.example.myawesomeapp") - used for package declarations
 * - Contact email and destination directory
 *
 * Non-interactive usage (all flags required together — used by
 * scripts/verify_template.sh and template CI):
 *
 *   ./init_project.main.kts \
 *     --name "My App" --package com.example.myapp \
 *     --email you@example.com --dir /path/to/new/project \
 *     --ci=yes --yes
 *
 * Any flag present requires ALL of them; exit code is non-zero on any
 * validation or copy failure so automation can gate on it.
 */

// Color codes for terminal output
private val RED = "\u001b[31m"
private val GREEN = "\u001b[32m"
private val YELLOW = "\u001b[33m"
private val BLUE = "\u001b[34m"
private val CYAN = "\u001b[36m"
private val RESET = "\u001b[0m"

fun printRed(text: String) = println("$RED$text$RESET")
fun printGreen(text: String) = println("$GREEN$text$RESET")
fun printYellow(text: String) = println("$YELLOW$text$RESET")
fun printBlue(text: String) = println("$BLUE$text$RESET")
fun printCyan(text: String) = println("$CYAN$text$RESET")

/**
 * Represents different naming conventions for the project name.
 * Given an input like "My Awesome App":
 * - pascalCase: "MyAwesomeApp"
 * - camelCase: "myAwesomeApp" 
 * - lowercase: "myawesomeapp"
 * - kebabCase: "my-awesome-app"
 * - snakeCase: "my_awesome_app"
 * - dotCase: "my.awesome.app"
 * - displayName: "My Awesome App"
 */
data class ProjectName(
    val displayName: String,      // "My Awesome App"
    val pascalCase: String,       // "MyAwesomeApp"
    val camelCase: String,        // "myAwesomeApp"
    val lowercase: String,        // "myawesomeapp"
    val kebabCase: String,        // "my-awesome-app"
    val snakeCase: String,        // "my_awesome_app"
    val dotCase: String           // "my.awesome.app"
) {
    companion object {
        /**
         * Creates ProjectName from a display name like "My Awesome App"
         */
        fun fromDisplayName(displayName: String): ProjectName {
            val words = displayName.split(Regex("[\\s_\\-\\.]+")).filter { it.isNotBlank() }
            
            val pascalCase = words.joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            val camelCase = words.mapIndexed { index, word ->
                if (index == 0) word.lowercase() else word.replaceFirstChar { it.uppercase() }
            }.joinToString("")
            val lowercase = words.joinToString("") { it.lowercase() }
            val kebabCase = words.joinToString("-") { it.lowercase() }
            val snakeCase = words.joinToString("_") { it.lowercase() }
            val dotCase = words.joinToString(".") { it.lowercase() }
            
            return ProjectName(
                displayName = displayName,
                pascalCase = pascalCase,
                camelCase = camelCase,
                lowercase = lowercase,
                kebabCase = kebabCase,
                snakeCase = snakeCase,
                dotCase = dotCase
            )
        }
        
        /**
         * Creates ProjectName from a PascalCase identifier like "MyAwesomeApp"
         */
        fun fromPascalCase(pascalCase: String): ProjectName {
            // Split PascalCase into words
            val words = pascalCase.replace(Regex("([a-z])([A-Z])"), "$1 $2").split(" ")
            val displayName = words.joinToString(" ")
            return fromDisplayName(displayName)
        }
    }
}

// Template placeholders - these are what we search for and replace
val TEMPLATE_NAME = ProjectName(
    displayName = "KMP Template",
    pascalCase = "KMPTemplate",
    camelCase = "kmpTemplate",
    lowercase = "kmptemplate",
    kebabCase = "kmp-template",
    snakeCase = "kmp_template",
    dotCase = "kmp.template"
)

// Old package prefix to replace
val TEMPLATE_PACKAGE = "com.kmptemplate"

// Extensions to process for content replacement
val TEXT_FILE_EXTENSIONS = setOf(
    "kt", "kts", "java", "xml", "json", "yaml", "yml", "md", "txt",
    "properties", "gradle", "swift", "h", "m", "plist", "entitlements",
    "xcconfig", "pbxproj", "xcscheme", "storyboard", "xib",
    // Server: fly.toml app name + version catalog. (Dockerfile/.env are
    // extensionless and server config is project-agnostic, so they don't
    // carry the project name.)
    "toml", "sql",
    // Staged GitHub Pages content (Sodogku etc. live in the HTML).
    "html", "css",
    // R8 keep rules are written against the package prefix. Left un-renamed,
    // every `-keep class com.kmptemplate.**` matches nothing in the generated
    // project — R8 then strips serializers, routes and the DI entry point, and
    // the build still succeeds. The failure is at runtime, in release only.
    "pro"
)

// Directories to skip during processing AND during copy
val SKIP_DIRECTORIES = setOf(
    ".git", ".gradle", ".idea", "build", "node_modules", ".kotlin",
    "caches", "generated", "intermediates",
    // Machine-local agent settings — never ship into generated projects.
    ".claude"
)

// The template/ folder at the repo root is the init-time staging area
// (SETUP.md + ci/). It is copied explicitly — the wholesale copy skips it.
val TEMPLATE_STAGING_DIR = "template"

// Files to skip during content replacement
val SKIP_FILES = setOf(
    "init_project.main.kts",
    "gradlew", "gradlew.bat",
    ".DS_Store"
)

data class ReplacementStats(
    var filesModified: Int = 0,
    var foldersRenamed: Int = 0,
    var filesRenamed: Int = 0,
    var replacementsMade: Int = 0
)

/**
 * Values collected from CLI flags for the non-interactive mode. Null means
 * "no flags given — run interactively".
 */
data class CliConfig(
    val name: String,
    val packageName: String,
    val email: String,
    val dir: String,
    val ciEnabled: Boolean
)

fun cliFail(message: String): Nothing {
    printRed("❌ $message")
    exitProcess(1)
}

/**
 * All-or-nothing flag parsing: any flag present requires --name, --package,
 * --email, --dir, --ci=yes|no AND --yes. Deliberately flags (not env vars or
 * piped stdin) so automation breaks loudly when the interface changes instead
 * of silently answering the wrong prompt.
 */
fun parseCliConfig(rawArgs: List<String>): CliConfig? {
    if (rawArgs.isEmpty()) return null
    val values = mutableMapOf<String, String>()
    var confirmed = false
    var i = 0
    while (i < rawArgs.size) {
        val arg = rawArgs[i]
        when {
            arg == "--yes" -> { confirmed = true; i++ }
            arg.startsWith("--") && arg.contains('=') -> {
                values[arg.substringBefore('=').removePrefix("--")] = arg.substringAfter('=')
                i++
            }
            arg.startsWith("--") -> {
                if (i + 1 >= rawArgs.size) cliFail("Missing value for $arg")
                values[arg.removePrefix("--")] = rawArgs[i + 1]
                i += 2
            }
            else -> cliFail("Unexpected argument: $arg")
        }
    }

    val required = listOf("name", "package", "email", "dir", "ci")
    val missing = required.filterNot(values::containsKey)
    if (missing.isNotEmpty() || !confirmed) {
        val flags = missing.map { "--$it" } + if (confirmed) emptyList() else listOf("--yes")
        cliFail(
            "Non-interactive mode is all-or-nothing; missing: ${flags.joinToString(" ")}\n" +
                "   Usage: ./init_project.main.kts --name \"My App\" --package com.example.myapp " +
                "--email you@example.com --dir /path --ci=yes|no --yes"
        )
    }

    val ci = when (values.getValue("ci")) {
        "yes" -> true
        "no" -> false
        else -> cliFail("--ci must be yes or no")
    }
    return CliConfig(
        name = values.getValue("name"),
        packageName = values.getValue("package"),
        email = values.getValue("email"),
        dir = values.getValue("dir"),
        ciEnabled = ci
    )
}

fun main(cli: CliConfig?) {
    if (cli == null) {
        printBlue("""
            ╔══════════════════════════════════════════════════════════════╗
            ║         🚀 KMP Template Project Initialization 🚀            ║
            ╠══════════════════════════════════════════════════════════════╣
            ║  Creates a fresh copy of the template with your project     ║
            ║  name — the original template is left untouched.            ║
            ╚══════════════════════════════════════════════════════════════╝
        """.trimIndent())
        println()
    }

    val projectName: ProjectName
    val packageName: String
    val contactEmail: String
    val projectDir: File

    if (cli != null) {
        projectName = validateProjectName(cli.name.trim()) ?: cliFail("Invalid --name: ${cli.name}")
        packageName = validatePackageName(cli.packageName.trim()) ?: cliFail("Invalid --package: ${cli.packageName}")
        contactEmail = cli.email.trim().ifEmpty { cliFail("--email must not be empty") }
        val dir = File(cli.dir).canonicalFile
        if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
            cliFail("--dir already exists and is not empty: ${dir.absolutePath}")
        }
        if (!dir.exists() && !dir.mkdirs()) cliFail("Could not create --dir: ${dir.absolutePath}")
        projectDir = dir
    } else {
        projectName = getProjectName() ?: return
        packageName = getPackageName(projectName) ?: return
        contactEmail = getContactEmail() ?: return
        projectDir = getProjectDir(projectName) ?: return
    }

    println()
    printCyan("📋 Configuration Summary:")
    println("   Display Name:  ${projectName.displayName}")
    println("   PascalCase:    ${projectName.pascalCase}")
    println("   camelCase:     ${projectName.camelCase}")
    println("   lowercase:     ${projectName.lowercase}")
    println("   kebab-case:    ${projectName.kebabCase}")
    println("   Package:       $packageName")
    println("   Destination:   ${projectDir.absolutePath}")
    println()

    if (cli == null) {
        print("Proceed with these settings? (Y/n): ")
        val confirm = readln().trim().lowercase()
        if (confirm.isNotEmpty() && confirm != "y" && confirm != "yes") {
            printYellow("👋 Initialization cancelled. Run again when ready!")
            return
        }
    }

    println()
    printBlue("🔄 Starting project initialization...")

    val stats = ReplacementStats()
    val templateDir = File(".").canonicalFile

    try {
        printBlue("📋 Step 1/8: Copying template to ${projectDir.absolutePath}...")
        copyTemplate(templateDir, projectDir)
        printGreen("   ✓ Template copied")

        printBlue("📦 Step 2/8: Placing SETUP.md + configuring CI...")
        placeSetupDoc(templateDir, projectDir)
        val ciEnabled = maybeEnableCi(templateDir, projectDir, cli?.ciEnabled)

        printBlue("📝 Step 3/8: Replacing file contents...")
        replaceFileContents(projectDir, projectName, packageName, stats)

        printBlue("📁 Step 4/8: Renaming directories...")
        renameDirectories(projectDir, projectName, packageName, stats)

        printBlue("📄 Step 5/8: Renaming files...")
        renameFiles(projectDir, projectName, stats)

        printBlue("🔖 Step 6/8: Substituting CI placeholders...")
        substitutePlaceholders(projectDir, projectName, contactEmail)

        printBlue("🧹 Step 7/8: Cleaning up template artifacts...")
        cleanupTemplateArtifacts(projectDir, projectName, ciEnabled)
        ensureExecutableBits(projectDir)

        printBlue("🔄 Step 8/8: Initializing git repository...")
        resetGitHistory(projectDir, projectName)

        println()
        printGreen("✅ Project initialization complete!")
        println()
        printCyan("📊 Summary:")
        println("   Files modified:     ${stats.filesModified}")
        println("   Folders renamed:    ${stats.foldersRenamed}")
        println("   Files renamed:      ${stats.filesRenamed}")
        println("   Total replacements: ${stats.replacementsMade}")
        println()
        printYellow("📍 Project created at: ${projectDir.absolutePath}")
        println()
        printYellow("✅ Project initialized.")
        printYellow("→ Open SETUP.md for the full action-item checklist")
        printYellow("   (GH secrets, store setup, first-release notes).")
        printYellow("→ Run ./scripts/install_hooks.sh before your first commit.")
        println()
        println("   Open the project:   cd ${projectDir.absolutePath}")
        println("   Sync Gradle:        open in IDE")
        println("   First build:        ./gradlew build")
        println()
        printGreen("🎉 Happy coding with ${projectName.displayName}!")

    } catch (e: Exception) {
        printRed("❌ Error during initialization: ${e.message}")
        e.printStackTrace()
        printYellow("⚠️  Partially created project may exist at: ${projectDir.absolutePath}")
        printYellow("     The original template was not modified.")
        exitProcess(1)
    }
}

/**
 * Shared validation for both the interactive prompts and the CLI flags.
 * Returns null when the input is unusable.
 */
fun validateProjectName(input: String): ProjectName? {
    if (input.isEmpty()) return null
    val projectName = ProjectName.fromDisplayName(input)
    if (projectName.pascalCase.isEmpty() || !projectName.pascalCase[0].isLetter()) return null
    if (!projectName.pascalCase.all { it.isLetterOrDigit() }) return null
    return projectName
}

fun validatePackageName(input: String): String? {
    val packageRegex = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$")
    return if (packageRegex.matches(input)) input else null
}

fun copyTemplate(source: File, dest: File) {
    dest.mkdirs()
    source.listFiles()?.forEach { file ->
        if (file.name in SKIP_DIRECTORIES) return@forEach
        // template/ is a staging folder copied explicitly (SETUP.md always,
        // CI conditionally via enableCi()). Don't ship the staging folder
        // itself into the new project.
        if (file.parentFile?.canonicalPath == source.canonicalPath &&
            file.isDirectory && file.name == TEMPLATE_STAGING_DIR) return@forEach
        // The root .github/ is the TEMPLATE repo's own CI (template-ci.yml).
        // Generated projects get their workflows from template/ci/ instead.
        if (file.parentFile?.canonicalPath == source.canonicalPath &&
            file.isDirectory && file.name == ".github") return@forEach
        // Machine-specific (Android SDK path etc.) — the IDE regenerates it.
        if (file.parentFile?.canonicalPath == source.canonicalPath &&
            file.name == "local.properties") return@forEach
        val target = File(dest, file.name)
        if (file.isDirectory) {
            copyTemplate(file, target)
        } else {
            file.copyTo(target, overwrite = false)
            // File.copyTo doesn't preserve POSIX permissions. Restore the
            // executable bit for shell scripts and gradlew so the new project
            // is immediately runnable without `chmod +x`.
            if (file.canExecute()) {
                target.setExecutable(true, false)
            }
        }
    }
}

/**
 * Recursive file copy preserving directory structure. Skips SKIP_DIRECTORIES.
 */
fun copyRecursive(source: File, dest: File) {
    if (source.isDirectory) {
        dest.mkdirs()
        source.listFiles()?.forEach { child ->
            if (child.name in SKIP_DIRECTORIES) return@forEach
            copyRecursive(child, File(dest, child.name))
        }
    } else {
        dest.parentFile?.mkdirs()
        source.copyTo(dest, overwrite = true)
    }
}

/**
 * File.copyTo doesn't preserve the executable bit, so any shell scripts and
 * git hooks shipped in the template need +x applied explicitly after copy.
 */
fun ensureExecutableBits(projectDir: File) {
    val execPaths = listOf(
        "scripts/install_hooks.sh",
        "scripts/enable_ci.sh",
        "scripts/cleanup.sh",
        "scripts/create_module.main.kts",
        "scripts/rotate_apple_sign_in_token.main.kts",
        ".githooks/commit-msg",
        ".githooks/post-commit",
        ".githooks/pre-push",
        "gradlew"
    )
    execPaths.forEach { rel ->
        val f = File(projectDir, rel)
        if (f.exists()) f.setExecutable(true, false)
    }
}

fun placeSetupDoc(templateDir: File, projectDir: File) {
    val setupSrc = File(templateDir, "$TEMPLATE_STAGING_DIR/SETUP.md")
    if (!setupSrc.exists()) return
    val setupDst = File(projectDir, "SETUP.md")
    setupSrc.copyTo(setupDst, overwrite = true)
    printGreen("   ✓ Placed SETUP.md in project root")
}

/**
 * Asks whether to enable CI (or uses the --ci flag in non-interactive mode)
 * and, if yes, copies every file under template/ci/ into the project,
 * preserving relative paths.
 *
 * When CI is declined, the staging folder is still shipped (as template/ci/)
 * together with scripts/enable_ci.sh so the project can opt in later — the
 * staged files go through the same rename/substitution passes as everything
 * else, so enabling later is a pure file move.
 */
fun maybeEnableCi(templateDir: File, projectDir: File, cliCiEnabled: Boolean?): Boolean {
    val ciSrc = File(templateDir, "$TEMPLATE_STAGING_DIR/ci")
    if (!ciSrc.exists() || !ciSrc.isDirectory) return false

    val enable = cliCiEnabled ?: run {
        println()
        printCyan("""
            🚢 Enable CI / release automation?

            This copies release-please, fastlane, GitHub Pages, and the Sentry
            triage prompt into your project:

              • .github/workflows/*.yml  (ci, release-please, release, etc.)
              • apps/ios/Gemfile + apps/ios/fastlane/*
              • pages/*.html, style.css, icons
              • release-please-config.json, .release-please-manifest.json

            You'll still need to set GitHub secrets and create store listings
            before the pipeline will actually ship — see SETUP.md.

            Say no if you want to wire this up later (or never) — you can
            enable it any time by running ./scripts/enable_ci.sh.
        """.trimIndent())
        println()
        print("Enable CI? (y/N): ")
        val answer = readln().trim().lowercase()
        answer == "y" || answer == "yes"
    }

    if (!enable) {
        // Ship the staging folder so scripts/enable_ci.sh can install it later.
        copyRecursive(ciSrc, File(projectDir, "$TEMPLATE_STAGING_DIR/ci"))
        printYellow("   → CI skipped. Run ./scripts/enable_ci.sh later to install it.")
        return false
    }

    ciSrc.listFiles()?.forEach { child ->
        val dst = File(projectDir, child.name)
        copyRecursive(child, dst)
    }
    printGreen("   ✓ CI files installed")
    return true
}

/**
 * Substitute the Sodogku / contact@nightjarlabs.llc / 2026-09-07 /
 * Sodogku — official site. / Sodogku is a cross-platform app built with Kotlin Multiplatform and Compose. placeholders that live inside the
 * CI staging files. Runs after replaceFileContents so it applies to the
 * already-copied, already-renamed content.
 */
fun substitutePlaceholders(projectDir: File, projectName: ProjectName, contactEmail: String) {
    val today = java.time.LocalDate.now().toString()
    val tagline = "${projectName.displayName} — official site."
    val description = "${projectName.displayName} is a cross-platform app built with Kotlin Multiplatform and Compose."
    val pairs = listOf(
        "Sodogku" to projectName.displayName,
        "contact@nightjarlabs.llc" to contactEmail,
        "2026-09-07" to today,
        "Sodogku — official site." to tagline,
        "Sodogku is a cross-platform app built with Kotlin Multiplatform and Compose." to description
    )

    fun walk(f: File) {
        if (f.isDirectory) {
            if (f.name in SKIP_DIRECTORIES) return
            f.listFiles()?.forEach(::walk)
            return
        }
        if (!shouldProcessFile(f)) return
        try {
            var content = f.readText()
            val original = content
            for ((k, v) in pairs) content = content.replace(k, v)
            if (content != original) f.writeText(content)
        } catch (_: Exception) {}
    }

    walk(projectDir)
}

fun cleanupTemplateArtifacts(projectDir: File, projectName: ProjectName, ciEnabled: Boolean) {
    val toDelete = mutableListOf(
        "scripts/init_project.main.kts",
        "scripts/rename_to_template.sh",
        // Template-maintenance tooling and planning docs — meaningful only in
        // the template repo itself, never in a generated project.
        "scripts/verify_template.sh",
        "docs/template-maintenance.md",
        "docs/cards-backport-plan.md",
        "docs/template-upgrade-execution-plan.md",
        // The port queue and its plans are the template's own backlog. They name
        // the source apps systems were carried back from, which a generated
        // project has no use for and the de-branding check would flag.
        "docs/PORT-CANDIDATES.md",
        "docs/plans"
    )
    if (ciEnabled) {
        // CI is already installed, so the late-opt-in path has nothing to do.
        toDelete += "scripts/enable_ci.sh"
    }
    toDelete.forEach { relative ->
        val file = File(projectDir, relative)
        if (file.exists()) {
            // deleteRecursively, not delete: the list holds directories as well
            // as files, and File.delete() is a silent no-op on a non-empty one.
            file.deleteRecursively()
            printGreen("   ✓ Removed $relative")
        }
    }

    rewriteReadme(projectDir, projectName)
    rewriteAgentsMd(projectDir, projectName)
}

fun rewriteReadme(projectDir: File, projectName: ProjectName) {
    val readmeFile = File(projectDir, "README.md")
    if (!readmeFile.exists()) return

    var content = readmeFile.readText()

    val initSectionHeader = "### Initialize Your Project"
    val nextSection = "### Build & Run"
    val startIdx = content.indexOf(initSectionHeader)
    val endIdx = content.indexOf(nextSection)
    if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
        content = content.removeRange(startIdx, endIdx)
    }

    content = content
        .replace(
            "# KMP Template\n\nA Kotlin Multiplatform template with",
            "# ${projectName.displayName}\n\nA Kotlin Multiplatform app with"
        )
        .replace("## Quick Start\n\n### Build & Run", "## Build & Run")

    readmeFile.writeText(content)
    printGreen("   ✓ Rewrote README.md")
}

fun rewriteAgentsMd(projectDir: File, projectName: ProjectName) {
    val agentsFile = File(projectDir, "AGENTS.md")
    if (!agentsFile.exists()) return

    var content = agentsFile.readText()

    content = content
        .replace(
            "Guidelines for AI agents working in this KMP template repository.",
            "Guidelines for AI agents working in the ${projectName.displayName} repository."
        )
        .replace(
            "KMP (Kotlin Multiplatform) template with",
            "KMP (Kotlin Multiplatform) app with"
        )

    agentsFile.writeText(content)
    printGreen("   ✓ Rewrote AGENTS.md")
}

fun resetGitHistory(rootDir: File, projectName: ProjectName) {
    val gitDir = File(rootDir, ".git")
    if (gitDir.exists()) {
        gitDir.deleteRecursively()
        printGreen("   ✓ Removed old git history")
    }

    // DISCARD the child's output instead of leaving the default pipe: nothing
    // reads that pipe, and the initial commit of a full project prints enough
    // (one line per file) to fill the 64KB buffer — the child then blocks
    // writing and waitFor() deadlocks. Found the hard way via a hung smoke run.
    fun git(vararg args: String): Int = ProcessBuilder("git", *args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor()

    val result = git("init")

    if (result == 0) {
        printGreen("   ✓ Initialized fresh git repository")

        git("add", ".")

        // Explicit identity + no signing so the commit succeeds on machines
        // with no global git identity (CI runners) and never blocks on a
        // host's signing agent.
        val commit = git(
            "-c", "user.name=Template Init",
            "-c", "user.email=init@localhost",
            "-c", "commit.gpgsign=false",
            "commit", "-m", "Initial commit - ${projectName.displayName}",
        )

        if (commit == 0) {
            printGreen("   ✓ Created initial commit")
        } else {
            printYellow("   ⚠ git commit failed (exit $commit) — commit manually after init")
        }
    } else {
        printYellow("   ⚠ Could not initialize git (git may not be installed)")
    }
}

fun getProjectDir(projectName: ProjectName): File? {
    val templateDir = File(".").canonicalFile
    val parentDir = templateDir.parentFile?.absolutePath ?: System.getProperty("user.home")
    val suggestedPath = File(parentDir, projectName.pascalCase).absolutePath

    println()
    printCyan("""
        📂 Where should the new project be created?

        This is the full path to the new project folder. It will be created
        if it does not already exist.

        Press Enter to use suggested: $suggestedPath
    """.trimIndent())
    println()
    print("Project directory [$suggestedPath]: ")

    val input = readln().trim()

    if (input.lowercase() in listOf("q", "quit", "exit")) {
        printYellow("👋 Goodbye!")
        return null
    }

    val projectDir = File(input.ifEmpty { suggestedPath }).canonicalFile

    if (projectDir.exists() && projectDir.listFiles()?.isNotEmpty() == true) {
        printRed("❌ Directory already exists and is not empty: ${projectDir.absolutePath}")
        printYellow("   Choose a different location or remove the existing directory.")
        return null
    }

    if (!projectDir.exists() && !projectDir.mkdirs()) {
        printRed("❌ Could not create directory: ${projectDir.absolutePath}")
        return null
    }

    return projectDir
}

fun getProjectName(): ProjectName? {
    printCyan("""
        📛 Enter your project name
        
        This will be used to generate all naming variants:
        - Display name (e.g., "My Awesome App")
        - Code identifiers (e.g., MyAwesomeApp, myAwesomeApp)
        - File/folder names (e.g., my-awesome-app)
        
        Examples: "My App", "Super Todo", "Fitness Tracker"
    """.trimIndent())
    println()
    print("Project name: ")
    
    val input = readln().trim()
    
    if (input.isEmpty() || input.lowercase() in listOf("q", "quit", "exit")) {
        printYellow("👋 Goodbye!")
        return null
    }
    
    val projectName = validateProjectName(input)
    if (projectName == null) {
        printRed("❌ Invalid project name. Must start with a letter and use only letters, numbers, and spaces.")
        return null
    }
    return projectName
}

fun getContactEmail(): String? {
    println()
    printCyan("""
        ✉️ Contact email

        Shown in the privacy/terms pages and used as the default support
        address. You can change it later by editing pages/*.html.

        Press Enter to use a placeholder (you@example.com).
    """.trimIndent())
    println()
    print("Contact email [you@example.com]: ")

    val input = readln().trim()
    if (input.lowercase() in listOf("q", "quit", "exit")) {
        printYellow("👋 Goodbye!")
        return null
    }
    return input.ifEmpty { "you@example.com" }
}

fun getPackageName(projectName: ProjectName): String? {
    val suggestedPackage = "com.example.${projectName.lowercase}"
    
    println()
    printCyan("""
        📦 Enter your package name
        
        This will be used for Kotlin/Java package declarations and Android namespace.
        Format: com.yourcompany.${projectName.lowercase}
        
        Press Enter to use suggested: $suggestedPackage
    """.trimIndent())
    println()
    print("Package name [$suggestedPackage]: ")
    
    val input = readln().trim()
    
    if (input.lowercase() in listOf("q", "quit", "exit")) {
        printYellow("👋 Goodbye!")
        return null
    }
    
    val packageName = validatePackageName(input.ifEmpty { suggestedPackage })
    if (packageName == null) {
        printRed("❌ Invalid package name. Must be lowercase, dot-separated, and start with a letter.")
        printRed("   Example: com.mycompany.myapp")
        return null
    }
    return packageName
}

fun buildReplacements(projectName: ProjectName, packageName: String): List<Pair<String, String>> {
    return listOf(
        // Package replacements (most specific first)
        TEMPLATE_PACKAGE to packageName,

        // Specific template-framing phrases (before generic name replacements)
        "Guidelines for AI agents working in this KMP template repository." to
                "Guidelines for AI agents working in the ${projectName.displayName} repository.",
        "KMP (Kotlin Multiplatform) template with" to "KMP (Kotlin Multiplatform) app with",
        "this KMP template repository" to "this ${projectName.displayName} repository",

        // Name replacements in various formats
        // SCREAMING case first (env vars like KMPTEMPLATE_WIRETAP_IOS — the
        // wiretap noop switch read by libraries/networking/impl, the staged
        // beta/release workflows, and the staged Fastfile must all agree
        // post-rename).
        TEMPLATE_NAME.lowercase.uppercase() to projectName.snakeCase.uppercase(),
        TEMPLATE_NAME.pascalCase to projectName.pascalCase,
        TEMPLATE_NAME.camelCase to projectName.camelCase,
        TEMPLATE_NAME.kebabCase to projectName.kebabCase,
        TEMPLATE_NAME.snakeCase to projectName.snakeCase,
        TEMPLATE_NAME.dotCase to projectName.dotCase,
        TEMPLATE_NAME.lowercase to projectName.lowercase,
        TEMPLATE_NAME.displayName to projectName.displayName,
        
        // Also handle "Kmp Template" and "kmp template" variations
        "Kmp Template" to projectName.displayName,
        "kmp template" to projectName.displayName.lowercase(),
        "KmpTemplate" to projectName.pascalCase,
        "Kmptemplate" to projectName.pascalCase,
        "kmptemplate" to projectName.lowercase
    )
}

fun replaceFileContents(dir: File, projectName: ProjectName, packageName: String, stats: ReplacementStats) {
    dir.listFiles()?.forEach { file ->
        if (file.name in SKIP_FILES) return@forEach
        
        if (file.isDirectory) {
            if (file.name !in SKIP_DIRECTORIES) {
                replaceFileContents(file, projectName, packageName, stats)
            }
        } else if (shouldProcessFile(file)) {
            val modified = replaceInFile(file, projectName, packageName, stats)
            if (modified) {
                stats.filesModified++
            }
        }
    }
}

fun shouldProcessFile(file: File): Boolean {
    val extension = file.extension.lowercase()
    return extension in TEXT_FILE_EXTENSIONS || file.name in listOf(
        // fastlane's files are extensionless — missing Appfile here shipped a
        // project whose TestFlight uploads looked up com.kmptemplate.KMPTemplate
        // on ASC and failed (found via Cards, 2026-07-09).
        "Podfile", "Gemfile", "Makefile", "Dockerfile", "gradlew",
        "Appfile", "Fastfile", "Matchfile", "Deliverfile",
        // .env files resolve to extension "example"/"env" — the server's
        // .env.example carries the project name in OTEL_SERVICE_NAME.
        ".env", ".env.example",
        // detekt's ServiceLoader registration: the file's dotted name defeats
        // extension matching, but its one line is the rule-set provider's FQN
        // under com.kmptemplate — without a rename the generated project's
        // detekt jar points at a class that no longer exists.
        "dev.detekt.api.RuleSetProvider",
        // Git hooks are extensionless; pre-push names the custom detekt
        // ruleset (`kmptemplate`) in its comments.
        "commit-msg", "post-commit", "pre-push",
    )
}

fun replaceInFile(file: File, projectName: ProjectName, packageName: String, stats: ReplacementStats): Boolean {
    try {
        var content = file.readText()
        val originalContent = content
        
        // Build replacement pairs in order of specificity (longer matches first)
        val replacements = buildReplacements(projectName, packageName)
        
        for ((old, new) in replacements) {
            val count = content.split(old).size - 1
            if (count > 0) {
                stats.replacementsMade += count
                content = content.replace(old, new)
            }
        }
        
        if (content != originalContent) {
            file.writeText(content)
            return true
        }
    } catch (e: Exception) {
        // Skip binary files or files we can't read
    }
    return false
}

fun renameDirectories(dir: File, projectName: ProjectName, packageName: String, stats: ReplacementStats) {
    // Package directories FIRST: com/kmptemplate must become the full package
    // path (com/your/pkg) before the generic name pass below renames it to
    // com/<lowercase> and leaves directories out of sync with the package
    // declarations rewritten by replaceFileContents (found via Cards, 2026-07-09:
    // dirs were com/cards while packages were com.dangerfield.cards).
    renamePackageDirectories(dir, packageName, stats)

    // Collect all directories first, then sort by depth (deepest first)
    val allDirs = mutableListOf<File>()
    collectDirectories(dir, allDirs)
    
    // Sort by path length descending (deepest paths first)
    allDirs.sortByDescending { it.absolutePath.length }
    
    for (directory in allDirs) {
        val newName = getReplacedName(directory.name, projectName)
        if (newName != directory.name) {
            val newDir = File(directory.parentFile, newName)
            if (directory.renameTo(newDir)) {
                stats.foldersRenamed++
            }
        }
    }
    
}

fun collectDirectories(dir: File, collected: MutableList<File>) {
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory && file.name !in SKIP_DIRECTORIES) {
            collected.add(file)
            collectDirectories(file, collected)
        }
    }
}

fun renamePackageDirectories(rootDir: File, newPackage: String, stats: ReplacementStats) {
    // Find and rename kmptemplate package directories to new package structure
    val oldPackagePath = TEMPLATE_PACKAGE.replace(".", File.separator)
    val newPackagePath = newPackage.replace(".", File.separator)
    
    fun findAndRenamePackageDirs(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name !in SKIP_DIRECTORIES) {
                val relativePath = file.absolutePath
                if (relativePath.contains(oldPackagePath)) {
                    val newPath = relativePath.replace(oldPackagePath, newPackagePath)
                    val newFile = File(newPath)
                    newFile.parentFile?.mkdirs()
                    if (file.renameTo(newFile)) {
                        stats.foldersRenamed++
                    }
                } else {
                    findAndRenamePackageDirs(file)
                }
            }
        }
    }
    
    findAndRenamePackageDirs(rootDir)
}

fun renameFiles(dir: File, projectName: ProjectName, stats: ReplacementStats) {
    dir.listFiles()?.forEach { file ->
        if (file.name in SKIP_FILES) return@forEach
        
        if (file.isDirectory) {
            if (file.name !in SKIP_DIRECTORIES) {
                renameFiles(file, projectName, stats)
            }
        } else {
            val newName = getReplacedName(file.name, projectName)
            if (newName != file.name) {
                val newFile = File(file.parentFile, newName)
                if (file.renameTo(newFile)) {
                    stats.filesRenamed++
                }
            }
        }
    }
}

fun getReplacedName(name: String, projectName: ProjectName): String {
    var result = name
    
    // Replace in order of specificity
    result = result.replace(TEMPLATE_NAME.pascalCase, projectName.pascalCase)
    result = result.replace(TEMPLATE_NAME.camelCase, projectName.camelCase)
    result = result.replace(TEMPLATE_NAME.kebabCase, projectName.kebabCase)
    result = result.replace(TEMPLATE_NAME.snakeCase, projectName.snakeCase)
    result = result.replace(TEMPLATE_NAME.lowercase, projectName.lowercase)
    result = result.replace("KmpTemplate", projectName.pascalCase)
    result = result.replace("Kmptemplate", projectName.pascalCase)
    result = result.replace("kmptemplate", projectName.lowercase)
    
    return result
}

// Run the script. Explicit exit: the script JVM has been observed lingering
// after main() completes (a stray non-daemon thread keeps it alive), which
// hangs automation that waits on the process.
main(parseCliConfig(args.toList()))
exitProcess(0)
