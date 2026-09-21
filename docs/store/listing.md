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
| App Store promotional text | `A new daily board every day at midnight, the same one for everybody. A thousand hand-verified levels underneath it.` | 170 | 115 |

Two alternates for the short description, if the first reads too flat:

- `No numbers. Ten colours, three rules, and one place each dog can go.` (68)
- `Sudoku's satisfaction without the arithmetic. Every level has one answer.` (73)

**On the title.** `Sodogku` alone is stronger branding and worse discovery. Play title text is
indexed, App Store names are indexed, and nobody searches for a word they have never seen. The
suffix earns "dog", "logic" and "puzzle". Drop it later if the name gets known.

**On the level count, which is the only number in this file.** The promotional text is the one
App Store field that can be rewritten without submitting a build, so it is the one place a
figure that grows every time the pack does is cheap to keep honest. Everywhere else, and
especially in the long description, a count has to be corrected through a release. It went
stale once already: this file said 500 for however long it took someone to read it against the
pack, and the bullet at the bottom claiming the number matched the pack was wrong the whole
time. The long description now carries no count at all. What it says instead is that no two
boards in the game are the same puzzle, which is a harder claim, a test holds it, and it does
not age.

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

Same text for both stores. The limit is 4,000 characters on each and this is about 650, which is
deliberate. The previous draft was 2,250 and read like a feature list with headings. Nobody
reads a store description to the end; they read the first line and the shape of it.

Spelled **colored**, not coloured, because both listings are US English. The rest of this repo
writes British English and this one field deliberately does not.

> Sodogku is sudoku with dogs instead of numbers.
>
> Every board is a grid of colored regions, and every region hides one dog. Work out where each
> one goes: one dog per color, one per row and column, and no two touching, not even at the
> corners. Tap once to cross a square off, twice to place a dog. Get it wrong and it costs you a
> bone.
>
> Boards run from 4x4 up to 10x10. Every one has exactly one answer and you can reason your way
> to all of it, so there is never anything to guess. There is a new board every day as well, the
> same one for everybody.
>
> No account, no signup, nothing to sign in to.
>
> Sodogku Pro is a one-time purchase: no ads, unlimited offline play, and boosters topped back up
> at the start of every attempt.
>
> Go find the dogs.

### What came out, and why

The cuts are the point, so they are listed rather than just made.

- **The auto-marking sentence was wrong**, not merely long. It said placing a dog correctly
  "rules out its own row, column, colour and neighbours for you, and half the board falls into
  place at once". That is the "Cross off squares for me" setting, and `AppCache.autoMarkEnabled`
  defaults to **false**, so it is not what a new player sees. It would have been the first thing
  in the listing a reviewer could call untrue.
- **"Plays offline" is gone as a flat claim.** Every level is in the app, but the free game needs
  a connection for ads and `pages/terms.html` says so. Offline now appears only where it is
  unconditionally true, in the Pro line.
- **The two helpers, the uniqueness guarantee, the colourblind setting and the difficulty
  curve** were all true and all cut. They are reasons to keep playing, not reasons to install,
  and the screenshots carry more of them than a paragraph does.
- **No count of levels.** It went stale once already, at 500, and a number in the long
  description can only be corrected through a release. The App Store promotional text is the
  field for anything that grows, because it can be edited without submitting a build.

### Still deliberately absent

- No "endless hours of fun", no "train your brain", no claim about IQ or cognition.
- No achievements. A badge count is not a reason to install.
- **No leaderboards**, even though they are built, because on Android every board id is still an
  empty string and the feature is inert there. One description serves both stores, and a sentence
  that is true on an iPhone and false on a Pixel is worse than no sentence.
- **No sharing.** The feature was removed entirely
  (`features.md#what-the-game-does-not-have`) and the sentence about posting coloured squares
  outlived it long enough to be worth recording here.

---

## 4. Screenshot plan

Eight captured, in `docs/store/screenshots/android-phone/`. Play takes up to eight; the first three
are what almost everyone sees, because Play shows a scrollable row and most people never scroll.

> **None of these eight can be submitted as they stand.** They were captured against a build that
> has since moved, and two of them are now wrong rather than merely dated. `02-good-dog.png` has a
> **Share** button on the win sheet and the app has no sharing at all any more, which is a
> screenshot advertising a control that is not there. `07-achievements.png` reads "2 of 21 earned"
> over a flat grid, and the screen is now 73 badges on nine labelled shelves. Recapture the set
> before anything is filed. The plan below is still the right plan; it is the frames that are
> stale.

Order matters more than count. This order answers, in sequence: what is this, why would I care, is
there enough of it.

| # | File | Screen | What it has to prove |
|---|---|---|---|
| 1 | `01-place-the-dogs.png` | 7x7 mid-attempt, three dogs down, auto-mark cascade visible, one red X | The mechanic in one glance. You can see that placing a dog cleared its row, column and colour, which is the thing that makes the game feel good and is impossible to explain in a sentence. |
| 2 | `02-good-dog.png` | Win sheet: dog, "Good dog!", paw rating, score, streak. **Recapture: the current file shows a Share button, and three paw slots where the rating now runs to five** | The reward. Also the only place the paw rating appears, so it doubles as "there is a score here and you can do better". |
| 3 | `03-ten-by-ten.png` | Level 391, 10x10, eight dogs placed, score 15,268 | Depth. Screenshots 1 and 2 are a small friendly board; this says the game does not run out. |
| 4 | `04-levels-and-daily.png` | Level pane over the board: daily card, level ladder with sizes and Treat rewards | Volume and the daily loop, in the same frame. Locked rows running past 400 do the "this list keeps going" job without a number, which is now the only place the listing makes that point at all. The Treat chips in the current file sit on levels the shipped schedule no longer pays, so this one needs recapturing too. |
| 5 | `05-daily-board.png` | A fresh 7x7 daily, untouched | The palette, clean. This is the prettiest frame in the set and the one that sells the look. |
| 6 | `06-shapes-on-colors.png` | 10x10 with the colourblind glyph set on | Accessibility, and it earns the line in the description. Also a genuinely distinctive image next to every other puzzle listing. |
| 7 | `07-achievements.png` | The badge shelves, a few earned. **Recapture: the current file predates the shelves and says 21** | Something to come back for. Weakest of the eight; drop it first if a stronger frame appears. |
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
- **iOS product-page screenshots are uploaded**, four of them, from the designed export frames
  resized to 1284x2778 for the 6.5" slot. The claim that lived here, that iOS had never been run on
  this machine, stopped being true well before that: Xcode builds and the simulator both work.

- **The `sodogku_pro` review screenshot is a real capture, not an export frame**, and lives at
  `docs/store/screenshots/ios-review/sodogku-pro-iap.png`. 1206x2622 from an iPhone 17 Pro
  simulator, taken of the actual paywall reached through Settings, "Get Sodogku Pro". It is the
  copy of what Apple holds; re-upload it rather than re-shooting if the console ever loses it.
  Two things about the capture that are not obvious:
  - **The price renders.** `$4.99` comes back from StoreKit on the simulator even with no
    `Products.storekit` configuration, which is not what `StoreBilling.swift`'s setup comment
    predicts. Do not assume a blank price and mock one up.
  - **The status bar was staged**, `simctl status_bar ... override --time 9:41`, and the app was
    relaunched from the home screen first, because launching it over another app leaves a
    "◀ Doublestack" return chip in the corner that no override removes. Clear the override
    afterwards.

### The `sodogku_pro` review notes, as filed

Kept here because the console is the only other copy, and because the paragraph about where the
purchase lives goes stale the moment an entry point moves. Re-check it against
`features.md#pro` before a resubmission.

> Sodogku Pro is the app's only in-app purchase. It is a one-time non-consumable, not a
> subscription, and nothing renews.
>
> How to reach the purchase screen shown in the attached screenshot: from the board, tap the gear
> in the top right to open Settings, scroll to the "Sodogku Pro" section, and tap "Get Sodogku
> Pro". The same screen also opens from the Go Pro button in the level drawer, and from the
> level-complete and level-failed screens.
>
> What Pro unlocks: no ads, unlimited offline play, continues, skips and streak freezes without
> watching an ad, boosters topped back up at the start of every attempt, and every level in the
> campaign open from the start.
>
> Restoring a purchase: Settings has a "Restore purchases" row directly under "Get Sodogku Pro".
> It is always present, whether or not the player already owns Pro, and it calls
> `AppStore.sync()`.
>
> The app has no accounts and no sign-in. The entitlement belongs to the Apple Account that bought
> it, so no demo credentials are needed to test any of the above.

**If you are filling these fields with a browser agent:** App Store Connect's text fields are
React-controlled. Setting `value` on the element makes the page look right and even enables Save,
but the next render throws the text away and Save silently does nothing. Type real keystrokes.
Uploads are the opposite: a file input persists as soon as it is set, with no Save at all.
- **No feature graphic** (Play, 1024x500) and no App Store preview video. Both want the icon
  artwork first.

---

## 5. Categorisation and the rest of the form

| Field | Play | App Store |
|---|---|---|
| Category | Games → Puzzle | Games → Puzzle (secondary: Board) |
| Contains ads | Yes | N/A (declared via the label) |
| In-app purchases | Yes, one managed product `sodogku_pro`, $4.99 | Yes, one non-consumable, same id and price |
| App price | Free, all 175 regions | Free, all 175 regions |
| Content rating | Everyone, IARC filed 2026-09-21, sole descriptor "In-Game Purchases" | 4+, calculated 2026-09-21, no override |
| Target audience | 13+ on the general-audience branch (`features.md#audience-and-consent`) | N/A |
| Privacy policy URL | `https://elijah-dangerfield.github.io/Sodogku/privacy.html` | same |
| Support URL | `https://elijah-dangerfield.github.io/Sodogku/` | same |

Data safety and the privacy nutrition label are in
[`data-safety.md`](./data-safety.md), not here.

### The two age-rating answers that are judgement, not fact

Both are on Apple's form and both would be easy to answer differently on a
re-file, so they are recorded rather than re-derived. Everything else on that
questionnaire is None or No.

- **Contests: Infrequent.** Apple's 2025 wording is "events that allow users to
  compete with one another for rankings, rewards, or the achievement of personal
  goals", which the Game Center boards plainly are. Infrequent rather than
  Frequent because the boards are a screen you go to from Settings, not part of
  the loop. It does not move the rating off 4+, but it is why Apple lists
  **Afghanistan and Morocco** as places the app will not be sold under local law.
  Answering None would be tidier and would not be true.
- **Social Media Disabled for Users Under 13: No.** The row stays enabled even
  with Social Media answered No, and Next will not advance until it is answered.
  Yes would assert that we call the Declared Age Range API before enabling social
  features. We have no social features and call no such API, so No is the answer
  that is true. It has no effect on the rating.

---

## 6. Copy that has to match the app

If any of these change in code, this file is wrong. Kept deliberately short: every line here is
something a person has to re-check by hand, so a claim that earns its place has to be worth that.

- "three rules" and their wording match the rule chips in
  `libraries/resources/.../strings.xml`.
- The 4x4 to 10x10 range matches `Board.MIN_SIZE` and `Board.MAX_SIZE` (`features.md#the-board`).
  Both ends are load-bearing constants rather than curve choices, so this is the one dimension
  claim safe to make.
- "one solution, reachable by reasoning" and "no two boards are the same puzzle" are both held by
  `LevelPackVerificationTest` over the whole pack (`features.md#the-campaign`). If that test is
  ever weakened, these two sentences come out of the description.
- "a Sniff refuses to be spent if it has nothing to add" matches
  `features.md#sniffs-and-treats` and `game.booster_no_op`.
- "no account, no sign-up" matches the whole architecture and the Settings copy.
- "your progress lives on this phone" is what Settings says, and it has an Android caveat. See
  `data-safety.md` §7.1 before this line goes public.

**No level count appears anywhere except the App Store promotional text**, which can be edited
without submitting a build. Keep it that way. A count in the description is the only claim in
this file that goes stale on its own, with nothing failing and nobody told, every time a level is
appended to the pack.
