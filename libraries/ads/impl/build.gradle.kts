plugins {
    id("sodogku.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.sodogku.libraries.ads.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.config)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.flowroutines)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.config)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.google.play.services.ads)
            implementation(libs.google.ump)
        }
    }
}
