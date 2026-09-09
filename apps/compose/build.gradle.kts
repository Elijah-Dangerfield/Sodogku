plugins {
    id("sodogku.application")
    id("co.touchlab.skie") version "0.10.12"
    alias(libs.plugins.sentryAndroid)
    alias(libs.plugins.baselineProfile)
}

android {
    namespace = "com.sodogku"
}

/**
 * Consume the committed Baseline Profile rather than regenerating it on every
 * release build — generation starts an emulator and walks the app, which is
 * minutes nobody wants in the release path.
 *
 * Regenerate deliberately when the app's shape changes:
 * `./gradlew :apps:compose:generateBaselineProfile`
 */
baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(projects.apps.baselineprofile)
}

/**
 * Sentry's Android Gradle plugin, for exactly one job: making obfuscated crash
 * reports readable.
 *
 * R8 renames methods, so from the first minified release every Sentry frame
 * arrives as `a.b.c`. Deobfuscating needs two things — the mapping file
 * uploaded, and a ProGuard UUID stamped into the build tying that mapping to
 * this APK. A hand-rolled `sentry-cli upload-proguard` step supplies only the
 * first: with no UUID to match against, the upload associates with nothing and
 * still reports success. Confirm this is working by unzipping the APK and
 * checking `assets/sentry-debug-meta.properties` for `io.sentry.ProguardUuids`,
 * not by trusting a green upload step.
 */
sentry {
    org.set(providers.environmentVariable("SENTRY_ORG"))
    projectName.set(providers.environmentVariable("SENTRY_PROJECT"))
    authToken.set(providers.environmentVariable("SENTRY_AUTH_TOKEN"))

    // This project already uses the Kotlin Multiplatform Sentry SDK.
    // Auto-installation would add `sentry-android` on top of it, and two SDKs
    // initialising in one process is not a thing to discover in production.
    autoInstallation { enabled.set(false) }

    // Always stamp the UUID: it is what makes a mapping associable at all, and
    // it costs nothing in a build without a token.
    includeProguardMapping.set(true)

    // Only upload when a token exists, so a contributor can still build a
    // release locally without one.
    autoUploadProguardMapping.set(
        providers.environmentVariable("SENTRY_AUTH_TOKEN").isPresent,
    )

    // No build-time telemetry to Sentry about our Gradle builds.
    telemetry.set(false)
}

kotlin {

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.work.runtime)
            implementation(compose.uiTooling)
        }

        commonMain.dependencies {
            // Project dependencies
            api(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.sodogku.impl)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.navigation.impl)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.review)
            implementation(projects.libraries.review.impl)
            implementation(projects.libraries.puzzle)
            implementation(projects.libraries.levels)
            implementation(projects.libraries.scoring)
            // `api`, not `implementation`, because Swift needs `AdUnits`.
            // Kotlin/Native only exports declarations reachable from the
            // framework's own API, and refuses to export a non-api dependency at
            // all. `AdUnits` is named by nothing on iOS — its only other caller
            // is `AdMobAdNetwork` in androidMain — so without this it never
            // reaches ComposeApp.h and `AdNetwork.swift` cannot see it. Paired
            // with the `export(...)` in ApplicationConventionPlugin.
            api(projects.libraries.ads)
            implementation(projects.libraries.ads.impl)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.billing.impl)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.progress.impl)
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.achievements.impl)
            implementation(projects.libraries.sharing)
            implementation(projects.libraries.sharing.impl)
            // The impl is what turns leaderboards on: `RealLeaderboards`
            // declares `replaces = [NoLeaderboards::class]`, so without this
            // line the graph silently keeps the no-op and every submission
            // goes nowhere. Android has no game service behind it and binds
            // `NoGameServices`, which is the fail-open path working as
            // designed rather than something missing.
            implementation(projects.libraries.leaderboards)
            implementation(projects.libraries.leaderboards.impl)

            implementation(projects.libraries.storage)
            implementation(projects.libraries.storage.impl)
            implementation(projects.libraries.sodogku.storage)
            implementation(projects.libraries.config)
            implementation(projects.libraries.config.impl)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.networking.impl)
            implementation(projects.libraries.telemetry.impl)

            implementation(projects.features.home)
            implementation(projects.features.home.impl)
            implementation(projects.features.onboarding)
            implementation(projects.features.onboarding.impl)
            implementation(projects.features.game)
            implementation(projects.features.game.impl)
            implementation(projects.features.settings)
            implementation(projects.features.settings.impl)
            implementation(projects.features.achievements)
            implementation(projects.features.streak)
            implementation(projects.features.streak.impl)
            implementation(projects.features.achievements.impl)
            implementation(projects.features.paywall)
            implementation(projects.features.paywall.impl)
            implementation(projects.features.gate)
            implementation(projects.features.gate.impl)

            implementation(libs.atomicfu)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
        }
    }
}