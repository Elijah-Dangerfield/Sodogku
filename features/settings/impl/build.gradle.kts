plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.settings.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.settings)
            // For `FeedbackRoute`, which still lives in :features:home because
            // :features:game:impl navigates to it. See docs/decisions.md.
            implementation(projects.features.home)
            // For `AchievementsRoute` — the badge grid is reached from here.
            implementation(projects.features.achievements)

            implementation(projects.libraries.core)
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
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
