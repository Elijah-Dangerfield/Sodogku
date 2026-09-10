# Boards past 10x10, with zoom and pan

**No, and no.** Boards should stop at 10x10, and zoom and pan should not be
built, because the geometry that was supposed to force them does not, the
generator gets 22 times more expensive per shipped board between 10x10 and
12x12, and the game's own difficulty rater says a 12x12 is a longer board rather
than a harder one.

That is a decisive no rather than a not-yet. The one thing here that would move
the answer is not a fix to any of it: it is a reason to want a bigger board that
is about the puzzle rather than about the number.

Everything below was measured on this checkout. The generator numbers come from
running the real pipeline, not from extrapolating the 10x10 figure. `MAX_SIZE`
and `REGION_LETTERS` were bumped locally to take them and reverted afterwards;
nothing from the harness is checked in.

## 1. Touch targets, and the number that was supposed to force zoom

The board's geometry is fixed and small enough to state exactly.
`GameScreen` pads the column by `Dimension.D500` (12dp) on each side,
`BoardSurface` insets the card by `D400` (10dp), and `BoardCellGap` is `D200`
(6dp). So a cell is `(width - 24 - 20 - 6 × (n - 1)) / n`, and its touch bounds
are that plus 6dp, because `decisions.md` measured that Compose expands a
pointer node toward 48dp and clips at the neighbour, which makes the gutter live.

That model reproduces all four of the device measurements in `decisions.md` at
10x10 (32.2 / 33.7 / 35.5 / 37.3), so it is arithmetic against a checked
baseline rather than a new estimate. Extending it:

| Width | 10x10 drawn / touch | 11x11 drawn / touch | 12x12 drawn / touch |
|---|---|---|---|
| 360dp (Android floor) | 26.2 / **32.2** | 23.3 / **29.3** | 20.8 / **26.8** |
| 375dp (iPhone SE) | 27.7 / 33.7 | 24.6 / 30.6 | 22.1 / 28.1 |
| 393dp (Pixel 4a) | 29.5 / 35.5 | 26.3 / 32.3 | 23.6 / 29.6 |
| 411dp | 31.3 / 37.3 | 27.9 / 33.9 | 25.1 / 31.1 |

**The 44pt line falls at 8x8**, not at 12x12. On the 360dp floor an 8x8 square
gets 40.2dp of touch bounds and a 7x7 gets 46.0dp, so seven across is the
largest grid that clears Apple's 44pt and WCAG AAA 2.5.5. The campaign's 8x8
band starts at level 181. We crossed the line that is supposed to force zoom
three hundred levels ago, shipped it, and wrote down why in `decisions.md`: a
grid of N is the puzzle, which is the 2.5.8 essential-presentation exemption.

Going to 12x12 does not break that exemption. 26.8dp at the 360dp floor still
clears WCAG 2.2 AA 2.5.8's 24×24 minimum. The floor is not reached until 14x14
(23.0dp).

The reason no threshold breaks is that **a bigger board is not a bigger board**.
`BoardRows` divides a fixed square by `size`; the card is the same 336dp across
at 10x10 and at 12x12, so nothing overflows, nothing needs scrolling, and the
vertical layout is untouched. Growing N only makes the cells finer.

So the touch-target argument for zoom is not there. What is there is a
legibility argument, and it is quieter and worse. `RegionGlyph`'s own doc says
its silhouettes are "kept to shapes that stay legible at the ~40dp a 10x10 cell
gets", which was already optimistic at the 26.2dp a 10x10 actually gets on the
floor device. Ten distinguishable shapes at 20.8dp is where colourblind mode
stops working, and it stops working silently: the board still renders, the
glyphs are still there, and a player who needs them cannot separate a Ring from
a Circle. That failure is not fixed by zoom either, because a player who has to
pinch to tell two regions apart has lost the whole-board read that the puzzle is
made of.

## 2. Gestures, against a tap the game charges a bone for

Pan needs a slot and there is no free one.

The one-finger drag is already taken. `Modifier.dragAcrossCells` is the marking
gesture: a stroke across the grid crosses off every square it enters. It is not
incidental, and the comment in `BoardDrag.kt` explains the constraint it is
built around. It refuses to report or consume anything until the stroke reaches
a *different* square from the one it started on, precisely so that a sloppy
double tap is still a double tap. The double tap is the gesture that places a
dog and costs a bone when it is wrong. `BoardCell` deliberately does not
register `onDoubleTap`, because Compose withholds `onTap` for the double-tap
timeout once you do, and the second tap is recognised upstream against
`GameViewModel.DoubleTapWindowMs` (320ms) instead.

That leaves three options and all three are bad. A pan that claims at touch slop
eats the second tap of a double tap, which is the exact failure the drag
detector was written to avoid. A pan that waits for a second square is
indistinguishable from drag-to-mark on the same stroke, so one of the two has to
lose. A two-finger pan sits next to a two-finger pinch, is not discoverable, and
ends one-handed play, which is most of how this game is held.

The coach marks are worse, because they are cached geometry. `FocusRegistry`
stores a `Rect(positionInRoot(), size)` per target when `onGloballyPositioned`
fires, and `FocusScrim` cuts its holes from that cache in root coordinates. A
pan drawn the cheap way, as `graphicsLayer { translationX }`, never re-runs
layout, so the hole stays where the cell used to be while the cell moves out
from under it. `BoardSurface` already uses exactly that layer for the wrong-guess
shake, so the disagreement exists today and is only survivable because it lasts
a few frames. A pan drawn as `offset {}` does re-fire, at the cost of relaying
out 144 cells every frame, in a screen that already only registers spotlit cells
because "registering costs an `onGloballyPositioned` per cell" and already has a
decisions entry about a hundred semantics blocks costing frames.

Then there is the part with no good behaviour. `FocusScrim` swallows every
pointer down (`awaitFirstDown(requireUnconsumed = false)`, then `down.consume()`),
so while any coach mark is up the player cannot pan at all. The tutorial is safe
by accident: it runs on `TutorialBoard`, a fixed 5x5, and would never be off
screen. The sniff is the real case. `SniffHint` lights every ruled-out square on
the live board and hands `CoachMark` the union of them. With pan, a lit square
outside the viewport gets its hole cut off screen, so the scrim is uniformly dark
with nothing to look at, and `CoachMark`'s below-or-above flip degenerates to
`MinBottom`, parking the card at the bottom of the screen pointing at nothing.
Nothing crashes. The player has spent a charge on a hint they cannot see. Note
that `SniffHint` already passes `anchorBottomPx = 0f` because scattered squares
on a 10x10 have no meaningful anchor, so the anchoring story is already thin
before pan makes it wrong.

## 3. The generator, measured at 11x11 and 12x12

200 attempts per size through the same pipeline `Generator.build` runs
(`randomSolution` → `growRegions` → `refineToUnique` → `uniqueSolutionOrNull` →
`structuralProblems` → `Difficulty.score`), seed 20260907, default refine budget,
one JVM run on an M-series Mac:

| Size | Unique | Rate | Wall | ms/attempt | ms/board | Tier 4 | Seconds per tier-4 board |
|---|---|---|---|---|---|---|---|
| 8x8 | 164/200 | 82% | 5.1s | 25 | 31 | 31 | 0.16 |
| 10x10 | 107/200 | 54% | 24.4s | 122 | 228 | 38 | 0.64 |
| 11x11 | 76/200 | 38% | 83.4s | 417 | 1,098 | 20 | 4.2 |
| 12x12 | 50/200 | 25% | 320.8s | 1,604 | 6,417 | 23 | **13.9** |

Every single failure was `refineToUnique` exhausting its budget. Nothing was
rejected as ambiguous and nothing came out structurally broken, so this is one
cost curve rather than several problems, and `DEFAULT_REFINE_BUDGET` is the dial:
12x12's conversion rate can be bought back at proportionally more time, which
does not help.

The number that matters is the last column, because tier 4 is what the back of
the campaign is made of. `LevelCurve` gives both the 9x9 and the 10x10 band 80
tier-4 levels. Filling an 80-level tier-4 run costs about **51 seconds at 10x10
and about 19 minutes at 12x12**, which is 22 times as much for the same band.
The whole shipped pack today is 1,230 boards in about 42 seconds. Add two 12x12
bands and regeneration becomes a 40-minute step. That is not a one-off cost:
`Difficulty`'s own doc says retiering a technique re-rates the entire pack, which
is why the pack is regenerated rather than hand-edited.

The attempt budget itself would survive. `ATTEMPTS_PER_LEVEL` is 24, so a
110-level band is allowed 2,640 attempts and needs about 700. A 12x12 band would
not fail loudly. It would just be slow, which is the worse failure mode for
something that runs at build time.

Separately, here is everything that hard-caps at ten, because it is cheap to
list and expensive to rediscover. None of it is difficult and none of it is the
reason for the no.

- `Board.MAX_SIZE` and `Board.REGION_LETTERS` (`"ABCDEFGHIJ"`), and
  `PuzzleSolver.COLUMN_RANGE`, which is sized from `MAX_SIZE`. The `Int`
  bitmasks are fine to 32 despite what the comment on `MAX_SIZE` implies.
- `LevelCodec` encodes a solution as one digit per row, so a column index past 9
  has no representation. Its doc already says so.
- `RegionPalette.styles` has exactly ten colours and `RegionGlyph` exactly ten
  shapes, and `RegionPalette.get` wraps with `mod` rather than throwing. Region
  10 would render in region 0's pink, on a board whose entire rule set is one dog
  per colour. The palette also records a hard-won CIELAB separation floor of
  23.6, reached by pulling the two closest pairs apart deliberately; two more
  pastels come out of that headroom.
- `board_region_0` through `board_region_9` and `board_glyph_0` through
  `board_glyph_9`, with `BoardCellLabels.describe` wrapping the same way, so a
  screen reader would call region 10 by region 0's name.

## 4. Does size add difficulty at all

SPEC 1.7 is right, and the column that proves it is the last one. Same boards as
above, rated by the shipped `Difficulty` and stepped through `DeductionEngine`:

| Size | Cells | Tier mix (1/2/3/4) | Median steps | Mean steps | Steps per cell |
|---|---|---|---|---|---|
| 8x8 | 64 | 9 / 84 / 40 / 31 | 13 | 13.8 | 0.216 |
| 10x10 | 100 | 4 / 29 / 36 / 38 | 17 | 17.3 | 0.173 |
| 11x11 | 121 | 1 / 23 / 32 / 20 | 17 | 17.9 | 0.148 |
| 12x12 | 144 | 2 / 9 / 16 / 23 | 21 | 21.6 | 0.150 |

A 12x12 has 44% more squares than a 10x10 and needs 25% more deductions to
finish. Reasoning per square falls as the grid grows, monotonically from 8x8 up.
The extra squares are scanning, not thinking, which is precisely the "long board
rather than a hard one" claim, now with a number on it.

One thing cuts the other way and is worth stating rather than hiding. The tier
mix does drift upward: 36% of converted 10x10 boards rate tier 4 against 46% at
12x12. That is not the grid getting cleverer. `refineToUnique` has to kill more
rival solutions on a bigger board, so the boards that survive refinement are more
tightly constrained, and tight constraint is what the contradiction technique
feeds on. Either way the ceiling does not move. Tier 5 is `BEYOND_DEDUCTION` and
never ships, so the hardest 12x12 and the hardest 10x10 are rated identically by
the game's own rater. What the player gets for the extra 44 squares is more time
spent on the same depth of reasoning.

## The cheaper question: is there a "bigger" worth having?

**More colours on a 10x10 is not available.** Regions are exactly N on an NxN
board, and that is what makes one dog per region the same shape of constraint as
one dog per row. A 10x10 with twelve colours is a different puzzle with a
different rule set, not a bigger version of this one. It would need its own
solver invariants and its own uniqueness argument, which is a larger spike than
this one.

**Edge to edge on a tablet already ships.** `GameScreen` puts no `widthIn` on the
board, so at 800dp a 10x10 cell is 70.2dp drawn and 76.2dp of touch, well past
44pt. If a bigger board ever did ship, a tablet 12x12 gets 57.5dp drawn and
63.5dp of touch and would need no zoom whatever. Which is the point: the tablet
is not where the problem is, and neither, really, is the phone's finger. The
problem is that a 20.8dp square cannot carry a colourblind glyph, and that the
board it is part of costs 22 times as much to make.

## What to do instead

**Answer SD-20 no and unblock SD-13 on 4x4 through 10x10.** The campaign's size
ladder is finished at ten. There is no 11x11 band, no 12x12 band, no zoom and no
pan.

For SD-13 specifically, the measurements say the second five hundred levels are
close to free at the sizes we already ship. Filling out more 9x9 and 10x10 bands
at the current tier mix costs a couple of minutes of generation, which keeps the
pack regenerable in one sitting and keeps `LevelPackVerificationTest` cheap.
`PACK_VERSION` and the append-only rule already make growing the campaign safe.

Spend the difference on not repeating. Meowdoku's reviewers say its boards start
repeating around every 100, and `Generator.canonicalKey` already dedups up to the
eight grid symmetries and any renaming of regions, so we can make the
non-repetition claim honestly where a competitor cannot. That is a better thing
to be better at than a number on a grid.

If "bigger" is still wanted later for its own sake, the cheap version is a
stricter curve on a 10x10 rather than an eleventh column: more of the back half
at tier 4, and a band that opens at tier 3 instead of dropping to 2. That costs
generation time we have and touches nothing else.
