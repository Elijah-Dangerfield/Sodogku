# Sodogku

A logic puzzle game for Android and iOS, built as one Kotlin Multiplatform codebase with a Compose Multiplatform client and a small Ktor server on Fly.io.

The game is Queens' ruleset with dogs. An `N x N` grid is split into `N` coloured regions and you put `N` dogs on it: one per region, one per row, one per column, and no two touching, not even at the corners. There are no numbers, every board has exactly one solution, and a solver proved it before the board shipped.

**[docs/reference/features.md](docs/reference/features.md) is what the game does**, feature by feature, as the code behaves today. This file is about the repository.

## What is in here

**Client (Android + iOS from one codebase)**
- **The game**: puzzle rules, solver and hint engine in pure Kotlin (`:libraries:puzzle`), level packs shipped as generated Kotlin source so a malformed pack fails compilation rather than the app, and a verification test that re-solves every level in CI
- **No accounts, on purpose**: no sign-in, no cloud save, no user-scoped server state, progress is device-local. The seams that once carried them are gone; see "No accounts" in AGENTS.md
- **Offline detection that tells the truth**: OS connectivity kept separate from witnessed backend reachability, because the ad layer and the block screen care about the first and the app banner cares about the second. `SyncTriggers` derives the edges (`warmForeground`, `cameOnline`) that config refresh and ad preloading hang off
- **Remote config end-to-end**: typed `ConfiguredValue`s, offline-first fetch with kill-switch flags (`upgrade.maintenanceMode`, forced upgrade), QA overrides, and a hosted **admin console** (Kotlin/JS) with targeting rules, an audit log, and prod confirm-by-typing. Tests hold that every declared key has a bundled fallback and that every monetization key fails open toward the player
- **Telemetry that answers pages**: one `session_id` pivots Sentry issues, Grafana Loki logs, and Tempo traces; structured `logEvent`s ship over OTLP with disk-buffered durability; MetricKit exit reports on iOS
- **Dev tooling**: shake for the QA dialog, on-device Wiretap network inspector (debug-only, noop artifact in store builds), a living design-system catalog, in-app review prompting with sane eligibility gates

**Server (Ktor + Postgres, deploys to Fly.io)**
- Remote-config source + token-gated admin API, ban gate (403 envelope the client understands), session-correlated tracing/logging. No auth plugin and no user data
- Boots gracefully with zero config (limited mode) and ships a docker-compose local stack
- Two environments: dev auto-deploys on merge, prod behind an approval gate

**Process**
- CI from the first push: Android and iOS compile, unit/server/integration test jobs, release-please versioning, TestFlight/Play release pipelines, detekt with a custom user-facing-strings rule, conventional-commit hooks
- An **integration harness** that drives the real client stack against the real server over a real Postgres, in a unit test
- Tests that guard the docs as well as the code: every doc reference has to resolve to a file and a heading that exist, and the app-event registry and the Grafana dashboards are held against the code

## Build & Run

```shell
# Android
./gradlew :apps:compose:assembleDebug

# iOS - compile Kotlin framework
./gradlew :apps:compose:compileKotlinIosSimulatorArm64

# iOS - or open in Xcode
open apps/ios/iosApp.xcodeproj

# Server (boots in limited mode with zero config)
./gradlew :apps:server:run

# Server with a local Postgres
docker compose -f apps/server/docker-compose.yml up -d

# What CI gates a PR on
./gradlew :apps:compose:compileDebugKotlinAndroid :apps:compose:compileKotlinIosSimulatorArm64
./gradlew testDebugUnitTest -x :apps:integration:testDebugUnitTest
./gradlew detekt :detekt-rules:test
./gradlew :apps:server:test

# The integration tier, in its own job because it needs Docker
./gradlew :apps:integration:testDebugUnitTest
```

### First-time setup

See **[SETUP.md](SETUP.md)** for the hour-1/day-1 runbook: Fly dev/prod apps, GitHub secrets, Sentry/Grafana keys, store listings, and the first-release manual-promotion gotcha. Each step has the command and the expected output.

Before your first commit:

```shell
./scripts/install_hooks.sh   # installs the Conventional Commits + detekt hooks
```

## Project Structure

```
apps/compose/          # KMP entry point (Android + iOS)
apps/ios/              # Swift/Xcode wrapper
apps/server/           # Ktor + Postgres backend (Fly.io)
apps/admin/            # Kotlin/JS remote-config admin console
apps/integration/      # End-to-end harness (real client ↔ real server ↔ real DB)
features/<name>/       # Routes and public API
features/<name>/impl/  # Screens and ViewModels
libraries/<name>/      # Interfaces
libraries/<name>/impl/ # Implementations
tools/level-generator/ # Offline JVM CLI that generates and verifies the level packs
```

Architecture rules (enforced at Gradle configuration time), the ViewModel/DI/navigation patterns, and every convention live in **[AGENTS.md](AGENTS.md)**. It is written for AI agents and humans alike and is the single source of truth for how code here is shaped.

## Doc map

| Doc | What it covers |
|---|---|
| [SETUP.md](SETUP.md) | Init → running app → first release, step by step |
| [AGENTS.md](AGENTS.md) | Architecture, conventions, module rules, testing, the iOS landmines |
| [docs/reference/features.md](docs/reference/features.md) | What the game offers: every player-facing feature, its rules, its config keys, where it lives |
| [docs/decisions.md](docs/decisions.md) | Why anything non-obvious is the way it is. Newest first |
| [docs/todos.md](docs/todos.md) | The work queue. `docs/backlog.md` is the same for things nobody has committed to |
| [docs/store/](docs/store/) | Listing copy, data safety, icons, screenshots |
| [docs/practices/testing.md](docs/practices/testing.md) | Which layer catches which bug; fakes; the integration harness |
| [docs/practices/observability.md](docs/practices/observability.md) | The session_id pivot; finding one session across Sentry/Loki/Tempo |
| [docs/practices/app-events.md](docs/practices/app-events.md) | The structured-event registry + `logEvent` discipline |
| [apps/server/DEPLOY.md](apps/server/DEPLOY.md) | Fly.io two-environment deployment |
| [apps/admin/README.md](apps/admin/README.md) | The remote-config admin console |
| [docs/swift-kotlin-communication-patterns.md](docs/swift-kotlin-communication-patterns.md) | Exposing Kotlin to Swift and vice versa |

## This app was generated from a template, and owes it

Sodogku started as a checkout of a Kotlin Multiplatform template, which is where the DI wiring, the config stack, the telemetry pivot, the CI and most of the conventions in AGENTS.md came from. The template is a sibling repo, `KMPTemplate`, and it is not a dependency: nothing here pulls from it and nothing here updates when it changes.

What still runs between the two repos is a queue, and it only runs upward. An app in production hits the store review, the policy deadline, the R8 rule that only breaks at runtime, the Compose bug that only shows up at 60fps with real data. The template never does. So when something here turns out to be a bug in inherited code, or a fix that any new app would want on day one, it gets written to `KMPTemplate/docs/PORT-CANDIDATES.md`, **in the template repo, not this one.** There is no copy of that file here and there should not be; a generated app does not carry the template's backlog.

AGENTS.md has the rule and what a good entry looks like. The short version is that the diagnosis is worth more than the diff: the next person meets a symptom, not a cause.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
