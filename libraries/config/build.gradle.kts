plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.config"
}


kotlin {
    sourceSets {
        commonMain.dependencies {

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(libs.kotlin.inject.runtime.kmp)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}