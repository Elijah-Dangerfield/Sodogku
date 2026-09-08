plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.achievements"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The badge copy lives here rather than in `impl` on purpose: the
            // win sheet in :features:game:impl needs a name for a badge it just
            // unlocked, and a feature impl may only see another feature's api.
            implementation(projects.libraries.achievements)

            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.flowroutines)

            // Compose dependencies (navigation and lifecycle provided by sodogku.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.achievements)
        }
    }
}
