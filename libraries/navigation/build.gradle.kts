plugins {
    id("sodogku.compose.multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.sodogku.libraries.navigation"

    // Compose's test harness inflates a real ComponentActivity, which needs the
    // merged manifest and the packaged resources. Without this the rule dies on
    // a missing theme rather than on anything the test is about.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.flowroutines)
            api(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }

        // `RouteTransitions` decides what each screen does during a navigation.
        // It used to be inline lambdas in `App.kt` that no test could reach.
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }

        // The composition-under-test tier. It lives in `androidUnitTest` rather
        // than on a `jvm()` target, for the same reason `:apps:integration`
        // gives: the Android variants already run on the host JVM, and adding a
        // JVM target here would cascade through :libraries:{core,ui,resources,
        // sodogku,flowroutines,storage}, each of which would need `actual`
        // stubs for camera and microphone permission launchers and image
        // decoding, plus a third value on the two-case `Platform` enum that
        // several exhaustive `when`s read. That is a lot of shipped surface
        // invented to serve a test.
        //
        // `commonTest` is the wrong home too: it also compiles for iOS, where
        // none of this resolves.
        androidUnitTest.dependencies {
            implementation(compose.foundation)
            implementation(libs.androidx.compose.uiTest.junit4)
            implementation(libs.androidx.compose.uiTest.manifest)
            implementation(libs.robolectric)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutines.test)
            // Not for the host itself. A floating window's lifecycle only
            // matters through what the app hangs off it, and every one of those
            // goes through `ObserveWithLifecycle`, so the test asserts against
            // the real observer rather than a stand-in with the same gate.
            implementation(projects.libraries.flowroutines)
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
