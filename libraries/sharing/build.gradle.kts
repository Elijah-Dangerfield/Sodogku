plugins {
    id("sodogku.kotlin.multiplatform")
}

android {
    namespace = "com.sodogku.libraries.sharing"
}

// No dependencies, deliberately — not on :libraries:puzzle, not on
// :libraries:achievements. A share is a string built from a size, a region
// layout and four numbers, and the module that builds it must not be able to
// see a solution it could accidentally print. Keeping it free of the puzzle
// model is what makes "no spoilers" a property of the signature rather than of
// the implementation.
