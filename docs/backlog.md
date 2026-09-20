# Backlog

Ideas and gaps that are real but not being worked. Deliberately separate from
[todos.md](todos.md), which is a queue a worker takes from: anything in here has
been looked at and set down, and nothing should pick it up without a person
saying so first.

Move an item back into `todos.md` when it is actually next. Delete it when it
stops being a good idea, and say why in the commit.

## SD-10 [P1] — The game makes no sound

**Ask:** Haptics ship (`AppCache.hapticsEnabled`, `rememberHaptics`), audio does not exist
anywhere: no clips, no player, no `soundEnabled`, and `features.md#settings` records the
absence rather than a plan. Audio is one of the two things Meowdoku's reviewers praise
unprompted, the other being its hint.

**Done when:** Dog placed, strike, level win, praise sting, button tap and
achievement unlock all play; a Settings row silences them; and nothing plays
over the iOS silent switch.

**Hints:** `docs/OWNER-TODO.md` lists the six clips under "Art and audio" and they are still
unordered. Follow the haptics shape exactly: a flag in `AppCache`, a toggle in
Settings, and playback at the screen rather than in the ViewModel.

## SD-18 [P2] — Golden Race: a periodic pack where one mistake ends the run

**Ask:** Owner's design, 2026-09-09, taking the shape of Meowdoku's Golden Fish
(added late August 2026: one error and the run is over). A pool of 100 to 200
boards compiled every few weeks, not repeated in the campaign, entered from the
side pane with a badge, one mistake ends the run, ranked on how far and how
fast.

**Done when:** A decision is written down first, then built. The mode itself is
small; where the ranking lives is not.

**Decision needed:** Game Center and Play Games can both host a recurring
leaderboard that resets on a schedule, which covers ranking with no accounts and
no server. What they cannot host is the content, and what nothing can host
without a durable player identity is the Duolingo-style bracket the owner also
raised: promotion and relegation need cohorts assigned and remembered somewhere.
So this splits into (a) a local mode plus a platform recurring board, which can
ship now, and (b) a served event with brackets, which is v2 and reopens the
accounts question C0 closed. Pick (a) first and say so in `decisions.md`.

**Hints:** Content delivery is SD-19. One mistake ending the run interacts with
bones, which are one global count across the whole game: a race must not spend
them, or a bad run costs a player the campaign too. Meowdoku's own players are
angry about Golden Fish, and the complaint is that it changed the main loop
underneath them rather than sitting beside it. Ours has to be opt-in.

## SD-19 [P2] — Deliver level packs over the wire

**Ask:** Owner, 2026-09-09: a way to add levels, remove levels, and reorder the
campaign without a release. Today both packs are Kotlin source compiled into the
binary (`CampaignPackData.kt`, `DailyPackData.kt`) and decoded lazily by
`LevelPacks`, so every content change is an app update.

**Done when:** The app can fetch a pack, verify it, and use it in place of the
bundled one, and falls back to the bundled pack when the fetch fails, the device
is offline, or verification does not pass.

**Hints:** This does not break `features.md#the-campaign`. Generation stays offline on a JVM;
only delivery moves. `features.md#what-the-game-does-not-have` lists server-delivered packs as
a v1 non-goal, so this is a deliberate reversal and belongs in `decisions.md`.

Two hazards, both sharp. Progress is keyed on level id, so a pack that removes
or reorders ids silently reassigns a player's completed levels;
`PACK_VERSION` exists for exactly this and there is no migration behind it yet.
And `LevelPackVerificationTest` is the only thing standing between an unsolvable
board and a player, and it runs at build time, so a served pack needs the same
uniqueness check before it is signed, not after it is downloaded.

## SD-21 [P2] — Lockdown mode, where regions fade and the board reshuffles (spike)

**Ask:** Owner brainstorm, 2026-09-09: lock a colour in by finding its dog, and
if you do not, watch it fade to grey and the remaining tiles shuffle up into a
new valid configuration. "The animation there would need to be sick."

**Done when:** There is a written answer with a recommendation.

**Hints:** The animation is not the hard part. Every board has exactly one solution, and that
is the entire reason a tap can be answered right or wrong (`features.md#the-board`). A
reshuffle changes the answer underneath the player, so "wrong" stops being a fact about the
puzzle. The only version that keeps the promise is a precomputed chain generated offline: board
2 is a valid unique board that agrees with every dog already locked on board 1. Price that in
the generator before anybody designs the screen, because nothing is generated on device.

The cheap cousin worth costing in the same pass: the fade as pure time pressure,
with no reshuffle at all.

## SD-28 [P2] — Decide what a streak freeze is, now that the streak is not the daily's

**Ask:** Owner, 2026-09-09: *"We should have a todo to figure out streak freezes
and how that will work later. Maybe thats another thing users and earn idk."*

**Why this is now open rather than done.** Freezes already exist, but they were
built for the *daily*: `DailyRepository` has `freeze` and `restore`, they are
budgeted per month, and `DailyOutcome.Frozen`/`Restored` are rows in
`daily_result`. The streak no longer reads any of that. It folds over `play_day`,
where a day is either played or not, and `StreakDayState.Bridged` is currently a
state nothing can produce.

So there are two half-systems: a freeze that covers a missed *daily puzzle*, and
a streak that does not care about the daily. Neither is wrong; they are just no
longer the same feature.

**Decided 2026-09-20, by the owner: one freeze economy, on the play streak, and
the daily's own freeze and restore retire.** The reasoning and the phased plan are
in [`design/streak-freeze.md`](design/streak-freeze.md), and the short version is
that a freeze which auto-spends leaves nothing for a "restore" to do for anyone
who held one, so the two are one item with two spend moments. The three questions
below are kept because the document answers each of them and a reader arriving
here should see what was asked before seeing what was picked.

This item stays in the backlog until phase 1 ships. What is settled is the shape,
not the code.

**The decision to make first**, before any code:

- **What does a freeze cover?** Missing a day entirely is the only way to break a
  streak now, so a freeze is a day you did not open the app. That is a different
  product from "I opened the daily and lost", which is what the current freeze
  was for.
- **Where does one come from?** The owner's instinct is earning them. Options
  worth weighing: a reward for a run length (7 days pays one), a level reward
  alongside the Treat, an ad, or a Pro perk. Each implies a different cap.

  He came back to this on 2026-09-16 and picked the level-reward option out of
  that list: *"bones shouldnt be the only gift right? streak freezes too."* So a
  freeze becomes one of the things the first-clear reward can pay, alongside the
  Treat and bones, rather than a currency with its own source. That is the
  cheapest of the four to build and the only one that needs no new surface, but
  it couples the freeze budget to campaign progress, which is a different shape
  from the daily's per-month cap and cannot coexist with it unchanged. Decide
  which budget survives before building either.
- **Is it spent or automatic?** Duolingo's is bought in advance and spent
  silently on the missed day, which is why it feels like insurance rather than a
  refund. Spending it after the fact turns a broken streak into a shop prompt at
  the worst moment.

**Done when:** A missed day can be covered, the calendar draws it as
`StreakDayState.Bridged` (the state already exists and is already styled), and
`playStreakOn` walks through it without counting it. That last part matters:
`DailyStreak.streakOn` already had this shape, where a bridged day continues the
run without adding to it, and the new fold deliberately does not.

**Hints:** `libraries/progress/impl/.../streak/PlayStreak.kt` is the fold and is
a pure function of a set of dates, so covering a day is a matter of what goes
into that set, or a second set walked alongside it. The daily's own freeze
budgeting in `DailyRepositoryImpl` is worth reading before designing this, and
worth deciding whether it survives: two separate freeze economies would be one
too many.

Not urgent. A streak with no freeze is a working streak, and shipping the wrong
freeze is harder to undo than shipping none.

## SD-33 [P2] — An iOS ad has no timeout, so a wedged SDK freezes the board

**Was part of SD-1**, which was removed from the queue because its verification
half is owner work (`OWNER-TODO.md` item 11). This half is not verification. It
is a real hole in the fail-open rule.

Android wraps its load and consent calls in `withTimeoutOrNull`, so an SDK that
never answers becomes a free reward. iOS has no equivalent, so the same wedge
leaves the player looking at a board that will not move.

**Not fixed rather than fixed badly.** Racing an `async throws` whose
cancellation is opaque risks a leaked continuation, which fails worse than what
it guards. Doing it properly means an explicit continuation the timeout can
resume exactly once, and it wants somebody who can watch a real ad while they
build it, which is why it is here and not in the queue.

**Done when:** pulling the network mid-ad on iOS still lands the reward, and only
a deliberate dismissal withholds it.


## SD-53 [P2] — One owner directive arrived with no message on it

**Found by:** the 2026-09-10 sweep of `feedback_kind:owner_directive`.

Sentry SODOGKU-4, filed 2026-09-09 on `0829933`, has every tag a report should
have and no `feedback_message` in its extra data at all. Nine other directives
from the same period all carry theirs, so this is one lost report rather than a
broken pipeline.

It predates the fix in `4aef85e` that put the typed message on the carrier event,
so the likeliest reading is simply that it is older than the code that captures
it properly. Filed rather than chased because there is nothing to read: whatever
the owner typed is gone and only they know what it was.

**Worth a look only if it happens again** on a build from 2026-09-10 or later. If
it does, it is a live capture bug and belongs in `todos.md`, not here.

## SD-57 [P2] — The speed window scales linearly and difficulty does not

**Found by:** the SD-47 agent, 2026-09-10, after doubling the window.

`Scoring.speedWindowMsFor` grows the speed window in proportion to the grid, so
a 10x10 gets two and a half times a 4x4's allowance per placement. That made the
median run land inside the window again, which was the fix SD-47 needed.

But a board's actual difficulty grows faster than its side length. Deduction
depth is what makes a board hard, and it does not scale with the grid the way
cell count does. So the linear law is a convenient approximation rather than a
measured one.

**Not worth acting on yet, and that is the point of filing it here.** The
magnitude is right now, and the scaling law only matters if it is wrong at the
edges. If telemetry later shows big-board runs bunching at the bottom of the
ladder again, the law is the next thing to look at rather than the magnitude,
and that is the thing worth knowing in advance.

**Look at this if:** paw distributions by grid size diverge in the field.

## SD-69 [P1] — The store screenshots show a feature that no longer exists

**Found by:** the SD-65 agent, 2026-09-10, while sweeping the listing.

All eight frames in `docs/store/screenshots/android-phone/` predate the current
build, and two are wrong in ways a reviewer would see:

- **`02-good-dog.png` has a SHARE button on the win sheet.** Sharing was removed
  entirely. A submitted screenshot advertising a control that is not in the app
  is the kind of thing a review rejects over. It also shows three paw slots and
  paws now run to five.
- **`07-achievements.png`** reads "2 of 21 earned" over a flat grid. The page is
  now 73 badges on nine labelled shelves.

`04-levels-and-daily.png` shows a Treat chip on level 395, which the current
schedule no longer pays. The listing file now carries a callout saying the set is
stale, which is the holding position, not the fix.

**Done when:** all eight frames match the shipped app, and nothing in them
advertises something that was removed.

**Hints:** This needs an emulator, so it is owner work or work for a session with
a device. It is the same job as the iOS 6.9" frames in `OWNER-TODO.md`, which are
blocked on item 11, so doing both at once is the cheap order. The streak pages,
the win sheet, the board clock and the lose sheet have all changed too, so check
every frame rather than the two named here.

**Parked 2026-09-11.** Owner: they will retake the whole set themselves before
submitting, so there is no point fixing two frames now. Nothing reads these
files, so a stale screenshot in the repo costs nothing until somebody uploads
one. The listing copy that made the same claim in words is already fixed.

