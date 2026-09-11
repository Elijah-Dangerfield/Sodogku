# What Sodogku offers

Every player-facing feature, as the code behaves today. Each section says what the
feature does, the rules it keeps, which of its numbers are remote config, and where
it lives.

This is a description, not an argument. **`docs/decisions.md` is where the reasoning
lives**, and it is worth reading before changing anything here, because most of the
numbers below were moved at least once and the entry says what broke. Defaults quoted
here are the shipped fallbacks; anything marked as config can be different on a device
that has reached the server.

Sodogku is the Queens ruleset with dogs. An `N x N` grid is split into `N` coloured
regions and the player puts `N` dogs on it. There are no accounts, no cloud save and no
server the game needs; see [What the game does not have](#what-the-game-does-not-have).

---

## The board

**The rules.** Exactly one dog per region, one per row, one per column, and no two dogs
touching, including diagonally. A board is won when every row holds a dog and no rule is
broken. `Board.ruleViolations` in
`libraries/puzzle/src/commonMain/kotlin/com/sodogku/libraries/puzzle/Board.kt` enforces
all three; the diagonal rule falls out of `abs(col[r] - col[r-1]) < 2` between adjacent
rows.

**Sizes.** `Board.MIN_SIZE` is 4 and `Board.MAX_SIZE` is 10. Four because 2x2 and 3x3
have no legal placement at all. Ten because the solver's bitmasks are that wide, the
region alphabet `REGION_LETTERS = "ABCDEFGHIJ"` is that long, and an eleventh region
fails loudly rather than borrowing region 0's colour. Whether a board *should* go past
ten is a settled question with measurements behind it:
[`large-boards-spike.md`](large-boards-spike.md).

**Regions** are 4-connected. Two cells touching only at a corner are not one region.

**Tapping.** One tap crosses a square off, or clears the cross. It is always free and it
can never cost a bone. Two taps on the same square inside `DoubleTapWindowMs` (320ms)
commit a guess. Right, and a dog lands and the cascade fires. Wrong, and it costs a
bone, the square stays crossed off, and the combo breaks.

The double tap is recognised in `GameViewModel`, not by Compose's `onDoubleTap`.
Registering that makes Compose withhold the first tap for the whole timeout, which puts
about 300ms of lag on the gesture a player performs dozens of times a board.

**Dragging paints.** A stroke across the grid crosses squares off or clears them,
whichever the first square it can act on implies, and it holds that direction for the
whole stroke (`Modifier.dragAcrossCells`,
`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/board/BoardDrag.kt`).
It never places a dog. The gesture does not claim the pointer until the stroke reaches a
different square, so a sloppy double tap is not eaten.

**There is no long press on the board** for sighted players. A screen reader gets one;
see [Accessibility](#accessibility).

**Auto-mark.** Placing a dog rules out its whole row, its whole column, its entire
region and its eight neighbours (`CandidateGrid.place`). This is what makes a 9x9
tractable rather than exhausting.

The cascade is **always computed**. The Settings toggle only decides whether it is
*drawn*: `GameState.autoMarks` is the deduction and `GameState.visibleAutoMarks` is the
subset on screen. The sniff, the difficulty engine and the shipped packs all reason over
the first, so turning the crosses off changes the picture and nothing about the puzzle,
and in particular never buys worse hints for asking for less help. The toggle ships
**off** by default.

**Manual crosses** sit on top of auto-mark and are what lets a careful player record
their own deductions on the squares the cascade cannot rule out. A square that cost a
bone can never be un-crossed.

**Inert squares nudge.** Tapping a placed dog or a square that already cost a bone
shakes and buzzes rather than doing nothing, so a tap that reaches nothing says so.

**Answer checking is a lookup.** The solution ships with the level, so a tap is an array
index rather than a solve.

**There is no free first mistake.** A one-shot forgiveness on campaign level 3 used to
exist and is gone. The only board where a wrong guess costs nothing is the rehearsal
board in [the tutorial](#onboarding-and-the-tutorial).

**Where it lives:** `libraries/puzzle` (rules, solver, cascade),
`features/game/impl/.../GameViewModel.kt` (gestures, state),
`libraries/ui/.../components/board/` (rendering).

---

## Bones

Bones are lives. There are three, and they are **one count across the whole game**,
persisted in `AppData.bones` (`libraries/sodogku/.../AppCache.kt`). The daily and the
campaign spend from the same pool. Starting a level does not top them up, and a retry
hands none back.

`ScoringConfig.MAX_LIVES` is 3 and is a binary constant, not a config key.

A wrong committed guess costs one. At one remaining the game says so once per attempt.
At zero the attempt ends: the board dims, the answer is **not** revealed, and the lose
sheet appears.

**Opening a board at zero** shows the refill offer straight away rather than waiting for
the guess that ends the attempt. The board is fully markable while that offer is
dismissed, because only a committed placement can cost anything.

**The lose sheet reports the run.** Dogs placed and time on the board, as pills. Score
and mistakes are deliberately absent: mistakes reads the same on every loss ever played,
and the score is whatever the last strike left, which the lifetime total banks none of.
A pill drops out entirely at zero rather than drawing a nought. Below the pills:

| Option | Cost |
|---|---|
| Watch an ad to refill bones and keep the board | Rewarded ad, free for Pro |
| Start over | Free, grants no bones |
| Skip the level | Rewarded ad, and only when [Skip](#skip) is offered |
| Back to the level pane | Free |

**There is exactly one revive and it restores the whole set** (`maxOf(livesRemaining,
boosters.refillTo)`, a floor rather than an assignment). A second button that restored a
single bone used to sit under it and is gone: handing one bone to somebody who has
already run out puts them back on the same sheet one guess later.

**The revive cannot fail closed.** It goes through `AdGate.showRewarded`, where every
outcome except a deliberate dismissal grants. No fill, no network, an SDK that threw,
ads switched off in config, a config server nobody can reach: all grant. The only way to
leave with nothing is to close the ad yourself, and the offer is still there on the next
tap.

**Pro gets no per-attempt bone floor**, unlike sniffs and treats. Pro's bone benefit is
that the revive costs no ad, which is unlimited bones already. A floor would make bones
per-attempt again for the one group most likely to notice.

**Scoring prices the attempt, not the holding.** The completion bonus, the paw rating and
every "no mistakes" badge read `strikesThisAttempt`, carried through a process death by
`BoardSnapshot.strikesTaken`, never `MAX_LIVES - held`. Otherwise a mid-board refill
would be a score multiplier.

---

## Sniffs and treats

Three consumables share one shape, because three economies would be three things to
learn before the puzzle.

| Consumable | What it does | How it is spent |
|---|---|---|
| **Bone** | A wrong guess costs one. Out of bones ends the attempt. | By being wrong, never by tapping |
| **Sniff** | A hint. Proposes squares that deduction has ruled out. | The Sniff button |
| **Treat** | Places one correct dog, free, with no bone at risk. | The Treat button |

All three start at 3 (`boosters.startingSniffs`, `boosters.startingTreats`, and the
bones constant). All three refill to `boosters.refillTo` (3) for a rewarded ad. All
three are held across boards in `AppData` and none is granted by starting an attempt.
All three may be **held above 3**: clearing levels grants extra, and a refill is a floor,
never a reduction. Each shows its count on its button including at zero, because a badge
that vanishes makes the button look broken rather than empty, and empty is the state
that should invite a tap.

**The sniff shows where a dog cannot go, never where one does.** It searches up to 24
ruled-out squares, drops everything already crossed off, then picks the single
*shallowest* technique that proves something and lights up to 4 squares that technique
alone found. The result is a **proposal**: the squares draw differently until the player
keeps or discards them. It reads the full deduction, not the drawn set, so a player with
auto-mark off gets the same hint.

**The treat places a dog.** It runs the same hint finder, takes the next provable cell
and places it. Counted before the placement, so the dog is priced as bought help.

**A booster is never spent for nothing.** If deduction has nothing to add, the sniff
declines and keeps the charge, and logs `game.booster_no_op`. The treat does the same
when there is no provable cell. The freeze and restore offers on the daily follow the
same rule: they are only shown when using one would actually lengthen a streak.

**First use always explains.** The first tap of each booster opens a dog, a sentence, and
three buttons: use one, watch an ad for three, or not now. After that a tap just spends
one, and the explainer only returns when the count hits zero. Spending cannot be undone,
so the first time someone taps an unfamiliar button they find out what it costs before it
happens.

**The level reward.** Clearing a campaign level that `boosters.treatSchedule` pays on
grants one Treat, on the **first clear only**. The schedule is a list of bands, each
running until the next one starts, and inside a band a level pays when its id is a
multiple of that band's interval. Shipped: every 3rd from level 1, every 6th from 21,
every 12th from 61, every 25th from 151. Over 1000 levels that is **54 Treats**, against
the 200 a flat every-fifth rule would pay. Dense at the start where a Treat teaches and
the player has no stash, sparse later where they are holding several and a reward on a
metronome has stopped reading as one. A replay pays nothing: a level that paid every time
it was finished would be an ad-free treat printer. The level pane marks every row that
pays and marks the collected ones as spent, so the ladder is visible before it is walked.
Campaign only, because the daily's ids are positions in another pack.

**The free dog.** A level near the start of each **grid size** opens with one dog already
placed. Not the start of each band: the six consecutive 10x10 bands count as one stretch,
so the position is measured from the first level at that size. `progression
.starterDogLevelsPerBand` (3) says how many, which works out to 21 free dogs across the
seven sizes. It exists to fire the auto-mark cascade before the player has reasoned about
anything, and that lesson is worth repeating every time the board gets wider. It scores
nothing, never appears on a resumed board, and never appears on the daily.

**Pro** opens every attempt with a floor of `boosters.proSniffsPerAttempt` and
`boosters.proTreatsPerAttempt` (3 and 3). A floor, never an assignment: a Pro player
holding nine Treats opens their next board with nine. It cannot be farmed by restarting.

`features.boosters` turns the whole thing off. Bones are exempt from that switch.

**Where it lives:** `features/game/impl/.../GameViewModel.kt`,
`libraries/config/.../values/ProgressionConfigValues.kt`,
`libraries/config/.../values/TreatSchedule.kt`.

---

## Score and paws

**The header shows one lifetime score, not the attempt's.** It is every board the player
has banked, campaign bests plus every daily result, summed on demand and never counted
into a stored total. A tally that drifts has no witness and no server copy to rebuild
from, and a fold makes a past bug retroactively fixable.

While a board is open the number is everything else banked plus the better of this
board's own best and the attempt on screen. So it climbs on a first play, moves on a
replay only once the attempt passes the old best, and drops back the moment the bones run
out, because a lost attempt banks nothing.

**Per correct placement:**

```
combo  = min(1.0 + consecutiveCorrect × comboStep, comboMax)
window = speedWindowMs × size / 4
speed  = 1.0 + (speedMaxMultiplier - 1.0) × (1 - elapsedSinceLastPlacement / window)
points = basePerPlacement × size × combo × speed
```

`size` is the grid dimension, so a 10x10 placement is worth more than a 4x4 one. The
speed decay is linear rather than exponential, so the pressure a player feels is
proportional to the clock they can see, and it is pinned to 1.0 once the window has
passed. The window scales with the grid because nobody places a dog on a 9x9 as fast as
on a 4x4; the reference size 4 is a binary constant, and the magnitude next to it is the
config key. A 4x4 gets 16 seconds a placement, a 10x10 gets 40.

**Completion bonus**, priced per cell so both halves of a run grow with the board at the
same rate, which is what lets one set of paw fractions describe a 4x4 and a 10x10 alike:

```
bonus = completionPerCell × size × size
      × (1 + (difficulty - 1) × difficultyBonusRate)
      × (1 + livesRemaining × livesBonusRate)
```

**Boosters cost points, not paws:**

```
banked = earned × (1 - boosterPenaltyRate) ^ (sniffs + treats spent this attempt)
```

At 0.15 one booster keeps 85% of the run, two keep 72%, five keep 44%. Multiplicative, so
nothing can drive a score negative, the cost is the same wherever in the attempt the help
was taken, and it scales with the board. The banked number is what the record, the win
sheet, the achievement log and `game.level_completed` all carry.

The paw rating is measured on what the run earned **before** the cost. Because the cost
is a multiplier and the thresholds are fractions of par, that is the same arithmetic as
scaling par by the same factor, and it keeps the top rungs reachable for anyone who took
a hint, including every player following the tutorial, which instructs both. Paws say how
the board was solved; the score says what the help was worth.

**Par** is derived at runtime, never stored in the pack, so retuning what a five-paw clear
means is a config change:

```
par = Σ placements at full combo and speedMaxMultiplier
    + completionBonus(size, difficulty, MAX_LIVES)
```

That is every placement at the multiplier for a zero-millisecond tap, finished without
losing a bone. No human equals it. It is only the scale the thresholds are fractions of.
`placements` is dogs the *player* places, one fewer where a free dog opened the board.

**Paws** run 0 to 5 and are score-based rather than strike-based. Finishing at all earns
one; the other four are fractions of par, walked from the top so the first rung cleared
wins:

| Paws | Fraction of par | Config key |
|---|---|---|
| 5 | 0.85 | `scoring.fivePawFraction` |
| 4 | 0.77 | `scoring.fourPawFraction` |
| 3 | 0.64 | `scoring.threePawFraction` |
| 2 | 0.53 | `scoring.twoPawFraction` |
| 1 | finished | none |
| 0 | did not finish | none |

The rungs are placed in the gaps between measured runs, not chosen for roundness
(`ScoringTest.theLadderIsSpacedAgainstMeasuredPlay` holds the measurement):

```
two strikes, past the speed window    0.48 .. 0.49    one paw
one strike, past the window           0.57 .. 0.62    two paws
clean, past the window                0.67 .. 0.75    three paws
clean, 2500ms a row                   0.79 .. 0.84    four paws
clean, 900ms a row                    0.92 .. 0.94    five paws
```

**Compressing the range is the failure mode to watch** whenever these get retuned. Two
separate rounds of this have shipped a rating that carried no information: once because
the speed window was flat and the speed term was dead on every large board, and once
because no clean run at any pace could score below 0.73, which put two of the four cuts
underneath the floor. The `ScoringConfig` KDoc warns about it twice and `ScoringConfig`
itself throws on a non-ascending ladder.

**Praise text** floats over the board on high-multiplier placements, keyed off `combo ×
speed`: Nice at 1.5, Great at 1.9, Excellent at 2.4, Perfect at 2.9. Purely cosmetic,
every cutoff in config.

**Time to beat.** A level already cleared opens with its previous best time as a target.
The clock under the board reports inside or past it once a second, and the win sheet says
whether it was beaten. The target is read when the attempt opens, because the win
overwrites the record. A tie is a miss. Zero on the daily and the rehearsal board.

**Records kept per level:** best score, best time, best paws, each an independent best.
A fast replay does not cost a three-paw clear, and `bestPaws` only ever goes up, so a
retune never demotes a record.

**All 17 `scoring.*` keys are remote config**, assembled by
`features/game/impl/.../ConfiguredScoring.kt` on every scoring decision and never
captured in a field. An invalid set falls back to `ScoringConfig.Default` **whole**, never
field by field, because some of the rules are about pairs and a half-remote blend is a
combination nobody chose.

**Where it lives:** `libraries/scoring/.../ScoreCard.kt` and `ScoringConfig.kt` (pure
Kotlin, no Compose, no DI), `features/game/impl/.../ConfiguredScoring.kt`.

---

## Skip

After repeated failed attempts on a campaign level, the lose sheet offers a skip. It is
the last control on the sheet, under both the revive and Start over, because it is a
rescue rather than an invitation to stop thinking.

A skipped level records as skipped: no score, no paws, no time. It stays on the list and
can be cleared properly later, it does not count as a clear for achievements, and it
unlocks the next level normally.

**What counts as a failed attempt.** `progression.skipAfterFailedAttempts` (2) is
compared against `level_progress.attempts`, which counts *starts* of a level that has
never been cleared, not failures. There is no failure column and adding one means a
schema bump. Attempts over-count: an abandoned attempt and a board resumed after a
process death both add one. So the skip arrives slightly early for some players, which is
the right direction for a rescue that already costs an ad and comes out of a daily
allowance.

**The cap is `progression.skipsPerDay` (3), and it applies to everyone including Pro.**
Otherwise a Pro player reaches the end of a thousand levels in an afternoon and has
nothing left. Pro's benefit is that the skip costs no ad.

The allowance is checked **before** the ad, so nobody watches thirty seconds for a skip
they cannot have, and it is spent before the progress write, so a crash between the two
costs a skip rather than granting an uncapped one. Only a deliberate dismissal withholds
it; every other ad outcome grants.

With the allowance spent the control stays on screen, disabled, saying why. Removing it
is how a player learns that a feature they used yesterday has silently gone away.

**The per-day counter and a moved clock.** The allowance lives in its own persisted
`skip_state` cache, so a force-quit is not a way around the cap. The recorded day **only
ever moves forward**: a clock wound back does not roll the counter over, so a spent skip
cannot be recovered. A clock wound forward does grant a fresh allowance, because refusing
a future date means trusting a clock we already do not trust, but it drags the recorded
day with it, so the player gets nothing until the real calendar catches up. This is
deliberately the opposite of what [the daily](#the-daily) does, and `decisions.md` says
why.

**Where it lives:** `libraries/progress/.../SkipRepository.kt`,
`libraries/progress/impl/.../SkipRepositoryImpl.kt` and `SkipStateCache.kt`.

---

## The campaign

**1000 verified levels**, bundled, ordered into bands.

| Levels | Grid | Difficulty runs |
|---|---|---|
| 1 to 10 | 4x4 | tier 1 ×2, tier 2 ×8 |
| 11 to 40 | 5x5 | 2 ×12, 3 ×12, 4 ×6 |
| 41 to 100 | 6x6 | 2 ×4, 3 ×16, 4 ×40 |
| 101 to 180 | 7x7 | 3 ×24, 4 ×56 |
| 181 to 280 | 8x8 | 3 ×30, 4 ×70 |
| 281 to 390 | 9x9 | 3 ×30, 4 ×80 |
| 391 to 500 | 10x10 | 3 ×30, 4 ×80 |
| 501 to 1000 | 10x10 | five bands of 3 ×20, 4 ×80 |

**Difficulty is not grid size.** It is the deepest technique the deduction engine had to
reach: 1 for single-candidate elimination, 2 for adjacency or group confinement, 3 for a
naked set, 4 for a contradiction step. Tier 5 exists in the enum and never ships, because
a board no reasoning solves is not a puzzle, so the whole ladder is 1 to 4.

Tier 3 arrives at level 23 and tier 4 at level 35. Each band opens a tier *below* where
the last one closed, deliberately: the grid just grew, which is its own difficulty jump,
so the reasoning gets a breather while the player learns to read a wider board.

**Both axes are spent at 500.** The size ladder stops at ten because an 11x11 measured as
longer rather than harder ([`large-boards-spike.md`](large-boards-spike.md)), and the
tier ceiling is four because five is unshippable. So the back five hundred exist to *not
repeat*: every board in the pack is distinct up to the eight grid symmetries and any
renaming of regions, asserted over the whole pack by
`LevelPackVerificationTest.noBoardRepeatsEvenTurnedAroundOrRecolored`. That is a claim the
genre's incumbent cannot make.

**Levels are appended and never reordered**, because progress is keyed on level id.
Reordering silently reassigns everyone's cleared levels to different boards.

**The pack.** One pipe-delimited line per level, `id|size|regions|solution|difficulty`.
Regions are one letter per cell, row-major; the solution is one digit per row giving that
row's column, which works because boards cap at ten. Packs ship as **generated Kotlin
source**, not as an asset: the verification test is the only thing standing between an
unsolvable level and the store, so it has to run everywhere, and generated source loads
identically in a JVM test, on Android and on iOS with no resource plumbing. A malformed
pack fails compilation rather than the app.

The solution ships with the level. A determined player can unzip the binary and read it.
That buys them nothing worth defending, and shipping it makes strike checking an O(1)
lookup with no runtime solve cost on a cold tap. It does mean the leaderboards are not
cheat-proof, and they are not claimed to be.

**The verification test** loads both packs and asserts, for every level: regions are
contiguous, region count equals size, the solver finds exactly one solution, and it
equals the shipped one. It also holds the pack against `LevelCurve` level by level, so a
deliberate re-curve and a band that quietly came up short are not the same diff.

**Generation is offline.** `tools/level-generator` is a JVM CLI depending on
`:libraries:puzzle` and nothing else, so the solver that verifies the pack is the solver
that runs the sniff on device. Nothing is generated on device.

**The level pane** is a drawer over the board, not a screen, because the puzzle is the
home screen. All 1000 rows in one list, scrolled on open to where the player is. Each row
carries a dog or a padlock, the level id, its grid size, its best time, its paw rating
once cleared, and a chip on every level the treat schedule pays. Locked levels are shown
rather than hidden: seeing that level 200 is an 8x8 you have not reached is the whole
reason to scroll the list. Pro can jump to any level.

**Start over** is always available, on the campaign and on the daily, and is refused only
on a spent daily's recap. It costs the position and nothing else: no bones back, no
streak change.

**The ending.** Clearing the last level does not navigate. The win sheet becomes a
campaign-end sheet with the total levels cleared, total paws and total score, and it
offers the daily as its primary action. It does not promise more content. The check is
`levelId >= lastCampaignLevelId` rather than `==`, so a shorter pack cannot strand a
player past the end. This replaced a `NavigateBack` that popped the app's start
destination, which meant the reward for clearing the whole campaign was the app closing.

**Top Dog** is the achievement, not the sheet: 1000 levels cleared.

**Where it lives:** `libraries/levels/.../LevelCurve.kt` (the curve as a declared list),
`LevelCodec.kt`, `LevelPacks.kt`, the generated pack data,
`features/game/impl/.../LevelDrawer.kt` and `GameOutcomeSheets.kt`,
`tools/level-generator/`.

---

## The daily

One board per calendar day, identical for every player, drawn from a separate pool of
**730** boards so it never spoils a campaign level. The index is
`(epochDay + daily.poolOffset) mod 730` on the local device date, with no server. Someone
can time-travel by changing their clock; that costs nothing and nothing tries to stop
them.

**The daily pool is not a ramp.** Every player meets the same board whatever level they
are on, so it stays at 6x6 to 8x8 and stays shuffled, with no tier 1 at all, a mode of
tier 3 and about a fifth at the ceiling. It is a three-to-five-minute habit rather than a
wall, and it is deliberately not *easier* than the campaign level its player is on, which
is the wrong signal from the thing that exists to bring them back.

**One attempt per day, enforced by the primary key.** `daily_result` takes one row per
date, the insert is ignore-on-conflict, and there is deliberately no update path on the
table at all. The lock therefore holds whatever the clock is set to.

**A loss writes nothing.** The day stays open, so the player can revive with an ad or
start over. The **lost board is kept**: the in-progress snapshot survives a lost phase on
the daily and only there, so reopening the day hands back the position rather than a
fresh run. Bones do not come back with it.

**There is no give-up.** The control that wrote a `Failed` row is gone. It spent the day,
ended the streak, and could not afterwards be frozen or restored, and the player got
nothing back for any of it. `DailyOutcome.Failed` still exists as a legacy reading,
because rows carrying it are on players' disks and dropping the name would silently
shorten somebody's history. Nothing writes one.

**Start over is offered**, same as the campaign. Every restart it can reach happens on a
day with nothing written against it, and the eventual clear still writes exactly one row.

**A spent day still opens**, on its result rather than its board: date, outcome, paws and
score if it was cleared, and the streak. The daily card offers it as a review. The recap
is non-interactive and writes no snapshot.

**The attempt is keyed to the date the board opened**, so an attempt that runs through
midnight counts for the day it started and leaves the new day unplayed.

**Timezones.** The day boundary is computed as the start of the next local day, not as
plus 24 hours, because a DST day is 23 hours and midnight can be missing entirely. The
zone is re-read on every pass, so a player who flies gets the right board at their new
midnight. The countdown is floored at one second so a bad clock cannot busy-loop.

**The card** sits at the top of the level pane and carries the date, the daily streak,
the day's state and paw rating, a countdown to the next board, and the freeze or restore
offer when there is one. A dot on the drawer button says today is unplayed. While the
daily is the board on screen, the header shows the streak where the level number usually
sits, because a daily's id is a position in a 730-board pool and reads as a campaign level
nobody has reached.

`daily.enabled` and `features.dailyChallenge` both gate it, and either one off closes the
card entirely, so neither is a control nothing listens to.

**Where it lives:** `libraries/progress/.../daily/`,
`libraries/progress/impl/.../daily/DailyRepositoryImpl.kt` and `DailyCalendar.kt`,
`libraries/ui/.../components/game/DailyCard.kt`.

---

## The streak

**There are two streak numbers and they measure different things.**

**The play streak** is the one the flame and the streak page show. It folds over
`play_day`, a Room table with exactly one column: the local ISO date. Any day with a
finished board writes a row, campaign or daily, and the write happens before the game
branches on which, because both of them are a finished board and the streak does not care
which. One row per day, not per board.

Today not being played does not break the run: the walk starts at today if it is played
and at yesterday if it is not, then counts backwards while the day is in the set. Nothing
is stored but the days, so the number cannot drift out of step with the history it claims
to summarise. Longest is a linear walk over the same rows. The calendar shows five whole
Monday-first weeks ending in today's week, and a future day is drawn as future even if a
row exists for it, so a clock set forward cannot draw a lived-in day.

The play streak does not read the daily's config, so switching the daily off cannot cost
somebody a run.

**The daily streak** is consecutive days with a cleared daily, folded from `daily_result`
on every read. It is shown on the daily card and the daily win sheet. Today does not have
to be done yet. Frozen and restored days **bridge without counting**. A day the player
attempted and lost is not a missed day and cannot be bridged.

**Streak freezes.** One rewarded ad covers one missed day, capped at
`daily.freezesPerMonth` (2), counted against the month the covered day falls in. It only
covers a day with no row at all, and it is only offered when using it would actually
reconnect a run. Pro gets it without the ad. This is the single most reliable ad
impression in the app.

**Streak restores.** The bigger hammer, same ad placement. It bridges a run of consecutive
missed days, minimum two, so it and the freeze are mutually exclusive by construction.
Bounded twice: `daily.restoreMaxDays` (3) is the longest gap it will reach, and
`daily.restoreDaysPerMonth` (3) is how many days it may bridge in a calendar month, which
is one restore at full size. It writes one row per bridged day and is deliberately not
transactional: the table is insert-only, so a half-finished run leaves a shorter gap and
no lie.

Together the two forgive at most three consecutive missed days. Past that the daily
streak really does end.

**The streak page** shows current, longest, the five-week grid, whether today is played,
and how long is left. Two ways in: tapping the flame in the level pane, which animates
nothing, or the ceremony after a clear, which fills exactly one cell. The ceremony is
marked as shown on arrival, not on dismissal, so a force-quit is not an exit.

**The commitment screen** fires once ever, after the second board cleared, and shows a run
the player already has rather than asking them to start one. It records itself as shown
the instant it opens.

**The ceremony fires every day the run grows**, after the first, rather than on
milestones.

**Where it lives:** `libraries/progress/impl/.../streak/PlayStreak.kt`,
`DailyStreak.kt`, `StreakPrompts.kt`, `features/streak/impl/`.

---

## Achievements

**73 badges across 9 shelves**, all local. `Achievements.sections` is the source of truth
and `catalog` is its flattening, so a badge cannot be in one and off the other.

| Shelf | n | What is on it |
|---|---|---|
| The campaign | 13 | 1 / 10 / 25 / 50 / 100 / 250 / 500 / 1000 levels cleared (levels, not clears: a replay does not count); first 7x7 and first 10x10; 10 and 50 clears on a 7x7 or bigger; 25 on a 10x10 |
| Clean play | 8 | 1 / 25 / 100 clears that cost no bones; 3 / 10 / 25 of them consecutively; 1 and 10 on a big board |
| The hard way | 9 | 1 / 10 / 50 clears after losing two bones, and one of those on a big board; 25 / 100 / 250 with no sniff and no treat, and 20 / 50 of those in a row |
| Speed | 6 | 1 / 25 / 100 clears inside 30s; 1 and 10 big boards inside 60s; a 10x10 inside 5 minutes |
| Score and combos | 6 | three score rungs derived from par; runs of 5, 8 and 10 consecutive correct placements |
| Paws | 9 | 1 / 10 / 50 / 150 / 300 levels taken to three paws, counted the first time each gets there; 5 and 20 in a row; 1 and 10 on a 10x10 |
| Daily challenge | 11 | 1 / 10 / 50 / 150 dailies cleared; 3 / 7 / 30 / 100 day streaks; 1 and 10 dailies with no bones lost; 10 at three paws |
| Time on the boards | 3 | 1, 10 and 50 hours summed across every recorded attempt |
| Secrets | 9 | all hidden: come back to a level that beat you, and do it 10 times; finish on both a sniff and a treat; a clear inside 10s, and 10; a clear between 1am and 5am, and 10; the same between 5am and 8am |

**Every criterion is one shape: a counter reached a number.** Anything that cannot be
phrased that way becomes a new counter in the fold rather than a new kind of criterion,
which keeps progress-toward-unlock a division rather than a special case.

**The three score targets are derived, not typed.** `ScoreLadder` computes them from par
on a 4x4 and a 10x10 at the paw fractions, then floors to two significant figures: 450,
1,600 and 2,300 today. A badge nobody can ever earn looks exactly like a working one, so
this is arithmetic rather than a guess.

**What is stored is the facts, not the counters.** One `achievement_fact` row per finished
attempt, append-only, deduped on a key built from mode, level and finish time so an
at-least-once caller cannot double-count. Every counter is folded back out of the log on
demand. There is no second copy of a player's progress to drift, and an achievement added
in a later release **back-fills from history**, dated to the attempt that really earned
it, rather than starting everyone at zero. A separate `achievement_unlock` table records
what has already been *announced*, so a catalog change cannot re-toast a two-month-old
badge, and moving a target out of reach never retracts a badge somebody holds.

Notable fold rules: a failed attempt breaks all three consecutive-clear streaks; time on
the boards is the only thing a failed attempt buys; three-paw counters only move when the
previous best was below three, so a replay cannot re-earn one; night is 1am to 4:59am and
dawn is 5am to 7:59am, non-overlapping.

**The catalog carries no display copy, only stable ids.** Names, descriptions and glyphs
are exhaustive `when`s over the enum, so adding an achievement fails the build until
somebody writes the words for it.

**The page.** A count-up hero, then a spotlight card, then one labelled shelf per group in
a three-column grid. The spotlight shows everything just earned if anything is new, and
otherwise the three nearest unearned badges from three different shelves. Hidden badges
show a question mark, the name `???`, and **no progress at all** until earned, because a
counter under a mystery badge narrows down the condition. All nine sit together under
Secrets rather than each on the shelf its criterion belongs to, for the same reason.
Tapping a badge opens the design system's dialog, so it gets the spring entrance,
back-press dismissal and scrim for free.

**New-badge marking** uses a watermark timestamp read once when the screen loads and
frozen for its life, so a badge that lands while the grid is open still reads as new on
screen. The watermark only ever moves forward, and it is written from the newest unlock's
own time rather than a wall clock.

**Toasts** appear over the board only, as a stack, each dwelling 2.6 seconds and
dismissible on tap.

**Two independent gates.** The Settings toggle (`AppData.achievementsVisible`, default on)
suppresses toasts and hides the way in but keeps recording, so re-enabling shows accurate
history, and the screen keeps an "achievements are off" panel behind the hidden row that
says outright that recording carried on. `features.achievements` is the remote kill
switch; with it off the recording path returns no badges to announce, and the log is still
written.

**Progress cannot survive a reinstall.** That is the honest cost of having no accounts and
Settings says so in as many words.

**Where it lives:** `libraries/achievements/.../Achievement.kt` and
`AchievementCounters.kt` (pure fold and catalog),
`libraries/achievements/impl/.../AchievementsRepositoryImpl.kt`,
`features/achievements/impl/`.

---

## Leaderboards

Three boards, hosted by the platform. Nothing about them reaches an account of ours, a
server of ours, or a per-player record in our systems: the platform owns the identity,
the scores, the UI and the display-name moderation.

| Board | What is submitted |
|---|---|
| Lifetime score | The banked lifetime total, on every clear |
| Longest daily streak | The longest run, on every clear including campaign ones |
| Weekly score | Points banked since the start of the current window |

A leaderboard is a shared room, and splitting a small player base across several empties
all of them, so the test was not "could this be a board" but "would a stranger's name be
next to yours on it in week one". Per-level boards, a daily board and a total-paws board
were all considered and rejected; `decisions.md` has each reason. The daily one is the
trap worth remembering: our daily rolls at device-local midnight, so a recurring board
would rank two different puzzles against each other.

**The week boundary is not decided on the device.** Game Center is asked for the start of
the occurrence currently accepting scores, per submission, never cached. Play Games has no
recurring board, so Android has no window and the weekly board is iOS-only by design.

**iOS is Game Center**, via Kotlin/Native bindings to GameKit rather than a Swift shim,
because GameKit is an ordinary system framework and a Swift file would only be a second
place for the seam to drift from.

**Android is Play Games Services.** Today every Android board id is an empty string, so
the platform reports itself unavailable, the Settings row is not drawn and nothing is
submitted. The ids are waiting on Play Console; the weekly board's Android id is
permanently empty on purpose.

**Failing open is structural, not a comment.** The `Leaderboards` interface has no
suspending method and no method that returns a result, so there is no way to write a call
site that waits on a leaderboard or branches on one. The only readable thing is whether to
draw an entry point. Every failure is a log line.

**A score earned before sign-in resolves is held.** Authentication is slow and a board is
quick, so the first score of a session routinely happens while the platform is still
deciding who the player is. One slot per board, best value wins, flushed the moment
authentication lands. In memory only, because every value is a running total the next
board recomputes.

**The sign-in sheet never arrives on its own.** It is presented only when the player opens
a leaderboard themselves. A full-screen sign-in that shows up at launch, for a feature the
game does not need, is exactly the interruption the fail-open rule exists to prevent.

**Achievements are not mirrored** to either platform. `decisions.md` has the three reasons,
the shortest of which is that it would be 73 console forms and 73 images the game does not
have.

**Where it lives:** `libraries/leaderboards/`, its `iosMain` and `androidMain` impls, and
`RealLeaderboards.kt` for the windowing and holding.

---

## Pro

One non-consumable in-app purchase, product id `sodogku_pro`, identical on both stores. It
is a one-time payment and the copy says so. **The price comes from the store and only from
the store**, per storefront; it is never hardcoded and never in config. Until the store
answers, the button reads "Get Pro" with an empty price.

**What Pro grants, all five implemented:**

| Benefit | How it works |
|---|---|
| No ads, ever | Every gate short-circuits on the entitlement before anything else, and preloading returns immediately |
| Unlimited offline play | The offline block is never requested for Pro, and dismisses itself if the entitlement arrives while it is up |
| Continues, skips and streak freezes with no ad | Every one of those call sites reads `isPro || adOutcome != Dismissed` |
| Boosters topped up at the start of every attempt | A **floor** of 3 sniffs and 3 treats, never an assignment, so a Pro player holding nine keeps nine |
| Every level open from the start | The level pane unlocks everything and the jump is allowed |

There is a sixth that is not on the sheet: booster buttons stop showing the watch-an-ad
affordance.

**Pro gets no per-attempt bone floor.** The free continue is unlimited bones already, and
a floor would make bones per-attempt again for the one group most likely to notice.

**Restore Purchases is in two places**, and Settings is the one that matters, because
Apple rejects a non-consumable app with no visible restore control. It is drawn whether or
not the player is already Pro. Only a deliberate restore that comes back "not owned" is
allowed to clear the entitlement; an unreachable store leaves the cache alone.

**The entitlement is one boolean in `AppData` and is treated as true until proven false.**
If the store is unreachable at launch, a paying customer must not see ads. Store ownership
is three-valued precisely so "we could not ask" is distinguishable from "no". It refreshes
at init and on every foreground.

**The paywall** is a bottom sheet with four triggers:

| Trigger | Gated on `paywall.triggers` | Counted against `paywall.sessionCap` |
|---|---|---|
| Offline block | no, it has its own switch | no, it is a state rather than an offer |
| Continue level | yes | yes |
| Skip level | yes | yes |
| Direct, from Settings | no, never gated or capped | no |
| Ad unavailable | no, it has its own switch | yes |

**The ad stand-in.** When a gate asked and the network had nothing, the player used to
carry on with no sign anything was attempted. Now that case raises a Pro sheet that holds
its own close controls for five seconds. **System back always gets through**: a five-second
sheet a player cannot escape is an ad network's bad afternoon becoming their problem,
which is the one thing the fail-open rule forbids. It is suppressed where the gate has
just put the same sheet up on the way in, because two Pro sheets around one ad gate is a
nag. In a debug build it carries a `placement · reason` line; in release that string is
empty, so release exercises the same path.

**Android is Play Billing 8**, not 7, because Play stopped accepting new releases on 7.
iOS is StoreKit 2 in Swift, injected through the DI graph. Under `Entitlements` sits a
narrow platform seam that only talks to the SDK; the entitlement cache, the gates and the
fail-open mapping are all common Kotlin above it, which is what makes them testable
without a store.

**Where it lives:** `libraries/billing/`, `libraries/billing/impl/`, `features/paywall/`.

---

## Ads

**Four placements, all rewarded.** There is no format that interrupts.

| Placement | Trigger |
|---|---|
| `continue_level` | Third strike, from the lose sheet: restore the bones, keep the board |
| `booster_grant` | Refill a sniff, a treat or bones on a board still in play |
| `skip_level` | The skip offer |
| `streak_freeze` | Cover a missed daily, or restore a run of them |

The freeze and the restore share one placement deliberately: they are the same thing to an
operator, and a second id would be half a kill switch. `daily.freeze_used` and
`daily.streak_restored` tell them apart in reporting.

**Every placement being rewarded is the policy, not an accident of what got built.** An
interstitial, an app-open ad and a map banner were all specified and all deleted, having
produced no impressions because nothing ever called them. `AdPolicyTest` now pins the
enum, so a format that is not rewarded has to be argued for in a test rather than merely
added.

**The gate, in order.** Each of these is a reason the reward is free and no ad is shown:
the player is Pro, `ads.enabled` is off, the placement is disabled in
`ads.rewardedPlacements`, or the player is inside the new-user grace. The Pro offer, where
a placement has one, sits **below** every free path, so a day-0 player is never sold to.

**New-user grace: no ads before level 5 or the first 5 minutes**, and **both** legs have to
be past. Day-0 ad exposure is the biggest single driver of first-session churn in this
genre. The clock starts at first app run, not at first ad request, and a level read that
throws resolves to "treat as new".

**A failed ad grants the reward.** No fill, offline, not shown, an SDK that threw, and the
whole path wrapped so a thrown exception still grants. Only a deliberate dismissal
withholds. Every call site reads "not dismissed" as granted.

**Ad unit ids and the product id are in the binary, never in config**, because changing
one is a store operation and a config outage that blanked them would take ads and
purchases down together. One switch flips the whole app between Google's published test
units and the real ones, and a blank live id falls back to its test unit rather than
requesting nothing. The AdMob *app* id is the exception, because both SDKs read it before
any app code runs, so it lives in the Android manifest and the iOS plist.

**No config value is captured in a field.** A kill switch is read at the point of use, so
it takes effect within the hour rather than after a force-quit nobody performs
mid-incident.

**`ads.failureMode` is declared and read by nothing.** `LOCK` was an A/B arm that was
never built, and a test pins that it cannot reach the reward path.

**Never on the board.** No banner over a grid whose squares are already under 44pt.

**Where it lives:** `libraries/ads/`, `libraries/ads/impl/`,
`apps/ios/iosApp/Platform/AdNetwork.swift`.

---

## Offline

Play is fully local. Both packs are bundled and nothing needs the network to solve, so the
only thing going offline stops is ads, and therefore the ad-funded grants. Pro is
unaffected.

**Two different offline signals, and using the wrong one is the bug this section exists
for.** `AppState.isOffline` folds in whether our own backend is reachable, which says
nothing about whether AdMob is. The ad layer and the block screen read
`AppState.isDeviceOffline`, the platform connectivity signal on its own. The block screen
went up on full wifi the first time this ran on a device, because the dev server is not
deployed.

**The grace.** A free player gets `ads.offlineGraceLevels` (3) or
`ads.offlineGraceMinutes` (20), whichever comes first, **counted from the first ad gate
that could not be served**, not from going offline. The player is paid the reward on every
unservable gate whether or not the grace is spent; what changes past it is that the block
screen goes up behind them.

**The grace resets on a successful ad view, never on reconnecting.** Coming back online
without watching anything leaves the debt owed. Both counters persist to disk in their own
cache.

A configured zero on either leg blocks on the first unservable gate. That is deliberate,
and the admin console puts a confirm sheet in front of writing one.

**The block screen** goes up when all four are true: the device reports no network, the
grace is spent, `paywall.offlineBlockEnabled` is on, and the player is not Pro. It is a
full screen and not a bottom sheet, deliberately, because a sheet is dismissible by scrim
tap or drag and this must not be. It swallows the back gesture, since a block you can
dismiss is not one. It offers Pro and a retry, and it **dismisses itself** the moment
connectivity returns or the entitlement arrives, so nobody has to work out what to press.
Retrying while still offline just softens the copy.

It is the highest-intent paywall moment in the app and is instrumented separately. It is
not counted against the session cap, because it is a state rather than an offer.

**Config is offline-first.** The first frame never blocks on the network even on a fresh
install: the stream starts from the bundled fallback map and a synchronous read returns
whatever is in hand. Refresh happens on foreground, throttled, with a five-second timeout,
and the timestamp is stamped at the *attempt* so a failure does not retry on every rapid
resume. A corrupt cached snapshot takes the same path as an absent one.

**Where it lives:** `libraries/ads/impl/.../RealAdGate.kt` and `AdStateCache.kt`,
`features/paywall/impl/.../OfflineBlockScreen.kt`,
`libraries/config/impl/.../repository/OfflineFirstAppConfigRepository.kt`.

---

## Audience and consent

**The app is general audience, not child-directed, and the code implements that branch.**
"Big bubbly kids themed" is an art direction with an expensive policy consequence. A
child-directed classification restricts Play to certified ad SDKs with no personalised ads
and no ad id, and Apple's Kids Category bans third-party analytics and advertising
outright, which would remove AdMob, Sentry and the telemetry pipeline together and take
the whole rewarded-ad economy with them.

So: 13+ on the Play target-audience questionnaire, not enrolled in Designed for Families,
not in the Kids Category, AdMob's child-directed tag set to not-child-directed and the
under-age-of-consent tag left unset. A heavily kid-appealing icon plus a 13+ declaration
can still draw a Play review flag, so the art should read "cute", not "preschool".

**This is still formally an open decision for the owner**, because it gates filing either
store's privacy form. `docs/store/data-safety.md` §6 sets out exactly what each branch
produces, and `docs/OWNER-TODO.md` carries it.

**Consent.** Google's UMP form for the EEA and UK, and Apple's App Tracking Transparency
on iOS. Both are raised **before the first ad request and not at launch**, at a moment
where the value is legible. The order is enforced in one place per platform rather than
trusted to call sites: UMP, then ATT, then SDK init, then the first request. That
preparation is called lazily by the first ad gate, which is what keeps ATT away from
launch.

The gate is "can we request ads", **not** "did the form show". UMP answers yes for a user
outside the EEA who was never shown anything, so reading the form's presence would block
ads for most of the world.

**Where it lives:** `libraries/ads/impl/src/androidMain/.../AdMobAdNetwork.kt`,
`apps/ios/iosApp/Platform/AdNetwork.swift`, `docs/store/data-safety.md`.

---

## Launch gates and legal

Force update, maintenance and legal re-acceptance are one decision, should this launch
proceed, resolved from `upgrade.*` and `legal.*` **at the moment it is made** rather than
captured at construction, so an operator's change lands on the next config refresh.

A **blocking** gate is rendered *instead of* the navigation host, not navigated to. There
is no back stack entry to pop, no destination for a deep link to reach, and deep links are
dropped while a block is up. A **notice** is a dismissible banner drawn over whatever the
player was doing. At most one of each:

| | Order | Why |
|---|---|---|
| Blocking | force update, then maintenance, then legal | An update is the only permanent fix, and it also replaces the client reading this config. Consent is worth nothing while the app is unusable. |
| Notice | maintenance, then legal, then soft update | The incident outranks the paperwork, which outranks the suggestion. |

**Force update** fires when the installed version code is below
`upgrade.minSupportedVersionCode`. A build that cannot say what version it is blocks
nobody, because it would otherwise be below every threshold an operator could set.

**Maintenance** needs both `upgrade.maintenanceMode` and a non-blank
`upgrade.maintenanceMessage`: two keys means "mode without message" is what a half-finished
write looks like, and the operator's text is the whole content of the screen. Casing is
forgiving and anything outside off, banner and blocking resolves to off. The banner's
dismissal is session-scoped and never persisted, so a running incident says so again next
launch and a reworded message is a new message.

**Legal.** Versions and URLs come from config, so publishing new terms is a config change
rather than a release. Three rules the implementation added, each because the
straightforward reading bricks somebody:

- **A first launch is seeded, not prompted.** Never having been asked is not the same as
  having accepted version 0. The first resolve records the versions in hand and gates
  nothing. Without this, every fresh install starts out of date against a shipped terms
  version of 1, and with a floor set, walled out of a game it has never played.
- **`legal.forceReacceptBelow` is capped per document at the version on offer.** A floor of
  5 against a terms version of 2 is unsatisfiable: accepting records 2 and the wall stays
  up forever.
- **Closing the non-blocking banner is the acceptance**, and the copy says so. The blocking
  full screen is what a material change gets; continued use is what a minor one gets. It is
  a full screen rather than a sheet because a sheet sits on a screen the player can still
  reach.

Acceptance is recorded with the versions **carried on the gate**, not re-read, because a
refresh between prompt and tap would record consent to a version nobody saw, and only ever
moves forward.

**Everything here fails open.** Every key behind a gate defaults to 0 or off, so a missing,
partial or unreachable config blocks nobody. The whole resolve is wrapped so that a
resolver that throws lets the player play. A force-update gate is the only config value
that can brick every install at once.

**The soft-update banner's** dismissal is persisted against the version code it was made
at, so raising the target asks again and nothing else does.

**Where it lives:** `features/gate/.../LaunchGates.kt` (pure resolution),
`features/gate/impl/`, `LaunchGatesTest` pins the four failure modes: absent, malformed,
partial and unsatisfiable.

---

## Onboarding and the tutorial

**The welcome screen** is one screen: an amber field with a looping dog, a cream card
below with the tagline, the three rules in a line each, and the legal links. Two exits,
both into the game: start the tutorial, or skip it. It is the start destination only until
it has been passed once.

**The tutorial teaches on a board of its own.** A hand-authored 5x5 rehearsal board that
is in no pack, has no level id worth writing down, and cannot be finished. It replaced a
guided run over campaign levels 1 to 3, which had two problems: a player's first three real
boards were spent under a scrim, and every lesson had to point at whatever square the
generator happened to produce. This one is chosen so each lesson has a clean example, and
its uniqueness is checked with the shipped solver, the same property the packs get.

It opens **in front of** the routed level with no navigation at all: the same screen swaps
the board under itself at graduation.

**Nothing on it counts.** No attempt recorded, no level record touched, no achievement
folded, no bone spent, no snapshot written, no game event fired. The header drops the level
number, because a practice board has none, and the lifetime score holds still.

**Fourteen steps**, or **twelve** for a player who has turned auto-mark off, because
teaching a feature somebody disabled is worse than not teaching it. The script is read when
the tutorial begins, so a Settings replay respects the current setting.

In order: the free dog, then the three rules read off the board around it, each lighting
the squares that rule ruled out. Then the two gestures, one tap to cross off and two to
place, each on a single lit square with the rest of the board dead. Then the bones. Then
place a dog and watch auto-mark fire, with the squares *that placement just crossed off*
lit through the scrim. Then the sniff and the treat. Then a lit wrong square with "get one
wrong on purpose", which costs nothing because nothing on this board does, then what the
red X means, then the sign-off.

**A step is one of two shapes**, and the difference is what stops it becoming a dead end. A
step you *read* dismisses on a tap anywhere. A step you *do* keeps its lit square live,
ignores taps everywhere else, has no confirm button, and moves only on the gesture it asked
for, and then not until that gesture has finished drawing itself. Advancing in the same
frame as the tap meant the spotlight jumped away while the cross was mid-stroke, so the one
thing the lesson asked for was the one thing the player never saw. A step with nothing to
light is skipped rather than shown.

**Every step carries a skip**, and a skip leaves the rehearsal for level 1 rather than
dropping the player onto a demo board they can never finish.

**A screen-reader player can complete the gated steps.** See
[Accessibility](#accessibility).

Replaying it is a Settings row, which clears the flag *before* the navigation event goes
out, because the board reads it once as its ViewModel loads. That flag is separate from the
onboarding flag precisely so a replay does not put the welcome screen in front of somebody
200 levels in.

**Where it lives:** `features/onboarding/impl/`, `features/game/impl/.../Tutorial.kt`,
`TutorialRunner.kt`, `TutorialBoard.kt`.

---

## Settings

A full screen reached from the board header, not a sheet: it is its own context, it holds
the legal links the stores require, and it has to be findable by somebody who was told
"it's in settings". Every row writes straight through to `AppData` on tap, anywhere on the
row and not only on the switch. There is no save button, so there is nothing to hang a
deferred commit off.

**Playing**

| Row | What it does | Default |
|---|---|---|
| Cross off squares for me | Draws the auto-mark cascade. Presentation only; the game deducts either way. Also decides which tutorial script runs. | off |
| Vibration | Buzz on mark, place and strike. | on |
| Reduce animations | Still dogs, shorter board entrance, dialogs fade rather than spring. | off |
| Shapes on colors | Colourblind mode. See [Accessibility](#accessibility). | off |
| Replay the tutorial | Clears the tutorial flag and returns to level 1. | n/a |

**Achievements**, each row conditional: the way into the badge grid, drawn only when
badges are both visible and enabled; the show-achievements toggle, drawn whenever the
feature flag is on; and a leaderboards row, drawn only when the platform says a dashboard
is offerable.

**Sodogku Pro**: the Pro row, which is tappable only when the player is not Pro, and
**Restore purchases**, always drawn.

**About**: send feedback, terms of service, privacy policy (both opening hosted pages from
the URLs in remote config), and the version as text rather than a row you can tap.

**A footer, outside any card**, saying that progress lives on this device and that levels,
streaks and badges do not survive a reinstall or a move to a new phone. That is the honest
cost of having no accounts and it is stated where somebody will read it.

**The QA menu** is reachable two ways, both gated on the build being a tester build (debug
or TestFlight, decided by a runtime receipt check on iOS): a Debug section at the bottom of
Settings, and a button on the shake dialog. Its route is registered unconditionally; the
guard is at the entry points. It holds the floating-feedback-button switch on any tester
build, and on debug builds only, a shiftable clock (move the day by ±1 or ±7, or go back to
real time, and the move survives a reboot) and streak seeding.

The shake dialog itself ships in release, carrying the player-facing report-a-bug action.
Its network inspector and QA buttons are absent there.

**Where it lives:** `features/settings/impl/`, `apps/compose/.../qa/`.

---

## Accessibility

The core mechanic is colour, so this is a design constraint rather than a checkbox. Roughly
8% of men have red-green colour vision deficiency and no ten-colour palette survives
deuteranopia.

**Colourblind mode**, labelled "Shapes on colors", overlays each region with a distinct
glyph: circle, ring, square, diamond, triangle up, triangle down, plus, bar, double bar,
chevron. Ten of them, one per region colour, and the enum size is checked rather than
maintained by hand. There is deliberately no X or cross in the set, because that reads as
the player's own mark. The glyph is painted as a watermark inside the cell at 42% of the
cell and 60% ink alpha, composited contrast 1.82:1 to 2.02:1 against its own fill.

The base palette is picked for lightness separation as well as hue: closest pair 23.6 apart
in CIELAB against a floor of 20, luminance span 0.37 against a floor of 0.30, and the
softest fill sits at 1.44:1 against the dog art, which is why the dog carries a contact
shadow.

**Nothing is encoded only in colour.** Rule chips, strike feedback and region highlight all
carry a shape or a motion component.

**Screen readers.** The board is playable with one. Every square is a labelled, activatable
node.

- **A square says** "Row 3, column 4, pink" as its content description and its state
  separately: empty, crossed off, suggested, dog, or "wrong guess, cost a bone". They are
  split because state is the half a reader re-announces on its own when it changes.
  Positions are spoken one-based; the code is zero-based, and the label builder is the only
  place the two conventions meet.
- **In colourblind mode the label names the glyph, not the hue.** "Row 3, column 4, square".
  With glyphs on, the glyph is the region's identity, and "periwinkle" is useless to that
  player.
- **Crossing off is the node's ordinary activation.**
- **Placing a dog is a long press and a custom action of the same name**, because a second
  tap inside 320ms is consumed by the reader and never reaches the app. Without this, a
  board with descriptions and nothing else can be marked and unmarked and never played.
- **A square already crossed off by the board offers no placement**, and it is the *drawn*
  set that decides, not the deduction. With auto-mark off nothing is drawn, so every empty
  square offers the action and a reader user is not silently locked out of a whole row,
  column, region and ring per dog. An auto-mark the player tapped away reads as empty and
  offers it too.
- **The board vanishes from the tree while anything covers it**, a spotlight, an outcome
  sheet or the level pane, because the scrim that swallows a sighted player's taps is a
  drawing and stops nothing else.
- **The tutorial's lit square is a real control.** A spotlight hole is a drawing and is
  invisible to the semantics tree, so a labelled spotlight places a named, activatable node
  over each lit rectangle: "Place a dog, row 2, column 1", built from the board's own
  vocabulary. Activating it sends the taps the step is waiting for. The node declares
  semantics only and registers no pointer input, so it is invisible to a finger.
- **Deliberately not labelled:** the dog inside a square (the square says "dog"), the region
  glyph (the region name is the same information), the flying points and the placement
  starburst.

**Touch targets.** 44pt at 10x10 is geometrically impossible: ten columns of 44 is 440dp,
wider than any phone. Measured on device, the second column being what a finger actually
gets, since Compose expands a pointer node toward 48dp and clips at the neighbour, so the
6dp gutter belongs to the nearest square rather than to nobody:

| Width | Drawn cell | Touch bounds |
|---|---|---|
| 411dp | 31.2dp | 37.3dp |
| 393dp | 29.5dp | 35.5dp |
| 375dp (iPhone SE, the iOS floor) | 27.7dp | 33.7dp |
| 360dp (the Android floor) | 26.2dp | **32.2dp** |

**So the rule is 32.2dp minimum on a board square at the narrowest supported width**, which
clears WCAG 2.2 AA 2.5.8 and its exception for a presentation that is essential, since a
grid of ten is the puzzle. It does not clear AAA 2.5.5 or Apple's 44pt and cannot while
keeping ten columns. **Everything that is not a board square clears 44dp** and does: header
buttons 48, rule chips 48 tall, boosters comfortably past it.

**Dynamic type.** Sizes are declared in scalable units throughout, so system scaling applies
everywhere. Verified at font scale 2.0 on the board, Settings and every dialog. Two things
needed fixing and both were fixed in the design system rather than at a call site: the
booster row wraps so a long ad-offer label is never broken mid-word, and dialogs inset
themselves and cap at the window height, so a long explainer scrolls inside the card instead
of pushing its title under the clock.

**Reduce animations** reaches the design system as one composition local, provided once at
the app root, so a new screen honours the setting without its author knowing the setting
exists. Everything that moves on its own reads it there: the dogs hold frame 0, the board
entrance shortens, dialogs fade rather than spring, and the achievements stagger stops.

**Still unverified: VoiceOver on iOS.** The semantics are `commonMain` and
platform-independent, but no pass has been run.

**Where it lives:** `libraries/ui/.../components/board/BoardCellLabels.kt`,
`libraries/ui/.../system/color/RegionPalette.kt`, `libraries/ui/.../system/Focus.kt`,
`libraries/ui/.../system/Local.kt`.

---

## The dog

The playing piece, and the app's character.

**Static poses** split into board weight and hero weight, and the split is a performance
boundary rather than a stylistic one. Board poses ship at 192px, because a cell on a 10x10
is about 108 physical pixels and up to a hundred are on screen. Hero poses ship at 512px and
are never larger than about 256dp with only one on screen. Callers pick a mood, not a file,
and that enum is the guardrail: shipping the originals everywhere would be 8.3MB of assets
and roughly 28MB of decoded bitmaps for one puzzle.

**Animation ships as sprite sheets, not animated WebP**, because animated WebP does not
render on Compose Multiplatform iOS. Coil's animated path goes through Android's image
decoder and Skia hands back a single frame, so shipping the clip would mean an animation
that works on Android and silently freezes on iOS. A sheet is one image decoded once and
shared by every dog on the board, which is what makes it affordable: at most ten dogs are
ever placed and they all read from one bitmap. There is still no third-party image library
in the app at all.

**A placed dog keeps itself company.** It plays a loop, holds still a moment, then plays a
different one. A single loop on repeat stops being seen after about three passes; what reads
as alive is that it sometimes stops. The schedule is deterministic and seeded per dog: never
the same clip twice running, a pause about half the time, and the pause sits on frame 0 of
the clip about to play, so it reads as the dog waiting to do something rather than freezing
mid-gesture. Each cell starts at a different frame and picks its own loop, or a board of dogs
blinks in unison and reads as a rendering glitch rather than a row of animals.

The board loops are head-only, because a body animation is mush at 34dp, and the two that
read as a head-shake are excluded because they pull focus off the puzzle. Hero dogs get a
smaller set of larger sheets and a slow vertical float, applied in the draw phase rather than
read during composition.

**The Focus system** is one design-system primitive rather than three overlays, because
dimming the screen and lighting one thing is used by the last-bone warning, the sniff, and
the tutorial coach marks. Things register where they are by key; the scrim dims everything
and punches holes. Blend-mode holes rather than four rectangles around one rect is what lets
a spotlight cover several scattered targets at once, which is exactly what the sniff needs.
A spotlight can declare its targets live, which is what "only the correct cell is tappable"
means in a guided lesson, and can carry a label, which is what makes the hole reachable by a
screen reader.

**Where it lives:** `libraries/ui/.../components/dog/Dog.kt`, `AnimatedDog.kt`,
`DogLoopSchedule.kt`, `libraries/ui/.../system/Focus.kt`. Source art and the sheet-building
script live in `art/` and `scripts/`.

---

## Feedback

**Send feedback** is its own page under Settings: a text field capped at 400 characters, a
send button, and a confirmation panel that replaces the form rather than a toast fired during
a transition. It forwards the note to Sentry as a user feedback report. There is no Sodogku
feedback backend and the no-accounts rule means there will not be one.

**A send is never reported as failed.** Telling somebody their complaint failed to send is a
second thing to complain about.

**Report a bug** is reachable from the shake gesture, from error screens, and from a
home-screen quick action. It carries an assisted log id, an error code and a context message.
Shake listens only while the app is in the foreground, so a shake in the background is not
detected rather than queued, and it is suppressed while the dialog is already up.

**Both player-facing forms say what they attach**, in as many words: the note is sent with a
log of what the app did this session, which is how the problem gets found without making the
player describe it.

**The floating feedback button** is a tester-build feature and does not exist in a player
build: no pointer handler, no per-frame layer recording, no panel in the tree. It is a
draggable 48dp circle whose position is stored as fractions of the available travel, so it
survives rotation, split-screen and a different device, and it is clamped so it can never be
dragged off-screen. Only the button itself is hit-testable, and the system-gesture exclusion
is scoped to its own rect so an edge-parked button does not lose its touch to the back
gesture. Its panel captures a screenshot **before** the panel covers the screen, using the
composition's own graphics layer rather than a platform capture API, so it needs no
permission. Recent logs are attached by default and the screenshot is removable.

**Home-screen quick actions**, the same two on both platforms and in the same order: the
daily challenge, and report a bug. Report a bug is second deliberately, because that row sits
directly above Delete App. The daily action carries no level id on purpose; the board is
resolved from the repository. A test in `:apps:integration` reads the iOS plist, the Android
shortcut XML and the Kotlin declarations and fails on drift between them.

**Where it lives:** `features/settings/impl/.../feedback/`,
`features/home/impl/.../bugreport/`, `apps/compose/.../devfeedback/`,
`libraries/navigation/.../AppShortcuts.kt`.

---

## Saved progress

Progress is device-local. It does not survive a reinstall, and Settings says so.

**Room**, currently at version 10 with real auto-migrations and **no destructive fallback**,
because there is no account and no server copy, so a wipe is unrecoverable.

| Table | Holds |
|---|---|
| `level_progress` | Per level: state, best score, best paws, best time, attempts, first cleared at, last played at. Rows are written lazily, so an untouched level has none. Every column is a best except attempts. |
| `daily_result` | One row per local date: pack index, outcome, score, paws, time. Insert-only, no update path; the primary key is the one-attempt-per-day lock. The pack index is recorded rather than recomputed, because the pool offset can move. |
| `play_day` | One column, the local date. The play streak, and nothing else. |
| `score_event` | Append-only points with an epoch timestamp, pruned on a retention window. This is what lets the weekly leaderboard price a window; a lifetime total cannot. |
| `achievement_fact` | One row per finished attempt, append-only, deduped on a per-attempt key. |
| `achievement_unlock` | What has already been announced, and when it was really earned. |

**Nothing derived is stored.** The lifetime score, both streaks, every achievement counter,
and both freeze allowances are folded out of the rows above on every read. A stored tally has
no witness and no server copy to rebuild from, and a fold makes a past bug retroactively
fixable.

**The in-progress board** is one blob on `AppData`, not a table, because there is only ever
one of it and nothing queries it. It stores placements, manual marks, wrong guesses, strikes
taken, score, combo, best combo, elapsed time, attempt number and boosters used. Auto-marks
are recomputed rather than stored, since a stored copy could disagree with the placements.
Elapsed time is stored as a duration rather than a start timestamp, so hours the app spent
closed are not charged to the player.

It stores strikes taken rather than bones remaining, because bones are a global holding
already on disk; what a resumed attempt cannot recover any other way is how cleanly *it* was
going, which is what the bonus and the badges are priced on.

**One slot, and the newest real board takes it.** A *clearing* write is held back when the
slot belongs to another board, because opening a fresh level produces an empty snapshot on
its first frame and writing that unconditionally threw away the half-finished level the
player had left behind. Over-correcting on that once meant the new board was never saved at
all, so a daily opened over an unfinished campaign level could not save its own loss. A real
snapshot always takes the slot; only the clearing write is held. It survives a lost board on
the **daily only**, because the day needs its position back and the campaign has Start over.

It is written after every move rather than on a lifecycle callback, because a force-quit
skips the callback.

**`AppData`** is one serialised record covering onboarding and tutorial flags, the four
Settings toggles, achievements visibility and its watermark, bones, sniffs and treats, which
boosters have been explained, the board snapshot, the install id, screen-visit counters,
feedback counters, review-prompt state, the Pro entitlement, and the accepted legal versions.

**Writes to it must go through an atomic update.** Several writers are live at once on a cold
start and every toggle and counter shares the one record. A read-then-write means two
overlapping writers each transform a snapshot the other has already replaced, and the later
one silently reverts the earlier. Both cache implementations override it and the interface
default carries a warning saying why any new one must too.

**Separate caches**, deliberately not on `AppData`: the skip allowance, the streak-prompt
state, and the ad state (offline grace, first-seen timestamp).

**Where it lives:** `libraries/storage/impl/.../db/AppDatabase.kt`,
`libraries/progress/.../db/`, `libraries/achievements/.../db/`,
`libraries/sodogku/.../AppCache.kt` and `BoardSnapshot.kt`.

---

## Remote config

Postgres-backed, edited through the `:apps:admin` web console, with a bundled fallback map
and an offline-first client repository. This is the live-ops lever and the reason the server
exists at all.

**The rule: config owns numbers and switches, the binary owns content and logic shape.** If
changing it needs a new asset, a new string or a new code path, it belongs in the binary. If
it is a threshold, a cap, a frequency, a URL or an on/off, it belongs in config. Getting this
split right determines what can be fixed on a Tuesday afternoon versus what needs a two-week
store review.

### Two hard constraints, both held by tests

1. **Every declared key has a bundled fallback.** The app has to be fully playable, correctly
   monetized and legally compliant on a first launch with no network, forever, if the server
   never comes back. `FallbackConfigCompletenessTest` checks that every declared path
   resolves, that the fallback equals the declared default, and that no orphan key sits in the
   map that nobody declares.
2. **Monetization keys fail open toward the player.** A config outage must produce *fewer* ads
   and *fewer* blocks, never more. A server problem must never lock a player out of a game
   they already paid for or already had access to. `MonetizationFailsOpenTest` reads every
   value against an empty map and against mistyped values and asserts the resolved behaviour.

Both constraints are about **defaults**. Tightening a number in config, more ads or a harsher
grace, is a live-ops decision and is allowed. It is the shipped fallback that has to be
generous, because that is what an outage resolves to. The admin console puts a confirm sheet
in front of the writes that block a player or make a monetization key fail closed, and on
prod that sheet requires typing the environment name. It does not refuse them.

**A mistyped value on a boolean key does not fall back.** Booleans resolve through
`toString().toBoolean()`, and `"banana".toBoolean()` is `false`, so a string written to
`daily.enabled` turns the daily off for everyone rather than resolving to the shipped `true`.
Numeric keys fall back correctly. The server's type check is what prevents this, and it only
covers keys present in the uploaded manifest, which is why `apps/admin/config-manifest-registry.json`
must list **every** declared key and why `ConfigManifestRegistryDriftTest` fails the build when
it does not.

**Kill switches are read at the point of use**, never captured in a ViewModel at screen entry,
so an emergency change lands on the next refresh rather than after a force-quit nobody performs
mid-incident.

**A declared key that nothing reads fails a test.** `ConfigValuesAreReadTest` scans the source
for a reader of every declared value and fails unless the key is on an explicit unwired list.
That list can only shrink. It currently holds three: `ads.failureMode` (the `LOCK` arm was never
built), `progression.lookaheadCount` (the silhouette level map does not exist; the level pane
shows every level), and `boosters.adGrantsPerDay` (nothing counts rewarded booster grants per
day).

### The keys

51 declared keys. Defaults below are the shipped fallbacks.

**`ads.*`**: `enabled` (true), `newUserGraceLevels` (5), `newUserGraceMinutes` (5),
`failureMode` (`CONTINUE`, unread), `offlineGraceLevels` (3), `offlineGraceMinutes` (20),
`rewardedPlacements` (all four on; an unknown id resolves enabled).

**`progression.*`**: `skipsPerDay` (3), `skipAfterFailedAttempts` (2), `lookaheadCount` (5,
unread), `starterDogLevelsPerBand` (3).

**`boosters.*`**: `startingSniffs` (3), `startingTreats` (3), `treatSchedule` (every 3rd from
level 1, 6th from 21, 12th from 61, 25th from 151), `adGrantsPerDay` (5, unread),
`proSniffsPerAttempt` (3), `proTreatsPerAttempt` (3), `refillTo` (3).

**`scoring.*`**, 17 keys: `basePerPlacement` (10), `completionPerCell` (4), `comboStep`
(0.08), `comboMax` (2.0), `speedWindowMs` (16000), `speedMaxMultiplier` (2.0),
`livesBonusRate` (0.8), `difficultyBonusRate` (0.2), `boosterPenaltyRate` (0.15),
`twoPawFraction` (0.53), `threePawFraction` (0.64), `fourPawFraction` (0.77),
`fivePawFraction` (0.85), and the four praise cutoffs `nicePraiseAt` (1.5), `greatPraiseAt`
(1.9), `excellentPraiseAt` (2.4), `perfectPraiseAt` (2.9).

**`daily.*`**: `enabled` (true), `freezesPerMonth` (2), `restoreMaxDays` (3),
`restoreDaysPerMonth` (3), `poolOffset` (0).

**`paywall.*`**: `triggers` (offline block, continue level, skip level),
`offlineBlockEnabled` (true), `adStandInEnabled` (true), `sessionCap` (2). The price is never
here; it comes from the store.

**`legal.*`**: `termsVersion` (1), `termsUrl`, `privacyVersion` (1), `privacyUrl`,
`forceReacceptBelow` (0).

**`upgrade.*`**: `minSupportedVersionCode` (0), `softUpdateVersionCode` (0),
`maintenanceMode` (`off`), `maintenanceMessage` (empty). These sit under `upgrade.` rather
than `app.`, which is where an earlier draft put them: the admin console's kill-switch panel
was already built against `upgrade.*`, so `app.*` would have left the one control that has to
work in an emergency editing a key no client reads.

**`app.*`**: `reviewPromptAfterLevel` (10). Clearing that campaign level asks the review
coordinator, which keeps its own rationing on top. Zero turns it off.

**`features.*`**: `dailyChallenge`, `achievements`, `boosters`, all true. One per
shippable-but-hideable feature, so anything can be dark-launched.

**`telemetry.*`**: `appEventsEnabled` (true), `klogForwardingEnabled` (true),
`appEventsSampleRate` (1.0). Declared in `:libraries:telemetry:impl` rather than
`:libraries:config`, which is why `:apps:integration` is the only place that can see the whole
declared set at once.

One key sits outside the registry: `config.refreshThrottleMs` (5 minutes) is declared next to
the repository that reads it and is in neither completeness test.

### What stays in the binary, and why

| Thing | Why not config |
|---|---|
| Level packs | Content. Needs generation and CI verification. A bad pack is worse than a stale one. |
| Achievement definitions | Each needs a glyph and copy, so a new one needs a release anyway. |
| Game rules and the scoring formula shape | Only the coefficients are tunable, not the formula. |
| Band structure and the difficulty curve | It is the pack. |
| Ad unit ids and the store product id | Changing one is a store operation, and an outage that blanked them would take ads and purchases down together. |
| The Pro price | Per-storefront, and only the store knows it. |
| Anything needed before the first config fetch | Onboarding, the tutorial, level 1. |

**Where it lives:** `libraries/config/.../values/`,
`libraries/config/impl/.../model/FallbackConfigMap.kt`, `apps/admin/`, `apps/server/`.

---

## Telemetry

Sentry, Loki and Tempo, pivoting on a session id, with the conventions and the authoritative
event registry in [`../practices/app-events.md`](../practices/app-events.md). **That page is
the source of truth for which events exist and which are deliberately absent**, and a contract
test holds it against the code.

**No per-tap event.** Forty taps per level across a thousand levels is a volume and a cost
problem. Taps are aggregated into the completion event.

Broad shape: game lifecycle (`game.level_started`, `game.level_completed`,
`game.level_failed`, `game.level_abandoned`, `game.continued`, `game.skipped`,
`game.booster_used`, `game.booster_no_op`, `game.campaign_completed`), the daily
(`daily.started`, `daily.completed`, `daily.reviewed`, `daily.freeze_used`,
`daily.streak_restored`), ads (`ads.gate_shown`, `ads.result`, `ads.offline_block`,
`ads.stand_in`), purchase (`iap.paywall_shown`, `iap.purchase_result`, `iap.restore_result`),
onboarding and tutorial, the launch gates, the offline banner, and leaderboard submission.

Two things worth knowing when reading a dashboard:

- **Ad latency is measured on a monotonic clock**, not the wall clock, so a clock change
  cannot produce a negative or absurd duration.
- **Purchase and restore outcome names are hand-written literals**, not derived from class
  names, because R8 renames those and the dashboard would silently start counting nothing.

**The only identifier is an install id**, a per-install UUID minted on first read that dies
with an uninstall. The app never shows it to anyone. There is deliberately no way to attach a
user or an email to a report: both seams existed, compiled, had no caller, and would have made
two published sentences in the privacy policy false, so they were deleted rather than left
dormant.

**Six dashboards** are committed as JSON under `ops/grafana/`, one file per dashboard,
imported by hand: level drop-off, difficulty calibration, the ad funnel, paywall conversion,
daily retention, and the tutorial funnel. A contract test holds their queries against the
event registry.

**There is no in-app analytics opt-out.** `telemetry.appEventsEnabled` is an operator switch,
not a player one, and how a player asks for their data to be deleted is an open question in
`docs/OWNER-TODO.md`.

---

## What the game does not have

Not oversights. Each of these was decided, and most of them were built and then removed.

- **No accounts, no sign-in, no cloud save, no user-scoped server state.** The identity stack
  was removed rather than disabled. Progress is device-local. Switching phones carries the Pro
  entitlement via store restore and strands the progress; the cheap fix, if it is ever wanted,
  is an export/import code the player pastes, not a server.
- **No sharing.** The library, both platform launchers, the share button, the share sheet
  local, the feature flag, the event and the strings are all gone.
- **No give-up on the daily.** See [The daily](#the-daily).
- **No interstitials, no banners, no app-open ads.** See [Ads](#ads).
- **No free first mistake.** See [The board](#the-board).
- **No progressive disclosure on the level list.** Every level is visible, locked or not.
- **No sound.** There is no audio anywhere: no clips, no player, no toggle. It is in
  `docs/backlog.md`.
- **No server-delivered level packs.** Generation stays offline on a JVM; only delivery could
  move, and that is a backlog item rather than a plan.
- **No cosmetics economy or dog skins.**
- **No leaderboard on Android today**, because the Play Console board ids do not exist yet.
  The code is there and inert. See [Leaderboards](#leaderboards).
- **No mirrored platform achievements.**
- **No in-app analytics opt-out**, and no way to request deletion in the app.

**Not in this list, because it is not settled:** the kids-versus-general-audience decision.
See [Audience and consent](#audience-and-consent).
