plugins {
    id("sodogku.compose.multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.sodogku.libraries.navigation.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.sodogku)
            api(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.libraries.navigation)
            // `RouteDeepLinksTest` drives the bridge and its consumer against a
            // controlled scheduler, so a link held for a graph that does not
            // exist yet is a step rather than a sleep.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}