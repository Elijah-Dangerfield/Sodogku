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
trying to go to achivements. Its not working."*

**UPDATE 2026-09-10, and it moves the whole diagnosis.** Owner, asked again:
*"it wasnt just about navigation. I couldnt draw X's or do anything. It didnt
work again until the shake dialog popped back up."*

So the earlier reading below is wrong where it says marks still register. Marks
reached the view model and never reached the screen. Nothing on the board moved
either.

**That is one symptom, not two.** Taps are still delivered, because the view is
still in the hierarchy and its pointer handlers still exist, which is why
`Sending event OpenAchievements` keeps logging. What stopped is everything
downstream of state: the board does not redraw, `repeatOnLifecycle(STARTED)`
collectors suspend, and the router's queue fills without draining. A paused
recomposer plus a lifecycle below STARTED produces exactly this and nothing else
does. The `STARTED` gate is not the bug, it is the one part of the wreck that
left a log line.

Which points at the host rather than at any of our code. On iOS,
`ComposeUIViewController` drives both the frame clock and the lifecycle owner
from the controller's appearance callbacks, so a controller that believes it
disappeared and never hears that it reappeared pauses recomposition and drops
the lifecycle, while its view keeps taking touches. The feedback panel had a
keyboard up moments before, and `ShakeDialogRoute` arrived while that was
tearing down. **Presenting the shake dialog again is what un-stuck it**, which is
what a controller re-entering the appeared state would do, and is hard to explain
any other way.

**UPDATE 2026-09-10, third pass. There is a reproduction now, see SD-48.** A
separate report carries the transaction `GADFullScreenAdViewController`, so the
same dead-controls symptom happened after a rewarded ad. That is the only native
modal this app presents, and unlike the shake dialog it can be triggered on
demand. Do SD-48 before spending any more time on the theory below.

What the ad has in common with the original report is not the dialog, it is that
something took over the screen and the app did not fully come back. A keyboard in
its own window, a full-screen ad view controller, a floating-window destination.
One bug with three ways in is a better reading of the evidence than three bugs.

**Start here:** what the feedback panel and the shake dialog do to the hosting
`UIViewController`, whether either presents over the Compose host, and whether
an appearance transition can be interrupted by the keyboard dismissing under it.
Confirm the frame clock is stopped rather than assumed: an on-screen frame
counter, or the recomposition logging that already exists, will tell the
difference between a paused recomposer and a lifecycle-only stall in one look.

**What the logs showed at the time.** Only navigation looked broken, because
only navigation logs.

```
17:11:08.182  Sending event OpenAchievements
17:11:08.186  (DelegatingRouter) Enqueuing navigation: navigate to AchievementsRoute
17:11:09.440  Sending event OpenAchievements     <- and eleven more like it
                                                    with no Enqueuing line at all
```

The first tap reached the router. The next eleven produced the event and no
navigation, and the one that *was* enqueued never executed.

**The mechanism, which is the useful part.** Two separate things gate on
`Lifecycle.State.STARTED`, and they are the two things in this trace that
stopped:

- `GameFeatureEntryPoint` collects events through `ObserveEvents`, which is
  `repeatOnLifecycle(STARTED)`. Below STARTED it stops collecting, so
  `router.navigate` is never called and nothing is even enqueued.
- `DelegatingRouter.setNavController` drains its channel through
  `observeWithLifecycle(lifecycle)`, also STARTED. Below STARTED, `trySend`
  still succeeds into an UNLIMITED channel and the command sits there. That is
  why the queue can log an enqueue for work that never runs.

`sendEvent` has no lifecycle gate, which is why the events keep logging and the
app looks alive.

So the question is not "why did navigation break" but **what is holding the
lifecycle below STARTED while the Compose UI is still drawing and handling
touches.**

**Prime suspect: the shake dialog.** 30 seconds earlier:

```
17:10:15  Feedback forwarded to Sentry (owner_directive)
17:10:18  Enqueuing navigation: navigate to ShakeDialogRoute
17:10:34  Enqueuing navigation: go back
```

The shake at :18 was almost certainly spurious (the owner had just put the phone
down after submitting feedback; the recognizer was retuned for that in a later
commit, which reduces the trigger but does not fix this). Note the feedback
panel had a keyboard up immediately before, and `ShakeDialogRoute` arrived while
that was tearing down.

**UPDATE 2026-09-09, from the crash log.** The stampede half is fixed and the
stall half is not. Shaking the device during the stall restarted collection, and
twelve banked `OpenAchievements` events -- the eleven taps from 17:11:08-17:11:16
plus one at 17:17:45 -- drained 1.5ms apart and crashed NavController with
`Attempted to pop Destination route=AchievementsRoute, which is not the top of
the back stack`. Events now expire after five seconds, so a stall can no longer
end in that crash. **The stall itself is still unexplained and is what this item
is now only about.**

Also added since: lifecycle-gated collection logs when it starts and stops,
tagged. Reproducing this should now produce `Collection stopped for
GameViewModel events` and `Collection stopped for navigation queue`, which is
the evidence that was missing. Owner reports being fully on the game screen at
the time, so a phantom window from the bug-report dialog is the standing
suspicion.

**UPDATE 2026-09-10, second pass, and it narrows the search a long way.** The
guess above about `ComposeUIViewController` pausing is probably wrong, and the
code says why.

A feature's screen state and its events are both collected against the
**`NavBackStackEntry`** lifecycle, not the host's. `GameFeatureEntryPoint` sits
inside `screen<GameRoute> { }`, so its `collectAsStateWithLifecycle()` and its
`ObserveEvents` read a `LocalLifecycleOwner` that is the entry. An entry pinned
below STARTED gives exactly the reported symptom and nothing else does: the board
holds its last state and never redraws, its events are never delivered, and
touches, logging and the shake detector all keep working, because those hang off
the host.

The shake detector is the evidence for this. `ShakeHandler.start`/`stop` are
driven by a `LifecycleStartEffect` in `App.kt` on the **host** lifecycle. If the
host had dropped below STARTED, the detector would have been stopped and the
shake could not have been noticed at all. It was noticed, so the host was fine
and something below it was not.

Which points at `libraries/navigation/.../floatingwindow/`, our own
`FloatingWindowNavigator` and `FloatingWindowHost`, because that is what hosts
both the feedback sheet and the shake dialog and it is what completes a
transition. An entry left in `transitionsInProgress` is held below its target
state by `NavController`, and nothing ever completes it again. Note
`FloatingWindowHost` only calls `onTransitionComplete` from a `DisposableEffect`
in `visibleBackStack.forEach`, so an entry that leaves the visible list without
disposing, or one that never enters it, is never completed.

**Done when:** Opening the feedback panel, submitting, then triggering the shake
dialog and dismissing it leaves the board and navigation working.

**The reporting half is done** (`NavigationQueueWatchdog`, 2026-09-10). A queue
that has not moved for four seconds now logs an error carrying the host
lifecycle state and every back stack entry with its own state, which is the
reading nobody has ever taken. Reproduce it once and the log says which entry is
pinned and at what state, which is the difference between looking at the host and
looking at `FloatingWindowHost`.

**Hints:** `libraries/navigation/impl/.../DelegatingRouter.kt` (`setNavController`,
`clearNavController`, `enqueueNavigation`),
`libraries/flowroutines/.../Compose.kt` (`observeWithLifecycle`). Worth checking
whether `clearNavController` ran without a matching `setNavController` -- it
cancels `processingJob` and replaces `viewScope` with a fresh
`CompletableDeferred`, and nothing ever completes that again until a new
controller is bound. On iOS, check what the feedback panel and the shake dialog
do to the hosting `UIViewController` and therefore to the lifecycle owner.

Reproduce with the log lines above rather than by guessing: `Enqueuing
navigation` with no visible result is the signature.
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
## SD-48 [P1] — A rewarded ad is a way to reproduce SD-26

**Ask:** Owner, 2026-09-10, on `GameRoute`: *"both the levels button and the start
over button are not doing anything right now."*

**The tag on that report is the whole point.** Sentry recorded the transaction as
`GADFullScreenAdViewController`, so a rewarded ad had been presented over the app
when the buttons went dead. A minute earlier the same session reported *"I just
clicked on Level levels, but it did nothing."*

SD-26 is the same symptom from a different session, where the trigger looked like
the feedback panel and the shake dialog instead. What those have in common is
that something took over the screen and the app did not fully come back:
a `GADFullScreenAdViewController` presented over the Compose host, a keyboard in
its own window, a floating-window destination. That is one bug with three ways
in, and the ad is the one an owner can trigger on demand.

**Do this before anything else on SD-26.** It has been open as a P0 with no
reproduction, and a reproduction is worth more than another theory.

**Done when:** watching a rewarded ad to completion and returning to the board
leaves every control working, and there is a test or a log line that would have
caught it.

**Hints:** `apps/ios/iosApp/Platform/AdNetwork.swift:106` finds the root view
controller through `connectedScenes.keyWindow.rootViewController` and presents
from it. Check what that does to the Compose host's appearance callbacks and
therefore to `LocalLifecycleOwner`, and check whether the host reliably returns
to STARTED after the ad is dismissed. Note the ad is the only native modal this
app presents, which is why it is the cleanest of the three ways in.

The navigation queue watchdog added on 2026-09-10 will log an error naming the
host lifecycle state and every back stack entry's state while this is happening.
Reproduce it with logs attached and the answer is in the report.

Provenance: Sentry SODOGKU-A and SODOGKU-9, session `95dd30d1`, 2026-09-10.
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
## SD-55 [P2] — `TopDog` still unlocks at the halfway point

**Found by:** the SD-13 agent, 2026-09-10.

The achievement fired at 500 cleared levels, which used to mean "you finished the
campaign". The campaign is now a thousand, so it means "you are halfway".

**Not simply raised to 1000, and the reason is the interesting part.** The
achievement log recomputes from counters, so moving the threshold would
*un-unlock* it for anybody already holding it. An achievement that disappears is
worse than one that arrives early.

**Done when:** finishing the campaign unlocks something that says so, and nobody
loses an achievement they already have.

**Hints:** A second achievement at 1000 leaves the existing one alone and is the
cheap answer, but then `TopDog` is an award for being halfway and its name says
otherwise. Renaming what a player already earned is its own small betrayal.
Decide which of those two you would rather explain. `AchievementCounters` and
`Stat` are where it lives.
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
## SD-58 [P2] — The ad funnel filters on an outcome nothing emits

**Found by:** the SD-39 agent, 2026-09-10, while confirming that enum `.name`
survives R8.

`ad-funnel.json` filters `outcome=~"Rewarded|Completed"`, and `AdShowResult` has
no `Completed` value. No interstitial path emits one either.

Milder than SD-39, because the panel is not empty: `Rewarded` matches and the
chart draws. But it counts less than its title claims, and nobody reading the
number would know. `docs/practices/app-events.md` repeats the error, so the two
places somebody would check agree with each other and disagree with the code.

**Done when:** the filter names only values `AdShowResult` can produce, or
`Completed` exists and something emits it.

**Hints:** Decide which before editing either. If interstitials were meant to
report a completion distinct from a reward, that is a missing emit rather than a
stale filter, and deleting the alternation would quietly close the question.
`DashboardQueryContractTest` checks that attribute *names* agree across the two
sides and does not check that filtered *values* are producible, which is the hole
this fell through. SD-39 added a value check for the `iap.*` events; widening it
to every event with a closed value set is the guard.
## SD-59 [P2] — `Modifier.pulsate` is dead and does not respect reduce-animations

**Found by:** the SD-52 agent, 2026-09-10, while looking for the booster pulse.

`libraries/ui/.../system/Pulsate.kt` has zero call sites. The booster nudge is
`BoardControl(attention = ...)`, which drives its own `Animatable`. So the brief
for SD-52 pointed at the wrong thing, and so would the next person's.

It also ignores `LocalReduceAnimations`, which `BoardControl` respects. That is
harmless while nothing calls it and is a trap the moment somebody does, because
it looks like the obvious tool for the job.

**Done when:** it is deleted, or it respects reduce-animations and something uses
it.

**Hints:** Deleting is the answer unless somebody wants it. It is named in
`docs/SPEC.md`, which SD-22 is deleting anyway, so do this after that or take
the reference out in the same pass.
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

## SD-61 [P1] — `:apps:server:test` is red on main and CI cannot see it

**Found by:** two agents independently on 2026-09-10, each of which stashed its
own work to confirm the failure predates it.

`DatabaseSchemaTest.migrationsCreateAppConfigTable` asserts `app_config_values`
is empty after migrations. `V4__app_config.sql:25` seeds three kill-switch rows
into it. So the test fails deterministically on any machine with Docker running.

**The reason nobody noticed is the worse half.** The test is skipped via JUnit
`Assume` when Docker is absent, and CI has no Docker, so it has never run there.
A test that is red everywhere it executes and skipped everywhere it is watched
is not a test, and it has been in that state since C0.

**Done when:** the assertion matches what the migrations actually do, and either
CI runs this suite with Docker or the suite says out loud that it did not run.

**Hints:** Decide which side is wrong before editing either. If `app_config_values`
is meant to ship seeded, the assertion is stale and the seed is the contract. If
it is meant to be empty, `V4` is seeding production data in a migration, which is
a different and larger problem.

The skip-when-absent behavior is worth keeping, but a green build that silently
skipped its only schema test is a lie either way. A count of skipped tests in the
CI summary is the cheap version.
