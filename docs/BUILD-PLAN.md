# Sodogku build plan

Chunks are ordered by risk, not by visibility. The riskiest logic in the app (uniqueness,
difficulty, level generation) is pure Kotlin with no UI, so it goes first and gets fully tested
before anything is rendered. By the end of C4 there is a playable game with no monetization and
no network, which is the only point at which "is this actually fun" can be answered.

Each chunk states what unblocks it, what it delivers, and how we know it is done.

---

## Working agreement

Every chunk closes the same way, and none of it is optional — the docs are how the next session
(or the next person) knows why anything is the way it is.

1. **Verify.** `./gradlew testDebugUnitTest :apps:server:test :apps:compose:assembleDebug
   :apps:compose:compileKotlinIosSimulatorArm64 detekt` all green. Run the app on a device when
   the chunk changed anything visible.
2. **Update the docs in the same change.** `SPEC.md` when behaviour or a number changed,
   `BUILD-PLAN.md` with the chunk's outcome and anything it discovered, `decisions.md` for any
   non-obvious call, `docs/practices/app-events.md` for any new event.
3. **Record what was measured, not just what was chosen.** Several decisions here were made
   against numbers that contradicted the obvious guess (balanced region growth being worse,
   gentler scoring coefficients making the paw rating meaningless). Those numbers are the reason
   nobody re-litigates them blind.
4. **Say what is not verified.** Skipped tests, untested platforms, and environment gaps get
   written down, not glossed.
5. **Commit to `main`** with a Conventional Commits message.

## C0 · Project generation and template trim — **DONE** (2026-09-07)

**Delivers**

- Template generated into `Sodogku/` in place, git history preserved.
- 8 stray root scripts removed (they are regenerated correctly under `scripts/`).
- Supabase identity stripped: `:libraries:identity` and its impl, the auth orchestrator, the
  session-recovery screens, OAuth redirect handling in `App.kt`.
- Server trimmed: migrations `V1`, `V2`, `V3` deleted; `MeRoutes`, `PlayerReportRoutes`, profile
  and moderation repositories removed; Supabase JWT verification removed. App config, the admin
  console, and `/_health` stay.
- `:apps:integration` harness updated to compile against the trimmed graph.

**Done when** `./gradlew :apps:compose:assembleDebug`, `compileKotlinIosSimulatorArm64`,
`:apps:server:test` and `:apps:integration:testDebugUnitTest` are all green, and the app launches
to the template home screen on both simulators.

**Outcome.** All green: 300 tests, 0 failures, detekt clean, both platforms compile. The app
installs and boots on an Android emulator to the Sodogku welcome screen with no Supabase traffic
on the launch path. Three decisions recorded in `docs/decisions.md`.

Two things beyond the original scope landed here because they were cheapest now:

- The `:apps:integration` harness was **rewritten**, not deleted. Its whole reason to exist was
  the `/v1/me` profile round trip; it now drives the real `RemoteConfigRemoteDataSource` over
  real TCP against the real server and a real Testcontainers Postgres. That is the surface C7
  needs anyway.
- String resources are wired up for real (`VerifyStrings` honored rather than baselined). See
  the decisions entry.

**Not verified:** the integration smoke test and the server's Postgres tests self-skip when
Docker is unreachable, and Docker was not running on this machine. 5 of 300 tests skipped for
that reason. Start Docker and re-run `./gradlew :apps:integration:testDebugUnitTest
:apps:server:test` to confirm the rewritten harness actually passes.

**Also not verified:** iOS runtime. `xcode-select` points somewhere that is not Xcode, so the
simulator could not launch. The fix needs a password, so it is yours to run:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

The iOS *Kotlin* target compiles, and the Swift wrapper had its two auth files removed and its
`create(...)` call updated, but nothing has actually run on an iOS simulator yet.

---

## C1 · `:libraries:puzzle` — **DONE** (2026-09-07)

**Unblocked by** C0.

**Delivers** pure Kotlin, zero dependencies, targets android + ios + jvm.

- `Board` (size, region map), `Placement`, `Cell`.
- Rule validation: one per region, one per row, one per column, no king-move adjacency.
- **Exact solver** with solution counting, stopping at 2 (we only ever need "is it unique").
- **Technique-tier solver**: solves using progressively deeper techniques and reports the
  shallowest tier that suffices (1 to 5, per spec 1.7).
- **Next-deduction finder**: given a partial board, returns the cell provable by the shallowest
  remaining technique. This is what Sniff calls.
- Auto-mark derivation: given placements, the set of cells ruled out.

**Done when** the module has no `implementation` dependencies at all, and unit tests cover: known
4x4 through 10x10 boards solve correctly, a deliberately ambiguous board reports 2 solutions, the
tier solver agrees with hand-classified fixtures, and the next-deduction finder never returns a
cell that is not actually forced.

**Outcome.** 64 tests, green on JVM, Android and iOS, detekt clean. `BoardFactory` also landed
here rather than in the generator: the construction primitives are puzzle-domain logic, they keep
the C2 tool thin, and the soundness property test needs them.

Two tests carry most of the weight:

- **`DeductionSoundnessTest`** drives the engine over random unique boards and asserts it never
  eliminates a cell the solution occupies and never places a dog outside it. An unsound technique
  does not crash, it quietly mis-scores difficulty and points hints at the wrong square.
- **`countSolutions_agreesWithBruteForceEnumeration`** checks the pruned search against an
  independent oracle that enumerates every column permutation and filters with the public rule
  checker, sharing no code with the solver.

Soundness alone would pass with a do-nothing engine, so `DifficultyTest` separately pins that the
engine finishes at least 80% of unique boards and that scores spread across tiers.

### The finding C2 has to deal with

Measured difficulty distribution on random unique boards (40 per size), and the generation cost
alongside it:

| Size | Unique boards found | Attempts | Tier 1 / 2 / 3 / 4 | Time |
|---|---|---|---|---|
| 4 | 40 | 40 | 8 / 30 / 1 / 1 | 31ms |
| 6 | 40 | 40 | 11 / 24 / 2 / 3 | 66ms |
| 7 | 40 | 40 | 5 / 22 / 6 / 7 | 387ms |
| 8 | 40 | 59 | 3 / 29 / 3 / 5 | 1.8s |
| 9 | 40 | 295 | 7 / 22 / 4 / 7 | 17s |
| 10 | **9** | 400 | 2 / 6 / 0 / 1 | 39s |

The tier spread is healthy and nothing scored tier 5, so the engine is strong enough to rate the
whole pack. **Uniqueness is the problem.** At 10x10 the hit rate collapses to about 2%, and the
campaign needs 110 boards at that size plus the daily pool.

Two things were ruled out by measurement rather than assumed:

- **More mutation rounds do not help.** 0 hits out of 60 at 10x10, unchanged from 12 rounds to
  120. Single boundary-cell moves barely shift the constraint structure.
- **The mutation primitive is not broken.** `mutateRegions_actuallyMovesCells` pins that it really
  moves cells and preserves every invariant, because a silent no-op and a non-converging strategy
  look identical from the outside and want opposite fixes.

Region sizes are already very uneven from the frontier-biased growth (spreads like
`[1, 7, 7, 7, 10, 12, 12, 13, 14, 17]`), and those boards still blow past 50 solutions. So
unevenness is not the missing ingredient either.

**What C2 should do instead: targeted refinement.** When a board has more than one solution, pull
a second solution, find a row where it differs from the seed, and move region boundaries
specifically to invalidate *that* alternative (for example, put the cell the alternative uses into
a region another of its dogs already occupies). That is directed search against a named
counterexample rather than random walking, and it is the standard way these generators converge.
Budget real time for C2 regardless: the 9x9 and 10x10 bands are where it will be spent.

---

## C2 · Level generation — **DONE** (2026-09-07)

**Unblocked by** C1.

**Delivers**

- `tools/level-generator`: JVM CLI implementing spec 3.1. Seeded and deterministic, so a given
  seed reproduces a given pack.
- `campaign.pack` (500 levels on the band curve) and `daily.pack` (730 levels).
- `:libraries:levels`: pack model, asset loading, `LevelRepository`.
- **The verification test.** Loads both shipped packs and asserts every level: contiguous
  regions, region count equals size, exactly one solution, matches the shipped solution, paw
  thresholds ascending. Runs in CI on every commit.

**Done when** both packs exist, the verification test passes, and a difficulty histogram of the
campaign pack matches the intended curve.

**Outcome.** 500 campaign + 730 daily levels generated in 42 seconds, all bands hitting target.
468 tests across the repo, 0 failures, detekt clean, green on JVM, Android and iOS.

The C1 blocker is fixed. Targeted refinement (`BoardFactory.refineToUnique`) converts 23/40 boards
at 10x10 where random mutation converted 0/60. Balanced region growth was tried and was
measurably *worse* — uneven regions constrain more, because a small region pins its dog tightly.
Both are recorded in `docs/decisions.md`.

Shipped campaign curve, tier counts per band:

| Band | n | t1 | t2 | t3 | t4 | t5 |
|---|---|---|---|---|---|---|
| 4x4 | 10 | 2 | 8 | 0 | 0 | 0 |
| 5x5 | 30 | 8 | 19 | 3 | 0 | 0 |
| 6x6 | 60 | 12 | 33 | 6 | 9 | 0 |
| 7x7 | 80 | 10 | 47 | 7 | 16 | 0 |
| 8x8 | 100 | 16 | 41 | 19 | 24 | 0 |
| 9x9 | 110 | 8 | 40 | 25 | 37 | 0 |
| 10x10 | 110 | 1 | 30 | 43 | 36 | 0 |

Difficulty shifts steadily rightward with size and nothing scores tier 5. Levels 1 and 2 are
tier 1, levels 3 to 10 are tier 2, and level 11 resets to tier 1 on a bigger grid — the sawtooth
working as intended. The opening bands cap difficulty explicitly (`Band.maxDifficulty`) rather
than relying on the sort: the first pass put a tier-4 board, which needs a hold-a-hypothesis
contradiction step, at level 10, and that is exactly where a puzzle game loses a first session.

**Two deviations from the spec**, both recorded in `docs/decisions.md`:

- Packs ship as **generated Kotlin source**, not an asset file, so the verification test runs
  identically on JVM, Android and iOS with no resource loading in the way.
- `parScore` and `pawThresholds` are **not in the pack**. They get derived at runtime from size
  and difficulty using config coefficients, so retuning a three-paw clear is a config change
  rather than a regenerated pack plus an app release. This also decouples C2 from C3 entirely.

**Regenerating:** `./gradlew :tools:level-generator:run`, optionally `--args="--seed N"`.
Deterministic per seed. Note that `LevelPacks.PACK_VERSION` must be bumped if the pack ever
changes after release — progress is keyed on level id, so a regenerated pack silently reassigns
players' completed levels to different boards.

---

## C3 · `:libraries:scoring` — **DONE** (2026-09-07)

**Unblocked by** C1 (needs nothing from C2).

**Delivers** pure Kotlin scoring: per-placement points with combo and speed multipliers,
completion bonus, praise-threshold classification, paw rating from score against pack thresholds.
Every coefficient injected as a config-shaped data class, never a hardcoded constant.

**Done when** unit tests pin the formula against worked examples and confirm a strike resets the
combo.

**Outcome.** 24 tests, zero dependencies (not even `:libraries:puzzle` — scoring only ever needs a
size and a difficulty tier as ints).

The tuning caught a real problem. The first set of coefficients produced a multiplier range too
narrow for the paw thresholds to mean anything: the worst run a player can physically finish (two
strikes spent, every placement slow) still scored 63% of par, above the two-paw line, so one paw
was unreachable. Widening combo step 0.05 → 0.08, speed max 1.3 → 1.6 and lives rate 0.25 → 0.5
spreads completed runs across roughly 0.45 to 1.0 of par, and the three bands are now each pinned
by a test that drives the real API end to end rather than the formula in pieces.

---

## C3a · Theme, art and design-system foundations — **DONE** (2026-09-07)

**Unblocked by** C0. Scheduled **before** the board on purpose: every screen built against a
placeholder theme is a screen that has to be revisited, and C4 will define most of the game's
visual language whether the tokens are ready or not.

**Delivers**

- The Sodogku palette and type scale in `:libraries:ui/system`: 10 colourblind-workable region
  colours, the rounded display font, radii, elevation, spacing.
- Motion tokens so springs are consistent rather than per-call-site.
- Game components in the catalog with previews: `BoardCell`, `RuleChip`, `LifeRow`, `PawRating`,
  `LevelTile`, `ScoreCounter`, `FloatingPoints`, `BoosterButton`.
- Detekt rules for the recurring mistakes (raw `dp` literals, direct Material imports in
  `features/`), following the `VerifyStrings` pattern.

**Done when** a new screen can be written with no raw `dp`, no Material import and no drawable
reference, and looks right by default.

**Outcome.** All green, verified on device.

- **`Dog(pose = ...)`** with six poses, downscaled per use case (620KB shipped against 8.3MB of
  source). Originals and animated clips archived in `art/source/`; SPEC section 16a covers why the
  clips are not wired up and why there is no Coil dependency.
- **`RegionPalette`**, ten fills plus a distinct glyph each, and **`BoardCell`** with its pop,
  shake, auto-mark and long-press built in — so a screen cannot forget to animate a cell or wire
  press feedback by hand.
- **`Motion`** tokens (Pop, Tap, fade, shake duration), a `Radii.Cell` token, and
  **`bounceCombinedClick`** for the tap-plus-long-press gesture the board needs.
- **Poppins** replaces Roboto as the sans family. Already bundled with the template, geometric and
  near-circular, so the display face got rounder with no new asset and no licensing step. Baloo 2
  or Fredoka are drop-in if it should be rounder still.
- **`NoRawDesignValues`** detekt rule: fails raw `dp`/`sp` literals and direct Material imports in
  feature code, excluding `:libraries:ui` (which has to define them) and previews. It caught one
  real violation in `HomeScreen`, which was fixed rather than baselined.
- **Catalog page** for regions, cells and dog poses, with previews.

### Two bugs the on-device render caught that review did not

Both were found by putting the palette on a phone and looking at it, and neither would have
failed a test:

1. **Three of the ten ink colours were wrong.** Marks are drawn in dark or light ink depending on
   the fill, and hand-assigning that put light ink on coral, green and plum — all dark enough that
   the mark nearly vanished. Ink is now *derived* from the WCAG contrast ratio against each fill,
   which removes the whole error class rather than fixing three values.
2. **The X mark reused a region's identity glyph.** `BoardCell` drew the player's "no dog here"
   mark with `RegionGlyph.Plus` — which is region 6's glyph — directly under a comment claiming it
   deliberately was not one of them. On a region-6 cell in colourblind mode, "this is region 6" and
   "you ruled this out" would have been the same shape. The mark now has its own cross, and a
   cross is excluded from the region set so it can only ever mean one thing.

### Deferred out of this chunk, deliberately

`RuleChip`, `LifeRow`, `PawRating`, `LevelTile`, `ScoreCounter`, `FloatingPoints` and
`BoosterButton` are **not** built yet. Their shape depends on the screen that holds them, and
designing seven components against no layout is how a catalog fills up with APIs nobody can use.
They land in C4 alongside the board, against the existing tokens.

---

## C4 · `:features:game` — the playable board — **DONE** (2026-09-07)

**Unblocked by** C1, C2, C3.

**Delivers** the whole game loop against **fake** `AdGate` and `Entitlements`.

- Board rendering with region colors, rounded cells, the three rule chips with diagrams.
- Tap to place, long-press to mark, auto-mark on placement.
- Lives (bone row), score counter, floating `+points` and praise text.
- Win sheet and lose sheet, with continue / retry / map.
- Boosters bar (Sniff, Treat) with inventory counts.
- Timer on a monotonic clock, paused on background.
- New UI components land in `:libraries:ui` catalog: `BoardCell`, `RuleChip`, `LifeRow`,
  `PawRating`, `ScoreCounter`, `FloatingPoints`, `BoosterButton`.

**Done when** a level is playable end to end on both platforms, win and lose both reachable, and
this is the first point where we stop and actually play it before continuing.

**Outcome.** Playable. Verified on an Android emulator by actually solving level 1: wrong tap cost
a bone, correct taps placed dogs and scored, auto-mark fired, the win sheet showed 4411 points and
2 paws, and three deliberate misses reached the lose sheet with continue / retry / levels. 20
GameViewModel tests, everything green, detekt clean.

`:libraries:ads` and `:libraries:billing` landed here as **api modules only** — `AdGate`,
`Entitlements` and their outcome types, with `AlwaysRewardingAdGate` and `FreeEntitlements` as the
default bindings until C8. Defining the seam now means the gating logic in the game loop is real
and tested rather than retrofitted.

The load-bearing rule of the ad layer is pinned by test: `RewardOutcome` distinguishes `NoFill`,
`Offline` and `Failed` from `Dismissed` because **only a deliberate dismissal may withhold a
reward**. An empty ad network must never be why someone cannot finish a puzzle they are most of the
way through.

### Four bugs found by running it, not by testing it

1. **The win sheet had no surface.** Content sat straight on the scrim, so "Good dog!" was dark
   text on a dark translucent board — effectively invisible. It has its own card now.
2. **Bones rendered as blobs.** The lobes were large enough relative to the bar that they merged
   into one lump, and a bone in a square box cannot read as a bone regardless. Landscape box,
   smaller lobes, wider spread.
3. **The "1 dog per color" rule diagram showed a shaded column**, which is not what a region is.
   It said "one per column" twice and never mentioned colour. It now draws three contiguous groups
   in real palette fills with a pip in one.
4. **A void between the header and the board.** The rule chips were floating below a flexible
   spacer; they belong directly under the score as reference material.

### Two bugs caught by the project's own detekt rules

- `AnimatedStateReadInComposition` caught `LifeRow` unwrapping `animateFloatAsState` with `by`,
  which recomposes the whole row on every animation frame, three times over. Now read inside
  `graphicsLayer`.
- `NoRawDesignValues` (added in C3a) caught a raw `4.dp` in the board's cell gutter.

Both are exactly what those rules exist for, and neither would have failed a test.

### Scaffolding to remove in C5

`HomeScreen` is a temporary three-button launcher (4x4, 7x7, 10x10) so the board is reachable and
testable at the sizes that differ. The level map replaces it wholesale.

### Not yet wired

Timer ticks, the interstitial after a level completes, progress persistence, and the next-level
button on the win sheet. Those need `:libraries:progress` (C5) and the config-driven ad frequency
gate (C7), and stubbing them here would mean rewriting them there.

---

## C4a · Interaction model, motion and the Focus system — **DONE** (2026-09-07)

Design feedback after playing C4. **Unblocked by** C4.

**Delivers**

- **Tap marks, double tap commits.** The safe gesture is now the cheap one; a single tap can never
  cost a bone. See `decisions.md` for why the second tap is recognised in the ViewModel rather than
  by `detectTapGestures`.
- **Motion everywhere on the board.** The grid lands as a diagonal wave, crosses draw stroke by
  stroke, dogs overshoot and settle, wrong guesses flash red and shake.
- **A wrong guess leaves the cell marked**, because the player just proved no dog goes there.
- **The Focus system** (`:libraries:ui/system/Focus.kt`): `Modifier.focusTarget(key)`, a
  `FocusRegistry`, and a `FocusScrim` that punches holes in a dim layer with `BlendMode.Clear`.
  Several scattered targets can be lit at once, which is what the hint mode needs.
- **The last-bone warning**, the first thing built on Focus: dims the board, spotlights the bones,
  hangs a bubble under them. Fires on the *edge* into one life, once per attempt.
- **In-game settings**, reachable from the board without leaving it. Currently exposes colourblind
  mode, which C3a built and nothing had surfaced.
- **Header chrome**: menu on the left, level and score centred, settings on the right.
- **Bones from an ad** on the lose sheet — all three, not one, since a single bone puts the player
  straight back where they were.
- **A starter dog on levels 1 to 25.** A teaching aid more than a leg-up: the free dog fires the
  auto-mark cascade immediately, so a new player sees the rules ruling cells out before having to
  reason about any of them. It scores nothing.

**Outcome.** 28 GameViewModel tests, all green, detekt clean, verified on device.

### The bug this chunk actually turned up

`SEAViewModel.state` reads a *derived* `stateIn` flow, so it lags `updateState` by a dispatch.
Reading `state` back inside the same action returns the pre-update value. It surfaced as the
starter dog working on device but not in tests — and the same pattern was in `place()` → `win()`,
where a level's final score could silently drop the points for the placement that won it. Both are
fixed and the rule is written down in `decisions.md`.

### Still open from the same feedback

Level drawer, hint spotlight mode, an always-available ad button, haptics, and the ad-frequency
question (bones-only versus an interstitial every N levels). Tracked in C4b.

---

## C4b · The consumable economy — **DONE** (2026-09-07)

**Unblocked by** C4a. Haptics, the level drawer and the in-game dialogs already landed.

### The three consumables

Redefined after play feedback. All three behave the same way, which is the point — one mental
model, one refill mechanic, one dialog shape:

| Consumable | What it does | Starts at |
|---|---|---|
| **Bone** | A wrong guess costs one. Out of bones ends the attempt. | 3 |
| **Sniff** | A *hint*: dims the board and shows where a dog cannot go. | 3 |
| **Treat** | A free correct placement. | 3 |

- Every one refills to 3 by watching an ad.
- Counts **can exceed 3**. Clearing levels grants extra, so the store of them is a reward for
  playing rather than a meter that only ever empties. The cap is on the *refill*, not the holding.
- Each shows its count as a badge on its button.

### First use of a booster opens an explainer

A dog still, a sentence on what the thing does, then either **Use it** (if they hold any) or
**Watch an ad** and **Not now**. Only the first time per booster; after that a tap uses it, or
offers the ad when the count is zero.

The reason it is a dialog and not a tooltip: spending a consumable is irreversible, and the first
time someone taps an unfamiliar button they should find out what it costs before it happens.

### Also in this chunk

- **A wrong guess leaves a permanently red cross**, not an ordinary one. It marks a square the
  player *paid* for, which is different information from one they reasoned out.
- Level rows in the drawer preview their reward.
- An always-available ad button.

---

## C5 · Progress and per-level records — **DONE** (2026-09-07)

**Unblocked by** C4b.

`:libraries:progress` + impl: Room-backed `level_progress` (best score, best paws, best time,
attempts, state) and the in-progress board snapshot so backgrounding mid-level resumes exactly.
Level rewards are granted here, which is what feeds the drawer's prize preview.

The one number that exists today, `AppData.currentLevel`, moves into this module.

**Outcome.** `:libraries:progress` (api + impl, 15 tests) backs the level drawer. `LevelState` is
*ranked* — `Locked < Unlocked < Skipped < Completed` — and only ever moves up, persisted by name
rather than ordinal so reordering the enum cannot silently re-rate everyone's history.

**What it discovered.** Deleting `AppData.currentLevel` left a cold-start regression: `AppViewModel`
still read it for the start destination, so every launch went to level 1 no matter what the
player had cleared. Nothing failed — the field still existed and still had a default. The fix
injects `ProgressRepository`; the clamp for a finished campaign lives in `LevelPacks.clampToCampaign`
rather than at the call site, because forgetting it is a crash-free *wrong answer* that only shows
up for the players who finish.

The drawer's `open` flag also lived in the screen's `remember` while everything it draws is loaded
in the ViewModel, so the pane could open onto a list of locked rows while the records were still
in flight. Both now move in one `updateState`.

---

## C6 · Daily challenge — **DONE** (2026-09-07)

**Unblocked by** C5.

**Delivers** date-seeded selection from `daily.pack`, the daily card on the map, streak counting,
streak freeze (fake ad for now), one-attempt-per-day locking, `daily_result` persistence.

**Done when** the date rolls correctly across midnight, a missed day breaks the streak, a freeze
covers exactly one day, and a completed daily cannot be replayed for a better score.

**Outcome.** Everything except the card. 43 tests in `:libraries:progress:impl`, detekt clean,
Android and iOS both compile. All four done-when conditions are pinned by tests that fail when the
implementation is broken (checked by mutation: forcing the streak fold to return zero fails 23 of
them, and relaxing the insert conflict strategy fails the two replay tests).

The surface the card is built against, all in `:libraries:progress`:

```kotlin
interface DailyRepository {
    fun observe(): Flow<DailyStatus>        // re-emits on a result AND at local midnight
    suspend fun status(): DailyStatus
    suspend fun history(): List<DailyResult>
    suspend fun onCompleted(date: LocalDate, score: Int, paws: Int, timeMs: Long)
    suspend fun onFailed(date: LocalDate, timeMs: Long)
    suspend fun useFreeze(): FreezeResult   // shows the rewarded ad itself
    suspend fun reset()
}
```

`DailyStatus` carries `date`, `packIndex`, `levelId`, `result`, `streak`, `freezeOffer`,
`resetsIn` and `enabled`, plus a computed `playable`. The card should do no date arithmetic of its
own — everything in one status comes from one snapshot of the clock.

### The card — **DONE** (2026-09-07)

`DailyCard` in `:libraries:ui` (with previews), rendered at the top of `LevelDrawer` and absent
entirely when either flag is off. `GameRoute` gained `daily: Boolean`, `GameViewModel` an
`isDaily` assisted arg and a `DailyRepository`, and the drawer's card, the header stat, the win
sheet and the loss sheet all read `state.isDaily`. 15 new ViewModel tests, 62 in the file.

### What is left

- **Telemetry.** `daily.started`, `daily.completed` and `daily.freeze_used` are emitted through
  `logEvent`; `daily.streak_broken` is not, because nothing on the client is told when a streak
  ends — the fold simply returns a smaller number on the next read. Emitting it needs a
  "streak as of last read" to compare against, which is the counter this design refuses.
- **Sharing** (C10) reads `history()`.

### What it discovered

- **`AppData` does not gain a streak.** `dailyStreak`, `lastDailyDate` and `freezesUsedThisMonth`
  were in spec 13.4 and are not stored; all three fold out of `daily_result`. SPEC updated.
- **`daily_result` uses an `outcome` enum**, not the `completed` + `froze` booleans 13.2 sketched.
  Four states, one of them meaningless.
- **Both daily flags are read.** `daily.enabled && features.dailyChallenge`. Reading one would
  have left the other looking operable in the admin console, which is the failure already recorded
  against `app.minSupportedVersion`.
- **`fallbackToDestructiveMigration` is now a data-loss bug waiting to happen.** A schema version
  bump drops every table, which used to cost an example row and now costs the player's whole
  campaign and their streak, with no server copy of either. Real migrations are needed before the
  first store release — not urgent this week, unshippable after it.
- `LevelPacks.dailyIndexFor(epochDay)` was added next to `dailyFor` so the stored `levelIndex` and
  the board come from the same wrap.

### What the card discovered

- **The shared number line reaches further than the pack lookup.** Resolving the board against the
  right pack is the obvious half. The other half is every place a level id is treated as campaign
  progress: `progress.record`, `onAttemptStarted`, `onCompleted`, and `maxOf(unlocked, level.id)`
  in two places — a daily would have unlocked campaign levels up to its own id. The drawer had it
  too, and that one was only visible on device: it scrolled the campaign list to the daily's id
  and highlighted a locked stranger as "current". `currentLevelId` is now nullable for exactly
  that reason.
- **A lost daily cannot be written when the bones run out.** `daily_result` takes one row per date
  and never updates it, and the spec allows a failed daily to be revived with a rewarded ad. Write
  the failure at the loss and the revive's clear can never land. It is written when the player
  walks away from the loss sheet instead — see `decisions.md` for the force-quit hole that leaves.
- **The daily's level id is not a number to show anyone.** It is a position in a 730-board pool
  that reads as a campaign level nobody has reached. The header shows the streak instead.
- **Compose resources do not honour Android's `\'` escape.** `Play today\'s board` rendered the
  backslash on device. Typographic apostrophes throughout instead.

**Verified on device** (Android emulator, `scripts/dev/drive.py`): the card renders at the top of
the drawer with the date, streak, countdown and CTA; the daily opens its own 7×7 board with
"Streak" where "Level" usually sits; clearing it shows three paws and "1 day streak"; and after a
force-stop and relaunch the card reads "Today's board is done" with the paws intact and no CTA —
which is the process-death path the C6 logic tests could not see. Campaign level 2 was still
locked afterwards.

**Not verified:** the freeze offer and its five outcome dialogs were never seen on a device. The
offer only appears for a missed day that a freeze would reconnect, and producing one needs the
device clock moved, which this emulator refuses without root. Unit tests cover every branch and
the dialog has previews. The Room queries remain unexercised for the reason the logic entry gives.

---

## C7 · Remote config — **code DONE** (2026-09-07), Fly deploy outstanding

**Unblocked by** C5. Deliberately **before** real ads, so the ad code reads its numbers from
config from the first line rather than being retrofitted.

**Delivers**

- Every key in spec 4.3 defined as a typed `ConfiguredValue`.
- A **complete** `FallbackConfigMap`, plus a test that asserts every declared key has a fallback.
- A test asserting monetization keys fail open (config unavailable produces fewer ads, never
  more).
- Admin console verified against the real key set.
- Fly deploy: server + small Postgres, config endpoints live.

**Done when** the app is fully playable and correctly gated with the server switched off, and
flipping a value in the admin console changes app behavior on the next fetch.

**Outcome (2026-09-07) — everything except the Fly deploy.**

Client half: 57 typed `ConfiguredValue` classes across ten namespaces, a complete
`FallbackConfigMap`, and the two SPEC 4.2 tests (`FallbackConfigCompletenessTest`,
`MonetizationFailsOpenTest`).

Server and admin half:

- `apps/admin/config-manifest-registry.json` went from **4 entries to 60** — every declared key
  with its real type, default and allowed values, including the two `JsonConfigValue` composites
  and the three `telemetry.*` keys. This is what the server type-checks admin writes against, so
  before this it was checking four `upgrade.*` keys and waving everything else through.
- `ConfigManifestRegistryDriftTest` (`:apps:integration`) holds the file against the real value
  classes and prints the line to paste when they disagree. It is the *only* test module that can
  see both `:libraries:config` and `:libraries:telemetry:impl`, which is why it lives there.
- `ShippedConfigSchemaTest` (`:apps:server`) builds a `ConfigSchema` from the committed registry
  and proves the real key set is enforced; `ConfigAdminRoutesTest` uploads the same registry
  through the real route and checks the rejections end to end.
- `dangerousWarning` in the console grew from 3 cases to cover lockouts
  (`legal.forceReacceptBelow`, `upgrade.softUpdateVersionCode`), fail-closed monetization
  (`ads.failureMode = LOCK`, zero offline grace, a disabled rewarded placement) and
  everyone-loses-something writes (`ads.enabled`, `daily.enabled`, `features.*`, the opt-in ad
  formats). Deliberately *not* ordinary retuning — see `decisions.md`.

**Discovered.**

- A mistyped boolean does not fall back to its default; `"banana".toBoolean()` is `false`, so a
  string on `daily.enabled` turns the daily off for everyone. Written into SPEC 4.2.
- Declaring the registry as a `Test` task input is load-bearing. Without `inputs.file`, editing
  the registry left the drift test `UP-TO-DATE` and it passed against a file with a key deleted
  and three values wrong. Observed, not theorised.
- `apps/server/DEPLOY.md` still describes Postgres and auth as living on Supabase. Auth was
  removed in C0 and the server has no user data; the Postgres half is still true but the
  Supabase framing reads as leftover. Worth a pass before the deploy, not touched here.

**Not verified.**

- **The Postgres tests did not run.** Docker was not running on this machine, so the four
  `Postgres*`/`DatabaseSchemaTest` classes in `:apps:server:test` and `HarnessSmokeTest` in
  `:apps:integration` self-skipped. 184 tests ran, 5 skipped, 0 failures. Start Docker and re-run
  `./gradlew :apps:server:test :apps:integration:testDebugUnitTest`.
- **`:apps:compose:assembleDebug` and `testDebugUnitTest` could not be run to completion**, because
  `:features:game:impl` was mid-edit by concurrent work (an `@Assisted` parameter mismatch in
  `GameViewModel`). `:apps:server:test`, `:apps:admin:build`, `:apps:integration:testDebugUnitTest`,
  `:libraries:config:impl:testDebugUnitTest` and `detekt` (0 findings) are all green.
- **Nothing was exercised against a live server.** The admin console's rendering of 60 flags
  instead of 4 has not been looked at in a browser, and no manifest has been uploaded anywhere.

### Follow-up (2026-09-07): the keys that were declared and inert

`ConfigValuesAreReadTest` found that 39 declared values were named nowhere outside
`:libraries:config` — the console rendered a typed editor for each, an operator could change any
of them, and nothing happened. That list is now **19**. What was wired:

- **All fourteen `scoring.*` keys.** `ConfiguredScoring` (in `:features:game:impl`) assembles them
  into the `ScoringConfig` that `Scoring.placement` / `complete` / `paws` take, and `GameViewModel`
  passes it instead of letting the parameter default. `:libraries:scoring` stays dependency-free,
  which is a stated intent in its build file, so the assembler lives next to its only consumer.
  An invalid remote set falls back to `ScoringConfig.Default` **whole** — see `decisions.md`.
- **`boosters.startingSniffs`, `boosters.startingTreats`, `boosters.refillTo`.** The first two
  needed `AppData.sniffs`/`treats` to become nullable first: a record defaulting to 3 is
  indistinguishable from a player who spent down to 3, so the config value could never win.
- **`features.achievements`, `features.sharing`, `features.boosters`**, each read at the point the
  feature draws itself rather than resolved into a field.

**What is still inert, and why it is not a call site somebody missed.** Twelve of the nineteen name
a feature that does not exist: skips (no button, no per-day counter), the level map with
silhouettes (`progression.lookaheadCount` — the drawer deliberately shows every level with locks
instead), a treat granted every N levels, a daily cap on ad grants, Pro's per-attempt boosters
(SPEC 5.1 promises them; nothing implements them), and the app-open ad (`AdFormat.AppOpen` reaches
the SDK, but there is no `AdPlacement`, no gate and no cold-start hook, so the cooldown has nothing
to space out). `ads.failureMode`'s `LOCK` arm was never built at all. The remaining five need the
upgrade gate, the maintenance screen or the legal re-accept sheet, which are chunk-sized.

**Two gaps left open on purpose**, both in files owned by concurrent work in the same session:

1. `GameScreen.kt`'s `BoosterBar(state, onAction)` call needs wrapping in
   `if (state.boostersEnabled)`. The switch already stops the economy — a tap spends nothing and
   the ad refill refuses — but the two buttons still render.
2. `BoosterPrompt.kt` prints the compile-time `ConsumableRefillTo` in its "watch an ad for N" copy
   while the refill itself now uses `boosters.refillTo`. Raise the config value above 3 and the
   button under-promises. It wants the number passed in.

**Also found:** the 37 in the original decisions entry and in the test's KDoc was a miscount. The
set had 39 entries from the day it was written.

**Remains: the Fly deploy.** Explicitly out of scope for this session; nothing was deployed and
no production state was touched. The steps, in order:

1. `fly apps create sodogku-server-dev` and `sodogku-server-prod` (names already set in
   `apps/server/fly.toml` and `fly.prod.toml`; region `iad`).
2. Provision a Postgres per environment and `fly secrets set DATABASE_URL=…` on each. Flyway runs
   the migrations on boot through `Database.connect`. Note the Supabase framing in `DEPLOY.md`
   above — decide whether the DB is Supabase-hosted or Fly Postgres and correct the doc either way.
3. `fly secrets set ADMIN_API_TOKEN=…` per app, and put the same values in the GitHub repo
   secrets `ADMIN_API_TOKEN_DEV` / `ADMIN_API_TOKEN_PROD`. The deploy workflow's manifest upload
   step warns and skips without them rather than failing, so a mismatch is silent.
4. `fly tokens create deploy -a sodogku-server-dev` (and `-prod`) → repo secrets
   `FLY_API_TOKEN_DEV` / `FLY_API_TOKEN_PROD`.
5. `fly deploy --config apps/server/fly.toml --remote-only` from the repo root, then
   `curl https://sodogku-server-dev.fly.dev/_health`.
6. Let the workflow's `Upload config manifest` step run (or PUT
   `apps/admin/build/config-manifest.json` by hand) and confirm the console lists 60 flags with
   types, not 4.
7. Then close the chunk's real acceptance test: flip a value in the console and watch app
   behaviour change on the next fetch. That is the one thing no test in this repo can prove.

---

## C8 · Ads and billing — **DONE** (2026-09-07), minus a store account and an Xcode package

**Unblocked by** C7.

**Delivers**

- `:libraries:ads` impl: AdMob on Android, AdMob on iOS via Swift through
  `IosAppComponentFactory`. All placements from spec 5.3.
- `:libraries:billing` impl: Play Billing 7 and StoreKit 2. Purchase, restore, entitlement cache
  that is true-until-proven-false.
- UMP consent flow, ATT prompt, both before the first ad request.
- Paywall screen and its triggers.
- Offline grace and the offline block screen.
- The frequency gates: new-user grace, N-levels, cooldown, session cap.

**Done when** a real test ad shows on both platforms, a sandbox purchase grants Pro and survives
a reinstall via restore, an ad failure grants the reward anyway, and the offline grace expires
and blocks correctly with counters that survive a force-quit.

**Outcome (2026-09-07) — everything except the two things that need a store account.**

Shipped: `:libraries:ads:impl` (`RealAdGate` in common Kotlin over an `AdNetwork` seam,
`AdMobAdNetwork` + UMP on Android, `IOSAdNetwork` in Swift), `:libraries:billing:impl`
(`RealEntitlements`, `RealPaywallCoordinator`, `PlayStoreBilling` on Play Billing 8,
`IOSStoreBilling` on StoreKit 2), and `:features:paywall` / `:features:paywall:impl`
(the Pro sheet, the offline block screen, and the navigator that turns a coordinator
request into a route). 48 new tests.

All policy is in common Kotlin — the frequency gates, both graces, the fail-open mapping,
the entitlement cache — and the platform seams only load, show, and say what happened.
That is what makes the interesting behaviour reachable from `commonTest` without an ad
network or a store, and it is why the Android and iOS builds cannot disagree about when
an ad is allowed.

**Demonstrated on a Pixel 4a (physical device).**

1. **A real AdMob test rewarded ad shows.** Tapped the board's ad offer, watched the
   Google Ads test creative play through to "Reward granted", closed it. The trail:
   `ads.gate_shown placement=booster_grant is_offline=false` → `AdMob initialised` →
   `ads.result outcome=Rewarded latency_ms=26583`.
2. **An ad failure grants the reward anyway.** Covered by `RealAdGateTest` across the
   whole `AdShowResult` enum, and demonstrated on device: every offline gate returned a
   reward-granting outcome while the network was unreachable.
3. **The offline grace expires and blocks, and the counters survive a force-quit.**
   Airplane mode on, one gate spent (`grace_levels_used=1`, written to `ad_state.json`),
   `am force-stop`, relaunch, second gate → `ads.offline_block grace_levels_used=2` and
   the block screen. Airplane mode off → the screen dismissed itself and the board came
   back. `GET PRO` opened the paywall; `GET PRO` there reported the store as unavailable
   (correctly — offline, and the product does not exist yet) rather than hanging.

**Not demonstrated.**

- **iOS, at all.** `xcode-select` on this machine points at something that is not Xcode,
  and fixing it needs the user's password. `:apps:compose:compileKotlinIosSimulatorArm64`
  is green, so the Kotlin half and the `IosAppComponent` signature change compile, but no
  Swift in `apps/ios/iosApp/Platform/` has been through a compiler. Treat
  `AdNetwork.swift` and `StoreBilling.swift` as unverified drafts.
- **The sandbox purchase.** There is no Play Console app, no App Store Connect record and
  no `sodogku_pro` product anywhere, so "a sandbox purchase grants Pro and survives a
  reinstall via restore" could not be run. The half that *is* tested is the half that
  owns the bug: `RealEntitlementsTest` covers unknown-vs-not-owned, restore, reinstall
  (empty cache + a store that remembers) and persistence across a relaunch.
- **The UMP form itself never appeared**, because the test device is not in the EEA.
  `canRequestAds()` returned true without one, which is the correct path for most of the
  world and also the path that proves nothing about the form. Test it with
  `ConsentDebugSettings.setDebugGeography(DEBUG_GEOGRAPHY_EEA)` plus a test-device hash.

**Discovered.**

- **The offline block fired on full wifi.** `AppState.isOffline` also means "our backend
  is unreachable", and the dev server is not deployed. SPEC 6 already said only the OS
  signal should trip the grace; nothing expressed it. Fixed with `AppState.isDeviceOffline`
  — see `decisions.md`, including why no test could have caught it before the fake was
  split in two.
- **The first rewarded ad takes about five seconds to load**, with no visual feedback,
  because nothing calls `AdGate.preload`. The gate warms the next ad after each show, so
  only the first one in a session is slow. The fix is one line in the game layer, below.

**What is left, and who owns it.**

1. **`GameViewModel` never calls `showInterstitial`.** The `level_complete` placement,
   its triple gate and its telemetry are all implemented and tested; the trigger is
   missing because that file was owned by another chunk this session. One line after a
   level is recorded: `adGate.showInterstitial(AdPlacement.LevelComplete)`. Everything
   else — Pro, the kill switch, the new-user grace, N-levels, cooldown, session cap,
   offline — is decided inside the gate.
2. **`AdGate.preload` has no caller.** `adGate.preload(AdPlacement.ContinueLevel)` when
   the lose sheet opens removes the five-second wait from the moment it matters most.
3. **Settings has no "Sodogku Pro" or "Restore purchases" row.** Apple *requires* a
   visible restore control. It exists on the paywall, but nothing in Settings reaches the
   paywall. `:features:settings:impl` needs `implementation(projects.features.paywall)`
   and a row that navigates to `PaywallRoute(trigger = PaywallTrigger.Direct.id)` —
   `Direct` is deliberately exempt from both the trigger list and the session cap.
4. **The two opt-in formats are stubs.** `ads.bannerOnLevelMap` and `ads.appOpenEnabled`
   both default to off, which is why they are stubs and not a gap. `AdNetwork.show(Banner)`
   returns `NotShown` because a banner is a view in a layout, not something you show and
   await — the level map owns its own slot when it wants one. App-open *loads and shows*
   correctly on both platforms but nothing calls it; turning it on means a cold-start hook
   plus honouring `ads.appOpenCooldownHours`, which is a deliberate revenue decision
   rather than leftover work.
5. **The `GoogleMobileAds` SPM package is not in the Xcode project**, so iOS serves no
   ads yet. See `SPEC.md` §20 for the exact steps.

---

## C9 · Telemetry

**Unblocked by** C8 (so ad and IAP events are real).

**Delivers** every event in spec 14, added to `docs/practices/app-events.md` in the same change,
plus the six Grafana dashboards. Includes the difficulty-calibration view, which is the one that
pays for itself.

**Done when** a full playthrough on a device produces the expected event stream in Loki, filtered
by `session_id`, and every dashboard renders with real data.

### The dashboards — **WRITTEN, NOT VERIFIED** (2026-09-08)

The six dashboards SPEC §14 names are committed as JSON under `ops/grafana/`, one file each, plus
a README covering import and prerequisites. **They have never rendered a single real data point,
and cannot until the OTLP credentials exist** (SPEC §20 — `GRAFANA_OTLP_ENDPOINT` /
`GRAFANA_OTLP_TOKEN` are still on the to-provide list). No Sodogku build has shipped telemetry, so
there is no Sodogku data in any Loki anywhere. This half of C9 is queries written and statically
checked, not queries seen working.

| Dashboard | Answers |
|---|---|
| `level-drop-off.json` | How far players get, and which level stops them. Distinct installs per level, clear rate per level, furthest-level histogram, skips per level |
| `difficulty-calibration.json` | **The one that pays for itself.** Clear time, strikes, attempts and paws per designed tier, plus a per-level drill-down. Built to show an inverted step between adjacent tiers — the shape the 2026-09-07 guard bug would have made |
| `ad-funnel.json` | `gate_shown` → `result` by placement and platform: fill rate, no-fill (the giveaway cost), free grants by reason, latency, offline blocks |
| `paywall-conversion.json` | Offers by trigger, conversion, failure codes, restore outcomes, offer rate against ad gates |
| `daily-retention.json` | DAU on the daily, completion rate, streak-length distribution, freeze usage |
| `tutorial-funnel.json` | Step drop-off across the guided first three levels, and where the skippers gave up |

**What is blocked on the credentials, precisely.** Everything about *rendering*: whether a panel
type suits its data, whether a threshold sits at a sensible number, whether the histogram bucket
sizes are right, whether any query is too expensive at real volume, and whether a table's
transformations produce the columns intended. Nobody has looked at one of these boards.

**What is not blocked, and was done.** Every file parses. Every query names an event and
attributes that a `logEvent` call actually emits. The datasource uid (`grafanacloud-logs`) was read
off a live Grafana Cloud stack rather than guessed — it is the uid Grafana Cloud provisions Loki
under in every stack, and the README has a one-liner to rewrite it if a Sodogku stack differs. And
**every distinct LogQL shape on the six boards was sent to a real Grafana Cloud Loki and came back
accepted** — nested `count by`, `unwrap` with a `by` clause, bare `max_over_time`, the `!=""`
presence filter, regex alternation. That stack holds no Sodogku data, so each returned zero rows,
but a malformed query returns HTTP 400 there rather than zero rows, which is what makes the empty
answer worth something. The stack belongs to another project and nothing on it was created,
modified or deleted.

**The test that makes a rename fail.** `DashboardQueryContractTest`
(`:libraries:telemetry:impl`, androidUnitTest) parses every query in `ops/grafana/` and every
`logEvent(...)` in the source tree, and fails if a dashboard references an event or attribute
nothing emits. A panel filtering `strikes_used` against an app emitting `strikes` renders **empty**
and nothing else in the toolchain notices — Loki accepts it, the build passes, and the blank chart
is indistinguishable from "nobody has played yet". Mutation-checked in both directions: renaming
the attribute in the dashboard fails, and renaming it in `GameViewModel` fails.

It is a source scan, because the emitters live in `:features:game:impl` and two `:libraries:*:impl`
modules and only `:apps:*` may depend on an impl — no unit test anywhere can construct a
`GameViewModel` and watch it. So it proves the key is *spelled* the same at both ends, not that the
code path runs. The LogQL reader throws on any construct it does not understand rather than
extracting nothing from it, which is what stops the whole check passing vacuously.

**Three attributes the dashboards need and nothing emits.** Each is a one-line addition at a named
site, listed with what it unlocks in `docs/practices/app-events.md` → "What the dashboards ask for
and cannot have": `difficulty` on `game.level_failed` (blocks a true fail rate per tier — today the
calibration board only sees clears), `trigger` on `iap.purchase_result` (blocks conversion by
trigger, which is the question SPEC §14 asks of the paywall board), and `difficulty` on
`game.booster_no_op`. None was added here — `features/game/impl` and the billing impl were being
edited by other work.

**Doc drift found and fixed.** `app.startup` and `app.jank` are emitted and were in
`app-events.md` nowhere at all, on a page that calls itself the source of truth for dashboard
queries. `onboarding.auth_selected` was listed and does not exist — it went with
`:libraries:identity` in C0. Onboarding's real attributes (`duration_sec`, `skipped_tutorial`) were
undocumented, and `skipped_tutorial` is load-bearing for the tutorial funnel's denominator.

**One live bug documented, not fixed.** `ads.gate_shown` emits its own `is_offline` (the *device*
signal) and `GrafanaLogTree` stamps `is_offline` on every record (the app-wide banner signal). The
event's value wins, only because `forward` applies the per-record stamp before the event's extras.
Two meanings, one key, and the ordering that decides it says nothing about itself.
`EventAttributeShadowingTest` now pins it. The right fix is to rename the event's attribute to
`device_offline`, which is a change at an emit site in `:libraries:ads:impl` and so was left alone.

**Still open for the rest of C9:** the events themselves are only as complete as C8 left them, and
"a full playthrough produces the expected event stream in Loki, filtered by `session_id`" has not
been attempted, because that also needs the credentials.

---

## C10 · Achievements and sharing

**Unblocked by** C6.

**Delivers** `:libraries:achievements` pure fold logic, `:features:achievements` badge grid and
detail sheet, unlock toast, the settings toggle, and the Wordle-style share sheet for both
campaign levels and the daily.

**Done when** every badge in the catalog can be earned in a test, the toggle suppresses display
without stopping recording, and share text renders correctly on both platforms.

### The pure-logic half — **DONE** (2026-09-07)

`:libraries:achievements` (+ impl) and `:libraries:sharing`. 46 tests, detekt clean, green on JVM,
Android and iOS. No UI and no routes: the badge grid, the unlock toast, the settings toggle and the
platform share intent are still to come.

- **21 achievements**, each one `counter >= target` and nothing else. Every one is earned in a test
  from a synthetic history, and — the half that can actually fail — every one is checked *not* to
  be earned one short of its target.
- **A pure fold.** `AchievementEngine.apply(state, result)` takes no clock and no storage, so
  replaying a history reproduces the same badges with the same dates. An unlock is stamped with the
  attempt that earned it, not with the time of the fold.
- **The fact log is the storage.** `achievement_fact` (append-only, unique per attempt) plus
  `achievement_unlock` (what has been announced). Counters are not persisted at all — see
  `decisions.md` for the back-fill and no-drift argument, and for why the idempotency lives in the
  unique index rather than in the fold.
- **The share text cannot leak the solution**, because `ShareResult` has nowhere to put one.

### What the game layer still has to supply

Three fields on `LevelResult` are things the win path knows but does not currently compute. None is
more than a line, and until they are wired the achievements that read them cannot fire:

- `bestCombo` — `GameViewModel` tracks the current combo in `ScoreCard` but never its running max.
  (On a strike-free clear the final combo equals the grid size, so it is only really needed for
  clears that took a strike.)
- `sniffsUsed` / `treatsUsed` — the inventory is decremented, but nothing counts spends *per
  attempt*.
- `isFirstClear` and `previousBestPaws` — read `ProgressRepository.record(levelId)` **before**
  `onCompleted` writes over it. These are what make "levels cleared" count levels rather than
  clears.

`dailyStreakDays` and `localHour` both landed elsewhere while this was in flight:
`DailyRepository.status().streak` and the `DeviceTimeZone` seam C6 added.

### Two things the first draft wanted and the game does not record

- **Marathon** (a 30-minute session) needs session length. Nothing tracks it, and it is not a
  property of an attempt, so it is not in the catalog.
- **Completionist** (three paws on a whole band) needs the band's level count, which lives in the
  pack — the achievements module would have to depend on the content it is meant to be independent
  of. Show Dog (10) and Pedigree (50) replace it.

### The UI half — **DONE** (2026-09-07)

`:features:achievements` (+ impl), the settings entry point and toggle, the share intent, and the
unlock toast as a design-system component. 12 new view-model tests, 3 new share tests, 3 new
settings tests; detekt clean; Android and iOS both compile.

- **The badge grid** shows all 21, locked included, with progress. Locked tiles keep their shape
  and fade their glyph; earned ones take an accent border. The two hidden badges render as `???`
  with a `?` face until earned, and report **no** progress at all — a mystery badge that showed
  "0 / 1" would still say "one clear does it", which is the half of the surprise worth keeping.
- **Copy for all 21** in `strings.xml`, resolved by an exhaustive `when (AchievementId)` in
  `AchievementCopy`. It lives in the feature's **api** module, not its impl, so the win sheet can
  use it — see `decisions.md`.
- **The settings toggle** writes one boolean to `AppData.achievementsVisible` and stops. Recording
  is untouched, which is asserted by a test that turns badges off, moves the history on, and turns
  them back on.
- **The share sheet** is `Intent.ACTION_SEND` through a chooser on Android and
  `UIActivityViewController` on iOS, behind `ShareLauncher` in a new `:libraries:sharing:impl` —
  the same shape as `WebLinkLauncher`. It reaches composables through `LocalShareSheet` rather than
  a ViewModel, because the share's words are string resources.

**Verified on a device** (Pixel 4a, `08291JEC211015`): the grid, the detail sheet, the toast, the
Android chooser opening with the formatted share text, and the toggle removing the Achievements row
from Settings and surviving a force-stop.

**Not wired, and handed over as a diff:** the share button on the win sheet and the unlock toast on
the game screen. `:features:game:impl` was being edited concurrently, so those five hunks were
written and reviewed but not applied, and not compiled in place.

### Discovered: nothing navigates to `SettingsRoute`

`:features:settings` has worked since C11a and is unreachable — the board's gear opens the in-place
`GameDialog.Settings` sheet instead, and no `router.navigate(SettingsRoute())` exists anywhere in
the app. The achievements grid hangs off that unreachable page. One navigation call fixes it, from
a file this chunk was not allowed to touch. Recorded in `decisions.md`.

### Discovered: a schema bump wipes the campaign

`RealAppDatabaseProvider` builds with `fallbackToDestructiveMigration(dropAllTables = true)`, so
adding these two tables took the database from 7 to 8 and drops every existing row with it. Free
today and unrecoverable after release: `level_progress` and `daily_result` are a player's entire
history and there is no account to restore them from. Whatever ships first needs either real
migrations or a deliberate decision that the fallback stays.

---

## C11 · Legal, settings, tutorial

**Unblocked by** C7 (legal versions come from config).

**Delivers** `:libraries:legal` version gate with blocking and non-blocking modes, the full
settings screen, `pages/privacy.html` and `pages/terms.html`, and the guided tutorial for levels
1 to 3 with coach marks.

**Done when** bumping `legal.termsVersion` in the admin console triggers the right prompt on the
next launch, and a fresh install completes the tutorial without a dead end.

### C11a · The settings screen — **DONE** (2026-09-07)

The first half of C11. The legal *gate* (`:libraries:legal`), the hosted pages and the guided
tutorial are still open.

**Delivers** `:features:settings` (+ impl): a full screen with three toggles (vibration, reduce
animations, shapes on colors), terms and privacy opening the URLs from `legal.termsUrl` /
`legal.privacyUrl`, a feedback page, and the version. Plus the feedback page itself, moved out of
`:features:home:impl` so C11 owns the whole surface. 12 view-model tests.

**Outcome.** Verified on an emulator by driving it: every row toggles from a tap anywhere on the
row, the value is on disk before the app is force-stopped and is still there on the next launch,
"Terms of service" hands off to Chrome, and a typed note reaches the confirmation panel and moves
`feedbacksGiven` to 1.

**No new design-system component was needed**, which is the C3a investment paying off — `ListSection`
+ `ListSectionItem` + `ListItemAccessory.Switch/Text/Chevron` already are a settings row, with the
bounce, the dividers, the 58dp minimum height and the switch colours built in. The one addition is a
convention rather than a component: the whole row toggles, not just the switch.

### The bug this chunk found, which was never about settings

**`Cache.update` is a non-atomic read-modify-write, and `AppData` has several writers live at
once.** Flip a setting on a fresh install, send a piece of feedback, relaunch: the toggle is back
off, `feedbacksGiven` is 0 and `screenVisits` is empty. The install-id minter and the review
coordinator write `AppData` during boot from snapshots taken before the screen was up, and whichever
write lands last reverts everything else.

Two things made it invisible until now. Every unit test that covers a toggle uses a single-writer
in-memory fake, so the interleaving cannot happen. And Sodogku had never navigated to a
`TrackableRoute` before — every reachable route was a plain `Route` — so the navigation tracker's
`incrementVisit` write had never actually fired against a live screen.

Fixed by overriding `update` in both cache implementations (`DataStore.updateData` already
serialises read and write) and putting the reason on the interface default so the next
implementation does not inherit it. The same latent bug is in the template.

**Not fully explained:** one relaunch during testing came back with defaults *and a new install id*,
meaning the boot path read the file as absent or corrupt rather than losing a field to the race. It
did not reproduce across five subsequent cycles, warm and fresh, and the app is single-process with
no workers, so a second DataStore holder is ruled out. Worth remembering if progress ever
mysteriously resets.

### Not done here, and why

- **The board still opens its own settings dialog.** `GameDialog.Settings` in `:features:game:impl`
  duplicates every row on this screen. It stays until the header's settings button is repointed —
  `features/game/impl` was locked for editing while this landed.
- **`FeedbackRoute` still lives in `:features:home`**, for the same reason: `:features:game:impl`
  imports it by that name. The screen and view model moved to `:features:settings:impl` and the
  route class did not, which is the one thing about this module that reads wrong. See
  `docs/decisions.md`.

### C11b · The guided tutorial — **DONE** (2026-09-07)

The second half of C11. Fifteen coach marks over campaign levels 1 to 3, driven from `GameState`
so `GameScreen` stays a pure render, on top of the existing `Focus` spotlight rather than a second
overlay system.

**The curriculum** (`Tutorial.kt`, pure functions over a level and the board so far):

- **Level 1** — the three rules one at a time off the permanent rule chips, the free starter dog,
  then the two gestures: one tap crosses a square off, two taps place a dog. Ends on the bones.
- **Level 2** — place a dog and watch auto-mark fire, with the squares *that placement just
  crossed off* lit through the scrim. Then the sniff and the treat.
- **Level 3** — the ring of squares a dog rules out by touching, then "get one wrong on purpose"
  on a lit square that costs no bone (SPEC 10's free wrong tap), what the red X means, and the
  sign-off.

**What it took in the design system.** `Spotlight` grew `targetsAreLive` and `FocusScrim` grew an
`onTargetTap`; `CoachMark` is a new component with a `@Preview`. The scrim also hands `content` the
**union** of the lit rectangles rather than an arbitrary member, which is what makes a card under a
five-square spotlight land under all five.

**Two things the device found that no test would have.**

- **A Compose overlay does not share pointer input with the siblings it covers.** The first pass
  had the scrim decline to consume taps that landed in a hole, on the theory that the board cell
  underneath would then see them. It never does — `Modifier.pointerInput` reports
  `sharePointerInputWithSiblings() = false`, so the cell is simply not in the hit path. The scrim
  now *reports* the tap by key and the feature turns it back into a `CellTapped`, which also keeps
  double-tap timing where it belongs.
- **A card that hangs below its anchor falls off the bottom of the screen** when the anchor is the
  booster row. `CoachMark` flips above the anchor when there is no room below, measured against a
  fixed reserve rather than the card's own height so it does not jump on its second frame.

**Verified by driving it on an emulator**, all three levels start to finish: every coach mark
screenshotted, the lit square accepting a single tap and a double tap through the scrim, auto-mark
lighting exactly the three squares the placement added, the free wrong guess leaving all three
bones, "Replay the tutorial" in Settings coming back to level 1 with the coach marks armed, and a
skip mid-lesson leaving a board that still takes taps and staying skipped across a relaunch.

**Two copy changes made because of what was on screen**, not because a test failed: the starter-dog
body was cut to two lines because at three the card covered the crosses it was describing, and the
adjacency body no longer claims those squares are out "for one reason only" when several are also
out by row or column.

### C11c · The launch gates — **DONE** (2026-09-07)

C11's remaining half, plus the two controls next to it that had the same problem. The legal
version gate landed as `:features:gate` (+ impl) rather than the `:libraries:legal` the plan
named, because it turned out to be one feature with three faces: force update, maintenance and
legal re-accept are the same decision (should this launch proceed?) read from the same config
block at the same moment.

**Delivers**

- **The legal version gate**, both modes. `legal.termsVersion` / `privacyVersion` against a new
  `AppData` record; behind `legal.forceReacceptBelow` blocks, otherwise a dismissible banner
  whose dismissal *is* the acceptance.
- **The force-update gate.** Below `upgrade.minSupportedVersionCode` the app blocks with a store
  link; below `upgrade.softUpdateVersionCode` a dismissible banner, and the dismissal persists
  against the version it was made at.
- **The maintenance screen.** `upgrade.maintenanceMode` is `off` / `banner` / `blocking`, with
  `upgrade.maintenanceMessage` as raw operator text — the one piece of copy in the app that is
  deliberately not a string resource.
- **The review prompt trigger.** `app.reviewPromptAfterLevel` is watched in
  `:libraries:review:impl`; clearing that level asks the existing `ReviewPromptCoordinator`,
  which rations as before.
- One design-system component, `NoticeBanner` in `:libraries:ui`, with previews.

**Eight names left `UNWIRED`** in `ConfigValuesAreReadTest`, taking the debt from 14 to 6:
`LegalTermsVersion`, `LegalPrivacyVersion`, `LegalForceReacceptBelow`, `AppMinSupportedVersion`,
`AppSoftUpdateVersion`, `AppMaintenanceMode`, `AppMaintenanceMessage`, `AppReviewPromptAfterLevel`.

**A blocking gate is rendered instead of the nav host, not navigated to.** There is no back stack
entry to pop, no destination for a deep link to reach, and `App.kt` drops incoming deep links
while a block is up. Verified on a device: two back presses leave the wall in place, and a
`sodogku://game/5` intent starts the activity straight back onto it.

**Everything here defaults to "block nobody", and that is tested rather than asserted in prose.**
`LaunchGatesTest` drives the real `ConfiguredValue` classes from a config map through the
resolver, so "an empty config gates nobody" covers the whole path. Half the file is the opposite
direction — an operator who deliberately asks for the wall gets it — because every fail-open test
on its own passes against a resolver that returns nothing.

### What running it turned up

- **`app.reviewPromptAfterLevel` was wired and inert, and only a device showed it.** The watcher
  is an `AutoInit`, so it constructs in `Application.onCreate`, *before* the config stream has
  emitted — where `AppConfigMap` still answers from the bundled fallback. Reading the threshold in
  the constructor meant the key could never be anything but 10. It now waits on
  `configStream().first()`. This is the same failure `UNWIRED` exists to catch, one layer down:
  the value was injected, named, and read, and still could not be changed. **Anything else
  resolving a config value inside an `AutoInit` constructor has this bug.**
- The banner started as a `Card`, whose 28dp inset is sized for a page section. Over a live board
  it hid the header and half the rule chips. It is a `Surface` with its own padding now.

**Still open in C11:** the hosted `pages/privacy.html` / `pages/terms.html`. The gate and Settings
both open the URLs from `legal.termsUrl` / `legal.privacyUrl`; the pages themselves are not
written.

---

## C12 · Art, theme, accessibility

**Unblocked by** C4, but runs continuously and lands properly once assets arrive.

**Delivers** the real palette and type scale in `:libraries:ui/system`, real dog art, sounds,
haptics, the motion pass, colorblind mode with region glyphs, dynamic type, reduce-motion
handling, and 44pt touch targets verified on the smallest supported device at 10x10.

**Done when** a colorblind simulator pass is clean and every animation respects reduce-motion.

### Accessibility half — **DONE on Android** (2026-09-08)

**What a screen-reader player can do now.** Open the app, reach both header buttons by name, walk
the board square by square hearing "Row 3, column 4, pink" and its state, cross a square off with
the ordinary activation and hear "crossed off" back, and place a dog with double-tap-and-hold or
the actions menu. The board disappears from the tree while anything covers it. SPEC 16 has the
wording, the split between content and state description, and what is deliberately left unlabelled.

**What they still cannot do.** Complete the tutorial's "tap the lit square" steps: `FocusScrim`
owns the touch and knows its targets only as rectangles, so lighting one as an accessible control
needs a label on `Spotlight`. "Skip tutorial" is labelled and reachable, so the guided run can be
left — a worse first five minutes than a sighted player gets, and the first thing to pick up next.
The sniff hint and the last-bone warning also announce nothing when they appear.

**Measured.**

- **Touch targets.** 44pt at 10x10 is geometrically impossible (ten columns of 44 is 440dp). The
  number that matters is not the drawn cell: Compose expands a pointer node's bounds toward 48dp
  and clips at the neighbour, so the 6dp gutter is live and a square's target is **37.3dp at 411dp
  width, 32.2dp at the 360dp floor** against a 26.2–31.2dp drawn cell. Everything that is not a
  board square clears 44dp — header buttons 48, rule chips 48, boosters 48. SPEC 16 states the
  exception rather than a promise the geometry cannot keep.
- **Colourblind glyph contrast** is 1.74:1–2.02:1 composited against its own fill, not the
  1.82–2.02 that was written down. Over the 1.70 floor `RegionPaletteTest` enforces, and confirmed
  legible on a device with the mode on.
- **Frame time**, 40 rapid taps on a 10x10, alternating builds in one sitting: **8.4ms median /
  10.7ms p90 before, 9.2ms / 13.6ms after**, worst frame unchanged at ~20ms, budget 16.7ms. The
  first honest attempt was 11.2ms / 20.4ms and the fix was memoising the `semantics` block; see
  `decisions.md` for why an inline one invalidates a hundred nodes per recomposition.

**Two things this found that were not accessibility bugs.**

- **`Dialog` had no height bound at all.** At the largest system font the score explainer put its
  title under the clock and its only button under the gesture bar. It also means the inner scroll
  `GameDialogs` already had has never done anything.
- **The first fix for that crashed every dialog in the app** — an outer `verticalScroll` hands the
  inner one an unbounded height — through a clean build, clean detekt and a green test run. Only
  opening a dialog on a device caught it.

**Not verified.** iOS, entirely: the semantics are `commonMain` and platform-independent, but
`xcode-select` still does not point at Xcode, so no VoiceOver pass has been run and the app has
never launched on iOS. On Android, TalkBack's *focus* announcement was read out of the semantics
tree rather than heard — `adb shell input` bypasses the accessibility input filter on a Play
system image and `sendevent` needs root, so TalkBack's own gestures cannot be driven from here.
What was heard: TalkBack's speech-output overlay showed **"crossed off"** after a square was
marked, which is the state description doing its job. What was read out of the tree: all hundred
cells' descriptions, `long-clickable="true"` on each, and both header buttons as single 48dp
`android.widget.Button` nodes carrying their labels.

---

## C13 · Store prep — **PARTLY DONE** (2026-09-08)

**Unblocked by** everything.

**Delivers** icons, screenshots, listings, the Play target-audience questionnaire (per the
section 7.1 decision), data safety and privacy nutrition labels, IAP product configuration,
TestFlight and internal track builds.

### Delivered, 2026-09-08

Everything that could be derived from the code without an account, a decision or artwork. Three
documents under `docs/store/`, plus real screenshots.

- **`docs/store/data-safety.md`.** Both forms answered, one row per data type, every row citing the
  file and the mechanism. Traced: `AppData.installId` from mint (`CachedInstallIdProvider`) to all
  three egress points (`X-Install-Id`, the OTLP `install_id` attribute, the Sentry tag) and what
  the server does with it; the Grafana pipeline's payload and resource attributes; Sentry's scope
  and its `sendDefaultPii = false`; AdMob's advertising id, UMP and ATT; Play Billing and StoreKit;
  the feedback path and its session-log attachment; and everything in Room that never leaves. The
  no-accounts decision is stated as the load-bearing fact it is: `Telemetry.setUser` exists and is
  never called, so no email or name reaches any third party.
- **`docs/store/listing.md`.** Title, subtitle, short and long description, App Store keywords with
  character counts, categorisation, and the screenshot plan with what each frame is meant to prove.
  Draft copy, written to be argued with.
- **`docs/store/icons.md`.** What ships today versus what is a template default, and the exact file
  list to replace when the redraw lands.
- **`docs/store/screenshots/android-phone/`**, eight frames at 1080x2160. Captured on the emulator
  with `scripts/dev/drive.py`, cropped to 2:1 because Play rejects anything longer than twice its
  width and the emulator is 2.24:1.

**The kids-versus-general-audience question was not answered.** `data-safety.md` §6 gives the
answer set for each branch instead. On the Apple Kids Category branch the label collapses to "Data
Not Collected" and AdMob, Sentry and the Grafana pipeline all have to come out, so it is not a
form-filling difference; it is a different app.

### Found while tracing, and not fixed here

This chunk changed no Kotlin. Four of these want someone who owns the code:

1. **`android:allowBackup="true"` contradicts what Settings tells the player.**
   `AndroidManifest.xml:10`, with no `dataExtractionRules` and no `fullBackupContent`, and
   `AndroidFileManager` writing to `context.filesDir`. So `app_data` (including `installId`) and
   the Room tables are eligible for Android Auto Backup and device-to-device transfer. Settings
   says "levels, streaks and badges do not survive a reinstall or move to a new phone", and
   `AppCache.kt:92` says the install id "dies with uninstall". On Android neither is reliably true.
   Needs a decision, then either backup rules or a copy change.
2. **The paywall's Restore purchases button may be unreachable enough to fail App Review.**
   SPEC 5.1 says the control "lives in Settings". It does not: Settings has no Pro row, and
   `PaywallTrigger.Direct` is defined (`RealPaywallCoordinator.kt:66`) and never requested by any
   UI. The only ways to the paywall are a coordinator offer after a loss or a skip, and the offline
   block. Apple Guideline 3.1.1 expects a restore path a user can find.
3. **`android.permission.CAMERA` is a template leftover** (`AndroidManifest.xml:4`). Nothing under
   `features/` or `apps/` uses `CameraPreview` or `rememberCameraPermissionLauncher`; they exist
   only as `:libraries:ui` scaffolding, and iOS declares no `NSCameraUsageDescription`. It adds
   nothing to Data safety and it puts "Camera" on the Play listing of a puzzle game.
4. **There is no way to serve a deletion request.** The only identifier is `install_id`, and the
   app never shows it to the player, so a GDPR or CCPA erasure request against Loki or Sentry has
   no key. There is also no in-app analytics opt-out; `telemetry.appEventsEnabled` is an operator
   kill switch. Play's Data safety form asks about deletion directly, so this blocks one answer.

Two smaller ones: `apps/server/.../plugins/RateLimits.kt` still registers `DELETE_ACCOUNT_LIMIT`
and `PLAYER_REPORT_LIMIT` for endpoints deleted in C0, and both the Android manifest and
`Info.plist` still carry comments describing the Supabase OAuth deep links as live.

### Remaining in C13, and what each is waiting on

| Item | Waiting on |
|---|---|
| iOS app icon and the Play 512x512 listing icon | Artwork. Both are still the template's "YOUR APPS IMAGE HERE" placeholder and both are hard submission blockers. `docs/store/icons.md` §5 lists every file. |
| Play feature graphic, 1024x500 | Artwork. Does not exist. |
| iOS screenshots (6.9", and 13" if iPad is supported) | An iOS simulator. `xcode-select` still points somewhere that is not Xcode, so iOS has never run. Android renders must not be submitted as iPhone frames. |
| Onboarding / tutorial screenshot | Ten minutes with a fresh install. Skipped here because `drive.py launch --fresh` wipes app data and another agent was mid-session on the same emulator. |
| `PrivacyInfo.xcprivacy` | The final iOS SDK set, which is blocked on adding the Google Mobile Ads Swift package. |
| Filing either form | The SPEC 7.1 decision, plus a deletion-request answer (finding 4). |
| Play target-audience questionnaire | The SPEC 7.1 decision. |
| IAP product configuration, TestFlight, internal track | Play Console, App Store Connect and AdMob accounts. SPEC §20 lists what to create and where each value lands. |
| Privacy policy and terms text | Nobody has written `pages/privacy.html` or `pages/terms.html`. `data-safety.md` is the input for both; the AdMob disclosure and the session-log attachment on feedback are the two paragraphs that cannot be boilerplate. |

**Not verified.** The screenshot crop offsets are tuned to the current layout on a 1080x2424
emulator; a layout change moves them. No iOS surface in this chunk was verified on a device,
because none can be. The two "linked to the user" answers in `data-safety.md` are the ones most
dependent on Google's and Apple's current wording rather than on our code, and both are flagged
there with a confidence level and where to re-check.

---

## Critical path

```
C0 → C1 → C2 ──┐
     ├→ C3 ────┼→ C4 → C5 → C7 → C8 → C9
     └→ C3a ───┘         └→ C6 → C10
                              C7 → C11
```

C3a runs before C4 and C12 continues it from there. The dog art has landed, so the only remaining
hard external dependencies are the AdMob and store accounts (C8) and the redrawn app icon; every
chunk up to C7 can be built with nothing from outside.

---

## Polish punch list (from the user, 2026-09-07 late)

The brief: **"the perfect bubbly big game app"** — Candy Crush vibe, and it should
look a lot like Meowdoku. Everything below is feel, not function.

| # | Item | Owner | State |
|---|---|---|---|
| P1 | Dialogs have poor padding | me | done — fixed in the DS, so every dialog gets it |
| P2 | The level pane needs a real reward indicator, not a bare emoji | me | done — a glossy chip on every level that pays, and the reward it advertises now exists |
| P3 | Dog idle loops read oddly. Keep `idle` and `look`; drop the shake; the rest occasional at most | me | done — idle and look, with pant occasionally; tilt and flop out |
| P4 | Some X marks cannot be undone. The red X (paid for) and a placed dog must stay; every other mark must clear | me | done — auto-marks clear on tap; the red X and a placed dog do not |
| P5 | Tapping Level and Score opens an explainer; both labels a size bigger | me | done — both open explainers, labels legible rather than 8sp |
| P6 | A rounder, more playful display face. Poppins is already everywhere; `FontFamily.kt` names Baloo 2 and Fredoka as drop-in OFL replacements | me | done — Fredoka on Display, Heading and Label |
| P7 | A better dialog entrance animation | me | done — spring from 0.82 with the scrim fading under it |
| P8 | Keep checking the app against Meowdoku screenshots | me | done — `docs/reference/meowdoku.md`, and the board rebuilt against it |
| P9 | Propose features, argued both ways, leaning conservative, each with a backend-driven-or-not call | me | done — `docs/proposals.md`; four of its findings were bugs and are fixed |
| P10 | **Board state is lost.** Re-picking the level you are already on from the pane wipes every mark and placement. There is also no in-progress snapshot at all, so backgrounding loses the board — C5 promised one and it was never built | me | done — board persists, and comes back on both a relaunch and a level switch |
| P11 | Sniff and Treat buttons want colour and a playful, shiny treatment. Try several, screenshot, judge | me | done — `Modifier.glossy`; blue Sniff, orange Treat, purple ad offer |
| P12 | Splash: the dog head still, centred, with the loader appearing only after ~5s. Better than today's loader-and-words | me | done — the dog alone, spinner only after 5s |
| P13 | Use the supplied backgrounds on onboarding and splash — **blocked, see below** | me | blocked — the files are not on disk |

**Blocked on files that never reached disk.** Three sets of art have now been described in chat
and none of them exists in `art/source/`: the sad-dog still, the bone artwork, and these
backgrounds. Images pasted into a chat message do not reach the filesystem. They need saving into
`art/source/stills/` (or `art/source/backgrounds/`) before any of it can be wired, and until then
those call sites keep their drawn or placeholder versions.

**On P1, asked twice now:** yes, the fix belongs in the design system, and that is where it is
being made — `libraries/ui/.../components/dialog/`. Every dialog in the app should get correct
padding without its call site asking. The reason it kept recurring is that the padding was a
per-call-site concern and nothing failed when a call site forgot.

## Round three, 2026-09-08

| # | Item | State |
|---|---|---|
| R1 | **Score stays 0 in campaign.** The header shows the *current attempt's* score, which starts at zero each level. The ask is one persistent lifetime score, earned from every board including the daily, weighted by hints used and level difficulty | done — see below |
| R2 | Header lift-on-scroll drops a shadow on all four sides; it should only fall below | **DONE** — `elevateOnScroll` draws a bottom-only gradient via `drawWithContent`, replacing `Modifier.shadow` |
| R3 | Tutorial: teach on a **throwaway demo board**, not level 1. Highlight the actual column or colour a rule is about, not just the chip. Block "continue" until the player really has crossed a square off / placed a dog, and let the mark finish drawing first | done — see below |
| R4 | Sniff and Treat read oddly. The wanted look is the retro one from `Workspace/Cards`: the background duplicated and offset down, rather than the gradient-and-sheen currently there. Colours are right | **DONE** (2026-09-08) — see below |
| R5 | A background on the welcome screen — **blocked, files not on disk** | |
| R6 | A dialog when the dog counter (1/4) is tapped | **DONE** — `GameDialog.Dogs`, verified on device |
| R7 | Achievements: the earned border is clipped by the card's own shape; the detail dialog does not animate; grow the catalog toward ~75; more of them hidden until earned | **DONE** (2026-09-08) |
| R8 | Placing a dog auto-crosses too much and does the player's reasoning for them | **DONE** (2026-09-08) — see below |
| R9 | Confirm the daily is fully separate from the campaign, explain that in a first-run dialog, and settle whether any completed board feeds the streak or only the daily | **DONE** (2026-09-08) — see below |
| R10 | Level rewards are too frequent. Front-load them and thin out as levels climb | **DONE** (2026-09-08) |
| R11 | Run the beta workflow locally for a TestFlight build. Needs an App Store Connect record and a working `xcode-select` | |
| R12 | The iOS splash is ugly. Just the still dog head, in the exact spot it sits on the first-launch screen, with the paw-print background fading in behind it — so launch reads as one continuous render rather than a splash then a screen | done except the paw print — see below |
| R13 | Make sure the daily rolls at local midnight and the streak respects time zones. Add a way to restore a broken streak, which probably wants a config key | **DONE** (2026-09-08) — see below |
| R14 | **A deliberate illegal placement did nothing.** `commit` returned early on any auto-marked square, and a placed dog auto-marks its own row, column, region and neighbours — so exactly the squares where an illegal placement lives were unreachable, silently. Fixed. The wider ask stands: the board is the most important screen and wants heavier review, tests and telemetry | |

| R15 | **The bone economy is per-attempt, and it should be global.** `GameState.livesRemaining` resets to three on every `startAttempt`, so bones come back free by starting anything. Three symptoms from one cause: a "keep going" that hands a bone back for nothing; leaving a board and returning with a full three; and the daily and the campaign each having their own three. Bones should be one count, held across boards and refilled by watching an ad | done — see below |
| R16 | **Leaving a lost daily silently forfeits it.** Tapping Levels on a lost daily writes `onFailed`, which spends the day — so the player lands in the campaign and cannot reopen the daily. Writing on leave rather than on the third bone is deliberate (an ad revive can still earn the clear, and `daily_result` is insert-only), but forfeiting has to be a choice the player makes, not a side effect of navigating | done — see below |

**Still blocked on files that never reached disk.** The sad dog, the bone artwork
and now the welcome backgrounds. Four sets described in chat, none on the
filesystem — images pasted into a message do not reach it. They need saving into
`art/source/` before any of it can be wired.

### R1 · One score, and hints that cost — **DONE** (2026-09-08)

The engine was never broken. `GameScreen` rendered `state.score.total` — the
*current attempt's* card — so every campaign level opened at zero, and the daily
looked different only because its header shows a streak.

**The header now shows one lifetime total, derived and never counted.**
`LifetimeScore` (`:libraries:progress`) folds every `LevelRecord.bestScore` and
every `DailyResult.score` into one number, read when a board opens. No stored
tally, for the reason the daily streak has none — see `decisions.md`.

**The double-count rule**, which is the whole of the interesting logic:
`banked - bankedForThisBoard + max(bankedForThisBoard, attemptScore)`. Everything
else the player has banked, plus the better of this board's own best and the
attempt on screen. It climbs with every dog on a fresh level, and a replay moves
it only once the attempt beats the old best. A lost attempt contributes nothing.
Both directions are pinned by tests, because either one alone passes a wrong
implementation; mutation-checked in both (`banked + attempt` fails four, `banked`
fails five).

**Boosters cost points and deliberately cost no paws.**
`scoring.boosterPenaltyRate` (0.15) is multiplicative per sniff or treat spent in
the attempt. Paws are rated on the pre-penalty total, which is identical to
scaling par by the same factor — otherwise three paws would be unreachable for
anyone who took a hint, starting with every player the tutorial *tells* to spend a
sniff and a treat on level 2. Difficulty was already priced by
`Scoring.complete`; nothing was added for it.

**Seen on the emulator, fresh install.** Level 1 cleared for 4,083 with the header
climbing as dogs landed (0 → 1,057 → 4,083); level 2 opened at 4,083 and survived
a force-stop and relaunch; **replaying level 1** for 4,071 left the total at
4,083 rather than 8,154; clearing level 2 took it to 8,346; and replaying level 2
**with one sniff** banked 3,622 against the 4,263 the same board paid unaided —
85%, exactly one booster — with the same two paws and the total correctly
unmoved.

**Also emitted:** `sniffs_used` and `treats_used` on `game.level_completed`, which
SPEC §14 always listed. Without them a fall in median score reads as a difficulty
change when it may be players leaning harder on hints.

**Not verified:** iOS, as ever — `compileKotlinIosSimulatorArm64` is green and the
app has still never run there. And the header's number is plain digits, so a
player who clears most of the campaign will be looking at seven of them; it fits
the layout at 360dp by measurement, but nobody has seen it.

### R7 · Achievements — **DONE** (2026-09-08)

**The clipped border was a shape, not an order.** `Modifier.border(Border)` in the
design system never took a shape, so it drew a *rectangle* — and `BadgeTile`
clipped the card round before drawing it, so the four corners of the rectangle
were the four bits the clip removed. `border(border, radius)` now takes the radius
and the tile passes `Radii.Card` **before** the clip: Compose's border draws its
stroke on top of the content, so a clip after it would shave the stroke's outer
edge instead of leaving it alone. Screenshotted at 3x either side of the change.

**The detail sheet is on the design system's `Dialog` now**, not a hand-rolled
scrim, which was the standing instruction and also the cheapest fix. Frame by
frame off a `screenrecord`, the old one went from nothing to a fully formed card
between two consecutive frames; the new one takes about six, the scrim fading
under a card that scales up and overshoots. Two things came free: back-press
closes it, and the scrim covers the whole window rather than stopping at the top
bar, which it did because it could only cover its own sibling.

**21 badges to 73**, in nine labelled shelves. Sixteen new counters, all folded
from fields the `achievement_fact` log already carries, so every one back-fills
from a player's existing history. The catalog is grouped now because at
seventy-three tiles the ordering could no longer carry the grouping on its own,
and the nine hidden ones are gathered under "Secrets" so a mystery tile cannot be
narrowed down by the shelf it sits on. Full reasoning, including the four ideas
turned down, is in `decisions.md`.

**Stopped two short of 75 rather than pad.** What a further batch needs the game
to *record*, not the fold to compute: the level's **difficulty tier** and the
**local date** are both absent from `LevelResult`, and adding either means editing
`:features:game:impl`, which this chunk stayed out of.

**Measured on the emulator.** Scrolling the 73-tile grid end to end six times:
6.6% janky, p50 16ms, p90 18ms, p99 31ms. Settings, scrolled the same way in the
same build, is 3.1% / 16 / 17 / 19 — so the median is the emulator's floor and the
grid costs a slightly fatter tail. It is still a `NonLazyVerticalGrid`, which
composes all 73 tiles; the honest next step if the catalog grows again is a lazy
grid, and that is blocked on `TopBar` taking something other than a `ScrollState`
for its lift-on-scroll.

**Not verified:** iOS runtime, as ever.

---

**Tracked debt.** `GameViewModel` was 1895 lines with 22 constructor parameters. `GameContract.kt`
(the state, events and actions) and `TutorialRunner.kt` (the script, the position in it, and which
levels have been guided) are out, taking it to ~1490 — better, and still too big. The remaining
seams are the consumable economy and the daily, both of which need `updateState` and so want a
delegate that takes a state transform rather than a plain extraction. Original note follows.

`GameViewModel` is 1636 lines with 17 injected dependencies. It owns the board,
scoring, three consumables, the daily, achievements, the tutorial, progress, board persistence,
display settings and ads, because every chunk that needed the game added itself here. It is the
highest-churn file in the repo and the place quality decays first — five of the seven bugs found
today were in it. It wants splitting once the current wave of work lands; splitting it while three
agents are editing it would cost more than it buys.

**Standing instructions attached to this list:** keep reviewing the code for what
keeps it good, keep writing tests, commit to `main` often but not every edit, and
use sub-agents — including to argue a decision from both sides before taking it.

---

## Where this actually stands, 2026-09-07 evening

Everything through C7 is code-complete. What is left is either waiting on the user or is the
last third of the plan.

**Blocked on the user, not on us:**

- **AdMob and store accounts** (C8). The implementation is built against Google's published test
  ad unit ids and a StoreKit local configuration, so it runs end to end today; swapping in real
  ids is a config change. SPEC §20 lists exactly what to create.
- **Two art files that never reached disk** — the sad-dog still and the bone artwork described in
  chat. The lose sheet still uses `DogPose.HardMode` and the bones are still drawn in
  `GameShapes.drawBone`. Both are one-line swaps once the files exist.
- **The app icon**, which currently has sudoku numerals on it. Sodogku has no numbers.
- **The Fly deploy** (C7's tail). The steps are written out under C7 in order; none of them can be
  done from here without the account.
- **`xcode-select`** points at something that is not Xcode, so **iOS has never been run** — only
  compiled, on every chunk. `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`
  needs the user's password.
- **Docker**, when it is down, self-skips five Postgres and integration-harness tests. They have
  run green when it was up.

**The pattern worth carrying forward.** Four separate bugs this session came from the same
shape — a suspend function computing a value, then reading it back off `state`, which lags
`updateState` by a dispatch. It always compiles and is wrong about half the time depending on
dispatch timing. Three more came from tests that could not fail: assertions of the form "nothing
bad is in the output", which an empty output satisfies perfectly. Both are now written up in
`decisions.md`, and every new assertion of that shape gets a companion that proves the output is
non-empty.

---

## The consumable economy, 2026-09-07 late

**Delivered.** Four config keys that were declared, typed, rendered in the admin console and read
by nothing now decide something.

**`UNWIRED` removed** (`ConfigValuesAreReadTest`), five names:

- `BoostersTreatEveryNLevels` — a first clear on the cadence grants a Treat, and the level pane
  marks every row that pays.
- `BoostersProSniffsPerAttempt`, `BoostersProTreatsPerAttempt` — Pro's per-attempt floor.
- `ProgressionSkipsPerDay`, `ProgressionSkipAfterFailedAttempts` — the skip, its ad and its daily
  allowance.

**`UNWIRED` left alone**, on purpose. `ProgressionLookaheadCount` describes a level map with
silhouettes past the frontier, and the drawer deliberately shows all 500 rows with locks instead —
there is no disclosure window for the key to widen, and inventing one to consume a config value is
how the debt got created. `BoostersAdGrantsPerDay` counts rewarded booster grants per day: that is
ad bookkeeping, it belongs with the other five numbers in `AdStateCache`, and `:libraries:ads` was
out of this chunk's scope.

**Three calls that could have gone the other way**, all written up in `decisions.md`: Pro's
boosters as a floor rather than an assignment (the assignment reading takes consumables away from
a paying customer), the reward paying on a first clear only (otherwise the shortest 4x4 in the pack
is an ad-free treat printer), and the skip's per-day counter keeping a monotonic high-water mark
that the daily challenge deliberately refuses — same mechanism, different blast radius when it
misfires.

**Seen on device** (emulator, fresh install): the reward chips down the pane at 5, 10, 15…; the
Treat actually granted on clearing level 5, with the same chip on the win sheet and the booster
badge going 3 → 4; the muted "Earned" chip on level 5 afterwards; the Skip appearing on a second
failed attempt and not on a first; a real AdMob test rewarded ad playing, the level advancing 6 →
7, and the caption dropping from "3 skips left today" to "2".

**Found on device and left as-is:** `onAttemptStarted` fires again on every *resume*, so a board
re-entered after a process death counts as another attempt and can offer the skip one loss early.
The alternative is a failure column on `level_progress`, which means a schema bump on a database
that still rebuilds itself destructively. Being early with a rescue that already costs an ad and
comes out of a daily allowance is the cheap mistake; the KDoc and SPEC §1.6 both say so.

**Fixed on device:** the claimed chip's first label ("Treat earned") was wide enough to squeeze the
third paw off a completed row. It is "Earned" now, deliberately shorter than the unclaimed label so
the layout only ever gets roomier.

**Not verified:** Pro's per-attempt floor on a device — it needs a purchase, so it is covered by
unit tests only. iOS runtime, as ever (compiles, has never run). And the emulator is shared with a
concurrent chunk, so the run above was interrupted twice by that chunk's maintenance and
force-update gates firing mid-level.

### R15 · One bone count, held across boards — **DONE** (2026-09-08)

`AppData.bones` existed and was already *written* by the booster refill. Nothing
read it. `startAttempt` set `livesRemaining = ScoringConfig.MAX_LIVES` instead,
so every start of every board was a free refill — one line, three reported
symptoms.

It is now the count. `load()` reads it, `startAttempt` carries whatever state
holds, a wrong guess decrements and persists in the same breath, and the refill
tops up and persists. The bug this closes is the whole of the reported one.

**At zero the wall is the game, so the door has to be reliable.** The refill goes
through `AdGate.showRewarded`, which returns `Dismissed` on exactly one path (the
player closing the ad) and grants on every other — no fill, no network, a thrown
SDK, `ads.enabled` false, a config server nobody can reach. SPEC 4.2's fail-open
rule was already there; it is now the only thing standing between a player at
zero and a locked game, so a test walks every non-dismissal outcome and asserts
the write to disk as well as the state. **Verified on the emulator with wifi and
mobile data off**: three bones back, board in play. A board opened at zero shows
the offer straight away rather than waiting for the guess that ends it.

**"Keep going" is gone.** It bought one bone with the same ad as the button
directly above it, which bought three. Strictly dominated, and free by accident
in a build with no inventory, which is exactly what got reported. One revive now,
restoring the whole set, on `continue_level` from the lose sheet and
`booster_grant` from the standing offer.

**Pro gets no bone floor**, and that is the one place the three consumables
diverge. Reasoning in `decisions.md`; the short version is that a floor is a
starting hand for something you choose to spend, and bones are only spent by
being wrong.

**The knock-on that was not obvious.** `MAX_LIVES - livesRemaining` was how the
completion bonus, the achievement fold and the share card asked "how cleanly did
this go", and a global count stops answering that — a mid-board refill would
report a clean sheet and a stash would be worth points. `strikesThisAttempt` now
answers it, carried through a process death by `BoardSnapshot.strikesTaken`,
which replaces the snapshot's `livesRemaining`.

**And one more.** Two boards are live at once whenever the daily is open over a
campaign level, so the ViewModel underneath now follows `AppData.bones` rather
than trusting the count it was left with. Without that its next strike writes a
stale number back over what the daily spent.

### R16 · Forfeiting a daily is a decision — **DONE** (2026-09-08)

`Leave` wrote `daily.onFailed`. Tapping Levels on a lost daily was a forfeit.

The write did **not** move to the third bone — `daily_result` is insert-only and
that would lock the day against a clear an ad revive can still earn, which is the
reasoning the original write was built on. What moved is the trigger: a **Give up
on today** control, the quietest thing on the lose sheet, behind a confirmation
whose filled button is *Keep today open*. Plain `Leave` writes nothing at all,
and the sheet says so above the control.

**The lost daily board is kept** so leaving and coming back hands the position
back rather than a blank board — a blank board would be the fresh run that
one-attempt-per-day exists to refuse, and with the elapsed clock reset it would
be the *better* one. Campaign losses still clear the slot; they have Start over.

**A spent day opens on its result.** `todaysBoard()` returned null once the day
had a row and `load()` turned that into a `NavigateBack`, so the route popped
itself into the campaign with no explanation. `GamePhase.Recap` renders the
stored result over the day's own board, and the drawer's card offers it as "See
today's result".

**Fixed on the way.** `saveBoard` refused every write when the in-progress slot
belonged to another board, which meant a daily opened over an unfinished campaign
level could never save its own loss. A real snapshot takes the slot now; only a
clearing write is held back.

**Seen on the emulator, fresh install, before and after.** Before: play the
daily, run out, tap Levels, land on campaign level 1 with three full bones and a
daily card reading "Out of bones for today" with nothing to tap. After: the same
path lands on campaign level 1 with **zero** bones drawn and the daily card still
offering today's board; reopening it restores the lost position and the refill
prompt; giving up asks first, and the spent day then opens on its recap.

**Not verified:** iOS runtime (Kotlin compiles, as ever). Pro's free revive, which
needs a purchase and is covered by unit tests only. And the ad shown in the runs
above was AdMob's test unit resolving instantly, so the *offline* leg was tested
by turning the radios off rather than by a real no-fill.

### R12 · The launch, as one render — **DONE** apart from the paw print (2026-09-08)

Three separate things were wrong, and only the third was the one the ask named.

**There was no launch screen at all.** `Info.plist` had neither `UILaunchScreen`
nor a storyboard, so iOS painted a blank system screen before handing over. That
white flash against the app's cream was most of what read as ugly, and no amount
of work on the Compose side would have touched it — it happens before any Kotlin
runs. Fixed with a `UILaunchScreen` dictionary pointing at a new
`LaunchBackground` colorset holding `ColorResource.Cream50`.

Colour only, deliberately. `UIImageName` scales its image to fill the screen, so
it cannot place a dog at a fixed offset; it would land at a different size on
every device and the handoff would jump. The system paints flat cream, the app
paints flat cream, and the seam between them is invisible.

Those two cream values live in different files that nothing connects, so
`LaunchScreenMatchesTheAppTest` reads both as text and compares them. Both sides
are parsed rather than imported: `:apps:integration` has no Compose on its
classpath and adding `:libraries:ui` for one colour would put the design system
behind every test in the module. Mutation-checked both ways — drifting the
catalog to white and removing the plist key each fail it.

**The Compose splash faded its own background in.** Even with the launch screen
fixed, `SplashContent` animated one alpha across the whole `Box`, cream included,
so the first 450ms showed the bare window underneath — reintroducing exactly the
flash the launch screen had just removed. It is two alphas now: the cream is
opaque from frame one, and only the dog arrives.

**And the wordmark went.** It was `Brand.B1300` — the template's script face —
centred, which is a different thing in a different place from anything that
follows it. Now the splash draws `DogPose.Still` at the offset the first-run
screen uses, holds, and fades out. Because the welcome screen's own dog is the
same size in the same place, what the fade reveals is a dog that did not move:
the title and tagline arrive *around* it. For a returning player there is no dog
underneath and the overlay just fades — the nested alphas handle that with no
special case.

The two screens agree because they share `DogHeroTopInset`, not because someone
wrote 40dp twice. The failure mode here is silent and visual, and a shared
constant catches it at compile time where a test would only have caught it if
someone thought to write one.

**The paw-print background is not done, and is not startable.** It needs the art,
which is in the same position as the sad dog, the bone artwork and the welcome
backgrounds: described in chat, never saved to disk. The ripple-in load was
raised as a maybe and is deferred with it — it only makes sense as a reveal *of*
that background.

### R13 · The daily's clock, and bringing a broken streak back — **DONE** (2026-09-08)

**Half of this was already true, and the useful outcome is the tests that now say
so.** The daily has taken its date from `dayOf(now, zone)` and its countdown from
`untilNextDay(now, zone)` since C6. Both take the zone as an argument, so the
rollover was already local, already correct across DST, and already re-read on
every pass rather than captured at graph construction. Flying east and flying
west each had a test. Nothing about the clock needed fixing.

What was missing was coverage of the two places the *repository* could have got it
wrong without the calendar noticing:

- `DailyStatus.resetsIn` was only ever asserted at the `untilNextDay` level.
  Nothing checked that the number the card renders is that number. It now is, in
  three zones, plus a test that the countdown shortens through the day and does
  **not** restart when the day is played, which is the shape a "24 hours after the
  last board" implementation would have.
- A board played at 23:59 and one at 00:01 were never asserted to be different
  days. They are now, in New York, two minutes apart, ending at a streak of two.

Both bite: mutating `untilNextDay` to resolve against UTC fails the new
`resetsIn` test alongside the two calendar tests that already existed.

**The restore is new.** The freeze covers exactly one missed day, so a gap of two
ended a streak permanently. `freeze_coversOneDayAndNotTheDayBehindIt` pinned that
as intended behaviour, and it is the hole. `DailyRepository.restoreStreak()`
bridges a whole run of consecutive missed days for one rewarded ad, bounded by
`daily.restoreMaxDays` (3) and `daily.restoreDaysPerMonth` (3). Reasoning for
every number, and for why it stays a fold, is in `decisions.md`.

The shape that mattered: a restore is **rows**, one `DailyOutcome.Restored` per
bridged day, and the fold treats them exactly like `Frozen`. There is no restore
counter anywhere, and the monthly allowance is itself folded out of those rows.
`Restored` is a separate outcome from `Frozen` only so the two allowances can be
counted apart. One budget must not quietly spend the other.

**Measured against a permissive implementation.** Deleting the two bound checks
from `restoreStreak` fails `restore_isRefusedWhenTheGapIsOlderThanItsReach`,
`restore_stopsAtTheMonthlyAllowance` and
`restore_reachIsTheConfiguredNumber_notAHardcodedThree`, which is what those three
exist for. The reach test also pins the number to config rather than to a literal:
the same four-day gap is refused at a reach of 3 and granted at 4.

**Files.** `:libraries:progress` (`DailyResult`, `DailyStatus`, `DailyRepository`),
`:libraries:progress:impl` (`DailyStreak`, `DailyRepositoryImpl`),
`:libraries:config` (`DailyConfigValues`), `:libraries:config:impl`
(`FallbackConfigMap`), `apps/admin/config-manifest-registry.json`,
`:libraries:ui` (`DailyCard`), `:libraries:resources` (`strings.xml`), and five
files in `features/game/impl` kept to the minimum: one action, one ViewModel
method, one drawer parameter, three dialog branches, one screen callback.

**Not verified:** the emulator refuses `adb shell su 0 date` (`adbd cannot run as
root` on this image), so the rollover itself has never been watched happen on a
device. It is unit tests only, which is what the seams were built for. iOS
runtime, as ever. And the ad on the restore path is AdMob's test unit, so the
fail-open branches are covered by tests rather than by a real no-fill.


### R3 · The tutorial teaches on a board of its own — **DONE** (2026-09-08)

Three asks, and a fourth thing that had to come with them.

**A throwaway board.** `TutorialBoard` is a hand-authored 5x5 in no pack, id `0`, opened in front
of campaign level 1 by the same `GameViewModel` on the same route. It cannot be finished — the
script places three of its five dogs — and when the script ends or is skipped the ViewModel swaps
the board under itself and level 1 opens clean, with the player's bones intact. One field,
`rehearsing`, gates the attempt record, the level record, the achievement fold, the bone spend,
the board snapshot and every `game.*` event; `GameState.lifetimeScore` gets one clause so the
headline number holds still, the same sentence already written there for a lost attempt.

The board was found by search, not by hand: random contiguous partitions, filtered to those with
exactly one solution, then the whole script simulated over each to check every lesson still had
squares to point at. 21 candidates survived and the winner is the one whose three rule highlights
overlap least. `TutorialBoardTest` re-proves the uniqueness with `PuzzleSolver.uniqueSolutionOrNull`
and re-walks the script, which is what caught the first draft: three placements on a 5x5 left the
deduction tight enough that `TryAWrongOne` had no wrong square left and the lesson vanished.

**The rules light the board.** `RuleRegion`, `RuleLine` and `RuleTouching` fall through to
`Tutorial.cellsFor` instead of pointing at a rule chip, so they light the colour block, the cross
of row and column, and the ring of five — every square in each already crossed off by that rule,
with the dog in the middle. The starter dog moved to the front of the script, because "here is a
free dog" has to come before three lessons read off it. `NoTouching` was deleted: it lit the ring
around a dog on level 3, which is now exactly what `RuleTouching` does, and teaching it twice was
padding. Fourteen steps, not fifteen. The rule chips lost their `focusTarget` registration with it.

**The gate, and the wait.** A gated step already refused everything but its own gesture; what it
did not do was let the player see the result. `advanceTutorial` now holds for
`Tutorial.settleMillis(trigger)` — the design system's own `MarkDrawMillis` / `PlacementPulseMillis`
/ `ShakeMillis` — before moving. On device the cross draws, the dog lands with its starburst, the
red X shakes, all with the coach mark still up and the square still lit.

**And the fourth thing.** Making those steps mandatory made the screen-reader gap from C12 a wall
rather than an inconvenience, so it is closed: `Spotlight.targetLabel` puts a named, activatable,
semantics-only node over each lit rectangle, and `TutorialCoachMark` synthesises the one or two
taps the step wants. Verified with TalkBack on the emulator — the gated step advances on a reader's
double-tap activation.

**Measured on device, twice.** Once following the whole script: five distinct highlights, three
off-target taps ignored on a gated step, the mark visibly drawing under a still-open coach mark,
bones untouched through the taught wrong guess, score frozen at 0 throughout, then level 1 with
1/4 dogs and three bones. Once skipping from `MarkSquare` in the middle: straight to a clean,
immediately tappable level 1, and a relaunch does not replay anything.

**`tutorial.step_viewed` lost `level_id`** — there is one board now, so it would have been a
constant. `DashboardQueryContractTest` failed on exactly that, which is the entire reason it
exists; the *Step views by level* panel in `tutorial-funnel.json` went with the attribute.

**Not verified:** iOS. The Kotlin target compiles and every semantic is `commonMain`, but nothing
has run on an iOS simulator on this machine, so the coach mark's placement and the VoiceOver path
are untested there.

### R10 · A Treat curve instead of a metronome — **DONE** (2026-09-08)

`boosters.treatEveryNLevels` paid one Treat every fifth level, from 5 to 500. A
hundred free Treats, arriving at the same rate on level 480 as on level 5, which
is backwards twice over: early on a Treat teaches and the player has no stash, so
grants should be *denser* than they were; late on the player is holding several
and a reward on a metronome has stopped reading as a reward.

One integer cannot say "often at first, rarely later", so the key became
`boosters.treatSchedule` — a list of bands, each applying from its `fromLevel`
until the next one starts, paying when the level id is a multiple of that band's
`everyNLevels`. Shipped: every 3rd from level 1, every 6th from 21, every 12th
from 61, every 25th from 151. That is 34 Treats across the campaign against the
old 100, and *six* across the first twenty levels against the old four.

Bands rather than a decay formula, deliberately. `floor(3 * 1.004^level)` would
be smoother and completely opaque — no player can look at it and know when the
next Treat lands, and neither can whoever retunes this against play data. Four
short objects are legible in the admin console, and in the level pane the chips
visibly thin out as you scroll, which states the curve without a word of copy.

Structured, so it is tuned server-side. This is the game's difficulty relief
valve and the right curve is a question for real play data, not for an argument
before release.

**The test that mattered was not the one about the curve.** Swapping an `int` key
for a `json` one turned up a real hole: `FallbackConfigCompletenessTest` resolves
each value against the bundled map and compares it to the declared default, but
`JsonConfigValue` *returns its default when a decode fails*. A misspelled field,
or a typed Kotlin object where the pipeline expects plain collections, produces a
value equal to the default — so the test passes and the key is dead. The new
`BundledJsonConfigDecodesTest` checks the raw subtree instead, and its shape
check is general: `toJsonElement` stringifies anything that is not a map, list or
scalar, so a typed object in `BundledConfigDefaults` compiles, reads correctly,
and silently fails at runtime. Mutation-checked by putting the typed bands in the
fallback — caught only by the new test.

`ShippedConfigSchemaTest` listed the structured keys by hand; it derives them from
the registry now. A hardcoded set has to be edited whenever a structured key is
added, and that edit is indistinguishable from the mistake the test exists to
catch — a scalar key whose type went missing also lands in the unprotected set,
and appending it would look like the same routine update. Verified that retyping
a scalar as `json` now fails `ConfigManifestRegistryDriftTest` instead, which is
the layer that owns registry-versus-declaration agreement.

### R8 · Auto-mark is a setting, not a trimmed rule — **DONE** (2026-09-08)

"Maybe placing a dog shouldn't auto X a bunch of stuff. That makes it too easy for
the user."

The instinct was to trim part of the cascade — keep the mechanical rules, drop the
insightful one. It was measured across all 500 campaign levels instead, counting
the cells each rule *newly* rules out per placement, deduped against everything
earlier placements had already ruled out. The row and the column are the whole
story; the region is about one cell and the diagonals half a cell, flat across
board sizes. The table is in `decisions.md`, because it is the sort of thing that
gets re-litigated from intuition otherwise.

So there is no partial version to ship. Dropping adjacency or the region is
invisible to the player; dropping the line means X-ing seven cells by hand after
every placement on a 10x10, which is bookkeeping rather than thinking. Auto-mark
is all-or-nothing, which makes the right answer a player setting: **Cross off
squares for me**, first row in Settings → Playing, defaulting on. On by default
because it is what every existing player has and because the tutorial teaches
auto-mark as a step, which a default of off would turn into a lie on first launch.

**The work was separating what the game knows from what the player has been
shown.** Those used to be one set. `GameState.autoMarks` is now the deduction and
is computed from the placements on every move whatever the setting says;
`GameState.visibleAutoMarks` is the subset the board draws. `CandidateGrid` and
`Board.autoMarkedCells` are untouched, and a test asserts the ViewModel's cascade
equals `autoMarkedCells` with the setting off — the setting cannot move a baked
difficulty. The hint is the one that would have failed silently: had `useSniff`
reasoned from the drawing, a player with the crosses off would have bought *worse
advice* by asking for less help.

**The tutorial drops two lessons rather than lying.** `AutoMark` would have shown
regardless — its trigger is `Tap`, so the runner does not skip it for having
nothing to point at — and `PlaceAndWatch` exists only to set it up.
`Tutorial.scriptFor(autoMark)` filters both and `StarterDog` gets a second body
string. Reachable in practice: Settings has a Replay the tutorial row.

**Accessibility.** `placeAt` now tests the drawn set, so a screen-reader player
with auto-mark off is offered a placement on every empty square instead of being
locked out of a row, column, region and ring per dog with no announcement saying
why. It also fixes a pre-existing case: an auto-mark the player tapped away read
as empty but still refused the action.

**Telemetry.** `auto_mark` on `game.level_started` / `level_completed` /
`level_failed` / `commit`. On the denominator as well as the numerator, because
the question is a comparison between two populations rather than a fact about one
attempt. On `commit` it is required rather than nice: without it `on_marked` means
two different things in one series.

**No config key.** Argued in `decisions.md` and rejected — the persisted field
cannot distinguish "never touched" from "explicitly on" without going nullable,
the key's only safe value is `true` because of the tutorial, and SPEC 4.4 already
puts anything the tutorial and level 1 need in the binary.

**Not verified:** iOS. The Kotlin target compiles and every change is
`commonMain`, but nothing has run on a simulator on this machine.


### R4 · The chip that said it matched the buttons — **DONE** (2026-09-08)

The buttons themselves were converted earlier: `DeepSurface` gives Sniff, Treat
and the reward button the face-on-a-lip that `BasicButton` already had, replacing
a gradient-and-sheen that read as a shadow blob under a pill rather than as a
thing with thickness.

What was left was `LevelRewardChip`, which stayed on `glossy` while its KDoc
claimed it got "the same candy treatment as the booster buttons". So the one
place in the app that asserted it matched the buttons was the one place that no
longer did.

It could not simply use `DeepSurface`: that is a control, and wrapping a label in
the pressable version to borrow the look would hand a screen reader a button that
does nothing. `Modifier.deepFace` is the static lip, so there is one
implementation and one place to change it.

Verified on a device. The same screenshot confirms R10 on hardware: chips on
levels 3, 6 and 9 where the flat every-fifth rule paid 5 and 10.

### A copy pass on the words a player reads (2026-09-08)

Seven strings used an em dash and one had drifted to British "colour" while the
rule chip on every board says "color". Individually each looks fine, which is why
neither was caught in review; across a screen seven em dashes read as one voice
and two spellings read as two.

`UserFacingCopyStyleTest` holds both, scanning `<string>` bodies only, because
the comments in that file are for whoever edits it next and are not held to
either rule. Mutation-checked both ways.

It needed a build change to work at all. This module's test task declared only
Kotlin as its input, so an edit to `strings.xml` left the task UP-TO-DATE and
the test never ran. That is the same hole the iOS tests had.


### R9 · Saying out loud that the daily costs you nothing — **DONE** (2026-09-08)

The factual half was already true and had been confirmed in code: the daily and
the campaign are separate packs with their own ids, `daily_result` is its own
table, and the streak folds over daily rows only. Clearing campaign levels does
not feed it. Nothing needed changing.

What was missing was saying so. The question a player asks about a daily is
whether today's board costs them anything in the campaign, and until they ask it
they tend to assume it might. A one-time dialog on the first daily visit answers
it, and answering it once is cheaper than a support reply later.

Shown on the recap route as well as the play route: a player whose first visit is
a board they already finished has the same question.

It exposed the same trap that caught R8's auto-mark setting. `startAttempt` builds
a fresh `GameState` rather than copying one, enumerating by hand every field that
carries over, so `showDailyIntro` was silently reset before the first frame and
the dialog never appeared. The field list in that builder is now the third place
this has happened, and it is worth remembering that anything added to `GameState`
which must survive a retry has to be named there too.

Mutation-checked three ways: dropping the carry-over, showing it on every board
rather than the daily, and dropping the persist. Verified on a device, including
that it does not come back on the next launch.

### R14 · Five bugs the review found on the board (2026-09-08)

A read-only review of the puzzle screen. Its findings, and what was done:

**A second commit on a red square charged another bone.** The mirror of the bug
that prompted the review. That one was `commit` refusing too much; this is
`commit` refusing too little, in the one place the refusal was load-bearing.
`toggleMark` returned early on `wrongGuesses` and `commit` did not, so the first
tap on a red square did nothing, which is exactly what makes a player tap again,
and the second one spent another bone. At one bone left it ended the attempt.

The accessibility path never had it: `GameScreen` refuses the placement action on
`wrongGuesses`. The two paths disagreed about what a red square is, and that
disagreement was the bug. They agree now: it is inert.

`theStrikeNonceChangesSoTheSameCellCanShakeTwice` asserted the old behaviour, so
it had to be looked at rather than deleted. It arrived with the original board
build (C4) and its commit message says nothing about repeated wrong taps being
chargeable, so it was protecting the nonce mechanism and describing the
behaviour rather than deciding it. It now uses two different squares, which is
what consecutive strikes actually are.

**`game.commit.on_marked` was exactly inverted.** A commit is the *second* of two
taps, and the first has already written a manual cross or cleared an auto one, so
reading the mark state at the commit site reports the opposite: true on a plain
empty square, false on a crossed-off one. Pinned near 100% either way, so the
series looked healthy. Captured in `tap` before the first tap changes it now.

**The tutorial could write a phantom level 0.** A rehearsal strike is forgiven, so
`remaining` is whatever the player walked in holding. At zero it fell through to
`lose()`, which is the one write on this screen that was not gated on the
rehearsal, on the step that *instructs* a wrong guess. That wrote a `LevelResult`
for level 0 into the achievement log and a `game.level_failed` for a board nobody
chose to play. Reachable: Settings has a "Replay the tutorial" row, and a player
out of bones who declined the ad is exactly who goes to Settings.

**A spent sniff could be laundered by force-quitting.** Two causes, both needed
fixing. `useSniff` used `updateState`, and only `updateBoard` writes the
snapshot. And `BoardSnapshot.isEmpty` counted only placements and marks, so a
board where the player had *only* sniffed was discarded as untouched. A sniff
leaves nothing on the board, which is precisely why it needs saying.

**The bones pill went dead after one tap.** Sniff and Treat stop explaining once
known, because a later tap spends one. A bone is only ever spent by guessing
wrong, so its tap fell through to a branch that clears a prompt nobody opened.
The pill kept its press animation and its label and did nothing for the rest of
the install.

**The test harness that was missing.** Nothing in this module asserted on a single
emitted event. Twelve `game.*` events feed six dashboards and the only thing
holding them was a source-text scan for names. `RecordingEvents` plants a
`LogTree`, so events are now assertable without touching production code. The
inverted `on_marked` is what it was built for.

One mutation check earned its keep beyond finding the bugs: the first draft of
the bones-pill test dismissed the prompt rather than confirming it, so `Bone`
never entered `explainedBoosters`, and the test passed against the bug it was
written for.

Three more from the same review, fixed in a follow-up:

**The board never animated in after level 1.** `nextLevel` swaps the board in
place rather than navigating, and `BoardRows` had no `key`, so cells were
memoised by position and survived the swap. Each cell's entrance is
`remember { Animatable(0f) }` on `LaunchedEffect(Unit)`, so it played once per
process. Worse, a cell that held a dog kept `pop` at 1f, so the new board's
`Empty` state drove it back down and the *previous* level's dogs animated away on
top of the new puzzle. On a size change only the added rows and columns animated.
`key(level.id)` fixes all three. Confirmed on a device: level 2 opens with its
one starter dog and no ghosts from the four on level 1.

**An animated value was read during composition.** `if (pop.value > 0f)` in
`BoardCell` subscribed the content scope to every frame of the pop, which is the
landmine AGENTS.md documents. Gating on `state == Occupied` would also have fixed
it and would have been wrong: `pop` animates *down* when a dog is removed, and
the presence check is what keeps it on screen long enough to shrink away. A
`derivedStateOf` boolean flips twice per placement instead of once per frame and
keeps the fade-out.

**A second wrong tap left the first square off its grid line.** Every cell that
is not the current strike cell is driven to nonce 0, so a second wrong tap within
the shake cancelled the first cell's animation mid-flight and re-entered its
effect with nonce 0 — which returned before resetting, freezing the translation
at up to about six pixels. The reset now happens before the early return.

Still open from the review: `game.level_abandoned` not existing so drop-off has
no denominator, `mode` missing from the booster events, `daily.started` emitted
from one route out of several, and several weak tests in `libraries/puzzle`
named in the review (`CandidateGridTest` passes with the region rule deleted;
two `DeductionSoundnessTest` cases are tautological).
