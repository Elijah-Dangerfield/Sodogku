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
## SD-22 [P2] — Write down what the game offers, and delete SPEC

**Ask:** Owner, 2026-09-09: "It seems like it would be nice to have a wiki
markdown about the features we do offer." Confirmed 2026-09-10: *"at this stage
we can delete the spec doc and rebuild a features md."*

`docs/SPEC.md` is a design document that argues with itself across 1,500 lines
and records decisions that were later reversed. It is the only place that reads
like a description of the game, which makes it worse than nothing: a reader
trusts it and is wrong. The game shipped, so the code is the specification now.

**Done when:**

1. `docs/reference/features.md` describes every player-facing feature as it
   actually behaves, with its rules, which of its numbers are remote config, and
   where it lives in code.
2. `docs/SPEC.md` is deleted.
3. Every `SPEC <n>` citation in the codebase points at a section of the new doc
   or is removed. There are around 145 of them, mostly in KDoc, and leaving them
   dangling would trade one wrong map for a hundred broken links.
4. The doc map in `README.md` and any pointer in `AGENTS.md` names the new doc.

**Derive every line from the code, not from SPEC.** Where the two disagree the
code is right, and the disagreement is worth a sentence in the commit message
because it is usually a feature somebody remembers differently than it works.

**Hints:** Candidate sections: the board and auto-mark cascade, bones, sniffs
and treats, score and paws, skip, the campaign ladder and its bands, the daily,
the streak, achievements, leaderboards, Pro, ads, settings, accessibility.

Two things the old SPEC gets wrong that you will trip over. The streak is no
longer folded from the daily, it folds over `play_day`, and any day with a
finished board counts. Sharing was removed entirely and SPEC still describes it.

Where a decision was genuinely reversed rather than merely restated, the
reversal belongs in `decisions.md` before SPEC goes, or the reasoning dies with
the file.
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
## SD-34 [P1] — There is no way to test a composable

**Found by:** the SD-26 investigation, 2026-09-10.

A grep for `runComposeUiTest`, `ComposeUiTest` and `createComposeRule` returns
nothing, and the version catalog has no Compose test artifact. Every test in this
repo is a ViewModel test, a pure-function test or an integration test that reads
source files as text. Nothing has ever asserted against a composition.

**This is what SD-26 is stuck behind.** The suspected fault is an entry left in
`NavController.transitionsInProgress` because a `DisposableEffect` in
`FloatingWindowHost` never disposed, which is a statement about recomposition and
cannot be proved or ruled out from a ViewModel test. The reported sequence is
four navigations long (sheet open, sheet popped, dialog pushed, dialog popped)
and would be about twenty lines to drive with a composition under test.

**Done when:** a test can compose something, act on it, and assert, on at least
the JVM target, and one real test exists that would have caught a bug we shipped.

**Hints:** `org.jetbrains.compose.ui:ui-test-junit4` for the JVM/desktop target
is the cheap way in and covers everything in `libraries/ui` and every entry
point. Do not start by trying to cover the board: the board's gestures are the
hardest thing here and a first attempt at them will produce a flaky test that
teaches everybody to distrust the tier.

Two candidates worth writing first, because both are known-hard and currently
unguarded. The `FloatingWindowHost` transition-completion sequence above. And
`AnimatedStateReadInComposition`, the custom detekt rule, which catches the
static shape of a per-frame read but cannot catch one that is laundered through
a helper.

Weigh this against what it costs. A UI test tier that nobody trusts is worse
than none, so the bar is that it runs in CI, does not flake, and fails for a real
reason. If the first two tests cannot meet that, say so and close this rather
than leaving a tier half built.
## SD-51 [P2] — Nothing tells a player the missing starting dog is deliberate

**Ask:** Owner, 2026-09-10: *"we pretty quickly start giving us those puzzles that
don't have a starting dog... maybe the first time that they see one we should let
them know that there's no starting dog on purpose and that they should be able to
deduce it. Like maybe a little tool tip or something."*

**Half of this shipped.** SD-12 moved the free dog from one absolute level id to a
position inside each grid-size band, so it now comes back at every new board size
instead of ending inside the 5x5 band. The frequency complaint is answered.

**What is left is the sentence.** The first board a player opens with no dog on it
looks like a board that failed to load, and nothing says otherwise.

**Done when:** the first campaign board a player opens without a starting dog says
so once, and never again.

**Hints:** `CoachMark` and `AnchoredCard` are the existing tooltip surface and the
tutorial already drives them. "Once, ever" is a flag in `AppCache`, next to the
other one-time prompts. Say it without saying where a dog goes, the way the sniff
reason copy does. It has to be dismissible and must not fire on the rehearsal
board, which always has a dog.

Provenance: Sentry SODOGKU-7, session `95dd30d1`, 2026-09-10.
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
## SD-60 [P2] — The treat no-op emits nothing, so the panel splitting by booster is half a chart

**Found by:** the SD-44 agent, 2026-09-10, which fixed the words rather than the
gap and said so.

`game.booster_no_op` fires when a sniff has nothing left to show. The treat's
equivalent path emits nothing at all, so a dashboard splitting `by (booster)`
has one bar where it should have two and no way to tell an unused feature from
an unreported one.

The doc and the panel now say that out loud, which was the honest fix for an item
about misleading words. The gap is still a gap.

**Done when:** a treat that would do nothing reports it the same way a sniff does,
or somebody writes down why a treat cannot no-op.

**Hints:** `GameViewModel.kt:2490`. Small. Check first whether a treat genuinely
can no-op: if the answer is that it always has something to give, the fix is a
sentence rather than an emit, and the panel should say one booster on purpose.
## SD-62 [P2] — `FloatingWindowHost` never took androidx's fix for an entry popped before it composed

**Found by:** the SD-48 agent, 2026-09-10, while ruling the floating windows out
of SD-26.

`FloatingWindowHost` is a copy of androidx's `DialogHost` that predates a fix
upstream: androidx runs a `LaunchedEffect` that completes an entry popped before
it ever composed, and ours has no equivalent. Our `navigate` also uses
`pushWithTransition` where upstream uses `push`, which widens the window in which
that can happen.

**This is a leak, not the stall.** `NavControllerImpl` never holds the topmost
entry or the first non-`FloatingWindow` beneath one below STARTED, so an entry
stuck in `transitionsInProgress` cannot freeze the board. It holds a
`NavBackStackEntry` and its `ViewModelStore` alive for the life of the process.

**Done when:** an entry popped before it composed is completed, and a test shows
it.

**Blocked on SD-34.** Proving this needs a composition under test, which this repo
cannot do yet. Do not fix it blind: the last hand-edit to this file's transition
bookkeeping is what put SD-26 on the wrong trail for a day.

**Hints:** Diff `FloatingWindowHost` against the `DialogHost` on the
`navigation-compose` version actually on the classpath rather than against
memory. Decide the `pushWithTransition` question separately; it may be
deliberate, and the git history will say.

## SD-63 [P2] — A badge added to the catalog later arrives already stale

**Found by:** the SD-55 agent, 2026-09-10, while checking its new badge against
the achievements page.

`AchievementEngine.apply` stamps a backfilled badge with the **historical**
`finishedAt` that crossed its threshold, not with the moment it was granted. The
achievements page decides "just earned" by comparing `unlockedAt` against the
`achievementsSeenAt` watermark, so a badge added to the catalog today and
immediately backfilled onto an existing player is stamped with a date from weeks
ago and lands older than the watermark.

The result is a badge that toasts and then is not on the "Just earned" shelf when
the player opens the page the toast sent them to. Pre-existing, exposed rather
than caused by `HalfwayHound`, and it will happen to **every** badge added to the
catalog from now on.

**Done when:** a badge granted for the first time reads as new on the page it
points at, however old the play that earned it.

**Hints:** The historical stamp is not wrong for what it is. "When did you cross
this line" is the true answer and the recap and the log both want it. What the
shelf needs is a different fact, "when did we tell you", so this is likely a
second column rather than a changed one. Check what else reads `unlockedAt`
before moving it.

## SD-64 [P2] — `proposals.md` item 4 argues entirely from a 500-level campaign

**Found by:** the SD-55 agent, 2026-09-10.

Proposal 4 is the reasoning behind the campaign ending, and every number in it is
a number about a 500-level pack. The campaign is a thousand now and the ending
shipped, so the proposal is both stale and superseded by the thing it argued for.

**Done when:** it is deleted, or it says what it decided and points at the commit
that did it.

**Hints:** Deleting is probably right. `decisions.md` is where a decision lives
once it is made, and the ending's commit message already carries the reasoning
and what it rejected. A proposal kept past the proposal is a second, worse copy
of a decision. Check nothing else cross-references item 4 first.
