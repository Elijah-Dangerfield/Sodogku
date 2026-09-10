plugins {
    id("sodogku.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.sodogku.libraries.leaderboards"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            api(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            // `submitWindowed` takes a suspending lambda, so the one thing worth
            // asserting about the no-op binding needs a coroutine to assert in.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
