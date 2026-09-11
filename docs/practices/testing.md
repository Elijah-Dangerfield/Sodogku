# Testing approach

How this codebase tests, layer by layer, and the conventions every new test
follows. The goal is a pyramid where each bug class is caught at exactly one
layer, the cheapest layer that can fail when it breaks.

Two things here are house rules rather than preferences, and they are the two
most likely to be skipped by someone reading quickly. Every change is
[mutation-checked](#mutation-checking-is-the-house-rule) before it is called
tested, and a decision that lives inside a composable gets
[pulled out into a function](#push-the-decision-out-of-the-composable) so
something can test it.

## The layers

- **Unit tests** (`commonTest` in each module). Pure logic, repositories over
  hand-rolled fakes, and SEAViewModel behaviour. This is where most of the suite
  lives and where almost every new test should go. `SettingsViewModelTest` is the
  reference recipe for a view-model test: extend `CoroutineTest` (from
  `:libraries:flowroutines:testing`), hand-roll a fake per repository
  dependency, drive actions, assert on `vm.state`.
- **Composition tests** (`androidUnitTest`, Robolectric). A real composition,
  driven and asserted on the host JVM. Only for claims that are about
  composition itself. [See below](#composition-tests).
- **Source-scanning guards**. Tests whose subject is the repo rather than the
  program: does every declared config key have a reader, does every dashboard
  query name an event something emits, does every doc citation resolve.
  [See below](#source-scanning-guards).
- **Server unit and route tests** (`:apps:server` `src/test`, run with
  `./gradlew :apps:server:test`). Plugins and routes through Ktor's
  `testApplication` (`ExampleRoutesTest` is the reference), repositories over a
  Testcontainers Postgres via the `DatabaseTest` base class.
- **End-to-end integration** (`:apps:integration`). The real client stack over
  real TCP against a real server on a real Postgres.
  [See below](#the-integration-harness-appsintegration).

Two tasks cover the client: `./gradlew testDebugUnitTest` runs everything in
`commonTest` and `androidUnitTest` across every module, and `./gradlew jvmTest`
re-runs the same `commonTest` sources on the two modules that carry a `jvm()`
target, `:libraries:puzzle` and `:libraries:levels`. Those targets exist so
`tools/level-generator` can verify and write packs through the exact code the app
reads them with, not for testing, and there are no JVM-only test sources. So
`jvmTest` is duplicate coverage, CI does not run it, and it is in the local sweep
only because it is nearly free once the rest has built.

## Mutation checking is the house rule

A test that cannot fail is worse than no test, because it occupies the space
where a real one would have gone and it reports green forever. This repo has
found that failure in the wild often enough that mutation checking is expected
on every change, not saved for the interesting ones. A recent review of
`GameViewModel` turned up four tests that could not fail, three of which stayed
green with the behaviour named in the test's own function name deleted.
`LifetimeScore.withAttempt` is pinned in both directions only because the check
showed that each direction alone passes a different wrong implementation: a test
that a replay does not double-count passes against "ignore the attempt
entirely", and a test that the number climbs passes against "always add".

There is no mutation framework here. You edit the production source by hand,
run the tests that claim to cover it, and read what happened.

1. **Count the string first.** `grep -c` the exact text you are about to
   substitute and confirm it appears once. A substitution that matches nothing
   leaves the source untouched, and the run that follows is a run against the
   original code. Whatever colour it comes back, it is not evidence.
2. **Apply the edit**, and `diff` against a copy you took first so you can see
   the mutation you actually made rather than the one you meant to make.
3. **Run the owning module's task**, scoped: `./gradlew
   :features:game:impl:testDebugUnitTest --tests '*LossFactsTest*'`. Not the
   bare `testDebugUnitTest`. See the trap below.
4. **Read the JUnit XML**, at
   `<module>/build/test-results/testDebugUnitTest/TEST-*.xml`. The console tells
   you the colour and not the reason.
5. **Restore from the copy** and `diff` again to prove you did. Never
   `git checkout <path>`: that reverts to HEAD, which is not where you were if
   you had uncommitted work.

### The three traps, all of which have cost real time

**A `--tests` filter on a whole-repo task fails in every module that does not
contain the class.** `./gradlew testDebugUnitTest --tests '*LossFactsTest*'`
ends in `BUILD FAILED` with a wall of `No tests found for given includes` from
`:libraries:networking`, `:libraries:achievements` and a dozen others, while the
module that does own the test runs it and passes. Read quickly, `BUILD FAILED`
after a mutation says the mutation was killed. It says nothing of the sort.
Always name the module: `:features:game:impl:testDebugUnitTest --tests …`.

**`-q` hides the answer.** A failing scoped run under `-q` prints `BUILD FAILED`
and a link to a build scan, with no test names in it at all. One sweep read that
output and reported "killed by nothing" for eighteen fields that were in fact
covered. The XML has a `failures` count on the suite and a `<failure>` under each
test that failed, carrying the assertion message. That is the only place the
answer is.

**A build failure is not a test failure.** A mutation that does not compile, a
Gradle daemon that dies mid-run, a filter that matched nothing: all of them are
red, and none is a test doing its job. Before recording a kill, find the
assertion that failed and check it is one that should have.

## Push the decision out of the composable

No test in this repo can construct an `AnimatedContentTransitionScope` or read
what a `@Composable` returned. So a rule that lives inside one is a rule nothing
can check, and the fix is almost always to move the decision into a pure
function or onto view-model state and leave the composable drawing the answer.

`RouteTransitions` is the case that made the argument. The rules for what each
screen does during a navigation were inline `NavHost` lambdas in `App.kt`,
taking a `NavBackStackEntry` inside an `AnimatedContentTransitionScope`, neither
of which a unit test can build. The only way to check a transition was to open
the app and watch one, which is why the paywall bug survived: opening Pro from
Settings slid Pro up and slid Settings out to the left at the same time, so the
screen that should have been holding still was the one that appeared to move.
The rules moved into `RouteTransitions` and six tests cover them now, including
against the original bug.

`lossFacts` is the same move at a smaller scale. Which measurements a lost run
is allowed to report is the only decision on the lose sheet, and both of its
interesting cases are refusals that are invisible from the code drawing the row.
As a `buildList` inside a composable it was untestable. As a function taking two
ints it has five tests, and two mutations this week died there and nowhere else,
because no view-model test can see which pills a sheet chose to draw.

The shape to copy: a function over plain values, an `enum` or a data class for
what it returns, and a `@Composable` beside it that does nothing but render.
`lossFacts` / `lossStats` in `GameOutcomeSheets.kt` is the smallest complete
example.

## Composition tests

Sometimes the claim really is about composition, and then this tier is the only
one that can reach it.

`FloatingWindowHostTest` (`:libraries:navigation`) is the worked example and the
first thing in this repo that ever asserted against a composition. It drives a
real `NavController` and the real `FloatingWindowHost` through push-then-pop and
asserts the popped entry was released, including the entry that was popped
before it ever composed. `FloatingWindowHost` completes its navigator transition
from `onDispose`, so an entry with nothing to dispose stays pinned in
`transitionsInProgress` forever. That is a claim about `DisposableEffect` and
recomposition, and no view-model test can make it.

The recipe, to copy:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MyThingTest {
    @get:Rule val compose = createComposeRule()
    …
}
```

Where it lives and why: **`androidUnitTest`, not `commonTest`, and not a `jvm()`
target.** `commonTest` also compiles for iOS, where none of the harness
resolves. A `jvm()` target would be the Compose Multiplatform-idiomatic answer
(`runComposeUiTest` on desktop) but it cascades through
`:libraries:{core,ui,resources,sodogku,flowroutines,storage}`, each needing
`actual` stubs for permission launchers and image decoding, plus a third value
on the two-case `Platform` enum that several exhaustive `when`s read. That is a
lot of shipped surface invented to serve tests, and it contradicts the stance
`:apps:integration` already took, which is to reuse the Android variants on the
host JVM.

Setup lives in `libraries/navigation/build.gradle.kts`. Two parts are
load-bearing and worth copying wholesale into the next module that wants this:
`testOptions.unitTests.isIncludeAndroidResources = true`, and the
`stageRobolectricJars` task. The second one resolves Robolectric's Android
framework jar through Gradle and runs Robolectric offline against the staged
copy, instead of letting it fetch around 100MB from Maven into `~/.m2` at test
time. Nothing caches `~/.m2` on CI, and a network call inside a test is how a
tier earns a reputation for flaking.

It runs under `./gradlew testDebugUnitTest`, so CI's existing unit-test job
already covers it.

**When not to reach for it.** `:libraries:navigation` is still the only module
with a composition test, and that is the right number for now. This tier is slow
(a Robolectric sandbox per class) and it is the easiest place in the codebase to
write something that passes for the wrong reason: a `waitForIdle` that returns
before the thing you care about, a tag that matches a node you did not mean, an
assertion on a node that would be there either way. Anything a view-model test
or a pure-function test can answer belongs there instead, and if the answer is
"a composable is the only place this decision exists", the fix is
[the section above](#push-the-decision-out-of-the-composable) rather than this
one. Do not reach for it to cover the board's gestures.

## Source-scanning guards

A class of test here has the repository as its subject. It reads the working
tree at runtime and fails when two things that have to agree have drifted apart.
These exist because the mistakes they catch are invisible everywhere else: a
Grafana panel querying an attribute nothing emits renders empty, which is what a
healthy panel looks like before launch, and a config key nobody injects is a
console page an operator can edit while nothing happens.

The ones in the repo today:

| Guard | Holds |
|---|---|
| `ConfigValuesAreReadTest` | every declared config value is injected somewhere |
| `ConfigDeclarationsAreEnumeratedTest`, `ConfigManifestRegistryDriftTest` | the client's keys, the fallback map and the admin registry agree |
| `DocReferencesResolveTest` | a cited doc exists, and a cited anchor names a real heading |
| `DashboardQueryContractTest` (`:libraries:telemetry:impl`) | every dashboard query names an event and attribute a real `logEvent` emits |
| `UserFacingCopyStyleTest`, `BoosterNamingTest` | house style in the strings a player reads |
| `NoIdentitySeamsTest` | the privacy page's two claims about Sentry stay true |
| `ShortcutEntriesAgreeTest` | the home-screen shortcuts say the same thing on both platforms and point at links the app knows |
| `LaunchScreenMatchesTheAppTest`, `SharedSchemeExistsTest` | the iOS project files |
| `FeedbackTriageQueryContractTest` | the triage skill's Sentry queries against the enum behind the tags |

Most live in `:apps:integration` because it is an `:apps:*` module and may
therefore depend on impls, which is what lets it read the real classes instead of
pinning literals as an equivalent test in a library module would have to.
`DashboardQueryContractTest` is the exception and sits next to the logging it
holds, in `:libraries:telemetry:impl`. It can, because a text scan needs no
dependency on the modules it reads.

**Whatever the test reads at runtime has to be declared with `inputs.files(...)`
on the `Test` task.** This is not optional and it is not a performance tuning
knob. Gradle has no way to know a test read a file, so without the declaration
the task stays `UP-TO-DATE` when that file changes and the guard reports green
without running. It has silently blinded a guard four separate times in this
repo, and every one of them looked like a working test:

- the config manifest registry, where the first mutation run came back
  `BUILD SUCCESSFUL` against a registry with a key deleted and three values
  wrong;
- the iOS project files, where mutating the launch colour and then deleting the
  shared scheme both left the suite green, and the earlier runs that did fail
  only failed because unrelated Kotlin happened to be changing at the same time;
- the string resources, scoped to `:libraries:resources` while every feature
  carries its own `composeResources`, so a forbidden comment in the streak
  feature's strings went green and only went red under `--rerun-tasks`;
- the agent skills, where the enum half was covered by the `*.kt` tree and the
  markdown half was not.

`apps/integration/build.gradle.kts` has all of them with the reason written
above each. Declare a filtered `fileTree`, never `inputs.dir` on a source
directory: that sweeps in `*/build/**`, which is another task's output, and
Gradle correctly rejects the undeclared dependency, turning the whole suite red
while the test passes on its own.

**What a source scan proves, precisely.** That a name is spelled the same in two
places. It does not prove the code path runs: a `logEvent` in dead code counts
as emitted. That is the trade, it is deliberate, and each of these tests says so
in its own docblock. When you want the stronger claim, assert on behaviour
instead. `RecordingEvents` (`:libraries:flowroutines:testing`) plants a
`LogTree` so a test can assert on the events a screen actually emitted, which is
how an inverted `game.commit.on_marked` was finally caught after every
name-level check had passed it.

It started in `:features:game:impl`'s `commonTest` and moved out when the same
question came up in a second module. A name scan also cannot see an event going
out *twice*, and the duplicate one-shot completion turned out not to be a
game-screen shape: `tutorial.completed`, `onboarding.completed` and
`game.campaign_completed` were each written to log unconditionally on a control
whose only guard was state that lags a dispatch behind the tap (SD-85, SD-87).
Reaching the second tap needs no UI harness. `SEAViewModel` drains one channel
in one loop, so two `takeAction` calls and a settle are two full handler runs.

## The integration harness (`:apps:integration`)

An Android-library module whose tests run as host-JVM Android unit tests
(`./gradlew :apps:integration:testDebugUnitTest`), the same compilation path the
feature view models already use, so the harness can construct them directly.
`commonMain` is deliberately empty: nothing ships from this module, and the iOS
target must never try to link the JVM-only server.

What runs where in a harness test:

- **In-process, real:** a Netty engine on an ephemeral port booted through the
  production `installApp(component, adminConfig, configChangeNotifier)` seam
  (`InProcessServer`), and a real `ServerComponent` over a Testcontainers
  Postgres with the real Flyway migrations.
- **In the client, real:** `NetworkClientImpl` with the real headers provider,
  reachability tracker and access-denied bus, and the real
  `RemoteConfigRemoteDataSource` over it (`TestClient`). Requests travel real
  TCP, so real serialization, real headers, real status codes.
- **Fake, on purpose:** exactly the seams a device would own, which is the
  install and session ids and on-disk persistence (an in-memory `CacheFactory`
  running the real store logic).

There is no auth anywhere in this stack. Sodogku has no accounts, so every route
is ungated and the `AuthGate` seam is bound to `AlwaysReadyAuthGate`. See the
"No accounts" section of `AGENTS.md`.

`HarnessSmokeTest` pins the worked example: boot the server, seed a config value
in Postgres, and assert a real client reads it back over HTTP. New end-to-end
flows should follow its shape. Add a client surface to `TestClient`, a probe or
seed helper to `InProcessServer` if the server side needs one, and await state
with `awaitState` / `awaitUntil` rather than a fixed sleep.

Docker down means the suite skips (JUnit `Assume`) rather than failing, so a
contributor without Docker still gets a green build. That courtesy is only safe
because it is off on CI: both this harness and the server's `DatabaseTest` turn
the skip into a hard failure when `CI` is set, since a green job that ran none of
its tests is worse than a red one.

**Fault injection** is worth knowing about even though this app does not need it
yet. When a project grows a long-lived transport (a WebSocket, a sync loop),
wrap the real transport in a *decorator* that can drop, block or delay frames on
command, and give the harness a switch to install it. The decorator implements
the transport interface and forwards to the real one, so reconnect and presence
machinery is exercised over real plumbing with surgically induced failures, and
there is no mock transport quietly diverging from the real one.

## Which layer catches which bug (do not duplicate)

When you are tempted to assert the same thing at two layers, the lower one wins
and the higher one does not get written.

| Bug class | Owning layer | NOT here |
|---|---|---|
| Pure logic, data mapping, validation rules | library unit tests | never re-tested above |
| Action to state derivation in a view model | feature `commonTest` | not integration |
| What a screen decides to show, given state | a pure function next to the composable | not a composition test |
| Effects running or disposing, recomposition, what is on screen | composition tests | not view-model tests, which cannot see a composition |
| Two committed artifacts naming each other | a source-scanning guard | not a runtime test, which usually cannot reach both |
| Route status codes, error envelopes, response shapes | `:apps:server` route tests | not integration |
| SQL, migrations, repository contracts | `:apps:server` Testcontainers tests | not route tests |
| Client/server contract drift (serialization, headers, real HTTP semantics) | `:apps:integration` | not unit tests with canned JSON |

Integration tests are not a substitute for unit tests. They are slower and
harder to debug. Use them for the seam contract, not for every rule the lower
layers already own.

## Conventions

- **Hand-rolled fakes only.** No Mockito, no MockK, anywhere. Fakes go in
  `commonTest` next to their consumer, gathered into a `…TestDoubles.kt` when a
  module has several (`AdTestDoubles`, `BillingTestDoubles`,
  `LeaderboardTestDoubles`, `StreakTestDoubles`), or into a shared
  `:libraries:x:testing` module when they are reused across modules.
- **Dispatcher choice:** `UnconfinedTestDispatcher` (the `CoroutineTest`
  default) for most tests, because continuations run eagerly, mirroring
  `viewModelScope` under `Main.immediate`. `StandardTestDispatcher` only for
  time-sensitive tests (timeouts, debounce, backoff) that need an explicit
  dispatch-then-advance cadence. Integration tests use a **real** Main
  dispatcher (`Dispatchers.setMain(Dispatchers.Default)`) because real sockets
  run on real threads.
- **`runCurrent()` not `advanceUntilIdle()`** when there is an unwanted
  future-scheduled task such as a `withTimeout`, which `advanceUntilIdle()`
  fires.
- **`runCatching { … }` not `assertFailsWith { … }` around a *suspend* body.**
  `assertFailsWith` has subtle suspend-context issues where an async-thrown
  exception reaches the test scope before the assertion runs. Call the suspend
  function inside `runCatching`, then assert on the captured result. Around a
  plain call `assertFailsWith` is fine, and is what most of the suite uses.
- **A top-level KDoc on every test file** saying what is covered and what is
  deliberately not, and where the not-covered part lives instead. The small
  pure-logic files whose name is the whole story are the usual exception; a
  view-model test or a guard is not one of them.
- **Assertion messages are for the reader who did not write the test.** Say what
  the failure means, not what the numbers were. The runner already prints those.
- **No fixed sleeps.** Await a flow (`awaitState`), or poll with a timeout
  (`awaitUntil`) when the state has no push signal.
- **Integration tests belong in `:apps:integration`.** Do not inline a real
  server into a feature-module test.
