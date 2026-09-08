plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.achievements"
}

moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The explicit storage dependency is load-bearing on iOS:
            // `moduleConfig.storage()` adds it to the project-level
            // `implementation` configuration, which the Android target picks up
            // and the Kotlin/Native one does not. See docs/decisions.md.
            implementation(projects.libraries.storage)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
