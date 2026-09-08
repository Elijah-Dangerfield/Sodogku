package com.sodogku.libraries.core

import com.sodogku.buildinfo.SodogkuBuildConfig
import com.sodogku.libraries.core.BuildConfig as AndroidBuildConfig

actual object BuildInfo {
    actual val isDebug: Boolean
        get() = AndroidBuildConfig.DEBUG

    actual val platform: Platform
        get() = Platform.Android

    actual val applicationId: String
        get() = SodogkuBuildConfig.APPLICATION_ID

    actual val versionName: String
        get() = SodogkuBuildConfig.VERSION_NAME

    actual val versionCode: Int
        get() = SodogkuBuildConfig.VERSION_CODE

    actual val releaseChannel: String
        get() = SodogkuBuildConfig.RELEASE_CHANNEL

    actual val buildNumber: Int
        get() = SodogkuBuildConfig.BUILD_NUMBER

    actual val commitSha: String
        get() = SodogkuBuildConfig.COMMIT_SHA

    actual val commitBranch: String
        get() = SodogkuBuildConfig.COMMIT_BRANCH

    // Play has no TestFlight equivalent that a running app can detect: an
    // internal-testing install is indistinguishable from a production one.
    // Testers on Android get a debug build or nothing.
    actual val isTestFlight: Boolean = false
}