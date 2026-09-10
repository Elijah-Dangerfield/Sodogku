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
## SD-9 [P1] — There is no leaderboard at all on Android

**Ask:** `RealLeaderboards` is bound to `NoGameServices` on Android, which
reports `Unavailable` from construction and never changes its mind. So
`isOfferable` stays false, no entry point is ever drawn, and every score is
collected and never flushed. Meowdoku ships a global board on Android, which is
where most of this genre's installs are.

**Done when:** An Android player can reach the same leaderboard entry point iOS
draws, a `LifetimeScore` submission is accepted, and the platform's own
dashboard opens.

**Hints:** `libraries/leaderboards/impl/src/androidMain/.../NoGameServices.kt`
already names the whole mapping in its doc comment: `GamesSignInClient` answers
`startAuthentication`, `LeaderboardsClient.submitScore(id, value)` answers
`submit`, `getLeaderboardIntent` answers `presentDashboard`. Play mints its own
ids, so `Leaderboard` needs a second id per board and a resolver in the shape of
`AdUnits.android(format)`.

Two things that are not true of Game Center: Play Games sign-in can fail for a
player who has never opted into a Games profile, which is a normal state and not
an error, and the console needs the boards created and published before a
submission is anything but a silent no-op.
## SD-13 [P1] — 500 levels is not a campaign

**Ask:** Meowdoku reviewers report being at level 1912 and past 1000. Ours ends
at 500, and clearing it walks the player out of the app (`proposals.md` item 4).
Two problems, and the second one is worse than the first.

**Done when:** Finishing the last level lands on something that says so, and the
shipped campaign is at least 1000 levels.

**SD-20 is answered and it settles the shape.** See
`docs/reference/large-boards-spike.md`. No new grid size: 4x4 through 10x10 only,
no zoom, no pan. The measurements are decisive rather than close. The touch-target
rule that was supposed to force zoom already broke at 8x8 three hundred levels
ago, a 12x12 costs 22 times more generator time per shipped board, and the game's
own rater says a 12x12 needs 25% more deductions for 44% more squares, which is a
longer board rather than a harder one.

So the surplus goes into **non-repetition**, not size. `Generator.canonicalKey`
already dedups up to the eight symmetries and region renaming, so "our boards do
not repeat" is a claim we can make and Meowdoku's own reviewers say it cannot.
More 9x9 and 10x10 bands cost a couple of minutes of generation, which keeps the
whole pack regenerable in one sitting and `LevelPackVerificationTest` cheap.

**Also worth fixing while you are in here.** The spike found that
`RegionPalette.get`, `BoardCellLabels.describe` and the `board_region_*` /
`board_glyph_*` string sets all wrap silently with `mod` past ten. Harmless while
`MAX_SIZE` is 10, but an eleventh region would draw in region 0's pink and be
announced by region 0's name instead of failing loudly.

**Hints:** The generator makes 1,230 boards in about 42 seconds, so content is
cheap; verification is what costs. `LevelPacks.PACK_VERSION` exists because
progress is keyed on level id, which makes appending safe and reordering a
silent reassignment of everyone's history. Do not spend the daily pool on this,
for the reason in `proposals.md`. Meowdoku's own reviewers say its boards start
repeating around every 100, so this is a place where we can be better rather
than merely bigger.
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
## SD-25 [P2] — Custom quick actions on the iOS home-screen long press

**Ask:** Owner, 2026-09-09: *"on iOS how can I edit the options shown on the hold
to delete? Maybe like a 'bugs? Contact us' or a 'Stay, we value you!'"*

The long-press menu on the app icon. iOS builds it from two halves and we only
control one: **Delete App, Share App, Edit Home Screen and Require Face ID are
system entries and cannot be removed, reordered or renamed.** Everything above
them is ours, via `UIApplicationShortcutItems` in `Info.plist` (static) or
`UIApplication.shared.shortcutItems` (dynamic, so the list can react to state).

So a literal "Stay, we value you!" cannot be attached to the Delete row, and
nothing can intercept a delete. What is available is putting a route or two above
it, which is where a "Report a bug" belongs anyway.

Worth deciding what earns a slot before building it. Three or four is the visible
maximum and the menu is a place people go to delete the app, not to browse, so a
list that reads as marketing is worse than no list. The two that survive that test
are probably *Report a bug* (straight into the feedback panel, which is the one
thing a frustrated player wants and currently has to hunt for in Settings) and
*Daily puzzle* (straight into today's board).

**Done when:** Long-pressing the icon on a device shows our entries above the
system ones, and each opens the app on the right screen from cold start as well as
from background.

**Hints:** `apps/ios/iosApp/Info.plist` for the static list; each item needs
`UIApplicationShortcutItemType`, `...Title` and `...IconType`/`...IconFile`.
Handling is `application(_:performActionFor:completionHandler:)` on cold start and
`windowScene(_:performActionFor:)` when already running — both have to work, and
the cold-start path is the one that gets missed, because the shortcut arrives
before Compose has a router. Route through the same deep-link entry the app
already has rather than inventing a second way in. Android's equivalent is
`android.app.shortcuts` in the manifest, so this is worth doing on both or
neither.

**Not blocked on anything.** No store review implication, no new permission.
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
