plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.paywall.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.paywall)

            implementation(projects.libraries.core)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.config)

            // Compose dependencies (navigation and lifecycle provided by sodogku.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
