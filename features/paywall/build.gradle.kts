plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.paywall"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
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
    }
}
