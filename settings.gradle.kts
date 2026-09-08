enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Sodogku"

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        google()
        mavenCentral()
    }
}

// `-DserverOnly=true` (set by apps/server/Dockerfile) trims the build graph to
// just :apps:server so a server image build needs no Android SDK or Kotlin/Native
// toolchain. The property name is intentionally project-agnostic so the rename
// tooling can't break the Dockerfile↔settings contract. The server is a plain JVM
// module with no client-library deps, so nothing else has to be included. If
// apps/server ever depends on a :libraries:* module, add an always-included
// `include(...)` for it here (outside the `if`) and a matching COPY in the Dockerfile.
val serverOnly = System.getProperty("serverOnly") == "true"

// Apps (always included)
include(":apps")
include(":apps:server")

if (!serverOnly) {
    include(":apps:compose")
    // Note: iOS app is not a Gradle module - it's an Xcode project in apps/ios/

    // End-to-end integration harness: the real client stack (real view models)
    // driven against a real in-process Ktor server over a Testcontainers
    // Postgres. Depends on client impl modules + :apps:server, so it's gated
    // out of the server-only build.
    include(":apps:integration")

    // Baseline Profile generation + the minified-release smoke test. A
    // com.android.test module that ships nothing and exists only at build time,
    // so it is client-side and gated out of the server-only graph.
    include(":apps:baselineprofile")

    // Compose Multiplatform (web) admin console for remote config. The server
    // serves the prebuilt bundle at /admin; CI builds it — the server build
    // itself stays JS-toolchain-free, so it's gated out of the server-only
    // graph like the rest of the client. The first (and only) JS target.
    include(":apps:admin")

    // Features
    include(":features:home")
    include(":features:home:impl")
    include(":features:onboarding")
    include(":features:onboarding:impl")
include(":features:game")
include(":features:game:impl")
include(":features:settings")
include(":features:settings:impl")
include(":features:paywall")
include(":features:paywall:impl")
// The launch gates: force update, maintenance, legal re-accept. It has no
// routes on purpose — a blocking gate is rendered instead of the nav host, not
// navigated to, so there is nothing to pop it off or deep-link past.
include(":features:gate")
include(":features:gate:impl")

    // Libraries
    include(":libraries:config")
    include(":libraries:config:impl")
    include(":libraries:core")
    include(":libraries:flowroutines")
    include(":libraries:flowroutines:testing")
    include(":libraries:navigation")
    include(":libraries:navigation:impl")
    include(":libraries:networking")
    include(":libraries:networking:impl")
    include(":libraries:resources")
    include(":libraries:review")
    include(":libraries:review:impl")
    include(":libraries:storage")
    include(":libraries:storage:impl")
include(":libraries:achievements")
include(":libraries:achievements:impl")
include(":libraries:ads")
include(":libraries:ads:impl")
include(":libraries:billing")
include(":libraries:billing:impl")
include(":libraries:leaderboards")
include(":libraries:leaderboards:impl")
include(":libraries:levels")
include(":libraries:progress")
include(":libraries:progress:impl")
include(":libraries:puzzle")
include(":libraries:scoring")
include(":libraries:sharing")
include(":libraries:sharing:impl")
include(":features:achievements")
include(":features:achievements:impl")
// The streak page and the one-off intention moment. Its copy lives in its own
// composeResources rather than in :libraries:resources, because nothing outside
// the feature renders it.
include(":features:streak")
include(":features:streak:impl")
    // No api sibling on purpose: the public surface is the `logEvent`
    // extension in :libraries:core; this impl only hosts the experimental
    // opentelemetry-kotlin dependency + the GrafanaLogTree wiring.
    include(":libraries:telemetry:impl")
    include(":libraries:sodogku")
    include(":libraries:sodogku:impl")
    include(":libraries:sodogku:storage")
    include(":libraries:ui")

    // Offline level generation. Build-time only: it writes the generated packs
    // into :libraries:levels and ships nothing. Depends on the same
    // :libraries:puzzle solver the app hints with, so the pack is verified by
    // the code that will later be asked to reason about it.
    include(":tools:level-generator")

    // Custom detekt rules — a standalone JVM jar detekt loads via
    // `detektPlugins`. Dev/CI tooling only, never shipped; gated out of the
    // server-only Docker build like every other client module.
    include(":detekt-rules")
}