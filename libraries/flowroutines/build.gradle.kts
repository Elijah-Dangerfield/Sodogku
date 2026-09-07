plugins {
    id("sodogku.compose.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.sodogku.libraries.flowroutines"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)

            implementation(projects.libraries.core)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            
            // Compose dependencies
            api(compose.runtime)
            api(compose.ui)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.libraries.core)
        }
    }
}
