plugins {
    id("sodogku.compose.multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.sodogku.libraries.navigation"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.flowroutines)
            api(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }

        // `RouteTransitions` decides what each screen does during a navigation.
        // It used to be inline lambdas in `App.kt` that no test could reach.
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}