# AGENTS.md

Guidelines for AI agents working in the Sodogku repository.

## Overview

KMP (Kotlin Multiplatform) app with Compose Multiplatform. Modular architecture with Room database, navigation, and SEAViewModel pattern.

This is **Kotlin Multiplatform**—most code is shared, but some platform features (permissions, sensors, native APIs) require platform-specific implementations. When implementing something not inherently cross-platform, follow the patterns in `docs/swift-kotlin-communication-patterns.md`.

## Build Commands

```shell
./gradlew :apps:compose:assembleDebug          # Android
./gradlew :apps:compose:compileKotlinIosSimulatorArm64  # iOS Kotlin
xcodebuild -project apps/ios/iosApp.xcodeproj -scheme iOS -sdk iphonesimulator  # iOS full
```

## Module Structure

```
apps/compose/          # KMP entry point (Android + iOS)
apps/ios/              # Swift wrapper
features/<name>/       # Routes, public API
features/<name>/impl/  # Screens, ViewModels
libraries/<name>/      # Interfaces
libraries/<name>/impl/ # Implementations
```

**Rules** — enforced at Gradle configuration by the convention plugins:

- Only `:apps:*` may depend on `*:impl`. Impls are DI wiring composed by the app, not consumed by other modules.
- Feature `impl` modules may depend on another feature's `api`. Feature `api` modules may **not** depend on other feature `api`s (api-to-api is a cycle risk — shared types go in a library).
- Sub-modules of the same feature (`:features:foo:storage` → `:features:foo`) are allowed.
- `:libraries:storage:impl` is the one shared impl — it owns the `AppDatabase`.

Shared code → libraries. Main modules expose interfaces only; impl modules contain implementations.

## Conventional Commits (required)

Every commit (and every PR title — PRs are squash-merged) must follow [Conventional Commits](https://www.conventionalcommits.org/). Release-please derives the next version bump from commit history.

| Type | When | Version bump |
| --- | --- | --- |
| `feat:` | User-visible new capability | minor |
| `fix:` | Bug fix | patch |
| `perf:` | Perf improvement, user-visible | patch |
| `feat!:` / `BREAKING CHANGE:` | Breaking change | major |
| `refactor:`, `style:`, `test:`, `docs:`, `ci:`, `build:`, `chore:`, `revert:` | No user impact | none |

A local `.githooks/commit-msg` hook enforces this on every commit. The Gradle build fails with an install-hooks message if the hook isn't wired — run `./scripts/install_hooks.sh`.

## Convention Plugins

| Plugin | Use |
|--------|-----|
| `sodogku.kotlin.multiplatform` | Pure Kotlin |
| `sodogku.compose.multiplatform` | Kotlin + Compose |
| `sodogku.feature` | Feature modules |
| `sodogku.application` | apps:compose only |

Use `/scripts/create_module` for new modules.

## DI (kotlin-inject-anvil)

```kotlin
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class MyImpl : MyInterface

// Multibinding for FeatureEntryPoints
@ContributesBinding(AppScope::class, multibinding = true)
```

No expect/actual for platform impls—bind different implementations per platform. iOS impls written in Swift get passed into the DI graph via `IosAppComponentFactory.create(...)`.

### Boot-time construction: the `AutoInit` marker

Kotlin-inject singletons are constructed lazily on first injection, so a repo that nobody touches until a deep nav target stays cold — a hydrate-from-disk or listener-registering `init {}` doesn't run until something injects the class.

For singletons where the warm path matters (app-lifecycle dispatchers, disk-backed repositories, anything whose `init {}` is load-bearing), implement [`AutoInit`](libraries/core/src/commonMain/kotlin/com/sodogku/libraries/core/AutoInit.kt) and contribute a second binding via multibinding:

```kotlin
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = MyRepository::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class MyRepositoryImpl(...) : MyRepository, AutoInit
```

The `Set<AutoInit>` is resolved at app start (`Application.onCreate` on Android, `iOSApp.init` on iOS, `App.kt` remember-block on first composition). Resolving the set forces every contributor to construct, which runs their `init {}` — that's where hydrate-from-disk and lifecycle-listener registration happen.

**Opt in** when there's first-touch latency the user notices, an `init {}` that registers a listener, or a cache that needs its observer running before the user can navigate. **Skip** for debug-only / QA-menu singletons and anything whose `init {}` is empty. Forgetting the marker is a perf regression, not a correctness one — the class still works lazily — so the bigger risk is overuse making boot slow.

## No accounts

Sodogku has **no auth, no accounts, and no user-scoped server state**. The template's
`:libraries:identity`, its Supabase auth screens, the session-expired recovery route, and the
`UserScopedSyncer` / `UserScopedDataReset` machinery were all deleted in C0 — see
`docs/decisions.md`. Don't reintroduce them; if you find yourself wanting a user id, read the
"switching phones" section of `docs/SPEC.md` first.

What survives, and why:

- **`AuthGate`** still has its seam in `:libraries:core`, bound to `AlwaysReadyAuthGate` in
  `:libraries:networking` (next to `NoOpAuthTokenProvider`, and in the api module for the same
  boundary reason). Every call and route is ungated.
- **`SyncTriggers`** (`:libraries:sodogku:impl`) keeps `warmForeground` / `cameOnline` /
  `isOffline`. There is no `activeAccount` level any more. Use these edges for anything
  network-touching (config refresh, ad preloading); read `isOffline` before starting work that
  should defer.
- **`AccessDeniedBus`** still routes a `403` locked envelope to a blocking screen.
  `SessionRejectionBus` exists but nothing can trigger it.
- **Progress is device-local.** It does not survive a reinstall, and that is stated in Settings.

## Player-facing copy goes through `:libraries:resources`

The `VerifyStrings` detekt rule is enforced, not baselined. Copy passed to a DS `Text(...)` must
come from `stringResource(Res.string.…)`, backed by
`libraries/resources/src/commonMain/composeResources/values/strings.xml`. `OnboardingScreen` is
the worked example. Add `implementation(projects.libraries.resources)` to a feature's `impl`
module and import from `sodogku.libraries.resources.generated.resources`.

The `config/detekt/baseline.xml` still covers the template's leftover screens; convert each as
you replace it with real game UI rather than adding new baseline entries.

## Server (`:apps:server`)

A Ktor + Postgres backend serving exactly two things: `/_health` and remote config (plus the
`:apps:admin` console that edits it). There is no auth plugin and no user data — see "No accounts"
above. It reuses the client's conventions—kotlin-inject + anvil DI (`ServerScope` /
`ServerComponent`), the `domain/` interface + `data/` impl split, one `fun Route.xRoutes(deps)` per
resource—and degrades gracefully (boots with no DB at all). It's a plain JVM module, so it applies
plugins directly rather than via a convention plugin.

Remote config is the live-ops lever and the reason the server exists. `docs/SPEC.md` §4 owns the
rule for what belongs in config versus the binary; every key needs a `FallbackConfigMap` entry and
monetization keys must fail *open* toward the player.

The full reference—how to add a route, repository, migration, or config value, plus the auth, persistence, and testing patterns—lives in [`apps/server/README.md`](apps/server/README.md). Read it before touching the server.

## Testing

Conventions (hand-rolled fakes only, dispatcher choice, which layer catches which bug) live in [`docs/practices/testing.md`](docs/practices/testing.md) — read it before adding tests. The end-to-end tier is `:apps:integration`: an Android-library module whose tests run on the host JVM (`./gradlew :apps:integration:testDebugUnitTest`, needs Docker) and drive the real client stack — real `HomeViewModel`, real repositories, real HTTP client — over real TCP against a real in-process Ktor server on a Testcontainers Postgres. `HarnessSmokeTest` is the worked example; `commonMain` stays empty so iOS never links the JVM-only server.

## SEAViewModel Pattern

```kotlin
class MyViewModel : SEAViewModel<State, Event, Action>(initialStateArg = State()) {
    override suspend fun handleAction(action: Action) {
        when (action) {
            is Action.Load -> action.updateState { it.copy(loading = true) }
        }
    }
}
```

- **State**: Immutable data class for UI
- **Event**: One-shot side effects (navigation, toasts)
- **Action**: Only way to mutate state via `action.updateState { }`

## Navigation

Routes are `@Serializable` data classes extending `Route`. Register in `FeatureEntryPoint.buildNavGraph()`:

```kotlin
screen<MyRoute> { backStackEntry -> MyScreen(...) }
bottomSheet<SheetRoute> { backStackEntry, sheetState -> ... }
dialog<DialogRoute> { backStackEntry, dialogState -> ... }
navigation<MyGraph>(startDestination = MyRoute()) { screen<...>; bottomSheet<...> }
```

### iOS/Native landmines (production crashes, both)

1. **Routes must be `class` (or `data class`), never `data object`.** A
   `data object` route SIGSEGVs at navigate time on iOS — Native's
   serialization of object routes crashes inside androidx.navigation. An
   arg-less route is still a `data class MyRoute(...)` extending `Route`
   with default args.
2. **Every enum (or other non-primitive) route arg must be `@Serializable`
   AND registered in a typeMap.** Base-class args (`enter`/`exit`/`popExit`)
   come from `baseRouteTypeMap`, which every `screen<>`/`dialog<>`/
   `bottomSheet<>`/`routeDeepLink<>` builder merges in automatically. Args
   you add to your own route need `typeMap = mapOf(typeOf<MyEnum>() to
   serializableType<MyEnum>())` at the registration site. Miss one and
   graph-build throws `could not find any NavType for argument …` — often
   naming a *different* arg than the one you forgot. Use `routeDeepLink<T>`
   for deep links, never bare `navDeepLink`.

**Use `bottomSheet<>` for transient picker / overlay UIs** (a settings list, a "select an item" sheet) rather than pushing a full screen. The backstack stays one entry deep, the underlying screen is visible under a scrim, and `sheetState.dismiss()` is a clean exit. Reach for full `screen<>` only when the destination is its own context (settings page, detail view).

**Open external URLs via `Router.openWebLink(url)`** — don't roll your own platform `Intent.ACTION_VIEW` / `UIApplication.shared.open` plumbing. The implementation is in `libraries/navigation/impl/.../{Android,Ios,Jvm}WebLinkLauncher.kt` and is already wired into the DI graph and the `Router` interface.

## App-wide state

`AppData` (in `libraries/<projectid>/.../AppCache.kt`) is a `@Serializable` data class persisted via `CacheFactory.persistent`. Add fields here for things like:

- Onboarding flags (`hasUserOnboarded`)
- User-facing setting toggles
- Counters / lightweight telemetry (`feedbacksGiven`, `bugsReported`)

Don't roll a new persistent cache for a single boolean — extend `AppData`. Round-trip is automatic via `versionedJsonSerializer` (missing fields fall back to defaults, so adding a field is non-breaking). For an example wrapper that exposes `StateFlow<Boolean>` for Compose, see how a feature-level store reads `AppCache.updates` and writes via `appCache.update { it.copy(...) }`.

## Cross-cutting state in Compose

When something (a service, a setting, a theme value) is needed by every composable in a subtree but doesn't belong on the screen-level ViewModel, prefer a `staticCompositionLocalOf` over threading parameters. Provide it once at the subtree root:

```kotlin
val LocalMyService = staticCompositionLocalOf<MyService> { NoopMyService }

// At the screen root:
CompositionLocalProvider(LocalMyService provides realService) {
    HorizontalPager(...) { … }
}
```

Default it to a noop, never `error("not provided")`. This keeps `@Preview` and unit tests trivial — they get the noop automatically.

## Coding Guidelines

- Code like a staff engineer
- Use `Catching { }` from libraries/core instead of `runCatching`
- No comments in code
- Custom UI components in libraries/ui—avoid Material directly
- Check `ComposeApp.h` for Swift names of Kotlin types before using in Swift

## This template is fed by the apps built from it

Apps generated from here run into production before the template does. They hit the App Store review, the Play policy deadline, the R8 keep rule that only breaks at runtime, the Compose gotcha that only shows up at 60fps with real data. That knowledge is worth more than anything written speculatively in this repo, and it only arrives if someone carries it back.

**If you are working in a generated app, port it back.** Two things qualify, and both go to the same place — `docs/PORT-CANDIDATES.md` **in the template repo**, which is a queue of work for this repo, not a log of what has been done:

- **Something you built that a brand-new app would want before it has any features.** Say what it is, why a generic app wants it, and the path to copy from. Don't port speculatively; something that hasn't survived production downstream is not yet worth this repo's maintenance.
- **A bug in code you inherited from the template, or a fix that generalizes.** These matter more, because every generated app already has them. Say what broke, *how it looked from the outside*, and why it was hard to spot — the next person meets a symptom, not a cause. Worth writing even when the fix is one line: the diagnosis is the value, not the diff. If you can fix it in the template yourself, do that and skip the entry.

Note that file lives in the template only; a generated project doesn't carry a copy, so you are writing across repos on purpose.

**If you are working in this template**, `docs/PORT-CANDIDATES.md` is the queue. Take from it in priority order. Delete entries as you land them rather than ticking them off, so the file stays a queue and not a changelog.

**Generalize on the way in.** A port arrives shaped like the app it came from. Strip its domain, name it for what it does rather than what it did, and keep the *reason* — the comment explaining why a rule exists is usually the most valuable line in the diff, because it is what stops the next person deleting it.

## Known landmines

Each of these cost a downstream app real time. They are cheap to avoid and expensive to rediscover.

- **Compose Multiplatform's iOS klib only ships the JetBrains `Preview` annotation.** Migrating previews to `androidx.compose.ui.tooling.preview.Preview` compiles on Android and fails the iOS link. One app migrated 171 files before finding out, and only because it compiled the iOS target for an unrelated reason. Use `org.jetbrains.compose.ui.tooling.preview.Preview`.
- **Routes must be `class`, never `data object`.** A `data object FooRoute : Route()` SIGSEGVs the iOS navigator at navigate time. Also covered under Navigation.
- **Enum route arguments must be `@Serializable`** or the graph crashes at build time on iOS/Native. JVM tests will not catch it.
- **`UIApplication.canOpenURL` needs its scheme declared in `LSApplicationQueriesSchemes`.** Undeclared, it returns false for everything, and a launcher that checks it first silently opens nothing — every outbound link in the app dies with no error.
- **Infinite animations hang preview and screenshot capture.** Anything looping forever must return a fixed value under `LocalInspectionMode`, or a screenshot test waits for an idle state that never arrives.
- **Reading an animated value during composition recomposes the whole subtree every frame.** `val x by animateFloatAsState(...)` read in a composable body is the single most common Compose performance bug; feeding text with it thrashes Skia's glyph cache and can wedge the RenderThread into an ANR. Read it in `graphicsLayer`/`drawBehind` instead. A detekt rule for this is a listed port candidate.

## iOS Notes

- iOS framework compiled from `apps/compose`, embedded as `ComposeApp.xcframework`
- Swift types passed to Kotlin via `IosAppComponentFactory.create(...)`
- Reference `apps/compose/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/Headers/ComposeApp.h` for generated Swift interfaces
- **Use `@ObjCName("TypeName", exact = true)` on Kotlin types used from Swift** to give stable names that won't change when project is renamed:
  ```kotlin
  @file:OptIn(ExperimentalObjCName::class)
  import kotlin.experimental.ExperimentalObjCName
  import kotlin.native.ObjCName
  
  @ObjCName("MyType", exact = true)
  interface MyType { ... }
  ```
  Note: The `exact = true` parameter prevents module prefixes from being added. Without it, the Swift name would be `<ModuleName><ObjCName>` (e.g., `ComposeAppMyType`).

## Key Files

| Purpose | Path |
|---------|------|
| User model | `libraries/sodogku/src/.../User.kt` |
| SEAViewModel | `libraries/flowroutines/src/.../SEAViewModel.kt` |
| App DI | `apps/compose/src/.../AppComponent.kt` |
| iOS entry | `apps/ios/iosApp/iOSApp.swift` |
| Swift↔Kotlin patterns | `docs/swift-kotlin-communication-patterns.md` |

