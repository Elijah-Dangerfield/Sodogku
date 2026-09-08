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
    }
}
