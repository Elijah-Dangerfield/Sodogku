plugins {
    id("sodogku.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.sodogku.libraries.leaderboards.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.leaderboards)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
        }

        commonTest.dependencies {
            implementation(projects.libraries.leaderboards)
            // RealLeaderboards implements AutoInit, so the supertype has to be
            // resolvable from the test classpath or nothing can construct one.
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
