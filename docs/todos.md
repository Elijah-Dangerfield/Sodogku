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

- **`GameViewModel.kt` and its neighbors**, 2026-09-10. Twelve findings, now
  SD-73 through SD-84. Verdict: the file is in good shape and disciplined about
  its known footguns, and the real defects are at its seams. 56 mutations run,
  44 caught. The two rehearsal guards the previous slice found unkillable are
  killed now. No live case of the `SEAViewModel` one-dispatch lag: every handler
  reading `state` after its own `updateState` was traced.
- **The telemetry event surface**, 2026-09-10, against `6f3bf85`. Eight findings,
  now SD-39 through SD-46. Verdict: the pipeline and its guard test are sound and
  the values riding through it are not. Two existing tests were found unable to
  fail: `GrafanaLogTreeTest.samplingIsStablePerSession` stayed green with the
  sampler returning true for every session, and the rehearsal guards on
  `game.level_started` and `game.commit` could both be replaced with `if (true)`
  without moving any of 296 game tests.

**Still uncovered:** `libraries/scoring` and the difficulty ramp, and the
`libraries/ui` board and dog components.

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
## SD-69 [P1] — The store screenshots show a feature that no longer exists

**Found by:** the SD-65 agent, 2026-09-10, while sweeping the listing.

All eight frames in `docs/store/screenshots/android-phone/` predate the current
build, and two are wrong in ways a reviewer would see:

- **`02-good-dog.png` has a SHARE button on the win sheet.** Sharing was removed
  entirely. A submitted screenshot advertising a control that is not in the app
  is the kind of thing a review rejects over. It also shows three paw slots and
  paws now run to five.
- **`07-achievements.png`** reads "2 of 21 earned" over a flat grid. The page is
  now 73 badges on nine labelled shelves.

`04-levels-and-daily.png` shows a Treat chip on level 395, which the current
schedule no longer pays. The listing file now carries a callout saying the set is
stale, which is the holding position, not the fix.

**Done when:** all eight frames match the shipped app, and nothing in them
advertises something that was removed.

**Hints:** This needs an emulator, so it is owner work or work for a session with
a device. It is the same job as the iOS 6.9" frames in `OWNER-TODO.md`, which are
blocked on item 11, so doing both at once is the cheap order. The streak pages,
the win sheet, the board clock and the lose sheet have all changed too, so check
every frame rather than the two named here.
## SD-70 [P2] — `docs/practices/outbox.md` documents code deleted in C0

**Found by:** the SD-66 agent, 2026-09-10.

Its worked example is `PendingProfileEditStore` and `ProfileEditFlusher` in
`:libraries:identity:impl`, and step 3 tells a reader to implement
`UserScopedSyncer` and hang off the `activeAccount` level. All of it went with
accounts in C0.

The doc map row is labelled honestly now, which stops a reader trusting it
blind. The doc itself is still a set of instructions nobody can follow.

**Done when:** it is rewritten against `SyncTriggers`, which is what actually
exists, or deleted.

**Hints:** Decide which by asking whether this app has an outbox at all. If the
only sync surface left is `warmForeground` / `cameOnline` / `isOffline`, then
there is no outbox to document and the honest move is deletion plus a line in
`features.md` about what the app does instead.
## SD-71 [P2] — Comment rot left behind by the sharing removal

**Found by:** the SD-66 agent, 2026-09-10.

`libraries/resources/.../strings.xml` still has comments referencing the share
sheet and the share card, around lines 393 and 674. The strings they described
are gone; the comments explaining them are not.

**Done when:** no comment in the resources describes a feature the app does not
have.

**Hints:** Grep the whole tree for `share` rather than fixing the two lines
named here, since the same pass that left these probably left others. `--` inside
an XML comment fails the resource build with an error naming no file and no line,
so be careful editing them.
## SD-72 [P2] — `docs/practices/testing.md` describes a test suite that does not exist

**Found by:** the SD-67 agent, 2026-09-10, while correcting the parts of it that
named things it was deleting.

It cites `HomeViewModelTest`, a `HomeScenario` harness under
`features/home/impl/commonTest/harness/`, a JWT auth plugin, `IntegrationAuth`,
`HttpProfileApi` and `ProfileRepositoryImpl`. None of them exist.
`features/home/impl` has no `commonTest` directory at all. Most died with the
accounts deletion in C0.

This is the document a new contributor reads to learn how to test in this repo,
which makes it the worst place in the tree to be wrong. Somebody following it
would spend an afternoon looking for a harness that was deleted a year ago.

The parts naming deleted code have been corrected, so it is no longer actively
lying about the things SD-67 touched. The rest has not been read against reality.

**Done when:** every file, class and directory it names exists, and the practices
it describes are the ones the repo actually follows.

**Hints:** There is a lot to add as well as remove. The composition test tier
landed on 2026-09-10 and is documented, but mutation testing is the house rule
this repo actually runs on and the doc barely mentions it. So does the rule about
pushing decisions out of composables into pure functions, which is why several
bugs were catchable at all. Write down what is true now rather than patching what
was.

`DocReferencesResolveTest` will catch a dead file path but not a class name, so
grep for every symbol it names.
## SD-85 [P2] — A second Skip on the tutorial logs a duplicate completion

**Found by:** the SD-82 agent, 2026-09-10, while disproving the claim that the
`skipTutorial` fall-through was unreachable. It is reachable, and this is what
arrives through it.

`TutorialCoachMark` holds its card through the scrim's fade, and `FocusScrim`
keeps rendering content while `progress.value > 0f`, so the Skip button stays
live for a few frames after the first tap has already run `leaveRehearsal`. A
second tap lands with `rehearsing == false` and `skipTutorial` calls
`completeTutorial` unconditionally, so it logs a second `tutorial.completed`
with `last_step = "none"`.

Harmless to the player and not harmless to the number. `tutorial.completed` is
how we would ever know what fraction of players finish onboarding, and a
double-tapped Skip inflates it.

**Done when:** a tutorial reports its completion once however many times Skip is
tapped.

**Hints:** The fall-through is a real code path and should stay; the fix is that
`completeTutorial` is not idempotent and should be. Check whether the same shape
exists on the other terminal paths out of the rehearsal before fixing only this
one. `DashboardQueryContractTest` will hold the event's registry row if you touch
its attributes.

## SD-86 [P2] — `:apps:integration` has failed twice for reasons nobody can reproduce

**Found by:** two separate investigations, 2026-09-10.

`ConfigValuesAreReadTest` failed once and passed on re-run with no change. The
SD-37 agent went looking, disproved the obvious explanation (`FileTreeWalk` does
not throw when a file vanishes underneath it), fixed the wasteful walk anyway,
and reported that **the original failure still has no explanation.**

Then `DocReferencesResolveTest.everyCitedSectionExists` failed once immediately
after a merge and passed on eight consecutive re-runs afterwards. Its `.claude`
exclusion is correct and matches the relative path. No explanation either.

Two unexplained single failures in one tier is a pattern rather than two
coincidences, and this is the tier that holds every source-scanning guard in the
repo. A guard that fails at random gets re-run instead of read, which is how a
guard stops being one.

**Done when:** either the cause is found, or the tier runs enough times under
adversarial conditions to say honestly that it does not flake.

**Hints:** The common shape is a test that reads the working tree at runtime while
something else writes to it. Both failures happened while agent worktrees under
`.claude/worktrees/` were being created or removed. The tests exclude that
directory; the Gradle `inputs.files` declarations in
`apps/integration/build.gradle.kts` may not, and an input snapshot taken while a
tree is half-removed is a different situation from a walk that skips it.

Reproducing it may mean running the tier in a loop while adding and removing a
worktree. That is worth an hour: everything else in this repo trusts these guards.
