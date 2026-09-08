plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.achievements"
}

moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The explicit storage dependency is load-bearing on iOS:
            // `moduleConfig.storage()` adds it to the project-level
            // `implementation` configuration, which the Android target picks up
            // and the Kotlin/Native one does not. See docs/decisions.md.
            implementation(projects.libraries.storage)
            api(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            // Test-only, and deliberately one-directional: the catalog names
            // scores, combo lengths and board sizes, and `AchievementReachability
            // Test` checks each of them against the packs and the formula that
            // actually ship. Main source stays dependency-free so the fold can be
            // reasoned about on its own.
            implementation(projects.libraries.levels)
            implementation(projects.libraries.scoring)
        }
    }
}
