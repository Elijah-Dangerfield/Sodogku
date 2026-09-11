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
## SD-35 [P2] — Remote config offers a targeting axis that can never match

**Found by:** the SD-32 agent, 2026-09-10, while clearing account-era leftovers.

The admin console still offers a "uuid (for allow/deny + rollout)" input,
`RuleEditor` seeds `userAllow` from it, and the server deserializes
`ResolveRequest.userId` and then discards it. There are no accounts, so there is
no user id to match on and there never will be under the current design.

So an operator can author a rule, save it, see it listed, and have it silently
never fire. A control that looks like it works and does not is worse than a
missing one, because the operator debugs the feature instead of the console.

**Done when:** the axis is either removed from the console and the request
shape, or it is wired to something that exists (the install id is the obvious
candidate, and is what every other targeting decision already uses).

**Hints:** Prefer removing it. Install-id targeting sounds free and is not: it
would make config resolution depend on an identifier the privacy policy
describes as device-scoped, which is a policy question rather than a code one.
Touches `:apps:admin` and possibly a migration.
## SD-36 [P2] — `docs/store/data-safety.md` answers against code that no longer exists

**Found by:** the SD-32 agent, 2026-09-10.

Items 5, 6 and 8 cite `DELETE_ACCOUNT_LIMIT`, `PLAYER_REPORT_LIMIT` and manifest
comments that were deleted with the account machinery. The file is a dated
derivation record rather than the form itself, so nothing is broken today, but a
store form filled from it would be answering Google about a code state that has
not existed since C0.

**Done when:** the three items are re-derived against the code that exists, or
the file says at the top which date it was true on and that it must be re-derived
before use.

**Hints:** Cheaper than it looks. The owner has already filed the form once, so
this is about the next time rather than this time. Decide first whether the file
is worth keeping at all: if the answer is "re-derive it when asked", a two-line
note beats a stale nine-item table.
## SD-37 [P2] — `ConfigValuesAreReadTest` walks directories Gradle is writing

**Found by:** the SD-32 agent, 2026-09-10, after it failed once and passed on
re-run with no change.

It walks `apps/**` including `build/` directories, and `FileTreeWalk` throws when
a file vanishes underneath it. A test that fails once in twenty for a reason that
has nothing to do with what it asserts is a test people re-run instead of read.

**Done when:** the walk cannot see a generated directory.

**Hints:** `UserFacingCopyStyleTest` had the same class of bug and the fix is
already there to copy: an `onEnter` filter excluding `build`, `.git` and
`.claude`. Check every other source-walking test in `:apps:integration` in the
same pass rather than fixing the one that happened to fail.
## SD-40 [P2] — `placement` is two vocabularies, so the refill cannot be joined to its ad

**Ask:** `game.bones_refilled` emits `"placement" to placement.name`
(`GameViewModel.kt:2009`), which is the enum name: `ContinueLevel`, `BoosterGrant`. Every
other event that carries `placement` emits `placement.configId`: `ads.gate_shown`
(`RealAdGate.kt:122`), `ads.result` (`:157`, `:252`, `:262`), `ads.offline_block` (`:244`)
and the ad stand-in `iap.paywall_shown` (`RealPaywallCoordinator.kt:109`), which is the
snake-case id the `ads.rewardedPlacements` config is keyed on: `continue_level`,
`booster_grant`. Same key, two spellings of the same thing.

In practice: the obvious question after a bad fill day is "of the `continue_level` ads
that were rewarded, how many actually refilled the board", which is `ads.result` joined
to `game.bones_refilled` on `placement`. `placement="continue_level"` returns the ad side
and zero refills, and whoever wrote it concludes the refill path is broken. The registry
makes it worse: `docs/practices/app-events.md` line 148 describes the enum-name values
and says they are "the split the rewarded-placement config gates on", which they are
not; the config uses the other spelling.

Confidence: high, from reading both emit sites. `configId` is `internal` to
`:libraries:ads:impl`, which is presumably why the game reached for `.name`.

**Done when:** `game.bones_refilled.placement` carries the same string as
`ads.result.placement` for the same gate, and a query filtering both on one literal
returns both. The doc row is corrected in the same change.

**Hints:** Put the config id on `AdPlacement` itself (it lives in the api module,
`libraries/ads/src/.../AdGate.kt:22`) so `RealAdGate` and `GameViewModel` read the same
property, and delete the impl-private extension in `AdPlacements.kt:24`. The other
`.name` emits in the surface (`step`, `board`, `outcome` on `daily.reviewed`) are enum
names on both ends and are fine. Filed by the SD-6 telemetry review, 2026-09-10.
## SD-41 [P2] — The sampling test passes whether or not sampling happens

**Ask:** `GrafanaLogTreeTest.samplingIsStablePerSession` sets the rate to 0.5, emits two
events, and asserts the count is `0 || 2`. A tree that samples nothing out passes (2), a
tree that samples everything out passes (0), and only a per-event coin flip that happened
to split could fail it. Verified by mutation: with `isSessionSampledIn` returning `true`
for every session at every rate in (0, 1) (`GrafanaLogTree.kt:154`), all 15 tests in the
class stay green, including `sampleRateZero_dropsExport`, which is satisfied by the
`rate <= 0.0` short-circuit two lines earlier.

In practice: `telemetry.appEventsSampleRate` is the volume dial the registry says will be
turned when the user base grows. Someone tidies the hash (say, `id.hashCode()` becomes
`id.length`, or the coerce moves) and the dial does nothing: 0.1 ships 100% and the
Grafana bill says so a month later, or it ships 0% and every dashboard goes flat. No test
moves either way.

Confidence: high, by mutation.

**Done when:** A test with rate 0.5 and two session ids whose buckets fall on opposite
sides of the rate (compute them in the test from the same formula, or pick them by
search once and pin them) asserts that one session's events export and the other's do
not, and the mutation above goes red.

**Hints:** `GrafanaLogTreeTest.kt:165`. The stability property the existing test wants
is still worth keeping; it just needs the two-id boundary test beside it. Filed by the
SD-6 telemetry review, 2026-09-10.
## SD-42 [P2] — "The practice board emits no `game.*` events" is pinned for one of them

**Ask:** The registry and two long comments in `GameViewModel` say the tutorial board
emits nothing under `game.*`, because its board id is not a level id and its taps are
scripted. The guards are `if (!rehearsal)` before `game.level_started`
(`GameViewModel.kt:724`) and `if (!rehearsing)` before `game.commit` (`:1312`), plus the
`lose()` path. Only the last is tested: `aPlayerWithNoBonesLeftCanStillBeTaught` asserts
no `game.level_failed`. Verified by mutation: replacing both guards with `if (true)` left
all 296 game tests green.

In practice: SD-6's own brief notes `startAttempt` has silently dropped a field three
times. The next time it is rebuilt and the `rehearsal` branch goes, every new install's
first `game.level_started` is the tutorial board, with the rehearsal's id and difficulty
0. `level-drop-off.json` counts it in "Installs playing the campaign" and "Furthest level
reached", "Clear rate by level" gains a level nobody can clear, and `game.commit`'s
`on_marked` series absorbs scripted taps. Every one of those is a plausible-looking
number rather than an empty panel.

Confidence: high, by mutation.

**Done when:** A `recordingEvents` test drives the rehearsal from start to
`tutorial.completed` and asserts `events.all.none { it.first.startsWith("game.") }`, and
the mutation above goes red.

**Hints:** `GameViewModelTest.aPlayerWithNoBonesLeftCanStillBeTaught` shows how to drive
the rehearsal (`vm.driveTo(TutorialStep.…)`); `RecordingEvents.all` already exists for
exactly this assertion. Filed by the SD-6 telemetry review, 2026-09-10.
## SD-43 [P2] — Log redaction runs on one of the three sinks

**Ask:** `redactSecrets` (`LogRedaction.kt:22`) has exactly one caller,
`InMemoryLogTree.log` (`InMemoryLogTree.kt:45`), the buffer attached to feedback. The
same `LogEntry` reaches `SentryLogTree.addBreadcrumb` (message plus every `extra.*`, Info
and above in release) and `GrafanaLogTree.forward` (message body and `exception_message`,
Warn and above, on by default), and neither is redacted. The redaction doc explains the
threat it guards against is a credential interpolated into a message: a DSN, a bearer
header echoed while debugging a request, a signed URL. That line goes to Grafana and
Sentry unchanged and only the attachment is clean.

In practice: someone debugging the OTLP exporter logs the request at Warn with its
headers. The feedback attachment shows `Basic <redacted>`; Loki, on a shared Grafana
Cloud stack, holds the write token in plain text under the app's own service name, and
the Sentry breadcrumb trail holds it on every subsequent error. Nothing in the app
notices.

The privacy policy is not wrong today: it claims redaction only for the feedback
attachment, and no current line leaks. This is about where the guard sits.

Confidence: high on the fact (grep shows one caller); medium on urgency, since there is
no known leaking line now.

**Done when:** Redaction is applied once, before fan-out, so every tree sees the scrubbed
message and throwable message (and string extras), with one test per sink asserting a
bearer token in a Warn line does not reach it. Or, if per-tree is preferred,
`SentryLogTree` and `GrafanaLogTree` each call it and each has the test.

**Hints:** `LoggingEngine.submit` in `KLog.kt:184` is the single point every entry passes
through; `LogEntry` is a data class so a scrubbed copy is cheap. `redactSecrets` is
`internal` to `:libraries:core`, which the engine is in, so no visibility change is
needed for that placement. Filed by the SD-6 telemetry review, 2026-09-10.
## SD-44 [P2] — The event registry and the dashboard prose have drifted from the code

**Ask:** `docs/practices/app-events.md` calls itself the source of truth for names and
attributes and says "when this page and the code disagree, the code wins, and this page
is what gets fixed". Today it disagrees in these places:

- Emitted and not registered: `game.drag` (`GameViewModel.kt:1277`), `game.hint_applied`
  (`:2362`), `ads.stand_in` (`RealAdGate.kt:215`).
- `iap.paywall_shown` also carries `placement` and `reason` when the trigger is the ad
  stand-in (`RealPaywallCoordinator.kt:106`); the table lists `trigger` alone and its
  trigger vocabulary does not include that trigger.
- `purchase.failed` (line 211) is in the monetization table and nothing emits it. The
  only occurrence of the name is a fixture in `GrafanaLogTreeTest`.
- "What the dashboards ask for and cannot have" (line 239) says `trigger` is missing from
  `iap.purchase_result`. It is emitted (`RealEntitlements.kt:119`) and plumbed from
  `PaywallRoute` through `PaywallViewModel`.
- A second tutorial table (line 178) says `tutorial.step_viewed` carries `level_id`,
  contradicting the R3 paragraph at line 102 that says it was removed.
- The `ads.gate_shown` paragraph (line 272) says the event shadows the per-record
  `is_offline` and `EventAttributeShadowingTest` pins the ordering. The event emits
  `device_offline` (`RealAdGate.kt:130`), so no production event shadows anything; the
  test still exercises the tree's ordering, which is harmless, but its docblock and this
  paragraph describe a situation that no longer exists.
- `onboarding.abandoned` (line 89): "a process death on the welcome screen looks like
  this too". `onCleared` does not run on process death, so it does not.
- `difficulty-calibration.json`: the board description and the "Median strikes used"
  panel description both say `game.level_failed` carries no `difficulty`. It does
  (`GameViewModel.kt:1813`), and the doc table at line 141 already says so.
- `game.booster_no_op` only fires on the sniff path; the treat no-op (`GameViewModel.kt:2372`)
  emits nothing, so the "Hint engine running dry" panel's `by (booster)` can only ever
  show `sniff`. The doc says "one site", which is accurate, but the panel and the table
  row both read as though both boosters report.

In practice: the next panel gets written from the registry, as the registry says it
should be, and queries `purchase.failed` or splits `iap.purchase_result` without
`trigger` because the page said it was missing. The contract test catches the first;
nothing catches the second, and the person reading the blended conversion number does
not know it could have been split.

Confidence: high; each line was checked against the file and line named.

**Done when:** Every emitted event has a row, no row names an event nothing emits, the
duplicate tutorial table and the stale shadowing paragraph are gone, the "cannot have"
table is empty or true, and the two dashboard descriptions match the emit site.

**Hints:** The contract test can hold the doc too: extend `scanLogEventCalls()`'s result
against a parse of the registry's tables so an emitted event with no row fails, which is
the same shape as the existing dashboard check and cheaper than remembering. Filed by
the SD-6 telemetry review, 2026-09-10.
## SD-45 [P2] — `ads.result.latency_ms` is the only wall-clock duration in the surface

**Ask:** `RealAdGate` measures ad latency as `now() - started` where `now()` is
`clock.now().toEpochMilliseconds()` (`RealAdGate.kt:152`, `:159`, `:309`). Every other
duration on an event uses a monotonic source: `session_duration_sec`
(`TimeSource.Monotonic`), `duration_ms` on the level events (a `TimeMark`),
`startup_ms` (the process clock), `duration_sec` on onboarding (`elapsedNow()`).

In practice: a rewarded ad is a thirty-second window, and phones step their clock on
network time sync, often right after regaining connectivity, which is also when the
first ad after an offline stretch is requested. One step during an ad produces a
negative or hours-long `latency_ms`, the ad-funnel board unwraps it into p50/p90 by
placement, and one such record a day is enough to move the p90 line for that day. The
doc already warns to "read its floor, not its mean"; a monotonic clock removes the reason
for the warning.

Confidence: high on the fact, low on how often it happens.

**Done when:** `latency_ms` is measured with a `TimeMark` from an injected `TimeSource`,
and the `Clock` in `RealAdGate` is used only for the two wall-clock legs of the grace
windows, which do need calendar time.

**Hints:** `LifecycleAppEventLogger` shows the injected `TimeSource` pattern with a
`@Inject constructor() : this(TimeSource.Monotonic)` secondary. Filed by the SD-6
telemetry review, 2026-09-10.
## SD-46 [P2] — Two PII seams on `Telemetry` that nothing uses and the policy promises never will

**Ask:** `Telemetry.setUser(email, name, id)` (`Telemetry.kt:6`, implemented at
`AppTelemetry.kt:126` as `Sentry.setUser(...)`) has no caller anywhere in the tree.
`Telemetry.captureUserFeedback` takes an `email` parameter and copies it onto the Sentry
`UserFeedback` (`AppTelemetry.kt:251`); `FeedbackRepositoryImpl` never passes one, so it
is always null, and the `has_email` extra it logs is always false. The privacy policy
says "the app never gives it a name, an email address or a user id" and "the form has no
email field".

In practice: both are one-line calls that compile, look like the natural thing to do
when someone adds a contact field to the feedback form, and turn a published policy
statement false with no test or review gate in the way. `setUser` in particular is a
leftover from the template's account era, which `AGENTS.md` says was deleted in C0.

Confidence: high; verified by grep.

**Done when:** `setUser` is gone from `Telemetry` and `AppTelemetry`, `email` and
`has_email` are gone from `captureUserFeedback`, and the three callers
(`FeedbackRepositoryImpl`, `BugReportViewModel`, `DevFeedbackViewModel`) still compile. If a contact field is ever wanted, it goes in the message body, which is what
the policy already describes.

**Hints:** `Telemetry.kt`, `AppTelemetry.kt`, `FeedbackRepository.kt`. No Swift caller:
`grep setUser apps/ios` is empty. Filed by the SD-6 telemetry review, 2026-09-10.
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
## SD-49 [P1] — Losing a board dead-ends, and giving up on the daily dead-ends harder

**Ask:** Owner, 2026-09-10, twice. On the fail dialog: *"I'm also not really sure
that we should have levels as an option on this dialogue. It seems kind of stupid.
Maybe similar to the success page that we have we should have a failure page.
Maybe the user should be able to restart from zero on this puzzle."* And on the
daily: *"I gave up on today's board and when I revisited it it just shows me this.
We might need to rethink what giving up means. I don't think the user should be
able to give up. I don't know if there should be a total failure state. I think
you should probably always be able to just start from the beginning."*

Two reports, one shape. The win path got a sheet that reports the run as facts.
The lose path got a dialog with a Levels button on it, and the daily's give-up
path got a screen that says the day is over and offers nothing.

**Done when:** losing a board lands on something with the same weight as the win
sheet, restarting from zero is always available, and giving up on the daily is
either not offered or is not permanent.

**Hints:** `GameOutcomeSheets.kt` holds the win sheet and is the register to
match: facts, not commiseration. The daily's terminal states are
`DailyOutcome`, and "gave up" being permanent is a deliberate old decision, so
reversing it belongs in `decisions.md` with the reason. Check what a restart does
to `daily_result` and to the streak before allowing one: the streak now folds
over `play_day` and counts any finished board, so a daily restart is no longer
the same question it was.

Provenance: Sentry SODOGKU-9 and SODOGKU-5, session `95dd30d1`, 2026-09-10.
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
## SD-52 [P2] — The timer is small and the boosters barely ask

**Ask:** Owner, 2026-09-10: *"let's make the timer text just slightly bigger and
let's make the pulsing of those buttons on the bottom a little bit more
noticeable. Like I wanna see them with the icon shaking in the middle a little
bit more. And I honestly haven't been seeing them that much. We probably need a
better algorithm for deciding when they should pulse."*

Three things, and the third is the real one. The first two are a size and an
amplitude. The third says the prompt is not firing when a player is actually
stuck, which is the only thing it exists for.

**Done when:** the clock is legible at a glance, a pulsing booster reads as asking
to be pressed, and the rule that decides when to pulse has been checked against
what being stuck actually looks like rather than tuned by feel.

**Hints:** `StruggleDetector` owns the decision and is the part worth measuring.
Write down what it currently keys on before changing it, then say what it should
key on. Time on the board without a placement is the obvious signal and it is not
the only one: a run of wrong guesses, or a long pause after a strike, are both
stuck in a way a stopwatch alone misses.

The clock is `elapsedLabel` drawn in `BoardClock`, which now also carries the
time-to-beat caption from SD-17, so a size change has to leave room for both. The
pulse is `Modifier.pulsate`; read `Animatable.value` inside `graphicsLayer` and
not in composition or `AnimatedStateReadInComposition` fails the build.

Provenance: Sentry SODOGKU-6, session `95dd30d1`, 2026-09-10.
## SD-54 [P2] — Two exits from a board still throw the player out of the app

**Found by:** the SD-13 agent, 2026-09-10, after fixing the third one.

`GameAction.Leave` sends `NavigateBack`, and the board **is** the start
destination, so popping it leaves the app. That was the whole bug behind "finishing
the campaign closes the app", now fixed for the campaign ending.

The same mechanism is still wired to the "Levels" button on the **lose** sheet, and
to the same button on the fail dialog. It may be deliberate that losing walks you
out; nobody has said so either way, and the owner has separately called that dialog
"kind of stupid" (SD-49).

**Done when:** every control that says "Levels" opens the level pane, or somebody
writes down why one of them should close the app.

**Hints:** Do this with SD-49 rather than before it, since that item is rebuilding
the lose path anyway and this is one of the buttons on it.
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
