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

## SD-1 [P2] — Nobody has watched a rewarded ad on iOS

**Ask:** The SDK work is done (`b1d5905`): the Google Mobile Ads package is
linked, the `#if canImport(GoogleMobileAds)` paths compile, `AdUnits` is exported
to Swift, and `xcodebuild` is green. What is unproven is the only thing that
matters to a player, that a rewarded request actually shows Google's test ad and
the reward lands after it.

**Done when:** Someone has watched a test ad on an iOS simulator or device and
seen the bones or sniffs arrive afterwards.

**Hints:** Blocked on `docs/OWNER-TODO.md` item 11: tapping the simulator needs a
`sudo xcode-select` this host cannot run. Dropped from P0 to P2 because the free
rewards are fixed; this is verification debt, not a live defect.

Check the fail-open rule while you are there: pull the network mid-ad and confirm
the reward still lands. Only a deliberate dismissal may withhold it.

**Known gap, filed rather than fixed:** Android wraps its load and consent calls
in `withTimeoutOrNull` so a wedged SDK becomes a free reward instead of a frozen
board. iOS has no equivalent, because racing an `async throws` whose cancellation
is opaque risks a leaked continuation, which fails worse than what it guards.
Worth doing properly once someone can test it.

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

## SD-11 [P1] — A sniff should say what proved it

**Ask:** `HintFinder.ruledOutCells` returns `List<Int>` and throws away the
`Deduction.technique` the engine has already computed. The single most praised
feature in Meowdoku's reviews is that its hint explains why a cat can or cannot
go somewhere instead of just handing over the square. We do the harder half
already and say nothing.

**Done when:** The sniff reveal carries the technique that ruled those squares
out, and the board shows one sentence naming it in a player's words, for example
"only one square in this region is still open in this row".

**Hints:** `libraries/puzzle/.../Difficulty.kt:105` is the function.
`Techniques.kt` already has five named tiers whose doc comments read almost as
the copy. Words live in `:libraries:resources` and map with an exhaustive
`when`, the same rule achievements follow, so a new technique fails the build
until somebody writes its sentence. The strike path already names the rule that
was broken (`b490d40`); this is the same idea on the help path.

## SD-12 [P1] — The starter dog leaves too early

**Ask:** Owner, 2026-09-09: "I feel like we remove the starting dog too soon."
`StarterDogThroughLevel = 25` is a hardcoded constant, so the head start ends
inside the 5x5 band and never comes back for a player's first 7x7, 8x8, 9x9 or
10x10, which are the boards where the auto-mark cascade is most worth watching.

**Done when:** The free dog is granted by position within a band rather than by
one absolute level id, so the first levels of every new grid size open with one
placed, and the number is remote config rather than a constant.

**Hints:** `features/game/impl/.../GameViewModel.kt:2577`. `LevelCurve` knows
the bands. It scores nothing, so widening it cannot inflate an early best, and
that property has to survive the change. Config key belongs under
`progression.*`.

## SD-13 [P1] — 500 levels is not a campaign

**Ask:** Meowdoku reviewers report being at level 1912 and past 1000. Ours ends
at 500, and clearing it walks the player out of the app (`proposals.md` item 4).
Two problems, and the second one is worse than the first.

**Done when:** Finishing the last level lands on something that says so, and the
shipped campaign is at least 1000 levels.

**Hints:** The generator makes 1,230 boards in about 42 seconds, so content is
cheap; verification is what costs. `LevelPacks.PACK_VERSION` exists because
progress is keyed on level id, which makes appending safe and reordering a
silent reassignment of everyone's history. Do not spend the daily pool on this,
for the reason in `proposals.md`. Meowdoku's own reviewers say its boards start
repeating around every 100, so this is a place where we can be better rather
than merely bigger.

## SD-16 [P2] — The QA panel cannot move the day

**Ask:** Owner, 2026-09-09: "How can I test out streaks without actually playing
them?"

**Half of this shipped.** `com.sodogku.qa.QaToolsScreen` exists, is reachable
from Settings and from the shake dialog in debug builds, and can seed past days,
mark today played, reset the streak prompts and wipe the lot. That covers
testing the streak itself, which was the original need.

**What is left is the clock.** Nothing can move the date the daily and streak
code sees, so anything that depends on a *rollover* still needs real calendar
time: watching a run break at midnight, checking the countdown as it runs down,
or seeing the calendar redraw when the day changes.

**Done when:** The panel can set the date the app resolves as today, and undo it.

**Hints:** Both `DailyRepositoryImpl` and `StreakRepositoryImpl` take a
`kotlin.time.Clock` and a `DeviceTimeZone` and derive everything from them, so
the seam already exists and there is exactly one place to override. Two rules to
respect rather than route around: future-dated results are deliberately invisible
to the walk (`playCalendarOn` checks future before played, for exactly this), and
the skip allowance keeps a high-water day on purpose.

Freeze and restore were part of the original ask and are no longer testable
because they are no longer designed; see SD-28.

## SD-17 [P2] — A time to beat on a replay

**Ask:** Owner, 2026-09-09, on the ghost race: "I wouldn't wanna see my previous
placements tho. I'd probably just wanna have a time to beat."

**Done when:** Reopening a cleared level shows the best time for it, the running
clock is measured against it, and the moment the run passes or misses it is
marked. Offline, no identity, no server.

**Hints:** `level_progress.best_time_ms` already exists and is already written.
The whole feature is display plus one comparison, which is why it is the cheapest
competitive thing on this list. Decide what a replay that beats the time does to
`best_score`, which keeps the better of the two today.

## SD-18 [P2] — Golden Race: a periodic pack where one mistake ends the run

**Ask:** Owner's design, 2026-09-09, taking the shape of Meowdoku's Golden Fish
(added late August 2026: one error and the run is over). A pool of 100 to 200
boards compiled every few weeks, not repeated in the campaign, entered from the
side pane with a badge, one mistake ends the run, ranked on how far and how
fast.

**Done when:** A decision is written down first, then built. The mode itself is
small; where the ranking lives is not.

**Decision needed:** Game Center and Play Games can both host a recurring
leaderboard that resets on a schedule, which covers ranking with no accounts and
no server. What they cannot host is the content, and what nothing can host
without a durable player identity is the Duolingo-style bracket the owner also
raised: promotion and relegation need cohorts assigned and remembered somewhere.
So this splits into (a) a local mode plus a platform recurring board, which can
ship now, and (b) a served event with brackets, which is v2 and reopens the
accounts question C0 closed. Pick (a) first and say so in `decisions.md`.

**Hints:** Content delivery is SD-19. One mistake ending the run interacts with
bones, which are one global count across the whole game: a race must not spend
them, or a bad run costs a player the campaign too. Meowdoku's own players are
angry about Golden Fish, and the complaint is that it changed the main loop
underneath them rather than sitting beside it. Ours has to be opt-in.

## SD-19 [P2] — Deliver level packs over the wire

**Ask:** Owner, 2026-09-09: a way to add levels, remove levels, and reorder the
campaign without a release. Today both packs are Kotlin source compiled into the
binary (`CampaignPackData.kt`, `DailyPackData.kt`) and decoded lazily by
`LevelPacks`, so every content change is an app update.

**Done when:** The app can fetch a pack, verify it, and use it in place of the
bundled one, and falls back to the bundled pack when the fetch fails, the device
is offline, or verification does not pass.

**Hints:** This does not break SPEC 3. Generation stays offline on a JVM;
only delivery moves. SPEC 18 lists server-delivered packs as a v1 non-goal, so
this is a deliberate reversal and belongs in `decisions.md`.

Two hazards, both sharp. Progress is keyed on level id, so a pack that removes
or reorders ids silently reassigns a player's completed levels;
`PACK_VERSION` exists for exactly this and there is no migration behind it yet.
And `LevelPackVerificationTest` is the only thing standing between an unsolvable
board and a player, and it runs at build time, so a served pack needs the same
uniqueness check before it is signed, not after it is downloaded.

## SD-20 [P2] — Boards past 10x10, with zoom and pan (spike)

**Ask:** Owner brainstorm, 2026-09-09: "Maybe we could even make larger grid
sizes where you need to zoom in and pan?"

**Done when:** There is a written answer with a recommendation, not a feature.

**Hints:** Three things to price before any of it is built. The 44pt touch
target rule against a 12x12 on a phone, which is what forces the zoom in the
first place. Pan against the single-tap and double-tap gestures the entire game
rests on, and against the coach marks that point at specific squares. And the
generator, which converts 23 of 40 attempts to a unique board at 10x10 and gets
worse from there. Also answer whether size adds difficulty at all: SPEC 1.7 says
it does not, difficulty is deduction depth, and a 12x12 that falls to repeated
last-candidate is a long board rather than a hard one.

## SD-21 [P2] — Lockdown mode, where regions fade and the board reshuffles (spike)

**Ask:** Owner brainstorm, 2026-09-09: lock a colour in by finding its dog, and
if you do not, watch it fade to grey and the remaining tiles shuffle up into a
new valid configuration. "The animation there would need to be sick."

**Done when:** There is a written answer with a recommendation.

**Hints:** The animation is not the hard part. Every board has exactly one
solution, and that is the entire reason a tap can be answered right or wrong
(SPEC 1.1). A reshuffle changes the answer underneath the player, so "wrong"
stops being a fact about the puzzle. The only version that keeps the promise is
a precomputed chain generated offline: board 2 is a valid unique board that
agrees with every dog already locked on board 1. Price that in the generator
before anybody designs the screen, because nothing is generated on device.

The cheap cousin worth costing in the same pass: the fade as pure time pressure,
with no reshuffle at all.

## SD-22 [P2] — Write down what the game actually offers

**Ask:** Owner, 2026-09-09: "It seems like it would be nice to have a wiki
markdown about the features we do offer." SPEC is a design document that argues
with itself across 1,500 lines and records decisions that were later reversed.
There is nowhere to read what is true today.

**Done when:** `docs/reference/features.md` lists every player-facing feature
with its rules, which numbers are remote config, and where it lives in code, and
the doc map in `README.md` points at it.

**Hints:** Candidate sections: the board and auto-mark, bones, sniffs and
treats, score and paws, skip, the campaign ladder and its bands, the daily, the
streak with freeze and restore, achievements, sharing, leaderboards, Pro, ads,
settings, accessibility. Derive every line from the code, not from SPEC. Where
the two disagree the code is right and SPEC gets a correction in the same pass.

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

## SD-26 [P0] — Navigation dies while the board keeps taking taps

**Ask:** Owner, 2026-09-09, on iOS: *"idk whats happening but im clicking all
over and nothing is happening Im marking things, trying to open the side pine,
trying to go to achivements. Its not working."*

**What the logs show.** Not a frozen UI. Marks still register and the view model
still fires events; only navigation stops.

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

**Done when:** Opening the feedback panel, submitting, then triggering the shake
dialog and dismissing it leaves navigation working. And, separately, an
enqueued-but-undrained command cannot sit silently: either the router surfaces a
queue that has not drained within a few seconds, or it stops gating the drain on
STARTED.

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

## SD-27 [P2] — Replace the feedback drag handle with a movable FAB

**Ask:** Owner, 2026-09-09: *"the drag handle to swipe in the feedback is kinda
hard to grab. Mabye instead we have a floating, drag to move, small FAB that
opens it as a full screen. And maybe in QA settings you can toggle to just not
show it if you want."*

**Done when:** A small FAB floats over the app, can be dragged anywhere and
stays put, opens the feedback panel full screen, and can be switched off from QA
settings. The edge-drag handle is gone.

**Hints:** The panel is `apps/compose/.../devfeedback/DevFeedbackPanel.kt`. Three
things worth getting right rather than discovering later: the FAB must not sit
over the booster row or the board (which is the whole screen on a big grid), so
"drag to move" is a requirement and not a nicety; it must not appear in release
builds; and the toggle needs somewhere to persist, alongside whatever QA
settings already use.

Related: whether this FAB should also be the shake dialog's entry point, so
there is one way into feedback rather than three.

## SD-28 [P2] — Decide what a streak freeze is, now that the streak is not the daily's

**Ask:** Owner, 2026-09-09: *"We should have a todo to figure out streak freezes
and how that will work later. Maybe thats another thing users and earn idk."*

**Why this is now open rather than done.** Freezes already exist, but they were
built for the *daily*: `DailyRepository` has `freeze` and `restore`, they are
budgeted per month, and `DailyOutcome.Frozen`/`Restored` are rows in
`daily_result`. The streak no longer reads any of that. It folds over `play_day`,
where a day is either played or not, and `StreakDayState.Bridged` is currently a
state nothing can produce.

So there are two half-systems: a freeze that covers a missed *daily puzzle*, and
a streak that does not care about the daily. Neither is wrong; they are just no
longer the same feature.

**The decision to make first**, before any code:

- **What does a freeze cover?** Missing a day entirely is the only way to break a
  streak now, so a freeze is a day you did not open the app. That is a different
  product from "I opened the daily and lost", which is what the current freeze
  was for.
- **Where does one come from?** The owner's instinct is earning them. Options
  worth weighing: a reward for a run length (7 days pays one), a level reward
  alongside the Treat, an ad, or a Pro perk. Each implies a different cap.
- **Is it spent or automatic?** Duolingo's is bought in advance and spent
  silently on the missed day, which is why it feels like insurance rather than a
  refund. Spending it after the fact turns a broken streak into a shop prompt at
  the worst moment.

**Done when:** A missed day can be covered, the calendar draws it as
`StreakDayState.Bridged` (the state already exists and is already styled), and
`playStreakOn` walks through it without counting it. That last part matters:
`DailyStreak.streakOn` already had this shape, where a bridged day continues the
run without adding to it, and the new fold deliberately does not.

**Hints:** `libraries/progress/impl/.../streak/PlayStreak.kt` is the fold and is
a pure function of a set of dates, so covering a day is a matter of what goes
into that set, or a second set walked alongside it. The daily's own freeze
budgeting in `DailyRepositoryImpl` is worth reading before designing this, and
worth deciding whether it survives: two separate freeze economies would be one
too many.

Not urgent. A streak with no freeze is a working streak, and shipping the wrong
freeze is harder to undo than shipping none.

## SD-29 [P1] — The `install_id` tag is set opportunistically, and the privacy policy now leans on it

**Found by:** the agent that rewrote the legal pages, 2026-09-10.

`pages/privacy.html` now routes deletion requests through the in-app feedback
form, on the grounds that a feedback report reaches Sentry tagged with
`install_id` while an email cannot be matched to anything. That is the answer
Play's "can users request deletion" question is being given.

The tag is not guaranteed. `SessionTelemetryBinder.kt:55` does
`installIdProvider.current()?.let { telemetry.setInstallId(it) }`, and `current()`
is nullable because the id comes from an async `AppCache` read. A report filed in
the first seconds after a cold start can arrive untagged, silently, and there is
nothing in the report to say the tag is missing rather than absent by design.

Rare in practice and load-bearing in policy, which is the combination worth
fixing rather than accepting.

**Done when:** every feedback report carries an `install_id`, or the report
carries an explicit marker saying the id was not available so a triager can tell
the two apart.

**Hints:** Two shapes. Either await the id before capturing feedback (it is one
cache read and the capture is already suspending), or set the tag from
`captureUserFeedback` itself rather than relying on a scope set at boot. The
second is closer to where it is needed and does not make cold boot wait on
anything.

`libraries/sodogku/impl/.../SessionTelemetryBinder.kt`,
`libraries/sodogku/impl/.../AppTelemetry.kt` (`setInstallId`, `captureUserFeedback`).

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

**Hints:** Start from `Versioning.kt:138,150` and work outward. `PROFILE_WRITE_LIMIT`
on the server was left in place deliberately by the SD-30 agent because that
todo named only two rate limits; decide whether it goes too.
