package com.sodogku.libraries.core

expect object BuildInfo {
    val isDebug: Boolean
    val platform: Platform
    val applicationId: String
    val versionName: String
    val versionCode: Int
    val releaseChannel: String
    val buildNumber: Int

    /** Short git SHA the build was produced from — `GITHUB_SHA` in CI,
     *  `git rev-parse` locally, `"unknown"` when neither is available. */
    val commitSha: String

    /** Branch the build was produced from (`GITHUB_REF_NAME` in CI). */
    val commitBranch: String

    /**
     * Whether this install came from TestFlight rather than the App Store.
     *
     * A **runtime** answer, not a build flag: TestFlight and the App Store ship
     * the identical release binary, so nothing baked in at compile time can
     * tell them apart. iOS reads the receipt; Android has no equivalent channel
     * and answers false.
     */
    val isTestFlight: Boolean
}

/**
 * Builds whose only audience is the people making the app: local debug builds
 * and TestFlight. Gates developer affordances that must never reach a player,
 * like the feedback edge handle.
 */
val BuildInfo.isTesterBuild: Boolean get() = BuildInfo.isDebug || BuildInfo.isTestFlight

fun BuildInfo.isiOS() = BuildInfo.platform == Platform.iOS
val BuildInfo.buildType: String get() = if (BuildInfo.isDebug) "debug" else "release"
val BuildInfo.versionTag: String get() = "${BuildInfo.versionName}-${BuildInfo.releaseChannel}"
fun BuildInfo.versionString(): String = "$versionName ($buildNumber)"


enum class Platform {
    Android,
    iOS
}