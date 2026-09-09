# TODO queue

Work waiting to be done, one `##` section per item. A worker routine takes items
off the top; the `feedback-triage` skill puts them on. Humans can edit it by hand
too, and should.

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

## SD-2 [P1] — Nobody has read and owned the new privacy policy

**Ask:** `pages/privacy.html` was rewritten against the code in `1ec24e0` and is
live. The false "no ad networks" claim is gone and every statement traces to a
file. What is left is the half an agent cannot do: a person has to read it and
accept it as their own.

**Done when:** The owner has read it end to end and said so.

**Hints:** Three passages deserve a deliberate decision rather than a factual
check.

- **The opening line says the app is not released yet.** True today, false on
  launch day, and nothing catches it. There is a `DELETE THIS ON LAUNCH DAY`
  comment on the paragraph.
- **The deletion paragraph** states plainly that the install identifier is the
  only key on our records, that the app never shows it to the player, and that a
  deletion request therefore cannot be matched to anything. That is honest and it
  is also a product gap the page now commits us to closing. Play's Data safety
  form asks the question directly.
- **Analytics have an operator kill switch and no in-app opt-out.** The page says
  so rather than implying a choice the player does not have.

`pages/terms.html` was left alone. Nothing in it is false, but it says nothing
about purchases or ads, which is a gap rather than an error.

## SD-3 [P1] — A Settings toggle tells a screen reader "on" without saying what is on

**Ask:** Every toggle row in Settings exposes an unnamed `checkable` node beside
its label, so a screen reader announces the state with nothing naming the
setting it belongs to.

**Done when:** Each toggle is one node carrying both its name and its state, and
an accessibility dump shows no unnamed checkable node in Settings.

**Hints:** Found on an API 36 emulator while investigating the item this
section used to hold. The switch is rendered by the shared list item, so the fix
is in `libraries/ui`'s list components and reaches every toggle at once rather
than in `features/settings`.

The board is the worked example of the right shape: `BoardCellLabels.kt` puts
identity in `contentDescription` and state in `stateDescription`, on one node.

**This replaces the original SD-3, which was my mistake.** I reported that a
crossed-off square is invisible to a screen reader, having seen identical
`drive.py text` output with the assist on and off. The board has announced all
five cell states since `bc81aa5`, through `stateDescription`. `drive.py` reads
only `text` and `content-desc` from a `uiautomator` dump, and
`stateDescription` is not in that attribute set at all: it is readable only by
an accessibility service. So the tool is structurally blind to exactly the half
of the label that carries state, and identical output was never evidence of
anything. `scripts/dev/drive.py` now says so in its docstring.

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

## SD-7 [P1] — `docs/store/data-safety.md` is stale, and a store form gets filled from it

**Ask:** Three of its findings no longer match the code. That file is the input
for Play's Data safety form and Apple's App Privacy questionnaire, so a stale
claim there becomes a false declaration to a store rather than just a wrong doc.

**Done when:** Every claim in it has been re-derived from the current code, and
anything already fixed is marked fixed rather than left reading as outstanding.

**Hints:** Found while writing the new privacy policy (SD-2), and each one
verified directly:

- **§2.10 and §7.2** say `android.permission.CAMERA` is declared. It is not.
  `apps/compose/src/androidMain/AndroidManifest.xml` declares no permissions at
  all; the merged manifest's set comes entirely from bundled libraries.
- **§7.1** says `allowBackup="true"` contradicts the Settings copy. It is
  `android:allowBackup="false"` now, with the reasoning in a comment at
  `AndroidManifest.xml:5-14`. That was the file's highest-value finding and it
  is already done.
- **§2.5** says `FeedbackRepositoryImpl` passes neither screenshots nor email.
  It passes `screenshots: List<ByteArray>` and `includeLogs: Boolean` now
  (`libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/FeedbackRepository.kt:40-55`).
  Email is still never passed, so that half stands.

The new `pages/privacy.html` was written against the code rather than against
this file, so it is the more trustworthy of the two. Reconcile toward it, and
where they disagree, check the code rather than picking one.

## SD-8 [P1] — A streak day should be earned by finishing any board, not only the daily

**Ask:** The owner, on 2026-09-09: "Is it normal for the streak to be only the
daily challenge thing? I kinda thought a streak would've been 'did you play at
all' specifically did you finish any single board."

Decided: **any finished board keeps the streak alive**, campaign or daily.

The flaw in the current design is concrete rather than theoretical. A player who
clears ten campaign levels today and does not open the daily still loses their
streak, which reads as the app being broken rather than as a rule. It also makes
the campaign, which is the bulk of the game, contribute nothing to the one
retention mechanic. Duolingo, which the owner named as the model, counts any
lesson.

**Done when:** Finishing any board records today as a streak day, the streak
page and the flame badge reflect it, and the daily still pays its own separate
reward so it keeps a reason to exist.

**Hints:** The streak is currently derived entirely from the `daily_result`
table. `libraries/progress/impl/.../streak/StreakRepositoryImpl.kt:63` builds
`summary()` from `dao.all()` on that table alone, and
`libraries/progress/src/.../streak/StreakSummary.kt` documents the rebuild.

So this is a data-model change, not a copy change: a streak day needs a source
that campaign clears also write to. Options are a new table of active days, or
folding campaign completions into the same rows the streak folds over. Decide
deliberately and say which, because `DailyRepository` reads the same table for a
different question and must not start seeing campaign rows as daily results.

Two things that were priced against daily-only difficulty and should be
re-examined once this lands, though neither has to change in the same commit:
the streak freeze and the streak restore. A streak that is much easier to keep
makes both cheaper in real terms.

**Blocked** until the agents working `features/streak/impl` and
`features/game/impl` have landed; both are in the way.
