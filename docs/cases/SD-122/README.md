# SD-122 case file — five paws for a solve that took a while

On current `main`. This is the mirror image of SD-116, which was the complaint
that five paws could not be earned at all.

## The report

[SODOGKU-N](https://elijah-dangerfield.sentry.io/issues/SODOGKU-N), event
`a6a6e916b1a34e50a2fc186a4d2bbf85`, 2026-09-16 15:45:04Z, build `05b633b96a09`,
session `1ba50ef1-4f55-4832-870f-ce8b91da99c3`, GameRoute.

"I got 5 bones even tho I took a while. and again I'd like this to be full
screen. like the Duolingo lesson celebration. but just a dialog. same for the
streak celebration of either starting or incrementing."

He says "bones" and means paws. The second half of the report is a design ask
and is filed separately as SD-123; this case is the first sentence only.

## The exact run

`screenshot-level16-flawless.jpg` is the win sheet he was looking at:

| | |
| --- | --- |
| Level | 16 |
| Board | 5x5 (HUD reads 5/5 dogs) |
| Verdict | Flawless, five paws |
| Score | 745 |
| Time | 1:26 |
| Mistakes | 0 |

`session-log.txt` corroborates the timing exactly and gives the move-by-move:

```
15:42:49.291  game.level_started        level 16
15:43:52.021  Marked / commit / PlacedDog
15:44:13.838  Marked / commit / PlacedDog
15:44:14.763  Marked / commit / PlacedDog
15:44:15.770  Marked / commit / PlacedDog
15:44:15.957  game.level_completed
```

86 seconds wall clock, and note the shape of it: a 63-second gap before the
first placement, then three placements in two seconds. Four `PlacedDog` events
on a board showing 5/5, because level 16 is inside the starter-dog opening run
that SD-113 added, so one dog was already down.

That starter dog is the seam SD-116's regression test guards. It is almost
certainly not the cause here, but check it first, because the same seam produced
the opposite bug six days ago.

## Why this is worth investigating rather than just retuning

`ab0c189`, "widen the scoring range so the top paw is reachable by a run that
earns it", landed on Sep 10 to fix SD-116. SD-116's own sweep reported the
slowest flawless pace that still earns five paws as roughly 26 to 40 seconds on
a 4x4 and 60 to 87 seconds on a 6x6 daily. A 5x5 at 86 seconds sits at or past
the top of that range, which is consistent with `ab0c189` having over-corrected
for this board size.

So the question to answer first is not "what should the threshold be" but
**"did widening the range for the starter-dog case also widen it for the
ordinary case, and by how much at each size?"** Sweep the ladder across sizes
before touching a constant.

## Done when

A flawless but unhurried solve lands below the top rung, a flawless quick solve
still earns five paws, and SD-116's regression test still passes.

## Hints

- `libraries/scoring/.../Standing.kt`, `ScoringConfig.kt`, `ScoreCard.kt`, and
  `features/game/impl/.../ConfiguredScoring.kt`.
- Every `scoring.*` key is remote-overridable. `FallbackConfigMap` matches
  `ScoringConfig.Default` key for key, so a tester build and the shipped
  defaults are the same numbers. The live console was never checked, and if it
  holds a different `fivePawFraction` or `speedWindowMs` nothing in the repo
  would show it. Rule that out before concluding anything about the code.
- `GameViewModelTest.aFastFlawlessSolveEarnsEveryPaw` is SD-116's guard. Your
  change must not break it. A second test for the slow case belongs beside it.
- The 63-second pause before the first move is worth thinking about. If the
  clock is meant to measure effort, a player who opens a board and stares at it
  is being measured the same as one who walks away. Whether that is the bug or
  just a curiosity is a judgment call, and it is the owner's to make. Raise it,
  do not silently redesign the clock.
