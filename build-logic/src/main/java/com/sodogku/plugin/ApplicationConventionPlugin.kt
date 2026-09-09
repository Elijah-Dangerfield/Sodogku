package com.sodogku.plugin

import com.android.build.api.dsl.ApplicationExtension
import com.sodogku.ext.ConfigurationExtension
import com.sodogku.util.SharedConstants
import com.sodogku.util.configureAndroid
import com.sodogku.util.configureKotlinInject
import com.sodogku.util.configureKotlinMultiplatform
import com.sodogku.util.configureReleaseSigning
import com.sodogku.util.enforceModuleBoundaries
import com.sodogku.util.libs
import com.sodogku.util.loadSupabaseMetadata
import com.sodogku.util.verifyGitHooksInstalled
import com.sodogku.util.loadVersionMetadata
import com.sodogku.util.optInKotlinMarkers
import com.sodogku.util.VersionMetadata
import com.sodogku.util.writeCommonMetadata
import com.sodogku.util.writeSupabaseMetadata
import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for the main Android application module.
 *
 * **When to use this plugin:**
 * - The main app module that gets installed on devices
 * - The module that contains MainActivity and app-level configuration
 * - The module that defines the applicationId and app metadata
 *
 * **What this plugin provides:**
 * - Android application plugin configuration
 * - Kotlin Multiplatform setup with Android and iOS targets
 * - Compose and Compose Compiler plugins
 * - iOS framework configuration for KMP
 * - Application-specific build configuration (version codes, signing, etc.)
 * - Activity Compose dependencies
 *
 * **Examples of modules that should use this:**
 * - apps:compose (your main app)
 * - apps:desktop (if you have a desktop app variant)
 *
 * **Don't use this plugin for:**
 * - Feature modules (use sodogku.feature instead)
 * - Library modules (use sodogku.compose.multiplatform or sodogku.kotlin.multiplatform)
 * - Server modules (these wouldn't be Android applications)
 */
class ApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val versionMetadata = loadVersionMetadata()
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.application")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply(libs.plugins.kotlinSerialization.get().pluginId)
                apply(libs.plugins.buildconfig.get().pluginId)
            }

            project.optInKotlinMarkers("kotlin.time.ExperimentalTime")
            project.optInKotlinMarkers("kotlin.uuid.ExperimentalUuidApi")

            configureKotlinMultiplatform {
                binaries.framework {
                    baseName = "ComposeApp"
                    isStatic = true
                    binaryOption("bundleId", "com.sodogku")
                    export(project(":libraries:core"))
                    // Swift reads `AdUnits` directly: the ad unit id lives in
                    // one file on purpose, so the iOS ad code asks Kotlin for it
                    // rather than keeping a second copy that could drift to a
                    // test id while Kotlin held the real one.
                    export(project(":libraries:ads"))
                }
            }
            configureKotlinInject()

            extensions.configure<ApplicationExtension> {
                configureAndroid()

                defaultConfig {
                    applicationId = versionMetadata.applicationId
                    targetSdk = SharedConstants.targetSdk
                    versionCode = versionMetadata.versionCode
                    versionName = versionMetadata.versionName
                }

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }

                val releaseSigning = configureReleaseSigning(this)

                buildTypes {
                    debug {
                        applicationIdSuffix = ".debug"
                    }
                    release {
                        // R8 on. Play flags an app under 25% obfuscation as
                        // below its threshold with a Feb 2027 deadline, and the
                        // size and startup wins come with it. The keep rules in
                        // apps/compose/proguard-rules.pro are what make this
                        // survive: R8 breaks whatever is resolved by name at
                        // runtime, and every failure mode is at runtime, so a
                        // release build that merely compiles proves nothing.
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
                    }
                }
            }

            if (extensions.findByName("moduleConfig") == null) {
                extensions.create("moduleConfig", ConfigurationExtension::class.java)
            }
            configureAppBuildConfig(versionMetadata)

            verifyGitHooksInstalled()
            enforceModuleBoundaries()
        }
    }

    private fun Project.configureAppBuildConfig(metadata: VersionMetadata) {
        val supabaseMetadata = loadSupabaseMetadata()
        extensions.configure(BuildConfigExtension::class.java) {
            packageName("${metadata.applicationId}.appconfig")
            className("AppBuildConfig")
            useKotlinOutput {
                internalVisibility = false
            }
            writeCommonMetadata(metadata)
            writeSupabaseMetadata(supabaseMetadata)
        }
    }
}