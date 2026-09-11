
plugins {
    id("sodogku.compose.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.ui"

    // Compose's test harness inflates a real ComponentActivity, which needs the
    // merged manifest and the packaged resources. It is also how a composition
    // test gets at `composeResources`: the dog sprite sheets are read through
    // the asset manager, and without this the sheets resolve to nothing.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    sourceSets {

        androidMain.dependencies {
            api(compose.preview)
            api(compose.uiTooling)
        }

        commonMain.dependencies {
            implementation(projects.libraries.core)
            api(projects.libraries.resources)
            // TODO honestly the sodogku library should expose the component that require sodogku domain
            implementation(projects.libraries.sodogku)
            api(compose.ui)
            api(compose.uiUtil)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.components.resources)
            api(compose.components.uiToolingPreview)
            api(compose.materialIconsExtended)
            api(compose.material3AdaptiveNavigationSuite)
            api(libs.compose.backhandler)

            api(libs.compottie)
            api(libs.compottie.resources)
            api(libs.compottie.dot)
            api(libs.compottie.lite)
            api(libs.compottie.network)
        }

        commonTest.dependencies {
            // Test-only, and deliberately not a production dependency. The
            // design system does not know what a puzzle is; what it needs to
            // know is that its region palette is at least as long as the
            // biggest board the engine will hand it, and that is an assertion
            // rather than a call.
            implementation(projects.libraries.puzzle)
        }

        // The composition-under-test tier, second module to take it after
        // :libraries:navigation — read the long note in
        // `libraries/navigation/build.gradle.kts` for why it lives in
        // `androidUnitTest` rather than on a `jvm()` target or in `commonTest`.
        //
        // It is here because `Dog`'s contract is a claim about what a
        // composition does over time, and nothing below composition can hold it:
        // the decision reads two composition locals and its effect is that a
        // coroutine does or does not run. A unit test of a pure function would
        // restate the `if` rather than test it.
        androidUnitTest.dependencies {
            implementation(libs.androidx.compose.uiTest.junit4)
            implementation(libs.androidx.compose.uiTest.manifest)
            implementation(libs.robolectric)
            implementation(libs.kotlin.testJunit)
        }
    }
}

// Robolectric fetches the Android framework jar itself, from Maven, at test
// runtime, into ~/.m2, a directory no CI cache covers. That is ~100MB per run
// and a network call inside the test, which is exactly how a tier earns a
// reputation for flaking. Resolve it through Gradle instead, stage it, and run
// Robolectric offline against the staged copy.
val robolectricAndroidAll: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    robolectricAndroidAll(libs.robolectric.androidAll)
}

val stageRobolectricJars = tasks.register<Sync>("stageRobolectricJars") {
    from(robolectricAndroidAll)
    into(layout.buildDirectory.dir("robolectric-android-all"))
}

tasks.withType<Test>().configureEach {
    dependsOn(stageRobolectricJars)
    // Gradle's default is 512m. A Robolectric sandbox plus a Compose
    // composition fits, but not with much room, and an OOM here would read as
    // a flake rather than as the resource problem it is.
    maxHeapSize = "2g"
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-android-all").get().asFile.absolutePath,
    )
}