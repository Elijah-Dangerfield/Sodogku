plugins {
    id("sodogku.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.sodogku.libraries.progress.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.progress)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.config)
            implementation(projects.libraries.levels)
        }
        commonTest.dependencies {
            implementation(projects.libraries.progress)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.config)
            implementation(projects.libraries.levels)
        }
    }
}
