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
## SD-73 [P1] — A resumed board nudges the player one second after their first cross

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified with a scratch test
against `StruggleDetector`, and by reading the wiring in `startAttempt`.

**Ask:** Opening a board that has time on it (a relaunch onto a saved board, or
coming back to level 7 from the drawer after leaving it half done) must not start
with the Locate button pulsing. The detector's own KDoc names this as the case it
avoids: "comes back to the board they left rather than to a button insisting they
are stuck".

What happens: `startAttempt` calls `struggle.reset()` (GameViewModel.kt:739), which
sets `lastPlacementAt = 0`, and then feeds the detector the attempt clock, which on
a resume starts at the saved `elapsedMs` (`elapsedBeforeResume`, line 748). The
first cross calls `onMarked(300_000)`; on the next tick `droughted` reads
`now - lastPlacementAt = 301_000 >= 40_000` (StruggleDetector.kt:199-208) and the
burst starts. With six crosses the allowance is 20 s, so a board resumed at 0:25
nudges three seconds in. The first pace gap is recorded from zero too
(StruggleDetector.kt:124), so on a resumed board the median is later pulled to the
whole pre-resume span and the drought rule goes quiet for far too long in the other
direction.

Scenario: play level 7 for a minute, tap Levels, open level 3, come back to 7, cross
one square. One second later the Locate button is beating. Same on every relaunch
onto a saved board that had more than forty seconds on it.

**Done when:** a board resumed with `elapsedMs = 300_000` is not nudged on the tick
after its first cross, and its first recorded pace gap is measured from the resume
and not from zero. Both at the detector level and through `GameViewModel` with a
`BoardSnapshot`.

**Hints:** `StruggleDetector.reset()` needs a starting time: `reset(at = resume?.elapsedMs ?: 0L)`
setting `lastPlacementAt` and `lastInputAt` to it, or the ViewModel should seed
those after `reset()`. `touched` should stay false until the first input either
way. `StruggleDetectorTest` has no resume case; the two scratch assertions were
`onMarked(300_000)` then `assertFalse(nudging(301_000))`, and six `onMarked` from
25_000 then `assertFalse(nudging(28_000))`. Both failed against current code.
Confidence: high.
## SD-74 [P2] — Zero bones into the tutorial opens the refill prompt over the first coach mark

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified by reading;
`aPlayerWithNoBonesLeftCanStillBeTaught` (GameViewModelTest:3345) builds exactly
this fixture and never asserts on `boosterPrompt`.

**Ask:** The rehearsal costs no bones, so it should not ask the player to buy any.
`startAttempt` sets `boosterPrompt = if (it.livesRemaining <= 0) Consumable.Bone
else null` (GameViewModel.kt:860) without checking `rehearsal`, and `GameScreen`
composes `BoosterPrompt` after `TutorialCoachMark` (GameScreen.kt:253, 282), so a
player who reaches the tutorial at zero bones sees a "watch an ad for bones" dialog
on top of "This is your starter dog".

Scenario: the test's own comment gives one (Settings, Replay the tutorial, after
declining the ad at zero bones). Another needs no Settings visit: a new install
plays the daily first, loses three bones there, then opens level 1.

**Done when:** a rehearsal opened at zero bones has `boosterPrompt == null` and a
real board opened at zero still has `Consumable.Bone`.

**Hints:** One clause: `if (!rehearsal && it.livesRemaining <= 0)`. Extend
`aPlayerWithNoBonesLeftCanStillBeTaught` with the assertion, and keep
`aBoardOpenedAtZeroMeetsTheOfferRatherThanTheNextWrongGuess` as the other side.
Confidence: high.
## SD-75 [P2] — The daily route ignores the daily kill switch

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified by reading; not
run on a device.

**Ask:** `daily.enabled` gates the card in the drawer, `PlayDaily`
(GameViewModel.kt:2211) and the campaign ending's button, but not the route itself.
`loadDaily` (GameViewModel.kt:636-650) takes `dailyBoard()` and starts an attempt
whether or not `status.enabled` is true. The home-screen shortcut
`sodogku://game?daily=true` (`AppShortcuts.DailyChallengeUrl`, registered as a deep
link in `GameFeatureEntryPoint.kt:38`) lands there directly, so a daily switched
off from the console is still playable, still writes `daily_result`, and still
feeds the streak, for anyone arriving by shortcut or by a stale back stack.

`aDailyThatIsSwitchedOffOpensNothing` (GameViewModelTest:3002) covers `PlayDaily`
only; nothing constructs the ViewModel with `isDaily = true` and a disabled status.

**Done when:** a `GameViewModel(isDaily = true)` whose status has `enabled = false`
does not start an attempt.

**Hints:** Decide what it shows instead. `NavigateBack` is the cheap answer and is
what a missing board already does; a `Recap`-shaped "not today" is kinder if the
shortcut is the way most people reach it. Note the recap path
(`today.result != null`) should probably still open, since reviewing a spent day
is not playing one. Confidence: high on the code path, medium on how often the
shortcut is the entry.
## SD-76 [P2] — `state.isPro` is read once, so a purchase from Settings does not reach the board until relaunch

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified by reading.

**Ask:** `load` snapshots `entitlements.isPro.value` into `GameState.isPro`
(GameViewModel.kt:552) and nothing observes the flow afterwards, even though
`Entitlements.isPro` is a `StateFlow` and the init block already argues, for the
four display settings, that the gear opens a real screen and the player comes
straight back (GameViewModel.kt:391-414). Settings offers `OpenPaywall` when the
player is not Pro.

After a purchase and a back-tap: the drawer still has `canJumpAnywhere = false`,
the booster row still shows Ad badges (`tapPlaysAd`'s first clause reads
`isPro`), and `SkipOffer.free` is only right because `skipOffer` reads the flow
live. The ViewModel itself already grants without an ad (`refill`, `refillBones`,
`goToLevel` all read `entitlements.isPro.value`), so the screen is promising an ad
the code will not show and locking rows the code would open.

**Done when:** flipping `entitlements.isPro` while a board is open updates
`state.isPro` without a relaunch, and a test says so.

**Hints:** Collect `entitlements.isPro` in `init` the way the display settings
are collected, into a `ProChanged` action, and carry it in `startAttempt` as now.
The test fixture already has `ProEntitlements` and `FreeEntitlementsFake`; a
`MutableStateFlow`-backed fake would do. Confidence: high.
## SD-77 [P2] — Bones refilled through the pill are reported under the wrong event

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified by reading the
two paths and `docs/practices/app-events.md`.

**Ask:** There are two ways to trade an ad for bones and they emit different
events. The standing offer under the board and the lose sheet send `RefillBones`,
handled by `refillBones()` (GameViewModel.kt:2052-2096), which emits
`game.bones_refilled` with `placement`. The bones pill in the header sends
`BoosterTapped(Bone)` (GameScreen.kt:171), which always opens the prompt
(GameViewModel.kt:2353); for a bone the prompt's only button is `onWatchAd`
(BoosterPrompt.kt:84-93), which sends `BoosterRefillRequested(Bone)` and lands in
`refill()` (GameViewModel.kt:2378-2407). That path emits
`game.booster_refilled { booster = bone }` with no placement and never
`game.bones_refilled`.

`app-events.md:175` calls `game.bones_refilled` "the one way back from zero" and
describes an `ads.result` join on `placement`; `refillBones`'s own KDoc says "the
one way back from zero, and the reason there is only one". Every refill taken
through the pill is missing from that join.

**Done when:** a bone refill from the pill and one from the standing offer produce
the same event with the same attributes, and `refill()` is no longer reachable
with `Consumable.Bone`.

**Hints:** Route `BoosterRefillRequested(Bone)` to `refillBones()` in
`handleAction`, and make `refill()` a sniff-and-treat function (its `Bone` branch
at line 2397 sets `phase = Playing` on a board that is already playing, which is
another sign it was never the bone path). `aRefillNamesItsPlacementTheWayTheAdEventsDo`
is the test to extend. Confidence: high.
## SD-78 [P2] — Eight fields `startAttempt` carries have no test that fails if they are dropped

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified by mutation:
each field below was replaced with its default in the `GameState(...)` at
GameViewModel.kt:838-926 and the suite stayed at 376 green.

**Ask:** `playStreak` was dropped from this constructor and blanked the flame for
a session; the fix added the field and a test, but the pattern that let it happen
is still open. Fields whose carry nothing guards: `autoMarkVisible` (line 888),
`colorblind`, `haptics`, `reduceAnimations` (885-887), `showAchievements` (889),
`refillTo` (902), `isPro` (904), `explainedBoosters` (884), `records` (905).

`theSettingSurvivesStartingTheBoardOver` (GameViewModelTest:232) is the test that
looks like it covers the first one and cannot: it uses `puristCache()` (crosses
off) and asserts they stay off after a retry, and `GameState.autoMarkVisible`
defaults to `false`, so dropping the field is indistinguishable from "still off".
It catches a reset to `true` (the historical bug) and not the field going missing
(the bug the comment above it describes).

What each drop would look like: `refillTo` at 0 disables the standing bone offer
on every board and puts "Refill to 0" on the lose sheet; `isPro` locks the drawer
for Pro after the first retry; `showAchievements` brings the trophy back for a
player who turned badges off; `explainedBoosters` re-explains a booster after a
retry. `records` is benign (reloaded when the drawer opens) and can stay
uncovered.

**Done when:** a test fails when any field that is not about the board is dropped
from the constructor, including fields that do not exist yet.

**Hints:** One test rather than eight. Build a board with every non-default
setting on (Pro, colorblind, haptics off, reduce animations, badges off, refillTo
from config, a booster explained), retry, and assert the projection of `state`
that excludes board fields is equal before and after. Written as
`state.copy(<board fields reset>)` on both sides, a new `GameState` field is
covered the day it is added, and the author has to decide whether it is a board
field. Flip `theSettingSurvivesStartingTheBoardOver` to an assisted cache as well,
so the assertion is on something the default cannot satisfy. Confidence: high.
## SD-79 [P2] — The empty-board note's "once" is a field write nothing tests

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified by mutation.

**Ask:** `noteEmptyBoard` sets `emptyBoardNoteSeen = true` before writing the flag
(GameViewModel.kt:2667), and the comment explains it is the answer that does not
lag. Delete the line and all 376 tests stay green. Without it the card comes back
on the tick after every dismissal, until the player touches the board, because
`alreadySeen` is read from the field alone.

`theEmptyBoardIsExplainedOnceAndNeverAgain` (GameViewModelTest:1068) checks a
*second ViewModel*, which re-reads the flag from disk in `load`, so it is a test
of the cache write. `anEmptyBoardSaysSoOnceThePlayerHasHadTimeToLookAtIt`
(GameViewModelTest:1046) dismisses the card and stops.

**Done when:** dismissing the note and ticking again on the same board leaves
`warning == null`, and deleting the field write makes that test fail.

**Hints:** Two lines on the end of the first test: `clock += Reading; vm.tick();
assertNull(vm.state.warning)`. Confidence: high.
## SD-80 [P2] — Nothing tests that the last level offers no skip, and without the guard the ending sheet covers a lost board

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified by mutation and
by tracing the path.

**Ask:** `skipOffer` returns null for `level.id >= LevelPacks.lastCampaignLevelId`
(GameViewModel.kt:1991). Remove the line and the suite stays green. It is
load-bearing: with it gone, losing level 1000 after enough attempts offers Skip,
`skipLevel` calls `nextLevel`, `nextLevel` asks `endsTheCampaign` (line 2111) and
sets `campaignComplete = true, phase = Won`, so the campaign ending, with its
"999 of 1000" pill, is drawn over a level that was lost. The four skip tests all
run on an early level.

**Done when:** losing `lastCampaignLevelId` with the skip threshold met produces
`skip == null`, and there is a test for it.

**Hints:** The fixture for `clearingTheLastLevelEndsTheCampaignInsteadOfTheApp`
already opens the last level; lose it instead of clearing it. Confidence: high.
## SD-81 [P2] — Three tests pass against the bug their name describes

**Found by:** the SD-6 GameViewModel slice, 2026-09-11. Verified by mutation.

**Ask:** Each of these stays green when the behaviour it is named for is removed.

- `theStandingAdOfferRefillsBonesWithoutEverReducingThem` (GameViewModelTest:2632)
  refills at three bones with `refillTo` at three, so `topped = refillTo()` passes
  it. The claim needs a holding above the floor. The treat and Pro versions
  (`aRefillToppedUpByAnAdNeverReducesAHolding`, `theProFloorNeverTakesAStashAway`)
  hold five and nine and do catch their mutations.
- `everyRestartCountsAsAnotherAttempt` (GameViewModelTest:2192) counts the
  repository's attempts, which `onAttemptStarted` bumps. The ViewModel's own
  `attemptNumber++` in `restart` (GameViewModel.kt:2321) can be deleted without
  moving it, and that number is `attempt_number` on `game.level_started`,
  `game.level_completed`, `game.level_failed` and `game.level_skipped`.
- `theLastBoneWarningFiresOnceOnTheEdgeIntoOneLife` (GameViewModelTest:2137)
  never checks the "once". Dropping `&& !warnedAboutLastBone`
  (GameViewModel.kt:1540) changes nothing it asserts. Within one ViewModel the
  guard is also unreachable as a difference: the only ways back to two bones
  (`refillBones`, `refill`) reset the flag. It only matters when another board's
  `BonesChanged` echo raises the count, and whether the warning should fire again
  then is a question worth answering in the test rather than leaving the flag as
  decoration.

**Done when:** each of the three mutations above turns its test red, or the guard
in the third is deleted with a comment saying why the cross-board case does not
need it.

**Hints:** For the first, set `bones = 5` on disk or `boosters.refillTo` to 2. For
the second, `RecordingEvents` already exists; assert `attempt_number == 2` on the
`game.level_started` after a retry. Confidence: high.

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
