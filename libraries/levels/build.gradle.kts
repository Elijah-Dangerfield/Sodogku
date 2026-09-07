plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.levels"
}

// The jvm() target lets tools/level-generator write packs through the same
// codec the app reads them with, so a format change cannot desync the two.
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.puzzle)
        }
    }
}
