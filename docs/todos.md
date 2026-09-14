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

## SD-117 [P1] — Teach feedback triage to check the reported build against the log

**Ask:** Every feedback carrier event carries `commit_sha`, and `AppTelemetry`
puts it there on purpose: its comment says the provenance is so triage can tell
"whether it's already fixed on a later commit". The `feedback-triage` skill never
reads it. The 2026-09-14 pass filed SD-111 through SD-116 from reports against
builds 160 to 177 commits behind `main`, and four of the six were already fixed.

**Done when:** the skill's read-the-report step requires resolving `commit_sha`
against the log and saying how far behind the report is, and its file-it step
requires checking whether the behavior still exists on `HEAD` before writing an
item. An item filed anyway carries the distance in its provenance line.

**Hints:** The skill is `.claude/skills/feedback-triage/SKILL.md`. Step 2 also
still tells the reader to pull the Sentry feedback twin for the text, which is
not reachable from the Sentry MCP for this project — the text is on the carrier,
in the `feedback_message` extra and the `feedback.txt` attachment, both added in
26fcba1. Fix both instructions in one pass. `docs/feedback-log.md` has the
tooling notes from that run.

## SD-118 [P2] — Delete the daily failure state's leftovers

**Ask:** SD-111 stopped `toResult` reading a `Failed` row, so nothing reaches the
dead-end card any more. `DailyCardState.Failed` in `libraries/ui` and the
`daily_out_of_bones` string now have no live caller, and the `DailyOutcome.Failed`
branches in `LevelDrawer.DailyCardSlot` and `DailyStreak.missedDayBefore` survive
only for `when` exhaustiveness.

**Done when:** the unreachable state and its copy are gone, or there is a written
reason to keep them.

**Hints:** Left in deliberately rather than removed during SD-111, because a
cross-module deletion while four other agents were live in the tree was the
riskier half of the change. `DailyOutcome.Failed` itself must stay in the enum:
dropping the name makes `toResult` fail to *parse* those rows, which reaches the
same outcome by accident and costs the parse failure its own meaning. Four
`DailyStreakTest` cases now pin behavior for an input the repository cannot
produce; decide whether they are a guard on the pure fold or dead weight.

## SD-119 [P2] — A `Surface` overload that takes a semantics label

**Ask:** `BadgeTile`, `SpotlightCard`, `GameOutcomeSheets.OutcomeLayout` and
`LevelDrawer.LevelRow` each hand-roll `bounceClick + clearAndSetSemantics + clip
+ background + border + padding`, which is the body of the design system's
`Surface`. Four copies of one thing.

**Done when:** those call sites use `Surface`, or the duplication has a reason
written down next to it.

**Hints:** The reason they are not `Surface` today is real and was checked on a
device: `Surface(onClick=)` applies its `bounceClick` *inside* the caller's
modifier, so a `clearAndSetSemantics` passed in sits above the clickable and
clears the click action along with the labels. `bounceClick`'s own KDoc records
that two tidier variants were tried and neither reached the tree. So the fix is
an overload that takes the semantics label as a parameter and applies it in the
right order, not a call-site conversion. Found while auditing the achievements
screen for SD-114.
