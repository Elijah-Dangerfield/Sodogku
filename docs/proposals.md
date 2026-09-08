# Feature proposals

Punch-list item P9: propose features, argue both sides, lean conservative, and say for each
one whether it belongs in remote config or in the binary.

Ordered by how strongly I recommend it. Each entry states what it is, the case for, the case
against, where it sits under the SPEC 4.1 split (*config owns numbers and switches, the binary
owns content and logic shape*), and a verdict with a confidence level.

The bias throughout is toward things the code already knows and never says, and away from
anything that adds a system. Three of the nine below are recommended against, and there is a
shorter rejected list at the end. A list where everything is a yes is not a set of
recommendations.

**Three things I found while writing this** that are not proposals, because they are gaps
rather than ideas:

- **All fourteen `scoring.*` config keys are read by nothing.** `GameViewModel` calls
  `Scoring.placement`, `Scoring.complete` and `Scoring.paws` without a `ScoringConfig`
  argument, so every one resolves to `ScoringConfig.Default`. Proposal 1.
- **The in-progress board is not persisted.** SPEC 13.3 describes it and BUILD-PLAN C5 records
  it as delivered. There are five `@Entity` classes in the app and none of them is a board
  snapshot. Proposal 5.
- **Clearing level 500 closes the app.** `nextLevel()` finds no level 501, sends
  `GameEvent.NavigateBack`, and the entry point maps that to `router.goBack()` on the start
  destination. Proposal 4.

---

## 1. Let the scoring dials actually turn

**What it is.** `ScoringConfig` is a data class with fourteen fields and a `Default` built from
compile-time constants. `ScoringConfigValues.kt` declares all fourteen as `ConfiguredValue`
classes, `FallbackConfigMap` carries all fourteen, `config-manifest-registry.json` lists all
fourteen, and the admin console renders all fourteen. Nothing joins the two halves.
`GameViewModel.kt:533`, `:605` and `:611` call into `Scoring` with the config parameter
defaulted. Build a `ScoringConfig` from the injected config values, hold it on the ViewModel,
and pass it at those three call sites plus wherever par is read.

**The case for.** This is the failure `decisions.md` has already recorded twice under different
names. "The kill switch was pointed at a key nothing reads" was about `app.minSupportedVersion`;
"both daily flags are read, so neither becomes a switch nothing listens to" was about
`daily.enabled`. Both entries say the same thing: a control an operator can see and change, that
no client consults, is worse than no control, because it looks like it worked. Every scoring key
is in that state right now. Someone will eventually widen `comboMax` in the console during a live
tuning pass, watch the histogram not move, and spend a day looking for the bug in the wrong
place.

It also decides the cost of every other scoring change on this list. SPEC 4.3 lists the paw
fractions and the speed window as config precisely so that "what counts as a three-paw clear" can
be retuned against real play data. C2 went to the trouble of keeping par and the paw thresholds
out of the pack for the same reason, and recorded it as a decision. That whole argument is
currently theoretical. Wire this and retuning is a console edit; leave it and retuning is a store
release, which means it will not happen.

The change is small and it is in the direction the code already points: every `Scoring` function
already takes the config as a parameter, so nothing needs restructuring.

**The case against.** It converts a set of compile-time constants that are currently guarded by
`ScoringConfig`'s `init` block and by 24 tests into values a console write can set. The `init`
block does validate (positive point values, `threePawFraction >= twoPawFraction`, fractions in
0..1), but a `require` that fires means a thrown exception on the scoring path rather than a
fallback, and SPEC 4.2's rule is that a config outage produces a playable game. So this needs a
deliberate answer to "what happens when the eight numbers resolve individually and the resulting
combination is invalid", and the honest answer is probably to catch and fall back to
`ScoringConfig.Default` rather than to let a `require` reach a player mid-attempt.

There is a second, quieter cost. Scores are compared against a par derived from the same
coefficients, and `best_paws` is stored while par is not. Once the coefficients are live, a
console edit re-rates every future attempt against a bar that a player's stored records were not
measured on. That is already true in principle (it is why par is derived) but it becomes true in
practice the moment the keys are live, and it is the reason proposal 2 has to be careful about
what it shows.

**Config or binary.** Neither, and that is the point: this is the wiring that makes fourteen
already-declared keys real. **No new keys.** The formula shape stays in the binary, as SPEC 4.4
says.

**Verdict: do it, and do it before anything else on this list. High confidence.** It is the
cheapest item here, it fixes an established failure mode rather than adding a feature, and it
turns proposals 2 and 7 from code changes into experiments.

---

## 2. Show the paw target on the win sheet

**What it is.** `Scoring.parScore(size, difficulty, config)` is public and has exactly one caller,
`Scoring.paws`, twelve lines below it in the same file. The number that decides the player's
rating is computed and discarded. On the win sheet, under the existing `PawRating`, draw the
score against the two-paw and three-paw lines: a thin bar with the player's total on it and the
next paw marked. Nothing else changes.

**The case for.** SPEC 1.3 built the rating as a fraction of par on purpose, so that speed and
combo would be legible in the one number the header shows. Hiding par makes it illegible in the
opposite direction: the player watches the score climb during the attempt, gets two paws at the
end, and has no way to connect the two. The win sheet is the moment they have the app's full
attention and the moment they decide whether to hit Next or Retry, and it currently answers
"how did I do" with a rating whose scale is a secret.

It costs nothing to a player who ignores it. It is one derived `Int` on `GameState`, one
composable, no persistence, no new module, no new state machine. And it makes the campaign's
existing replay affordance mean something: a completed level in the drawer already shows its paw
rating, so "two paws" becomes a task instead of a fact.

**The case against.** This is the strongest objection on the page and it is not about cost.

The shortfall is only actionable one way. On a 10x10 at difficulty 3, par is 30,510. One surviving
bone is worth 1,750 points. The speed multiplier spread across ten placements is worth 8,160. So
"you were 1,240 short" resolves, for any player who thinks about it, to *guess faster*. The
double-tap commit is the fast action and the single-tap mark is the slow one, and C4a's whole
decision was that the safe gesture should be the cheap one. Surfacing par puts a number on the
board that rewards skipping the marking step. The game already has this incentive through the
score and the praise text, but making it precise makes it optimisable, and an optimisable casual
puzzle is a different game from a relaxing one.

Second: par moves and `best_paws` does not. Once proposal 1 lands, a console edit to
`comboStep` changes what the bar says about a level whose stored rating was earned under the old
coefficients. There is nowhere to reconcile that, by the deliberate decision not to bake par into
the pack.

Third: paws gate nothing. `LevelState`, `unlockedThrough` and the drawer never read `bestPaws`
except to draw it, and only two achievements consume it. Telling a player they fell short of a
thing that unlocks nothing creates an obligation with no reward behind it.

**How to build it so those hold.** Show the *target* on the current attempt's sheet, not the
shortfall, and not on stored records. A bar with the three-paw line marked says the same thing
without the accusation, and a player who does not care reads it as decoration. Do not put par in
the drawer, where it would be a stale number against a stored rating.

**Config or binary.** Binary. It is a layout and a derivation over numbers that are already
tunable. **No new keys.** The thresholds are already `scoring.twoPawFraction` and
`scoring.threePawFraction`, and the whole point of proposal 1 is that this reads them.

**Verdict: do it, in the target-not-shortfall form. Medium-high confidence.** The speed-incentive
objection is real and is the reason for the softer framing rather than a reason to skip it.

---

## 3. Stop the sniff selling squares the player already crossed out

**What it is.** Two changes to one booster. First, a bug: `useSniff` filters the engine's output
against `state.autoMarks + state.placedCells + state.wrongGuesses` (`GameViewModel.kt:1041`).
`manualMarks` is not in that set, so a player who has crossed four squares by hand can spend a
sniff and watch it light up those same four squares. Add the set. Second, and optional: when the
engine's first step is an elimination, name the reasoning in one line under the spotlight instead
of the generic "These squares are ruled out".

**The case for.** The first half is a straight violation of a rule the project has already
enforced once. SPEC 1.5: "a booster is never spent for nothing", and `decisions.md` has a whole
entry ("three tests that passed on `emptyList()`") about a sniff-spent-for-nothing bug found on a
device and the tests that could not catch it. This is the remaining instance and it is worse than
the one that was fixed, because it punishes precisely the player who is playing the way the game
wants: manual X-marking on top of auto-mark is, per SPEC 1.2, "what lets a careful player record
their own deductions". The careful player is the one who gets nothing for their charge. The fix
is one set in one union.

The second half is a promise the spec makes and the code does not keep. SPEC 1.5 says the sniff
"shows the technique that found them". `Deduction` carries `technique` on both variants and
`ruledOutCells` throws it away, returning a bare `List<Int>`. There are five techniques total, so
this is five strings, not a glossary. A hint that says *why* is the difference between a hint that
unsticks a player once and one that teaches them to unstick themselves, and teaching is the only
thing that makes a 500-level curve survivable.

**The case against.** The technique half does not have a well-defined answer, and this is not a
detail. `ruledOutCells` runs `DeductionEngine.nextStep` in a loop until it has accumulated up to
24 candidate cells, and different steps in that loop use different techniques. Worse, the loop
handles a `Deduction.Place` by calling `grid.place(step.cell)`. So some of what a sniff lights up
was ruled out by a dog the engine placed and the player cannot see. Naming that honestly means
saying "I worked out where a dog goes and these follow", which hands over the answer the design
refuses to hand over. Naming it dishonestly means picking one technique from several and hoping.

The copy is the other problem. The five techniques are `LastCandidateInGroup`,
`AdjacencyConfinement`, `GroupConfinement`, `NakedSet` and `Contradiction`. Writing those for a
Meowdoku audience without either lying or sounding like a sudoku manual is real work, and "naked
set" told to a casual player does not teach, it announces that the game is for somebody else. The
current copy is honest, sufficient, and one string.

**How to build the second half so it survives.** Report the technique of the **first** step only,
and only when that step is an `Eliminate`. That is well-defined, it can never leak a placement,
and when the first step is a `Place` the UI falls back to today's generic line. It changes
`ruledOutCells`'s return type from `List<Int>` to a two-field result, which is contained inside
`:libraries:puzzle` and its two call sites.

**Config or binary.** Binary: the technique names are copy and the filter is logic. One new key
falls out of reading this code, though, and it is not really about this proposal:
`SniffRevealLimit = 4` and `StarterDogThroughLevel = 25` are compile-time constants in
`GameViewModel`, and both are exactly the "threshold or cap" the SPEC 4.1 rule puts in config
next to `boosters.refillTo` and `progression.lookaheadCount`.

| Key | Default | What it does |
|---|---|---|
| `boosters.sniffRevealCells` | 4 | Squares one sniff lights up. |
| `progression.starterDogThroughLevel` | 25 | Last level that opens with a free dog placed. |

`DoubleTapWindowMs = 320` is deliberately **not** on that list. It is a number, but a console
write of 3000 there would break the core gesture on every device that fetched it, and the fail-open
rule cannot help with a value that is valid and wrong.

**Verdict: fix the filter now, high confidence. Ship the technique line only in the
first-elimination-only form, medium confidence.** The filter fix is a bug, not a feature, and
should not wait for a decision about the other half.

---

## 4. Finishing the campaign should not close the app

**What it is.** `nextLevel()` looks up `LevelPacks.campaign.byId(current.id + 1)`, gets null at
level 500, and sends `GameEvent.NavigateBack`, which `GameFeatureEntryPoint` maps to
`router.goBack()`. `GameRoute` is the app's start destination, so on Android the Next level button
closes the app. Add a fourth arrangement to `GameOutcomeSheets.kt` for the last level: the win, a
campaign total, and a button that opens the daily instead of a button labelled Next level that
quits.

**The case for.** The `TopDog` achievement fires at 500 levels cleared, so the unlock toast does
appear over the sheet. Then the button underneath it closes the app. That is the reward for
clearing 500 verified boards, and it is the single cheapest emotional win left in the project.

It has somewhere to send them, which is the part that matters commercially. The daily is what
SPEC 2 calls the strongest retention mechanic in the genre and it is the only loop that keeps
earning after the campaign is spent. The most engaged player the app has is the one who most needs
to be told the daily exists, and today they are the one the app says goodbye to.

The components already exist in the file: `OutcomeLayout`, `Dog(pose = DogPose.Solved)`,
`PawRating` and `ShareButton` are all imported. The campaign total folds out of
`ProgressRepository.all()`, which the drawer already reads.

**The case against.** Nobody knows how many players see this screen, and the honest prior in this
genre is very few. SPEC 14's first dashboard, the level drop-off curve, is called "*the* metric
for a level-based puzzle game" and it does not exist yet: C9 is unstarted and there is no Grafana
endpoint. Building the least-viewed screen in the app before the most important dashboard is the
wrong order.

More seriously, "something at the end of the campaign" is unbounded in the worst place if it is
allowed to mean content. Appending levels 501 and up means regenerating `CampaignPackData.kt`,
re-running the verification test and the difficulty re-derivation test, and bumping
`LevelPacks.PACK_VERSION`, which BUILD-PLAN C2 warns "silently reassigns players' completed levels
to different boards" because progress is keyed on level id. That is a data-loss-shaped risk for a
reward almost nobody collects. And a second endgame loop would compete with the daily for the same
player.

**Config or binary.** Binary. It is a screen and a branch. **No new keys.**

**Verdict: do the sheet. High confidence. Reject anything larger.** The scope has to be exactly
one branch in one file. If it grows a mode, a new pack, or a second progression track, it has
stopped being this proposal.

---

## 5. Persist the in-progress board

**What it is.** SPEC 13.3 says the board, marks, lives, score, combo and elapsed time are saved
separately so that backgrounding mid-level resumes exactly, and calls its absence something that
"reads as a bug". BUILD-PLAN C5 records it as delivered. It is not there. `AppDatabase` is at
version 8 with five entities and none is a board snapshot; `GameState` lives only in the
ViewModel. Add the table, write on every state-changing action, clear it when the attempt ends.

**The case for.** The gap is worse than losing a board. Booster spends are persisted immediately
through `persistCounts`, so a player who force-quits or gets killed by the OS mid-attempt keeps
the two sniffs they spent and loses the board those sniffs paid for. That is the shape of a
complaint that reads as the app stealing from them, and there is no account and no server copy to
argue with.

It is also load-bearing for other things. Proposal 2's par bar and proposal 3's filter both assume
an attempt is a continuous thing. And the resume path is what `HintFinder`'s dead-end guard was
written for: `isDeadEnd`'s KDoc says outright that today's flow never produces an unwinnable
partial and that it is "one undo or restore-state feature away from mattering". The guard already
exists, so the landmine this would normally step on is already defused.

**The case against.** It is the only proposal here that touches the database, and the database is
where this project's most expensive near-miss lives. `RealAppDatabaseProvider` was building with
`fallbackToDestructiveMigration(dropAllTables = true)` until very recently, and the fix narrowed
the destructive range to versions 1 through 5 with auto-migrations above. A sixth entity is a
version 9 and Room will write the migration itself, so this is safe *today*, and it is one more
thing that has to stay safe.

The serialisation surface is not trivial either. A snapshot has to hold `Solution`, three cell
sets, lives, a `ScoreCard`, the elapsed clock and the tutorial step, and it has to survive a pack
regeneration, which `PACK_VERSION` exists to detect and which nothing currently checks. A snapshot
restored against a different board is a worse bug than no snapshot.

And the value is bounded. An attempt is three to five minutes. Retry is free and always available.
The daily has no loss here at all, because a force-quit mid-daily leaves the day unwritten and
therefore still playable, which the daily's own decision entry already accepts.

**Config or binary.** Binary. It is schema and logic. **No new keys.** Resist the temptation to
add a `features.resumeInProgress` flag: a kill switch on a persistence path leaves rows written
that nothing reads.

**Verdict: do it, and correct SPEC 13.3 and BUILD-PLAN C5 in the same change so the docs stop
claiming it. Medium-high confidence.** The confidence is not higher only because it is the one
item here with a schema bump in it.

---

## 6. Make the board usable with a screen reader

**What it is.** `BoardCell` has no semantics at all. It takes input through raw
`pointerInput { detectTapGestures }`, so it is not an unlabelled control, it is not a control.
TalkBack and VoiceOver see up to a hundred anonymous boxes. The header's menu and settings icons
pass `contentDescription = null`. Give the cell a semantics block built from the arguments it
already receives, give it a custom accessibility action for committing a guess, summarise the
auto-mark cascade in one announcement, and label the two header buttons.

**The case for.** SPEC 16 opens by saying the core mechanic is colour and that accessibility is
therefore "a design constraint, not a checkbox", then addresses only colour vision. This is a pure
logic puzzle with no reflex component and no rendering requirement, which is exactly the genre
screen-reader users play. The whole board state is region, row, column and one of four cell
states, and every one of those is already a parameter on the composable.

It costs a sighted player nothing, and it is far cheaper now than later. The design system already
does this elsewhere: `Surface`, `Dialog` and `BasicButton` all set semantics, and `IconResource`
already carries a `contentDescription` field that the two header call sites pass null to. C12 owns
this chunk and has not run.

**The case against.** The obvious cheap version ships something worse than silence, and this is
the part worth reading twice.

Committing a guess is a second tap on the same cell within 320ms, recognised in `GameViewModel`
because `BoardCell` deliberately refuses to register `onDoubleTap`. Under explore-by-touch on
either platform, the user's double tap is consumed by the screen reader and delivered to the app
as a single activation. `detectTapGestures` never sees two taps. So a board with content
descriptions and nothing else is a board a screen-reader user can mark and unmark and can never
place a dog on. That is a worse outcome than an unlabelled board, and it is the sort of thing a
store accessibility scan finds.

Doing it properly is a project rather than a semantics block. It needs a custom action for
placement, per-cell announcements that do not become a firehose (one placement changes up to 30
cells through auto-mark), and an answer for the Focus system, which draws with `BlendMode.Clear`
and is invisible to semantics. The sniff, the last-bone warning and the tutorial coach marks all
ride on it, and the tutorial's `targetsAreLive` mode deliberately disables every other cell, which
under a screen reader is 99 focusable inert elements with no explanation.

None of it is verifiable in this repo. There is no instrumented or UI test target, only
`commonTest`, and iOS has never been run at all.

**Config or binary.** Binary. Semantics are part of the component. **No new keys.** A flag here
would only ever be used to turn accessibility off.

**Verdict: do it, scoped as custom-action-plus-semantics, not as content descriptions alone.
Medium confidence.** The cheap version is the one to refuse. Schedule it inside C12 with the
colour-blind and reduce-motion work, and be honest in the chunk notes that it cannot be verified
here.

---

## 7. Scale the speed window with grid size

**What it is.** `scoring.speedWindowMs` is a flat 8,000ms at every board size. Replace the flat
window with `base + perSize * size`, so a 10x10 gets a longer runway than a 4x4.

**The case for.** The asymmetry is real and it runs backwards. Working from the shipped defaults:
three paws on a 4x4 permits roughly 7.1 seconds per placement, and on a 10x10 roughly 4.5. The
bigger, denser, harder board demands that the player think *faster*. The mechanism is the
completion bonus, whose share of par falls from about 47% on a 4x4 to about 22% on a 10x10, so the
fixed cushion that forgives slow play on a small board evaporates on a large one. Because
`difficultyBonusRate` inflates that bonus, the *easiest* 10x10 in the pack is the most
time-pressured board in the game.

**The case against.** The proposed fix does not do what it looks like it does, and this is the
argument that changed my verdict.

`parScore` multiplies by `speedMaxMultiplier` and never reads `speedWindowMs`. So widening the
window raises every achievable score on a big board while par stays exactly where it is. Push the
10x10 window out far enough to buy the same 7.1 seconds a 4x4 gets and the three-paw threshold is
now met by players who were scoring two, across the 320 levels at 8x8 and above. That is not
equalising the difficulty of three paws, it is a grade increase on most of the campaign. The
symmetric half is worse: tightening the 4x4 window makes paws hardest in the tutorial band, which
BUILD-PLAN C2 already identifies as where a puzzle game loses its first session.

`ScoringConfig`'s KDoc names this exact failure: "the multipliers have to spread *wide* enough for
the paw thresholds to mean anything... Compressing the range is the failure mode to watch when
retuning these". The first tuning pass made one paw unreachable. This one makes three paws cheap.

**Config or binary.** The formula shape is binary, the coefficient is config. If this is ever
shipped, ship it as an inert key first:

| Key | Default | What it does |
|---|---|---|
| `scoring.speedWindowPerSizeMs` | 0 | Added to `scoring.speedWindowMs` per grid row. 0 reproduces today's behaviour exactly. |

A default of 0 means shipping the code changes nothing, and the experiment is a console write.
`ScoringConfig.init` needs a matching non-negative guard, since it already guards
`speedWindowMs > 0`.

**Verdict: do not ship the retune. Ship the observation. Low confidence in the fix, high
confidence in the problem.** The right sequence is proposal 1, then C9's difficulty-calibration
dashboard, then look at whether median clear time per size actually says what the arithmetic says
it should. Retuning a scoring curve against a spreadsheet is how the first pass got it wrong.

---

## 8. A local daily-challenge reminder

**What it is.** An opt-in local notification, one per day at a player-chosen hour, cancelled if
the day's board is already done. No server, no push, no account. A settings row next to the
existing toggles.

**The case for.** SPEC 2 calls the daily the strongest retention mechanic in the genre and calls
the streak freeze "the single most reliable ad impression in the app". Both depend on the player
remembering, and nothing reminds them. A streak breaks in silence: the player finds out on
Wednesday that Tuesday cost them the number they were proud of, and the freeze offer, its ad
impression and the habit all go with it.

It does not touch a single non-goal. SPEC 18 rules out accounts, cloud save, social features and
server-delivered packs; a local notification needs none of them. `DailyStatus` already carries the
done state and `resetsIn`, and `Permission.Notifications` and a `PermissionManager` interface
already exist in `:libraries:sodogku`, with an iOS implementation in
`apps/ios/iosApp/Platform/PermissionManager.swift`.

**The case against.** It is the only item on this list that is a new system on both platforms, and
it collides with an existing decision rather than merely costing money.

The plumbing first. There is no Android implementation of `PermissionManager`, no
`POST_NOTIFICATIONS` in the manifest, no channel, no scheduler, no `BOOT_COMPLETED` reschedule, and
no route registers a deep link even though the `sodogku://` filter exists. The iOS half exists on a
platform that BUILD-PLAN says has never been run and whose Swift files it calls "unverified
drafts".

The architectural collision is sharper. Any reminder worth sending says something about the
streak, and the streak is deliberately not stored: "there is no stored streak... a derived number
cannot drift", and the entry explains that a counter has no witness on a device with no server
copy. A notification composed at schedule time carries a day-old copy of exactly the counter that
decision refuses to keep, on the one surface in the app that cannot correct itself. Related:
`DeviceTimeZone` is re-read on every call because the zone changes while the app is running, and a
scheduled fire time is the one thing that cannot re-read it. A player who flies east gets their
reminder at the wrong hour on the day they are most likely to break a streak, which is the day the
freeze exists for.

It also outruns the kill switch. `DailyStatus.enabled` is `daily.enabled && features.dailyChallenge`
and is checked at the point of use. A notification queued yesterday fires into a disabled feature
and opens a drawer with no card in it.

And SPEC 7.1 is still open. Asking for notification permission in a cute dog game is exactly the
surface that draws a Play Families review flag on a build that has not yet declared its target
audience.

**Config or binary.** The scheduler and the copy are binary, the switches and the hour are config.

| Key | Default | What it does |
|---|---|---|
| `features.dailyReminder` | false | Rollout flag. Dark until deliberately turned on. |
| `daily.reminderDefaultHour` | 19 | Local hour offered first. The player's own choice overrides it and is stored in `AppData`. |

Read it alongside `daily.enabled` and `features.dailyChallenge`, the way `DailyStatus.enabled`
already does, so a disabled daily cannot have a live reminder.

**Verdict: not now. Medium-high confidence.** The value is the highest on this list and so is the
cost. The preconditions are the 7.1 kids-versus-general-audience decision, an iOS build that has
actually run, and an answer for composing a message about a number the app deliberately does not
store. Revisit after C13.

---

## 9. Show the designed difficulty on the level row

**What it is.** Every `LevelDefinition` carries `difficulty` and `LevelRow` already receives the
whole definition. Render it as a 1-to-5 indicator next to the `NxN` label.

**The case for.** Difficulty is what the entire content pipeline is organised around. SPEC 1.7
defines it as deduction depth, the generator buckets on it, and the shipped ordering deliberately
sawtooths it so each band opens easier than the last one closed. A player scrolling the drawer sees
only grid size, which is monotonic, so the curve the pack was built to deliver is invisible. Level
200 and level 240 look identical and play nothing alike. It also explains scoring, since
`difficultyBonusRate` means a harder board of the same size pays a bigger completion bonus.

**The case against.** The pack cannot support the scale it would be drawn on.

Counting the shipped campaign after the C11 re-rating: 57 levels at tier 1, 228 at tier 2, 107 at
tier 3, 108 at tier 4, and **zero at tier 5**. A five-point gauge where 46% of levels share one
value and the top value never appears is not information. Inside the 10x10 band it is 1 / 34 / 46 /
29, so the hardest band shows the same three numbers as the 8x8 band. And `Difficulty` treats 5 as
`BEYOND_DEDUCTION`, a sentinel meaning the solver got stuck, so the top of the scale does not even
mean "hardest", it means "unrated".

The sawtooth is the reason to show it and also the reason it will read as a bug. Levels 1 and 2 are
tier 1, 3 to 10 are tier 2, and level 11 resets to tier 1 on a bigger grid. As a column of numbers
in a list, that is a curve to whoever designed it and a glitch to everyone else.

The number also does not mean what a player will read it as. It is deduction depth, not felt
effort: a tier-2 10x10 takes longer and hurts more than a tier-4 6x6. SPEC 14's difficulty
calibration dashboard exists precisely because designed and real difficulty diverge, and that
dashboard does not exist yet. Publishing the designed number before measuring the real one is
publishing the guess.

Finally, the row already carries `PawRating`, a three-symbol rating that counts up. A second
rating scale on the same row, counting up differently and meaning something else, is how a
500-row list stops being scannable. And a "4" on the level a nervous player was about to tap is a
reason not to tap it.

**Config or binary.** Moot. If it were built it would be binary (content), possibly behind a
`features.*` flag, and the existence of a flag would be a sign nobody was confident.

**Verdict: no. Medium-high confidence.** Revisit only if C9's calibration data ever produces a
*measured* difficulty per level, which is a different and more honest number to show. Even then,
consider showing it as a word rather than a number, and not next to the paws.

---

## Considered and rejected

Short reasons, so none of these has to be re-argued from scratch.

**Sniff marks that persist as ordinary player marks.** Tempting, and it does not survive contact
with the code. `autoMarks` is recomputed wholesale on every placement, so anything folded into it
is erased by the next dog. `manualMarks` is erasable by a tap and is subtracted on placement, so a
player can delete the four squares they just paid for. That means a fifth `BoardCellState`, a new
persisted set, and a change to proposal 5's snapshot. Worse, `ruledOutCells` is a pure function of
`state.placed`, so on an unchanged board a second sniff returns the same four cells. Today they
have evaporated and nobody notices; persist them and the player pays again for crosses that are
already on screen, and the "never spent for nothing" guard does not fire because the list is not
empty. It also erases what the sniff is: the scrim reveal *is* the moment they bought.

**A "Your record" stats page folded from the fact log.** The page worth having wants
distributions (median clear time per size, attempts per clear), and `AchievementCounters` holds
only "a counter reached a number" because the achievement criteria demand that shape. Every
display-only statistic either becomes a permanent `Stat` that the achievement engine folds on
every attempt forever, or needs a raw `List<LevelResult>` accessor that gives up the property that
counters are the single representation of progress. The settings toggle has no good answer either:
hide the page with the badges switch and one toggle silently controls two features whose copy is
wrong for one of them; leave it visible and a player who switched badges off is still reading the
badge log.

**Undo.** There is almost nothing to undo. A placement is only ever correct, so undoing one is
undoing a fact. A mark is undone by tapping it again. What is left is undoing a *wrong* guess,
which is undoing a bone, and that is a fourth consumable wearing a different hat.

**Endless or on-device generated levels.** SPEC 3 is explicit that nothing is generated on device,
and the verification test is the only thing between an unsolvable board and the store. Generation
also takes 42 seconds for 1,230 boards on a JVM with a warm JIT.

**Reusing the daily pool as post-campaign content.** The pool wraps with `daily.poolOffset`, so
spending it as campaign content spoils boards the daily will serve later, on the players most
likely to still be here when it does.

**Leaderboards, friends, versus mode, sharing that unlocks anything.** All require identity, and
identity was deleted rather than disabled in C0 for a reason recorded in `decisions.md`: dormant
auth still runs at boot. Anything adjacent inherits the same problem. Note that this rules out the
softer versions too, including a global "you beat 60% of players on this board" line, which needs a
server that receives per-player results. `share.tapped` is the growth channel we have, and it works
because it needs nobody's account.

**Cloud save or a progress export code.** Already scoped as a v2 candidate in SPEC 18, and the
export/import string is the right shape when it happens. It is not a v1 feature and it should not
be smuggled in as one.

**Cosmetics, dog skins, a second currency.** SPEC 18 lists it as a v2 candidate and the reasoning
in the "three consumables, one shape" decision generalises: a second economy is a second thing to
learn before the puzzle.

**A second, deeper hint tier.** Same argument. One booster that rules squares out is a mental
model; two boosters with different hint strengths is a purchasing decision in the middle of a
puzzle.

**A lives-regenerate-on-a-timer meter.** SPEC 1.4 already decided lives do not persist across
attempts and do not regenerate. A timer meter is the mechanic the rewarded-continue pattern exists
to replace, and it converts worse.
