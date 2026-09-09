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
            // The three score badges are fractions of `Scoring.parScore` rather
            // than typed numbers of points, so the catalog needs the formula at
            // compile time. This used to be test-only, on the argument that main
            // source should stay dependency-free; the targets were literals and
            // a 10x rescale of the coefficients would have put all three
            // permanently out of reach. `:libraries:scoring` itself depends on
            // nothing, so the fold is still reasonable on its own.
            implementation(projects.libraries.scoring)
            api(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            // Test-only, and deliberately one-directional: the catalog names
            // combo lengths and board sizes, and `AchievementReachabilityTest`
            // checks each of them against the packs that actually ship.
            implementation(projects.libraries.levels)
            // Repeated rather than inherited: `commonMain` declares scoring as
            // `implementation`, which a test compilation does not see.
            implementation(projects.libraries.scoring)
        }
    }
}
