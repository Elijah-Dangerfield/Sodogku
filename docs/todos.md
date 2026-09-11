# TODO queue

Work waiting to be done, one `##` section per item. The `nightly-run` skill takes
items off the top; the `feedback-triage` skill puts them on. Humans can edit it by
hand too, and should.

**This is a queue, not a changelog.** When an item ships, delete its section in
the same commit as the fix. An item that stays here with a tick next to it is an
item the next worker has to read and skip.

**Not the same thing as the polish punch list** in `BUILD-PLAN.md`. That table is
the owner's hand-written list from one sitting, kept next to the plan it belongs
to. This file is the standing queue the feedback loop feeds, appended to by a
routine. Keeping them apart is deliberate: a routine appending to a table inside
a two-thousand-line planning document conflicts with every human editing it.

## Format

Every item is exactly this shape. A worker should be able to start from the
section alone, without the conversation that produced it.

```markdown
## SD-<n> [P0|P1|P2] — <one line, imperative>

**Ask:** What should be different, in the reporter's terms. Quote them when the
wording carries intent.

**Done when:** The observable condition that ends this item. Not "refactor X",
but something you could check.

**Hints:** Where to start. File paths, the symbol that owns the behavior, what
has already been ruled out. Ends with the provenance line when it came from
feedback: `Sentry <url> · session <id> · <date>`.
```

**IDs** are one flat namespace, `SD-<n>`, assigned by incrementing the highest in
the file. Flat rather than per-area because area prefixes go stale the moment the
module structure moves, and the only thing an id has to do is be stable enough to
name in a commit message.

```shell
grep -oE '\bSD-[0-9]+' docs/todos.md | grep -oE '[0-9]+' | sort -n | tail -1
```

**Priorities.** `P0` the app is broken or losing data. `P1` real, wrong behavior
a player would notice. `P2` polish, or a rare edge. Nothing is `P0` because it is
annoying.

## Items

<!-- Newest at the bottom. -->
## SD-6 [P2] — Standing code review, by an agent that did not write the code

**Ask:** A recurring review pass looking for better ways of doing things:
additions worth making, cleanup worth doing, tests worth having. Not a lint run,
which the build already does.

**Done when:** A review has run over a named slice of the codebase and its
findings are either fixed or filed here as their own items. This item does not
close; re-run it and update which slices have been covered.

**Slices covered so far:**

- **The telemetry event surface**, 2026-09-10, against `6f3bf85`. Eight findings,
  now SD-39 through SD-46. Verdict: the pipeline and its guard test are sound and
  the values riding through it are not. Two existing tests were found unable to
  fail: `GrafanaLogTreeTest.samplingIsStablePerSession` stayed green with the
  sampler returning true for every session, and the rehearsal guards on
  `game.level_started` and `game.commit` could both be replaced with `if (true)`
  without moving any of 296 game tests.

**Still uncovered:** `GameViewModel.kt`, `libraries/scoring` and the difficulty
ramp, and the `libraries/ui` board and dog components.

**Hints:** Run it as a **Fable** subagent, and give it a *slice*, not the whole
repo. A review with no boundary returns a list of generalities. Slices worth
taking, roughly in order of how much has been built in them without a second
pair of eyes:

- `features/game/impl/GameViewModel.kt`, which is about 2,000 lines and has
  absorbed nearly every feature this project has shipped
- `libraries/scoring` and the difficulty ramp
- The telemetry event surface, against `DashboardQueryContractTest`
- `libraries/ui` board and dog components

What to point it at specifically, because these are the failure modes this
codebase actually has:

- **Tests that cannot fail.** Mutation-check the existing suite, do not just
  read it. Two real cases so far: a rehearsal guard whose condition was already
  unreachable, and a bones-pill test that dismissed the prompt instead of
  confirming, so it passed against the bug.
- **`startAttempt` in `GameViewModel`** builds a whole fresh `GameState` rather
  than `it.copy`, enumerating carried fields by hand. It has silently dropped a
  field three times.
- **`SEAViewModel.state` lags `updateState` by a dispatch.** Any suspend
  function that computes a value and then re-reads it from `state` is a bug.
- **Unobservable defaults.** Several fields are initialised to a value that
  `load()` always overwrites. Those are fine, but they should not be confused
  for behaviour, and a test for one would be unreachable.
- **Gradle tasks that are up to date when they should not be.** A test reading a
  file at runtime needs that file declared as a task input, or it silently stops
  running.

Ask for a ranked list with a file:line and a concrete failure scenario for each,
and require it to say which findings it verified versus which are hunches. Take
nothing on trust: reviews from agents have been confidently wrong here before.

<!--
SD-9 through SD-23 came out of one sitting on 2026-09-09: a feature-by-feature
read of Meowdoku (Oakever Games, 10M+ installs, #1 free puzzle) against what we
ship, plus the owner's own ideas in the same conversation. The competitor notes
live in `docs/reference/meowdoku.md`, which until now only covered the look.
-->
## SD-26 [P0] — The screen stops updating while it keeps taking taps

**Ask:** Owner, 2026-09-09, on iOS: *"idk whats happening but im clicking all
over and nothing is happening Im marking things, trying to open the side pine,
trying to go to achivements. Its not working."* Confirmed 2026-09-10 that the
board would not take marks either, and that it only recovered when the shake
dialog reappeared.

**The mechanism is settled. The interleaving that causes it is not.**

Three readings from the androidx and Compose Multiplatform sources, each of which
alone narrows it, and together leave one answer:

1. `NavBackStackEntryImpl.updateState()` sets an entry's lifecycle to
   `min(hostLifecycleState, maxLifecycle)`.
2. `NavControllerImpl.updateBackStackLifecycle()` never assigns a `maxLifecycle`
   below STARTED to the topmost entry, nor to the first non-`FloatingWindow`
   entry beneath one. The `nextStarted` walk exists to guarantee exactly that.
   **So no state of `FloatingWindowNavigator` or `FloatingWindowHost` can freeze
   the board.**
3. `DelegatingRouter.Bind` reads `LocalLifecycleOwner.current` from inside
   `AppNavigation`, a sibling of `NavHost` rather than a destination, so its
   drain gate is the **host** and no entry state reaches it.

The log signature this was reported with, `Enqueuing navigation` followed by
nothing executing, is therefore only producible by **the host being below
STARTED**.

**On iOS that has exactly one cause.** `UIKitLifecycleOwner` computes CREATED as
`!isViewAppeared || !isAppForeground`. `!isAppActive` alone yields STARTED, which
still drains everything, and `isAppForeground` only goes false on
`UIApplicationDidEnterBackground`. With the player looking at the screen, host
CREATED means `isViewAppeared == false`, and that flag moves only on
`viewDidDisappear` / `viewWillAppear` of the Compose hosting view controller. The
only thing in this app that fires those is a full-screen modal presented over the
host, and `present(` appears at exactly two call sites, both in
`AdNetwork.swift`: the rewarded ad and the UMP consent form.

**What is left is finding the interleaving** that makes a `viewWillAppear` go
missing after an ad dismisses. That needs a device. The candidate fixes all
change behavior in ways that are unsafe to ship unverified: presenting from a
dedicated `UIWindow` so the host never disappears changes whether the game keeps
running under the ad, and re-asserting with `beginAppearanceTransition` double
-fires the keyboard manager and is documented as something not to do to a
UIKit-managed child.

**Two earlier diagnoses in this item were wrong, and both are worth keeping as
corrections rather than deleting.**

The first pass blamed the `STARTED` gate in `FloatingWindowHost`. Ruled out by (2).

The second pass argued the host must have been healthy because the shake detector
still worked. That does not hold: `LifecycleStartEffect` at CREATED calls
`shakeHandler.stop()`, which stops CoreMotion. "It did not work again until the
shake dialog popped back up" reads at least as well the other way round, as the
host coming back, the detector restarting, the shake registering, and everything
draining at once.

The keyboard is also not a way in. `UIRemoteKeyboardWindow` changes neither view
appearance nor an app-level notification, so it cannot move the lifecycle owner
at all. Its only relevance is that it changes which window is key, which is what
`AdNetwork.rootViewController()` reads.

**Done when:** watching a rewarded ad to completion and returning to the board
leaves every control working.

**The instrumentation is in and it is what closes this.** `HostLifecycleWatchdog`
logs an error when a press reaches the root while the host has been below STARTED
for two seconds, which is a contradiction because a covered view takes no
touches. A host held down with nobody tapping stays silent, so an ad and a
backgrounding make no noise. This covers a blind spot in
`NavigationQueueWatchdog`, which arms on an enqueued command and would have
watched an empty queue throughout the original incident, since delivering the tap
to the view model was one of the things that stopped.

**Reproduce it like this**, on a device, with Sentry attached:

1. Reach `GameRoute` and trigger a rewarded ad, through Hint or the
   continue-after-fail path. Watch it to completion and dismiss it.
2. Tap Levels and Start over a few times.
3. Repeat five to ten times. It is intermittent and one clean run refutes nothing.

What the log settles:

- A `HostLifecycle` error naming presses against a host below STARTED is
  **conclusive**: the hosting view controller believes its view is off screen and
  the ad's `viewWillAppear` never came back. The fix is on the iOS presentation
  side.
- `Collection stopped for navigation queue` with no matching `Collection started`
  after the ad says the same thing from the other end.
- Controls dead with **no** `HostLifecycle` error, and the router's stall line
  reporting STARTED or RESUMED, means the mechanism above is wrong and the answer
  is somewhere it was ruled out. That log is worth more than any of this.
## SD-56 [P2] — An Android shortcut may be pointing at a package that is not installed

**Found by:** the SD-25 agent, 2026-09-10, which flagged it rather than shipping
around it.

`res/xml/shortcuts.xml` declares its intents with no `targetPackage` or
`targetClass`. The documented form hardcodes the package, and a resource file gets
no `${applicationId}` substitution, so the literal would read `com.sodogku` while
every debug install is `com.sodogku.debug`. Broken on exactly the build somebody
would test it on.

The agent used an implicit VIEW intent against our own `sodogku://` filter instead,
which should work and has not been tapped on a device.

**Done when:** somebody has long-pressed the icon on an Android debug build and
both entries appeared and launched.

**If they did not**, this is the cause, and the fix is a Gradle-generated
`@string/` holding the real application id rather than a literal in the resource.
Nothing else in the change would explain the entries being absent or dead.
## SD-65 [P1] — The store listing says 500 levels and the pack holds 1000

**Found by:** the SD-22 agent, 2026-09-10.

`docs/store/listing.md` claims 500 levels, and a bullet says the number matches
the pack. It did until today. This is copy that goes in front of a reviewer and
a customer, and it is the only stale claim of the fifteen SD-22 found that a
player could ever read.

**Done when:** the listing says a thousand, and nothing else in it is a number
somebody has to remember to update.

**Hints:** Sweep the rest of the file while you are in it, against
`docs/reference/features.md`, which is now derived from code. Sharing is gone,
there are leaderboards on both platforms, and the daily has no give-up. Any of
those could be in there too.

Worth asking whether the listing should name a level count at all. A number in
store copy is a promise that ages every time the pack grows, and "hundreds of
handmade boards" ages never.
## SD-66 [P2] — `README.md` still describes a template file this repo does not have

**Found by:** the SD-22 agent, 2026-09-10, while fixing the doc map.

`README.md`'s doc map links `docs/PORT-CANDIDATES.md` and a whole section
explains what it is for. The file does not exist here. It lives in
`Workspace/KMPTemplate`, which is the repo Sodogku was generated from, and the
cross-repo pointer is described in `AGENTS.md`.

The new `DocReferencesResolveTest` has it as a documented exemption, a set of
one, which is the right holding position and not the right answer.

**Done when:** the README either stops describing a file this repo does not
have, or says plainly that it lives in the template repo and why a reader here
would care.

**Hints:** Check the rest of `README.md` for the same thing. It is largely still
the template's, which SD-32 noted and only corrected where it had been made
definitively false. A full pass is this item.
## SD-67 [P2] — Three things nothing calls, and one config key no test can see

**Found by:** the SD-22 agent, 2026-09-10, while deriving the features doc from
code.

Four small ones, grouped because each is a line or two and they are all the same
kind of rot:

- **`config.refreshThrottleMs` is declared outside `SodogkuConfigValues` and has
  no `FallbackConfigMap` entry**, so neither completeness test sees it. Benign
  today and a real hole in the guard that is supposed to make config keys
  impossible to forget. Fix this one first; the others are tidying and this is a
  blind spot.
- **`features/home` is dead at runtime.** `HomeScreen` is a hardcoded dev
  launcher with three level buttons and a no-op ViewModel. Only the bug-report
  screen in that module is live.
- **`GameViewModel.clearSavedBoard` has no callers.**
- **`AppData.resetAccountScoped()` has no callers**, which is an account-era
  leftover SD-32 missed.

**Done when:** the config key is visible to both completeness tests, and each of
the other three is either deleted or has a caller.

**Hints:** Check `features/home` carefully before deleting it. A dev launcher
that nobody ships is still the thing somebody reaches for when they need to jump
to a level, and the QA panel may or may not have replaced it.

## SD-68 [P2] — `pushWithTransition` pins a live floating window at STARTED

**Found by:** the SD-34 agent, 2026-09-10, which deliberately left it alone.

`FloatingWindowNavigator.navigate` uses `state.pushWithTransition(entry)` where
androidx's `DialogNavigator` uses plain `push`. The effect is that a sheet or
dialog that is on screen and interactive is held at STARTED rather than reaching
RESUMED.

Git history says nothing about why: the file arrived whole in `02ec89c`, the
commit that generated Sodogku from the template, so it is inherited rather than
chosen.

**Not folded into the SD-62 fix on purpose.** Changing it is a real behavior
change to every sheet and dialog in the app, and SD-62 was about an entry that
leaks, not about the state of one that works.

**Done when:** it matches upstream, or a comment says why it should not.

**Hints:** Work out what actually differs at RESUMED before changing it. A
`collectAsStateWithLifecycle` inside a sheet gates on STARTED and would not
notice, so the answer may be nothing, in which case the fix is the comment. The
composition test tier can now drive this: assert the sheet entry's state while it
is on screen.
