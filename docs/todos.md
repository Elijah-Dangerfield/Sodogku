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

## SD-111 [P1] — Rethink what giving up on the daily board leaves behind

**Ask:** "I gave up on today's board and when I revisited it it just shows me
this we might need to rethink what giving up means . I don't think the user
should be able to give up. I don't know if there should be a total failure
state. I think you should probably always be able to just start from the
beginning. I don't know, but let's rethink this and see how we could probably
improve the user experience"

**Done when:** Returning to a daily board after running out of bones no longer
dead-ends. Whatever replaces it, there is a way back into the same puzzle from
the start.

**Hints:** The attached screenshot is the dead end: a dimmed board behind an
"Out of bones for today / Sep 9" card whose only control is a LEVELS button.
Streak reads 0, score 1.8K, bones 0/6. Related: SD-115 is the report that the
LEVELS button on this card does nothing, so the card is currently a dead end in
both senses. The owner floats a failure page in the SD-115 report too.
Sentry https://elijah-dangerfield.sentry.io/issues/SODOGKU-5 · session
95dd30d1-9ca1-4614-816c-a71e02b191d8 · 2026-09-10

## SD-112 [P2] — Bigger timer text, and a booster pulse you actually notice

**Ask:** "let's make the timer text just slightly bigger and let's make the
pulsing of those buttons on the bottom a little bit more noticeable. like I
wanna see them With the icon shaking in The middle a little bit more. And I
honestly haven't been seeing them that much. We probably need a better algorithm
for deciding when they should pulse."

**Done when:** The timer under the board is a step larger, and the booster
buttons pulse with visible icon movement.

**Hints:** Three asks in one report, filed as one. The other two are the pulse
animation itself (the owner wants the icon shaking, not just the button
breathing) and the rule that decides when a booster pulses at all, which he says
fires too rarely to see. Filed from GameRoute.
Sentry https://elijah-dangerfield.sentry.io/issues/SODOGKU-6 · session
95dd30d1-9ca1-4614-816c-a71e02b191d8 · 2026-09-10

## SD-113 [P2] — Hold back the no-starting-dog puzzles, and say so the first time

**Ask:** "i've noticed that we pretty quickly start giving us those puzzles that
don't have a starting dog. I don't know if that should be the case to be honest
I think that should probably start much later in the year's progress. And maybe
the first time that they see one we should let them know that there's no
starting dog on purpose and that they should be able to deduce it. Like maybe a
little tool tip or something"

**Done when:** Boards with no pre-placed dog start appearing later in the
progression than they do now, and the first one a player meets says out loud
that the empty start is deliberate.

**Hints:** Two parts, the ramp and the one-time tooltip. "the year's progress" is
dictation for the level progression. The generator's starting-dog decision and
the difficulty ramp in `libraries/scoring` are where to look first.
Sentry https://elijah-dangerfield.sentry.io/issues/SODOGKU-7 · session
95dd30d1-9ca1-4614-816c-a71e02b191d8 · 2026-09-10

## SD-114 [P2] — Bring the achievements page in line with the rest of the app

**Ask:** "The UI of the achievements page honestly isn't really in a line with
what we're going for. Let's see if we can make this a little bit more In line
with the rest of the app. Think Duolingo"

**Done when:** The achievements screen reads as the same app as the board and
the outcome sheets.

**Hints:** Filed from AchievementsRoute. The screen is
`features/achievements/impl/.../AchievementsScreen.kt`. "Think Duolingo" is the
reference; `docs/reference/meowdoku.md` has the existing competitor look notes.
Sentry https://elijah-dangerfield.sentry.io/issues/SODOGKU-8 · session
95dd30d1-9ca1-4614-816c-a71e02b191d8 · 2026-09-10

## SD-115 [P1] — The Levels and Start over buttons do nothing

**Ask:** "both the levels button and the start over button are not doing
anything right now", and from a minute earlier: "I just clicked on Level levels,
but it did nothing."

**Done when:** Both buttons navigate. Tapping Levels opens the level list and
tapping Start over restarts the puzzle.

**Hints:** Two reports, forty seconds apart, same session, same defect. The
second one names both buttons. Start at `GameOutcomeSheets.kt` in
`features/game/impl` and the routes it dispatches. Worth checking against
SODOGKU-3, an unresolved `IllegalStateException` about popping
`AchievementsRoute` when it is not the top of the back stack, filed from the
same period. The first report also asks two design questions that are not this
item: whether Levels belongs on that dialog at all, and whether there should be
a failure page with a restart-from-zero, which is SD-111.
Sentry https://elijah-dangerfield.sentry.io/issues/SODOGKU-A and
https://elijah-dangerfield.sentry.io/issues/SODOGKU-9 · session
95dd30d1-9ca1-4614-816c-a71e02b191d8 · 2026-09-10

## SD-116 [P1] — Five paws looks unearnable

**Ask:** "I don't know how I possibly could've earned five paws. I did this in
five seconds with no mistakes", and a minute later: "yeah, I am crushing these
puzzles, but still not getting five paws"

**Done when:** A clean, fast solve awards five paws, or the thresholds are shown
to be right and the reason a five-second flawless solve falls short is written
down.

**Hints:** Two reports, eighty seconds apart, same session, both from GameRoute.
The standing thresholds live in `libraries/scoring/.../Standing.kt` and
`ScoringConfig.kt`, wired up in
`features/game/impl/.../ConfiguredScoring.kt`. Note the history: SD-88 through
SD-92 came out of a review of this same slice and found a compression bug in
`Standing.Sharp`, so check whether the top band is reachable at all for the
sizes being played rather than only reading the constants.
Sentry https://elijah-dangerfield.sentry.io/issues/SODOGKU-B and
https://elijah-dangerfield.sentry.io/issues/SODOGKU-C · session
fd9affc0-cc4e-4386-a0d6-5eecef3753e0 · 2026-09-10
