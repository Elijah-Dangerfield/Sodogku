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

## SD-10 [P1] — The game makes no sound

**Ask:** Haptics ship (`AppCache.hapticsEnabled`, `rememberHaptics`), audio does
not exist anywhere: no clips, no player, no `soundEnabled`, and SPEC 11 still
lists the Settings row as outstanding. Audio is one of the two things
Meowdoku's reviewers praise unprompted, the other being its hint.

**Done when:** Dog placed, strike, level win, praise sting, button tap and
achievement unlock all play; a Settings row silences them; and nothing plays
over the iOS silent switch.

**Hints:** SPEC 20 lists the six clips under "Art and audio" and they are still
unordered. Follow the haptics shape exactly: a flag in `AppCache`, a toggle in
Settings, and playback at the screen rather than in the ViewModel.

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

## SD-14 [P1] — Share the daily only, and without the grid

**Ask:** Owner, 2026-09-09: only the daily should be shareable, and the text
should not carry the region layout. Today `ShareButton` is on every win sheet
and `ShareResult` carries `regions`.

**Done when:** A campaign win has no share control, the daily's share text is
the date, time, score, paws, bones and streak with no grid at all, and
`ShareResult` has nowhere to put a layout.

**Hints:** `features/game/impl/.../GameOutcomeSheets.kt:162`,
`libraries/sharing/.../ShareResult.kt`, `ShareText.kt`. Dropping the grid makes
the no-spoilers property trivially true instead of carefully arranged, and it
retires the `🔲` compromise for ten-region boards. Tests pin the emoji grid;
they go with it.

## SD-15 [P1] — Say the daily is waiting, on the button that opens it

**Ask:** Owner, 2026-09-09: a badge on the menu icon while today's daily exists
and has not been played.

**Done when:** The drawer button carries a dot while today has no result, and it
clears the moment the day is completed or forfeited.

**Hints:** `AchievementsButton` in `GameScreen.kt:442` is the pattern, including
the rule that there is no badge at zero. `DailyRepository.status()` already
answers the question. A dot is invisible to a screen reader, so the button's
label has to say it too.

## SD-16 [P2] — A QA panel that can move the day

**Ask:** Owner, 2026-09-09: "How can I test out streaks without actually playing
them?" Streak, freeze and restore are all built and none of them is reachable in
under a week of real calendar time.

**Done when:** A debug-only panel can move the date the daily and streak code
sees, write a result for an arbitrary past date, and clear the lot, and a
freeze offer and a restore offer can both be produced in one sitting.

**Hints:** The shake dialog is the way in: `ShakeDialogEntryPoint` already
gates the network inspector on `BuildInfo.isDebug` and is the established place
for this. The streak folds out of stored rows (`DailyStreak.kt`), so seeding
rows is enough and a second source of truth would be a bug. Two rules to respect
rather than route around: future-dated results are deliberately invisible to the
walk, and the skip allowance keeps a high-water day on purpose.

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

## SD-24 [P2] — The Pro upsell reads like a shakedown, and looks flat

**Ask:** Owner, 2026-09-09, on the redesigned paywall.

Copy first, because it is the part that actually matters:

> "the copy on the upsell kinda sucks. Like saying 'and Sodogku stops asking
> you for anything' is like saying 'hey give us money and we will stop
> bothering you'. Maybe the header could be better too. Could just be 'Unlock
> Sodogku Pro' and the other text could be 'Here's what you get with pro:' just
> keeping it super simple."

They are right, and it is worth naming why: the line frames the free product as
a nuisance the player is paying to switch off. That is an argument for
resenting the app, printed on the screen asking for money. Replace it. The
suggested header and lead are deliberately plain and should be taken more or
less as given rather than "improved" into something clever.

Visual, same message:

> "we should likely use that paw svg and we need a touch more contrast between
> the background yellow and paw yellow. Also pick a different dog still."
> "would be really cool if we could make the status bar yellow when the upsell
> is up. and maybe we need to make the back nav an X since its a slide up type
> of thing."

**Done when:** The copy no longer implies the app pesters you; the bullets use
the shared paw; the paw reads clearly against the amber slab; a different
`DogPose` is chosen; the dismiss affordance is an X rather than a back chevron;
and the status bar is amber while the sheet is up and back to normal after.

**Hints:** `features/paywall/impl/.../PaywallScreen.kt`. The paw is `Icons.Paw`
in `libraries/ui/.../components/icon/` (added by the booster-row work — confirm
the name before using it).

Contrast: the bullets currently draw at an amber close to the slab's own amber.
Note the slab already had one contrast fix — the type on it is Brown900 rather
than the white the mockup showed, because white measures 1.79:1 on that amber.
Whatever colour the paw takes, measure it; `Colors.kt` documents its ratios and
there is a `NoRawDesignValues` rule.

The status bar is platform-specific. Find how the app sets system bar
appearance today before adding a second mechanism, and make sure it is restored
when the sheet closes **by any route** — dismiss, back, purchase, or a process
death with the sheet open. A status bar left amber over the board is a worse
bug than the one being fixed.

`DogPose` options are in `libraries/ui/.../components/dog/`.

