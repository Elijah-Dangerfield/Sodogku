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

## C6 · Daily challenge — **logic DONE** (2026-09-07), card UI outstanding

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

### What is left

- **The card itself**, and the route that opens the board from `packIndex`. Note that daily and
  campaign level ids share a number line (daily level 7 is not campaign level 7), so whatever
  launches the game needs the *pack* as well as the id — `GameViewModel` currently resolves its
  level against `LevelPacks.campaign` only.
- **Telemetry.** `daily.started` / `daily.completed` / `daily.streak_broken` / `daily.freeze_used`
  are specced in section 14 and not emitted; the streak is available at every one of those call
  sites.
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

**Not verified:** nothing was run on a device — there is no UI to run. The Room queries themselves
are unexercised for the same reason `level_progress`'s are (a KMP Room database needs a native
driver the host JVM test source set does not have); the tests drive an in-memory DAO that
reproduces the `IGNORE` conflict behaviour the schema depends on. The first thing the card should
prove on device is that a completed daily survives a process death, because that is the one path
these tests cannot see.

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

## C8 · Ads and billing

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

---

## C9 · Telemetry

**Unblocked by** C8 (so ad and IAP events are real).

**Delivers** every event in spec 14, added to `docs/practices/app-events.md` in the same change,
plus the six Grafana dashboards. Includes the difficulty-calibration view, which is the one that
pays for itself.

**Done when** a full playthrough on a device produces the expected event stream in Loki, filtered
by `session_id`, and every dashboard renders with real data.

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

---

## C12 · Art, theme, accessibility

**Unblocked by** C4, but runs continuously and lands properly once assets arrive.

**Delivers** the real palette and type scale in `:libraries:ui/system`, real dog art, sounds,
haptics, the motion pass, colorblind mode with region glyphs, dynamic type, reduce-motion
handling, and 44pt touch targets verified on the smallest supported device at 10x10.

**Done when** a colorblind simulator pass is clean and every animation respects reduce-motion.

---

## C13 · Store prep

**Unblocked by** everything.

**Delivers** icons, screenshots, listings, the Play target-audience questionnaire (per the
section 7.1 decision), data safety and privacy nutrition labels, IAP product configuration,
TestFlight and internal track builds.

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
