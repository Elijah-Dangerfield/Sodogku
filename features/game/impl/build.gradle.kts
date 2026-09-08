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
            implementation(projects.features.home)


            implementation(projects.libraries.core)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.puzzle)
            implementation(projects.libraries.levels)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.achievements)
            // AchievementCopy, for the unlock toast's badge names.
            implementation(projects.features.achievements)
            implementation(projects.features.settings)
            // The two streak ceremonies are navigated to from the win path.
            implementation(projects.features.streak)
            implementation(projects.libraries.sharing)
            implementation(projects.libraries.scoring)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            // The scoring coefficients, the booster economy and two feature
            // switches are all `scoring.*` / `boosters.*` / `features.*` keys.
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
            // `RecordingEvents` plants a `LogTree` to capture the `game.*`
            // events this screen emits. Nothing here asserted on an event
            // before, and the dashboards read all twelve of them.
            implementation(projects.libraries.core)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.levels)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.puzzle)
            implementation(projects.libraries.scoring)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.config)
            // `brokenRule` answers with the design system's `RuleDiagram`,
            // which is the chip the board outlines.
            implementation(projects.libraries.ui)
            // The dialog copy tests name StringResources directly.
            implementation(projects.libraries.resources)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
