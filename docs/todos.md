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

**Do SD-20 first.** Whether the campaign grows past 10x10 changes what
"1000 levels" is made of, and generating a thousand boards to one shape and then
deciding the shape was wrong is the expensive order to do this in.

**Hints:** The generator makes 1,230 boards in about 42 seconds, so content is
cheap; verification is what costs. `LevelPacks.PACK_VERSION` exists because
progress is keyed on level id, which makes appending safe and reordering a
silent reassignment of everyone's history. Do not spend the daily pool on this,
for the reason in `proposals.md`. Meowdoku's own reviewers say its boards start
repeating around every 100, so this is a place where we can be better rather
than merely bigger.
## SD-20 [P2] — Boards past 10x10, with zoom and pan (spike)

**Ask:** Owner brainstorm, 2026-09-09: "Maybe we could even make larger grid
sizes where you need to zoom in and pan?"

**Blocks SD-13.** Extending the campaign to 1000 levels means deciding what
those levels look like, so this answer comes first.

**Done when:** There is a written answer with a recommendation, not a feature.

**Hints:** Three things to price before any of it is built. The 44pt touch
target rule against a 12x12 on a phone, which is what forces the zoom in the
first place. Pan against the single-tap and double-tap gestures the entire game
rests on, and against the coach marks that point at specific squares. And the
generator, which converts 23 of 40 attempts to a unique board at 10x10 and gets
worse from there. Also answer whether size adds difficulty at all: SPEC 1.7 says
it does not, difficulty is deduction depth, and a 12x12 that falls to repeated
last-candidate is a long board rather than a hard one.
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
## SD-23 [P2] — A weekly score board, so a newcomer can win something

**Ask:** `Leaderboard.kt` names this itself: an all-time score board is
unwinnable for anyone who installed today, and the standard answer is a rolling
window everyone starts level in. Game Center supports recurring boards natively.

**Done when:** A weekly board exists and is submitted to, and it resets without
anything on the device having to know it did.

**Hints:** The blocker is upstream and small: `:libraries:progress` folds a
lifetime total and there is no "points banked since a date". That addition
first, then one entry in the `Leaderboard` enum. Do not use a recurring board
for the daily challenge, for the local-midnight reason already written down
there.
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
## SD-32 [P1] — The app still ships a Supabase anon key, and a pile of account machinery nothing calls

**Found by:** the SD-30 agent, 2026-09-10, while clearing account-era leftovers.

Accounts were deleted in C0. The machinery was not, and some of it is still
compiled into the shipped binary.

**The one that is not merely untidy:** `libraries/core/SupabaseInfo.kt` exposes
`SUPABASE_PROJECT_ID`, `SUPABASE_URL` and `SUPABASE_ANON_KEY`, wired through
`build-logic/.../Versioning.kt:150` into `BuildConfig`. **Nothing reads it**, and
a grep for `SupabaseInfo.` returns no call sites at all. So every release build
carries a Supabase anon key for a project the app never contacts. Not a
vulnerability on its own, an anon key is meant to be public, but shipping a
credential for a service you do not use is the kind of thing a security review
asks about and nobody can answer.

The rest, all confirmed present and uncalled:

- **The server still has a Supabase auth surface.** `ServerConfig.kt:162-188`
  parses `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`, and KDocs in
  `Application.kt` and `ServerConfig.kt` describe "the authenticated `/v1/me`
  route". There is no `MeRoutes` and no auth plugin.
- `libraries/core/AuthGate.kt`, a whole guest/claimed/anonymous vocabulary,
  referenced in 13 places.
- `libraries/networking/`: `SessionRejectionBus` (AGENTS.md already notes nothing
  can trigger it), `AuthTokenInvalidator`, `AuthTokenProvider`.
- `NativeViewFactory.createAppleSignInButton` and `AppleSignInButtonHost` in
  `IOSNativeViewFactory.swift`, fully implemented, no caller.
- `libraries/navigation/impl/build.gradle.kts:18-22`, comments about minting
  guest sessions and a `SignInRoute`.
- `config/detekt/baseline.xml:42,45`, baselined onboarding strings "Continue as
  guest" and "sign in to pick up where you left off".

**Done when:** no Supabase credential is compiled into a release build, and a
grep for `AuthGate`, `SignIn`, `guest` or `Supabase` in `libraries/` and
`apps/compose/` returns nothing live.

**Do the key first and separately.** It is the only part with a consequence, and
it should not wait behind a large deletion.

**This app was generated from `Workspace/KMPTemplate`, so the key is probably
still there too.** Whatever the fix is here, it belongs in the template as well,
or the next app generated from it ships the same credential. The template keeps
`docs/PORT-CANDIDATES.md` for exactly this.

**Hints:** Start from `Versioning.kt:138,150` and work outward. `PROFILE_WRITE_LIMIT`
on the server was left in place deliberately by the SD-30 agent because that
todo named only two rate limits; decide whether it goes too.
