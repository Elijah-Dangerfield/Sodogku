
plugins {
    id("sodogku.compose.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.ui"
}

kotlin {
    sourceSets {

        androidMain.dependencies {
            api(compose.preview)
            api(compose.uiTooling)
        }

        commonMain.dependencies {
            implementation(projects.libraries.core)
            api(projects.libraries.resources)
            // TODO honestly the sodogku library should expose the component that require sodogku domain
            implementation(projects.libraries.sodogku)
            // `api`, not `implementation`: `LocalShareSheet` exposes
            // `ShareLauncher` in its public type, so every consumer of the
            // design system has to be able to name it.
            api(projects.libraries.sharing)

            api(compose.ui)
            api(compose.uiUtil)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.components.resources)
            api(compose.components.uiToolingPreview)
            api(compose.materialIconsExtended)
            api(compose.material3AdaptiveNavigationSuite)
            api(libs.compose.backhandler)

            api(libs.compottie)
            api(libs.compottie.resources)
            api(libs.compottie.dot)
            api(libs.compottie.lite)
            api(libs.compottie.network)
        }
    }
}