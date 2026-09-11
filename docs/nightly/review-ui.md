# SD-6 review: the `libraries/ui` board and dog components

Reviewed 2026-09-11 against `17a2a6c`, the last slice on the SD-6 list.

Scope, exactly: `components/board/` (`BoardCell`, `BoardCellLabels`, `BoardDrag`,
`BoardSurface`, `PlacementPulse`), `components/dog/` (`AnimatedDog`, `Dog`,
`DogLoopSchedule`), `components/game/` (`BoardControl`, `GameHud`, `StatPills`,
`CoachMark`) and the `system/` primitives they lean on (`AnimatePlacement`,
`AnchoredCard`, `Motion`, `Span`, `Haptics`, `Focus`). Call sites in
`features/game/impl` were read to confirm how the components are driven, not
reviewed.

Nothing was run on a device or emulator. Every "a screen reader hears" below is
inferred from the semantics tree the code builds, and is labelled as such.

## Verdict

The board cell is the best-built composable in the repo and the two things the
brief worried about most, animated reads in composition and the drag gesture,
are both done right there. The defects are in the smaller components around it,
and almost all of them are semantics: three components put a label on one layout
node and the click, or the children, on another, so a screen reader meets a
named thing that does nothing and an unnamed thing that does. That is the same
mistake three times, and the composition tier can prove all three.

## Numbers

- **Tests in the slice:** 41 across six classes (`BoardGeometryTest`,
  `PlacementPulseTest`, `BoardCellLabelsTest`, `DogLoopScheduleTest`,
  `BoardControlBeatTest`, `AnchoredCardTest`), all green at baseline. 86 in the
  module at `17a2a6c`, 85 since SD-98 deleted the template `SharedCommonTest`.
- **Mutations:** 16 run, 13 killed, 3 survived. Every survivor is explained
  below; two of them are tests that cannot fail for the reason they claim
  (SD-107), one is a doc-versus-arithmetic disagreement (SD-106).
- **What has no test at all:** every composable. Of 17 production files (about
  3,350 lines), six pure units totalling about 270 lines are under test:
  `BoardGeometry`, `PlacementPulse`, `BoardCellLabels.describe/stateOf/fill`,
  `DogLoopSchedule`, `beatsForAttention`, `anchoredCardTop`. `BoardCell`,
  `BoardSurface`, `dragAcrossCells`, `AnimatedDog`, `LoopingDog`, `Dog`,
  `BoardControl`, the five composables in `GameHud`, `StatPills`, `CoachMark`,
  `animatePlacement`, `AnchoredCard`'s layout, `FocusScrim`, `focusTarget`,
  `coveredByOverlay` and `Haptics` have none. Roughly nine lines in ten of the
  slice.
- **Provable with the composition tier** (Robolectric, `androidUnitTest`, the
  `FloatingWindowHostTest` recipe): SD-99, SD-100, SD-101 and SD-108 with a
  single semantics assertion each; SD-102 and SD-105 with a recomposition
  counter; SD-103 by construction; SD-104 with the test clock. `:libraries:ui`
  has no `androidUnitTest` setup yet, so the first of these carries the cost of
  copying the Robolectric block from `libraries/navigation/build.gradle.kts`.
  `testing.md` says not to use the tier for the board's gestures and this review
  agrees.

## Mutation log

Run scoped to `:libraries:ui:testDebugUnitTest --tests '<class>'`, results read
from the JUnit XML. Each target string was confirmed to occur once before the
edit and every file was restored from a copy and `diff`ed after.

| # | File | Mutation | Result |
|---|---|---|---|
| 1 | `BoardDrag.kt:52` | `<= cellPx` to `< cellPx` | killed, 2 tests |
| 2 | `BoardDrag.kt:37` | `row * size + column` to `column * size + row` | killed, 2 |
| 3 | `BoardDrag.kt:49` | drop `if (position < 0f) return null` | killed, 1 |
| 4 | `PlacementPulse.kt:64` | `singleOrNull()` to `firstOrNull()` | **survived** (SD-107) |
| 5 | `PlacementPulse.kt:45` | drop `nonce == 0 -> None` | killed, 1 |
| 6 | `PlacementPulse.kt:47` | drop the column half of the line test | killed, 3 |
| 7 | `BoardCellLabels.kt:145` | swap the `Marked` and `Wrong` phrases | **survived** (SD-107) |
| 8 | `BoardCellLabels.kt:139` | `row + 1` to `row` (run alone) | killed, 2 |
| 9 | `BoardCellLabels.kt:135` | ignore `colorblind`, always name the hue | killed, 1 |
| 10 | `BoardCellLabels.kt:181` | `argument - 1` to `argument` | killed, 2 |
| 11 | `DogLoopSchedule.kt:46` | `choice < previous` to `<=` | killed, 2 |
| 12 | `DogLoopSchedule.kt:41` | forget the previous clip | killed, 2 |
| 13 | `DogLoopSchedule.kt:58` | `roll >= max` to `roll > max` | **survived** (SD-106) |
| 14 | `AnchoredCard.kt:101` | never flip above the anchor | killed, 2 |
| 15 | `AnchoredCard.kt:105` | drop the reversed-range guard | killed, 1 (by exception) |
| 16 | `BoardControl.kt:240` | drop `&& !inspecting` | killed, 1 |

A throwaway probe was also run against the unmutated `DogLoopSchedule.clipAt`:
it returns at turn 50,000 and overflows the JVM stack at turn 100,000 (SD-106).

## Where the code is fine

Worth stating, because it is most of the slice.

- **`BoardCell`.** Every `Animatable` is read inside `graphicsLayer` or
  `drawBehind`; the one value composition needs, whether the dog exists, goes
  through `derivedStateOf`. The semantics block is the right shape: content
  description, state description, click, long click and custom action all on one
  node, remembered against the values it reads, callbacks behind a stable holder.
  `placementPulseProgress` only allocates for the squares a placement touched.
  The restored-board pop (a cell that starts `Occupied` still runs the overshoot
  once) is a choice, not a bug.
- **`dragAcrossCells`.** The wait-for-a-second-square design holds up under a
  close read. Consuming the change once dragging cancels the start square's tap
  through the `Final` pass, which `waitForUpOrCancellation` checks; the touch-slop
  gate handles a finger that goes down a pixel from a boundary; the gutter is
  nobody's, consistently with `BoardGeometry`; a system cancel arrives as an up,
  so `onDragEnd` fires. All three geometry mutations died. One narrow gap is in
  SD-109.
- **`rememberPlacementPulse`.** The stale-`previous` case across levels that
  worried me is closed by `key(level.id)` in `GameScreen`.
- **`BoardSurface`.** Labels resolved once and handed down a static local; the
  grid is a traversal group rather than a merged node, which is right.
- **`animatePlacement` and `AnchoredCard`.** The split between placing and
  animating is right, `onPlaced` sits outside `offset` in the chain so it
  measures the unanimated position, and the spring is read in the layout phase.
  Both placement mutations died.
- **Reduce animations.** `BoardCell` still runs its entrance spring with a zero
  stagger under the setting. `features.md` says the entrance "shortens", so this
  matches the spec rather than breaking it.
- **`LifeRow`** has no semantics, and its only caller wraps it in a labelled pill.
  **`RuleChip`** puts its label on the node that takes the click. **`Haptics`**
  defaults to silent and **`LocalReduceAnimations`** to false, as the house rule
  asks.
- **The detekt rule** catches exactly what it says it does. Its blind spot is the
  subject of SD-102.

## Findings

Eleven, ranked. Two P1, nine P2. Ready to paste into `docs/todos.md`.

Ids start at SD-99 as briefed. The file's own rule, increment the highest id
present, would say SD-93 today, because SD-93 through SD-98 were filed and
closed on 2026-09-11 and closed items are deleted. Those six are retired, not
free; a commit message already names each of them.

## SD-99 [P1] — Put the booster control's name on the node that takes the tap

**Ask:** A screen reader user should meet each control under the board as one
button with a name, a count and a working activation. Today
`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/game/BoardControl.kt:121`
puts the content description on the outer `Column` and `bounceClick` (a
`clickable`) on the face `Box` two layout nodes down (line 145). A plain
`semantics {}` does not merge or hide its children, so the tree holds four stops
per control: a named node with no action, an unnamed button, the badge text and
the label text. Activating the named node does nothing. The disabled state of
the greyed-out refill button is on the unnamed node too.

Also on that line: the name is the hard-coded English `"$label, $count left"`.
`booster_a11y` in `strings.xml` is the same sentence as a resource and is used
only by `BoosterButton`, which nothing calls (SD-109).

Scenario: a TalkBack user swipes to "Sniff, 3 left", double-taps, and nothing
happens; the next swipe lands on "button" with no name, and that one works.
Inferred from the tree, not run on a device. Confidence high on the structure,
which is a read of the code.

**Done when:** each `BoardControl` is a single semantics node carrying the label,
the count, `Role.Button`, the disabled state and the click, and a composition
test asserts it: `onNodeWithContentDescription("Sniff, 3 left").assertHasClickAction()`
fails today.

**Hints:** `RuleChip` in `GameHud.kt:248` is the shape that works: the
`semantics { contentDescription }` and the `bounceClick` on the same modifier
chain, as the `bounceClick` docblock in `system/BounceClick.kt` explains at
length. Moving both onto the `Column` makes it a merging node, so the
`RewardBadge`'s own description ("Watch an ad") merges into the name, which is
what the sighted badge is saying anyway. Take the string from
`stringResource(Res.string.booster_a11y, label, count)` and delete
`BoosterButton` in the same change. The composition tier needs the Robolectric
block from `libraries/navigation/build.gradle.kts` copied into `:libraries:ui`
first. Found by the SD-6 review of the `libraries/ui` board and dog slice,
2026-09-11, against `17a2a6c`.

## SD-100 [P1] — Speak the paw rating

**Ask:** `PawRating`
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/game/GameHud.kt:98`)
draws its paws in `drawBehind` and declares no semantics. None of its three
callers adds any: the win sheet (`GameOutcomeSheets.kt:131`), the level list
(`LevelDrawer.kt:335`) and the daily card (`DailyCard.kt:152`). `winStats` leaves
paws out of the pills on purpose ("Paws are not a pill. They have their own row
above, drawn as paws"), so on the win sheet the rating exists only as a drawing.

Scenario: a screen reader user clears a level and hears the verdict title, the
score, the time and the mistakes, and never how many paws they earned; in the
level list every cleared row is silent about its rating. `features.md` promises
that nothing is encoded only visually. Confidence high; this is a read of the
code and there is no node to find.

**Done when:** `PawRating` carries a content description built from a plural
resource ("3 of 5 paws"), on a node that merges or clears its drawn children, and
a composition test finds it. `onNodeWithContentDescription("3 of 5 paws")`
fails today.

**Hints:** `clearAndSetSemantics { contentDescription = spoken }` on the `Row`;
the `Row` has no text children so merging is not the concern here, and the
`bounceClick` docblock's warning about merged nodes with children does not
apply. Resolve the string in the composable, not in a `semantics` lambda: the
`BoardCellLabels` trick exists because a hundred cells share one set, and there
are at most five paws. Found by the SD-6 review of the `libraries/ui` board and
dog slice, 2026-09-11, against `17a2a6c`.

## SD-101 [P2] — StatPills leaks its caption and value as separate nodes

**Ask:** `StatPills`
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/game/StatPills.kt:67`)
sets `contentDescription = stat.spoken` with a plain `semantics {}`, which does
not merge or hide the two `Text` children beneath it. A screen reader gets the
sentence and then the caption and the value as two more stops: "342 points",
"SCORE", "342". The docblock at the top of `Stat` says the pill exists to stop
exactly that ("the caption and value read fine side by side and terribly one
after the other").

Scenario: every outcome sheet and the achievements screen, nine stops for three
facts. Inferred from the tree; confidence high on the structure.

**Done when:** each pill is one node. In a composition test with the merged tree,
`onNodeWithText("SCORE").assertDoesNotExist()` passes; today it fails.

**Hints:** `clearAndSetSemantics { contentDescription = stat.spoken }`. The pill
is not interactive so there is no click to lose by clearing. Found by the SD-6
review of the `libraries/ui` board and dog slice, 2026-09-11, against
`17a2a6c`.

## SD-102 [P2] — FocusScrim reads its Animatable in composition, and the detekt rule cannot see it

**Ask:** `libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/system/Focus.kt:196`
is `if (progress.value <= 0f) return`, a read of a `remember { Animatable() }`
in the composable body. `AnimatedStateReadInComposition` matches only
`by animate*AsState(...)`, so this shape is invisible to it in every file. Two
more of the same shape outside this slice: `ScoreCounter.kt:223`
(`if (progress.value >= 1f) return`) and `LevelDrawer.kt:126`
(`if (slide.value <= 0f) return`, where `slide` is an `animateFloatAsState` kept
as `State`, which is the rule's own recommended fix and then read anyway).

Three consequences in `FocusScrim`:

- The whole body, the `lit` lookup, the `Box`, the `AccessibleHole` nodes and
  the `content` lambda's scope, recomposes on every frame of the `Motion.Pop`
  spring in and out. Bounded, small subtree, and still the pattern the repo
  bans.
- `Motion.Pop` is a 0.5-damping spring with no bounds set on the `Animatable`.
  On dismissal it undershoots zero (about 16% for that damping, then about 3%
  back over), so the `<= 0f` gate removes the scrim from composition and then
  composes it again as the spring recrosses zero: a fresh `pointerInput`, fresh
  `AccessibleHole` semantics nodes, the coach mark re-entering, for a few frames
  at an alpha nobody can see. This half is reasoning from the spring, not
  observed. Confidence medium-high.
- The scrim ignores `LocalReduceAnimations`. `features.md` says dialogs fade
  under it; the scrim springs regardless.

Scenario: a TalkBack user dismissing the last-bone warning may hear the lit
control's node flicker back into the tree during the fade-out. A sighted player
sees nothing.

**Done when:** composition depends on a `derivedStateOf { progress.value > 0f }`
as `BoardCell` already does; the progress cannot leave `[0, 1]` (either
`updateBounds(0f, 1f)` or `Motion.fade()` under reduce-animations and a bounded
spring otherwise); and the detekt rule flags a `.value` read of an
`Animatable` or a kept `State` in a composable body outside a
`graphicsLayer`/`drawBehind`/`offset`/`layout` lambda, or a targeted rule
covers the `if (x.value` gate shape. A composition test with a recomposition
counter on `content` proves the first part.

**Hints:** The two sibling sites are worth fixing in the same sweep; the
`LevelDrawer` one shows the rule's fix text creates the blind spot. Found by the
SD-6 review of the `libraries/ui` board and dog slice, 2026-09-11, against
`17a2a6c`.

## SD-103 [P2] — AnimatedDog loops forever under inspection mode

**Ask:** `AnimatedDog`
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/dog/AnimatedDog.kt:71`)
runs `while (true) { delay(83); frame++ }` and reads neither
`LocalInspectionMode` nor `LocalReduceAnimations`. Its sibling `LoopingDogImpl`
(line 207) and `BoardControl.beatsForAttention` both stop under inspection, and
AGENTS.md lists an infinite animation in a preview as a known landmine.

Two costs. `AnimatedDogPreview` and `BoardCellStatesPreview` (it has an
`Occupied` cell) never settle. And the first composition test that composes a
`BoardCell` with `state = Occupied` and `animated = true`, which SD-99 through
SD-101 make likely, inherits a clock that is never idle under `autoAdvance`, and
the failure will read as a hung test rather than as this.

Confidence high; this is a read of the code.

**Done when:** `AnimatedDog` holds its frame under `LocalInspectionMode`, and the
board cell preview renders to a still.

**Hints:** `val playing = playing && !LocalInspectionMode.current` before the
effect. Reduce-animations is correctly the caller's decision (`BoardCell` swaps
in `Dog` for it); inspection mode is not. Found by the SD-6 review of the
`libraries/ui` board and dog slice, 2026-09-11, against `17a2a6c`.

## SD-104 [P2] — RuleChip flashes once per rule, not once per strike

**Ask:** `RuleChip`'s flash
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/game/GameHud.kt:230`)
is `LaunchedEffect(highlighted, still)` on a `Boolean`. A second wrong guess
against the same rule leaves `highlighted` true, so the effect does not restart
and the chip does not move, while the cell shakes and the board flinches, both
of which are keyed on a nonce for exactly this case. `BoardCell`'s own docblock
states the rule: "A nonce rather than a boolean, so the same cell can be got
wrong twice running."

Scenario: a player breaks "no touching" twice in a row. The first strike flashes
the chip and it settles to the tint; the second changes nothing on the chip, so
the one piece of feedback that names the rule is the one that goes quiet.
Confidence high on the behaviour; whether a re-flash is wanted is a design call,
and the rest of the slice has already made it.

**Done when:** a second strike against the same rule re-flashes its chip.

**Hints:** Key the effect on `state.strikeNonce`, passed from `RuleChips` in
`GameScreen.kt:572`, and keep the boolean for which chip. A pure
`ruleChipFlashKey(broken, strikeNonce)` beside `brokenRule` makes the decision
testable without a composition. Found by the SD-6 review of the `libraries/ui`
board and dog slice, 2026-09-11, against `17a2a6c`.

## SD-105 [P2] — rememberHaptics allocates a new Haptics on every composition

**Ask:** `rememberHaptics`
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/system/Haptics.kt:70`)
remembers nothing: it returns `Haptics(feedback, enabled)` each call. `Haptics`
is `@Immutable` with identity equality, so every composition yields a "changed"
value. In `features/game` it is used locally and this costs one allocation per
recomposition. In `features/streak` it is the value of a
`staticCompositionLocalOf` provided at an entry point that recomposes on every
state change (`StreakFeatureEntryPoint.kt:54` and `:72`), and a static local
whose value changes does not track reads: it recomposes everything under the
provider and disables skipping while it does. Confidence high on the
allocation, medium-high on the static-local consequence, which is Compose's
documented behaviour and was not measured here.

**Done when:** `rememberHaptics` returns the same instance for the same
`(feedback, enabled)`, and a composition test with a recomposition counter under
`CompositionLocalProvider(LocalHaptics provides rememberHaptics(...))` shows the
child not recomposing when an unrelated state changes.

**Hints:** `remember(feedback, enabled) { Haptics(feedback, enabled) }`. It is a
one-line change plus an import and was not taken here because its effect is in
another feature. Found by the SD-6 review of the `libraries/ui` board and dog
slice, 2026-09-11, against `17a2a6c`.

## SD-106 [P2] — DogLoopSchedule.clipAt recurses once per turn, without bound

**Ask:** `clipAt(turn)`
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/dog/DogLoopSchedule.kt:41`)
calls `clipAt(turn - 1)` to learn the previous clip, so its stack depth is
`turn`. `LoopingDogImpl` calls it on every recomposition with a `turn` that
only ever grows. Measured on the JVM: it returns at turn 50,000 and overflows
the stack at 100,000. A turn is about 2.5 to 3.2 seconds, so that is over forty
hours of one dog looping on one screen before the JVM limit; the iOS main thread
has a 1MB stack and larger native frames, so its limit is lower and was not
measured. Latent rather than live, and the per-recomposition cost grows
linearly with time on screen for as long as the dog is composed.

Same file, `holdTurnsAt` (line 58): `roll` is in `0 until 2 * max` and
`roll >= max` maps to zero, so the hold is never `max` (the top value is
`max - 1`) and zero comes up four times in six, not "about half" as the docblock
says. Mutating `>=` to `>` survived because
`theDogSometimesPausesAndSometimesDoesNot` allows `0..MaxHold`. Confidence high
on both; the first was measured, the second is arithmetic.

**Done when:** `clipAt(1_000_000)` returns in `DogLoopScheduleTest`, and the
hold's name, docblock and range agree.

**Hints:** Iterate from turn zero carrying the previous clip, or have
`LoopingDogImpl` hold the previous clip in state and pass it in; the schedule's
determinism is preserved either way. Found by the SD-6 review of the
`libraries/ui` board and dog slice, 2026-09-11, against `17a2a6c`.

## SD-107 [P2] — Two board tests cannot fail for the reason they claim

**Ask:** Both found by mutation.

`PlacementPulseTest.severalSquaresAppearingAtOnceIsNotAPlacement` restores
`{1, 7, 13}` and asserts that cell 7 has no role. With `singleOrNull()` changed
to `firstOrNull()` the pulse picks cell 1 (row 0, column 1), and cell 7 (row 1,
column 2) is on neither of its lines, so the test stays green against the exact
wrong implementation it was written to refuse.

`BoardCellLabelsTest.everyCellStateSaysSomethingDifferent` asserts that the five
state phrases are distinct and nothing else, as its docblock admits. Swapping
the `Marked` and `Wrong` phrases in `stateOf` stayed green. No test anywhere
pins which phrase belongs to which state, and this is the string a screen
reader user hears when they cross a square off: after that swap they would hear
"wrong guess, cost a bone".

**Done when:** the restore test asserts `restored == PlacementPulse.None` (or
probes the cell that would be the false origin), and a mapping test asserts
each `BoardCellState` against its own phrase. Both mutations above then die.

**Hints:** Both are additions to existing test files. Found by the SD-6 review
of the `libraries/ui` board and dog slice, 2026-09-11, against `17a2a6c`.

## SD-108 [P2] — A coach mark appears silently to a screen reader

**Ask:** `CoachMark`
(`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/game/CoachMark.kt:49`)
declares no live region and requests no focus, and `FocusScrim` hides the
board underneath it via `coveredByOverlay`. When the tutorial or the empty-board
warning appears, a TalkBack user whose reading cursor was on a cell has that
node vanish and hears nothing about why; the card has to be found by swiping.
The same applies to `SpeechBubble`, which is outside this slice.

Confidence: high that nothing announces (a read of the tree); medium on the
remedy, which is standard practice for overlays rather than something this repo
has specified.

**Done when:** the card's title announces on appearance, and a composition test
asserts `SemanticsProperties.LiveRegion` (or focus) on it. The assertion fails
today.

**Hints:** `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on the
title `Text`, or a `FocusRequester` on the card fired from a `LaunchedEffect`
keyed on the anchor. Prefer the live region: moving focus fights the reading
order a user is in. Found by the SD-6 review of the `libraries/ui` board and dog
slice, 2026-09-11, against `17a2a6c`.

## SD-109 [P2] — Untidy: dead code, stale comments and one narrow gesture gap in the board and HUD

**Ask:** Six small things, none of which a player meets on its own, gathered so
one pass can clear them.

- `BoosterButton` (`GameHud.kt:310` to `374`) has no callers outside its own
  preview. Its `booster_a11y` string is kept alive only by it, which is why the
  unused-strings guard from `c4a8b0f` does not see it. SD-99 wants the string;
  delete the composable after.
- `Spotlight.message` (`Focus.kt:69`) is never set and never read.
- `BoardCell.kt:259` to `272` is a second, mis-indented copy of the
  `derivedStateOf` paragraph, inside `drawBehind` where the concern does not
  apply. Delete it.
- `BoardCell.kt:197`: `rememberUpdatedState(strikeNonce)` feeding
  `LaunchedEffect(nonce)` is `LaunchedEffect(strikeNonce)` plus one snapshot
  write per cell per composition. The comment above it explains the reset,
  which is right; the holder is not doing anything.
- `BoardCellLabels.kt:268`: the `remember` keys omit `clearAction` and
  `placeAction`. Harmless today, since a locale change moves every other key,
  and wrong in principle.
- `dragAcrossCells` (`BoardDrag.kt:96`): if the `pointerInput` key changes
  mid-stroke (a rotation, or a second finger toggling `enabled`), the coroutine
  is cancelled before the trailing `onDragEnd()`. The cost today is one lost
  `game.drag` event and a `MarkStroke` held in the view model until the next
  stroke or attempt; `startStroke` resets it, so nothing on the board is wrong.
  A `try/finally` around the loop closes it.

**Done when:** the six are gone and `git grep` finds no `BoosterButton`,
`Spotlight.message` or duplicated paragraph.

**Hints:** All in files this review read end to end; none needs a test beyond
the build. Found by the SD-6 review of the `libraries/ui` board and dog slice,
2026-09-11, against `17a2a6c`.

## Suggested entry for the SD-6 slice list

- **The `libraries/ui` board and dog components**, 2026-09-11, against
  `17a2a6c`. Eleven findings, now SD-99 through SD-109. 16 mutations, 13
  killed, every survivor explained. Verdict: the board cell and the drag are
  right about the two things the brief worried about, animated reads and gesture
  ownership, and the defects are semantics in the components around it: three
  controls with the name on one node and the action or the children on another,
  all provable with the composition tier. Nine lines in ten of the slice have no
  test, which is the expected shape for composables; two existing tests were
  found unable to fail for the reason they claim.
