# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

## 2026-09-08 — seventy-three badges, and every one of them off the log the game already keeps

The catalog went from 21 to 73. The rule it was built under was not "reach 75", it was **every
new counter has to come out of fields `achievement_fact` already stores**, because a stat that
needs a new column starts everybody at zero and a stat that needs a new `LevelResult` field means
editing `:features:game:impl`, which this chunk stayed out of. Sixteen new stats, no new fields,
so all 73 back-fill from a player's existing history the first time anything is folded.

**Two of them are a new shape, and they are the reason `AchievementCounters` grew two fields.**
The class was `Map<Stat, Long>` and nothing else, which is exactly right for a counter that only
looks at one attempt. `RedemptionClears` ("clear a level that beat you") cannot be answered that
way — nothing about a single attempt says it is a rematch — so the fold carries the set of level
keys the player has failed and not yet come back to. `MinutesPlayed` carries milliseconds at full
precision and divides for display, because a hundred forty-second attempts each rounded to whole
minutes as they land is a hundred zeroes. Neither is a `Stat`; both are what the fold has to
remember to compute one.

The rematch set is keyed on `LevelResult.levelKey` (`mode:levelId`), not on the id. Campaign 7 and
daily 7 are different boards on a shared number line, which has already caused one round of bugs
recorded further down this file, and a loss on one must not arm a rematch on the other.

**It answers SPEC 8's rejected "Marathon" honestly.** That was turned down because *session*
length is not a property of an attempt and nothing tracks it. That is still true. Cumulative time
on the boards is a sum of `timeMs`, which is exactly a property of an attempt, so it is a badge
this log can actually justify.

**What was turned down, and why, since the ask was "ideally 75":**

- **A lifetime-score badge.** R1 landed `LifetimeScore` in `:libraries:progress` while this was in
  flight. The fold could sum `score` itself, but that sum is a *different number* from the one the
  header shows (theirs is weighted, and a replay only counts once), and two numbers in one app
  both called the total score is a support ticket. The `BestScore` ladder covers the axis.
- **"Cleared a level in every hour of the day."** Reachable, and it asks somebody to set an alarm
  for 3am twice. A badge that is only earnable by being annoyed at is not worth a tile.
- **A play-count ladder** ("finished 500 attempts"). It is the same tile as "cleared 500 levels"
  with a lower bar, which is the definition of padding here.
- **`DistinctSizesCleared` at 7.** Sizes only appear in ascending campaign order, so "cleared all
  seven board sizes" and "cleared a 10x10" are the same event. Two badges, one moment.
- **Anything keyed on difficulty tier or on the calendar date.** `LevelResult` records neither.
  Those are the next batch and they need the *game* to start recording, not the fold to get
  cleverer.

**The stopping point was 73, not 75.** Two more rungs would have been two more rungs.

**Targets that describe the game are now checked against the game.**
`AchievementEngineTest.everyAchievementInTheCatalogCanBeEarned` proves each target is reachable by
*some* history, which is a statement about the fold and nothing else — it would happily bless a
40,000-point badge on a formula whose ceiling is 31,760, or an eleven-long combo on a board that
holds ten dogs. `AchievementReachabilityTest` takes a **test-only** dependency on
`:libraries:levels` and `:libraries:scoring` and asks the shipped packs instead: par is the most
the formula can ever pay, a combo cannot outlive its board, and the campaign is 500 levels long.
Its `when` over `Stat` has no `else`, so a new stat has to answer "what bounds this" rather than
inherit "nothing does". Main source stays dependency-free.

That test is what set two of the numbers. `Jackpot` is 26,000 against a hard ceiling of 31,760
(a difficulty-4 10x10 at par) and a *practical* ceiling near 29,700, so it is a fast clean run on
the biggest board and nothing else. `Unbroken` is 10 because ten placements is the whole of a
10x10 — there is no eleventh rung to add, ever.

**Hidden badges are all in one section rather than each sitting with its criterion.** A mystery
tile filed under "Speed" has already given away the half of the surprise worth keeping. The
section is called Secrets, it holds all nine, and a test pins that the two sets are equal in both
directions.

**The grid is grouped now, and `Achievements.sections` is the source of truth** with `catalog` as
its flattening — one list, not two, so a badge cannot be in the catalog and off the screen. At 21
tiles the catalog's ordering carried the grouping implicitly, as its own comment claimed; at 73 it
does not, and the screen was a wall.

**The copy test is the one that will actually fire.** The exhaustive `when`s guarantee every badge
has *a* name, a description and a face; they cannot notice the mistake that happens when
seventy-three rows get typed out, which is a copy-pasted line leaving two badges sharing one
badge's words. `AchievementCopyTest` compares `StringResource`s by key, so it needs no Compose
harness to ask.

## 2026-09-08 — a border has to know what shape the thing is, and be drawn outside the clip

The earned badge tile drew a blue outline and the card's rounded clip cut its four corners off.
Two separate mistakes, and fixing either one alone leaves a visible fault.

**`Modifier.border(Border)` never took a shape**, so it drew a rectangle. On a rounded card the
corners of that rectangle are precisely the pixels the clip removes, so the tile ended up with
four straight blue lines and four bare corners. It takes a `Radius` now, defaulting to square,
which is what it always did.

**And the border has to come *before* the clip in the chain.** Compose's border draws the content
and then strokes on top of it, so a clip that comes afterwards is inside the border node and
shaves the stroke's own outer edge — a rounded border in the right shape, still a pixel thin at
the corners. Ordered the other way the stroke lands on top of everything, unclipped, at full
width.

Worth writing down because the code read correctly at every step: a `Border` type, a `clip`, a
`background`, all design-system calls in a plausible order. It is a two-pixel bug that only exists
at the corners and only on a rounded surface, and the only thing that settles it is a zoomed
screenshot before and after.

## 2026-09-08 — the badge detail sheet moves onto the design system's dialog

It was an in-place `Box` with a scrim inside the screen's content slot, and it paid for that three
times. No entrance animation, which is what the report was about: frame by frame off a
`screenrecord`, it went from nothing to a fully formed card and a fully dark scrim between two
consecutive frames. No back-press handling. And a scrim that stopped at the top bar, because a
sibling drawn in the content slot cannot cover the `Scaffold`'s app bar.

All three were already solved in `:libraries:ui`'s `Dialog` — the spring entrance recorded further
down this file, `BackHandler`, and a host mounted at the root of `App.kt` that draws over the whole
window. The card also pads itself, so the call site lost its own padding.

This is the standing instruction working as intended and the reason it exists: the sheet was
written separately, so it got none of it, and nothing failed. A surface that reimplements a
design-system behaviour does not look broken, it looks slightly worse in three ways nobody
attributes to the same cause.

**What it does not fix:** the dialog is composed conditionally (`state.selected?.let { … }`), so
its *exit* is not animated — the host entry is disposed the moment the selection clears. That is
how `GameDialogHost` behaves too, and matching it was deliberate: an exit animation here needs the
caller to hold the last-shown badge alive while the card leaves, and doing that at one call site is
how a design system grows two dismissal conventions.

## 2026-09-08 — the board says where it is and what is on it, and offers placement as an action

`BoardCell` draws its fill, its region glyph and its cross rather than composing them, which is
what makes a hundred of them affordable and is also why a screen reader met a hundred anonymous
boxes: there is nothing in the tree to infer meaning from. So the cell states its meaning. Every
part of it was already a parameter.

**Split across `contentDescription` and `stateDescription`, not one string.** The description is
the square's identity — "Row 3, column 4, pink" — and never changes. The state is what is on it,
and it is the half TalkBack and VoiceOver re-announce on their own when it changes under the
reading cursor. Folded into one string, a player who crosses a square off hears nothing back.
Verified on a device: with TalkBack running, marking a square put **"crossed off"** on TalkBack's
own speech-output overlay.

**In colourblind mode the label names the glyph, not the hue.** "Row 3, column 4, square". SPEC 16
says the glyph *is* the region's identity in that mode, and the one player who has turned it on is
the one for whom "periwinkle" is the least useful word available. The `colorblind` flag was
already a parameter on the cell; this costs a list index.

**Placement is an action, not a gesture.** Committing a guess is a second tap inside 320ms,
recognised in `GameViewModel` because the cell deliberately refuses `onDoubleTap`. Under
explore-by-touch the screen reader consumes the double tap and delivers one activation, so a board
with content descriptions and nothing else is a board that can be marked and unmarked and never
played — worse than an unlabelled one, because it looks finished. The cell declares `onLongClick`
(Android's double-tap-and-hold, verified as `long-clickable="true"` in a `uiautomator` dump) *and*
a custom action of the same name (what VoiceOver puts on its rotor). Neither is a fallback.

**The action sends two `CellTapped`s rather than a new `GameAction`.** The double tap is not
something the ViewModel is told about; it is two ordinary taps measured against a clock in
`GameViewModel.tap`. Sending both halves is the same input a thumb produces, so the events, the
scoring, the tutorial triggers and the bone all behave identically by construction and there is
nothing new to test in the ViewModel. It is in `GameScreen.placeAt` with the reasoning attached,
and it becomes a first-class action the day the 320ms window moves somewhere a synthetic pair
cannot reach.

**The strings are resolved once per board, not once per cell.** `BoardSurface` provides
`LocalBoardCellLabels`; `BoardCell` reads it. `stringResource` is a composable with per-call-site
state, and a hundred cells asking for fourteen each is fourteen hundred of them per board
recomposition. The description itself is built *inside* the `semantics` block, which the platform
only invokes when a service is reading, so a board nobody is listening to builds no strings at all.

**What is deliberately not labelled:** the dog image inside a cell (the cell already says "dog"),
the region glyph (same information as the region name), `FloatingPoints` and the placement
starburst (a celebration, and the score is already a labelled control), and the `LifeRow` bones
(the HUD pill they sit in is the thing to label, and it is a separate item on the punch list).

## 2026-09-08 — an icon with nothing to say has to spell that out

`Icons.Menu(null)` compiled, and three call sites passed `null` — including both buttons on the
game screen. Nothing failed, because `null` is *also* how a genuinely decorative icon is spelled,
so "no label" and "no label needed" were the same expression at the call site and in review.

`Icons.X(contentDescription: String)` is non-null now and `Icons.X.decorative` is the other case:
a decision somebody made and a word somebody can grep for. `IconButton` refuses a decorative icon
outright in debug builds, matching what `Icons.Filled` already does for a missing variant — an
icon button has no text to fall back on, so there is no correct use of one.

**The label moved onto the button.** Left on the `Icon` it landed on a node of its own: measured in
a `uiautomator` dump as a 28dp unfocusable child of a 48dp focusable button carrying nothing.
Whether a reader finds it was up to the reader. `IconButton` merges its descendants and sets the
description itself, and the dump now shows one `android.widget.Button` at 48dp with the label on it.

**`NoticeBannerDefaults.DismissDescription` is gone.** Its comment said `:libraries:ui` holds no
copy; the module has had `api(projects.libraries.resources)` all along. It uses `common_close`.

## 2026-09-08 — a scrim is a drawing, so what is under it stays reachable

`FocusScrim` swallows every touch and *reports* the ones that land in a hole; nothing under it is
interactive. None of that is in the semantics tree. The moment the board's squares became
activatable, a screen-reader player could place a dog through a coach mark that a sighted player
cannot even tap.

`Modifier.coveredByOverlay(covered)` (`hideFromAccessibility`) goes on the thing being covered, not
on the cover — Compose gives a sibling no way to reach back over what it was drawn on top of — and
`GameState.isCovered` lists the eight overlays exhaustively rather than summarising, because the
failure of a missing entry is silent.

**The cost, stated plainly:** the tutorial's "tap the lit square" steps cannot be completed with a
screen reader. The scrim owns the touch and only knows its targets as rectangles, so lighting one
as an accessible control needs a label on `Spotlight`, which is a wider change than this chunk.
"Skip tutorial" is labelled and reachable, so a screen-reader player can leave the guided run and
play; that is a worse first five minutes than a sighted player gets, and it is written down in
BUILD-PLAN C12 rather than closed.

## 2026-09-08 — a 10x10 cell cannot reach 44pt, and the number that matters is not the one drawn

SPEC 16 asked for 44pt minimum touch targets verified on the smallest supported device at 10x10.
Ten cells across cannot: `10 × 44 = 440dp` is wider than any phone. Measured, at 10x10:

| Width | Drawn cell | Reported touch bounds |
|---|---|---|
| 411dp (test emulator) | 31.2dp | **37.3dp** |
| 393dp (Pixel 4a) | 29.5dp | 35.5dp |
| 375dp (iPhone SE, the iOS floor at deployment target 18.2) | 27.7dp | 33.7dp |
| 360dp (the Android floor this assumes) | 26.2dp | 32.2dp |

The second column is the one that matters and it was a surprise. Compose expands a pointer-input
node's touch bounds toward the 48dp minimum and clips at the neighbour, so **the 6dp gutter is
live** — every square's target is the cell plus the gutter, measured as 98px against an 82px cell
in a `uiautomator` dump, and there is no dead space between two squares. No code was needed for
this; it was worth measuring rather than assuming, because the obvious reading of the layout says
the gutter is dead.

**Where that leaves the requirement.** 32.2dp at the assumed floor clears WCAG 2.2 AA
(2.5.8 Target Size (Minimum), 24×24), which also exempts a target whose presentation is essential —
a grid of ten is the puzzle. It does not clear WCAG AAA 2.5.5 or Apple's 44pt, and nothing that
keeps ten columns can. Everything that is *not* the grid does clear it: both header icon buttons
measure 48dp, the rule chips 48dp tall, the boosters 48dp. SPEC 16 now states the exception and
the floor instead of a promise the geometry cannot keep.

**What was rejected:** shrinking the board to 8x8 on narrow phones (changes the puzzle), and a
scrollable grid (a board you cannot see at once is not a board you can reason about).

## 2026-09-08 — two things broke at the largest system font, and one of them was a crash waiting

Checked at `font_scale 2.0` on the board, Settings and the dialogs. Settings was already fine.

- **"Free bones" broke mid-word as "Free bone / s".** Three controls no longer fit across 411dp.
  The booster row is a `FlowRow` now; the ad offer drops to a second line. Shrinking the label or
  clipping the offer were the other options and both hide a control the player is being sold.
- **The score explainer grew past the screen.** Its title ran under the clock and its only button
  was cut in half by the gesture bar, with no way to reach either. `Dialog` insets itself with
  `safeDrawingPadding` and caps at the window height from `LocalWindowInfo`.

**The near-miss worth writing down.** The first fix added `verticalScroll` to the dialog card.
`GameDialogs` already scrolls its own body, and an outer scroll hands the inner one an unbounded
height — *every dialog in the app* crashed on open, at every font size, with "Vertically scrollable
component was measured with an infinity maximum height". It shipped through a clean
`assembleDebug`, a clean `detekt` and a green test run, and the only thing that caught it was
opening a dialog on a device. The cap alone is the fix: what the inner scroll never had was a
bound, so it was a scroll container the length of its own content, which is a scroll container that
never scrolls.

## 2026-09-08 — a hundred semantics blocks cost frames, and most of it was the lambda

Adding a `semantics { }` to `BoardCell` regressed the board, measurably. Forty rapid taps across a
10x10, `gfxinfo framestats`, UI-plus-render-thread work (`HandleInputStart` → `SwapBuffers`), three
alternating runs per build so both halves see the same machine:

| | median | p90 | worst | over 16.7ms |
|---|---|---|---|---|
| Before any semantics | **8.4ms** | 10.7ms | 20.0ms | 3 / 203 |
| Naive inline `semantics { }` | **11.2ms** | 20.4ms | 29.7ms | 40 / 217 |
| Block memoised | 9.4ms | 14.3ms | 27.2ms | 14 / 211 |
| …plus the set hoist | **9.2ms** | 13.6ms | 20.5ms | 10 / 210 |

**What cost the 3ms.** `Modifier.semantics { }` takes a plain lambda, and a plain lambda that
closes over anything is a new object every composition. Compose compares those objects to decide
whether the node changed, so a block written inline invalidates all hundred cells' semantics every
time the board recomposes — whether or not anything is listening. `rememberBoardCellSemantics`
keys the block on what it actually says, so a square whose state did not change hands back the
same object and invalidates nothing. This is the same shape as the `Radii.Cell` getter recorded
below: a value that is constant in fact but new in identity, multiplied by a hundred.

**The two callbacks are behind a plain holder, not `rememberUpdatedState`.** Both are fresh lambdas
every composition, so a snapshot state each would be two hundred snapshot *writes* per board
recomposition for values nothing needs to recompose on.

**`GameState.placedCells` is a `get()` that rebuilds a set**, and the grid was asking for it twice
per square. Read once for the whole grid. Worth 0.2ms, and it was already wrong before this chunk.

**What is left, and why it stays.** 9.2ms against 8.4 — about 0.8ms of median and 2.9ms of p90 for
a hundred nodes that exist in the tree, with the worst frame unchanged at ~20ms. Ruled out as the
cause: content capture, which is on by default on the Play emulator image and walks semantics
independently of any screen reader — turning it off measured 9.1ms, inside the noise. It is the
nodes themselves. Both figures sit inside a 16.7ms budget and this is the price of the board being
playable at all without sight.

**On the measurement.** The first attempt at this reported 55–130ms medians for *both* builds and
looked like a catastrophic regression; it was the machine, running an IDE at 240% CPU and six
Gradle daemons. The before-and-after builds have to be measured alternately in one sitting, and a
number that moves by 15x between runs is a number about the host.

## 2026-09-08 — the region glyph contrast, measured rather than claimed

The entry below records the colourblind watermark landing at "1.48–1.67:1 to 1.82–2.02:1" after the
pastel retune. Recomputed against the ten shipped fills, at `GlyphAlpha = 0.60` and `DARK_INK`
alpha `0x8C`, the composited range is **1.74:1 to 2.02:1** — orchid at 1.74 and periwinkle at 1.76
are the two below the figure that was written down. Still over the 1.70 floor
`RegionPaletteTest` enforces, and confirmed legible on a device with the mode on, but the range in
that entry was optimistic by 0.08. Closest pair in CIELAB is pink/orchid at 23.6 and the luminance
span is 0.371, both as recorded.

## 2026-09-07 — a blocking launch gate is rendered instead of the nav host, not navigated to

`AccessDeniedRoute` and `OfflineBlockRoute` are both nav destinations that swallow back. That
works for them because both are *reactions* to something the player just did. The launch gates are
not: they are a verdict on the launch itself, and the requirement is that they cannot be got
around at all.

So `LaunchGateHost` wraps `AppNavigation` in `App.kt` and, when a gate is blocking, returns the
gate screen and never composes the nav host. There is no back stack entry to pop, no destination
for a deep link to resolve to, and `App.kt`'s deep-link collector drops incoming URLs while a
block is up rather than queueing them — a link that fired the moment a maintenance window ended
would land minutes late on a screen nobody asked for.

**What this costs, plainly:** a block lifting mid-session rebuilds the nav graph, so the player
returns to the start destination rather than to where they were. The in-progress board survives
(it is in `AppData`), and the alternative — an opaque full-screen overlay with the app still live
underneath — keeps a screen running that a deep link could still navigate while the wall is up.
A gate that is merely on top of the app is not a gate.

**A notice is drawn over the content in a `Box`, with the content first.** Deliberately not a
`Column` with the banner above it: inserting a sibling *before* the content changes its slot, and
Compose throws away the nav host and every screen under it every time a banner appears or goes.

The gate state is read inside the host and never in `App`, for the reason `SplashGate` already
documents — a state read in `App` recomposes the root, and the root rebuilding the graph has
pushed a duplicate start destination once before.

## 2026-09-07 — a config value read in an `AutoInit` constructor can never change

Found by running the review prompt on a device with an override set, and watching it do nothing.

`AutoInit` singletons construct in `Application.onCreate`. `AppConfigMap` at that moment is
`LazyAppConfigMap`, whose `map` getter falls back to `fallbackConfig.map` until the config stream
has emitted — which it has not, because the cached snapshot has not been read off disk yet. So a
`ConfiguredValue` resolved in an `AutoInit` constructor resolves to the **bundled default, on
every cold start, forever**. It is injected, it is named, it is read, and it is inert.

`ReviewPromptOnMilestoneLevel` now awaits `configStream().first()` before reading
`app.reviewPromptAfterLevel`. Worth stating as a general rule, because nothing catches it:
`ConfigValuesAreReadTest` is a text search and this passes it, and a unit test hands the class a
config map that already has the value in it. **Anything resolving config inside an `AutoInit`
constructor has this bug**, and the symptom is a console that appears to work.

## 2026-09-07 — the legal gate seeds a first launch rather than prompting it

`legal.termsVersion` ships as 1 and `AppData.acceptedTermsVersion` defaults to 0, so on the
letter of "compare accepted against config" every fresh install is already out of date — and with
a `forceReacceptBelow` set, walled out of a game it has never played.

`AppData` therefore carries `legalAcceptedAt`, and 0 means **never asked**, which is a different
state from "accepted version 0". The first resolve on a device with no record writes the versions
in hand and gates nothing. The version gate exists to notice a *change* since acceptance, and on a
first launch there has not been one.

**The re-accept floor is capped, per document, at the version actually on offer.** A
`forceReacceptBelow` of 5 against a `termsVersion` of 2 is unsatisfiable: accepting records 2, 2 is
still under 5, and the player is walled out permanently by a config they can do nothing about.
Capping per document rather than as a whole matters too — terms can move while privacy does not,
and a floor applied to both would leave the privacy record permanently short of a version that
does not exist. Every block this raises is one the accept button can clear, and
`LaunchGatesTest` asserts the clearing, not just the raising.

**Closing the non-blocking banner is the acceptance**, and the copy says so. That is the split
SPEC 7.3 draws: a material change forces consent, a minor one takes continued use as consent. The
version travels on the gate object and on the action rather than being re-read at the tap, so a
refresh landing between the frame and the tap cannot record consent to something nobody was shown.

## 2026-09-07 — a maintenance gate with no message is treated as no gate

`upgrade.maintenanceMode` and `upgrade.maintenanceMessage` are two keys, so "blocking, with no
message" is exactly what a half-finished admin write looks like from the client. It is also a wall
with nothing written on it: the operator's text is the entire content of the screen, so raising it
without one strands the player on a blank apology.

Both keys or neither, in both modes. This is the one rule in the gates that is a judgement rather
than a default, and it is the shape a partial config would take, which is one of the four failure
modes SPEC 4.2 names.

Casing is forgiving on the mode itself (`Blocking` works) for the same reason the boolean parser
is — the console takes raw text and that is not a mistake worth punishing — but anything outside
the three declared words resolves to `off`. That forgiveness lives in the gate resolver, not in
`AppMaintenanceMode`: a `StringConfigValue` hands back what was written, `allowedValues` is a QA
menu hint rather than a filter, and `MonetizationFailsOpenTest` pins that split so it stays
deliberate.

**Blocking order is force-update, then maintenance, then legal.** An update is the only one of the
three the player can act on permanently, and it also replaces the client that is reading this
config; telling somebody on an unsupported build to come back after maintenance sends them back to
the same wall tomorrow. Legal is last because consent to keep using an app is worth nothing while
the app is unusable.

## 2026-09-07 — Pro's per-attempt boosters are a floor, not an assignment

SPEC 5.1 says Pro "starts every attempt with 3 Sniffs and 3 Treats", and the obvious reading of
that sentence is `sniffs = proSniffsPerAttempt` at the top of every attempt. That reading is
wrong, and it is wrong in the most expensive direction available: **it takes consumables away
from a paying customer.** A Pro player who has banked nine Treats from level rewards would open
their next board with three and six would be gone, with no event, no message and no way to tell
it from a bug.

The implementation is `maxOf(held, proTreatsPerAttempt())`. Two properties fall out of it:

- **It cannot take.** A holding above the floor is untouched, so the level-reward stash survives
  and Pro accumulates like everyone else, only from a higher starting point.
- **It cannot be farmed.** It never *adds* to a holding that already clears the floor, so
  restarting a level twenty times leaves a Pro player with exactly what one attempt gives them.
  The alternative worry — "Pro gets unlimited boosters" — is true in the sense that they never run
  out, which is what $4.99 buys, and false in the sense that matters: the number cannot be driven
  up by repetition.

This is the same shape as `boosters.refillTo` and `refillBones`, both of which are already floors
with no cap. Three places now say "never downward", which is the point: the economy has one rule
about holdings and it holds everywhere.

The top-up runs as its own `updateState` *before* the board's, rather than inside it. `state` lags
`updateState` by a dispatch, so computing the floor from `state.sniffs` at the top of
`startAttempt` would read the count from before `load()` landed. The transform's own argument is
fresh, so the board update that follows sees the granted values without asking.

## 2026-09-07 — the level reward pays on a first clear, and the pane stops lying about it

The level pane already drew a bare 🦴 on the frontier row. Nothing granted anything, so the pane
was advertising a prize that did not exist — which is a worse bug than a missing feature, because
it is the kind a player notices and we do not.

`boosters.treatEveryNLevels` (default 5) is now read in two places, and both matter:

- `GameViewModel.win` grants one Treat when the id is a multiple of it.
- The pane marks **every** row that pays, not just the frontier. One chip on one row is a
  coincidence; a chip every fifth row down 500 rows is a ladder, and the ladder is the reason to
  scroll the pane at all.

**First clear only.** The record is read from `recordBeforeAttempt` — the snapshot taken when the
level opened — because `progress.onCompleted` has already moved the live record to `Completed` by
the time the reward is decided. Asking the live record would answer "not a first clear" for every
level in the game. The same expression already decided `LevelResult.isFirstClear` for the
achievement fold, so it is now one property (`levelNeverCleared`) and the two cannot disagree.

Cleared rows keep the chip in a spent state rather than dropping it. Dropping it would break the
column down the list and would quietly remove the only evidence that the level ever paid.

**A cadence of zero pays nothing** rather than dividing by it. Zero is a real thing for an
operator to type and it means "no ladder"; there is no failing-open case here, because a player
who gets no free treats can still buy them with an ad and still play every level.

## 2026-09-07 — the skip's daily counter keeps a high-water mark, and the daily deliberately does not

`progression.skipsPerDay` needs a per-day counter that survives a force-quit and is not reset by
moving the device clock. The daily challenge faced the same question and answered "defend against
corruption, not against cheating" — no monotonic counter, because a device whose clock shipped
wrong and later corrected itself would be **bricked out of the daily entirely**.

The skip keeps the counter the daily refused, and the reason is the asymmetry in what a false
positive costs. If the high-water mark is wrong here, the player gets no free skips for a while
and can still play every level in the game. If it were wrong in the daily, the whole feature is
gone. Same mechanism, different blast radius, different call.

The rule is one line: **the recorded day only ever moves forward.**

- Clock back → the date is not later than the record, so nothing rolls over. A spent skip stays
  spent. This is the manipulation worth defending against, because it is the free one.
- Clock forward → a fresh allowance, *and* the recorded day jumps with it. Putting the clock back
  then returns next week's spent allowance, not today's fresh one, and the player gets nothing
  until the real calendar catches up. One day's skips bought with every day until then.
- Flying west repeats a date and correctly grants nothing new; flying east skips one and correctly
  rolls over. Both are one test each with a fake zone, following the daily's pattern.

It lives in its own persisted `skip_state` cache rather than in `AppData`, next to `ad_state` and
for the same reason: two numbers that mean nothing without the config key they are compared
against are machinery, not settings.

`SkipRepository` is a third interface rather than two more methods on `ProgressRepository`,
because the skip is a *transaction* — allowance, then ad, then write — and putting an ad network
behind the interface that answers "what has this player done" would make every caller of it depend
on advertising. It is shaped like `DailyRepository.useFreeze`, which is the same three steps in
the same order.

**The cap is checked before the ad**, so nobody watches thirty seconds of advertising for a skip
that was never available. And a **negative cap grants zero, not everything** — the one place in
this feature where failing open would be wrong, since a typo in the admin console would otherwise
become an unlimited skip printer.

## 2026-09-07 — an invalid remote `ScoringConfig` is discarded whole, not repaired

`ScoringConfig` validates in its `init` and **throws** — deliberately, since a
negative `basePerPlacement` made par negative and handed every player three paws
for scoring zero. Now that the fourteen `scoring.*` keys actually reach it, that
throw would land inside `place()`, on the tap that put a dog on the board. So
`ConfiguredScoring` catches it, logs, and returns `ScoringConfig.Default`.

**The whole set, not the offending field.** Rebuilding field by field — keep the
remote values that validate, default the rest — was the obvious alternative and
it is wrong twice over. Two of the rules are about *pairs*: `threePawFraction`
against `twoPawFraction` is a contradiction neither field can be blamed for, so
"which one do I discard" has no answer. And the coefficients were balanced
against each other; the entry above about a compressed multiplier range making
one paw unreachable is what a half-remote blend produces. A blend is a
combination nobody chose, that nobody could reproduce from the console, and that
would rate a run against thresholds it was never played under.

The cost is that one bad key throws away thirteen good ones. That is the right
trade for a set of numbers whose whole value is their relationship, and it is
loud: the log line names the config as invalid, and `ConfiguredScoringTest` pins
that a valid `basePerPlacement` sitting next to an invalid `completionBase` does
**not** survive.

`win()` resolves the config once and passes the same instance to `complete` and
`paws`, because the paw rating compares a score against a par derived from the
same coefficients. Two resolves across that comparison is a refresh landing
mid-sentence.

## 2026-09-07 — the opening handful of boosters is null, not three

`boosters.startingSniffs` could not be wired while `AppData.sniffs` defaulted to
`ConsumableRefillTo`. A record that already says "3" is indistinguishable from a
player who spent down to three, so the config value could never win: the default
baked into the record answered first, every time.

`sniffs` and `treats` are now `Int?`, null meaning "never granted any". The
opening grant comes from config; everything after it is a number the player owns.
That also removes a second answer to the same question — the compile-time
constant an operator cannot change.

`bones` keeps the constant, because nothing reads it as a starting count: an
attempt opens on `ScoringConfig.MAX_LIVES`, which is a game rule.

**Not finished.** `boosters.refillTo` now decides what a rewarded ad tops a
holding up to, but `BoosterPrompt` still prints `ConsumableRefillTo` in its
"watch an ad for N" copy. Raise the config value above 3 and the button
under-promises. The fix is to hand the prompt the number instead of letting it
reach for the constant; the file was owned by concurrent work in this session.

## 2026-09-07 — a feature switch has to be read where the feature draws itself

Three switches, three different seams, and the shape is the same each time: the
value object is injected and *called at the point of use*, never resolved into a
field or into state at construction. A `features.*` key exists so a feature can
be pulled without a release, and one that waits for a process restart is not a
switch.

- **`features.achievements`** gates the unlock toast in `GameViewModel` and the
  badge rows in Settings. It does **not** stop `AchievementsRepository`
  recording — the same reason the player's own toggle is display-only. A dark
  launch that also stopped the fold would hand everyone an empty grid on the day
  it was switched back on.
- **`features.boosters`** is read inside `boosterTapped` and `refill` rather
  than trusted from `GameState`, so a switch thrown mid-session stops the
  economy on the next tap. Bones are exempt: three strikes is a game rule, and
  that path is only their explainer.
- **`features.sharing`** reaches `ShareButton` as `LocalSharingEnabled`, which
  holds a `() -> Boolean` rather than a `Boolean`. A boolean provided at the
  root of the tree would be answered once — `App` is built to recompose almost
  never — so the switch would need a process restart. The composition local is
  the seam because only a composable can build a share string (see the entry on
  `LocalShareSheet`), and the button draws nothing rather than greying out: a
  disabled Share with no explanation is a support ticket.

All three default **on** and stay on against a malformed value, which is now
asserted directly rather than inferred from the empty map — see the entry on
`"banana".toBoolean()`. The absent-value tests could never have caught that
case, because the bug was in resolving a value that was present.

## 2026-09-07 — nineteen keys are still inert, and none of them is a missing call site

`ConfigValuesAreReadTest`'s `UNWIRED` went from 39 names to 19. (Thirty-nine:
the entry that introduced it says 37, and the set has always had 39. The set is
what runs.)

What is left splits in two, and the split is the useful part. Most of them are
keys whose **feature does not exist** — `progression.skipsPerDay` has no skip
button and no per-day counter; `progression.lookaheadCount` describes a level
map with silhouettes, and the level drawer deliberately shows every level with
locks instead; `boosters.treatEveryNLevels` has no level reward to attach to;
`boosters.adGrantsPerDay` has nowhere to count; `boosters.proSniffsPerAttempt`
is a Pro benefit SPEC 5.1 promises and the code does not implement.
`ads.appOpenCooldownHours` has an `AdFormat.AppOpen` that reaches the SDK and no
`AdPlacement`, no gate and no cold-start hook to space out.

`ads.failureMode` is the one worth naming on its own. `LOCK` — the level locks
until a rewarded ad reopens it — was never built anywhere, and `RealAdGate`
documents at length that it does not consult this key on the reward path *on
purpose*. Wiring it would mean inventing the harsher arm of an A/B test, in the
one place SPEC 4.2 says an outage must never be able to reach. It stays inert,
which is the correct state for a key whose only non-default value does not
exist.

The rest need a screen: the upgrade gate, the maintenance screen, the legal
re-accept sheet. Those are chunk-sized, not call-site-sized.

**The rule this leaves behind:** wiring a key to nothing is what produced the
debt in the first place, so a key with no feature stays on the list. The list
shrinking is the goal; the list shrinking *honestly* is the point.

---

## 2026-09-07 — the palette went pastel, and it had to get *further* apart to do it

Softening ten fills is not a colour change, it is a compression: every colour moves
towards white, and the ones that were already near each other arrive at the same place.
The first pastel pass looked lovely in a swatch and put two purples 14.2 apart in CIELAB.
On a 10x10 with both regions on screen — which is exactly where region colour is doing
the most work — that is a board a player cannot read.

So the set was tuned against a measured floor rather than by eye. The shipped ten hold a
minimum pairwise ΔE of **23.6**, which is *better* than the 21.5 of the saturated palette
they replaced, and `RegionPaletteTest.noTwoFillsAreCloserThanTheSeparationFloor` fails at
20. Two of the ten (periwinkle, orchid) are deliberately deeper than the rest so the
lightness ladder survives the softening: the luminance span is 0.37 against the old 0.42.

**What this cost, stated plainly.** The dog is near-white art, and on the two lightest
fills it now sits at 1.44:1. That is thin. `BoardCell` draws a soft contact shadow under
every placed dog, and that shadow is the difference between a dog and a smudge — it is
load-bearing, not decoration, and the KDoc on both ends says so.

**What it gained.** The region glyph — colourblind mode's actual answer, since ten hues
cannot survive deuteranopia whatever they are — went from a composited 1.48–1.67:1 to
**1.82–2.02:1**, because a dark watermark has more room on a pale fill. Its alpha went
0.45 to 0.60 at the same time, which is where most of that came from.

## 2026-09-07 — the player's cross is white, and stopped being the derived ink

`RegionStyle.ink` is still derived from contrast and still worth deriving. It just is no
longer what the player's "no dog here" mark is drawn in.

A mark has to be found at a glance across a hundred squares. The answer to "what reads on
ten different pastels" is one loud colour on all of them, not ten quiet ones that each
merely pass — and ten different marks also means the mark's meaning is carried by a colour
that already means something else. It is white, at 60% of the square, with round caps,
drawn arm by arm as before.

The derivation keeps its job for the **region glyph**, which genuinely does want the
higher-contrast ink for the fill it sits on, and which is a real accessibility mode rather
than a preference. Worth knowing: after the pastel retune the dark ink wins on all ten, so
the function currently returns one branch. That is not a reason to delete it —
`RegionPaletteTest.aDarkFillFlipsTheInkToLight` feeds it a fill only a light ink can win,
so a hardcoded `DARK_INK` fails, and the derivation will notice again the first time
somebody darkens a fill.

## 2026-09-07 — Fredoka, and the display face is only part of the scale

Reverses "Poppins, not a new font file" below, on the terms that entry set out: it said
Baloo 2 and Fredoka were both drop-in and named the condition as *needing to be rounder*.
Both were rendered side by side against real strings from this app — a level number, a
score, the rule chips, a paragraph of dialog copy — before choosing. Fredoka won on three
things that can be checked rather than argued:

- **It is rounder.** Round terminals and open counters. Baloo 2 is rounded but tall and
  narrow, and its lining figures — which is what a level number and a score *are* — come
  out condensed.
- **It has the Light this scale declares.** Fredoka's axis is 300–700, exactly the five
  weights `FontFamily.kt` names. Baloo 2 starts at 400, so `FontWeight.Light` would have
  aliased Regular and quietly done nothing. That was visible in the comparison render as
  two identical rows.
- **260KB against 1.6MB.** Baloo 2 carries a Devanagari companion in every static
  instance. Five Fredoka weights cost less than one Baloo 2 weight.

**It is bound to Display, Heading and Label — not the whole family.** The earlier note said
a swap would change nothing else in the type scale, and that is true but not obviously
right: Body and Caption are the sizes a player *reads*, and every word of legal text in
this app is set at 12sp. A rounded display face there is playful at the reader's expense.
Poppins keeps those two. The split is by what the reader is doing, not by size.

The weights ship as static instances cut from the variable font, not as the variable font
itself. Compose Resources' `Font()` takes a weight and picks a file; handing it one
variable face would need the `wght` axis set per style, which Compose Multiplatform does
not do for you on every target.

## 2026-09-07 — the board's consequence animation is nineteen cells, not a hundred

A placement should show what it *resolved*, not just that a dog arrived. The reference app
does this by glowing the row and column and dimming the rest of the board.

Dimming the rest is the expensive half: on a 10x10 it means all hundred cells animating on
every placement, a hundred concurrent animations and a hundred draw invalidations per
frame. Brightening the two lines instead is the same read — the resolved lines stand out
from the rest — for **nineteen**. `PlacementRole` is `None` for the other eighty-one, and
`placementPulseProgress` returns a shared zero `State` for them rather than allocating an
`Animatable`, so a board where nobody has placed anything holds none at all.

**What was measured.** On a 10x10, with the starburst and the nineteen-cell glow running,
`gfxinfo framestats` across the pulse reports frame work of **5.8ms at the median, 11.6ms
at p90 and 23.8ms at worst against a 16.7ms budget** — 2 frames of 69 over. Forty rapid
taps across the same board (which draws a hundred crosses stroke by stroke) reported 2.9%
janky frames at p90 28ms. Both on an emulator; the physical Pixel 4a was locked and
`screencap` refuses on a locked device, so nothing here was measured on real hardware.

**One thing found while measuring and worth not repeating.** `Radii.Cell` was written as
`get() = Radius(CornerSize(percent = 20))` — a getter, matching its neighbours. A hundred
cells ask for it on every recomposition, and a new `Radius` each time hands
`Modifier.clip` a shape that is never equal to the last, rebuilding a hundred modifier
nodes per frame for a constant. It is a `val`. The percentage itself is the point: a 4x4
gives cells three times a 10x10's size, and a fixed 8dp corner makes the easy boards look
like a spreadsheet and the hard ones look like sweets.

## 2026-09-07 — which square a placement resolved is derived in the design system, not the ViewModel

`GameState` has no "last placed cell". Rather than add one, `rememberPlacementPulse` diffs
the set of placed dogs the screen was already rendering and answers with the row and column
of whatever square joined it.

**Why there and not in `GameViewModel`:** the rule that a dog resolves its own row and its
own column is a fact about the board, not about this game's state machine, and putting the
derivation in `:libraries:ui` next to `BoardCell` keeps the promise that a screen cannot
forget a board animation — it hands over the set it already has and gets back the answer
for every square.

**Two cases that are deliberately not a placement**, both pinned by tests: nothing added
(a mark, a strike, a redraw) and *more than one* square appearing at once. The second is a
restore or an undo, and it has no single line to celebrate; picking one arbitrarily would
point the player at a deduction nobody made. That case is live now that C-late added a
board snapshot.

**The nonce is counted apart from the pulse.** Reading it back as `pulse.nonce + 1` looks
tidier and is wrong: a pulse that resolved to `None` takes the nonce to zero, and the next
placement then reuses a number a cell has already animated on and stays silent.

## 2026-09-07 — the rule chip that lights up is derived from the strike, and is usually none

Grouping the three rule chips into one card gave the outline something to mean. What it
means is "the rule your last wrong guess ran into", worked out in the screen from the
board, the placed dogs and `strikeCell` — no new ViewModel state.

**Null is the common answer and it matters that it is representable.** Most wrong guesses
on a partly-solved board break no *visible* rule: the square simply is not where the dog
goes, and nothing on screen yet says why. Outlining a rule there would teach a player
something untrue about a game they are already finding hard.

**The check order is not the order the chips are drawn in.** Adjacency is tested first,
because an orthogonally adjacent square also breaks the row-and-column rule — test that one
first and the subtler answer is never reached. A diagonal neighbour breaks adjacency alone,
which is the case `BrokenRuleTest` uses to prove the check runs at all.

The test board is worth a note: region A is the four *corners* of a 4x4, an awkward
partition on purpose. With compact regions every square that shares a region also touches
its neighbours, so there is no ordinary board shape that can tell the colour rule and the
touching rule apart.

## 2026-09-07 — the dialog card pads itself, and the floating-window host had nothing to do with it

Every dialog in the app had its title, its body and its close button flush against the card
edges. The cause was not five call sites each forgetting padding — it was that `Dialog` gave them
nowhere to put it. `Dialog`'s surface was a `Box` with a background and no inset, so "looks
right" was something each caller had to reconstruct, and the one caller that did (`BasicDialog`)
reconstructed it wrongly: it passed the caller's `modifier` to `Dialog` **and** again to the
`ModalContent` inside it, so any size, weight or click a caller attached was applied twice.

`ModalDialogDefaults.ContentPadding` is now applied inside the card and the call sites carry
none. That is the shape the standing instruction asks for — a new screen gets the right behaviour
without its author knowing the rule exists.

Two smaller things fell out of the same read:

- The surface was `clipToBounds()`, a **rectangular** clip on a rounded card, so a full-width
  button at the bottom of a dialog squared off the card's bottom corners. It is `clip(shape)`
  now. Nobody would have found this from the code; it is only visible with a button flush to the
  bottom edge, which is exactly the state the missing padding produced.
- The card is `0.85f` of the width rather than `fillMaxWidth()`, and there is a test saying so.
  The strip of scrim either side is the tap target that dismisses the dialog, so widening the
  card silently removes a gesture.

**The floating-window nav host does not control any of this.** `FloatingWindowHost` iterates
`FloatingWindowNavigator`'s back stack and calls `destination.content(entry)`; it decides *which*
nav destinations exist and nothing about how they appear. Dialog presentation is
`DialogHostState` → `DialogHost` → `DialogOverlay`, mounted once in `App.kt`, and every dialog in
the game reaches it through `Dialog(...)` without touching navigation at all. Worth writing down
because the two look interchangeable from the outside and only one of them is where the animation
lives.

## 2026-09-07 — `reduceAnimations` is a composition local, not a parameter

The dialog entrance is a spring, so it has to answer to the reduce-animations setting, and the
design system has no ViewModel to ask. `LocalReduceAnimations` is provided once in `App.kt` from
`AppViewModel.reduceAnimations` and defaults to `false` — never `error(...)`, so previews and
tests animate normally with no setup.

The spec is resolved **at the call site** (`ModalDialogDefaults.animationSpec()`, composable) and
carried into `DialogHostEntry`, not read inside `DialogOverlay`. The host renders from a snapshot
captured when the caller composed, so a local read in the host would be read outside the subtree
the value is provided to. The non-composable `animationSpecFor(reduceAnimations)` exists so the
choice itself is testable without a Compose harness.

Reduced motion is a plain fade on both the scrim and the card, not a shortened spring — the
setting is for players who find movement unpleasant, and a fast bounce is still a bounce. The
test pins this by asserting the card animates *exactly like the scrim* when reduced and *unlike
it* when not; the second assertion is the one that stops "reduce everything" passing.

## 2026-09-07 — the win and lose sheets stay out of `Dialog`, and borrow its padding token

`GameOutcomeSheet` looks like a dialog and is not one. `Dialog` dismisses on an outside tap, and
a lose sheet that vanishes when the player taps the board behind it strands them on a finished
grid with no retry. So `OutcomeLayout` keeps its own card — but it now takes
`ModalDialogDefaults.ContentPadding` rather than repeating the two numbers, which is what stops
the two kinds of card drifting apart the next time the dialog padding is retuned.

## 2026-09-07 — the score explainer names no numbers

Every scoring coefficient is remote config (`scoring.*`, SPEC §4). Copy that says "100 points per
dog" is a sentence that goes stale the first time anyone retunes the formula, in a build nobody
would think to re-check. `score_explainer_body` describes the *shape* — bigger board pays more, a
combo builds, speed stacks, finishing pays a bonus — and names no value that config can move.

The level explainer takes the same line for a different reason: it reads the campaign length from
`LevelPacks.campaign.size` rather than the 500 the spec names, so a content update that lengthens
the pack cannot leave the copy lying. There is a test pinning the shipped count at 500 so that
change is a deliberate one.

## 2026-09-07 — the tutorial's scrim reports taps instead of letting them through

A guided step that says "tap the lit square" needs the lit square to work while the rest of the
board is dead. The obvious build is a scrim that declines to consume taps landing inside a hole, so
the board cell underneath handles them normally. **It does not work, and it fails silently.** A
Compose `Modifier.pointerInput` node answers `sharePointerInputWithSiblings() = false`, so a
sibling the overlay covers is not in the hit path at all — declining to consume changes nothing,
because nothing else was ever going to be asked.

`FocusScrim` now takes an `onTargetTap: (FocusTargetKey) -> Unit` and reports which lit thing was
tapped; `TutorialCoachMark` maps the key back to a cell and dispatches the same `CellTapped` action
the cell would have. Double tap still works because the two taps arrive as two actions and
`GameViewModel` owns the 320ms window either way.

The alternative was a custom `PointerInputModifierNode` overriding
`sharePointerInputWithSiblings()`. It would restore the press animation on the lit cell, which the
current version loses, but it buys that with a hand-rolled node in the design system and a much
subtler failure mode. Not worth it for a lesson the player sees once.

Two smaller calls came out of the same work. `Spotlight.targetsAreLive` defaults to **off**, so the
last-bone warning keeps behaving as it did — a tap on the lit bones closes the warning rather than
opening the bone explainer behind it. And `FocusScrim` hands its `content` the **union** of the lit
rectangles rather than `first()`: the previous behaviour picked an arbitrary member of a set, which
put the auto-mark card on top of two of the three squares it was describing.

## 2026-09-07 — the tutorial's step pointer lives in the ViewModel, not in `GameState`

`state` lags `updateState` by a dispatch, so `tutorialIndex`, the script, and whether the run is
still active are plain fields on `GameViewModel`. The state only carries what the screen renders:
the current step and the squares it points at. Two consequences worth knowing:

- **Every advance is handed the board it should point at as parameters** (`level`, `placed`,
  `autoMarks`, `justMarked`). The auto-mark lesson lights the squares a placement *just* added, and
  that difference is computable only at the moment of the placement.
- **A step whose target square does not exist is skipped, not shown.** `resolveFrame` walks forward
  past any gesture step with nothing to point at. That branch is unreachable with the shipped
  levels; it exists because the failure it prevents is a scrim over an untappable board on the
  first level of the game, and a `noStepCanLeaveTheBoardUntappable` test asserting a property is
  worth more than one asserting today's fifteen steps.

`hasCompletedTutorial` is written when the level 3 script ends or on a skip, and never on the
levels before it. Which levels have already been taught is a per-ViewModel set, so losing level 1
and starting over does not replay seven coach marks, while quitting the app mid-lesson does replay
them — somebody who left halfway has not had the lesson.

## 2026-09-07 — the offline grace reads the OS, not `AppState.isOffline`

Running C8 on a device put the offline block screen up on full wifi. `AppState.isOffline`
is `!osOnline || !backendReachable`, and the dev server is not deployed yet, so every
launch was permanently "offline" as far as the ad gate could tell. SPEC 6 already said
what the rule should be — "only the former trips the grace, since ad networks are
reachable when our own server is down" — and there was simply no flow that expressed it.

`AppState` grew `isDeviceOffline`, the platform connectivity signal on its own, with a
default of `get() = isOffline` so previews and test doubles that model one signal keep
compiling. `AppStateImpl` is the only implementation with two things to tell apart.
`RealAdGate` and `OfflineBlockViewModel` are its only consumers, and they should stay
its only consumers: anything talking to *our* backend still wants the wider signal.

Worth noticing how this failed. Nothing crashed and no test could have caught it — both
flows are `StateFlow<Boolean>` with identical types and near-identical names, and the
fake in the test file had one field standing in for both. `FakeAppState` now models them
separately, which is what makes `ourOwnBackendBeingDownDoesNotSpendTheOfflineGrace`
possible at all.

## 2026-09-07 — the ad gate asks for a paywall, it does not navigate to one

`RealAdGate` lives in a library and has no business knowing a route exists. It calls
`PaywallCoordinator.requestOffer(trigger)`, which answers yes or no against
`paywall.triggers`, `paywall.sessionCap` and the entitlement; a `PaywallNavigator` in
`:features:paywall:impl` collects the resulting bus and does the navigating.

The bus has **no replay**, which makes `PaywallNavigator`'s `AutoInit` marker
load-bearing rather than a performance tweak — a request made before the collector
attaches is gone. That is the failure mode to watch for if the boot-warm set is ever
made lazy.

The alternative was `libraries/ads/impl` depending on `:features:paywall` and calling
`Router.navigate` itself. The module-boundary checker would have allowed it (it only
forbids feature-api → feature-api and non-app → impl), which is exactly why it was worth
writing down that we chose not to.

## 2026-09-07 — the paywall is offered *after* the rewarded ad, not instead of it

The ideal shape at a third strike is one sheet with two buttons: watch an ad, or buy Pro
and never see one again. That needs `GameViewModel` to ask which the player wants, and
`GameViewModel` was owned by another chunk this session. What shipped instead:
`showRewarded` requests the offer and then runs the ad exactly as before, so the sheet
lands on top once the ad closes.

It is deliberately the safe half of the trade. The reward path is untouched, so nothing
about the offer can withhold a reward, and `paywall.sessionCap` already stops it becoming
a nag. When the game layer is free, the better version is a choice sheet in
`GameViewModel` that calls `paywallCoordinator.requestOffer` on one branch and
`adGate.showRewarded` on the other — the coordinator API already supports it.

## 2026-09-07 — ad frequency state gets its own cache; the entitlement stays in `AppData`

Two persisted things landed in C8 and they went to different places.

The **entitlement** is one boolean in `AppData` (`isProEntitled`), where SPEC 5.2 says it
goes. It is a fact about the player, it belongs next to the other things a person would
recognise, and it is exactly the "don't roll a new cache for a single boolean" case
AGENTS.md names.

The **ad bookkeeping** is a separate `ad_state` cache. Five numbers — first launch,
last interstitial, levels since, grace start, grace spent — none of which mean anything
without the config key they are compared against. Putting machinery in the file the whole
app writes to would make `AppData` harder to read for no gain, and keeping it apart means
QA can reset the ad state without touching a player's settings.

**Per-session counters are in neither.** The interstitial ceiling and the paywall cap are
in memory, keyed on `SessionTracker.current.id`. A ceiling that survived a restart would
mean a player who force-quit twice never saw another interstitial, and one that never
reset would mean a week-long session never stopped seeing them.

## 2026-09-07 — Play Billing 8, not the 7 the spec names

SPEC 5.2 says "Play Billing 7". Play stopped accepting new releases on 7 before this was
written, so 8.3.0 shipped instead. The migration is two things and both are already in
`PlayStoreBilling`: `queryProductDetailsAsync` hands back a `QueryProductDetailsResult`
rather than a bare list, and `enablePendingPurchases(PendingPurchasesParams)` is
mandatory. `enableAutoServiceReconnection()` is on, which removes the reconnect loop
every Billing 5-era implementation had to hand-roll.

The one thing not to copy from a Billing tutorial: `BILLING_UNAVAILABLE` is **not**
mapped to "not owned". It means this device cannot do billing at all, and a device that
cannot ask is not a device that answered no. Same for `SERVICE_*` and `NETWORK_ERROR`.
All of them return `StoreOwnership.Unknown`, which is the value that leaves a paying
customer alone.

## 2026-09-07 — every ad id is in one file behind one boolean

`AdUnits.useTestUnits` is the whole migration. Google's published sample units are
committed for both platforms and the `Live` blocks are empty; when a live id is missing
the accessor falls back to the test unit rather than requesting an empty string, because
a blank unit id is an SDK error and an SDK error is one more way for an ad to "fail" —
and this app *pays the player* when ads fail. A typo would quietly hand out free rewards.

The AdMob **app** id cannot live there: both SDKs read it before any app code runs, so it
is in `AndroidManifest.xml` (`com.google.android.gms.ads.APPLICATION_ID`) and `Info.plist`
(`GADApplicationIdentifier`). That split is the one thing about this file that will catch
somebody out, which is why both ends say so in a comment.

## 2026-09-07 — the iOS ad network is compile-guarded, the iOS store is not

`IOSStoreBilling` is unguarded and real: StoreKit ships with the OS. `IOSAdNetwork` is
wrapped in `#if canImport(GoogleMobileAds)`, because the SDK arrives through SPM in Xcode
and nothing in this repo can add a package dependency to a project it cannot open. Until
somebody does, the guard makes every ad "fail", and the shared Kotlin grants the reward
anyway — correct behaviour, zero revenue, and an app that still builds.

The alternative was to commit the Swift unguarded and leave the iOS target broken until
the package is added. A repo where the iOS app does not compile is a repo where nobody
notices the next thing that breaks it.

## 2026-09-07 — badge copy lives in `:features:achievements` (the api module), not its impl

The catalog carries stable ids and no words; an exhaustive `when (AchievementId)` in the UI is
what makes adding a badge fail to compile until somebody writes them. That `when` had an obvious
home in `:features:achievements:impl` next to the grid, and it is the wrong one: the unlock toast
fires on the **win sheet**, in `:features:game:impl`, and a feature impl may only depend on
another feature's *api*. Putting `AchievementCopy` in the impl would have forced a second copy of
the mapping in the game — two exhaustive `when`s over the same enum, one of which nobody would
remember to extend.

So `AchievementCopy` is in the api module, alongside the route, and returns `StringResource`
rather than `String` — non-composable, so it is usable from a `remember` or a preview and testable
without a Compose harness. The glyphs are Kotlin constants rather than string resources: there is
nothing in an emoji to translate, and 21 more rows in `strings.xml` is 21 more things for a
translator to skip past.

## 2026-09-07 — the share sheet is a composition local, not a ViewModel dependency

`ShareText.format` is handed a title, a streak line and a footer that are **already localised** —
that is the whole reason `:libraries:sharing` holds no English. The consequence is that the only
thing in the app that can build a share is a composable, because `stringResource` is the only way
to resolve the words. A `ShareLauncher` injected into `GameViewModel` would therefore have to be
handed a string the composable built and passed back down through an action, which is a round trip
for no gain.

`LocalShareSheet` (in `:libraries:ui`) provides the launcher to the whole tree from `App.kt`,
defaulting to a no-op so previews and tests get something harmless. `ShareButton` in the design
system does the formatting, so a screen supplies only the two things it alone knows: what the board
was, and what to call it. SPEC §9 wants three share points (win sheet, daily card, level-map
long-press) and this is what keeps the second and third from being a copy of the first.

**`:libraries:sharing` keeps its empty dependency list.** `ShareLauncher` returns `Unit` rather
than `Catching<Unit>` — unlike `WebLinkLauncher`, which it otherwise mirrors — so the interface
needs nothing from `:libraries:core`. That is not only bookkeeping: a web link that fails to open
leaves the player on an unchanged screen and needs an answer, while a share sheet that fails to
present has no recovery a caller could offer. The platform impls log and move on.

## 2026-09-07 — the achievements toggle hides the row that opens the grid, not the grid's contents

SPEC §8 says the toggle "suppresses toasts and hides the tab". There is no tab, so the question was
what "hidden" means for a Settings row and a screen. Turning badges off removes the **Achievements
row** from Settings; the toggle itself stays, or there would be no way back. The screen keeps an
"achievements are off" panel behind it, which is what a deep link or a stale back stack lands on,
and that panel says in as many words that recording carried on — because the reading that stops
somebody switching them back on is the one where turning them off threw the history away.

The toggle writes exactly one boolean into `AppData` and touches nothing else. `SettingsViewModel`
has no `AchievementsRepository` at all, and the test asserts the whole of `AppData` afterwards
rather than the one field, so a second effect added later fails rather than passing quietly.

## 2026-09-07 — nothing in the app navigates to `SettingsRoute`

Found while verifying C10 on a device, and it predates this chunk: `:features:settings` builds its
graph and `SettingsScreen` works, but no `router.navigate(SettingsRoute())` exists anywhere. The
board's gear icon opens `GameDialog.Settings`, an in-place sheet with three toggles, which is a
different and much smaller surface. The full settings page — and therefore the achievements grid
hanging off it — is currently unreachable in a shipped build.

Verification for this chunk was done by pointing the start destination at `SettingsRoute()`
temporarily, driving the device, and reverting. The real fix is one navigation call from the
board's settings sheet into `SettingsRoute`, and it belongs to whoever owns `:features:game:impl`
next; it is written up here so it is not rediscovered a third time.

---

## 2026-09-07 — the pack is a route argument, and it is a `Boolean`

**Decision:** `GameRoute(levelId: Int, daily: Boolean = false)`. Which pack a board comes from is
carried by the route, not by a mode the ViewModel can be switched into, and it is a plain
`Boolean` rather than the `PackKind` enum that already exists.

**Why the route and not a mode:** the route is what a process death restores. A screen that had
been switched into daily mode in place would come back as whatever level id the route still held,
in the campaign — the player force-quits mid-daily and reopens onto a different board, with their
attempt lost and the day still open.

**Why a Boolean:** an enum route argument has to be `@Serializable` *and* registered in a typeMap
at every registration site, and forgetting either crashes graph construction on iOS with a message
that names a different argument. That landmine is already recorded twice in `AGENTS.md`. Two packs
do not need a type to tell them apart, and a third would be the moment to pay for the enum.

**The daily ignores the route's `levelId`.** It resolves the board from `DailyRepository.status()`
instead, because the board and the date the result is written against have to come from one
snapshot of the clock. The route's id came from a card drawn at some earlier moment; trusting it
would let a screen opened a second before midnight record yesterday's board against today.

## 2026-09-07 — a lost daily is spent when the player walks away, not when the bones run out

**The constraint:** `daily_result` takes one row per date and never updates it (that insert-only
rule is what makes the one-attempt lock hold whatever the clock says), and SPEC §2 allows a failed
daily to be continued with a rewarded ad exactly like a campaign level. Those two together mean
the result cannot be written at the moment the third bone goes: the revive's clear would then find
the day already locked to a loss, and the player would watch an ad, finish the board, and get
nothing.

**Decision:** `onFailed` is written when the player leaves the loss sheet. A win writes
`onCompleted` immediately, as it always could. Starting over is removed from the daily's loss
sheet entirely — a second run at a board whose score commits to a date is the replay the
one-attempt rule exists to stop — but both ad revives stay.

**The hole this leaves:** force-quitting at the loss sheet leaves the day unwritten and therefore
still playable, so a determined player can retry today's board until they win it. That is
deliberate, and it is the same call already recorded under "what the daily defends against a moved
clock": the daily is device-local with no leaderboard, every defence costs an honest player
something, and someone who does this has only cheated themselves. Closing it properly means either
an upgradeable row (giving up the insert-only lock) or writing the loss immediately (giving up the
revive), and both are worse.

## 2026-09-07 — the shared level-id number line is a progress bug, not a lookup bug

**What it looks like:** `LevelPacks.campaign.byId(7)` and `LevelPacks.daily.byId(7)` are different
boards. The obvious consequence — resolve against the right pack — is one line. The dangerous
consequence is everywhere a level id is *stored* or *compared* as campaign progress:

- `progress.onAttemptStarted(id)` and `progress.onCompleted(id)` would unlock campaign levels the
  player has never seen, and clearing the daily would hand out campaign level `id + 1`.
- `progress.record(id)`, read for `isFirstClear` and `previousBestPaws`, would answer with a
  campaign level's history.
- `maxOf(unlockedThrough, level.id)` in `startAttempt` and `loadRecords` would widen the drawer's
  frontier to the daily's id — a 264 in a pool of 730 unlocks most of the campaign.
- The drawer scrolled to `currentLevelId - 1` and highlighted `level.id == currentLevelId`, which
  on the daily meant scrolling the campaign list to a locked stranger and calling it current.

**Decision:** the daily touches `ProgressRepository` for reads that are genuinely about the
campaign (`unlockedThrough`, for the drawer) and for nothing else. `campaignFrontier()` is the one
place that decides whether the level on screen counts as progress, and `LevelDrawer.currentLevelId`
is nullable so "the board on screen is not a campaign level" is representable rather than faked
with a number that means something else.

The last one is worth the emphasis: it was invisible in tests and obvious in two seconds on a
device. Every other item here is asserted by a ViewModel test that fails when the guard is removed.

## 2026-09-07 — every freeze outcome gets a sentence, including the one that means "no"

`FreezeResult` has four branches and the repository call can also throw, so the UI has five
answers to give. All five are a dialog with a title and a line of copy.

`Declined` is the reason this is not a `when` with three empty arms. It is what a player who
opened a rewarded ad and closed it early gets, and it is the outcome most likely to be read as the
app breaking: the ad disappears, the streak does not move, and nothing says why. A thrown
repository call maps to its own "not right now" rather than to `NothingToFreeze`, because telling
someone there is nothing to freeze when the database just failed is a lie they cannot act on.

## 2026-09-07 — the config registry drift guard is a test in `:apps:integration`, not a generator

`apps/admin/config-manifest-registry.json` is a hand-written transcription of the
`ConfiguredValue` classes, and until now nothing checked it. The obvious fix is a
Gradle task that *generates* it, which would make drift impossible rather than
merely detectable. It was rejected on cost: generating means running
`:libraries:config` code on the JVM, and that module has only Android and iOS
targets. Adding a `jvm()` target to it — and to `:libraries:core` and
`:libraries:flowroutines` underneath it — purely to feed codegen is a much larger
change than reading the same classes from a test that already has them on its
classpath. The test prints the exact JSON line to paste, so the ergonomics land
in the same place.

It lives in `:apps:integration` rather than in `:libraries:config` for a reason
that only shows up at the end: it is the only test module that can see **both**
halves of the declared key set. The `telemetry.*` values are declared in
`:libraries:telemetry:impl`, which `:libraries:config` may not depend on, so a
test there would have had to pin their paths and defaults as literals — the same
hand-maintenance it exists to remove. `:apps:integration` is an `:apps:*` module
and may depend on impls, so it reads them from the real classes. It also happens
to be the module `apps/admin/build.gradle.kts` named as the intended home when
this gap was first written down.

The residual seam is unchanged and worth restating: neither `SodogkuConfigValues.all`
nor the test's three telemetry classes come from the DI graph's
`Set<QaConfigValue>`, so a value contributed to DI and added to neither list is
still invisible. No unit test can close that one without an app.

**The non-obvious half is the Gradle wiring, not the Kotlin.** A file read at
test *runtime* is invisible to the up-to-date check, so editing the registry
alone left the test task `UP-TO-DATE` and the drift shipped. This was observed,
not theorised — the first mutation run reported BUILD SUCCESSFUL against a
registry with a key deleted and three values wrong. `inputs.file(registry)` on
the `Test` tasks in both `:apps:integration` and `:apps:server` is what makes the
guard real.

## 2026-09-07 — composite `JsonConfigValue` flags belong in the manifest after all

The registry deliberately omitted them, on the grounds that they aren't targeted
per version or locale and their defaults are large. Neither holds here:
`ads.rewardedPlacements` is a four-entry map and `paywall.triggers` a three-entry
list, and the console's "what did v1.0.1 ship with" view was quietly answering
that question wrong by two keys. The schema still can't type-check them beyond
"some JSON" — `ShippedConfigSchemaTest` pins exactly which keys are in that
state, so a third one can't join them silently — but an unparseable value there
is caught by `JsonConfigValue`'s decode fallback, which is a fail-open path.

## 2026-09-07 — the console warns; the server does not refuse

SPEC 4.2 says monetization keys fail open, and the admin API can write values
that run that backwards: `ads.failureMode = "LOCK"`, `ads.offlineGraceLevels = 0`,
a rewarded placement switched off. The server could reject those outright. It
doesn't, because SPEC 4.2 is explicit that tightening in *config* is a live-ops
decision and it is the shipped **defaults** that must fail open — a server that
refuses the harsher arm also refuses the A/B test the key exists for.

So the guard is a confirm sheet (`dangerousWarning`), which on prod also makes
the operator type the environment name. That friction is the reason the list is
short. Raising `paywall.sessionCap` or dropping `ads.interstitialEveryNLevels` to
1 earns nothing: warning about ordinary retuning would only teach the operator to
type the environment name without reading the sentence above it.

## 2026-09-07 — a mistyped boolean does not fall back, it becomes `false`

Found while working out what the server's type check is actually protecting.
`getValueRecursive` resolves a boolean as `rawValue.toString().toBoolean()`, and
`"banana".toBoolean()` is `false`. So a string on `daily.enabled` or
`features.sharing` does not resolve to the shipped default and does not log — it
turns the feature off on every device that fetches it. Numeric keys are luckier:
an unparseable number resolves to null and falls back.

This is why the registry being incomplete was not a cosmetic problem.
`ConfigSchema` waves through any path the uploaded manifest doesn't mention, so
until now every `ads.*`, `daily.*`, `paywall.*` and `features.*` key was one
mistyped admin write away from being off for everyone.

## 2026-09-07 — `Cache.update` was a read-then-write, and `AppData` has several writers

**The bug:** `Cache.update`'s interface default is `set(transform(get()))`. Two callers that
overlap each transform a snapshot the other has already replaced, so the second write silently
reverts the first. `AppData` is one record shared by every toggle, counter and boot-time field, and
on a cold start the install-id minter, the review coordinator and the navigation tracker are all
writing it while the first screen is already interactive.

**How it looked from the outside:** flip a setting on a fresh install, send a piece of feedback,
relaunch — the toggle is off again, `feedbacksGiven` is 0, and `screenVisits` is empty. Three
unrelated features losing a write at once, which reads like the file never got saved rather than
like a race.

**Why nothing caught it.** Every unit test for a toggle uses a single-writer in-memory fake, where
the interleaving cannot occur; the tests are correct and will stay green through the bug. And
Sodogku had never navigated to a `TrackableRoute` — every reachable route was a plain `Route` — so
the navigation tracker's `incrementVisit` write had never fired against a live screen. Adding the
first tracked route is what made a latent race frequent enough to see.

**Fix:** both implementations override `update` atomically (`DataStore.updateData` already
serialises the read and the write; `InMemoryCache` uses `MutableStateFlow.update`), and the
interface default carries the reason so the next implementation does not quietly inherit it. The
alternative — auditing call sites to avoid concurrent writes — was rejected because the writers are
in different modules and none of them can know about the others.

**This is a template bug**, not a Sodogku one. It belongs in `docs/PORT-CANDIDATES.md` upstream.

## 2026-09-07 — Settings is a screen, and the feedback page moved to it — but its route did not

**Decision:** `:features:settings` owns the settings screen and the feedback page. `FeedbackRoute`
stays declared in `:features:home`, and `SettingsFeatureEntryPoint` registers a route from another
feature's api module.

**Why the split is temporary and deliberate:** `:features:game:impl` navigates to
`com.sodogku.features.home.FeedbackRoute`, and game code was locked for editing while C11 landed.
Moving the class would have broken a module I could not fix, so the screen moved and the route
did not. Registering it from settings is still better than the alternatives — leaving the screen in
`:features:home:impl` splits the C11 surface across two features, and declaring a *second* feedback
route ships two feedback pages. The fix is one import in `GameFeatureEntryPoint.kt` whenever game
code is next open.

**Why a screen and not a bottom sheet**, against the general guidance in `AGENTS.md`: settings is
its own context rather than a transient picker, it holds the legal links a store reviewer has to be
able to find, and "it's in settings" has to lead somewhere with a back button.

**Feedback goes to Sentry, and that is the whole backend.** `FeedbackRepository` moved to
`:libraries:sodogku` (both the settings feedback page and home's bug reporter need it, and one
`impl` may not depend on another's) and forwards to `Telemetry.captureUserFeedback`, which mints a
carrier event and attaches the note, the build, the commit and the buffered session log. There is no
Sodogku-owned inbox and, under the no-accounts rule, there will not be one. **A send is never
reported as failed** — the note is already logged locally, and telling someone their thank-you note
bounced only invites them to retype it into the same void.

## 2026-09-07 — The streak is folded from `daily_result`, never counted

**Decision:** there is no stored streak. `DailyRepository` walks the stored results backwards from
the player's current local date every time it is asked, and `AppData` gained none of the three
fields section 13.4 had penciled in for this (`dailyStreak`, `lastDailyDate`,
`freezesUsedThisMonth`). All three are derivable, and a derived number cannot drift.

**Why it matters more here than it looks:** a counter has no witness. If a bug, a crash between
two writes, or a clock jump leaves it one too low, nothing on the device can tell — the player
reports a number we can neither verify nor rebuild, and progress is device-local so there is no
server copy to fall back on. Folding makes every past bug retroactively fixable: ship the fix and
the number is right on the next read.

**The cost is one full table read per status.** A few hundred rows after several years, on a query
with no joins. A partial "recent tail" query would be a cache, and the cache is the thing this
avoids.

**Rules the fold encodes**, each pinned by a test that names an exact number rather than "not
broken" (a fold that always returned zero satisfies "a missed day breaks the streak"):

- Today does not have to be done yet. A run through yesterday counts all day today, including
  after today has been played and lost — the day is spent, but the run breaks at midnight, not on
  the loss.
- A frozen day **bridges without counting**. The streak is days you played; an ad is not one.
- A failed day is not a missed day, so no freeze may cover it. Losing has to mean something or
  the bones on the daily are decoration.
- Results dated after today are invisible to the walk. That is what a clock set forward and back
  leaves behind, and they simply wait for the date to reach them.

## 2026-09-07 — What the daily defends against a moved clock: corruption, not cheating

**Decision:** nothing in the daily tries to detect or punish clock manipulation. Selection is the
local date, results are keyed on the local date, the streak is folded from them, and a player who
moves their clock gets a self-consistent answer for whatever date they claim it is.

**Why not defend:** the daily is device-local with no leaderboard, so a time traveller only cheats
themselves — the spec said as much in section 2 before any of this was built. Every defence that
was considered costs a legitimate player something real:

- *Refuse to write a date older than the newest row* — breaks flying west, which genuinely returns
  the player to yesterday's date, and breaks an attempt that starts at 23:58 and finishes at 00:01.
- *Refuse a date in the future* — the future is only knowable relative to a clock we already do not
  trust, and the check fires on exactly the reading it cannot verify.
- *Keep a monotonic "highest date seen"* — a stored counter again, and this one bricks the daily
  for anyone whose device shipped with a bad clock that later corrected itself.

**What is defended, and is tested:** that nothing a clock does can leave a *permanently* wrong
state. Rows are only ever inserted, never updated or deleted, so a clock moved forwards and back
hides rows and then reveals them again. The streak follows the date and comes back whole. A day
that already has a result stays locked whichever direction the clock moved, so no clock setting
buys a second attempt at a board.

`DailyRepository.onCompleted` takes the **date** rather than reading "now", for the 23:58 case: the
result belongs to the board that was played, and the new day is left genuinely unplayed. Being
generous there is cheaper than explaining why a puzzle finished at one minute past midnight
counted for neither day.

## 2026-09-07 — Timezone changes are a date question, not a special case

**Decision:** `dayOf(now, zone)` and `untilNextDay(now, zone)` are free functions taking both
arguments, and `DeviceTimeZone` is a seam re-read on every call rather than a `TimeZone` resolved
once in the DI graph.

**Why the seam:** the zone changes while the app is running. A value captured at graph construction
serves a player who has just landed their old midnight — right through their first day in the new
place, which is exactly the day they are most likely to be off routine and lose a streak.

**What falls out of it**, and is what makes the behaviour testable at all: flying east can skip a
calendar date, so the streak breaks — and the freeze offer covers precisely that day, which is what
a freeze is for. Flying west repeats a date; the earlier day's board is already solved so it does
not reopen, and the streak reads one lower until the date catches up. Both are one test each with a
fake zone, no device and no clock skew.

`untilNextDay` goes through `atStartOfDayIn` rather than adding 24 hours, because a DST
spring-forward day is 23 hours long and in a few zones midnight does not exist at all on the
transition day. It is floored at a second so a strange clock cannot turn the rollover flow into a
busy loop.

## 2026-09-07 — `daily_result` is insert-only, and the primary key is the lock

**Decision:** the one-attempt-per-day rule is `@Insert(onConflict = IGNORE)` against a table whose
primary key is the local date. There is no update path on the table at all.

**Why the database and not a check:** "read the row, write if absent" is two statements with a race
between them, and the caller who forgets the read gets a silent overwrite of a better score. The
conflict strategy makes the first result the only one the database will accept, so the rule holds
even for a caller that does not know about it.

`outcome` is a stored enum name (`Completed` / `Failed` / `Frozen`) rather than the `completed` and
`froze` booleans section 13.2 sketched. Two booleans describe four states and one of them —
completed *and* frozen — is meaningless. Stored by name, not ordinal, for the reason
`level_progress.state` is.

**Related, and unresolved:** `AppDatabase` still builds with `fallbackToDestructiveMigration`. It
was harmless when the only table was an example; it now means a schema bump silently deletes every
campaign record and every streak, on a device that has no server copy of either. Fixing it needs
real migrations and belongs to the chunk before the first store release, not to C6.

## 2026-09-07 — Both daily flags are read, so neither becomes a switch nothing listens to

**Decision:** `DailyStatus.enabled` is `daily.enabled && features.dailyChallenge`.

The config has carried both since C7's key set was written — a kill switch for a broken or
offensive board, and a feature flag for rollout. Reading only one would have left the other in the
admin console looking operable, which is the failure that had already happened once with
`app.minSupportedVersion` (see the entry below). Two flags that both mean "off" cost nothing to AND
together, and a flag nobody reads costs an emergency.

## 2026-09-07 — Achievements store the facts, not the counters

**Decision:** `achievement_fact` holds one append-only row per finished attempt, and every counter
an achievement is judged on is folded back out of that log on demand. Nothing materializes a
counter. `achievement_unlock` stores only what has already been *announced*.

**Why:** two properties fall out that a counters table cannot have. An achievement added in a later
release back-fills from a player's history — the next attempt they finish grants it, dated to the
attempt that really earned it, instead of starting them at zero. And there is exactly one
representation of progress, so no cached number can disagree with the history it came from. Cards
went the same way for a different reason (its server had to re-derive counters a reinstalled client
could not be trusted to report); here there is no server, and the argument that survives is
back-fill plus no drift.

**Cost accepted:** every read re-folds the whole log, O(n) in attempts. A completionist's log is
500 campaign rows plus one a day, and the fold is arithmetic over a list — if it ever matters,
materializing counters behind `AchievementsRepository` is invisible to callers.

**The idempotency comes from the store, not the fold.** The engine cannot double-*grant* (an id
already unlocked is filtered before anything is announced) but it would happily double-*count* the
same result twice. That is why a fact carries a key and the table has a unique index on it: record
the same attempt ten times and the counters move once. Recording is written insert-first,
fold-after, so the fold only ever sees what the index let through.

## 2026-09-07 — A share cannot leak the answer because it is never handed one

**Decision:** `:libraries:sharing` takes a size, a region layout and four numbers. There is no
parameter for the solution and no dependency on `:libraries:puzzle`, so the formatter cannot print
the placements the way a screenshot would.

**Why the signature rather than a rule:** "don't include the dogs" is the sort of thing that holds
until someone adds one field to a win-sheet payload. Making it unrepresentable costs nothing — the
grid is the region partition every player sees before their first move, which is exactly what makes
a daily share worth posting.

**The test that can actually fail** is not "two solutions share identically" (that one is true by
construction and stands as a regression guard). It is `everyCellOfARegionRendersTheSameSquare`,
which fails the moment any cell is rendered differently from its neighbours in the same region —
the shape a "show where the dogs went" implementation would take.

**Discovered constraint:** Unicode has exactly nine coloured-or-neutral square emoji
(🟥🟧🟨🟩🟦🟪🟫⬜⬛) and the 10x10 band needs ten regions. Region 10 is `🔲`, the closest pair in the
set. Repeating a square instead would merge two regions into one shape on the biggest boards, which
is worse than a slightly awkward tenth glyph.

**The words are the caller's.** Title, streak line and footer are passed in already localised. The
module holds no English, does no date formatting, and cannot be the reason a share is in the wrong
language. Score grouping is a plain comma — common Kotlin has no locale-aware number formatter, and
threading a separator through the API for a string nobody parses buys less than it costs.

## 2026-09-07 — The achievement catalog carries ids, not copy

**Decision:** `Achievement` is an id, a stat, a target and a hidden flag. No name, no description,
not even a string resource key.

**Alternative considered:** `nameKey: String = "achievement_top_dog_name"`, which is what the Cards
registry does in spirit. Rejected because a typo or a forgotten string resolves to null at runtime,
in the release build, as a badge captioned with its own key. With ids only, the UI maps them in an
exhaustive `when`, so adding an achievement fails to compile until someone writes the words.

**Related:** every criterion is `counter >= target`, replacing the sealed `Criterion` hierarchy the
Cards catalog uses. Anything that cannot be phrased that way becomes a new `Stat` in the fold. One
place reasons about what counts, and progress-toward-unlock is a division instead of a per-criterion
special case.

## 2026-09-07 — Three consumables, one shape

**Decision:** Bones, Sniffs and Treats all start at 3, all refill to 3 for a rewarded ad, and all
may be *held* above 3 from level rewards. Sniff is the hint (rules squares out); Treat is a free
correct placement.

**Why one shape:** three different economies would be three things to learn before the puzzle. The
refill is capped, the holding is not — so clearing levels grows a stash, and the store of them
reads as a reward for playing rather than a meter that only ever empties. A refill never *reduces*
a holding, which is the bug a naive `count = 3` would ship.

**First tap of a booster always explains it**, whatever the count, and every tap explains once the
count is zero. Spending a consumable is irreversible, so an unfamiliar button says what it costs
before it costs anything. After that a tap just works.

**A booster is never spent for nothing.** Found on device: on an easy board the deduction engine
solves by placement alone and announces no eliminations, so the first `ruledOutCells` returned an
empty list and a sniff was consumed with no visible effect. It now reports every square that
*became* ruled out however the engine got there, and the ViewModel refuses to spend when there is
nothing to show.

## 2026-09-07 — `:libraries:progress` puts its Room entity in the api module

**Decision:** the `@Entity` and `@Dao` for `level_progress` live in `:libraries:progress` (api),
not in its `impl`.

**Why:** the shared `AppDatabase` lives in `:libraries:storage:impl`, and the module-boundary rule
forbids one impl depending on another. The entity has to be visible to the database, so it goes in
the api module — the same reason the template's own `ExampleUserDataEntity` sits in
`:libraries:sodogku:storage`. The alternative was giving progress a second database.

**Also worth knowing:** `moduleConfig.storage()` alone does *not* give a module Room on iOS. It
wires the plugin and KSP and adds `:libraries:storage` to the project-level `implementation`
configuration, which the Android target picks up and the Kotlin/Native target does not. Android
compiles clean and `compileKotlinIosSimulatorArm64` fails with unresolved `androidx.room`. The fix
is an explicit `implementation(projects.libraries.storage)` in `commonMain.dependencies`.

**Level state is ranked, not ordered by the enum.** `Locked < Unlocked < Skipped < Completed`, and
state only ever moves up. That one rule gives "never regresses", "a replay is not a downgrade",
"skipping a cleared level is a no-op" and "clearing a skipped level promotes it". The ranking is
deliberately *not* the enum declaration order, so nothing outside the impl should read an ordering
off the enum. `state` is persisted by name, not ordinal, so reordering the enum cannot silently
reinterpret a saved campaign.

## 2026-09-07 — Dog animation ships as a sprite sheet, reversing the earlier call

**Decision:** `dog_look` is packed into a 30-frame sprite sheet at board resolution
(`scripts/build_dog_sprites.py`) and stepped in Compose. A placed dog looks around and blinks.

**This reverses the C3a decision** that the clips should stay archived. That call rested on two
claims, and one of them was wrong:

- *"Animated WebP does not play on Compose Multiplatform iOS."* Still true, and still the reason
  the source clips are not shipped directly.
- *"A clip cannot go on the board — ten of them is an out-of-memory crash."* Wrong, because it
  assumed one decode per cell. At most `N` dogs are ever placed, they all render the same
  animation, and a sheet is decoded **once** and shared. Packed at 128px and subsampled to 30
  frames it is 260KB, not 60MB.

The earlier note also recorded that Pillow could not read the clips. That was a bad feature probe
on my part (`features.check("webp_anim")` is not a real feature name); Pillow reads all 60 frames
fine.

**Cost:** the sheet's frame count and grid are duplicated between the script's arguments and
`AnimatedDog.kt`'s constants. A mismatch shows up as a visibly wrong animation rather than a build
failure, which is why both are documented in SPEC section 16a.

## 2026-09-07 — Haptics go through one object, not through call sites

**Decision:** `Haptics` in `:libraries:ui` maps game events (`Mark`, `Place`, `Strike`, `Win`) to
Compose feedback types, and is constructed with the player's on/off setting already applied.

**Why:** the setting has to be honoured everywhere without every feature remembering to check it,
and Compose Multiplatform exposes only two haptic types today (`TextHandleMove`, `LongPress`). The
mapping from a rich set of game moments onto that thin vocabulary is a decision worth making once,
in one place, rather than at each call site.

## 2026-09-07 — `state` lags `updateState`, so never read it back inside one action

**The landmine:** `SEAViewModel.state` reads `stateFlow.value`, and `stateFlow` is a *derived*
`stateIn` of the mutable flow. It propagates on a coroutine dispatch, so reading `state`
immediately after `updateState { }` in the same action returns the value from **before** the
update.

**Found by:** the starter dog silently not appearing in tests while working on device. Production
happened to interleave a dispatch between the two; the test scheduler did not. Same code, two
answers, neither of them reliable.

**It was not just the one site.** `place()` updated the score and then called `win()`, which
re-read `state.score` — so a level's final score could drop the points for the very placement that
won it. It looked right on device for the same accidental reason.

**Rule:** inside one action, read `state` once at the top, then either fold everything into a
single `updateState`, or pass computed values on as parameters. The lambda argument of
`updateState` *is* fresh (it reads the mutable flow), so composing writes is fine — only reading
`state` back is not.

## 2026-09-07 — Tap marks, double tap commits, and the second tap is recognised in the ViewModel

**Decision:** a single tap writes or erases the player's cross and can never cost a life. A second
tap on the same cell within 320ms commits a guess. The double-tap is detected in `GameViewModel`,
not by `detectTapGestures(onDoubleTap = ...)`.

**Why not the gesture detector:** registering `onDoubleTap` makes Compose withhold `onTap` until
the double-tap timeout expires. That puts ~300ms of lag on marking, which is the gesture a player
performs dozens of times per board. Recognising the second tap upstream lets the cross draw
instantly and convert if another tap follows.

**Alternative considered:** LinkedIn Queens cycles empty → X → queen → empty on single taps, which
has no timing window at all. Rejected because it makes "erase this cross" a three-tap operation,
and erasing is common.

**Cost accepted:** a very fast deliberate double tap on an empty cell briefly shows a cross before
the dog lands. It reads as the mark being upgraded rather than as a glitch.

**Related:** a wrong guess leaves the cell marked rather than clearing it. The player has just
proved no dog goes there, and discarding that would make a strike cost information as well as a
bone.

## 2026-09-07 — Region ink is derived from contrast, not chosen

**Decision:** `RegionStyle.ink` (the colour marks and glyphs are drawn in) is computed per fill by
comparing the WCAG contrast ratio of a dark ink and a light ink against it, rather than being
declared alongside the fill.

**Why:** hand-assigning it got three of ten wrong. Coral, green and plum all read as "dark
colours" by eye but are light enough that white ink nearly vanishes on them. The mistake was
invisible in code review and instantly obvious once the palette was rendered as a strip on a
device. A derivation removes the error class permanently, and it self-corrects if a fill is ever
retuned.

**Related:** the same render caught `BoardCell` drawing the player's X mark with
`RegionGlyph.Plus`, which is region 6's own identity glyph — under a comment asserting it was
deliberately not one of them. The mark now has a dedicated cross, and a cross is excluded from
`RegionGlyph` so the shape can only carry one meaning.

## 2026-09-07 — Poppins, not a new font file

**Decision:** the sans family points at Poppins, which the template already bundles, rather than
adding Baloo 2 or Fredoka.

**Why:** Poppins is geometric and near-circular, which gets most of the way to the rounded display
face the art direction wants, at the cost of zero new assets and no licensing step. If it needs to
be rounder, both alternatives are OFL and are a drop-in replacement in `FontFamily.kt` with nothing
else in the type scale changing.

## 2026-09-07 — Dog art ships downscaled behind a component, and the clips stay archived

**Decision:** the seven 1024px stills ship downscaled by use case (192px board poses, 512px hero
poses) behind a single `Dog(pose = DogPose.X)` component in `:libraries:ui`. The six animated WebP
loops are archived in `art/source/` and ship nowhere. No Coil dependency.

**Why the downscale:** a cell on a 10x10 grid is about 108 physical pixels. Shipping the originals
would be 8.3MB of assets and roughly 28MB of decoded bitmaps for one puzzle; downscaled it is
620KB. The `DogPose` enum makes the board-versus-hero split a compile-time choice rather than a
call-site judgement, so nobody can paint a 512px asset into a grid cell.

**Why the clips wait:** two independent blockers. One 60-frame 512px loop is about 60MB fully
decoded, so board-cell animation was never possible and has to be Compose-driven motion on a
static asset regardless. And animated WebP does not play on Compose Multiplatform iOS at all —
Coil 3's animated decoding routes through Android's `ImageDecoder`, and Skia gives you frame one.
Adding Coil now would ship an animation that works on Android and silently freezes on iOS.

**The portable fix, when we want it:** decode each clip to a build-time sprite sheet and step
frames in Compose. Identical on both platforms, one bitmap, and the frame rate becomes ours.
That is C12 work.

**No third-party image library at all right now.** Static PNGs go through Compose Resources'
`painterResource`.

## 2026-09-07 — Level packs ship as generated Kotlin, not as an asset

**Decision:** `:tools:level-generator` writes `CampaignPackData.kt` and
`DailyPackData.kt` into `:libraries:levels` as a `List<String>`, one
pipe-delimited line per level. The spec originally called for a JSON/binary
asset.

**Why:** the pack verification test is the only thing standing between an
unsolvable level and the store, and it has to run everywhere. Compose Resources
loading is suspend, platform-mediated, and awkward in Android unit tests;
generated source loads identically in a JVM test, on Android and on iOS with
nothing in the way. A malformed pack then fails compilation rather than the app.

**Cost:** 97KB of generated source in the repo, and regenerating produces a
large diff. Accepted — it is append-only data nobody reads by hand, and the
generator is deterministic per seed so an unchanged seed produces an unchanged
file.

## 2026-09-07 — Par scores and paw thresholds are derived, not baked into the pack

**Decision:** `LevelDefinition` carries only `id`, `board`, `solution` and
`difficulty`. The spec's `parScore` and `pawThresholds` fields are dropped;
those get derived at runtime from size and difficulty in `:libraries:scoring`
using coefficients from remote config.

**Why:** baked thresholds are un-tunable. What counts as a three-paw clear is
exactly the kind of number the config split (SPEC section 4) says belongs on the
server, and freezing it into the pack would mean a regenerated pack and an app
release to retune it. It also decouples C2 from C3 entirely.

## 2026-09-07 — Uniqueness comes from targeted refinement, not random mutation

**Decision:** `BoardFactory.refineToUnique` drives a board to a single solution
by repeatedly pulling a rival solution and applying the region move that kills
*that specific placement*, choosing between killers by which leaves the fewest
solutions.

**Alternatives measured, not assumed:**

- *Random mutation.* 0 unique 10x10 boards in 60 attempts, unchanged from 12
  rounds to 120. Random moves rarely invalidate any particular rival.
- *Balanced region growth* (extend the smallest region rather than a random
  frontier cell), on the theory that a 22-cell region constrains nothing.
  Measurably **worse**: 2/40 versus 4/40 at 10x10. Uneven regions constrain
  more, because a small region pins its dog tightly.

Targeted refinement converts 23/40 at 10x10, and generating all 1230 shipped
levels takes 42 seconds.

**The subtle part:** progress is deliberately not gated on the solution count
decreasing. The count is capped for speed, so a wide-open board reads the same
before and after a genuinely useful move; demanding a strict decrease stalls a
10x10 on move one. Killing the rival is the real invariant, and the seed
solution survives by construction because region moves never touch a dog cell.

## 2026-09-07 — No accounts: the identity stack is removed, not disabled

**Decision:** `:libraries:identity` (+ impl), the Supabase auth screens in
`:features:onboarding:impl`, the session-expired recovery route, the
user-scoped sync/reset machinery, and the server's `/v1/me` + player-report
surface are all deleted rather than left dormant. `AuthGate` keeps its seam in
`:libraries:core` with a new `AlwaysReadyAuthGate` default binding in
`:libraries:networking`, next to `NoOpAuthTokenProvider` and for the same
boundary reason.

**Alternatives:** leave identity in place and simply never call it. **Why
delete:** dormant auth still runs at boot — `GuestAccountCreator` and
`GuestSessionHealer` fire on the launch path and would make doomed Supabase
calls on every cold start, muddying logs and telemetry for the whole project.
And every screen built on top would have to decide whether to consult a session
that can never exist. Sodogku's only backend surface is public remote config.

**Cost accepted:** progress is device-local and cannot survive a reinstall. The
Pro entitlement still travels via store restore. If progress ever needs to move
devices, the cheap path is an export/import code (see `SPEC.md` §18), not
resurrecting accounts.

## 2026-09-07 — Rollout bucketing keys on install id, not user id

**Decision:** `AppConfigSource.read` drops its `UserId?` parameter and the
targeting engine buckets rollouts (and evaluates allow/deny lists) on
`ClientContext.installId`.

**Why:** the engine already fell back to `installId` when no user was resolved,
so this deletes a branch rather than adding one. Staged rollouts and A/B tests
on ad frequency — the main reason the config server exists — work fine keyed on
a stable per-install id, and there is no user id to key on.

**Cost:** a reinstall re-buckets that device. Acceptable for tuning ad cadence;
it would not be acceptable for a billing experiment.

## 2026-09-07 — User-facing copy goes through `:libraries:resources` from day one

**Decision:** the `VerifyStrings` detekt rule is honored rather than baselined.
`OnboardingScreen` is the worked example: strings live in
`libraries/resources/src/commonMain/composeResources/values/strings.xml` and
resolve through `stringResource(Res.string.…)`.

**Why:** the template ships the rule active but baselines every screen that
predates it, so nothing actually followed it. Sodogku will have a lot of copy
(rule chips, praise text, boosters, paywall, legal), and retrofitting string
extraction across twenty screens costs far more than writing the first one
correctly. The baseline stays for the template's leftover screens, which get
converted as each is replaced by real game UI.

## 2026-06-21 — Server mirrors client conventions

**Decision:** `:apps:server` reuses the client's stack — kotlin-inject + anvil DI
(`ServerScope`/`ServerComponent`), the `domain/` interface + `data/` impl split,
conventional commits, the version catalog. It's a plain JVM `application` module
(no convention plugin; those are KMP-only).

**Why:** one mental model across client and server. An agent (or human) moving
between them doesn't re-learn DI, error handling, or module layout. The cost —
the server can't use the KMP `:libraries:core` (`Catching`, logging) because that
module has no JVM target — was accepted; the server keeps a couple of small local
equivalents rather than forcing a `jvm()` target onto every client library.

## 2026-06-21 — Graceful degradation over required config

**Decision:** `DATABASE_URL`, `SUPABASE_URL`, `SENTRY_DSN`, and the OTLP endpoint
are all optional. With none set, the server boots and serves `/_health` +
`/v1/example`; DB-backed and authenticated routes simply aren't mounted, Sentry
no-ops, and OpenTelemetry exports to stdout.

**Alternatives:** require `DATABASE_URL` + `SUPABASE_URL` like the Cards origin
(fail-fast). **Why optional:** this is a template — "clone and run, see it boot"
beats a fail-fast error on first run. The fail-fast discipline still applies per
field via `Env.require` when a future field genuinely can't be defaulted.

## 2026-06-21 — Auth is JWKS verification, never a shared secret

**Decision:** the server verifies Supabase JWTs against the project's public keys
(JWKS / ES256). The `JwtVerification` sealed seam has `Jwks` (prod) and `Static`
(tests mint HS256 tokens against a known verifier).

**Why:** no Supabase secret ever lives on the server, and auth — the highest-risk
surface — is fully testable offline (route tests + `FullStackMeTest` run the real
validate/challenge path with no network).

## 2026-06-21 — `NoOpAuthTokenProvider` lives in the `:networking` api module

**Decision:** the default no-op `AuthTokenProvider` binding sits in
`:libraries:networking` (api), not `:impl`.

**Why:** the module-boundary rule forbids one `:impl` depending on another, but
`:libraries:identity:impl` must reference `NoOpAuthTokenProvider` to override it
with `@ContributesBinding(replaces = [NoOpAuthTokenProvider::class])`. Putting the
default binding next to the interface it defaults keeps the replacement
boundary-clean. (See also the `enforceModuleBoundaries` self-edge fix in
`build-logic`.)

## 2026-06-21 — `serverOnly` build slimming

**Decision:** `-Dsodogku.serverOnly=true` makes `settings.gradle.kts` include
only `:apps:server`, so a Docker image build needs no Android/iOS toolchain.

**Why:** this is a KMP monorepo; without slimming, a server image build would
configure every client module and need the Android SDK + Kotlin/Native. The
server has no client-library deps today, so the gate is a pure settings change;
if it gains one, add an always-included `include(...)` + a Dockerfile `COPY`.

## 2026-06-21 — Flyway SQL is the schema source of truth

**Decision:** migrations under `resources/db/migration` define the schema; the
Exposed `Tables.kt` objects are read-side projections kept honest by
`DatabaseSchemaTest`. Repositories treat a unique-violation (SQLSTATE `23505`) as
the arbiter rather than pre-checking for races.

**Why:** one procedure for schema change (add the next `V##__name.sql`, never edit
an applied one), and idempotency that's correct under concurrency.

## 2026-09-07 — the adjacency technique was half-blind, and the packs were re-rated

`adjacencyConfinement` swept rows and columns in one loop, and the guard that
skipped a resolved *row* skipped the *column* of the same index with it. Rows
resolve constantly mid-solve, so a slab of tier-2 reasoning disappeared exactly
when a board is most constrained, and the engine reached for a tier-4
contradiction where a tier-2 fact was sitting in plain sight.

Measured over 200 random unique boards: 4% were mis-rated, always *too hard*, by
up to two tiers. Split into two sweeps, one per axis. Regenerating the campaign
moved 14 levels down out of tier 4 (122 → 108) into tiers 2 and 3. Nearly half
the pack's lines changed, because difficulty feeds candidate acceptance and band
ordering, so a re-rating cascades through the generator's RNG stream.

The reason this was invisible: the baked difficulty is a cache of
`Difficulty.score`, and nothing compared the two. `LevelPackVerificationTest` only
asked whether the stored numbers were in range and non-decreasing, which stale
numbers satisfy perfectly. `everyBakedDifficultyMatchesWhatTheEngineNowSays` now
re-derives every one, so any future retiering fails the build with the command to
fix it instead of shipping a pack that describes an engine we no longer have.

`DeductionEngineTest.shallowerTechniquesAreAlwaysPreferred` could never have
caught it either: it compares `nextStep` against `nextStep(maxTier - 1)`, which
runs the same broken technique. An engine compared only against itself agrees
with itself.

## 2026-09-07 — a hint may not reason from a dead end

`ruledOutCells` checked `ruleViolations` and stopped there. Breaking no rule is
not the same as still being winnable: a partial can be legal and have zero
completions. Every technique in the engine is sound, which is precisely the
danger — reason soundly from a false premise and it will confidently cross out
the square the answer is on. It did, on 12% of legal-but-dead partials.

Both hint entry points now establish satisfiability first. `nextCell` already had
the solve and could not reach it, because `deducePlacement` returned before it;
the solve moved above. It costs under a millisecond at 10x10.

Not reachable in today's flow — a wrong guess marks the cell and costs a bone
rather than placing a dog, so `placed` only ever holds correct dogs. It is one
undo or restore-state feature away from mattering, and the KDoc already promised
the guarantee.

Related: `ruledOutCells` lost its `limit = Int.MAX_VALUE` default. Unbounded it
returns every non-dog square — 90 of 100 at 10x10 — so the convenient overload
was the one that hands over the answer by exclusion for the price of one sniff.

## 2026-09-07 — three tests that passed on `emptyList()`

The sniff-spent-for-nothing bug found on device had no regression test, and could
not have had one: all three tests covering `ruledOutCells` passed against a stub
returning an empty list. One asserted `none { it in truth }` (vacuous when
empty), one asserted `size <= 3` when the bug was size 0, and one asserted empty
on a finished board, which returns before the function body runs.

The lesson is narrower than "test more": an assertion of the form "nothing bad is
in the output" says nothing at all about an empty output, and it is the natural
shape to reach for when the property under test is soundness. Every such
assertion now has a companion that the output is non-empty.

`aHintAlwaysHasSomethingToSay` only asserts over the opening half of a board.
Late on, the auto-marks have already crossed off everything derivable — measured
at 5 of 7 placed with one candidate per open row — so there is genuinely nothing
to reveal, and the ViewModel declines to spend the sniff. Asserting a reveal
there would be asserting a lie.

## 2026-09-07 — the kill switch was pointed at a key nothing reads

The spec's config table named the force-update gate `app.minSupportedVersion`.
The admin console's kill-switch panel and its manifest registry were already
built against `upgrade.minSupportedVersionCode`, from the template. Following the
spec would have shipped an emergency control that edits a key no client reads,
and it would have looked like it worked right up until the emergency.

The client moved to the admin's namespace rather than the other way round: the
console has a live panel, a registry and server validation behind that path, and
the spec was a draft. `upgrade.maintenanceMode` came along with it, so the client
now declares the whole gate the console can already set.

The registry's `minSupportedVersionCode` default also went from 1 to 0, matching
the client's fail-open rule. Every gate in this namespace defaults to "block
nobody", because it is the only config that can brick every install at once.

## 2026-09-07 — treats start at three, like everything else

Section 4.3 of the spec said 1, section 1.5 and the "three consumables, one
shape" decision said 3, and the config landed on 1 because the table is what the
implementer was pointed at. Three is right — the whole reason the consumables
share a shape is that three different economies would be three things to learn
before the puzzle. The table was the stale copy; it now says 3.

Added `boosters.refillTo` while here. What an ad tops you up to was the one core
economy number that was a compile-time constant while everything around it was
tunable. It is a floor, never a cap: level rewards push a holding above it and a
refill leaves those alone.

## 2026-09-07 — the standing ad offer greys out rather than always firing

The ask was "an ad button that is always clickable, it just refills your bones".
It is always *there* — that is the part that matters, because otherwise nobody
discovers the offer until they have already run out and the app looks like it is
punishing them. But it greys out when bones are full.

Selling an ad for nothing is worse than not offering one. A player who taps it at
three bones, sits through thirty seconds and gets three bones back learns that the
button lies, and that is a more expensive lesson than the impression is worth. It
sits in the booster row rather than the header because that row is where a stuck
player is already looking.

`refillBones` also stopped assigning `MAX_LIVES` and now takes `maxOf`, matching
the rule the booster refill already followed: a holding earned above the floor is
never reduced by topping up.

## 2026-09-07 — placed dogs get one idle loop each and keep it

Four sprite sheets exist (`look`, `tilt`, `pant`, `flop`) and only `look` was
wired. Each placed dog now picks a loop from its cell index, so a board reads as
a row of animals rather than one animal drawn eight times.

It picks once and keeps it. Switching mid-attempt was the tempting version and is
the wrong one: motion in the periphery pulls the eye, and pulling it away from
the puzzle is the opposite of what idle animation is for. Four separate sheets
rather than one big one, because Compose caches each `imageResource` and a board
only ever shows a handful of dogs — only the loops in play get decoded.

Same reasoning retires the pop on `PawRating` in the level list. The pop means
"you just earned these", which is a lie when the rating is being recalled, and
in a 500-row list rows recycle, so every completed level twitched each time it
scrolled back on screen.

## 2026-09-07 — the game's numbers move to the Display scale

"Big and bubbly, the level especially." The level and the score were on
`Heading.H700`, which is the same weight as every section title in the app, so
they read as chrome — one more label on a screen already full of them.

They are now `Display.D900`, and the labels above them drop to `Caption.C300`.
The scale already existed and nothing in the game used it. Poppins was picked in
C3a precisely because it is geometric and near-circular; at display size the same
digits stop looking like a status bar. The label is there to say which number
this is, once. It does not need to compete.

No new font, and no new type scale. Adding either would have been the obvious
move and the wrong one — the "bubbly" was already available in the scale that
shipped, unused.

## 2026-09-07 — the win sheet's ad badge was a copy of the design-system one

`GameOutcomeSheets` had a private `AdBadge` that reimplemented `RewardBadge`
down to the same accent and radius. Two things meaning "an ad is involved" is one
too many: the whole point of a single accent for ad affordances is that a player
learns the colour once, and that only holds if there is one component to change
when it moves.

The lose sheet's refill button is now badged too, unconditionally — refilling
*always* costs an ad, unlike Next level, where one is only sometimes due.

## 2026-09-07 — blocked on art that never reached disk

Two assets the user described in chat are not in `sodogku-assets/`: the **sad dog**
for the out-of-bones sheet, and the **better bone art**. The lose sheet still uses
`DogPose.HardMode` and the bones are still drawn in `GameShapes.drawBone`. Both
call sites are one-liners once the files exist.

`dog-appmark.png` also still carries sudoku numerals, which Sodogku does not have.
It needs redrawing before store prep.

What *was* there and unused: `dog_idle.webp` and `dog_bark.webp`. `idle` is now a
fifth placed-dog loop. `bark` is a reaction rather than an idle, so it is being
kept for the win moment rather than added to the loop rotation.

## 2026-09-08 — and a test that proves the saves survive

The migration policy above was implemented and unproven: `AutoMigration` was
declared, Room generated the classes, and nothing ran them against a database
with a row in it. "The generated code exists" is not the claim that matters.

`AppDatabaseMigrationTest` builds a genuine v6 database — the tables exactly as
`6.json` declares them, `user_version = 6`, a real `level_progress` row — and
runs the generated migrations over it. The DDL is written out in the test rather
than read from `6.json`, so the fixture and the migrations have independent
origins: `MigrationTestHelper` reads the same exported schema the migrations are
generated *from*, and a mistake in the export would be invisible to it.

Three mutations, each failing exactly one thing: reinstating the unrestricted
destructive fallback, a migration that drops the old table, and bumping
`@Database(version = …)` without adding a migration.

The third took two attempts and is the useful lesson. The first version compared
`FIRST_PLAYER_DATA_VERSION + migrations.size` against a `CURRENT_VERSION`
constant declared *in the test file*, so bumping the real version left it green —
a guard written against itself. It now reads the highest exported schema JSON,
which Room writes on every build, so the two sides come from different places.

One build detail worth knowing: the test needs `androidx.sqlite:sqlite-bundled-jvm`,
not the plain artifact. The Android variant ships `.so` files for device ABIs and
cannot load in a host JVM, which is why previous attempts here concluded Room's
SQL was untestable off-device. It is not; it just needs the right variant.

## 2026-09-07 — the database stopped dropping itself

`RealAppDatabaseProvider` built with `fallbackToDestructiveMigration(dropAllTables = true)`.
Two agents flagged it independently on the same afternoon, from opposite ends of
the schema, and they were right: it was harmless while the only table was the
template's example, and it became a silent unrecoverable wipe the moment
`level_progress` landed.

There is no account. A player's campaign records, daily streak and achievements
exist in exactly one place — that file on their phone. The next release that adds
a column would have deleted all of it, on launch, with no error and nothing to
restore from.

Now: `autoMigrations` for 6→7 and 7→8, and destructive fallback narrowed to
versions 1–5, which are template history no install has ever run. Every addition
so far is a new table, so Room writes the migrations itself. The value of the
list is what happens when it *can't* — a renamed or retyped column now fails the
build rather than the player's save.

## 2026-09-07 — the lagging `state` landmine, third sighting

Wiring the achievement log found it again: `lose()` computed
`MAX_LIVES - state.livesRemaining` after the caller's `updateState` had set the
third strike, and got 2. `state` reads a derived `stateIn` flow that lags
`updateState` by a dispatch, so the read saw the board one strike ago and the
achievement log believed it.

This is the same shape as the starter-dog bug and the win-scoring bug. The rule
that keeps coming out of it: **a value that a suspend function computes and then
needs again must be passed as a parameter, never read back off `state`.** Reading
it back is always available, always compiles, and is wrong roughly half the time
depending on dispatch timing — which is why it keeps getting written.

Both `recordAttempt` call sites now take `livesRemaining` explicitly, and the
loss path passes the strike-adjusted score card rather than re-reading that too.

## 2026-09-07 — losses go in the achievement log

The log records failed attempts as well as clears. A run that ended on the last
bone is evidence about how someone plays, and some badges are about persistence
rather than success. Recording only wins would make the log a record of wins,
which is a different and much less useful thing — and it would make any future
"attempts per clear" question unanswerable from data we chose not to keep.

Consumable spends are counted per *attempt*, not per session, and reset on retry.
"Cleared it without help" is a claim about one attempt; a counter that carried
across retries would make it unearnable for anyone who ever used a hint.

## 2026-09-07 — an unparseable boolean was silently `false`

`getValueRecursive` resolved booleans with `rawValue.toString().toBoolean()`,
and `"banana".toBoolean()` is `false`. So a string typed into `ads.enabled`,
`daily.enabled` or any `features.*` flag in the admin console would have turned
that feature off on every device that fetched it — no log, no fallback, no crash.

This is the one path that could make a monetization key fail *closed*, which is
the exact guarantee SPEC 4.2 is built around, and the client-side fail-open test
could not see it: that test reads against an *empty* map, where every key already
falls back correctly. The hole was in the resolve of a value that was present and
malformed.

Booleans now resolve only from "true"/"false", case-insensitively, and anything
else is null so the declared default wins. Casing stays forgiving because the
console lets an operator type a raw value and "True" is not a mistake worth
punishing. Numeric keys were already safe — `toDoubleOrNull` returns null rather
than inventing a zero, which is exactly the shape the boolean case was missing.

The general rule this is an instance of: **a parse that invents an answer is
worse than no answer.** The default was chosen deliberately and is written down.
The invented value is whatever the coercion happened to produce.

## 2026-09-07 — the persistent cache's clear() deleted nothing

`DataStoreCache.clear()` called `deleteFile(name)` while the file is written as
`"$name.json"`, so it removed nothing. Correcting the name would not have been
enough either: `DataStore` holds the value in memory and serves it from there, so
even a successful delete would have left every reader on the old value until the
process died.

It now writes the serializer's default back through `updateData`, which is the
only write path `DataStore` observes. Nothing called `AppCache.clear()` yet, which
is why a doubly-broken method sat there unnoticed — the account-switch clearer it
was written for was deleted along with accounts in C0.

## 2026-09-07 — the settings screen was shipped and unreachable

`:features:settings` landed complete, tested and driven on a device, and nothing
in the app navigated to it. The board's gear opened `GameDialog.Settings`, an
in-place sheet that duplicated every row — including the strings. Two settings
surfaces would have drifted the first time either was edited, and the
achievements grid hangs off the screen nobody could reach, so C10's whole UI was
dead in a real build.

The gear now sends `GameAction.OpenSettings` out through an event to the router,
and `GameDialog.Settings` and `SettingsContent` are deleted. The lesson is
smaller than it looks: a feature module can be complete, tested, verified on a
device and still be dead code, and nothing in the build says so. Three separate
agents each assumed someone else owned the one line.

The daily agent found the mirror image of the same thing — the level pane scrolled
the campaign list to a *daily's* id and highlighted a locked stranger as "current",
because `currentLevelId` was not nullable and "not a campaign level" was not
representable. Both are the same failure to make an invalid state impossible.

## 2026-09-07 — three desktop source sets that were never compiled

`libraries/ui/src/jvmMain` and `libraries/navigation/impl/src/jvmMain` held a
`JvmWebLinkLauncher`, a desktop `FontFamily` and a `NativeButton` actual. Only
`:libraries:puzzle` declares a `jvm()` target — it needs one so the level
generator can run on the JVM — so none of those three files has ever been
compiled by anything.

Deleted. They read as live platform implementations, which is worse than absent:
someone fixing a link-opening bug on desktop would have edited a file that does
not run, and the build would have agreed with them by staying green.

Sodogku is Android and iOS. If a desktop target ever lands, this code is one
`git log` away and will need rewriting against whatever the desktop story is then.

## 2026-09-07 — placed dogs sit still, mostly

The user, on the idle loops: *"I prefer the idle and the look around. For sure
not the shake. Maybe occasionally or something."*

The rotation is now weighted rather than uniform — `idle` and `look` take five of
six slots and `pant` takes the sixth, so a full board has a couple of dogs doing
something slightly different and no board is a row of clones. `tilt` and `flop`
are out of the rotation entirely: they are the two that read as a shake, and a
head snapping about in peripheral vision pulls focus off the puzzle, which is the
opposite of what idle motion is for.

Both sheets stay on disk. They are the right loops for a celebration or an empty
state, where the motion *is* the point.

## 2026-09-07 — 37 config keys that nothing reads

A review found that all fourteen `scoring.*` keys are declared, in the fallback
map, in the admin registry and rendered in the console with a typed editor — and
that `GameViewModel` calls `Scoring.placement`, `complete` and `paws` letting the
`config` parameter default to `ScoringConfig.Default`. The entire scoring tuning
surface was decorative.

Rather than fix the instance, `ConfigValuesAreReadTest` now scans the source tree
for a mention of every declared value outside `:libraries:config`. It found **37**,
not 14: the whole consumable economy, all of progression, the legal gate, the
upgrade and maintenance gates, and three feature switches whose features shipped
without reading them.

This is the third time the same shape has cost us. `app.minSupportedVersion` was
the client's name for a key the console edited as
`upgrade.minSupportedVersionCode` — the kill switch, pointed at nothing. Every
check we had verified that a key was *declared consistently*, and a key can be
declared perfectly and be completely inert.

The method is a text search, and its limit is stated in the test: it proves a
class is named outside its own declaration, not that the value reaches a
decision. A value injected and then ignored still passes. That is a much smaller
hole than the one it closes, because forgetting to inject is the mistake people
actually make.

The 37 are baselined in `UNWIRED`, held by two tests: one fails if a new name
appears, the other fails if a listed name gains a reader and is left behind. The
list can only shrink.

## 2026-09-07 — the board survives a kill now

Reported from a device: open the level pane mid-puzzle, tap the row you are
already on, and every mark and placement is gone. Two separate faults behind one
symptom.

The small one: `goToLevel` restarted unconditionally, including for the level
already on screen. Tapping your own row is a way of closing the pane, not a
request to start over. It restarts only when the attempt is already over, where
refusing would strand the player on a dead board.

The large one: **there was no in-progress snapshot at all.** SPEC 13.3 describes
one and BUILD-PLAN recorded C5 as delivering it; nothing did. Booster spends were
written to disk the instant they happened and the board they paid for was not, so
a force-quit mid-puzzle kept the charge and lost the reasoning — the worst
possible half to save.

`BoardSnapshot` lives on `AppData` rather than in Room. It is one small blob,
there is only ever one of it, and nothing queries it; a table would have bought a
migration and no capability. It stores what the player *did* — placements, their
own crosses, the squares that cost a bone, lives, score — and not what the game
derived. Auto-marks are recomputed from the placements on restore, because they
are a function of it and a stored copy is a second source of truth that can
disagree.

Elapsed time is stored as a duration, not a start timestamp. A wall-clock start
would count the hours the app spent closed, and a player resuming the next
morning would find a level they had lost on time they never spent.

It saves after every move, not on a lifecycle callback: a crash and a force-quit
both skip `onStop`, and force-quitting mid-puzzle is the first thing a motivated
player tries, so the only save that can be relied on is one that already
happened.

Two things the tests caught that the design did not:

- The first attempt saved off `stateFlow`, which is a derived flow lagging the
  source of truth by a dispatch — so every write persisted the board as it was
  one move ago. Fifth time that lag has bitten in this file. `updateBoard`
  captures the new state inside the transform instead of reading it back.
- Opening a fresh level produced an empty snapshot on its first frame and wrote
  it unconditionally, deleting the half-finished level the player had left
  behind. A save now only ever writes over its own board's slot.

## 2026-09-07 — the boosters look like candy now

*"The color used for the sniff and treat buttons could be more colorful. you
could make the buttons more playful looking too. Maybe like a little squiggle or
something idk. to make it look shiny."*

`Modifier.glossy` in the design system, and it is three effects rather than one,
which is worth naming because "shiny" as a single effect does not work:

- **A shaded base**, drawn under the fill and peeking out below it. This is the
  whole illusion — it reads as the side of a thing with thickness. Without it
  the other two are just a gradient.
- **A vertical gradient** from a lighter tint to the colour, which is what a
  rounded surface does under a light above it.
- **A soft white sheen** across the top. The one that is easy to overdo.

Two device passes to get it right. The first drew square-cornered rectangles,
because `glossy` is a `drawBehind` and a `clip` only trims what comes *after* it
in the chain. The second read as a painted white oval rather than as light — the
giveaway was that you could see where the highlight stopped. It is now wider,
shallower and at 0.20 alpha; a sheen has no edge you can point at.

Sniff is blue and Treat is orange, and those are identities rather than theme
roles. A player learns "the blue one shows me squares" long before they read the
word, and that only holds if the colours never move. The standing ad offer is
purple, so an offer never wears a booster's clothes.

## 2026-09-07 — the board watches its settings instead of reading them once

Once the gear opened a real settings screen, `GameViewModel` reading `AppData`
at load stopped being enough: a player flips colourblind mode or reduce
animations and comes straight back to the board. Measured on a device — the
switch moved and zero pixels changed until the next launch.

It now observes `appCache.updates`, mapped to the three settings that change
what is on screen and `distinctUntilChanged` so unrelated `AppData` writes do
not re-dispatch. This ViewModel makes several of those per move.

The consumable counts are deliberately *not* observed. This ViewModel is their
writer, and echoing its own writes back in would fight the spend it just made.
One-way for state you own, observed for state someone else owns, is the rule the
two halves are split on.

## 2026-09-07 — the device driver was tapping the screen on every launch

`scripts/dev/drive.py launch` ran `monkey -p <pkg> -c LAUNCHER 1`. Monkey's
trailing count is the number of *random events* it injects after starting the
app, so every launch all session fired a stray event into the first frame. It
closed a launch-gate banner twice while an agent was verifying it, writing a
persisted dismissal into `AppData` and making the feature look broken.

Replaced with `am start -W -n <pkg>/<activity>`, which starts the app and does
nothing else.

**Measured on 2026-09-08**, because "it happened twice" is an anecdote and the
rate is the thing that decides how much to care. Two consecutive `am start`
launches leave an identical accessibility tree *and* an identical
`files/app_data.json` — the persisted file being the precise check, since the
original failure was a write rather than a visual change. The old `monkey` line,
run 16 times from a clean install, landed somewhere it should not have **once**,
that time leaving the device on the launcher's search instead of in the app.

Roughly 6% is the interesting number. It is low enough to read as flakiness and
high enough that a session with dozens of launches hits it several times, which
is exactly how it went unnoticed for a day. Tooling that fails outright gets
fixed; tooling that acts on the app occasionally makes every screenshot after it
one interaction ahead of where you think you are, and the app takes the blame.

## 2026-09-07 — splitting GameViewModel, as far as it splits cleanly

It reached 1895 lines and 22 constructor parameters, because every chunk that
needed the game added itself to it. Two extractions, both behaviour-preserving:

`GameContract.kt` takes the state, events, actions and phases. Nothing there has
behaviour; it is the vocabulary the screen and the ViewModel share, and reading
"what can this screen do" no longer means scrolling past how it does it.

`TutorialRunner.kt` takes the one responsibility in there with a state machine
of its own — a script, a position in it, and which levels have already been
guided. Those three fields sat among fifteen others that had nothing to do with
them. It deliberately owns no `GameState`: deciding *which* coach mark shows is
its business, putting it on screen is the ViewModel's, so every method returns a
frame and changes nothing visible.

That leaves about 1490 lines, which is still too big. The remaining seams — the
consumable economy, the daily — all call `updateState`, which is a member
extension on the SEAViewModel base and so cannot be reached from outside the
class. Splitting those means a delegate that takes a `(GameState) -> GameState`
rather than a plain move, and that is a change with real risk. Recorded rather
than attempted, because it was not the day for it: three agents were editing
this file.

## 2026-09-08 — filled buttons are deep by default

`BasicButton` has had a "3D lip" since C3a — a hard band behind the face that the
face drops onto when pressed — and `Button.kt` gated it behind `deep = false`. So
the mechanism existed, was documented in the KDoc with an example, and **no
screen in the app used it**. Every CTA shipped as a flat rectangle of Material
blue on a cream page.

Flipped to on. In a game a CTA should look like something you could press, and a
per-call-site opt-in means every new screen ships flat until somebody remembers.
Ghost buttons still get nothing: a lip under a borderless text button is a shadow
under a link.

Same shape as the dialog padding, found the same day. A design system that makes
the right thing *available* rather than *default* gets the wrong thing on every
screen, and each call site looks correct in isolation.

## 2026-09-08 — and the board comes back when you walk away from it too

The first fix restored a saved board on `load`, which covers a process death and
nothing else. Switching levels happens *inside* one ViewModel, so the snapshot
was written on the way out and never read on the way back — the board survived a
force-quit and not a trip through the level pane, which is much the more common
one. Every path that opens a board now looks the snapshot up.

Retry is the exception, and deliberately so: it is the one action that means
"throw this away", and handing the board back would make the button appear to do
nothing.

The first test for this **passed against the bug**, which is worth writing down
because it is the third time this shape has appeared today. `cellFor(row)` in the
test file resolves against a fixed shared level, so used on any other board its
cells are wrong and a "commit" lands as a strike. The board therefore held only
its starter dog before and after, and the assertion compared nothing to nothing.
Mutation-checking is what caught it: reverting the fix left the test green.

## 2026-09-08 — dashboards live in the repo, and a test holds their queries to the code

The six SPEC §14 dashboards are committed JSON under `ops/grafana/`, imported by hand, rather than
created in Grafana and left there.

The immediate reason is that the only Grafana stack this session could reach belongs to a
different project, and writing to it is not ours to do. But the arrangement is the right one
regardless, because of what a broken dashboard looks like. A panel that filters on `strikes_used`
against an app that emits `strikes` is not an error anywhere: Loki accepts the query, the panel
renders, and it renders **empty** — which is exactly what a healthy panel looks like before launch.
The person who finds it is whoever eventually asks the dashboard a question and believes the blank
answer. A dashboard that lives only in Grafana has no way to be wrong in a build.

So `DashboardQueryContractTest` parses every committed query and every `logEvent(...)` in the
source tree and fails when a dashboard names an event or attribute nothing emits. Renaming either
end goes red; mutation-checked both ways.

**Why a text scan.** The emitters are in `:features:game:impl`, `:libraries:ads:impl` and
`:libraries:billing:impl`, and only `:apps:*` may depend on an impl module — so no unit test can
construct a `GameViewModel` and watch what it emits. Both ends are read as text instead. That
proves the key is spelled the same at both ends and nothing more: a `logEvent` in dead code counts
as emitted. It is the same trade `ConfigValuesAreReadTest` already makes, for the same reason, and
it closes the mistake people actually make.

**The LogQL reader throws instead of shrugging.** It understands the stream selector, label filters
and `unwrap`, and rejects everything else. A looser regex reader would have extracted nothing from
a `| json` panel and reported no violations, which is how a check like this passes while proving
nothing — the failure mode that had three tests in this repo green against a stub returning an
empty list. A construct we want has to be taught to the reader first. That is a real cost and it
is the point.

**What this does not do.** Nothing provisions these dashboards, and nothing syncs UI edits back —
an edit made in Grafana is lost on the next import. Terraform or Grafana's git-sync would fix that
and both are a larger commitment than six files deserve before anyone has looked at one with real
data on it.

## 2026-09-08 — the store privacy forms are answered conservatively where the wording is ambiguous

`docs/store/data-safety.md` has two rows whose answer depends on how Google and Apple define
"linked to the user" rather than on anything our code does. Both are filed the safe way.

Google's definition treats data as linked if it is collected alongside a persistent identifier.
Apple's "Not Linked to You" requires de-identification and a commitment not to relink to a user
*or a device*. Everything we send rides with `install_id`, which is exactly a persistent
device-scoped identifier. So both are filed as **linked**, even though there is no account and
nothing on the other end can turn an install id into a person.

Same reasoning on **Purchases → Purchase history**. We never see a payment instrument and Play's
own purchase records are out of scope, but `iap.purchase_result` records against an `install_id`
that a purchase succeeded or failed. Declaring it costs nothing. Not declaring it is a position
you would have to defend to a reviewer, and the reviewer is holding the "remove app" button.

The rule this follows: over-declaring is never a violation, under-declaring is. Both answers are
marked with a confidence level and a pointer to the store's own help page in the document, so the
next person can tighten them deliberately rather than by accident.

## 2026-09-08 — store screenshots are cropped to 2:1 rather than shot at 9:16

The emulator is 1080x2424, which is 2.24:1. Play rejects a screenshot whose long side is more than
twice its short side, so the raw frames are not submittable.

Three ways out. Set `wm size 1080x1920` and shoot a true 9:16 pass; scale the frame down and pad
the sides with cream; or crop. Cropping won.

`wm size` was the technically cleanest and was rejected on process grounds: another agent was
driving the same emulator, and resizing their device mid-session to take pictures is the kind of
change that makes someone else's screenshot look like a layout bug. Padding was rejected because
cream side-bars on a cream app read as a rendering mistake rather than a frame.

So: `sips -c 2160 1080 --cropOffset 150 0`, giving exactly 2:1. 150 rows off the top removes the
status bar and stops just above the header buttons; the remaining 114 off the bottom removes the
gesture bar and stops short of the booster pills. Both margins are about 30px, so **a layout change
moves them** and the recipe in `docs/store/listing.md` says which edges to re-check.

The same pass captured the frames from the emulator's existing state (level 391, a 10x10 board)
rather than from `launch --fresh`. Fresh would have given an onboarding and tutorial frame, and
would have wiped the other agent's session to get it. The listing notes the missing frame instead.

## 2026-09-08 — two meanings under one key, decided by line ordering

`ads.gate_shown` emitted its own `is_offline`, and `GrafanaLogTree` stamps an
`is_offline` on every record. They are not the same thing: the record's is
`AppState.isOffline`, which is true when our *backend* is unreachable as well as
when the device is, and the gate's is the OS signal alone, which is the only one
the offline grace should read.

The event's value won, and only because `forward` applies the per-record stamp
before the extras loop. Nothing in either file said so. A dashboard reading
`is_offline` on that event would have been right by accident and wrong the moment
someone reordered two lines.

The gate's attribute is now `device_offline`. Naming was the whole fix — a
comment explaining which `is_offline` you were looking at would have needed
reading at query time, which is exactly when nobody reads comments.

Three attributes the dashboards needed and nothing emitted are now emitted:
`difficulty` on `game.level_failed` (without it only *clears* report a tier, so a
board hard enough to lose on is under-represented in every calibration panel —
the direction a mis-rating hides in), `difficulty` on `game.booster_no_op`, and
`trigger` on `iap.purchase_result`, which is the question section 14 actually
asks of the paywall board. `Entitlements.purchasePro` takes the trigger as an
optional parameter so the test doubles and the QA path did not all have to grow
an argument they do not care about.

## 2026-09-08 — three things a store submission would have caught

Tracing the app for the Play Data Safety form turned up three problems that were
only visible from the store's point of view.

**Restore Purchases was unreachable.** SPEC 5.1 says it lives in Settings; it did
not. `PaywallTrigger.Direct` was defined and requested by no UI, so the only
routes to the paywall were a post-loss offer and the offline block. Apple rejects
a non-consumable app with no visible restore control, and on a device with no
account a reinstall is the only way a paying player gets their purchase back.
Settings now has a Pro section with both, and every restore outcome says
something — a restore that silently does nothing is the commonest reason this
control is reported as broken, because the player cannot tell "you never bought
it" from "we could not ask".

**`allowBackup` was on, and Settings says the opposite.** The copy tells the
player progress does not survive a reinstall or a move to a new phone. Auto
Backup would have made that false *sometimes*, which is the worst of both — a
restore that may or may not happen is not something anyone can plan around. It
would also have carried `installId` to a second device, which is the one thing
that identifier exists not to do. Off, with the reasoning in the manifest.

**A puzzle game was asking for the camera.** `CAMERA`, a `camera` uses-feature,
and a whole `CameraPreview` expect/actual came from the template with no caller
anywhere in Sodogku. It would have put "Camera" on the Play listing. Removed,
along with the camera methods on the iOS `NativeViewFactory` — the Swift
implementations in `IOSNativeViewFactory.swift` are now unused and should go with
them the next time anyone can build iOS, which is not from here.

## 2026-09-08 — the first frame was blocking on a server that isn't deployed

Reported as "stuck on splash". Measured on an emulator with the network off:
**10 seconds** from cold start to the first real frame, every time.

`EnsureAppConfigLoaded` awaits `configStream().first()`, and that stream was
built with `mapNotNull` over the cached snapshot. On a device with no cached
config — a fresh install, or a corrupt cache — it emitted *nothing*, so `first()`
waited for a fetch to either succeed or fail hard enough that the failure path
persisted the fallback. Offline, that meant sitting through the whole retry chain
against `http://localhost/v1/app-config` until the 8-second boot timeout fired.

The file's own KDoc already promised "the first frame never blocks on the
network". It was true from the second launch onwards, which is why nobody caught
it: on a dev machine you launch the app twice and the second one is fine.

`configStream` now starts from the bundled fallback and re-emits when a cached or
fetched snapshot supersedes it. Every declared key has a bundled default
specifically so this is possible — SPEC section 4 requires it, and the app is
meant to be fully playable with the server switched off. Cold boot offline is now
**3.3 seconds**, measured the same way.

A corrupt snapshot takes the same path, because an unreadable file is exactly as
good a reason to start from the fallback as an absent one.

Two things made this worse than it looked. The boot screen had just been changed
to show the dog alone with the spinner delayed five seconds, so a ten-second boot
looked like a frozen splash rather than a slow one — the change was right and it
turned a visible wait into an invisible one. And `throwIfDebug` in the config
decode path means a debug build was the one most likely to sit there.

## 2026-09-08 — naming the controls that `bounceClick` builds

Reported as `IconButton` losing its `contentDescription`. It is not: `IconButton`
was fixed earlier and a `uiautomator` dump shows `Levels`, `Settings` and `Back`
on their clickable nodes. The unlabelled controls were the ones built on
`Modifier.bounceClick` — the rule chips, the booster buttons, the standing ad
offer, the bone counter and the Level/Score stats. Same diagnosis the report
gave, a different component.

`Modifier.clickable` contributes a click action and a role and nothing else, so
each of these came out as a focusable node with no accessible name and a separate
unfocusable child holding the text. The bone counter had nothing at all, being
drawn rather than written.

**Two tidier fixes were tried on a device and neither reached the tree**, which
is the part worth writing down:

- `Modifier.semantics(mergeDescendants = true) { }` inside `bounceClick`, both
  before and after the `clickable`. The labelled child stayed a separate node in
  the dump both times.
- A `label` parameter on `bounceClick` setting `contentDescription` itself, tried
  both inside the `composed { }` block and outside it.

The identical `contentDescription`, set by the caller one link earlier in the
chain, works. The difference is `composed { }`, which is deprecated for reasons
of about this shape. Rewriting `bounceClick` onto `Modifier.Node` is the real fix
and would let the label live in the modifier where it belongs; until then the
KDoc says plainly that it cannot name a control and that every caller must.

An explicit name is also the better answer on merit. A merged name is a
concatenation — "Sniff 3" rather than "Sniff, 3 left" — and the bone row has
nothing to merge in the first place.

**One imperfection remains, measured rather than assumed.** The description lands
on a wrapper node rather than on the node carrying `clickable="true"`. A reader
walking the tree announces it correctly, which is why every label now shows up in
a dump; but the described node and the pressed node are still two nodes, which is
exactly what the `Modifier.Node` rewrite would collapse.

## 2026-09-08 — you could not place a dog illegally

Reported from a device: *"I tried to place a dog illegally and it wouldn't let me
fail. like the double click did nothing."* It did nothing, and said nothing about
why. `commit` opened with `if (cell in state.autoMarks) return`, and a placed dog
auto-marks its own row, column, region and neighbours — so the squares where an
illegal placement actually lives were exactly the ones that could not be reached.

The guard was protective: do not let someone spend a bone on a square the game
already crossed out for them. But a double tap is a deliberate act rather than a
slip, and a control that silently refuses is worse than one that costs something.
A single tap still toggles a mark for free, so the board is not a minefield.

A `game.commit` event now carries whether the square was already marked. A rise in
that is a legibility problem — the crosses are not reading as "ruled out" — rather
than a difficulty one, and those want different fixes.

## 2026-09-08 — the board's controls sit on a lip, not under a gloss

The booster buttons drew a gradient with a shaded band and a sheen, and it read as
a shadow blob under a pill rather than as something with thickness. The user
recognised what it was reaching for and named the right reference: the buttons in
their Cards project, which duplicate the background and drop it down.

That mechanism already existed here — `BasicButton` has had a face-on-a-lip since
C3a, from the same template — and the board's own controls were the only ones not
using it. `DeepSurface` pulls it out so they can, and two ways of drawing
"pressable" collapse into one.

`Modifier.glossy` stays for the level pane's reward chip, which is a badge rather
than a control. A press-in lip on something that cannot be pressed promises a tap
that does nothing, which is the same class of lie the paragraph above is about.

## 2026-09-08 — one lifetime score, folded from the records rather than counted

Reported: *"why is my score staying at 0? … I see score when I play a daily board
but not for the normal campaign mode ones. It should all be one score I think
right?"* The engine was fine. The header rendered `state.score.total`, which is
the **current attempt's** card, so every campaign level opened at zero and the
whole scoring system looked broken from the only place a player can see it.

**Decision: there is no lifetime counter.** The number in the header is a fold —
every `level_progress.best_score` plus every `daily_result.score`, summed when a
board opens. Same argument as the daily streak: a stored tally has no witness. If
a crash between two writes or a bug in one call site leaves it wrong, nothing on
the device can tell, progress is device-local so there is no server copy to
arbitrate, and the player reports a number we can neither verify nor rebuild.
Ship a fix to a fold and every past bug is retroactively corrected on the next
read. The cost is two table reads per level opened, no joins.

**The rule that is easy to get wrong**, and the reason `LifetimeScore.withAttempt`
is a named function with its own tests rather than a `+` at a call site:

```
shown = banked - bankedForThisBoard + max(bankedForThisBoard, attemptScore)
```

Everything else the player has banked, plus the better of *this board's own best*
and the attempt on screen. A record only ever improves, so an attempt at a level
worth 5,000 may not add a second 5,000 as it goes — otherwise replaying the
shortest 4x4 in the pack is the fastest way to earn in the game. Both directions
are pinned, because each one alone passes a wrong implementation: a test that a
replay does not double-count passes against "ignore the attempt entirely", and a
test that the number climbs passes against "always add". Checked by mutation —
`banked + attempt` fails four tests, `banked` fails five.

A **lost** attempt contributes nothing. `GameState.lifetimeScore` drops the
attempt when the phase is `Lost`, because the loss banks no record and points
left on the header that no row will ever hold are a lie that a retry silently
corrects.

## 2026-09-08 — a booster costs points, and deliberately costs no paws

The other half of the same report: the score should reflect "how many hints they
used". `scoring.boosterPenaltyRate` (0.15) is multiplicative per sniff or treat
spent in the attempt — one banks 0.85 of the run, two 0.72, five 0.44.

**Multiplicative rather than a flat deduction** so that no number of boosters can
drive a score negative (nothing downstream has to clamp), the cost is the same
wherever in the attempt the help was taken, and it scales with the board rather
than needing a second per-size coefficient.

**It does not move the paw rating, and that is the interesting call.** Subtract
points and leave `Scoring.parScore` alone and three paws quietly becomes
unreachable for anyone who used a hint — including every player following the
tutorial, which *instructs* a sniff and a treat on level 2. So a teaching aid
would permanently cost the lesson's own board a paw, and nothing in the explainer
says so. The fix is arithmetic rather than a special case: because the cost is a
multiplier and the paw thresholds are fractions of par, rating the run on the
pre-penalty total is exactly the same as scaling par by the same factor
(`score × f ≥ par × f × 0.85` is `score ≥ par × 0.85`). Paws say how the board
was solved; the banked score says what the help was worth; neither has to know
about the other.

The alternative considered was charging at the moment of the spend, so the header
visibly dips when a sniff is used. Rejected: a flat cost early in a 4x4 takes the
running total below zero, and a percentage of a total that is still growing makes
an identical hint cost wildly different amounts depending on when it was taken.

**Difficulty was already in the score** — `Scoring.complete` multiplies the
completion bonus by `1 + (difficulty - 1) × difficultyBonusRate`, so a tier-4
board pays 1.6x a tier-1 board of the same size, and grid size scales every
placement on top of that. The user's "how complex the table was" is already
priced; a second difficulty term would have double-counted it.
