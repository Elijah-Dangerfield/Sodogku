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
`libraries/progress` and the streak folds, `libraries/navigation` and the
floating-window host, the iOS Swift layer, and `:apps:server`.

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

## SD-129 [P2] — Look at the two things this batch changed that only a device can judge

**Ask:** Two changes landed whose acceptance criterion is "does it look right",
and neither has been looked at. Both are reasoned about and tested structurally;
neither has been run.

**Done when:** somebody has opened the app and said yes or no to each.

**The press, from SD-119.** `bounceClick` moved above the fill inside `Surface`,
so every clickable `Surface` now scales its border, shadow and fill along with
its content, where before the content shrank away from a border that held still.
Press `Card`, `CardSecondary`, `NoticeBanner`, `StreakButton` and `IconButton`.
If the new press is wrong, the fix is a parameter, not a revert: the four SD-119
call sites need the new order to keep the press they already had.

**The win celebration, from SD-123.** Finish a level. The panel should rise from
the bottom over 340ms and the beats should land one at a time behind it, paws
staggered, score rolling up from zero. Things to look at specifically, because
they are the ones that were reasoned about rather than seen: the proportions on a
short display, the system-bar inset at the top, whether the stagger reads as
celebratory or as slow, and the script at its longest — a daily clear with a near
miss, a new best time, a treat and a streak is nine beats, which is when the new
`verticalScroll` starts mattering.

**The streak ceremony, from SD-124.** Clear a board on a day that grows the run.
The page should rise over a board that holds still, and the number should count
to the new run and thump. Two specific things: a run of **one** deliberately does
not flip (it starts where it ends, so only the thump lands), and
`StreakIntentionScreen` still counts `0 → 1` on the first-ever run — the two
pages now disagree about what a run of one does, which is defensible since they
are different moments, but somebody should look at them back to back and confirm
that.

**Hints:** `Surface.kt`, the `Box` modifier chain, and the KDoc section "Why the
press is applied before the fill". `bounceClick` is `graphicsLayer` then
`clickable`, and a `graphicsLayer` only transforms what is drawn below it, which
is the whole mechanism. `BasicButton` already works around the old order by
wrapping a non-clickable `Surface` in a `Box` that carries the press — if the new
order is right, that workaround comes out under SD-130.

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
