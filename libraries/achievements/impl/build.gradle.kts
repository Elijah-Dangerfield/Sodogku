plugins {
    id("sodogku.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.sodogku.libraries.achievements.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.achievements)
        }
        commonTest.dependencies {
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.flowroutines.testing)
        }
    }
}
