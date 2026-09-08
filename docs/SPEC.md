# Sodogku

A bubbly, animated logic puzzle game for iOS and Android. The player gets a grid divided into
colored regions and works out where the dogs go. Tap a cell: if a dog belongs there, a dog head
pops in with a bounce and points fly up. If it doesn't, you lose a life. Three lives and the
attempt is over.

Competitor and design reference: **Meowdoku** (`com.oakever.meowdoku`), itself a cat skin on
LinkedIn's *Queens*. We are building the dog version with better polish, real telemetry, a
cleaner offline story, and live-tunable monetization.

---

## 0. Repo status

Generated from the KMP template and trimmed (C0). The identity stack is gone: no accounts, no
Supabase, no user-scoped server state. Progress is device-local.

Chunks C0 through C4a are done and on `main` — the game is playable end to end. C5 (progress and
the level map) is next. `docs/BUILD-PLAN.md` tracks the rest and `docs/decisions.md` records why
anything non-obvious is the way it is.

---

## 1. The game

### 1.1 Rules

Sodogku is the Queens ruleset. An `N x N` grid is partitioned into `N` contiguous colored
regions. The player places `N` dogs so that:

1. Exactly one dog per color region.
2. Exactly one dog per row and exactly one per column.
3. No two dogs touch, including diagonally.

Three rule chips sit permanently under the header, each with a tiny 3x3 diagram illustrating the
rule, exactly as the competitor does. They are not a one-time tutorial element, they stay on
screen for all 500 levels.

**Every puzzle must have exactly one solution.** The tap mechanic tells the player "right" or
"wrong" on every tap, and "right" is only definable if the answer is unique. A two-solution
puzzle makes the game lie.

Minimum grid is 4x4. `N = 2` and `N = 3` have no valid placement under rule 3, so there is no 3x3
level.

### 1.2 Board interaction

| Input | Result |
|---|---|
| Tap a cell | Draws the player's X, stroke by stroke. Tap again to erase it. **Never costs a bone.** |
| Tap the same cell twice inside 320ms | Commits a guess. Correct: dog pops in, points fly up, auto-mark fires. Wrong: red X, shake, a bone. |
| Tap a rule chip | Pulses the cells of the relevant grouping. |

**The safe gesture is the cheap one.** A single tap only ever writes or erases a note, so the
destructive action takes deliberate effort. The second tap is recognised in `GameViewModel` rather
than by `detectTapGestures(onDoubleTap = ...)`: registering that makes Compose withhold the first
tap until the double-tap timeout expires, putting ~300ms of lag on the gesture players perform
dozens of times a board. See `decisions.md`.

**A wrong guess leaves the cell marked**, not cleared. The player has just proved no dog goes
there, and discarding that would make a strike cost information as well as a bone.

**Auto-mark is on by default and is not optional in spirit.** Placing a dog immediately X's its
whole row, its whole column, its entire color region, and its eight neighbors. The competitor
does this and the second screenshot shows it clearly: by six dogs placed on a 9x9, most of the
board is already X'd. This is what makes a 9x9 tractable and it is the single biggest quality
difference between a good implementation and a bad one. It is a settings toggle for purists, but
the default is on and the tutorial teaches it.

Manual X-marking on top of auto-mark is what lets a careful player record their own deductions on
the cells auto-mark cannot rule out.

**Levels 1 to 25 open with one dog already placed.** A teaching aid more than a leg-up: the free
dog fires the auto-mark cascade immediately, so a new player watches the three rules rule cells out
before having to reason about any of them. It scores nothing, so it cannot inflate an early best.

### 1.3 Score

Score is the headline number, shown in the header next to the level. Time is tracked underneath
for records and achievements but is not the primary display.

Per correct placement:

```
points = basePerPlacement × size × comboMultiplier × speedMultiplier
```

- `basePerPlacement` defaults to 100, remote config.
- `size` is the grid dimension, so a 9x9 placement is worth more than a 4x4 one.
- `comboMultiplier` ramps with consecutive correct placements: 1.0, 1.08, 1.16 and so on, capped
  at 2.0. Resets to 1.0 on a strike.
- `speedMultiplier` decays linearly from 1.6 to 1.0 over `scoring.speedWindowMs` (default 8000)
  since the previous placement. Linear rather than exponential so the pressure a player feels is
  proportional to the clock they can see.

The multipliers have to spread **wide**, not just exist. A first pass used gentler numbers
(combo step 0.05, speed max 1.3, lives rate 0.25) and the worst run a player could physically
finish still landed at 63% of par, above the two-paw line — so a single paw was unreachable and
the rating carried no information at all. Compressing the range is the failure mode to watch
whenever these get retuned.

Level completion bonus:

```
bonus = completionBase × size × (1 + (difficulty - 1) × difficultyBonusRate)
                              × (1 + livesRemaining × livesBonusRate)
```

Praise text floats over the board on high-multiplier placements: "Nice", "Great", "Excellent",
"Perfect". Purely cosmetic, thresholds in config.

**Paw rating** (0 to 3) is score-based, not strike-based, which rewards the speed and combo the
score system exists to measure. Par is *derived at runtime* from size and difficulty (see section
3.2), never stored in the pack, so retuning what a three-paw clear means is a config change.
Finishing at all earns one paw; the second and third are fractions of par (0.60 and 0.85).

Records kept per level: `best_score`, `best_time_ms`, `best_paws`.

### 1.4 Lives, failure, and continuing

Three lives per attempt, shown as bone icons in the header (the competitor uses fish; bones are
the dog equivalent). Lives do **not** persist across attempts and do **not** regenerate on a
timer. Every retry starts with three.

On the third strike the attempt ends. The board dims, the answer is **not** revealed, and the
player is offered:

| Option | Cost |
|---|---|
| Continue from here with one life restored | Rewarded ad, or free for Pro. Board state is preserved. |
| Retry from scratch | Free, always. |
| Back to the level map | Free. |

This is the "continue" pattern from endless runners and it is what casual puzzle games actually
ship. It converts better than a hard lock and it never strands a player.

`ads.failureMode` in remote config switches between `CONTINUE` (above, the default) and `LOCK`
(the competitor's harsher model: the level locks and a rewarded ad is required to reopen it). We
ship `CONTINUE` and keep `LOCK` behind the flag so it can be A/B tested without a release.

The failure state, whatever the mode, is **written to disk the moment the third strike lands**,
before any animation. Force-quitting the app is the first thing a motivated player tries.

### 1.5 The three consumables

Bones, Sniffs and Treats deliberately share one shape. Three different economies would be three
things to learn before the puzzle.

| Consumable | What it does | Where it is spent |
|---|---|---|
| **Bone** | A wrong guess costs one. Out of bones ends the attempt. | By guessing wrong, never by tapping |
| **Sniff** | A hint: dims the board and lights the squares deduction has ruled out. | The Sniff button |
| **Treat** | Places one correct dog, free, with no bone at risk. | The Treat button |

- All three start at **3**.
- All three **refill to 3** for a rewarded ad.
- All three may be **held above 3**. Clearing levels grants extra, so a stash is a reward for
  playing rather than a meter that only ever empties. The cap is on the *refill*, not the holding,
  and a refill never reduces a holding.
- Each shows its count as a badge on its button, including at zero. A count that vanishes when it
  runs out makes the button look broken rather than empty, and empty is the state that should
  invite a tap.

**The sniff shows where a dog cannot go, never where one does.** A hint that hands over the answer
ends the puzzle; one that rules squares out leaves the deduction intact and shows the technique
that found them. It picks the squares the *shallowest* remaining reasoning proves, straight out of
the technique-tier solver in `:libraries:puzzle`.

**A booster is never spent for nothing.** If deduction has nothing left to add, the sniff refuses
rather than consuming a charge — a booster that costs something and shows nothing is worse than
one that declines.

#### First use always explains

The first tap of each booster opens an explainer — a dog, a sentence, then **Use one** (if they
hold any), **Watch an ad for 3**, and **Not now**. After that a tap just spends one, and the
explainer only returns when the count hits zero.

Spending a consumable cannot be undone. The first time someone taps an unfamiliar button they
should find out what it costs before it happens.

### 1.6 Skip

After two failed attempts on a level, a **Skip** option appears. Rewarded ad, or free for Pro.
Capped at `progression.skipsPerDay` (default 3) for everyone including Pro, otherwise a Pro user
skips to level 500 in an afternoon and has nothing left.

A skipped level records `state = SKIPPED`: no score, no paws, no time. It stays available on the
map and can be cleared properly later. Skipping unlocks the next level normally.

### 1.7 Grid sizes and the 500-level curve

500 verified levels in a bundled pack, ordered into bands. Within each band the difficulty
sawtooths (each band opens easier than the previous band closed) so the ramp never feels like a
wall.

| Levels | Grid |
|---|---|
| 1 to 10 | 4x4 (tutorial band, 1 to 3 guided) |
| 11 to 40 | 5x5 |
| 41 to 100 | 6x6 |
| 101 to 180 | 7x7 |
| 181 to 280 | 8x8 |
| 281 to 390 | 9x9 |
| 391 to 500 | 10x10 |

Difficulty is not grid size, it is the deduction depth the solver needs, scored 1 to 5:

1. Single-cell region elimination alone.
2. Needs row/column exclusion inside a region.
3. Needs multi-region reasoning.
4. Needs one depth-1 contradiction step.
5. Deeper.

The generator buckets by this score and the shipped ordering interleaves it with grid size.
Telemetry then tells us where the real difficulty is versus the designed difficulty, which is
what we use to re-order the pack in a content update.

### 1.8 Level map and progressive disclosure

Vertically scrolling map grouped by band, with a band header showing progress ("Band 4 · 7x7 ·
12/80"). Completed levels show their paw rating. The current level is the visually loudest tile.
The next 5 are visible silhouettes; everything beyond is a dimmed placeholder. Pro can jump to
the first level of any band it has reached.

`progression.lookaheadCount` (default 5) is remote config, so the disclosure can be widened or
narrowed without a release.

---

## 2. Daily Challenge

One board per calendar day, identical for every player. This is the strongest retention mechanic
in the genre and it costs almost nothing given a bundled pack.

- **Pool.** A separate `daily` pack of 730 levels (two years), generated alongside the campaign
  pack. Kept separate so the daily never spoils a campaign level. When the pool runs out the app
  wraps with an offset, and we ship a new pool in a content update well before then.
- **Selection.** `dailyIndex = (daysSinceEpoch(localDate) + daily.poolOffset) % poolSize`. Local
  device date, no server. Someone can time-travel by changing their clock; that costs us nothing,
  and nothing tries to stop them — see `decisions.md` for what *is* defended.
- **One attempt shape.** Same three lives, same boosters, same scoring. Once completed or failed,
  the day's result is locked in. A failed daily can be continued with a rewarded ad exactly like
  a campaign level, but only once. The lock is the `daily_result` primary key, so it holds
  whatever the clock is set to. The result is recorded against the date whose board was played,
  so an attempt that runs through midnight counts for the day it started and leaves the new day
  unplayed.
- **Streak.** Consecutive days with a completed daily, **recomputed from the stored results on
  every read** rather than counted. Local. A missed day resets it. Today does not have to be done
  yet — a run through yesterday stands all day, including after today has been played and lost.
  A **Streak Freeze** costs a rewarded ad and covers one missed day, capped at
  `daily.freezesPerMonth` (default 2, counted against the month of the day being covered). A
  frozen day bridges the gap without counting toward the total, and is only offered when covering
  it would actually reconnect a run. A day the player attempted and lost is not missed and cannot
  be frozen. This is the single most reliable ad impression in the app.
- **Entry point.** A prominent card at the top of the level map with the day's date, the streak
  count, and a done/not-done state.
- **Sharing.** The daily is what people share, because everyone had the same board.

`daily.enabled` is a remote config kill switch, and `features.dailyChallenge` is the rollout flag.
The card reads both — either one off closes it — so neither is a control that nothing listens to.

---

## 3. Level content pipeline

Levels are generated offline by a JVM tool and shipped as versioned assets. Nothing is generated
on device.

### 3.1 The generator (`tools/level-generator`, JVM only)

1. Pick `N`. Backtrack a random valid dog placement: a permutation `p` of columns where
   `|p(i) - p(i+1)| >= 2` for all adjacent rows.
2. Seed `N` regions, one at each dog cell. Randomly flood-fill unassigned cells from adjacent
   assigned cells until the board is covered. This construction guarantees contiguity and exactly
   one dog per region by definition.
3. **Refine to unique.** A freshly grown board typically has dozens of solutions. Pull a rival
   solution, apply the region move that invalidates *that specific placement*, repeat. Random
   mutation does not converge here (measured: 0 unique 10x10 boards in 60 attempts); targeted
   refinement converts 23 of 40. The seed placement survives by construction, because a region
   move never touches a dog cell.
4. Score difficulty with the technique-tier solver.
5. Compute a par score and derive the three paw thresholds.
6. Deduplicate using a canonical form under the 8 grid symmetries plus region relabeling.
7. Bucket into the curve, emit `campaign.pack` and `daily.pack`.

The generator depends on `:libraries:puzzle` and nothing else, so the solver that verifies the
pack is byte-identical to the solver that runs Sniff on device. That shared dependency is why
`:libraries:puzzle` needs a `jvm()` target alongside android and ios, and why it must not depend
on `:libraries:core` (which has no JVM target, per the template's own decisions log).

### 3.2 Pack format

One pipe-delimited line per level, `id|size|regions|solution|difficulty`:

```
137|7|AABBCCDAABBCCD...|2461503|3
```

Regions are one letter per cell, row-major. The solution is one digit per row giving that row's
column, which works because boards cap at 10.

Packs ship as **generated Kotlin source** (`CampaignPackData.kt`, `DailyPackData.kt`), not as an
asset file. The pack verification test is the only thing standing between an unsolvable level and
the store, so it has to run everywhere; generated source loads identically in a JVM test, on
Android and on iOS with no resource plumbing in the way, and a malformed pack fails compilation
rather than the app.

**No par score or paw thresholds in the pack.** Those are derived at runtime from size and
difficulty using coefficients from remote config (section 4), so retuning what counts as a
three-paw clear is a config change rather than a regenerated pack and an app release.

The solution ships with the level. A determined player can unzip the IPA and read it; for a
single-player game with no leaderboard that is worth nothing, and shipping it makes strike
checking an O(1) lookup with zero runtime solve cost on a cold tap.

### 3.3 The test that makes this safe

One test in `:libraries:levels` loads both shipped packs and asserts, for every level: regions
are contiguous, region count equals `size`, the solver finds **exactly one** solution, and it
equals the shipped `solution`. Runs in CI on every commit. This is the only thing standing
between us and an unsolvable level in production, which is unrecallable without a store release.

---

## 4. Remote config: what is server-driven and what is not

The template's app config (Postgres-backed, edited through the `:apps:admin` web console, with a
bundled fallback map and an offline-first client repository) is the live-ops lever. Getting the
split right is worth more than any other decision in this document, because it determines what we
can fix on a Tuesday afternoon versus what needs a two-week store review.

### 4.1 The rule

**Config owns numbers and switches. The binary owns content and logic shape.**

If changing it requires a new asset, a new string, or new code paths, it belongs in the binary.
If it is a threshold, a cap, a frequency, a URL, or an on/off, it belongs in config.

### 4.2 Two hard constraints

1. **Every key has a bundled fallback.** `FallbackConfigMap` must be complete. The app has to be
   fully playable, correctly monetized, and legally compliant on a first launch with no network,
   forever, if the server never comes back.
2. **Monetization keys fail open toward the player.** A config outage must produce *fewer* ads
   and *fewer* blocks, never more. A server problem must never be able to lock a player out of a
   game they already paid for or already had access to.

Config refresh is throttled and offline-first, so a change applies on the next successful fetch,
not instantly to a session already in progress. For genuine emergencies, kill switches should be
checked at the point of use rather than cached in a ViewModel at screen entry.

### 4.3 The key table

**Ads**

| Key | Default | What it does |
|---|---|---|
| `ads.enabled` | true | Master kill switch. False means no ad calls at all. |
| `ads.newUserGraceLevels` | 5 | No ads at all before this level. |
| `ads.newUserGraceMinutes` | 5 | No ads in the first N minutes of first session. |
| `ads.interstitialEveryNLevels` | 3 | Levels between automatic interstitials. |
| `ads.interstitialCooldownSec` | 60 | Minimum wall-clock gap between interstitials. |
| `ads.interstitialsPerSessionMax` | 8 | Hard ceiling. |
| `ads.appOpenEnabled` | false | App-open ad on cold start. Off until we want it. |
| `ads.appOpenCooldownHours` | 4 | |
| `ads.bannerOnLevelMap` | false | Banner on the map. Never on the board. |
| `ads.failureMode` | CONTINUE | `CONTINUE` or `LOCK`. |
| `ads.offlineGraceLevels` | 3 | |
| `ads.offlineGraceMinutes` | 20 | |
| `ads.rewardedPlacements` | all on | Per-placement enable map. |

**Progression and economy**

| Key | Default |
|---|---|
| `progression.skipsPerDay` | 3 |
| `progression.skipAfterFailedAttempts` | 2 |
| `progression.lookaheadCount` | 5 |
| `boosters.startingSniffs` | 3 |
| `boosters.startingTreats` | 3 |
| `boosters.treatEveryNLevels` | 5 |
| `boosters.adGrantsPerDay` | 5 |
| `boosters.proSniffsPerAttempt` | 3 |
| `boosters.proTreatsPerAttempt` | 3 |
| `boosters.refillTo` | 3 |

**Scoring**

`scoring.basePerPlacement` (100), `scoring.completionBase` (250), `scoring.comboStep` (0.08),
`scoring.comboMax` (2.0), `scoring.speedWindowMs` (8000), `scoring.speedMaxMultiplier` (1.6),
`scoring.livesBonusRate` (0.5), `scoring.difficultyBonusRate` (0.2), `scoring.twoPawFraction`
(0.60), `scoring.threePawFraction` (0.85), and the four praise cutoffs.

**Daily**

`daily.enabled` (true), `daily.freezesPerMonth` (2), `daily.poolOffset` (0).

**Paywall**

`paywall.triggers` (which moments show it), `paywall.offlineBlockEnabled` (true),
`paywall.sessionCap` (2). Price is never in config, it comes from the store.

**Legal**

`legal.termsVersion`, `legal.termsUrl`, `legal.privacyVersion`, `legal.privacyUrl`,
`legal.forceReacceptBelow`.

**App and upgrade**

`upgrade.minSupportedVersionCode` (force-update gate), `upgrade.softUpdateVersionCode`,
`upgrade.maintenanceMode` (`off` / `banner` / `blocking`), `upgrade.maintenanceMessage`,
`app.reviewPromptAfterLevel`.

The upgrade gates sit under `upgrade.` rather than `app.`, which is where an earlier
draft of this table put them. The admin console's kill-switch panel and its manifest
registry were already built against `upgrade.*`, so the `app.*` naming would have left
the one control that has to work in an emergency editing a key no client reads. Every
one of these defaults to 0 or `off`: a force-update gate is the only config value that
can brick every install at once, so a missing, partial or unreachable config has to
resolve to "block nobody".

**Telemetry** (already in the template)

`telemetry.appEventsEnabled`, `telemetry.appEventsSampleRate`, `telemetry.klogForwardingEnabled`.

**Feature flags**

`features.dailyChallenge`, `features.achievements`, `features.sharing`, `features.boosters`. One
per shippable-but-hideable feature, so anything can be dark-launched.

### 4.4 What stays in the binary, and why

| Thing | Why not config |
|---|---|
| Level packs | Content. Needs generation and CI verification. A bad pack is worse than a stale one. |
| Achievement definitions | Each needs an icon and copy, so a new one needs a release anyway. Same argument as the Cards achievements doc. |
| Game rules and scoring formula shape | Only the coefficients are tunable, not the formula. |
| Band structure and difficulty curve | It is the pack. |
| Store product ID | Changing it is a store operation, not a config one. |
| Anything needed before first config fetch | Onboarding, tutorial, level 1. |

---

## 5. Monetization

### 5.1 The product

One non-consumable, **$4.99: Sodogku Pro**. Play calls it a managed in-app product, Apple calls
it non-consumable. It grants:

- No ads, ever.
- Unlimited offline play.
- Free continues, free skips (still under the daily skip cap), free streak freezes.
- 3 Sniffs and 1 Treat at the start of every attempt.
- Jump to the first level of any band reached.

Apple requires a visible **Restore Purchases** control; it lives in Settings. Because the
entitlement lives with the store account, restore already solves most of the "I got a new phone"
problem.

### 5.2 Plumbing (AdMob + native billing)

`:libraries:billing`:

```kotlin
interface Entitlements {
    val isPro: StateFlow<Boolean>
    suspend fun purchasePro(): PurchaseOutcome   // Success | Cancelled | AlreadyOwned |
                                                 // Unavailable | Failed(kind)
    suspend fun restore(): RestoreOutcome
}
```

Sealed per-operation outcomes, not thrown exceptions, matching how the template's identity
library models sign-in. Android wraps Play Billing 7. iOS wraps StoreKit 2 in Swift and is
injected through `IosAppComponentFactory.create(...)`, the template's established pattern for
Swift implementations (no expect/actual).

The entitlement is cached in `AppData` and treated as **true until proven false**. If the store
is unreachable at launch, a paying customer must not see ads. Only an explicit "not entitled"
response clears the cache.

`:libraries:ads`:

```kotlin
interface AdGate {
    suspend fun showInterstitial(placement: Placement): AdOutcome
    suspend fun showRewarded(placement: Placement): RewardOutcome  // Rewarded | Dismissed |
                                                                    // NoFill | Offline | Failed
    fun preload(placement: Placement)
}
```

Both libraries ship fake impls used by `:apps:integration` and every feature test, so all gating
logic is testable without an ad network.

### 5.3 Placements, normalized for the genre

Your original sketch had a forced ad to advance every level. That is unusually punishing and it
is not what shipped casual puzzle games do. The normal shape:

| Placement | Type | Trigger |
|---|---|---|
| `level_complete` | Interstitial | Fires **automatically** after a level, subject to the N-levels / cooldown / session-cap triple gate. The player never waits on it to advance and never opts in. |
| `continue_level` | Rewarded | Third strike, restore a life, keep the board. |
| `booster_grant` | Rewarded | Earn a Sniff or a Treat. |
| `skip_level` | Rewarded | After 2 failed attempts. |
| `streak_freeze` | Rewarded | Cover a missed daily. |
| `app_open` | App Open | Cold start, off by default. |
| `map_banner` | Banner | Level map only, off by default. Never on the board, it wrecks touch targets. |

Three things that matter more than the frequency numbers:

1. **New-user grace.** No ads before level 5 or the first 5 minutes. Day-0 ad exposure is the
   biggest single driver of first-session churn in this genre.
2. **Never on the board.** No banner over a grid with 44pt touch targets.
3. **A failed ad grants the reward.** `NoFill` and `Failed` both succeed. Only a deliberate
   `Dismissed` withholds. An ad network outage must never block a player.

---

## 6. Offline

- Play is fully local. Both packs are bundled, nothing needs the network to solve.
- Pro is unaffected, offline is unlimited.
- Free players get a grace of `ads.offlineGraceLevels` (3) levels or `ads.offlineGraceMinutes`
  (20), whichever comes first, counted from the first ad gate that could not be served.
- When the grace is spent: a friendly blocking screen offering reconnect or Pro. This is the
  highest-intent paywall moment in the app, so instrument it carefully.
- Grace counters persist to disk and reset on a successful ad view, not on reconnect.

The template's `AppState.isOffline` distinguishes "OS says no network" from "our backend is
unreachable". Only the former trips the grace, since ad networks are reachable when our own
server is down.

---

## 7. Legal and compliance

### 7.1 The kids-theming decision, resolve before ads are wired

"Big bubbly kids themed" is an art direction with an expensive policy consequence. If Sodogku is
classified child-directed:

- Google Play Families policy restricts you to certified ad SDKs, no personalized ads, no ad ID.
- Apple's Kids Category bans third-party analytics and advertising outright, killing both AdMob
  and the Grafana pipeline.
- COPPA and GDPR-K attach.

**Recommendation: general audience, not children.** Keep the friendly style, declare 13+ in the
Play target-audience questionnaire, do not enroll in Designed for Families, do not select the
Kids Category. Set AdMob's `tagForChildDirectedTreatment` to not-child-directed and leave
`tagForUnderAgeOfConsent` unset. This is what Meowdoku does. A heavily kid-appealing icon plus a
13+ declaration can still draw a Play review flag, so the art should read "cute", not "preschool".

### 7.2 Consent

- **UMP consent SDK** for EEA and UK, shown before the first ad request.
- **App Tracking Transparency** on iOS, before the first ad request, not at launch. Ask at a
  moment where the value is legible.

Both are hard store requirements and both are easy to forget until review rejects the build.

### 7.3 Terms and privacy acceptance

`:libraries:legal` holds a version gate. Versions and URLs come from config, so publishing new
terms is a config change, not a release. On launch, compare accepted versions in `AppData`
against config: behind `forceReacceptBelow` means a blocking sheet, otherwise a dismissible
banner. Acceptance is recorded locally with a timestamp. No accounts means no server-side record
and no need for one.

The template already generates `pages/privacy.html` and `pages/terms.html` through GitHub Pages.

---

## 8. Achievements

Cards-style but simpler: no accounts means no server fold. `:libraries:achievements` holds a pure
`fold(counters, facts)` and a client-side catalog. Facts are per-attempt records, counters are
derived, everything is local.

**What is stored is the facts, not the counters.** One `achievement_fact` row per finished
attempt, append-only, deduped on a per-attempt key. Every counter is folded back out of that log
on demand, so there is no second copy of a player's progress to drift, and an achievement added in
a later release back-fills from history instead of starting everyone at zero. `achievement_unlock`
records what has already been *announced*, so a catalog change cannot re-toast a two-month-old
badge.

Progress cannot survive a reinstall. That is the honest cost of no accounts and it should be
stated plainly in Settings.

Every criterion is one shape: a counter reached a number. Anything that cannot be phrased that way
becomes a new counter in the fold rather than a new kind of criterion, which is what keeps
progress-toward-unlock a division rather than a special case.

| Achievement | Earned by |
|---|---|
| First Steps / Good Dog / Best in Show / Top Dog | 1, 10, 100, 500 campaign levels cleared (levels, not clears — a replay does not count) |
| Perfect Form | a clear that cost no bones |
| Flawless Ten | 10 consecutive flawless clears; a strike *or* a failed attempt breaks it |
| Comeback | cleared after losing two bones |
| No Help Needed | 25 clears with no sniff and no treat |
| Speed Demon | any level inside 30s |
| Blitz | a 7x7 or bigger inside 60s |
| High Roller | 20,000 points on one level (par on a 10x10 is ~30,500, on a 4x4 ~6,400) |
| Chain of Eight | a run of 8 consecutive correct placements |
| Show Dog / Pedigree | 10 and 50 levels taken to three paws, counted the first time each gets there |
| Grid Seven / Grid Ten | clear a 7x7, clear a 10x10 |
| Fetch Daily | one daily cleared |
| Daily Devotion / Faithful | a 7 and a 30 day daily streak |
| Night Owl (hidden) | a clear between 1am and 5am |
| Early Bird (hidden) | a clear between 5am and 8am |

Two from the first draft did not survive contact with what the game records. **Marathon** (a
30-minute session) needs session length, which nothing tracks and which is not a property of an
attempt. **Completionist** (three paws on a whole band) needs the band's level count, which lives
in the pack — so the achievements module would have to depend on the content it is supposed to be
independent of; Show Dog and Pedigree replace it with count milestones.

The catalog carries **no display copy**, only stable ids. Names and descriptions are string
resources the UI maps with an exhaustive `when`, so adding an achievement fails the build until
somebody writes the words for it rather than shipping a badge captioned
`achievement_top_dog_name`.

Toggleable in Settings, which suppresses toasts and hides the tab but keeps recording, so
re-enabling shows accurate history. The toggle deliberately does not reach the repository.

---

## 9. Sharing

Wordle-style, and the daily is the version people will actually share, because everyone had the
same board.

```
Sodogku Daily · Sep 8
⏱ 1:42   🏆 14,820   🐾🐾🐾   🦴🦴

🟥🟥🟧🟧🟨🟨🟩
🟥🟦🟦🟧🟨🟩🟩
...

sodogku.app
```

The bones the player finished with are on the stats line next to the paws — surviving a 10x10 with
all three is the brag the number exists for. The streak line only appears for the daily.

**The grid is the region layout, and the text generator is never given the solution.** No-spoilers
is a property of `ShareResult`'s signature rather than of anyone's care at the call site: two
players who solved the same board produce byte-identical text, and so does a player who has not
solved it. Every cell of a region renders as the same square, including the one the dog was on.

Unicode has exactly nine coloured-or-neutral square emoji and the top band needs ten regions, so
region 10 is `🔲`. It is the closest pair in the set; on a 10x10 a share reads slightly worse than
a screenshot, which is the price of the format working at all.

Every word in a share — the title, the streak line, the footer — is passed in by the UI from
`:libraries:resources`, already formatted for the locale. `:libraries:sharing` owns the layout,
the emoji and the numbers, and holds no English and no date formatting of its own.

Share from the win sheet, the daily card, and a level-map long-press. `share.tapped` is worth
watching closely, it is the cheapest organic growth channel the app has.

---

## 10. Tutorial

Levels 1 to 3 are guided, not a separate mode. Retheme the template's existing
`:features:onboarding:impl`:

- **Level 1 (4x4):** one rule at a time. Highlight a region, "one dog per color". Only the correct
  cell is tappable.
- **Level 2 (4x4):** rows and columns. Show auto-mark firing, since it is the mechanic players
  most need to understand.
- **Level 3 (4x4):** adjacency and manual X-marking. Allow one wrong tap with no life charged.
- From level 4 the gloves come off.

Coach-mark overlay with a spotlight cutout. Per-step events, because tutorial drop-off is where
casual puzzle games bleed the most installs. Skippable, and re-runnable from Settings.

---

## 11. Settings

- **Game:** sound, haptics, auto-mark, colorblind mode, show timer.
- **Progress:** achievements on/off, reset progress (with a real confirmation), rerun tutorial.
- **Sodogku Pro:** buy, or "Pro active" plus **Restore Purchases**.
- **Legal:** privacy policy, terms, ad partners, reopen consent form.
- **Support:** report a bug (the template's Sentry-backed flow, which already attaches screenshots
  and a session log), rate the app (`:libraries:review`), version and build.

---

## 12. Architecture

```
features/
  game/          + impl    Board screen, GameViewModel, win/lose sheets, boosters
  levels/        + impl    Level map, band headers, daily card
  daily/         + impl    Daily challenge entry, streak, freeze
  settings/      + impl    Settings, legal, bug report, restore
  achievements/  + impl    Badge grid, detail sheet
  onboarding/    impl      Retheme into the tutorial
libraries/
  puzzle/                  Pure Kotlin. Grid, rules, exact solver, technique-tier
                           solver, uniqueness. android + ios + jvm. Zero deps.
  levels/                  Pack loading + the CI verification test
  progress/      + impl    Room-backed records, boosters inventory, streaks
  scoring/                 Pure Kotlin. Score, combo, paw thresholds.
  ads/           + impl    AdGate; AdMob Android, AdMob iOS via Swift
  billing/       + impl    Entitlements; Play Billing / StoreKit 2
  achievements/  + impl    Pure fold + catalog; Room-backed fact log
  sharing/                 Pure Kotlin. The share text. Zero deps, and no way
                           to be handed a solution.
  legal/         + impl    Document versions + acceptance gate
tools/
  level-generator          JVM CLI, depends on :libraries:puzzle
```

`:libraries:puzzle` and `:libraries:scoring` being pure and dependency-free is the load-bearing
decision. Everything interesting (uniqueness, difficulty, hints, pack verification, score) is
testable with no Compose, no DI, no platform code.

Board rendering lives in `:features:game:impl`, but reusable bouncy primitives (`BounceClick.kt`,
`Pulsate.kt`, the color and typography resource system) already exist in `:libraries:ui` and
should be extended there.

### 12.1 GameViewModel

```
State  = board, placedDogs, autoMarks, manualMarks, livesRemaining, score, combo,
         elapsedMs, sniffs, treats, phase (Playing | Won | Lost | Paused)
Action = CellTapped, CellLongPressed, SniffUsed, TreatUsed, Restart, Continue,
         Skip, Pause, Resume, TimerTick
Event  = ShowRewardedAd(placement), ShowInterstitial, NavigateNext, ShowPaywall,
         PlaySound, Haptic, ShowShareSheet, FloatPoints(cell, points, praise)
```

Timer runs off a monotonic clock and pauses on background, so a backgrounded app cannot silently
ruin a best time.

---

## 13. Persistence

### 13.1 Room: `level_progress`

`level_id` (PK), `state` (`LOCKED` / `UNLOCKED` / `IN_PROGRESS` / `COMPLETED` / `SKIPPED`),
`best_score`, `best_time_ms`, `best_paws`, `attempts`, `boosters_used_on_best`,
`first_completed_at`, `last_played_at`.

### 13.2 Room: `daily_result`

`date` (PK, local ISO date), `levelIndex`, `outcome` (`Completed` / `Failed` / `Frozen`), `score`,
`paws`, `timeMs`.

`outcome` replaces the `completed` + `froze` pair: two booleans describe four states and one of
them is meaningless. A day with **no row** is a missed day, which is the only thing a `Frozen` row
may stand in for.

Insert-only, with the primary key as the one-attempt-per-day lock. The streak is **not** stored —
it is folded out of this table on every read. See section 8's counters for the same rule and
`decisions.md` for why.

### 13.2a Room: `achievement_fact` and `achievement_unlock`

`achievement_fact`: `id` (PK, autoincrement — the fold is order-dependent), `key` (unique: mode,
level and finish time, so an at-least-once caller cannot double-count), `level_id`, `mode`, `size`,
`completed`, `score`, `paws`, `time_ms`, `strikes`, `best_combo`, `sniffs_used`, `treats_used`,
`first_clear`, `previous_best_paws`, `daily_streak_days`, `local_hour`, `finished_at`.

`achievement_unlock`: `achievement_id` (PK), `unlocked_at` — the timestamp of the attempt that
crossed the threshold, not of the write.

Counters are **not** stored. See section 8.

### 13.3 In-progress board

Saved separately: placements, marks, lives, score, combo, elapsed. Backgrounding mid-level and
returning an hour later resumes exactly where you were. Puzzle players expect this and its
absence reads as a bug.

### 13.4 `AppData`

`currentLevel`, `hasCompletedTutorial`, `soundEnabled`, `hapticsEnabled`, `autoMarkEnabled`,
`achievementsEnabled`, `colorblindMode`, `showTimer`, `acceptedTermsVersion`,
`acceptedPrivacyVersion`, `cachedAdFreeEntitlement`, `sniffCount`, `treatCount`,
`skipsUsedToday`, `skipsDate`,
`adGrantsToday`, `adGrantsDate`, `levelsSinceLastInterstitial`, `lastInterstitialAt`,
`interstitialsThisSession`, `offlineGraceLevelsUsed`, `offlineGraceStartedAt`,
`firstLaunchAt`, `totalPlayTimeMs`, `sessionsPlayed`, `longestSessionMs`.

`dailyStreak`, `lastDailyDate` and `freezesUsedThisMonth` were listed here and are **not** stored.
All three are derivable from `daily_result`, and a derived number cannot drift out of step with the
history it claims to summarise.

---

## 14. Telemetry

The template already has Sentry + Loki + Tempo pivoting on `session_id`, with conventions in
`docs/practices/app-events.md`. Add these in the same change that introduces each one.

**No per-tap event.** Forty taps per level across 500 levels is a volume and cost problem.
Aggregate into the completion event.

| Event | Attributes |
|---|---|
| `game.level_started` | `level_id`, `size`, `difficulty`, `attempt_number`, `is_retry`, `mode` (campaign / daily) |
| `game.level_completed` | `level_id`, `mode`, `duration_ms`, `score`, `paws`, `strikes_used`, `sniffs_used`, `treats_used`, `is_first_clear`, `is_personal_best`, `taps_total`, `manual_marks` |
| `game.level_failed` | `level_id`, `mode`, `duration_ms`, `dogs_placed`, `attempt_number` |
| `game.level_abandoned` | `level_id`, `duration_ms`, `dogs_placed` |
| `game.continued` | `level_id`, `source` (ad / pro) |
| `game.skipped` | `level_id`, `failed_attempts` |
| `game.booster_used` | `level_id`, `booster`, `remaining` |
| `daily.started` / `daily.completed` | `date`, `streak`, `score` |
| `daily.streak_broken` | `previous_streak` |
| `daily.freeze_used` | `streak` |
| `ads.gate_shown` | `placement`, `level_id`, `is_offline` |
| `ads.result` | `placement`, `outcome`, `latency_ms` |
| `ads.offline_block` | `level_id`, `grace_levels_used` |
| `iap.paywall_shown` | `trigger` |
| `iap.purchase_result` | `outcome`, `error_kind` |
| `iap.restore_result` | `outcome` |
| `achievement.unlocked` | `achievement_id`, `level_id` |
| `share.tapped` | `mode`, `level_id`, `paws` |
| `tutorial.step_viewed` / `tutorial.completed` / `tutorial.skipped` | `step` |
| `legal.terms_prompt_shown` / `legal.terms_accepted` | `terms_version`, `blocking` |

### Dashboards

1. **Level drop-off curve.** Players reaching level N. This is *the* metric for a level-based
   puzzle game; the cliff tells you which level is killing retention.
2. **Difficulty calibration.** Per level: fail rate, median clear time, median score, median
   strikes, booster rate, plotted against the generator's designed difficulty. Divergence drives
   the next content update's reordering.
3. **Ad funnel.** `gate_shown` to `result=rewarded`, by placement and platform. Watch nofill: a
   high rate means we are giving rewards away.
4. **Paywall conversion.** By trigger. Tells you which moment actually sells Pro.
5. **Daily retention.** DAU on the daily, streak length distribution, freeze usage.
6. **Tutorial funnel.** Step drop-off across levels 1 to 3.

---

## 15. Server scope

Minimal. `:apps:server` on Fly, one small instance plus a small Fly Postgres.

Postgres is required, not optional: the template's remote config is Postgres-backed
(`PostgresAppConfigSource`, migrations `V4` to `V6`) and the admin console writes through it.
Config is the reason the server exists, so the DB comes with it.

Ships: `GET /_health`, the app-config endpoints, and the `:apps:admin` console.

Removed from the template: migrations `V1__profiles.sql`, `V2__fk_auth_users.sql`,
`V3__player_reports.sql`, plus `MeRoutes`, `PlayerReportRoutes`, the profile and moderation
repositories, and the Supabase JWT setup. There are no users, so there is nothing to
authenticate. The admin console keeps its own admin auth.

The client treats the config endpoint as fully optional. A server outage must be completely
invisible to a player.

---

## 16. Accessibility

The core mechanic is color, so this is a design constraint, not a checkbox.

Roughly 8% of men have red-green color vision deficiency, and no 10-color palette survives
deuteranopia.

- **Colorblind mode** overlays each region with a distinct light glyph (paw, bone, star, heart)
  so the glyph, not the hue, is the region identity.
- Pick the base palette for lightness separation as well as hue separation.
- Never encode anything only in color: rule chips, strike feedback, and region highlight all need
  a shape or motion component.

Also: 44pt minimum touch targets means a 10x10 board fills the width on a small phone; dynamic
type in header and settings; and "reduce motion" degrades bounces to fades rather than removing
feedback.

---

## 16a. The dog

The art set is in hand (`art/source/`, seven 1024px stills and six 512px animated WebP loops).
What ships is downscaled per use case, exposed through one design-system component:

```kotlin
Dog(pose = DogPose.Solved)
```

`DogPose` splits into board weight and hero weight, and the split is a **performance boundary, not
a stylistic one**:

| Weight | Poses | Shipped at | Why |
|---|---|---|---|
| Board | `Still`, `Focused` | 192px | A cell on a 10x10 is ~108 physical px, and up to 100 are on screen |
| Hero | `Solved`, `Thinking`, `Paused`, `HardMode` | 512px | Never larger than ~256dp, only ever one on screen |

Shipping the originals everywhere would be 8.3MB of assets and roughly 28MB of decoded bitmaps for
one puzzle. Downscaled it is 620KB. The enum is the guardrail: callers pick a mood, not a file.

### The Focus system

Dimming the screen and lighting one thing is used by three features already — the last-bone
warning, the sniff hint, and the tutorial coach marks to come — so it is one design-system
primitive rather than three overlays.

`Modifier.focusTarget(key)` registers where a thing is; `FocusScrim` dims everything and punches
holes with `BlendMode.Clear`. The blend-mode approach is what lets a spotlight cover **several
scattered targets at once**, which drawing four rectangles around a single rect cannot: the sniff
lights four unrelated squares on the board.

### The clips ship as sprite sheets, not as animated WebP

A placed dog is alive: it looks around and blinks, from `dog_look`. It gets there via a build-time
sprite sheet (`scripts/build_dog_sprites.py`), not by playing the WebP.

**Why not play the WebP.** Animated WebP does not render on Compose Multiplatform iOS. Coil 3's
animated path goes through Android's `ImageDecoder`; Skia hands back a single frame. Shipping it
would mean an animation that works on Android and silently freezes on iOS.

**Why a sheet is affordable where the clip was not.** The source is 512px and 60 frames, roughly
60MB decoded. Packed at board resolution and subsampled to 30 frames it is a **260KB image, decoded
once and shared by every dog on the board**. That is the whole trick: only `N` dogs are ever placed
(at most ten), and they all read from one bitmap.

Each cell starts at a different frame, or a board of dogs blinks in unison and reads as a
rendering glitch rather than a row of animals.

Regenerate with:

```bash
./scripts/build_dog_sprites.py art/source/clips/dog_look.webp \
  libraries/resources/src/commonMain/composeResources/drawable/dog_look_sheet.png \
  --size 128 --frames 30
```

`FrameCount` and `Columns` in `AnimatedDog.kt` must match the script's arguments.

**Still no Coil dependency.** Static PNGs go through Compose Resources' `painterResource`, and the
sheet is an `imageResource` drawn with `drawImage`. No third-party image library at all.

### Asset gap to resolve

`dog-appmark.png` is the intended app icon and it has a **sudoku grid with the numerals 3, 7, 1
and 9 in it**. Sodogku has no numbers — that is the whole pitch. A store icon promising a number
puzzle mis-sells the app to everyone who taps it and disappoints the ones who install. Needs a
redraw with a colour-region grid instead of digits.

---

## 17. Visual direction

Candy-crush adjacent: generous corner radii, soft drop shadows, saturated but not neon, a rounded
display font (Baloo 2, Fredoka, or Nunito, all OFL and safe to bundle).

Build the Sodogku theme as a new palette and type scale inside `:libraries:ui/system`, and add
game components (`BoardCell`, `RuleChip`, `LifeRow`, `PawRating`, `LevelTile`, `ScoreCounter`,
`FloatingPoints`, `BoosterButton`) to the existing catalog so they get preview coverage.

Motion: every interaction gets a spring, everything under 300ms, everything cancellable. A player
clearing ten easy levels does not want to sit through animations.

### The design system has to make the right thing the easy thing

A new screen should get correct sizing, type, spacing and press feedback **by default**, not
because whoever wrote it remembered to. Concretely:

- **Nothing reaches past the design system.** No raw Material components, no hardcoded `dp` or hex
  colours in a feature module. Screens compose `Screen(...)`, DS buttons, `Text` with an
  `AppTheme.typography` token, and `Dimension.*` spacing.
- **Every tappable bounces.** `bounceClick()` is baked into the DS buttons and the board cell, so a
  feature never wires press feedback by hand.
- **Every asset goes through a component.** `Dog(pose = ...)`, never a raw drawable reference, so
  the board-versus-hero sizing decision cannot be got wrong at a call site.
- **Detekt enforces what it can.** `VerifyStrings` already fails inline copy. The same mechanism
  should grow rules for raw `dp` literals and direct Material imports inside `features/` once those
  show up as recurring mistakes — a rule is cheaper than a code review habit.
- **New components land in the catalog with previews** in the same change, so the next screen finds
  them instead of reinventing them.

This is why the theme work is scheduled *before* the board (C3a) rather than as a polish pass at
the end. Every screen built against a placeholder theme is a screen that has to be revisited.

---

## 18. Non-goals for v1

- No accounts, no sign-in, no cloud save. Progress is device-local, stated plainly in Settings.
- No social features, no friends, no leaderboards.
- No server-delivered level packs.
- No cosmetics economy or dog skins (strong v2 candidate, pairs well with the booster economy).

**Switching phones.** The Pro entitlement travels via store restore with no work. Only progress
is stranded. The cheap v2 fix is an export/import code: the client serializes progress into a
short opaque string the player pastes on the new device. No server, no account. That beats the
manual "give us a user id" idea, which is more support burden than it is worth.

---

## 19. Open questions

**Q1. Was the competitor's second booster a life-restore?** I specced Treat as a life restore
based on the two badged icons in the bottom bar. If it turns out to be something else (undo,
reveal-a-row, shuffle), swapping it is a small change.

**Q2. App-open ads.** Specced and built, defaulted off. They monetize well and they annoy well.
Worth turning on later with a cooldown once retention is measured, not at launch.

**Q3. Does the daily use campaign difficulty or its own curve?** Specced as its own pool at
moderate difficulty so it stays a 3-to-5-minute daily habit rather than a wall.

---

## 20. What I need from you

### Art and audio

- [x] ~~**Dog head asset.**~~ Delivered 2026-09-07: seven stills and six animated WebP loops.
      Landed as `Dog(pose = ...)` with six poses; see section 16a.
- [ ] **Redrawn app icon.** `dog-appmark.png` has a sudoku grid with the numerals 3, 7, 1, 9 in it.
      Sodogku has no numbers, so the icon mis-sells the app on the store page. Needs a
      colour-region grid instead of digits.
- [ ] **The sad-dog still and the bone artwork** shown in chat never reached disk — only the
      original seven stills and six clips are in `art/source/`. Drop them into
      `art/source/stills/` and they can replace the drawn bones and the lose-sheet pose.
- [ ] Which clips are worth sheeting beyond `dog_look`. `flop` for the lose sheet and `bark` for a
      win are the obvious candidates; each costs about 260KB.
- [ ] **Bone icon** for lives, **paw** for the rating, and icons for the two boosters.
- [ ] **App icon** (1024x1024), Android adaptive icon layers, notification icon.
- [ ] **Region palette:** 10 colors plus the colorblind glyph set. I can propose a first pass if
      you would rather react to something than start blank.
- [ ] **Font choice.**
- [ ] **Sounds:** dog place, strike, level win, praise sting, button tap, achievement unlock,
      booster use. Seven files, and they matter more than you would think for the feel.
- [ ] Empty-state, locked-level, and offline-block illustrations.

### Accounts and credentials

- [ ] Bundle IDs (proposing `com.sodogku` for both).
- [ ] Play Console app, AdMob app ID, and one ad unit per placement per platform (interstitial,
      rewarded, app-open, banner).
- [ ] App Store Connect record and the StoreKit non-consumable product ID.
- [ ] Play managed product ID. Keep both IDs identical if the stores allow.
- [ ] Sentry DSN.
- [ ] Grafana Cloud OTLP endpoint and token.
- [ ] Fly app name and org.
- [ ] Support email.
- [ ] Domain, for the share footer and privacy pages.

### Copy

- [ ] Privacy policy and terms text, naming AdMob as a data recipient and covering the ad ID. I
      can draft both from the `pages/` scaffolding, but a human should read them before they go live.
- [ ] Store listing: title, short and long description, keywords.

### Decisions

- [ ] Section 7.1, kids versus general audience. Gates the entire ad business model.
- [ ] Q1 through Q3 above.
