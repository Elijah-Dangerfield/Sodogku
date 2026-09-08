plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.apps.integration"
}

// End-to-end integration harness. The tests run as Android unit tests on the
// host JVM (`testDebugUnitTest`) — the same path the feature view models already
// compile through — so they can drive the REAL client stack (and the real
// HomeViewModel) against a REAL in-process Ktor server over a REAL Postgres
// (Testcontainers). Everything lives in the `androidUnitTest` source set;
// commonMain stays empty (nothing ships here, and the iOS target must not try
// to link the JVM-only server).
//
// No `jvm{}` targets are added to the client libraries — we reuse their
// existing Android variants on the host JVM. The one unusual edge this module
// proves out is consuming the JVM-only `:apps:server` from an Android
// unit-test classpath.
kotlin {
    sourceSets {
        androidUnitTest.dependencies {
            // Real server: installApp, ServerComponent, Database.connect.
            implementation(projects.apps.server)

            // Real client stack beneath the view models.
            implementation(projects.libraries.networking)
            implementation(projects.libraries.networking.impl)
            implementation(projects.libraries.config)
            implementation(projects.libraries.config.impl)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.core)

            // The `telemetry.*` ConfiguredValues live here rather than in
            // :libraries:config. This is the only test module that can see both
            // halves of the declared key set at once, which is what makes the
            // admin registry drift test complete.
            implementation(projects.libraries.telemetry.impl)
            implementation(libs.kotlinx.serialization.json)

            // Boot a real server on an ephemeral port. Declared here because
            // :apps:server's dependencies are
            // `implementation`-scoped and don't leak to consumers' compile
            // classpaths.
            implementation(libs.ktor.serverCore)
            implementation(libs.ktor.serverNetty)
            // The client's HttpClient {} resolves its engine per platform;
            // supply the Android/JVM one explicitly so engine discovery is
            // deterministic on the host JVM.
            implementation(libs.ktor.client.okhttp)

            // Real Postgres for the server side (same recipe as the server's
            // own DatabaseTest — shared container per JVM, Flyway migrations
            // through the production Database.connect path).
            implementation(libs.testcontainers.postgres)

            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// ConfigManifestRegistryDriftTest reads a file the repo commits, and an Android
// unit test's working directory is not worth guessing at — a wrong guess reads
// nothing and the test passes on an empty registry.
//
// The `inputs.file` is the load-bearing half. A file read at test *runtime* is
// invisible to Gradle's up-to-date check, so without it, editing the registry
// alone leaves the test task UP-TO-DATE and the drift ships.
tasks.withType<Test>().configureEach {
    val registry = rootProject.file("apps/admin/config-manifest-registry.json")
    inputs.file(registry).withPropertyName("configManifestRegistry")
    systemProperty("sodogku.configManifestRegistry", registry.absolutePath)

    // Same trick for the reader test: it greps the source tree, which Gradle
    // cannot see either. The whole repo is the input, so this task reruns
    // whenever anything at all changes — which is the honest cost of a test
    // whose subject is "does any file mention this class".
    val repo = rootProject.layout.projectDirectory
    systemProperty("sodogku.repoRoot", repo.asFile.absolutePath)
    // A filtered tree, not `inputs.dir`. Declaring the directories wholesale
    // swept in `*/build/**`, which is another task's output, and Gradle
    // correctly rejected the undeclared dependency — the whole suite went red
    // while the test passed on its own.
    inputs.files(
        rootProject.fileTree(repo) {
            include("libraries/**/*.kt", "features/**/*.kt", "apps/**/*.kt")
            exclude("**/build/**")
        },
    ).withPropertyName("configReaderScan")

    // The iOS tests in this module read files that are not Kotlin at all — a
    // plist, an asset catalog's Contents.json, an .xcscheme, the Fastfile — so
    // the `*.kt` tree above does not see them and the task stays UP-TO-DATE
    // when one of them changes.
    //
    // That hole is worse than it sounds: it makes the tests *look* like they
    // work. Mutating the launch colour and then deleting the shared scheme both
    // left the suite green, and the earlier mutation runs that did fail only
    // failed because unrelated Kotlin was changing at the same moment. A test
    // that passes for that reason is not a test.
    inputs.files(
        rootProject.fileTree(repo.dir("apps/ios")) {
            include("**/*.plist", "**/*.xcscheme", "**/Contents.json", "**/Fastfile")
            exclude("**/build/**", "**/xcuserdata/**")
        },
    ).withPropertyName("iosProjectScan")
}
