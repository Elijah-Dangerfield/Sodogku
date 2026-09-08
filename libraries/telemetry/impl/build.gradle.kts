plugins {
    id("sodogku.kotlin.multiplatform")
}

moduleConfig {
    di()
    optIn("io.opentelemetry.kotlin.ExperimentalApi")
}

android {
    namespace = "com.sodogku.libraries.telemetry.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.config)
            implementation(projects.libraries.networking)
            // AppEventListener — app.launched must wait for the cold-boot
            // dispatch so it carries the settled session_id.
            implementation(projects.libraries.sodogku)
            // FileManager — the on-device buffer directory for the durable
            // export pipeline.
            implementation(projects.libraries.storage)
            implementation(projects.libraries.flowroutines)
            implementation(libs.okio)
            implementation(libs.otel.kotlin.api)
            implementation(libs.otel.kotlin.sdk.api)
            implementation(libs.otel.kotlin.implementation)
            implementation(libs.otel.kotlin.exporters.core)
            implementation(libs.otel.kotlin.exporters.persistence)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.encoding)
        }

        commonTest.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(libs.okio)
            implementation(libs.otel.kotlin.api)
            implementation(libs.otel.kotlin.sdk.api)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.metrics.performance)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// DashboardQueryContractTest holds `ops/grafana/*.json` against the `logEvent`
// calls that feed them, so it reads two things Gradle cannot see: the committed
// dashboards, and the source tree they query.
//
// The `inputs` declarations are the load-bearing half. A file read at test
// *runtime* is invisible to the up-to-date check, so without them, renaming an
// attribute leaves this task UP-TO-DATE and the broken dashboard ships. The
// scan covers the whole repo, so the task reruns whenever any Kotlin changes —
// the honest cost of a test whose subject is "does any file emit this".
tasks.withType<Test>().configureEach {
    val repo = rootProject.layout.projectDirectory
    val dashboards = rootProject.file("ops/grafana")

    systemProperty("sodogku.repoRoot", repo.asFile.absolutePath)
    systemProperty("sodogku.grafanaDashboards", dashboards.absolutePath)

    inputs.dir(dashboards).withPropertyName("grafanaDashboards")
    // A filtered tree rather than `inputs.dir`, because the directories
    // wholesale sweep in `*/build/**`, which is another task's output.
    inputs.files(
        rootProject.fileTree(repo) {
            include("features/**/*.kt", "libraries/**/*.kt", "apps/**/*.kt")
            exclude("**/build/**")
        },
    ).withPropertyName("appEventScan")
}
