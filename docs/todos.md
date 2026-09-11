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

- **`libraries/scoring` and the difficulty ramp**, 2026-09-10. Five findings, now
  SD-88 through SD-92. 35 mutations, 21 killed, every survivor explained.
  Verdict: the formula and its two main guards are sound, and the fourth
  instance of the compression bug is real and is in `Standing.Sharp`. Three more
  tests found unable to fail for the reason they claim, two of them by reaching
  their target at one millisecond a move.
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

**Still uncovered:** the `libraries/ui` board and dog components.

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
## SD-87 [P2] — `onboarding.completed` has the same non-idempotent shape SD-85 fixed

**Found by:** the SD-85 agent, 2026-09-10, after fixing the tutorial's version and
checking whether the shape existed elsewhere.

`OnboardingViewModel.finish` logs `onboarding.completed` unconditionally, and
`exitedToHome` is only read in `onCleared`. The buttons are gated on
`state.isFinishing`, which makes this much harder to reach than the tutorial's
was, but `updateState` lags the state flow by one dispatch, so two taps inside a
single frame can both land in the channel before the disabled state renders.

Same consequence as SD-85: `onboarding.completed` is how we would know what
fraction of installs get through onboarding, and a double tap inflates it.

**Done when:** onboarding reports its completion once however many times the
button is tapped.

**Hints:** The fix SD-85 took is the shape to copy, and the reason it went where
it did is the lesson: a guard on the button would have left the other door open.
Put it on the thing that logs.

A `SEAViewModel` test can drive two actions through the channel without a UI
harness, which is how SD-85's test reached its second tap. Check the other
one-shot completion events in the same pass rather than fixing the third one
later: `tutorial.completed` and `onboarding.completed` were both written the same
way, so a third probably was too.
## SD-88 [P1] — `Standing.Sharp` is a verdict no human pace can earn, the fourth compression bug

**Found by:** the SD-6 scoring review, 2026-09-11.

**Ask:** `Standing.kt` line 87 defines `sharp` as `score >= par *
fivePawFraction`, and `Sharp` is that with a bone lost. `ScoringConfig`'s own
KDoc says a clean run is *necessary* for five paws. Those two sentences define
`Sharp` as empty, and measurement agrees. Driving the real API with one strike
taken on the second placement, which is the cheapest place to take it, the
slowest pace that still rates `Sharp` is: 10x10 tier 4, 2.3 seconds a move;
8x8 tier 4, 1.5 seconds; 4x4 tier 4, 0.4 seconds; and **never** on the
starter-dog 4x4, 5x5 and 6x6 tier-4 boards. With the strike taken mid-run it
is never on a 7x7 or 10x10 at tier 3 either. Tier 4 is 80 percent of the
campaign. A one-strike run at 900ms a row (the suite's "fast") lands at 80.4
to 80.8 percent of par on every size, which is four paws, which is `Solid`,
which is the plain won headline. So a single wrong tap takes a run from
"Flawless" to nothing, and `game_verdict_sharp` ("Sharp work") is a shipped
string that does not render.

This is the shape the `Standing.Flawless` KDoc describes as the bug it was
rewritten for, in the same file, one enum entry up. The tests missed it the
same way: `everyVerdictIsReachableByActuallyPlayingABoard` reaches `Sharp` at
1ms a move and `aBoneSpentCostsTheFlawlessVerdictAndNothingElse` at 250ms a
move on an 8x8.

The enum's KDoc is also still the three-paw ladder: `Solid` says "past the
two-paw line, short of the third" while the code uses `threePawFraction`, and
`Sharp` says "past the three-paw line" while the code uses `fivePawFraction`.

**Done when:** either a one-strike run at 2500ms a row earns `Sharp` on every
shape `LevelCurve.campaignShape` declares, starter-dog boards included, or
`Sharp` and its string are removed. In both cases `StandingTest`'s reachability
sweep starts no faster than 900ms a row, and the `Standing` KDoc describes the
ladder the code implements.

**Hints:** Two honest designs. Keep four verdicts and put `Sharp` at
`fourPawFraction` with a bone lost, which makes it "fast and one mistake" and
leaves `Solid` as "clean or slow"; `theVerdictNeverContradictsThePaws` then
needs its band map redrawn, because `Sharp` would sit at four paws. Or delete
`Sharp`, since the ScoringConfig KDoc already says five paws means clean, and
let the sheet have three states. Do not lower `fivePawFraction` to make room;
that is the relabelling the class KDoc warns about. The win sheet mapping is
`verdictTitle` in `GameOutcomeSheets.kt`.
## SD-89 [P2] — `nearMiss` is one point short on 221 of the 224 rungs the campaign ships

**Found by:** the SD-6 scoring review, 2026-09-11.

**Ask:** `NearMiss.kt` line 62 computes the rung as `(par * nextFraction).toInt()`,
which floors, while `Scoring.paws` awards on `score >= par * fraction` as a
Double. Whenever `par * fraction` is not an integer, which is 221 of the 224
(size, tier, placements, rung) combinations in the campaign, two things go
wrong at once. A score exactly at the floor is *not* awarded the paw, and
`nearMiss` returns null for it because `short <= 0`, so the closest possible
miss gets no line at all. And every `pointsShort` that is reported is one less
than the points actually needed. Concretely: 4x4 tier 1, par 574, two-paw rung
at 304.22. A score of 304 rates one paw with nothing said; a score of 294 is
told it was 10 points short, scores 304 next time, and is still one paw. The
file's KDoc says the whole value of the number is that it does not lie.

**Done when:** for every shipped shape and rung, a score of
`floor(par * fraction)` either earns the paw or gets a near-miss line, and a
run that scores exactly `score + pointsShort` earns the rung it was told about.

**Hints:** `ceil` rather than `toInt()`. `NearMissTest.aRunJustUnderTheNextRungReportsTheGap`
computes `needed` with the same `.toInt()` and asserts 10, so it agrees with
the bug and will need to move with the fix. Mutations S1 and S2 (each `>=` in
`paws` to `>`) both survive, so no test pins a tie at any rung; the sweep in
`theGapNeverExceedsTheWindowItIsGatedOn` does hit the tie at percent 64 by
accident of integer division but only checks `pointsShort > 0` there.
## SD-90 [P2] — The `ScoringConfig` guards stop a dropped minus sign and nothing else

**Found by:** the SD-6 scoring review, 2026-09-11.

**Ask:** `MAX_POINT_VALUE` is documented "so scoring cannot overflow", and it
bounds the two Ints only. Every multiplier is unbounded above, and all of the
following construct and get played:

- `comboStep = 1e9, comboMax = 1e12`, `livesBonusRate = 1e8` or
  `difficultyBonusRate = 1e8`: a clean 10x10 tier-4 run totals
  -2,147,480,930, par is negative too, the clean run rates one paw and a
  two-strike slow run rates five. That is the inverted rating the guards were
  added for.
- `speedMaxMultiplier = 1e9` or `Double.POSITIVE_INFINITY`: each placement
  saturates at `Int.MAX_VALUE`, ten of them wrap to -10, the run scores its
  completion bonus and everyone gets five paws.
- `speedWindowMs = Long.MAX_VALUE`: `speedWindowMs * size` overflows negative
  and the speed term is dead.
- `speedMaxMultiplier = 1.0`, `comboStep = 0.0`, or all four paw fractions at
  `0.0`: the compression bug by remote config, with a clean instant run rating
  five and a two-strike slow run rating three or five.
- `boosterPenaltyRate = 1.0`: one booster banks zero, and the tutorial
  instructs a sniff and a treat on level 2.

Mutations S6 to S9 show the guards that do exist (fraction range,
`speedWindowMs > 0`, `comboMax >= 1`, `speedMaxMultiplier >= 1`) have no test.

**Done when:** every coefficient has a ceiling as well as a floor, a non-finite
value is rejected, and a test in the shape of `configRejectsNegativeRates`
names each one. A run under the largest legal config still scores positive and
rates in order, which `theLargestLegalConfigStillScoresPositively` should be
extended to cover rather than only the two Ints.

**Hints:** The ceilings do not need to be clever: multipliers and rates at 10,
the window at ten minutes, fractions already at 1. `isFinite()` on every
Double covers the Infinity case; NaN is already rejected because every
comparison with it is false. Check `DoubleConfigValue` for whether `"1e9"` and
`"Infinity"` parse, since that decides whether this is reachable from the
console or only from code.
## SD-91 [P2] — `PawLadderReachabilityTest` asserts the middle rungs across the campaign, not per shape, and skips the daily

**Found by:** the SD-6 scoring review, 2026-09-11.

**Ask:** The test's name says every rung on every shape. Its per-shape
assertions are rungs 1 and 5 only; rungs 2 to 4 are asserted as a union over
all shapes, so a rung reachable on one board and unreachable on the other 27
passes. Mutations that survive it: `fourPawFraction` at 0.83 (inside the
considered-pace band), `threePawFraction` at 0.74 (inside the clean-slow band),
`speedWindowMs` back to 8000 (the exact SD-47 regression) and `livesBonusRate`
back to 0.5 (half of yesterday's fix). All four are killed in
`:libraries:scoring` by `theLadderIsSpacedAgainstMeasuredPlay`, which is the
test that reads the shipped sizes from `SMALLEST_BOARD`, `BIGGEST_BOARD` and
`SHIPPED_MAX_DIFFICULTY`, the arrangement this file exists to replace. It also
walks `campaignShape` only. `dailyShape` ships 7x7 tier 2 and 8x8 tier 2,
which no campaign band does; measured today they are fine.

**Done when:** the four surviving mutants above fail in `:libraries:achievements`,
and the sweep covers `LevelCurve.dailyShape` as well as the campaign.

**Hints:** Port the `cut()` gap assertions from `theLadderIsSpacedAgainstMeasuredPlay`
(ceiling of the band below each rung, floor of the band above it) into this
file, driven off the curve. Then the scoring copy can keep its constants as a
worked example and stop being the guard. `theSweepActuallyCoversTheCampaign`
is a real vacuity check and can stay.
## SD-92 [P2] — Comment rot in the scoring slice

**Found by:** the SD-6 scoring review, 2026-09-11.

**Ask:** Small, and each is a thing the next tuner would have to run to learn:

- `ScoringConfig.kt` class KDoc opens "balanced on four things" and lists
  five (First, Second, the third that "came back", Fourth, "The last one").
- The band table in that KDoc, repeated in `docs/reference/features.md`, gives
  "two strikes, past the speed window" as 0.48 to 0.49, and both
  `ScoringTest.theWorstCompletableRunStillOnlyEarnsOnePaw` and
  `PawLadderReachabilityTest` call that "the worst run a player can finish".
  It is the *best* two-strike run: strikes on the second and third placement
  cost the least combo. Strikes spread through a 10x10 score 45.4 percent. No
  cut moves, because the two-paw cut is checked against the band's ceiling and
  the early strike is the ceiling, but a reader retuning from the table would
  think the floor is 0.48.
- `MAX_POINT_VALUE`'s "so scoring cannot overflow" (SD-90).
- `HintFinder.mostConstrainedUnresolvedCell` is a fallback for a board the
  engine cannot finish from the player's partial, which no shipped tier
  reaches; mutation H5 (min to max) survives. A sentence saying it is
  unreachable on shipped boards, or a test, would settle it.
- `Standing` KDoc (SD-88).

**Done when:** each line above reads true against the code, and a reader can
learn the real two-strike floor from the table without running the sweep.

**Hints:** All prose. The one that takes a decision is the band table: either
add a "two strikes, spread" row or relabel the existing one as the ceiling of
the two-strike band.
## SD-93 [P2] — `GameState.bonesUnspent` has no reader outside its own tests

**Found by:** the SD-71 agent, 2026-09-10, while clearing comment rot.

The share card was its only production reader and went with the sharing feature.
`GameViewModel.win` computes the same expression locally, so the property is
kept alive by two assertions in `GameViewModelTest` and nothing else.

Its KDoc now describes what it is rather than a deleted caller, which stops it
misleading anybody, and leaves it as a field the state carries for no one.

**Done when:** it is gone, and the two assertions read whatever they were really
about.

**Hints:** The second half is the work. Those assertions were written to check
what the share card would draw, so deleting the field without deciding what they
should assert instead would quietly drop coverage of the bones-at-the-end rule.
Check whether `win`'s local computation deserves the test rather than the field.
## SD-94 [P2] — A quarter of the test files skip the header the practices doc requires

**Found by:** the SD-72 agent, 2026-09-10, while rewriting that doc.

`docs/practices/testing.md` mandates a top-level KDoc on every test file saying
what it holds and why. Around 138 of 186 files have one. `GameViewModelTest`,
the largest test file in the repo, is one of the ones without.

A convention three quarters kept is not yet a convention, and this one earns its
keep: several bugs this week were found by reading a test's stated purpose and
noticing the assertions did not serve it.

**Done when:** either every test file carries the header, or the doc stops
requiring it.

**Hints:** Consider a guard in `:apps:integration` rather than a one-time sweep,
since the sweep is what rots. It is the same shape as the other source-scanning
tests there, and `DocReferencesResolveTest` is the closest model. Declare the
Gradle inputs with `inputs.files(...)` or the guard will silently read nothing,
which has happened four times in this repo.

Write the headers where they are missing rather than deleting the rule. Starting
with `GameViewModelTest` would be worth it on its own.

## SD-95 [P2] — One feature module keeps its own strings, which will make translation harder

**Ask:** Owner, 2026-09-11: *"ideally we dont have any per feature strings xml.
Id prefer all strings be in the resources module. Thatll make it easier to
translate in the future."*

`features/streak/impl` has its own `composeResources/values/strings.xml` with 32
strings in it. Every other player-facing string in the app, 497 of them, lives in
`libraries/resources`. So this is one module out of step rather than a pattern,
and it is cheap to fix now and annoying to fix after a translator has been sent
the first batch.

**Done when:** `libraries/resources` holds every player-facing string, no other
module declares a `composeResources` strings file, and something fails the build
if one appears again.

**Hints:** The guard matters more than the move. `UserFacingCopyStyleTest` in
`:apps:integration` already walks every `composeResources/**/*.xml`, so it knows
how to find them; add an assertion that the only one is the resources module's.
Declare the Gradle inputs with `inputs.files(...)` or it will silently read
nothing, which has happened four times in this repo.

Moving the strings means the streak feature takes a dependency on
`:libraries:resources` if it does not already, and the generated `Res` accessor
changes package, so the call sites move with it. Check whether the split was
deliberate before assuming it was not: a feature owning its own strings is a
reasonable pattern, just not the one this app chose.
