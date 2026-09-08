# What Meowdoku actually looks like

Pulled from the App Store listing on 2026-09-07. Three screenshots are next to this
file: `meowdoku-board.png` (opening board), `meowdoku-midgame.png` (two cats placed),
`meowdoku-solved.png` (finished).

This is the look the user asked for — "candy crush vibe", "bubbly", "looks a lot like
Meowdoku". It is not a request to clone the app; the rules are LinkedIn's Queens and the
theme is ours. It is a request to match the *warmth*, which is where we are furthest off.

## The single biggest gap: temperature

Meowdoku's page background is a warm cream, somewhere around `#F4EEE7`, and every text
colour is a warm brown rather than black. Sodogku's is a cold near-white with pure black
text. That one substitution accounts for most of the difference in feel before a single
component changes — a cold grey background reads as a utility, a warm cream one reads as
a toy.

## What they do, item by item

**The board sits on a card.** A white rounded rectangle with real padding around the
grid, floating on the cream. Ours is bare cells directly on the page, which is why the
board reads as a table rather than an object.

**Region fills are pastels, not saturated.** Pink, butter yellow, sky blue, lavender,
sage. Ours are a strong blue, a hot pink, an orange and a lime — closer to a chart
legend than to candy. The pastels also leave room for the white X to be the loudest
thing on the cell, which is the point.

**The X is thick, white and rounded.** Roughly 60% of the cell, heavy stroke, round
caps. Ours is a thin dark cross in the region's ink colour. Theirs reads as a friendly
"nope"; ours reads as a spreadsheet.

**A placed cat gets a yellow starburst behind it** and its cell tints green. On
placement the affected row and column glow and the rest of the board dims — the
midgame screenshot catches this mid-animation. This is the juice the whole look rests
on, and we have nothing equivalent: our placement is a pop and a shake and no
consequence is shown on the lines the placement just resolved.

**Two centred white pills** hold the cat counter and the hearts, side by side under the
title. Ours are pushed to opposite edges, which leaves a hole in the middle of the row.

**The rule chips are one white container** holding three inner chips, with the currently
relevant rule outlined. Ours are three separate grey pills with no grouping, so they
read as three unrelated buttons.

**The header is a back arrow, a centred title and a gear**, all circular white buttons
on the cream. No score on screen at all during play.

## What we should not copy

- **Hearts.** Bones are ours and they are better for a dog game.
- **Dropping the score.** They have no score; we have a scoring formula, a paw rating
  and a par, and the score is the thing that makes a replay worth doing.
- **Their level chrome.** We put the level list in a slide-out pane because the puzzle
  is the home screen; they navigate to a map. Ours is fewer taps to the thing people
  opened the app for.

## The order to fix these in

1. Background temperature and text colour. Cheapest, and it changes everything else.
2. The board card and the pastel palette.
3. The white X.
4. The placement starburst and the row/column glow.
5. Grouping the rule chips and centring the pills.

---

## Where this stands, 2026-09-07 late

All five items above are done. `sodogku-board.png` next to this file is the same
opening board as `meowdoku-board.png`, shot on a device after the change; compare them
side by side rather than reading the list below.

**Matched.**

- **Temperature.** The page is `#F8F2ED` and every text colour is a warm brown. Body
  text sits at 10.6:1 against it and secondary text at 7.0:1, so nothing was traded for
  the warmth.
- **The board card.** White, 24dp corners, a soft shadow, and 10dp of white showing
  around the outermost cells. The gutter between cells went from 4dp to 6dp so the card
  shows through, which is what makes it read as a board rather than a table.
- **Pastel fills.** All ten resoftened. The white cross is now the loudest thing on a
  square, which was the point.
- **The white X.** Thick, round-capped, 60% of the square, drawn arm by arm.
- **Placement juice.** A landing dog gets a warm starburst; the row and column it
  resolved wash warm and lift very slightly, then settle.
- **The rule chips** are one white card holding three, with the rule a wrong guess just
  broke outlined.
- **The two pills** are centred side by side.
- **Circular white header buttons**, which was not on the list but is half of why their
  header looks placed and ours looked like leftovers.
- **A rounder face.** Fredoka for Display, Heading and Label; Poppins keeps Body and
  Caption.

**Still off, honestly.**

- **The board sits lower on the page than theirs.** Their grid starts about a third of
  the way down; ours is nearer the middle, because the booster row is pinned to the
  bottom and the two spacers around the board share the slack evenly. It is one weight
  away from matching and was left alone because moving the board changes where every
  coach mark points.
- **Their glow spills into the gutters.** Ours is clipped to the square, because the
  cells clip their own corners. Matching it means drawing the glow on the card behind
  the grid rather than in the cell, which breaks the rule that a cell owns its own
  animations.
- **Our rule chips carry miniature diagrams; theirs are text only.** Ours are better and
  this is a deliberate difference, but it does make our chip row taller and it is why
  two of the three labels wrap onto a second line.
- **The booster row is still three items on the page** rather than a grouped container
  like the rule chips. It reads fine now that the buttons are white pills, but it is the
  last row that has not been given a shape.
- **The dog is not the cat.** Their cat is black and white and reads on anything; our dog
  is near-white and needs the contact shadow the cells now draw under it. On the two
  lightest fills it is still the weakest thing on the board.
