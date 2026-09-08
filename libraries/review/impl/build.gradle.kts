plugins {
    id("sodogku.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.sodogku.libraries.review.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.review)
            implementation(projects.libraries.core)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            // `app.reviewPromptAfterLevel`, and the campaign record that says
            // when it was cleared. Both api modules; the coordinator itself
            // stays free of either.
            implementation(projects.libraries.config)
            implementation(projects.libraries.progress)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.review)
            implementation(projects.libraries.sodogku)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.config)
            implementation(projects.libraries.progress)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.google.play.review)
            implementation(libs.google.play.review.ktx)
        }
    }
}
