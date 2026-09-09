plugins {
    alias(libs.plugins.kotlinJvm)
}

// Custom detekt rules live in their own JVM module: detekt discovers them via
// the ServiceLoader on the `detektPlugins` classpath, so the rules must be a
// standalone jar — they can't live inside `build-logic` (an included build) or
// a multiplatform module. Wired into the build by the root build.gradle.kts
// detekt block.
//
// No `detekt-test` here: dev.detekt:detekt-test declares a runtime dependency on
// a `detekt-api` test-fixtures jar that isn't published to Maven Central for any
// 2.0.0 alpha through alpha.6 (404), so it can't resolve. `RuleHarness` in the
// test source set stands in for its `lint()` helper — it parses a snippet into a
// KtFile and calls `Rule.visitFile`, which is all detekt-test's helper does for a
// rule that needs no type resolution. Swap it out once an alpha publishes its
// fixtures.
//
// Pin detekt to alpha.6 or later. On 2.0.0-alpha.5 a custom rule can silently
// fail to dispatch: the build passes, detekt reports success, and the rule never
// runs — so a clean run is indistinguishable from a broken rule. If a new rule
// appears to do nothing, suspect the detekt version before the rule.
//
// The other way a new rule silently does nothing: **the Gradle daemon caches the
// ruleset ClassLoader by classpath path, not by jar contents.** Add a rule class
// to this module and the daemon keeps serving the classloader it built from the
// previous jar at the same path, so the ServiceLoader hands detekt a provider
// that has never heard of your rule. The build passes, `--rerun-tasks` doesn't
// help, and detekt reports zero findings. `./gradlew --stop` first, then run.
// Rules already registered before the daemon started are unaffected, which is
// what makes this so confusing: the three existing rules keep working while the
// new one does nothing.
//
// Either way, don't trust a clean run to mean the rule works. `detekt.sarif` in
// build/reports lists every rule detekt actually loaded — if yours isn't there,
// it never ran, and no amount of rule logic will fix that.
kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.detekt.api)

    // Not compileOnly for tests: the harness needs detekt-api and the Kotlin
    // compiler PSI it pulls in on the runtime classpath to parse a snippet.
    testImplementation(libs.detekt.api)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
