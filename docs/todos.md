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

- **`libraries/progress`, the streak and daily folds**, 2026-09-16, against
  `ae0c56b`, deliberately pointed at code that had changed the same day. Eleven
  findings. 37 mutations, 33 killed, every survivor explained. Verdict: the
  prompt rules are the best-covered code in the slice — eleven mutations against
  `StreakPrompts.kt`, all killed — and the defects are at the repository's seams
  and in what nobody observes. Two were fixed on the spot: a midnight crossing
  between deciding a prompt and recording it, which swallowed the next day's
  page (a real bug introduced by SD-121 hours earlier, reproduced before it was
  fixed), and `observe()` having no test at all, which two separate mutations
  proved by surviving the whole class. The rest are SD-137 through SD-140. The
  `celebratedOn` migration was checked against a real legacy blob through the
  actual serializer and is fine: existing players get one duplicate page on the
  day they update, and nothing after.
- **The `libraries/ui` board and dog components**, 2026-09-11. Eleven findings,
  now SD-99 through SD-109, two of them P1 and both about what a screen reader
  hears. Verdict: the board cell and the drag are right about the two things the
  brief worried about most, and the defects are three copies of one semantics
  mistake in the smaller components around them. Nine lines in ten of the slice
  have no test at all, which is the finding rather than a caveat: about 270 of
  3,350 lines are pure units with tests and the rest are composables. Seven of
  the eleven become provable with the composition tier that landed the day
  before.
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

**Every slice originally listed is now covered.** Pick new ones or decide this
runs on a schedule rather than on request. Candidates nobody has looked at:
`libraries/navigation` and the floating-window host, the iOS Swift layer, and
`:apps:server`.

**One thing the `libraries/progress` pass established that is worth repeating:**
pointing a review at code that changed the same day found a real bug in it within
the hour. Consider running this against a slice the last batch of work touched
rather than against the oldest untouched one.

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

## SD-129 [P2] — Look at everything the two September batches changed that only a device can judge

**Ask:** Two batches of work landed whose acceptance criterion is "does it look
right", and none of it has been run. Every piece is reasoned about and tested
structurally; nothing has been seen. Boot a phone and go through this list.

**Done when:** somebody has opened the app and said yes or no to each line.

**The palette, app-wide (visual handoff, 2026-09-20).** Every semantic token
moved a shade: blue `#2F8EF4`, ink `#3F2A1E`, page `#F5EFE7`, ivory cards, the
handoff's red and green. Walk the board, the level pane, settings and the
paywall, which are *not* in the handoff and shifted anyway. The board's region
palette did not move.

**The press, from SD-119.** `bounceClick` moved above the fill inside
`Surface`, so every clickable `Surface` scales its border and fill with its
content. Press `Card`, `NoticeBanner`, `StreakButton`, `IconButton`.

**The streak ceremonies (rows 06, 07, 08).** Clear a board on a day that grows
the run: the band should bleed under the status bar, the dog should hang off
its edge, the number should count up after the page lands, and the week strip's
new day should pop after the number settles. Then break a run and come back for
the brown page. Specifically: the status-bar icons are dark on every band, which
is wrong on blue and brown (SD-144); the dog has no drop shadow; and a run of one
does not flip while `StreakIntentionScreen` still counts `0 → 1`.

**Board cleared (row 03).** The ray burst overruns its band by about 25dp under
the scroll column; the three chip glyphs are `⚡ ⏱ ❌` as text and will render
differently on Android and iOS; the mistakes chip is red on a zero; and the
longest script (a daily with a near miss, a treat and a streak) is eight beats on
a short display.

**Achievements (row 10).** The grid drops from three columns to two, then one,
when the longest badge name cannot fit at the system font size; the locked
glyphs are desaturated through a `graphicsLayer` colour filter; See all should
scroll, not navigate. Set the font scale to 2 and look at the grid.

**The toast.** On a board in play it hangs off the lives pill; on the cleared
screen it hangs under the stat chips, because under the pill it sat on the dog.
On a phone whose header is taller than the design's, check it clears the
buttons; on a short phone, check the cleared-screen toast sits between the chips
and Next level rather than on either, and that a daily with a footnote under the
chips is readable once the toast has gone. Level 1 now hands out one badge
(First Steps); a first clear between 1am and 8am adds a hidden one, eight
seconds of toast unless tapped.

**The loading bone.** No outline now. Check it on the iOS splash overlay too.

## SD-130 [P2] — Fold the three hand-rolled presses in `libraries/ui` back onto `Surface`

**Ask:** Three design-system components build their own press rather than using
the DS component that exists for it, each for a reason that SD-119 partly
removed.

**Done when:** each of the three either calls `Surface` or has the reason it
cannot written next to it.

**The three:**

- `BasicButton` lines 95-110 wraps a *non*-clickable `Surface` in a `Box`
  carrying `bounceClick`, purely to get the press outside the fill. SD-119 made
  that the default order, so this should now be a plain `Surface(onClick =)`.
  Depends on SD-129 confirming the new order is the one we want.
- `CircleIcon` lines 32-42 puts a raw `Modifier.clickable` on a non-clickable
  `Surface`, so it has no press animation and no `Role.Button` — it is the only
  pressable surface in the DS that does not bounce.
- `ListItem` lines 163-166 rolls its own because `bounceClick` is a `composed {}`
  helper that cannot contribute a role or a toggled state. `Surface` now applies
  `role` in the right place; a `toggled` parameter beside it would likely finish
  the job.

**Also here, because it blocks writing tests for any of the above:**
`ColorResource` is `@Deprecated` at class level with no non-deprecated way to
name a literal colour, so `SurfaceSemanticsTest` needs a class-level
`@Suppress("DEPRECATION")` to say `ColorResource.White`. `Screen.kt:31` and
`Button.kt:378` carry the same suppression inside the DS itself. Either the
deprecation is wrong or it needs a replacement.

**Hints:** Found by the SD-119 agent while converting four call sites onto
`Surface`. `SurfaceSemanticsTest` in `:libraries:ui` `androidUnitTest` is the
composition-tier worked example for asserting what any of these expose.

## SD-131 [P1] — Remote config has no origin, so no shipped build can read it

**Ask:** AGENTS.md calls remote config "the live-ops lever and the reason the
server exists". It is wired end to end and it cannot work: `DefaultNetworkConfig`
is the only production `NetworkConfig` binding and its `baseUrl` is `""`.
Nothing replaces it anywhere. Every config read in every shipped build falls back
to `FallbackConfigMap`, and the `:apps:admin` console edits values no device will
ever fetch.

**Done when:** a release build reads config from the real server, or it is
written down that config is a compile-time table and the console is a staging
tool. Either is a fine answer. Believing the first while shipping the second is
not.

**Hints:** `libraries/networking/impl/.../DefaultNetworkConfig.kt` says in its
own KDoc how it expects to be replaced: bind your own `NetworkConfig` with
`replaces = [DefaultNetworkConfig::class]`, reading the base URL out of
BuildConfig per variant. That replacement was never written. `NetworkClientImpl`
line 95 skips `url()` entirely when the base URL is blank, which is why this
fails as a relative-URL request to `http://localhost` rather than as anything
that names the real cause — the SD-122 session log shows exactly that, an
NSURLError -1004 against `http://localhost/v1/app-config`.

Found while ruling out a live config override for SD-122. It mattered there and
it will matter to every item that assumes a value can be tuned without a release:
right now none of them can. Check whether the monetization keys' "fail open
toward the player" rule still holds when the server is unreachable by
construction, since that is the state every install is in.

## SD-132 [P2] — Decide whether the score should measure the attempt or the interval

**Ask:** The speed multiplier is charged per placement, against the gap before
it. So a player who reads the whole board, works the whole thing out, and then
taps the answer out scores **0.92 to 0.98 of par on every grid size**, whether
the reading took thirty seconds or five minutes. By score alone that run is
indistinguishable from a sprint.

**Done when:** there is a decision, written down, on whether that is the game we
want. It may well be — rewarding a player who solves it in their head before
touching anything is defensible. But right now it is a side effect of where the
clock is read, not a choice anybody made.

**Hints:** SD-122 moved `fivePawFraction` to 0.89 and that rung now sits in the
middle of the measured gap, which is as far as tuning goes: **no threshold below
0.92 closes this**, because the run genuinely reaches the fast band. Closing it
means the speed term reading the attempt rather than the interval, which changes
every score in the game and needs its own sweep.

The same seam holds the question the SD-122 case file asked to raise rather than
decide: the reported run had a **63-second pause** before its first placement,
and the clock cannot tell a player staring at a board from one who put the phone
down to answer the door. Both are charged the same. Whether an idle cap belongs
here is the owner's call and is part of the same decision.

`libraries/scoring/.../ScoringConfig.kt` documents the bands, and its
`fivePawFraction` KDoc states this limitation in the place somebody tuning the
number will read it. `PawLadderReachabilityTest` and `ScoringTest` sweep the
ladder and are where a new speed term would have to prove itself.

## SD-133 [P2] — Dark mode, defaulting to the device setting

**Ask:** The same report that asked for a full-screen win celebration (SD-123)
also asked for dark mode, defaulting to whatever the device is set to. It was
deliberately kept out of that item and is not started.

**Done when:** the app follows the system light/dark setting, and every screen
has been looked at in both.

**Hints:** `AppTheme` and `libraries/ui/.../system/color/` own the palette. This
is a sweep rather than a feature: the value is in finding the call sites that
name a literal colour instead of a `ColorResource`, and there are some — SD-130
records that `ColorResource` is `@Deprecated` at class level with no
non-deprecated way to name a literal, which is the same seam from the other end.
Sentry https://elijah-dangerfield.sentry.io/issues/SODOGKU-H · session
bec35582-715e-4374-98b3-19ad3ac2e472 · 2026-09-12

## SD-134 [P2] — Tell a daily player that a cross is a legitimate first move

**Ask:** SD-126 asked whether the generated daily boards were too hard to open.
They were measured and they are not: every one of the 730 has a deduction
available on the empty grid and none ever needs a guess. What is true is that
**38% of dailies open with no forced dog**, so the first move is a cross, and the
daily never says so. The campaign hands out a free dog for sixteen levels
precisely so the first empty grid arrives after the rules are known. The daily
inherits none of that.

**Done when:** a player meeting an empty-opening daily is pointed at the cross,
or at the sniff, once. Not a tutorial and not on every board.

**Hints:** The sniff already does this exact job — it shows where a dog cannot
go, names the technique, and declines to charge when it has nothing to add. The
screenshot behind SD-126 shows three bones and three sniffs unspent, so nothing
was lost; the owner just did not reach for it. That makes "point at the sniff"
the cheapest version of this and probably the right one.

`docs/cases/SD-126/README.md` has the measurement, the worked three-step solve of
the board he was stuck on, and the re-run recipe. `docs/decisions.md`
(2026-09-16) records why regenerating the pool and giving the daily a starter dog
were both rejected, so do not reopen either here.
Sentry https://elijah-dangerfield.sentry.io/issues/SODOGKU-J · session
bec35582-715e-4374-98b3-19ad3ac2e472 · 2026-09-12

## SD-135 [P2] — Look at the app and decide whether Caption moves to 12/10

**Ask:** SD-128 answered the half of the type-scale question that could be
answered from a test bench: C200 at 6sp was indefensible and is deleted. It
deliberately left the other half open, because it needs somebody's eyes.

**Done when:** there is a yes or no, written into `docs/decisions.md` beside the
2026-09-16 entry, on whether Caption C400 and C300 move from 10sp and 8sp to
12sp and 10sp.

**The numbers are already gathered**, in that entry. The short version: Material's
smallest token is `labelSmall` 11sp, UIKit's is `caption2` 11pt, and the app's
Poppins has a taller x-height than Roboto, so 10sp here reads like about 10.4sp
of Roboto. That leaves C400 roughly 0.6sp under the platform floor, which is a
much weaker case than C200's was. Against moving: 12/10 changes 32 shipped call
sites in 13 files, puts Caption within 2sp of Body's 14sp default and exactly on
`Body.B500`, and a caption that is nearly body text has stopped being a caption.

**While you have the app open, check the other three ramps' bottom steps**,
which SD-128 noticed and did not survey: `Heading.H400` is 10sp, `Body.B400` is
10sp, and `Label.L300` is 8sp. All three are under Material's floor too, and the
Caption decision says nothing about them.

**Hints:** `libraries/ui/.../system/typography/TypographyResource.kt` owns every
ramp. It is three lines per ramp. `StreakCalendarFitsTest` and
`BottomBarBadgeFitsTest` in `:libraries:ui` are the pattern for pinning "this
text fits its box at font scale 2" if a size does move — and note the trap
recorded in both: without `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, Robolectric
measures every string to the same box whatever size it is set in, and the test
is worthless while staying green.

## SD-136 [P2] — The notification badge on a tab says a bare number

**Ask:** A screen reader on the bottom bar hears the tab's name and then a bare
digit, with nothing saying what the digit counts.

**Done when:** the badge names what it is counting, and `99+` reads as something
a person would say.

**Hints:** `BottomBarBadge` in `libraries/ui/.../components/BottomBar.kt`. It
goes through Material's `BadgedBox` under `@OptIn(ExperimentalMaterial3Api::class)`,
against AGENTS.md's "avoid Material directly" guideline, and that is the same
knot: `BadgedBox` hangs the badge outside its anchor's bounds, which is why
`AppBottomBar` had to stop clipping under SD-128. A DS layout that measures the
badge into its own bounds would fix the semantics and the overflow together.
`Surface` gained a `clip` parameter for that fix and only the bottom bar passes
`false`; if the badge stops overhanging, the parameter may have no callers left.

Found while measuring the badge for SD-128. The overflow half is fixed and
tested; this half is not.

## SD-137 [P1] — A board finished after midnight counts for a different day than its daily does

**Ask:** `StreakRepositoryImpl.onBoardCompleted()` takes no date and writes
`today()` at the moment the board is finished. `DailyRepositoryImpl` deliberately
records against the *board's* date and has a test saying so
(`theResultIsRecordedAgainstTheBoardThatWasPlayed_notTheClockAtTheEnd`). So the
two disagree for any board started before midnight and finished after it.

**Done when:** a board started on day N and finished on day N+1 counts for the
same day in both, or the difference is written down as intended.

**Failure scenario, worked:** play day 0. Start day 1's daily at 23:55, finish at
00:05. `daily_result` has day 1, `play_day` has days 0 and 2. The streak calendar
marks day 1 Missed while the daily history says it was completed, the run reads 1
where the player has done three days in a row, and they get `Celebrate(1)` — or,
after SD-127, a page telling them the run they are still holding has ended.

**Hints:** `StreakRepositoryImpl.kt:77-79` against `DailyRepositoryImpl.kt:80`
and `GameViewModel.kt:2099`, which already passes the board's date to the daily.
Passing the same date to `onBoardCompleted` is the obvious shape; the campaign
has no equivalent date to pass, which is the part that needs deciding. Nothing in
the slice tests this shape.

Found by the SD-6 review of `libraries/progress`, 2026-09-16. Verified by
reading, not by running; the frequency is a guess.

## SD-138 [P2] — A bridged daily does not bridge the play streak, and SD-127 now says so out loud

**Ask:** A player with a 12-day run misses a day, watches the rewarded ad, and
the daily card says the run is bridged and continuing. The play-day fold knows
nothing about it: `playCalendarOn` only ever produces Future, Completed and
Missed, and `StreakDayState.Bridged` is a state nothing produces.

**Done when:** a freeze or a restore either bridges the play streak too, or the
daily stops claiming it bridged anything.

**Decided 2026-09-20:** the second one. The daily's freeze and restore retire and
a single freeze economy lands on the play streak, which closes this item as a side
effect rather than on its own. Do not fix this in isolation; the plan is
`docs/design/streak-freeze.md` phase 1 and the reasoning is in `docs/decisions.md`.
Note the design doc says to land SD-141 first, because the grace window changes
what counts as a missed day and that is the input to every rule here.

**Why it is worse now than it was:** before SD-127 the streak page quietly showed
a 1 and the disagreement was silent. Now `brokenPlayStreakOn` returns 12 and the
page says "Your run of 12 days ended" — the day after the app sold an ad on the
promise that it had not.

**Hints:** `PlayStreak.kt:99-121`, `StreakSummary.kt:19-20` (which already says
`Bridged` is never spent, "see SD-28"), and `DailyRepositoryImpl.useFreeze()` /
`restoreStreak()`, both live behind `GameAction.UseFreeze` and `RestoreStreak`.
This is a product decision rather than a code slip and it overlaps SD-28 in
`docs/backlog.md`, which has to answer what a freeze covers first.

Found by the SD-6 review of `libraries/progress`, 2026-09-16.

## SD-139 [P2] — A daily-only player is unreachable by two of the three streak rules

**Ask:** `boardsCleared` counts `LevelState.Completed` in `ProgressRepository`,
and dailies never write there. So a player who only ever plays the daily never
spends the intention moment, which means `intentionShown` stays false forever,
which means the Celebrate floor stays at 2 and the "a run of one is somebody who
came back" rule never applies to them at all.

**Done when:** either the intention can be spent by a daily, or the two rules
that read `intentionShown` use something a daily-only player can reach. **And a
player who has finished the whole campaign is a first-class player from then on**,
not a degraded one.

**Owner, 2026-09-20:** *"we should have some mechanism to still support the daily
board player because I assume people could finish all games and then want to just
play daily only."* That is the case that makes this more than an edge. The
campaign is 1,000 levels and the daily pool covers two years, so the intended
long-term player is a daily-only player. Right now the streak rules treat them as
somebody who has not started yet, forever.

**Three consequences, all reachable:**

- A daily-only player who plays every other day sees no page, ever.
- **A player who clears all 1,000 campaign levels and then plays only dailies**
  has `intentionShown` true, so they are fine. But a player who *never* touches
  the campaign, or stops early and switches to dailies, is stuck at the floor of
  2 forever. So the rule accidentally rewards finishing the campaign rather than
  turning up, which is the opposite of what the streak is for.
- A daily-only player whose 12-day run broke yesterday clears their first two
  campaign boards today. `promptFor` returns `Intention`, that spends the day,
  tomorrow the run is 2, and `Lost(12)` is never shown. This is pinned today by
  `theIntentionOutranksALostRunToo`, so changing it means changing that test
  deliberately.

**The likely shape**, not a decision: `boardsCleared` is the wrong input. The
intention is asking "has this player played enough to be asked for a habit", and
`play_day` already answers that without caring which puzzle, which is the same
argument that moved the streak off `daily_result` in the first place. Counting
finished boards of any kind, or even rows in `play_day`, would make every rule
reachable by everybody. Check what else reads `boardsCleared` before changing its
meaning.

**Hints:** `StreakPrompts.kt:126-133` and `intentionIsDue`. Two comments in
`StreakPromptsTest` (around lines 141 and 237) assert these collisions are
unreachable because "nobody holds a run of two days without having cleared two
boards" — a test in the same file, at 224-233, is built on exactly that player.
Those comments are wrong and should go with the fix either way.

Found by the SD-6 review of `libraries/progress`, 2026-09-16. The reachability is
verified; whether the outcome is wrong is a judgement call.

## SD-140 [P2] — Three small ones from the `libraries/progress` review

**Ask:** Three findings too small for their own items, all verified.

**Done when:** each is fixed or dismissed in writing.

- **A test that cannot fail.** `DailyCalendarTest.untilNextDay_isAlwaysPositive`
  (lines 67-75) is named for the `.coerceAtLeast(1.seconds)` floor in
  `DailyCalendar.kt:36-37` and cannot observe it: the fixture is 23:59:59Z, which
  yields exactly one second with or without the floor. Removing the floor leaves
  the test green. The floor may be unreachable in any case, since the next day's
  start is after every instant of today — in which case say so in the docblock
  rather than leaving a guard nobody can test.
- **`summaryOn` reads the clock twice.** `StreakRepositoryImpl.kt:129-136` builds
  `untilTomorrow` from a fresh `clock.now()` while `today` came from
  `dayChanges()`, so a DAO emission between real midnight and the flow waking
  gives tomorrow's date with a 24-hour countdown. Self-heals on the next
  emission, and the same shape is in `DailyRepositoryImpl.statusOn:167`. What is
  actually wrong is `StreakSummary`'s docblock (lines 77-84), which claims every
  field comes from one snapshot.
- **Stale docblocks in `StreakSummary.kt`.** Lines 41-44 say current and longest
  fold from `daily_result`, and line 47 says "as `DailyStatus.streak` reports
  it". Both predate the `play_day` split.

Found by the SD-6 review of `libraries/progress`, 2026-09-16.

## SD-141 [P2] — Give the streak a 15-minute grace window after midnight

**Ask:** The owner, 2026-09-20: *"I want streaks to have a 15 min buffer. So if
someone finished at 12:02 they should still get it for the previous day. IFF they
didn't already have one for that day. But not the next day."*

Clarified by the owner the same day: *"buffer just means we give the user a
little room in case they missed the midnight cut off. So we will give them a
little room to not mess up their streak."*

**So the purpose leads, and it settles the edges.** The point is that a run does
not break over a couple of minutes. A player who finishes at 00:02 has almost
certainly been playing since before midnight, and charging them a broken run for
that is the app being right and unhelpful at the same time. Where the rule below
is ambiguous, pick the reading that keeps the run alive.

**Done when:** a board finished within 15 minutes after local midnight marks
**yesterday** rather than today, but only if yesterday is not already marked; a
board finished at 00:16 or later marks today as it does now; and a player in the
window still gets the page for the day they just saved rather than losing the
next one (see the bookkeeping trap below, which is the part that will bite).

**The rule, spelled out**, because there is more than one way to read "buffer":

- Finish at 00:02, yesterday **not** already in `play_day` → write **yesterday**.
  Not today as well. One row, dated yesterday. The player has therefore not
  played "today" yet and needs another board to extend the run, which is
  deliberate and matches what the daily already does.
- Finish at 00:02, yesterday **already** in `play_day` → write **today**, the
  current behaviour. Nothing is gained by back-dating a day they already hold,
  and writing yesterday twice is a no-op that would silently lose the day.
- Finish at 00:16 → write **today**. No window.

**Hints:** `StreakRepositoryImpl.onBoardCompleted()` is the whole write path and
is currently one line, `dao.insertIfAbsent(PlayDayEntity(date = today().toString()))`.
It will now have to read the existing rows to answer the "unless they already
have it" clause, so it needs `dao.all()` first. Keep the date arithmetic in
`PlayStreak.kt` as a pure function over `(now, zone, played)` so the window is one
assertion rather than a scenario; that is the pattern every other date rule here
follows and the reason they are testable at all.

Assertions worth having, most of which are cheap once the decision is a pure
function: 00:00:00 exactly, 00:14:59, 00:15:00 (decide which side the boundary
falls on and pin it), 00:02 with yesterday already held, 00:02 on a day where
yesterday is held *and* today is held, and the same times across a DST
spring-forward boundary, where `atStartOfDayIn` is the only safe way to ask when
midnight was and in a few zones midnight does not exist at all.

**Make the 15 a named constant, and consider a config key.** Every other tunable
of this kind is in `libraries/config/.../values/`, and a grace window is exactly
the sort of number that gets argued about after launch. Note SD-131 first: no
shipped build can read config today, so a key is forward-looking rather than
immediately useful.

**The trap, and it is not the write path.** The grace window credits the board to
yesterday, but `pendingPrompt()` and `onPromptShown()` both work off `today()`, so
the *ceremony* bookkeeping would still spend the new day. Traced against the
current code, a player in the window gets `Celebrate(N)` for the run through
yesterday, `celebratedOn` is set to today, and then the board they play properly
this afternoon takes the run to N+1 and says **nothing**, because the day-scoped
guard thinks it has already spoken. The grace window would hand them yesterday's
page and take today's away, which is the opposite of "room to not mess up their
streak".

The fix is that the whole session belongs to yesterday, not just the `play_day`
row: when the window applies, the prompt is decided for and spends **yesterday**
too. `promptFor` already takes `today` as its first argument and
`StreakRepositoryImpl` already remembers `promptedFor` for the midnight-crossing
fix, so both seams exist. Whatever date the window picks should be computed once
and handed to the write, the prompt, and `onPromptShown` alike, rather than each
of the three asking the clock again.

Worth a test per leg: the row lands on yesterday, the page is for yesterday, and
the afternoon board still earns its own page.

**This largely dissolves SD-137 for the case that actually happens.** SD-137 is
that a board finished after midnight counts for the day it finished in the streak
and the day it *started* in the daily, so the two tables disagree about the same
session. Inside the window the two now agree. They still disagree for a long
session that crosses midnight by more than 15 minutes, so SD-137 does not close,
but the common case stops being wrong. Read SD-137 before starting this and
decide whether to do both at once.

**What this does not fix, deliberately:** a day genuinely skipped. A player who
flies east across the dateline can lose a local calendar day they never had a
chance to play. That is a freeze's job (SD-28 in `docs/backlog.md`), not a grace
window's.

## SD-142 [P1] — Two different streak numbers on one screen, under the same string

**Ask:** The level pane shows the streak twice, with two different numbers, using
the same string resource for both. A player reads one screen that disagrees with
itself.

**Done when:** the pane shows one streak number, or the two are visibly different
things with different labels.

**Where:** `LevelDrawer.kt` line ~178 passes `state.playStreak` to `StreakButton`
with `Res.string.daily_streak`. Line ~256 passes `status.streak`, the
daily-only fold from `daily_result`, into `DailyCard`, which renders it at
display size through the **same** `Res.string.daily_streak`. So a player who
cleared six campaign boards this week and no dailies sees a run of 6 on the
button and 0 on the card, both captioned the same way.

**Hints:** This is the visible half of the split recorded in SD-138 and worked
through in `docs/design/streak-freeze.md`, which recommends the daily card stop
printing its own streak entirely. Read that before picking a fix, because
"relabel both" and "delete one" lead to different work and the design doc argues
for the second.

The string name is itself a leftover: `daily_streak` predates the streak moving
off the daily. Whatever the fix, the resource wants renaming with it.

Found while planning the streak freeze, 2026-09-20. Verified by reading both call
sites; not yet seen on a device.

## SD-143 [P2] — Build phase 1 of the streak freeze

**Ask:** The owner decided on 2026-09-20: one freeze economy, on the play streak,
and the daily's freeze and restore retire. The plan is written; nothing is built.
This item is the build.

**Done when:** `docs/design/streak-freeze.md` phase 1 is shipped end to end: a
player who misses a day and holds a credit keeps their run and sees the bridged
day on the calendar; the daily card no longer offers a freeze or a restore; and
SD-28 and SD-138 are closed in the same commit.

**Read first:** `docs/design/streak-freeze.md` (the plan, with the file list and
the five config keys) and the 2026-09-20 entry in `docs/decisions.md` (the call).

**Still open, with the recommended answer for each.** Take these unless the
owner says otherwise:

- Numbers: starting balance 3, holding cap 5, auto-cover 2 consecutive days,
  retroactive reach 3 days. SD-131 means these defaults *are* the product until
  config can reach a build, so pick them as if they cannot change.
- The rollout flag has to ship `true` or the feature has to not ship, for the
  same reason: a `false` no console can flip is a dark feature forever.
- The daily card stops printing its own streak number (SD-142 closes with this).
- No cash purchase in this phase. Phase 3 at the earliest.

**Sequencing:** land SD-141 (the 15-minute grace window) first. It changes what
counts as a missed day, which is the input to every freeze rule, and a credit
spent on a day the grace would have saved is burned for nothing.

**A conflict to resolve before starting**, found 2026-09-20 in the visual design
handoff (`~/Documents/design_handoff_streak_rewards/README.md`): that document
proposes a *weekly streak present* on day seven as the main source of help, with
freezes in its randomised pool, and removes rewards from the level ladder. The
design doc and the owner's SD-28 answer put the freeze source on the campaign
first-clear reward. Those are two different sources and two different cadences.
The owner has to say which, or both; do not pick silently.

`DailyOutcome.Frozen` and `Restored` stay parseable forever regardless. It is the
offers that retire, not the vocabulary.

## SD-144 [P2] — Status-bar icons per screen, so the blue and brown bands do not get dark icons

**Ask:** The three streak ceremonies bleed a coloured band under the status bar,
by design. Android sets `SystemBarStyle.light` once in `MainActivity` (dark icons
everywhere) and iOS declares nothing, so the blue and brown bands show dark
icons on a dark colour, and the amber band happens to be right.

**Done when:** a screen can ask for light or dark status-bar icons and the three
ceremonies ask for the right ones.

**Hints:** No per-screen mechanism exists. `StreakCeremonyLayout` in
`features/streak/impl` is the one place that needs it today; `HeroBand`'s KDoc
records that the band deliberately bleeds under the bar. On Android this is
`enableEdgeToEdge` with a per-destination style or a `WindowInsetsController`
call scoped to the route; on iOS `preferredStatusBarStyle` through the
`ComposeUIViewController` host. Found by the streak pass on 2026-09-20; not built
because it is platform plumbing rather than a screen.

## SD-145 [P2] — The handoff's behaviours that were deliberately not built

**Ask:** The 2026-09-20 visual handoff describes four behaviours that are not
visual and were not built, because each is a product change and one of them
conflicts with a decision the owner made the same day. They are recorded here so
they are decided rather than forgotten.

**Done when:** each has a yes, a no, or its own item.

- **The day-seven present** and the weekly present economy behind it, with
  freezes in its randomised pool and rewards taken off the level ladder. This
  is the SD-143 conflict: the freeze source the owner chose is the campaign
  first-clear reward. Not built; no `pendingPresent` state exists; the intention
  copy no longer promises a present on Sunday.
- **Badges granting bones.** The toast design carries a `+3` bone pill. Badges
  grant nothing today. The toast has a trailing slot and no pill.
- **Streak lost shown on app open.** The handoff's table says the lost page
  appears when the app is opened after a missed day. Today it appears after the
  first board finished on the first day back (`pendingPrompt` runs on
  completion). Different moment, different design; the current one was chosen
  in SD-127 and has a reason.
- **`I'M IN` enabling a daily reminder.** There are no notifications in this app
  of any kind. The button records the commitment and nothing else.

**Hints:** `docs/design/streak-freeze.md` and the SD-28 decision in
`docs/decisions.md` for the first; `UnlockToast.kt`'s `trailing` slot for the
second; `StreakPrompts.kt` for the third.

## SD-146 [P2] — Small things the visual pass turned up and did not fix

**Ask:** Six findings from the four screen passes, none worth its own item.

**Done when:** each is fixed or dismissed in writing.

- `Scoring.nearMiss` has no production caller since `pointsToNextPaw` took over;
  only tests read it. Delete it or say why it stays.
- `FullScreenLoader` is a Material `CircularProgressIndicator` on a design
  system whose rule is "avoid Material directly", and it is what the streak,
  achievements and game screens show while loading. The bone is the app's
  loader; it should probably be this too.
- The unlock toast is a clickable `Row` holding three `Text` nodes, so a screen
  reader hears the label, the title and the reason as separate fragments. Same
  shape as the SD-119 finding; `Surface(contentDescription =)` is the fix.
- `PawRatingHoldsStillTest.EVERY_PAW = 3` is a stale name since five paws.
- `NonLazyVerticalGrid` appends a trailing spacer after its last row, so every
  achievements shelf carries 12dp of extra bottom space.
- Four other bones keep their outline (the lives row, the refill control, the
  level reward chip, `GameHud`'s `LiveBoneEdge`); the handoff only changed the
  loading bone. Decide whether the rest follow.
