# Sodogku build plan

Chunks are ordered by risk, not by visibility. The riskiest logic in the app (uniqueness,
difficulty, level generation) is pure Kotlin with no UI, so it goes first and gets fully tested
before anything is rendered. By the end of C4 there is a playable game with no monetization and
no network, which is the only point at which "is this actually fun" can be answered.

Each chunk states what unblocks it, what it delivers, and how we know it is done.

---

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

## C3 · `:libraries:scoring`

**Unblocked by** C1 (needs nothing from C2).

**Delivers** pure Kotlin scoring: per-placement points with combo and speed multipliers,
completion bonus, praise-threshold classification, paw rating from score against pack thresholds.
Every coefficient injected as a config-shaped data class, never a hardcoded constant.

**Done when** unit tests pin the formula against worked examples and confirm a strike resets the
combo.

---

## C4 · `:features:game` — the playable board

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

---

## C5 · Progress and the level map

**Unblocked by** C4.

**Delivers**

- `:libraries:progress` + impl: Room tables `level_progress` and `daily_result`, booster
  inventory, in-progress board snapshot.
- Resume: backgrounding mid-level and returning restores placements, marks, lives, score, elapsed.
- `:features:levels`: scrolling map, band headers with progress, paw ratings on completed tiles,
  progressive disclosure with a configurable lookahead.
- Unlock flow, skip flow (still against the fake `AdGate`).

**Done when** progress survives process death, resume is exact, and the map correctly reflects
every level state including `SKIPPED`.

---

## C6 · Daily challenge

**Unblocked by** C5.

**Delivers** date-seeded selection from `daily.pack`, the daily card on the map, streak counting,
streak freeze (fake ad for now), one-attempt-per-day locking, `daily_result` persistence.

**Done when** the date rolls correctly across midnight, a missed day breaks the streak, a freeze
covers exactly one day, and a completed daily cannot be replayed for a better score.

---

## C7 · Remote config

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

---

## C11 · Legal, settings, tutorial

**Unblocked by** C7 (legal versions come from config).

**Delivers** `:libraries:legal` version gate with blocking and non-blocking modes, the full
settings screen, `pages/privacy.html` and `pages/terms.html`, and the guided tutorial for levels
1 to 3 with coach marks.

**Done when** bumping `legal.termsVersion` in the admin console triggers the right prompt on the
next launch, and a fresh install completes the tutorial without a dead end.

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
C0 → C1 → C2 ─┐
     └→ C3 ───┴→ C4 → C5 → C7 → C8 → C9
                        └→ C6 → C10
                             C7 → C11
```

C12 runs alongside from C4 onward. The first hard external dependency is the dog art (C12) and the
AdMob and store accounts (C8); everything up to C7 can be built with nothing from outside.
