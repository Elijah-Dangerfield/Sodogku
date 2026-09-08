# Store listing

Draft copy for Google Play and the App Store. Written to be reacted to, not adopted unread. Every
factual claim in it is checked against the code and the spec; the voice is a first pass and yours
to overwrite.

Rules applied: no em dashes, no "not just X but Y", no three-item rhythm for its own sake, no
"seamless", "elevate", "unleash", "dive in". Nothing here promises a feature that does not exist.

---

## 1. Names and short copy

| Field | Value | Limit | Used |
|---|---|---|---|
| Play title | `Sodogku: Dog Logic Puzzle` | 30 | 25 |
| App Store name | `Sodogku` | 30 | 7 |
| App Store subtitle | `Sudoku rules, zero numbers` | 30 | 26 |
| Play short description | `A logic puzzle with no numbers. Work out where every dog belongs.` | 80 | 65 |
| App Store promotional text | `A new daily board every day at midnight, the same one for everybody. 500 hand-verified levels underneath it.` | 170 | 108 |

Two alternates for the short description, if the first reads too flat:

- `No numbers. Ten colours, three rules, and one place each dog can go.` (68)
- `Sudoku's satisfaction without the arithmetic. Every level has one answer.` (73)

**On the title.** `Sodogku` alone is stronger branding and worse discovery. Play title text is
indexed, App Store names are indexed, and nobody searches for a word they have never seen. The
suffix earns "dog", "logic" and "puzzle". Drop it later if the name gets known.

---

## 2. App Store keywords

100 characters, comma separated, no spaces after commas, no words already in the name or subtitle
(Apple indexes those separately, so repeating them wastes the field).

```
logic,brain,deduction,queens,grid,colour,color,daily,offline,relax,teaser,board,solitaire,thinking
```

98 characters. Notes on what is in and what is out:

- `queens` is in because the rules are LinkedIn Queens' rules and that is what people who like this
  genre search for.
- `sudoku` is **out of the keyword field** because it is already in the subtitle. Do not pay for it
  twice.
- No competitor app names. Apple rejects those, and it is a bad look besides.
- `colour` and `color` are both in; Apple does not stem across spellings.

Play has no keyword field. Play indexes the title, short description and long description, so the
terms above need to appear naturally in the long description instead. They do.

---

## 3. Long description

Same text for both stores. 4,000 character limit on each; this is about 1,900.

> Every board is a grid of coloured regions and a simple question: where does each dog go?
>
> Three rules, and they never change.
>
> One dog per colour. One dog per row and column. No two dogs touching, not even at the corners.
>
> That is the whole game. There are no numbers to add up and nothing to memorise. Every level has
> exactly one solution and can be reached by reasoning alone, because a solver checked all 500 of
> them before they shipped. If you are ever stuck, there is something on the board that proves the
> next move.
>
> **Tap to think, tap twice to commit.** A single tap crosses a square out and costs nothing, so
> you can leave notes all over the board while you work. Two quick taps places a dog. Get it right
> and the dog rules out its own row, column, colour and neighbours for you, and half the board
> falls into place at once. Get it wrong and it costs a bone.
>
> **500 levels, 4x4 up to 10x10.** They open small and get wider slowly. The difficulty is the
> depth of reasoning a board needs, not the size of it, so a 6x6 can be harder than an 8x8 and
> sometimes is.
>
> **A daily board.** One puzzle a day, the same one for everyone, with a streak that only counts if
> you keep showing up. Share your result as a grid of coloured squares. It gives nothing away.
>
> **Two helpers, and neither one solves it for you.** A Sniff dims the board and lights the squares
> your own deduction has already ruled out, so it shows you the technique rather than the answer. A
> Treat places one dog correctly with no bone at risk. Both refuse to be spent if they have nothing
> to add.
>
> **Plays offline.** Every level is in the app. Nothing to download, nothing to log in to.
>
> **No account, ever.** There is no sign-up, no email, no password. Your progress lives on this
> phone.
>
> **Built to be readable.** Region colours are the mechanic, and no set of ten colours survives
> red-green colour blindness, so there is a setting that puts a distinct shape on every colour as
> well. Every animation can be turned down.
>
> Sodogku Pro is a one-off purchase that removes ads, makes offline play unlimited, and starts
> every board with helpers in hand.

### Things deliberately not in it

- No "endless hours of fun", no "train your brain", no claim about IQ or cognition.
- No feature that is behind a flag or unbuilt. Achievements exist and are not mentioned, because
  21 badges is not a reason to install and the space is better spent on the rules.
- No score-and-paws explanation. It is a good system and it does not fit in a store listing; the
  screenshot carries it.

---

## 4. Screenshot plan

Eight captured, in `docs/store/screenshots/android-phone/`. Play takes up to eight; the first three
are what almost everyone sees, because Play shows a scrollable row and most people never scroll.

Order matters more than count. This order answers, in sequence: what is this, why would I care, is
there enough of it.

| # | File | Screen | What it has to prove |
|---|---|---|---|
| 1 | `01-place-the-dogs.png` | 7x7 mid-attempt, three dogs down, auto-mark cascade visible, one red X | The mechanic in one glance. You can see that placing a dog cleared its row, column and colour, which is the thing that makes the game feel good and is impossible to explain in a sentence. |
| 2 | `02-good-dog.png` | Win sheet: dog, "Good dog!", two of three paws, score, streak | The reward. Also the only place the paw rating appears, so it doubles as "there is a score here and you can do better". |
| 3 | `03-ten-by-ten.png` | Level 391, 10x10, eight dogs placed, score 15,268 | Depth. Screenshots 1 and 2 are a small friendly board; this says the game does not run out. |
| 4 | `04-levels-and-daily.png` | Level pane over the board: daily card, level ladder with sizes and Treat rewards | Volume and the daily loop, in the same frame. Locked rows reading 392 to 400 do the "there are hundreds of these" job without a marketing number. |
| 5 | `05-daily-board.png` | A fresh 7x7 daily, untouched | The palette, clean. This is the prettiest frame in the set and the one that sells the look. |
| 6 | `06-shapes-on-colors.png` | 10x10 with the colourblind glyph set on | Accessibility, and it earns the line in the description. Also a genuinely distinctive image next to every other puzzle listing. |
| 7 | `07-achievements.png` | Achievement grid, 2 of 21 earned | Something to come back for. Weakest of the eight; drop it first if a stronger frame appears. |
| 8 | `08-treats.png` | Treat explainer dialog with the dog | Shows the helper economy honestly, including that an ad is one way to get more. Optional. |

### Capture recipe

The emulator is 1080x2424, which is 2.24:1. Play rejects screenshots whose long side is more than
twice the short side, so the raws are cropped to 1080x2160 (exactly 2:1, inside the rule) with:

```
sips -c 2160 1080 --cropOffset 150 0 raw.png --out out.png
```

150 rows off the top removes the status bar and lands just above the header buttons; the remaining
114 off the bottom removes the gesture bar and stops short of the booster pills. Re-check both edges
if the layout moves.

Frames were driven with `scripts/dev/drive.py` (`text`, `tap`, `shot`) against a debug build on
`emulator-5554`.

### What is missing

- **No onboarding or tutorial frame.** Capturing one needs `drive.py launch --fresh`, which wipes
  app data, and another agent was mid-session on the same emulator. Worth adding: a coach-mark
  frame is a good #2 or #3 for a puzzle game because it says "this teaches you".
- **No iOS screenshots.** App Store Connect needs 6.9" (1320x2868) and, if the app supports iPad,
  13". They have to come from an iOS simulator, and iOS has never been run on this machine
  (`xcode-select` points at something that is not Xcode, per BUILD-PLAN C0). Android renders must
  not be submitted as iPhone screenshots.
- **No feature graphic** (Play, 1024x500) and no App Store preview video. Both want the icon
  artwork first.

---

## 5. Categorisation and the rest of the form

| Field | Play | App Store |
|---|---|---|
| Category | Games → Puzzle | Games → Puzzle (secondary: Board) |
| Contains ads | Yes | N/A (declared via the label) |
| In-app purchases | Yes, one managed product `sodogku_pro`, $4.99 | Yes, one non-consumable, same id and price |
| Content rating | Everyone / 4+, subject to the IARC questionnaire | 4+ |
| Target audience | 13+ on the general-audience branch (SPEC 7.1) | N/A |
| Privacy policy URL | `pages/privacy.html` via GitHub Pages, **not yet written** | same |
| Support URL and email | **Not yet decided** (SPEC §20) | same |

Data safety and the privacy nutrition label are in
[`data-safety.md`](./data-safety.md), not here.

---

## 6. Copy that has to match the app

If any of these change in code, this file is wrong:

- "three rules" and their wording match the rule chips in `libraries/resources/.../strings.xml`.
- "500 levels" and the 4x4 to 10x10 range match SPEC 1.7 and the generated pack.
- "one solution, reachable by reasoning" matches the pack verification test (SPEC 3.3).
- "a Sniff refuses to be spent if it has nothing to add" matches SPEC 1.5 and `game.booster_no_op`.
- "no account, no sign-up" matches the whole architecture and the Settings copy.
- "your progress lives on this phone" is what Settings says, and it has an Android caveat. See
  `data-safety.md` §7.1 before this line goes public.
