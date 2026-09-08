plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.storage.impl"
}

moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.storage)

            implementation(projects.libraries.progress)
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.sodogku.storage)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(projects.libraries.storage)
            implementation(projects.libraries.flowroutines.testing)
        }

        // The migration test opens a real database file, so it needs a
        // filesystem and the bundled driver's native library. Both are only
        // available on the host JVM, which is why it is not in commonTest.
        androidUnitTest.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled.jvm)
        }
    }
}

tasks.matching { it.name.contains("kspCommonMainKotlinMetadata", ignoreCase = true) }
    .configureEach { enabled = false }