plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.progress"
}

moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            api(libs.kotlinx.coroutines.core)
            // api, not implementation: the daily's public surface is keyed on
            // LocalDate, so every consumer needs the type on its classpath.
            api(libs.kotlinx.datetime)
        }
    }
}
