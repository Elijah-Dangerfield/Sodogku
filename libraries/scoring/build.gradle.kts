plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.scoring"
}

// No dependencies on purpose, not even :libraries:puzzle. Scoring only ever
// needs a grid size and a difficulty tier as plain ints, and keeping it free of
// the puzzle model means the formula stays trivially testable in isolation.
