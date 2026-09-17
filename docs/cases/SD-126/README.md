# SD-126 case file — how a generated daily board opens

On current `main`, pack version 3. **Nothing was changed.** This file is the
answer and the evidence behind it, so the question does not have to be asked
again.

## The report

[SODOGKU-J](https://elijah-dangerfield.sentry.io/issues/SODOGKU-J), session
`bec35582-715e-4374-98b3-19ad3ac2e472`, 2026-09-12.

"maybe I'm just dumb but this daily board seems way too hard to solve without a
starter dog or a single square color. maybe we should review our boards we
generated?"

## The short answer

Yes, a daily can open with no starter dog and no single-cell region, and 38% of
them do. No, that is not a board a player cannot start: every one of the 730 has
a legal deduction available on the empty grid and none of them ever needs a
guess. What the owner actually hit is that **the first move on such a board is a
cross, not a dog**, and on his board the first *dog* was three steps and a
tier-3 argument away.

That is the daily working as `features.md#the-daily` specifies it. The levers
that would change it all cost more than the complaint does — see
`decisions.md`, entry of 2026-09-17.

## The measurement

`OpeningAuditTest.kt` beside this file produced every number below. To re-run it,
drop it into
`libraries/levels/src/commonTest/kotlin/com/sodogku/libraries/levels/` and run:

```shell
./gradlew :libraries:levels:jvmTest --tests '*OpeningAuditTest*'
cat libraries/levels/build/test-results/jvmTest/TEST-com.sodogku.libraries.levels.OpeningAuditTest.xml
```

The output is in `<system-out>`; the console prints nothing. Delete the file
afterwards — it asserts nothing and is a measuring instrument, not a test.

It reads the shipped packs rather than regenerating, so it measures what players
actually get, and it drives `DeductionEngine.nextStep` from an empty
`CandidateGrid`, which is the same engine `Difficulty` scores with and the same
one the sniff hints with.

### The daily pool, 730 boards

| | |
| --- | --- |
| Sizes | 240 at 6x6, 250 at 7x7, 240 at 8x8 |
| Difficulties | 328 tier 2, 256 tier 3, 146 tier 4 |
| Has a single-cell region | 450 (61.6%) |
| **No single-cell region** | **280 (38.4%)** |
| Boards with no opening deduction at all | **0** |

On an empty grid a row and a column always hold `size` candidates, so
`LastCandidateInGroup` can only fire on a region of one cell. A single-cell
region is therefore exactly the same thing as "opens with a forced dog", and the
owner's two phrasings — "no starter dog" and "no single square color" — name one
condition, not two.

The first step available on each board:

| First step | Tier | Daily boards |
| --- | --- | --- |
| `LastCandidateInGroup` (a dog) | 1 | 450 |
| `GroupConfinement` (crosses) | 2 | 242 |
| `NakedSet` (crosses) | 3 | 38 |
| `Contradiction` | 4 | 0 |

So of the 280 boards that open empty, **242 have a tier-2 opening cross** and 38
need a tier-3 one. And there is real work available before the first dog: those
280 boards yield between 2 and 39 crosses (mean 15.7) before deduction reaches a
placement.

### The number that actually explains the complaint

How deep the reasoning has to go before *any* dog can be placed:

| Deepest tier before the first dog | Daily | Campaign |
| --- | --- | --- |
| 1 (a forced dog is the first move) | 450 | 748 |
| 2 | 128 | 101 |
| 3 | 131 | 106 |
| 4 (a contradiction) | **21** | 45 |

152 of 730 dailies (21%) need tier-3 or deeper reasoning to place their first
dog, and 21 of them need a contradiction argument. Steps before the first dog
run from 1 to 19, mean 3.0.

The campaign has the same shape — 252 of 1000 open with no single-cell region —
but the campaign's first sixteen levels and the opening of each size band are
covered by the starter dog (`progression.starterDogOpeningLevels`, SD-113). The
daily is not. That asymmetry is the whole of the owner's experience.

## The reported board, solved

The report is dated 2026-09-12, epoch day 20708. `daily.poolOffset` ships at 0,
so that resolves to **daily id 269**: 7x7, tier 3, smallest region 3 cells. It
matches the screenshot exactly — 7x7, no pre-placed dog, no single-cell region.

```
      c0 c1 c2 c3 c4 c5 c6
r0     A  A  A  A  A  B  B
r1     C  A  A  D  A  B  D
r2     C  C  A  D  D  D  D
r3     F  F  F  E  E  E  D
r4     F  F  F  E  G  G  G
r5     F  G  F  E  G  G  G
r6     G  G  G  G  G  G  G
```

Solution, column by row: `2 5 1 6 3 0 4`.

**It is solvable with no guessing**, and that is already proved on every build,
not asserted here: `LevelPackVerificationTest.everyDailyLevelHasExactlyOne
SolutionAndItIsTheOneWeShipped` re-solves it with the exact solver, and
`difficultyIsAlwaysDeducible` requires it to score below
`Difficulty.BEYOND_DEDUCTION`, which means pure deduction finishes it.

The opening, as the engine finds it:

1. **Row 6 is entirely region G.** Row 6 needs a dog, so G's dog is in row 6, so
   every G square outside row 6 is dead: `r4c4 r4c5 r4c6 r5c1 r5c4 r5c5 r5c6`.
   Seven crosses, `GroupConfinement`, tier 2. This is the move the owner could
   not find, and it is the easiest thing on the board — a whole row in one
   colour.
2. **E and F take rows 4 and 5 between them.** After step 1 the only live
   squares in row 4 and row 5 belong to E or F, and both regions only reach rows
   3, 4 and 5. Two rows, two regions, so they are spent there and row 3 can be
   neither. `NakedSet`, tier 3. The engine states it from the other end, as D
   being confined to row 3: crosses `r1c3 r1c6 r2c3 r2c4 r2c5 r2c6`.
3. **The first dog, at r3c6.** Row 3's only surviving square.
   `LastCandidateInGroup`, tier 1.

Three steps to the first dog, and the hard one is in the middle. Step 1 is the
kind of thing a player learns to look for; step 2 is a two-region naked set,
which is not what a person three days into a habit is scanning for.

## Judgement

**The generator is not the problem and the boards are not unfair.** No daily is
unstartable, none requires a guess, and the mix on the shelf is exactly what
`LevelCurve.daily` declares — `LevelPackVerificationTest.theDailyPoolHoldsTheMix
ItDeclares` asserts that multiset on every build. The reported board is tier 3,
which is the daily's *mode*, not its ceiling. It is on the harder side of typical
and well inside spec.

**What is real is that the daily never teaches the first cross.** The campaign
hands out a free dog for sixteen levels precisely so the empty grid is met after
the rules are known. A player who opens the daily on day three has had that
scaffolding on the campaign and none of it here, and the game never says out
loud that a cross is a legitimate first move.

**The affordance for that already exists and he did not use it.** The sniff
shows where a dog *cannot* go, names the technique that proved it, and refuses to
spend itself when deduction has nothing to add (`features.md#sniffs-and-treats`).
On daily 269 it would have handed over step 1 with its reason. The screenshot
shows three bones and one X placed, so nothing was spent and nothing was lost —
he was looking for a dog on a board whose first move is a cross.

## What was considered and rejected

Each of these was measured or read before being dropped. They are recorded here
so the next person does not re-derive them.

- **Regenerate the daily pool so every board has a single-cell region.** Off the
  table outright. `DailyResult` stores the *pool index* a day was played at, so
  reshuffling or refiltering the pool rewrites which board every past day was.
  `LevelPackVerificationTest.theDailyNeverServesABoardTheCampaignAlreadyDid`
  spells this out: if the packs ever collide, the fix is a different *campaign*
  seed, never a different daily one.
- **Give the daily a starter dog.** Technically a one-line change in
  `GameViewModel` — the `isDaily` branch already exists. Rejected because the
  daily is one shared board with one shared score and a leaderboard on it, so a
  free dog changes par for everybody at once, and because
  `LevelCurve`'s daily doc argues at length that the daily must not be *easier*
  than the campaign level its player is on. It is a product call about the
  daily's premise, not a fix for a board that opens empty.
- **Add a generator filter now** — "a daily board must have a dog provable at
  tier 3 or shallower" — so it takes effect at the next regeneration. Rejected
  because `LevelPacks` promises the generator reproduces the shipped pack byte
  for byte for a given seed, and a filter that changes nothing today would
  silently change the pack for whoever next runs `:tools:level-generator:run`
  expecting a no-op diff. The constraint is written down below instead.

## For whoever next regenerates the daily pool

The pool wraps in two years and will have to grow. When it does, and only then,
the cheap fix for this is a generation constraint: **reject a daily candidate
whose first placement needs tier 4.** That is 21 boards out of 730 today, a 2.9%
yield cost, and it removes the openings that are genuinely a wall on a board
`features.md` describes as "a three-to-five-minute habit rather than a wall".
Filtering to tier 2 as well would cost 131 more and would flatten the daily into
something easier than the campaign, which is the thing the curve was rebuilt to
stop.

Append the new boards, do not refilter the existing 730 — same reason as above.
