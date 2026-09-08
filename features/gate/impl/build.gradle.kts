plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.gate.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.gate)

            implementation(projects.libraries.core)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.ui)
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
            implementation(projects.features.gate)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
