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

<!-- Newest at the bottom. -->## SD-6 [P2] — Standing code review, by an agent that did not write the code

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
## SD-102 [P2] — FocusScrim reads its Animatable in composition, and the detekt rule cannot see it

**Ask:** `libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/system/Focus.kt:196`
is `if (progress.value <= 0f) return`, a read of a `remember { Animatable() }`
in the composable body. `AnimatedStateReadInComposition` matches only
`by animate*AsState(...)`, so this shape is invisible to it in every file. Two
more of the same shape outside this slice: `ScoreCounter.kt:223`
(`if (progress.value >= 1f) return`) and `LevelDrawer.kt:126`
(`if (slide.value <= 0f) return`, where `slide` is an `animateFloatAsState` kept
as `State`, which is the rule's own recommended fix and then read anyway).

Three consequences in `FocusScrim`:

- The whole body, the `lit` lookup, the `Box`, the `AccessibleHole` nodes and
  the `content` lambda's scope, recomposes on every frame of the `Motion.Pop`
  spring in and out. Bounded, small subtree, and still the pattern the repo
  bans.
- `Motion.Pop` is a 0.5-damping spring with no bounds set on the `Animatable`.
  On dismissal it undershoots zero (about 16% for that damping, then about 3%
  back over), so the `<= 0f` gate removes the scrim from composition and then
  composes it again as the spring recrosses zero: a fresh `pointerInput`, fresh
  `AccessibleHole` semantics nodes, the coach mark re-entering, for a few frames
  at an alpha nobody can see. This half is reasoning from the spring, not
  observed. Confidence medium-high.
- The scrim ignores `LocalReduceAnimations`. `features.md` says dialogs fade
  under it; the scrim springs regardless.

Scenario: a TalkBack user dismissing the last-bone warning may hear the lit
control's node flicker back into the tree during the fade-out. A sighted player
sees nothing.

**Done when:** composition depends on a `derivedStateOf { progress.value > 0f }`
as `BoardCell` already does; the progress cannot leave `[0, 1]` (either
`updateBounds(0f, 1f)` or `Motion.fade()` under reduce-animations and a bounded
spring otherwise); and the detekt rule flags a `.value` read of an
`Animatable` or a kept `State` in a composable body outside a
`graphicsLayer`/`drawBehind`/`offset`/`layout` lambda, or a targeted rule
covers the `if (x.value` gate shape. A composition test with a recomposition
counter on `content` proves the first part.

**Hints:** The two sibling sites are worth fixing in the same sweep; the
`LevelDrawer` one shows the rule's fix text creates the blind spot. Found by the
SD-6 review of the `libraries/ui` board and dog slice, 2026-09-11, against
`17a2a6c`.
## SD-103 [P2] — One Dog component, so no call site has to remember the preview fix

**Ask:** Owner, 2026-09-11: *"we should have a custom DS component called Dog and
maybe a few variants of that to render the dogs stills and animated versions.
That component should check local debug and choose to animate or not and that
component can be how all dogs are rendered, that way every call site doesn't need
to reimplement that fix."*

**The bug that prompted it.** `AnimatedDog` loops forever under
`LocalInspectionMode`, so a preview containing one spins until Android Studio
gives up. `LoopingDog` and `BoardControl` both check the flag and hold a frame.
`AnimatedDog` does not. That is three call sites and two of them got it right,
which is the shape that keeps producing this: every new caller has to know.

**Done when:** there is one `Dog` component in the design system, every dog in
the app is drawn through it, and the inspection-mode decision lives inside it and
nowhere else. A preview containing any dog renders to a still.

**Hints:** Take the variants from what callers actually need rather than from what
exists: a still at a pose, a looping idle, and whatever the win and streak screens
do. `DogPose` already names the poses. `AnimatedDog`, `LoopingDog` and the dog
inside `BoardControl` are the three shapes to fold in, and the streak hero and
the recap draw dogs too.

The check is `LocalInspectionMode.current`, not a debug flag: it is true in
previews and in composition tests, which is the other place an endless animation
hurts. `LocalReduceAnimations` is a separate question and both matter, so make the
component answer both and say which wins.

A guard is worth more than the refactor. Once one component owns it, a test that
fails when a composable outside the design system references a dog drawable keeps
it owned.
## SD-104 [P2] — RuleChip flashes once per rule, not once per strike

**Ask:** `RuleChip`'s flash
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/game/GameHud.kt:230`)
is `LaunchedEffect(highlighted, still)` on a `Boolean`. A second wrong guess
against the same rule leaves `highlighted` true, so the effect does not restart
and the chip does not move, while the cell shakes and the board flinches, both
of which are keyed on a nonce for exactly this case. `BoardCell`'s own docblock
states the rule: "A nonce rather than a boolean, so the same cell can be got
wrong twice running."

Scenario: a player breaks "no touching" twice in a row. The first strike flashes
the chip and it settles to the tint; the second changes nothing on the chip, so
the one piece of feedback that names the rule is the one that goes quiet.
Confidence high on the behaviour; whether a re-flash is wanted is a design call,
and the rest of the slice has already made it.

**Done when:** a second strike against the same rule re-flashes its chip.

**Hints:** Key the effect on `state.strikeNonce`, passed from `RuleChips` in
`GameScreen.kt:572`, and keep the boolean for which chip. A pure
`ruleChipFlashKey(broken, strikeNonce)` beside `brokenRule` makes the decision
testable without a composition. Found by the SD-6 review of the `libraries/ui`
board and dog slice, 2026-09-11, against `17a2a6c`.
## SD-105 [P2] — rememberHaptics allocates a new Haptics on every composition

**Ask:** `rememberHaptics`
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/system/Haptics.kt:70`)
remembers nothing: it returns `Haptics(feedback, enabled)` each call. `Haptics`
is `@Immutable` with identity equality, so every composition yields a "changed"
value. In `features/game` it is used locally and this costs one allocation per
recomposition. In `features/streak` it is the value of a
`staticCompositionLocalOf` provided at an entry point that recomposes on every
state change (`StreakFeatureEntryPoint.kt:54` and `:72`), and a static local
whose value changes does not track reads: it recomposes everything under the
provider and disables skipping while it does. Confidence high on the
allocation, medium-high on the static-local consequence, which is Compose's
documented behaviour and was not measured here.

**Done when:** `rememberHaptics` returns the same instance for the same
`(feedback, enabled)`, and a composition test with a recomposition counter under
`CompositionLocalProvider(LocalHaptics provides rememberHaptics(...))` shows the
child not recomposing when an unrelated state changes.

**Hints:** `remember(feedback, enabled) { Haptics(feedback, enabled) }`. It is a
one-line change plus an import and was not taken here because its effect is in
another feature. Found by the SD-6 review of the `libraries/ui` board and dog
slice, 2026-09-11, against `17a2a6c`.
