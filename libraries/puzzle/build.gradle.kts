plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.puzzle"
}

// The JVM target exists so `tools/level-generator` can reuse this exact solver
// to verify the packs it emits. Same code verifies a level at build time and
// hints at it on device — there is no second implementation to drift.
//
// Nothing from `:libraries:*` is depended on here, deliberately: `:libraries:core`
// has no JVM target, and pulling it in would strand the generator.
kotlin {
    jvm()
}
