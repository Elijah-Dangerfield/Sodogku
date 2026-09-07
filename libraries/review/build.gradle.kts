plugins {
    id("sodogku.kotlin.multiplatform")
}

moduleConfig {
    serialization()
}

android {
    namespace = "com.sodogku.libraries.review"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
