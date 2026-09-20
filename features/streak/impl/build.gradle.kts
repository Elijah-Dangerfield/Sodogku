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
            // Including this feature's own copy. It used to live in a
            // composeResources folder here, on the argument that nothing
            // outside the feature renders it and a shared file is a shared
            // merge conflict. Translation won that argument: one file is one
            // batch to send out and one file to get back.
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
            // The week strip's states are the design system's enum, decided
            // on the view model's state so a test can read them.
            implementation(projects.libraries.ui)
            implementation(projects.libraries.core)
            implementation(projects.libraries.sodogku)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
