plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.streak"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.navigation)

            implementation(compose.runtime)
        }

        // How the streak page arrives is decided by the route, because a page
        // cannot rise over a board the navigator has already removed. Repeated
        // here because `commonTest` does not inherit `commonMain`'s
        // `implementation` dependencies.
        commonTest.dependencies {
            implementation(projects.libraries.navigation)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
