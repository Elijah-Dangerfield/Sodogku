plugins {
    alias(libs.plugins.kotlinJvm)
}

// Custom detekt rules live in their own JVM module: detekt discovers them via
// the ServiceLoader on the `detektPlugins` classpath, so the rules must be a
// standalone jar — they can't live inside `build-logic` (an included build) or
// a multiplatform module. Wired into the build by the root build.gradle.kts
// detekt block.
//
// No `detekt-test` here yet: dev.detekt:detekt-test declares a runtime
// dependency on a `detekt-api` test-fixtures jar that isn't published to Maven
// Central for any 2.0.0 alpha through alpha.6 (404), so it can't resolve. Rule
// behaviour is instead verified by the real `detekt` task running over a sample
// in CI. Add the test dep back once an alpha publishes its fixtures.
//
// Pin detekt to alpha.6 or later. On 2.0.0-alpha.5 a custom rule can silently
// fail to dispatch: the build passes, detekt reports success, and the rule never
// runs — so a clean run is indistinguishable from a broken rule. If a new rule
// appears to do nothing, suspect the detekt version before the rule.
kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.detekt.api)
}
