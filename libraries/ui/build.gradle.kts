
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

        commonTest.dependencies {
            // Test-only, and deliberately not a production dependency. The
            // design system does not know what a puzzle is; what it needs to
            // know is that its region palette is at least as long as the
            // biggest board the engine will hand it, and that is an assertion
            // rather than a call.
            implementation(projects.libraries.puzzle)
        }
    }
}