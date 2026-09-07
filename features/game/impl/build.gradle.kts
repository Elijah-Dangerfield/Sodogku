plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.game.impl"
}


kotlin {
    sourceSets {
        commonMain.dependencies {
                        implementation(projects.features.game)


            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.puzzle)
            implementation(projects.libraries.levels)
            implementation(projects.libraries.scoring)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)

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
            implementation(projects.libraries.levels)
            implementation(projects.libraries.puzzle)
            implementation(projects.libraries.scoring)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
