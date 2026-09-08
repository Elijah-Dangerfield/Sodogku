plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.streak.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.streak)
            // A feature impl may depend on another feature's api. The intention
            // moment ends by opening today's daily, and `GameRoute` is the only
            // thing that names a board.
            implementation(projects.features.game)

            implementation(projects.libraries.core)
            implementation(projects.libraries.progress)
            // `AppData` owns the haptics and reduce-animations toggles. The
            // celebration is the only thing on this feature that moves or
            // buzzes, and both settings have to reach it.
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.resources)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.core)
            implementation(projects.libraries.sodogku)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// The streak's copy lives in this module rather than in `:libraries:resources`.
// Nothing outside the feature renders it, and a shared string file is a shared
// merge conflict.
compose.resources {
    packageOfResClass = "sodogku.features.streak.impl.generated.resources"
}
