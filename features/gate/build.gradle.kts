plugins {
    id("sodogku.feature")
}

android {
    namespace = "com.sodogku.features.gate"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            // The maintenance-mode words (`off` / `banner` / `blocking`) are
            // declared next to the config key that carries them, so the
            // resolver matches against the same constants the admin console
            // offers rather than a second copy of three strings.
            implementation(projects.libraries.config)
        }

        commonTest.dependencies {
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
