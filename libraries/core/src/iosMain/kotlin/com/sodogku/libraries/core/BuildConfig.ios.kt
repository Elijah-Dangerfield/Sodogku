package com.sodogku.libraries.core

import com.sodogku.buildinfo.SodogkuBuildConfig
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform as NativePlatform

@OptIn(ExperimentalNativeApi::class)
actual object BuildInfo {
    actual val isDebug: Boolean
        get() = NativePlatform.isDebugBinary

    actual val platform: Platform = Platform.iOS

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
}